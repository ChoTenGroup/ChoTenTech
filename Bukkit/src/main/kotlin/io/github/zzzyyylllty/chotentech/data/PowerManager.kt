package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.event.NoclipEnterEvent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.logger.warningS
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.util.isRightClickBlock


fun reloadAllPowers(newPowers: List<Power>) {
    PowerManager.cancelAllAndRestore()
    PowerManager.clearAllRuntime()

    newPowers.forEach { PowerManager.register(it) }
}

data class PowerRuntime(
    val power: Power,
    val originalBlocks: MutableMap<Pos, String> = mutableMapOf(),

    /** 拉闸后延迟开启任务 */
    var openDelayTask: PlatformExecutor.PlatformTask? = null,

    /** 开启后延迟关闭任务 */
    var closeDelayTask: PlatformExecutor.PlatformTask? = null,

    /** 所有正在执行的障碍物逐步动画任务 */
    val obstacleTasks: MutableList<PlatformExecutor.PlatformTask> = mutableListOf(),

    /** 电梯运行时状态，key 为 obstacle.id */
    val elevatorRuntimes: MutableMap<String, ElevatorRuntime> = mutableMapOf(),
)

object PowerManager {

    init {
        devLog("[PowerManager] PowerManager initialized")
    }

    private val runtimeMap = mutableMapOf<String, PowerRuntime>()

    /** 由电梯触发的切出中的玩家 UUID（不会因松开潜行而取消） */
    val elevatorNoclipPlayers = java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(true)

    /** 标记玩家为电梯触发的切出 */
    fun markElevatorNoclip(playerId: String) {
        elevatorNoclipPlayers.add(playerId)
    }

    /** 清理标记 */
    fun unmarkElevatorNoclip(playerId: String) {
        elevatorNoclipPlayers.remove(playerId)
    }

    fun getRuntimeMap(): Map<String, PowerRuntime> = runtimeMap

    fun register(power: Power) {
        runtimeMap[power.name] = PowerRuntime(power)
    }

    fun unregister(name: String) {
        cancelPower(name)
        runtimeMap.remove(name)
    }

    fun isRegistered(name: String): Boolean = runtimeMap.containsKey(name)


    /**
     * 取消某一个 power 的所有任务，并恢复它控制的门/障碍物
     */
    fun cancelPowerAndRestore(powerName: String) {
        val runtime = runtimeMap[powerName] ?: return
        cancelRuntime(runtime, restore = true)
    }

    /**
     * 取消所有 power 的任务，并恢复所有障碍物
     * 适合：插件重载、地图重置、服务器停止前处理
     */
    fun cancelAllAndRestore() {
        runtimeMap.values.forEach { runtime ->
            cancelRuntime(runtime, restore = true)
        }
    }

    fun onReload() {
        cancelAllAndRestore()
    }

    /**
     * 仅取消所有任务，不恢复方块
     * 如果你想在某些特殊场景下保留当前门状态，可以用这个
     */
    fun cancelAllTasksOnly() {
        runtimeMap.values.forEach { runtime ->
            cancelRuntime(runtime, restore = false)
        }
    }

    /**
     * 清空所有 runtime
     * 一般在你重新加载配置前可以先调用 cancelAllAndRestore()
     * 然后再 clear
     */
    fun clearAllRuntime() {
        runtimeMap.clear()
    }

    /**
     * 单个 runtime 的统一取消与恢复逻辑
     */
    private fun cancelRuntime(runtime: PowerRuntime, restore: Boolean) {
        runtime.openDelayTask?.cancel()
        runtime.closeDelayTask?.cancel()
        runtime.obstacleTasks.forEach { it.cancel() }

        // 清理电梯门任务
        runtime.elevatorRuntimes.values.forEach { elevator ->
            (elevator.doorTask as? PlatformExecutor.PlatformTask)?.cancel()
        }
        runtime.elevatorRuntimes.clear()

        runtime.openDelayTask = null
        runtime.closeDelayTask = null
        runtime.obstacleTasks.clear()

        if (restore) {
            restorePower(runtime)
        }
    }

