package io.github.zzzyyylllty.chotentech.data

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
    val obstacleTasks: MutableList<PlatformExecutor.PlatformTask> = mutableListOf()
)

object PowerManager {

    init {
        devLog("[PowerManager] PowerManager initialized")
    }

    private val runtimeMap = mutableMapOf<String, PowerRuntime>()

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
        val block = e.clickedBlock!!
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

            // 开始打开障碍物
            power.runAgent("onStart", actor)
            openObstaclesSequentially(runtime)
            power.runAgent("onEnd", actor)

            // 持续时间结束后关闭
            runtime.closeDelayTask = submit(delay = power.activeDurationSeconds * 20L) {
                runtime.closeDelayTask = null

                power.runAgent("onClose", actor)
                // 关闭障碍物，全部关闭完后清理状态
                closeObstaclesSequentially(runtime, actor) {
                    runtime.originalBlocks.clear()
                    runtime.obstacleTasks.clear()
                }
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
}