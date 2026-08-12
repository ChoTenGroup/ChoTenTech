package io.github.zzzyyylllty.chotentech.feature.steampipe

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import io.github.zzzyyylllty.chotentech.util.devLog
import kotlin.math.abs
import kotlin.random.Random

// ---- Stage & Unit ----

enum class SteamPipeStage { IDLE, WARNING, ERUPTION, END }

data class BlockPos(val x: Int, val y: Int, val z: Int) {
    constructor(loc: Location) : this(loc.blockX, loc.blockY, loc.blockZ)
    fun toLocation(world: World) = Location(world, x.toDouble() + 0.5, y.toDouble() + 0.5, z.toDouble() + 0.5)
}

class SteamPipeUnit(
    val world: World,
    val pos: BlockPos,
    val blockType: Material,
    val isNative: Boolean,
    val levelConfig: SteamPipeLevelConfig,
    val activatedFaces: MutableList<BlockFace> = mutableListOf(),
    var stage: SteamPipeStage = SteamPipeStage.IDLE,
    var stageTicks: Int = 0,
    var warningTotalTicks: Int = 0,
    var eruptionTotalTicks: Int = 0,
    var endTotalTicks: Int = 0
) {
    fun location() = pos.toLocation(world)
}

object SteamPipeManager {

    private val levelConfigs = ConcurrentHashMap<String, SteamPipeLevelConfig>()
    private val activeUnits = ConcurrentHashMap<BlockPos, SteamPipeUnit>()
    private val playerHits = ConcurrentHashMap<UUID, Int>() // per-life hit count

    private var taskRunning = false
    private var pluginEnabled = true
    private var tickTask: PlatformExecutor.PlatformTask? = null