    /**
     * 恢复这个 power 记录过的所有原始方块
     * 注意：必须在主线程执行
     */
    private fun restorePower(runtime: PowerRuntime) {
        if (runtime.originalBlocks.isEmpty()) return

        runtime.originalBlocks.forEach { (pos, saved) ->
            val block = pos.toLocation().block
            block.blockData = Bukkit.createBlockData(saved)
        }

        runtime.originalBlocks.clear()
    }

    @SubscribeEvent
    fun onLeverPull(e: PlayerInteractEvent) {
        val name = e.clickedBlock?.type?.name ?: return
        if (!(e.isRightClickBlock() && (name.contains("BUTTON") || name.contains("LEVER")))) {
            return
        }
        val player = e.player
        val block = e.clickedBlock!!

        // 先检查是否为电梯按钮
        if (handleElevatorButtonClick(player, block)) return

        // 原有拉闸逻辑
        val data = player.getData()
        if (data == null) {
            devLog("[PowerManager] Player ${player.name} has no data")
            return
        }
        val level = data.activeLevel
        if (level == null) {
            devLog("[PowerManager] Player ${player.name} has no active level")
            return
        }
        val powers = level.powers
        if (powers.isEmpty()) {
            devLog("[PowerManager] Level ${level.name} has no powers")
            return
        }
        var selectedPower: PowerRuntime? = null
        devLog("[PowerManager] Checking lever at ${block.world.name} ${block.x},${block.y},${block.z}")
        for (power in powers) {
            val rpower = runtimeMap[power]
            if (rpower == null) {
                devLog("[PowerManager] Power '$power' not found in runtimeMap")
                continue
            }
            val pos = rpower.power.leverPos
            devLog("[PowerManager] Comparing with power '${rpower.power.name}' at ${pos.world} ${pos.x},${pos.y},${pos.z}")
            if (pos.world == block.world.name && pos.x == block.x && pos.z == block.z && pos.y == block.y) {
                selectedPower = rpower
                devLog("[PowerManager] Found matching power: ${rpower.power.name}")
                break
            }
        }
        if (selectedPower == null) {
            devLog("[PowerManager] No matching power found for lever")
            return
        }
        devLog("[PowerManager] Activating power: ${selectedPower.power.name}")
        activate(selectedPower.power.name, e.player)
    }

    /**
     * 玩家拉闸后触发
     */
    fun activate(powerName: String, actor: Player? = null) {
        val runtime = runtimeMap[powerName] ?: return
        val power = runtime.power

        // 已经在执行中则不重复触发
        if (
            runtime.openDelayTask != null ||
            runtime.closeDelayTask != null ||
            runtime.obstacleTasks.isNotEmpty()
        ) {
            power.runAgent("onAlready", actor)
            return
        }

        // 执行 onPrepare agent
        try {
            power.runAgent("onPrepare", actor)
            devLog("[PowerManager] Executed onPrepare agent for power: $powerName")
        } catch (e: Exception) {
            warningS("[PowerManager] Failed to execute onPrepare agent for power: $powerName: ${e.message}")
        }

        val world = Bukkit.getWorld(power.leverPos.world) ?: return
        val leverLoc = Location(world, power.leverPos.x.toDouble(), power.leverPos.y.toDouble(), power.leverPos.z.toDouble())

        // 拉闸时可选音效/粒子
        power.openingAnimation.sound?.let {
            world.playSound(leverLoc, it, power.openingAnimation.volume, power.openingAnimation.pitch)
        }

        // 延迟开启
        runtime.openDelayTask = submit(delay = power.openDelaySeconds * 20L) {
            runtime.openDelayTask = null

            // 开始打开障碍物（跳过 ELEVATOR_DOORS，它们由按钮控制）
            power.runAgent("onStart", actor)
            openObstaclesSequentially(runtime)
            power.runAgent("onEnd", actor)

            // 初始化电梯运行时状态
            initElevators(runtime)

            // 持续时间结束后关闭
            runtime.closeDelayTask = submit(delay = power.activeDurationSeconds * 20L) {
                runtime.closeDelayTask = null

                power.runAgent("onClose", actor)
                closePowerAndHandleElevators(runtime, actor)
            }
        }
    }

    /**
     * 取消某个 power 的所有任务
     */
    fun cancelPower(powerName: String) {
        val runtime = runtimeMap[powerName] ?: return

        runtime.openDelayTask?.cancel()
        runtime.closeDelayTask?.cancel()

        runtime.obstacleTasks.forEach { it.cancel() }

        runtime.elevatorRuntimes.values.forEach { elevator ->
            (elevator.doorTask as? PlatformExecutor.PlatformTask)?.cancel()
        }
        runtime.elevatorRuntimes.clear()

        runtime.openDelayTask = null
        runtime.closeDelayTask = null
        runtime.obstacleTasks.clear()
    }

    /**
     * 按顺序缓慢打开障碍物
     */
    private fun openObstaclesSequentially(runtime: PowerRuntime) {
        val power = runtime.power

        power.obstacles.forEach { obstacle ->
            // 电梯门不在此处理
            if (obstacle.mode == ObstacleMode.ELEVATOR_DOORS) return@forEach

            val config = obstacle.sequentialConfig
            val blocks = obstacle.allBlocks()

            // 非逐步模式：直接开
            if (!config.enabled || obstacle.mode != ObstacleMode.RESTORE_BLOCKS) {
                openObstacleInstant(runtime, obstacle)
                return@forEach
            }

            val sorted = sortPositionsByDirection(blocks, config.openDirection)

            // 先保存原始方块
            sorted.forEach { pos ->
                val block = pos.toLocation().block
                if (!runtime.originalBlocks.containsKey(pos)) {
                    runtime.originalBlocks[pos] = block.blockData.asString
                }
            }

            // 逐步移除
            var index = 0
            lateinit var task: PlatformExecutor.PlatformTask

            task = submit(period = config.stepDelayTicks) {
                if (index >= sorted.size) {
                    runtime.obstacleTasks.remove(task)
                    task.cancel()
                    return@submit
                }

                val end = minOf(index + config.blocksPerStep, sorted.size)

                for (i in index until end) {
                    val pos = sorted[i]
                    val loc = pos.toLocation()

                    // 播放当前正在打开位置的粒子/音效
                    playProgressEffect(loc, config.openEffect)

                    // 移除障碍
                    loc.block.type = Material.AIR
                }

                index = end
            }

            runtime.obstacleTasks += task
        }
    }

    /**
     * 按顺序缓慢关闭/恢复障碍物
     * 所有障碍物关闭完之后执行 onFinished
     */
    private fun closeObstaclesSequentially(
        runtime: PowerRuntime,
        actor: Player?,
        onFinished: () -> Unit
    ) {
        val power = runtime.power

        if (power.obstacles.isEmpty()) {
            power.runAgent("onCloseEnd", actor)
            onFinished()
            return
        }

        var remaining = power.obstacles.size

        fun oneObstacleDone() {
            remaining--
            if (remaining == 0) {
                power.runAgent("onCloseEnd", actor)
                onFinished()
            }
        }

        power.obstacles.forEach { obstacle ->
            // 电梯门不在此处理
            if (obstacle.mode == ObstacleMode.ELEVATOR_DOORS) {
                oneObstacleDone()
                return@forEach
            }

            val config = obstacle.sequentialConfig
            val blocks = obstacle.allBlocks()

            // 非逐步模式：直接恢复
            if (!config.enabled || obstacle.mode != ObstacleMode.RESTORE_BLOCKS) {
                closeObstacleInstant(runtime, obstacle)
                oneObstacleDone()
                return@forEach
            }

            val sorted = sortPositionsByDirection(blocks, config.openDirection)
            val restoreList = if (config.reverseOnClose) sorted.asReversed() else sorted

            var index = 0
            lateinit var task: PlatformExecutor.PlatformTask

            task = submit(period = config.stepDelayTicks) {
                if (index >= restoreList.size) {
                    runtime.obstacleTasks.remove(task)
                    task.cancel()
                    oneObstacleDone()
                    return@submit
                }

                val end = minOf(index + config.blocksPerStep, restoreList.size)

                for (i in index until end) {
                    val pos = restoreList[i]
                    val saved = runtime.originalBlocks.remove(pos) ?: continue
                    val loc = pos.toLocation()

                    // 播放关闭过程粒子/音效
                    playProgressEffect(loc, config.closeEffect)

                    // 恢复方块
                    loc.block.blockData = Bukkit.createBlockData(saved)
                }

                index = end
            }

            runtime.obstacleTasks += task
        }
    }