    // ---- Constants ----
    private const val SCAN_INTERVAL = 200
    private const val TICK_INTERVAL = 20
    private const val PARTICLE_WARNING_BASE = 8
    private const val PARTICLE_WARNING_MAX = 30
    private const val PARTICLE_ERUPTION = 50
    private const val PARTICLE_END_BASE = 20
    private val PARTICLE_TYPE by lazy { Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft("cloud")) }
    private const val SOUND_WARNING = "block.fire.ambient"
    private const val SOUND_ERUPTION = "block.lava.extinguish"
    private const val ENTITY_DAMAGE_RATIO = 0.5

    // ==================== Config Loading ====================

    fun loadConfig() {
        levelConfigs.clear()
        val dir = File(getDataFolder(), "steampipe")
        if (!dir.exists()) {
            releaseResourceFolder("steampipe")
        }
        val file = File(dir, "config.yml")
        if (!file.exists()) return

        val config = Configuration.loadFromFile(file)
        val pipes = config.getConfigurationSection("pipes") ?: return

        for (levelKey in pipes.getKeys(false)) {
            try {
                val sec = pipes.getConfigurationSection(levelKey) ?: continue

                val blocks = sec.getStringList("blocks").mapNotNull { name ->
                    try { Material.valueOf(name.uppercase()) } catch (_: Exception) { null }
                }
                if (blocks.isEmpty()) continue

                levelConfigs[levelKey] = SteamPipeLevelConfig(
                    level = levelKey,
                    blocks = blocks,
                    centerRange = parseRange(sec.getString("center_range", "10-45")),
                    maxSearches = sec.getInt("max_searches", 4),
                    maxNativeUnits = sec.getInt("max_native", 2),
                    warningSeconds = sec.getDouble("warning", 7.0),
                    warningSecondsNative = sec.getDouble("warning_native", 4.0),
                    eruptionSeconds = parseRange(sec.getString("eruption", "5-10")),
                    endSeconds = sec.getDouble("end", 1.0),
                    endSecondsNative = sec.getDouble("end_native", 2.0),
                    firstHitDamage = sec.getDouble("first_hit", 25.0),
                    firstHitDamageNative = sec.getDouble("first_hit_native", 30.0),
                    firstHitMinHp = sec.getDouble("first_hit_min_hp", 10.0),
                    secondHitDamage = sec.getDouble("second_hit", 40.0),
                    secondHitDamageNative = sec.getDouble("second_hit_native", 45.0)
                )
            } catch (_: Exception) { /* skip */ }
        }
        devLog("<gray>[SteamPipe] 加载了 ${levelConfigs.size} 个管道等级配置: ${levelConfigs.keys}</gray>")
    }

    private fun parseRange(str: String?): IntRange {
        if (str == null) return 0..30
        val parts = str.split("-")
        if (parts.size != 2) return 0..30
        val min = parts[0].trim().toIntOrNull() ?: 0
        val max = parts[1].trim().toIntOrNull() ?: 30
        return if (min <= max) min..max else max..min
    }

    fun getLevelConfig(level: String): SteamPipeLevelConfig? = levelConfigs[level]

    // ==================== Lifecycle ====================

    fun startTask() {
        if (taskRunning || !pluginEnabled) return
        taskRunning = true
        devLog("<gray>[SteamPipe] 启动 tick 任务</gray>")
        tickTask = submit(period = TICK_INTERVAL.toLong(), delay = 40L) {
            tick()
        }
    }

    fun stopTask() {
        taskRunning = false
        tickTask?.cancel()
        tickTask = null
        val unitCount = activeUnits.size
        activeUnits.clear()
        playerHits.clear()
        devLog("<gray>[SteamPipe] 停止任务，清理 $unitCount 个活跃管道</gray>")
    }

    fun cleanup() {
        pluginEnabled = false
        stopTask()
        levelConfigs.clear()
        devLog("<gray>[SteamPipe] 完全清理</gray>")
    }

    // ==================== Tick (Main Thread) ====================

    private var tickCounter = 0

    private fun tick() {
        if (!taskRunning) return
        tickCounter++
        processUnits()
        if (tickCounter % (SCAN_INTERVAL / TICK_INTERVAL) == 0) {
            scanForNewUnits()
        }
    }

    // ==================== Scan ====================

    private fun scanForNewUnits() {
        for ((levelKey, config) in levelConfigs) {
            val players = Bukkit.getOnlinePlayers().filter { isPlayerInLevel(it, levelKey) }
            if (players.isEmpty()) {
                devLog("<gray>[SteamPipe] 扫描 $levelKey: 没有玩家在该区域</gray>")
                continue
            }

            for (player in players) {
                if (activeUnits.size > 200) break

                val natives = findNativeUnits(player, config)
                if (natives.isEmpty()) {
                    devLog("<gray>[SteamPipe] 扫描 $levelKey: ${player.name} 附近未找到管道方块</gray>")
                }
                for (native in natives) {
                    if (activeUnits.containsKey(native.pos)) continue
                    native.warningTotalTicks = (config.warningSecondsNative * 20).toInt().coerceAtLeast(20)
                    native.eruptionTotalTicks = (config.eruptionSeconds.random() * 20).toInt().coerceAtLeast(40)
                    native.endTotalTicks = (config.endSecondsNative * 20).toInt().coerceAtLeast(10)
                    native.stage = SteamPipeStage.WARNING
                    activeUnits[native.pos] = native
                    extendExtraUnits(player.world, native.pos, config)
                }
            }
        }
    }

    private fun findNativeUnits(player: Player, config: SteamPipeLevelConfig): List<SteamPipeUnit> {
        val world = player.world
        val results = mutableListOf<SteamPipeUnit>()
        val px = player.location.blockX
        val py = player.location.blockY
        val pz = player.location.blockZ
        val attempts = config.maxSearches.coerceIn(1, 32)
        val needed = config.maxNativeUnits.coerceIn(1, 8)

        for (a in 0 until attempts) {
            if (results.size >= needed) break

            val dist = config.centerRange.random()
            val angle = Random.nextDouble() * 2 * Math.PI
            val cx = px + (dist * Math.cos(angle)).toInt()
            val cz = pz + (dist * Math.sin(angle)).toInt()

            var foundPos: BlockPos? = null
            search@ for (dy in -3..3) {
                val y = py + dy
                if (y < world.minHeight || y > world.maxHeight) continue
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        val pos = BlockPos(cx + dx, y, cz + dz)
                        val mat = world.getBlockType(pos)
                        if (mat !in config.blocks) continue
                        val faces = detectExposedFaces(world, pos)
                        if (faces.isEmpty()) continue
                        foundPos = pos
                        break@search
                    }
                }
            }

            if (foundPos != null) {
                if (isInBaseRegion(world, foundPos)) continue
                results.add(SteamPipeUnit(
                    world = world,
                    pos = foundPos,
                    blockType = world.getBlockType(foundPos),
                    isNative = true,
                    levelConfig = config
                ).apply { activatedFaces.addAll(detectExposedFaces(world, foundPos)) })
                devLog("<gray>[SteamPipe] 发现原生管道 </gray><white>${world.name} ${foundPos}</white>")
            }
        }
        return results
    }

    private fun extendExtraUnits(world: World, nativePos: BlockPos, config: SteamPipeLevelConfig) {
        val directions = listOf(
            1 to 0,  // EAST
            -1 to 0, // WEST
            0 to 1,  // SOUTH
            0 to -1  // NORTH
        ).shuffled()

        for ((dx, dz) in directions) {
            val steps = Random.nextInt(2, 7) // 2-6
            val ex = nativePos.x + dx * steps
            val ez = nativePos.z + dz * steps
            val epos = BlockPos(ex, nativePos.y, ez)
            if (activeUnits.containsKey(epos)) continue

            val mat = world.getBlockType(epos)
            if (mat !in config.blocks) continue
            if (isInBaseRegion(world, epos)) continue

            val faces = detectExposedFaces(world, epos)
            if (faces.isEmpty()) continue

            val unit = SteamPipeUnit(
                world = world,
                pos = epos,
                blockType = mat,
                isNative = false,
                levelConfig = config
            ).apply {
                activatedFaces.addAll(faces)
                warningTotalTicks = (config.warningSeconds * 20).toInt().coerceAtLeast(20)
                eruptionTotalTicks = (config.eruptionSeconds.random() * 20).toInt().coerceAtLeast(40)
                endTotalTicks = (config.endSeconds * 20).toInt().coerceAtLeast(10)
                stage = SteamPipeStage.WARNING
            }
            activeUnits[epos] = unit
            devLog("<gray>[SteamPipe] 扩展管道 </gray><white>${world.name} ${epos}</white>")
        }
    }

    private fun detectExposedFaces(world: World, pos: BlockPos): List<BlockFace> {
        val faces = mutableListOf<BlockFace>()
        val checks = listOf(
            BlockFace.NORTH to BlockPos(pos.x, pos.y, pos.z - 1),
            BlockFace.SOUTH to BlockPos(pos.x, pos.y, pos.z + 1),
            BlockFace.EAST  to BlockPos(pos.x + 1, pos.y, pos.z),
            BlockFace.WEST  to BlockPos(pos.x - 1, pos.y, pos.z)
        )
        for ((face, neighbor) in checks) {
            val mat = world.getBlockType(neighbor)
            if (mat.isAir || !mat.isSolid) {
                faces.add(face)
            }
        }
        return faces
    }

    private fun isPlayerInLevel(player: Player, levelKey: String): Boolean {
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return false
        return data.activeLevelStr == levelKey
    }

    // ==================== Process Units ====================

    private fun processUnits() {
        val iter = activeUnits.entries.iterator()
        while (iter.hasNext()) {
            val (_, unit) = iter.next()

            when (unit.stage) {
                SteamPipeStage.WARNING -> processWarning(unit)
                SteamPipeStage.ERUPTION -> processEruption(unit)
                SteamPipeStage.END -> processEnd(unit)
                else -> {}
            }

            unit.stageTicks++

            when (unit.stage) {
                SteamPipeStage.WARNING -> {
                    if (unit.stageTicks >= unit.warningTotalTicks) {
                        devLog("<gold>[SteamPipe] ${unit.world.name} ${unit.pos} ⚡ WARNING → ERUPTION</gold>")
                        unit.stage = SteamPipeStage.ERUPTION
                        unit.stageTicks = 0
                    }
                }
                SteamPipeStage.ERUPTION -> {
                    if (unit.stageTicks >= unit.eruptionTotalTicks) {
                        devLog("<yellow>[SteamPipe] ${unit.world.name} ${unit.pos} ⚡ ERUPTION → END</yellow>")
                        unit.stage = SteamPipeStage.END
                        unit.stageTicks = 0
                    }
                }
                SteamPipeStage.END -> {
                    if (unit.stageTicks >= unit.endTotalTicks) {
                        devLog("<gray>[SteamPipe] ${unit.world.name} ${unit.pos} 管道消散</gray>")
                        iter.remove()
                    }
                }
                else -> {}
            }
        }
    }

    // ==================== Warning Stage ====================

    private fun processWarning(unit: SteamPipeUnit) {
        val world = unit.world
        val loc = unit.location()
        val progress = (unit.stageTicks.toDouble() / unit.warningTotalTicks).coerceIn(0.0, 1.0)
        val count = (PARTICLE_WARNING_BASE + (PARTICLE_WARNING_MAX - PARTICLE_WARNING_BASE) * progress).toInt()

        // Activate faces progressively (each eligible face rolls once)
        val allExposed = detectExposedFaces(world, unit.pos)
        for (face in allExposed) {
            if (face in unit.activatedFaces) continue
            val chance = 1.0 / (unit.activatedFaces.size + 1)
            if (Random.nextDouble() < chance) {
                unit.activatedFaces.add(face)
            }
        }

        for (face in unit.activatedFaces) {
            spawnFaceParticles(world, loc, face, count)
        }
        if (unit.stageTicks % 10 == 0 && unit.activatedFaces.isNotEmpty()) {
            playSteamSound(world, loc, SOUND_WARNING, 0.5f + 0.5f * progress.toFloat())
        }
    }

    // ==================== Eruption Stage ====================

    private fun processEruption(unit: SteamPipeUnit) {
        val world = unit.world
        val loc = unit.location()

        for (face in unit.activatedFaces) {
            spawnFaceParticles(world, loc, face, PARTICLE_ERUPTION)
        }
        if (unit.stageTicks % 20 == 0 && unit.activatedFaces.isNotEmpty()) {
            playSteamSound(world, loc, SOUND_ERUPTION, 1.5f)
        }
        if (unit.stageTicks % 20 == 0) {
            applyDamage(unit, loc)
        }
    }

    // ==================== End Stage ====================

    private fun processEnd(unit: SteamPipeUnit) {
        val world = unit.world
        val loc = unit.location()
        val remaining = 1.0 - (unit.stageTicks.toDouble() / unit.endTotalTicks).coerceIn(0.0, 1.0)
        val count = (PARTICLE_END_BASE * remaining).toInt()
        if (count <= 0) return

        for (face in unit.activatedFaces) {
            spawnFaceParticles(world, loc, face, count)
        }
        if (unit.stageTicks % 10 == 0 && unit.activatedFaces.isNotEmpty()) {
            playSteamSound(world, loc, SOUND_WARNING, 0.3f * remaining.toFloat())
        }
    }

    // ==================== Particles & Sound ====================

    private fun spawnFaceParticles(world: World, loc: Location, face: BlockFace, count: Int) {
        if (PARTICLE_TYPE == null || count <= 0) return
        val dir = face.direction.clone()
        val pLoc = loc.clone().add(dir.multiply(0.5))
        val ox = abs(face.modX).toDouble() * 0.5 + 0.3
        val oz = abs(face.modZ).toDouble() * 0.5 + 0.3
        PARTICLE_TYPE?.let { world.spawnParticle(it, pLoc, count.coerceAtMost(50), ox, 0.4, oz, 0.05) }
    }

    private fun playSteamSound(world: World, loc: Location, sound: String, volume: Float) {
        world.playSound(loc, sound, volume, 0.8f)
    }

    // ==================== Damage ====================

    private fun applyDamage(unit: SteamPipeUnit, loc: Location) {
        val config = unit.levelConfig
        val world = loc.world ?: return

        for (face in unit.activatedFaces) {
            val nx = loc.blockX + face.modX
            val nz = loc.blockZ + face.modZ
            for (dy in -2..2) {
                val y = loc.blockY + dy
                val dloc = Location(world, nx.toDouble() + 0.5, y.toDouble() + 0.5, nz.toDouble() + 0.5)
                val entities = world.getNearbyEntities(dloc, 0.6, 0.6, 0.6)
                for (entity in entities) {
                    if (entity is LivingEntity) {
                        if (entity is Player) {
                            applyPlayerDamage(entity, unit)
                        } else {
                            applyEntityDamage(entity, config)
                        }
                    }
                }
            }
        }
    }

    private fun applyPlayerDamage(player: Player, unit: SteamPipeUnit) {
        if (isPlayerInBase(player)) return
        val config = unit.levelConfig
        val hits = playerHits.getOrDefault(player.uniqueId, 0)

        if (hits == 0) {
            // First hit in this life — non-lethal
            val damage = if (unit.isNative) config.firstHitDamageNative else config.firstHitDamage
            val minHp = config.firstHitMinHp
            val currentHp = player.health

            if (currentHp < minHp) return
            if (currentHp - damage < minHp) {
                player.health = minHp
                devLog("<red>[SteamPipe] ${player.name} 首次命中(保底) ${currentHp}→$minHp HP</red>")
            } else {
                player.damage(damage)
                devLog("<red>[SteamPipe] ${player.name} 首次命中 </red><white>${damage}伤害 </white><gray>(${currentHp}→${player.health} HP)</gray>")
            }
            playerHits[player.uniqueId] = 1
        } else {
            // Second+ hit — can be lethal
            val damage = if (unit.isNative) config.secondHitDamageNative else config.secondHitDamage
            player.damage(damage)
            devLog("<red>[SteamPipe] ${player.name} 第${hits+1}次命中 </red><white>${damage}伤害 </white><gray>(${player.health} HP 剩余)</gray>")
        }
    }

    private fun applyEntityDamage(entity: LivingEntity, config: SteamPipeLevelConfig) {
        val damage = config.secondHitDamage * ENTITY_DAMAGE_RATIO
        entity.damage(damage)
        devLog("<red>[SteamPipe] 实体 ${entity.name} 受到 $damage 伤害</red>")
    }

    // ==================== Hit Reset (on death/respawn) ====================

    fun clearPlayerHits(player: Player) {
        playerHits.remove(player.uniqueId)
    }

    /** 玩家下线清除命中计数，防止未死亡玩家泄漏缓存 */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clearPlayerHits(event.player)
    }

    // ==================== Block Type Helper ====================

    private fun World.getBlockType(pos: BlockPos): Material = getBlockAt(pos.x, pos.y, pos.z).type

    // ==================== Base Guard ====================

    /** Check if a block position is inside any base region */
    private fun isInBaseRegion(world: World, pos: BlockPos): Boolean {
        try {
            val container = WorldGuard.getInstance().platform.regionContainer
            val rm = container[BukkitAdapter.adapt(world)] ?: return false
            val ids = rm.getApplicableRegionsIDs(BlockVector3.at(pos.x, pos.y, pos.z))
            return ids.any { it.startsWith("base-") }
        } catch (_: Exception) {
            return false
        }
    }

    /** Check if a player is currently inside any base region */
    private fun isPlayerInBase(player: Player): Boolean {
        return playerRegions[player.uniqueId.toString()]?.any { it.startsWith("base-") } == true
    }
}