    /**
     * 瞬间打开障碍物
     */
    private fun openObstacleInstant(runtime: PowerRuntime, obstacle: ObstacleConfig) {
        val blocks = obstacle.allBlocks()

        when (obstacle.mode) {
            ObstacleMode.RESTORE_BLOCKS -> {
                blocks.forEach { pos ->
                    val loc = pos.toLocation()
                    val block = loc.block

                    if (!runtime.originalBlocks.containsKey(pos)) {
                        runtime.originalBlocks[pos] = block.blockData.asString
                    }

                    block.type = Material.AIR
                }
            }

            ObstacleMode.ELEVATOR_DOORS -> {
                // 电梯门不在此处处理
            }

            ObstacleMode.OPEN_DOOR -> {
                blocks.forEach { pos ->
                    val loc = pos.toLocation()
                    val block = loc.block
                    val data = block.blockData

                    if (!runtime.originalBlocks.containsKey(pos)) {
                        runtime.originalBlocks[pos] = data.asString
                    }

                    if (data is Door) {
                        data.isOpen = true
                        block.blockData = data
                    } else {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }

    /**
     * 瞬间恢复障碍物
     */
    private fun closeObstacleInstant(runtime: PowerRuntime, obstacle: ObstacleConfig) {
        val blocks = obstacle.allBlocks()

        blocks.forEach { pos ->
            val saved = runtime.originalBlocks.remove(pos) ?: return@forEach
            val loc = pos.toLocation()
            loc.block.blockData = Bukkit.createBlockData(saved)
        }
    }

    /**
     * 播放某个正在开门/关门位置的粒子和音效
     */
    private fun playProgressEffect(loc: Location, effect: ProgressEffect) {
        if (!effect.enabled) return
        val world = loc.world ?: return

        effect.sound?.let {
            world.playSound(loc, it, effect.volume, effect.pitch)
        }

        world.spawnParticle(
            effect.particle,
            loc.x + 0.5,
            loc.y + 0.5,
            loc.z + 0.5,
            effect.count,
            effect.offsetX,
            effect.offsetY,
            effect.offsetZ,
            effect.speed
        )
    }

    // ===== 电梯系统 =====

    /** 获取或创建电梯运行时（保证电梯在无电源时也能运作） */
    private fun getOrCreateElevatorRuntime(runtime: PowerRuntime, obstacle: ObstacleConfig): ElevatorRuntime {
        return runtime.elevatorRuntimes.getOrPut(obstacle.id) {
            ElevatorRuntime(config = obstacle.elevatorConfig!!)
        }
    }

    /** 检查点击的按钮是否为电梯按钮，如果是则处理 */
    private fun handleElevatorButtonClick(player: Player, block: org.bukkit.block.Block): Boolean {
        val bw = block.world.name
        val bx = block.x
        val by = block.y
        val bz = block.z

        for ((_, runtime) in runtimeMap) {
            for (obstacle in runtime.power.obstacles) {
                if (obstacle.mode != ObstacleMode.ELEVATOR_DOORS) continue
                val cfg = obstacle.elevatorConfig ?: continue

                val isExternal = cfg.externalButton.let { it.world == bw && it.x == bx && it.y == by && it.z == bz }
                val isInternal = cfg.internalButton.let { it.world == bw && it.x == bx && it.y == by && it.z == bz }
                if (!isExternal && !isInternal) continue

                val elevator = getOrCreateElevatorRuntime(runtime, obstacle)
                val powerActive = runtime.closeDelayTask != null

                when {
                    isExternal && powerActive && elevator.state == ElevatorState.IDLE -> {
                        openElevatorDoors(runtime, obstacle, player)
                    }
                    isExternal && !powerActive -> {
                        player.sendMessage("§c需要先激活电源才能使用电梯。")
                    }
                    isExternal && powerActive && elevator.state != ElevatorState.IDLE -> {
                        player.sendMessage("§c电梯正在运行中，请稍后再试。")
                    }
                    isInternal && powerActive && elevator.state == ElevatorState.IDLE -> {
                        // 门已关闭，从内部切出
                        if (isPlayerInElevatorArea(player, obstacle)) {
                            triggerElevatorNoclip(runtime, obstacle, player)
                        } else {
                            player.sendMessage("§c请在电梯内使用此按钮。")
                        }
                    }
                    isInternal && powerActive && elevator.state == ElevatorState.DOORS_OPEN -> {
                        // 门开着，按内部按钮关门
                        closeElevatorDoors(runtime, obstacle) {}
                    }
                    isInternal && !powerActive -> {
                        if (isPlayerInElevatorArea(player, obstacle)) {
                            openElevatorDoors(runtime, obstacle, player)
                        } else {
                            player.sendMessage("§c电梯内按钮仅限电梯内部使用。")
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    /** 初始化电梯运行时状态（仅在不存在时创建，避免覆盖正在运作的电梯） */
    private fun initElevators(runtime: PowerRuntime) {
        for (obstacle in runtime.power.obstacles) {
            if (obstacle.mode != ObstacleMode.ELEVATOR_DOORS) continue
            val cfg = obstacle.elevatorConfig ?: continue
            getOrCreateElevatorRuntime(runtime, obstacle)
        }
    }

    /** 电梯切出完成后关闭非电梯障碍物并清理 */
    private fun finishPowerAfterElevatorNoclip(runtime: PowerRuntime, actor: Player?) {
        runtime.power.runAgent("onClose", actor)
        closeObstaclesSequentiallyExceptElevators(runtime, actor) {
            runtime.obstacleTasks.clear()
            runtime.power.runAgent("onCloseEnd", actor)
        }
    }

    /** 关闭所有非电梯障碍物（逐步动画），完成后回调 */
    private fun closeObstaclesSequentiallyExceptElevators(
        runtime: PowerRuntime,
        actor: Player?,
        onFinished: () -> Unit
    ) {
        val nonElevators = runtime.power.obstacles.filter { it.mode != ObstacleMode.ELEVATOR_DOORS }

        if (nonElevators.isEmpty()) {
            onFinished()
            return
        }

        var remaining = nonElevators.size

        fun oneDone() {
            remaining--
            if (remaining == 0) onFinished()
        }

        for (obstacle in nonElevators) {
            val config = obstacle.sequentialConfig
            val blocks = obstacle.allBlocks()

            if (!config.enabled || obstacle.mode != ObstacleMode.RESTORE_BLOCKS) {
                closeObstacleInstant(runtime, obstacle)
                oneDone()
                continue
            }

            val sorted = sortPositionsByDirection(blocks, config.openDirection)
            val restoreList = if (config.reverseOnClose) sorted.asReversed() else sorted

            var index = 0
            lateinit var task: PlatformExecutor.PlatformTask

            task = submit(period = config.stepDelayTicks) {
                if (index >= restoreList.size) {
                    runtime.obstacleTasks.remove(task)
                    task.cancel()
                    oneDone()
                    return@submit
                }

                val end = minOf(index + config.blocksPerStep, restoreList.size)
                for (i in index until end) {
                    val pos = restoreList[i]
                    val saved = runtime.originalBlocks.remove(pos) ?: continue
                    val loc = pos.toLocation()
                    playProgressEffect(loc, config.closeEffect)
                    loc.block.blockData = Bukkit.createBlockData(saved)
                }
                index = end
            }
            runtime.obstacleTasks += task
        }
    }

    /** 电源时间耗尽时的关闭处理（含电梯强制切出逻辑） */
    private fun closePowerAndHandleElevators(runtime: PowerRuntime, actor: Player?) {
        // 计数器：等非电梯障碍物 + 所有电梯关门完成后再清理
        var pendingOperations = 1 // 非电梯障碍物计为 1

        fun onOperationComplete() {
            pendingOperations--
            if (pendingOperations <= 0) {
                runtime.obstacleTasks.clear()
                runtime.power.runAgent("onCloseEnd", actor)
            }
        }

        for (obstacle in runtime.power.obstacles) {
            if (obstacle.mode != ObstacleMode.ELEVATOR_DOORS) continue
            val elevator = runtime.elevatorRuntimes[obstacle.id] ?: continue
            val playersInside = getPlayersInElevatorArea(obstacle)

            pendingOperations++

            if (playersInside.isNotEmpty()) {
                for (p in playersInside) {
                    elevator.noclipPlayers.add(p.uniqueId.toString())
                }
                elevator.state = ElevatorState.NOCLIPPING
                closeElevatorDoors(runtime, obstacle) {
                    val cfg = obstacle.elevatorConfig
                    if (cfg != null) {
                        for (p in playersInside) {
                            startPlayerNoclip(p, cfg.noclipPointName)
                        }
                    }
                    onOperationComplete()
                }
            } else {
                closeElevatorDoors(runtime, obstacle) {
                    onOperationComplete()
                }
            }
        }

        // 关闭非电梯障碍物
        closeObstaclesSequentiallyExceptElevators(runtime, actor) {
            onOperationComplete()
        }
    }

    /** 开门动画 + 音效 */
    private fun openElevatorDoors(runtime: PowerRuntime, obstacle: ObstacleConfig, player: Player? = null) {
        val cfg = obstacle.elevatorConfig ?: return
        val elevator = getOrCreateElevatorRuntime(runtime, obstacle)
        if (elevator.state != ElevatorState.IDLE) return

        elevator.state = ElevatorState.DOORS_OPENING

        // 播放音效
        val world = Bukkit.getWorld(cfg.externalButton.world) ?: return
        val soundLoc = cfg.externalButton.toLocation()
        cfg.doorOpenSound?.let { world.playSound(soundLoc, it, cfg.soundVolume, cfg.soundPitch) }

        // 逐步移除门方块
        val blocks = obstacle.allBlocks()
        val sorted = sortPositionsByDirection(blocks, OpenDirection.LEFT_TO_RIGHT)

        // 先保存原始方块
        sorted.forEach { pos ->
            if (!runtime.originalBlocks.containsKey(pos)) {
                runtime.originalBlocks[pos] = pos.toLocation().block.blockData.asString
            }
        }

        // 逐步移除
        var index = 0
        lateinit var task: PlatformExecutor.PlatformTask
        task = submit(period = 3L) {
            if (index >= sorted.size) {
                runtime.obstacleTasks.remove(task)
                task.cancel()
                elevator.state = ElevatorState.DOORS_OPEN
                elevator.doorTask = null

                // 无电源模式：N秒后自动关门
                if (runtime.closeDelayTask == null) {
                    val sec = cfg.doorOpenDurationSeconds
                    player?.sendMessage("§e电梯门已开启，将在 ${sec} 秒后自动关闭。")
                    elevator.doorTask = submit(delay = sec * 20L) {
                        elevator.doorTask = null
                        if (elevator.state == ElevatorState.DOORS_OPEN) {
                            // 通知电梯内的玩家门即将关闭
                            for (p in getPlayersInElevatorArea(obstacle)) {
                                p.sendMessage("§c电梯门即将关闭。")
                            }
                            closeElevatorDoors(runtime, obstacle) {}
                        }
                    }
                }
                return@submit
            }

            val end = minOf(index + 2, sorted.size)
            for (i in index until end) {
                val pos = sorted[i]
                val loc = pos.toLocation()
                cfg.doorOpenParticle?.let { p ->
                    loc.world?.spawnParticle(p, loc.x + 0.5, loc.y + 0.5, loc.z + 0.5, 5, 0.15, 0.15, 0.15, 0.01)
                }
                loc.block.type = Material.AIR
            }
            index = end
        }
        runtime.obstacleTasks += task
        elevator.doorTask = task
    }

    /** 关门动画 + 音效 */
    private fun closeElevatorDoors(
        runtime: PowerRuntime,
        obstacle: ObstacleConfig,
        onFinished: () -> Unit
    ) {
        val cfg = obstacle.elevatorConfig ?: return
        val elevator = getOrCreateElevatorRuntime(runtime, obstacle)
        if (elevator.state == ElevatorState.IDLE) { onFinished(); return }
        elevator.state = ElevatorState.DOORS_CLOSING

        // 播放音效
        val world = Bukkit.getWorld(cfg.externalButton.world) ?: return
        val soundLoc = cfg.externalButton.toLocation()
        cfg.doorCloseSound?.let { world.playSound(soundLoc, it, cfg.soundVolume, cfg.soundPitch) }

        // 逐步恢复门方块
        val blocks = obstacle.allBlocks()
        val sorted = sortPositionsByDirection(blocks, OpenDirection.LEFT_TO_RIGHT).asReversed()

        var index = 0
        lateinit var task: PlatformExecutor.PlatformTask
        task = submit(period = 3L) {
            if (index >= sorted.size) {
                runtime.obstacleTasks.remove(task)
                task.cancel()
                elevator.state = ElevatorState.IDLE
                elevator.doorTask = null
                onFinished()
                return@submit
            }

            val end = minOf(index + 2, sorted.size)
            for (i in index until end) {
                val pos = sorted[i]
                val saved = runtime.originalBlocks.remove(pos) ?: continue
                val loc = pos.toLocation()
                cfg.doorCloseParticle?.let { p ->
                    loc.world?.spawnParticle(p, loc.x + 0.5, loc.y + 0.5, loc.z + 0.5, 5, 0.15, 0.15, 0.15, 0.01)
                }
                loc.block.blockData = Bukkit.createBlockData(saved)
            }
            index = end
        }
        runtime.obstacleTasks += task
        elevator.doorTask = task
    }

    /** 触发电梯切出：关门 + 开始切出 + 提前结束电源 */
    private fun triggerElevatorNoclip(runtime: PowerRuntime, obstacle: ObstacleConfig, player: Player) {
        val cfg = obstacle.elevatorConfig ?: return
        val elevator = runtime.elevatorRuntimes[obstacle.id] ?: return

        // 立即取消电源计时器，防止时间耗尽触发重复关门
        runtime.closeDelayTask?.cancel()
        runtime.closeDelayTask = null

        elevator.state = ElevatorState.NOCLIPPING
        elevator.noclipPlayers.add(player.uniqueId.toString())

        // 先关门，门关好后再触发切出和关闭其他障碍物
        closeElevatorDoors(runtime, obstacle) {
            startPlayerNoclip(player, cfg.noclipPointName)
            finishPowerAfterElevatorNoclip(runtime, player)
        }
    }

    /** 开始玩家的切出流程 */
    private fun startPlayerNoclip(player: Player, noclipPointName: String) {
        val data = player.getData() ?: return
        val level = data.activeLevel ?: return
        val noclipPoint = level.noclips.find { it.name == noclipPointName }
        if (noclipPoint == null) {
            devLog("[PowerManager] Noclip point '$noclipPointName' not found in level ${level.name}")
            return
        }

        data.noclipPoint = noclipPoint
        data.noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * 1000).toLong()
        data.noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).toLong()
        data.noclipping = true
        NoclipEnterEvent(player, level.name, noclipPoint.name).call()
        markElevatorNoclip(player.uniqueId.toString())
        player.sendMessage("§a电梯门已关闭，正在开始切出...")
        devLog("[PowerManager] Started noclip for ${player.name} → ${noclipPoint.name}")
    }

    /** 获取电梯区域内的在线玩家 */
    private fun getPlayersInElevatorArea(obstacle: ObstacleConfig): List<Player> {
        return Bukkit.getOnlinePlayers().filter { isPlayerInElevatorArea(it, obstacle) }
    }

    /** 检查玩家是否在电梯区域内 */
    private fun isPlayerInElevatorArea(player: Player, obstacle: ObstacleConfig): Boolean {
        val cfg = obstacle.elevatorConfig ?: return false
        val a1 = cfg.noclipArea1
        val a2 = cfg.noclipArea2
        val minX = minOf(a1.x, a2.x).toDouble()
        val maxX = maxOf(a1.x, a2.x).toDouble() + 1.0
        val minY = minOf(a1.y, a2.y).toDouble()
        val maxY = maxOf(a1.y, a2.y).toDouble() + 1.0
        val minZ = minOf(a1.z, a2.z).toDouble()
        val maxZ = maxOf(a1.z, a2.z).toDouble() + 1.0
        val loc = player.location
        return loc.world?.name == a1.world &&
            loc.x in minX..maxX &&
            loc.y in minY..maxY &&
            loc.z in minZ..maxZ
    }
}