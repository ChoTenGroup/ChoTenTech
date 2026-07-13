package io.github.zzzyyylllty.chotentech.feature.trap

import io.github.zzzyyylllty.chotentech.feature.projectile.ParticleConfig
import io.github.zzzyyylllty.chotentech.feature.projectile.SoundConfig
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import taboolib.common.LifeCycle
import taboolib.library.configuration.ConfigurationSection
import java.util.UUID
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object TrapManager {

    // ==================== Configs ====================

    private val blowers = ConcurrentHashMap<String, BlowerConfig>()
    private val lasers = ConcurrentHashMap<String, LaserConfig>()
    private val rockets = ConcurrentHashMap<String, RocketConfig>()

    // ==================== Active State ====================

    private val blowerCounters = ConcurrentHashMap<String, Int>()
    private val laserCounters = ConcurrentHashMap<String, Int>()
    private val rocketCounters = ConcurrentHashMap<String, Int>()

    data class ActiveRocket(
        val config: RocketConfig,
        val entity: Firework,
        val origin: Location,
        var passenger: Player?,
        var distance: Double
    )

    private val activeRockets = ConcurrentHashMap<UUID, ActiveRocket>()

    // ==================== Task ====================

    private var task: PlatformExecutor.PlatformTask? = null

    // ==================== Config Loading ====================

    fun loadConfig() {
        blowers.clear()
        lasers.clear()
        rockets.clear()
        blowerCounters.clear()
        laserCounters.clear()
        rocketCounters.clear()
        cleanupRockets()

        val dir = File(getDataFolder(), "traps")
        if (!dir.exists()) releaseResourceFolder("traps")
        val file = File(dir, "config.yml")
        if (!file.exists()) return

        val config = Configuration.loadFromFile(file)
        for (key in config.getKeys(false)) {
            try {
                val sec = config.getConfigurationSection(key) ?: continue
                val type = sec.getString("type") ?: continue
                val worldName = sec.getString("world") ?: continue
                if (Bukkit.getWorld(worldName) == null) {
                    devLog("[陷阱] 跳过 $key: 世界 $worldName 不存在")
                    continue
                }
                val enabled = sec.getBoolean("enabled", true)
                val interval = sec.getInt("interval", 20).coerceAtLeast(1)

                when (type) {
                    "blower" -> loadBlower(key, sec, worldName, enabled, interval)
                    "laser" -> loadLaser(key, sec, worldName, enabled, interval)
                    "rocket" -> loadRocket(key, sec, worldName, enabled, interval)
                    else -> devLog("[陷阱] 未知陷阱类型: $type")
                }
            } catch (e: Exception) {
                devLog("[陷阱] 加载 $key 失败: ${e.message}")
            }
        }
        devLog("[陷阱] 加载完成: ${blowers.size} 吹风机, ${lasers.size} 激光, ${rockets.size} 火箭")
    }

    private fun loadBlower(id: String, sec: ConfigurationSection, world: String, enabled: Boolean, interval: Int) {
        val p1 = sec.getConfigurationSection("pos1") ?: return
        val p2 = sec.getConfigurationSection("pos2") ?: return
        val dir = sec.getConfigurationSection("direction") ?: return
        blowers[id] = BlowerConfig(
            id = id, world = world, enabled = enabled, interval = interval,
            minX = minOf(p1.getDouble("x"), p2.getDouble("x")),
            minY = minOf(p1.getDouble("y"), p2.getDouble("y")),
            minZ = minOf(p1.getDouble("z"), p2.getDouble("z")),
            maxX = maxOf(p1.getDouble("x"), p2.getDouble("x")),
            maxY = maxOf(p1.getDouble("y"), p2.getDouble("y")),
            maxZ = maxOf(p1.getDouble("z"), p2.getDouble("z")),
            dirX = dir.getDouble("x"), dirY = dir.getDouble("y"), dirZ = dir.getDouble("z"),
            force = sec.getDouble("force", 1.0),
            particle = parseParticle(sec.getConfigurationSection("particle")),
            sound = parseSound(sec.getConfigurationSection("sound"))
        )
        blowerCounters[id] = 0
    }

    private fun loadLaser(id: String, sec: ConfigurationSection, world: String, enabled: Boolean, interval: Int) {
        val p1 = sec.getConfigurationSection("pos1") ?: return
        val p2 = sec.getConfigurationSection("pos2") ?: return
        lasers[id] = LaserConfig(
            id = id, world = world, enabled = enabled, interval = interval,
            minX = minOf(p1.getDouble("x"), p2.getDouble("x")),
            minY = minOf(p1.getDouble("y"), p2.getDouble("y")),
            minZ = minOf(p1.getDouble("z"), p2.getDouble("z")),
            maxX = maxOf(p1.getDouble("x"), p2.getDouble("x")),
            maxY = maxOf(p1.getDouble("y"), p2.getDouble("y")),
            maxZ = maxOf(p1.getDouble("z"), p2.getDouble("z")),
            damage = sec.getDouble("damage", 5.0).coerceAtLeast(0.0),
            particle = parseParticle(sec.getConfigurationSection("particle")),
            sound = parseSound(sec.getConfigurationSection("sound"))
        )
        laserCounters[id] = 0
    }

    private fun loadRocket(id: String, sec: ConfigurationSection, world: String, enabled: Boolean, interval: Int) {
        val launch = sec.getConfigurationSection("launch") ?: return
        val dir = sec.getConfigurationSection("direction") ?: return
        rockets[id] = RocketConfig(
            id = id, world = world, enabled = enabled, interval = interval,
            launchX = launch.getDouble("x"), launchY = launch.getDouble("y"), launchZ = launch.getDouble("z"),
            dirX = dir.getDouble("x"), dirY = dir.getDouble("y"), dirZ = dir.getDouble("z"),
            speed = sec.getDouble("speed", 2.0).coerceAtLeast(0.1),
            maxDistance = sec.getDouble("max_distance", 30.0).coerceAtLeast(1.0),
            explosionPower = sec.getInt("explosion_power", 3).coerceIn(0, 10),
            damage = sec.getDouble("damage", 20.0).coerceAtLeast(0.0),
            particle = parseParticle(sec.getConfigurationSection("particle"))
        )
        rocketCounters[id] = 0
    }

    private fun parseParticle(sec: ConfigurationSection?): ParticleConfig? {
        if (sec == null) return null
        val key = sec.getString("particle") ?: return null
        return ParticleConfig(
            key = key,
            count = sec.getInt("count", 1),
            speed = sec.getDouble("speed", 0.0),
            offsetX = sec.getDouble("offset_x", 0.0),
            offsetY = sec.getDouble("offset_y", 0.0),
            offsetZ = sec.getDouble("offset_z", 0.0)
        )
    }

    private fun parseSound(sec: ConfigurationSection?): SoundConfig? {
        if (sec == null) return null
        val key = sec.getString("sound") ?: return null
        return SoundConfig(
            key = key,
            volume = sec.getDouble("volume", 1.0).toFloat(),
            pitch = sec.getDouble("pitch", 1.0).toFloat()
        )
    }

    // ==================== Lifecycle ====================

    @Awake(LifeCycle.ACTIVE)
    fun startTask() {
        if (task != null) return
        task = submit(period = 1L) { tick() }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopTask() {
        task?.cancel()
        task = null
        cleanupRockets()
    }

    // ==================== Per-Tick Processing ====================

    private var globalTick = 0

    private fun tick() {
        globalTick++

        // Process blower and laser only every 10 ticks for performance
        if (globalTick % 10 == 0) {
            for (blower in blowers.values) {
                if (!blower.enabled) continue
                val counter = blowerCounters[blower.id] ?: 0
                val next = counter + 10
                if (next >= blower.interval) {
                    blowerCounters[blower.id] = next - blower.interval
                    processBlower(blower)
                } else {
                    blowerCounters[blower.id] = next
                }
            }

            for (laser in lasers.values) {
                if (!laser.enabled) continue
                val counter = laserCounters[laser.id] ?: 0
                val next = counter + 10
                if (next >= laser.interval) {
                    laserCounters[laser.id] = next - laser.interval
                    processLaser(laser)
                } else {
                    laserCounters[laser.id] = next
                }
            }
        }

        // Process rocket launchers every tick (counters)
        for (rocket in rockets.values) {
            if (!rocket.enabled) continue
            val counter = rocketCounters[rocket.id] ?: 0
            if (counter >= rocket.interval) {
                rocketCounters[rocket.id] = 0
                launchRocket(rocket)
            } else {
                rocketCounters[rocket.id] = counter + 1
            }
        }

        // Tick active rockets every tick
        tickRockets()
    }

    // ==================== Blower ====================

    private fun processBlower(config: BlowerConfig) {
        val world = Bukkit.getWorld(config.world) ?: return
        val center = Location(world,
            (config.minX + config.maxX) / 2,
            (config.minY + config.maxY) / 2,
            (config.minZ + config.maxZ) / 2
        )
        val radius = center.distance(Location(world, config.minX, config.minY, config.minZ))
        val dir = Vector(config.dirX, config.dirY, config.dirZ).normalize().multiply(config.force)

        for (player in world.getNearbyPlayers(center, radius)) {
            if (!isInAABB(player.location, config)) continue
            player.velocity = player.velocity.add(dir)
            if (config.particle != null) spawnParticle(world, player.location.add(0.0, 1.0, 0.0), config.particle)
        }
        if (config.sound != null) world.playSound(center, config.sound.key.lowercase(), config.sound.volume, config.sound.pitch)
    }

    // ==================== Laser ====================

    private fun processLaser(config: LaserConfig) {
        val world = Bukkit.getWorld(config.world) ?: return
        val center = Location(world,
            (config.minX + config.maxX) / 2,
            (config.minY + config.maxY) / 2,
            (config.minZ + config.maxZ) / 2
        )
        val radius = center.distance(Location(world, config.minX, config.minY, config.minZ))

        for (player in world.getNearbyPlayers(center, radius)) {
            if (!isInAABB(player.location, config)) continue
            player.damage(config.damage)
            if (config.particle != null) spawnParticle(world, player.location.add(0.0, 1.0, 0.0), config.particle)
        }
        if (config.sound != null) world.playSound(center, config.sound.key.lowercase(), config.sound.volume, config.sound.pitch)
    }

    // ==================== Rocket ====================

    private fun launchRocket(config: RocketConfig) {
        val world = Bukkit.getWorld(config.world) ?: return
        val loc = Location(world, config.launchX, config.launchY, config.launchZ)
        val dir = Vector(config.dirX, config.dirY, config.dirZ).normalize()

        val firework = world.spawn(loc, Firework::class.java).apply {
            val meta = fireworkMeta
            meta.power = 127
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BURST).build())
            fireworkMeta = meta
            isShotAtAngle = true
            velocity = dir.clone().multiply(config.speed)
            isGlowing = true
        }

        val active = ActiveRocket(
            config = config,
            entity = firework,
            origin = loc.clone(),
            passenger = null,
            distance = 0.0
        )
        activeRockets[firework.uniqueId] = active
    }

    private fun tickRockets() {
        if (activeRockets.isEmpty()) return

        val iter = activeRockets.entries.iterator()
        while (iter.hasNext()) {
            val (_, rocket) = iter.next()
            val entity = rocket.entity

            if (!entity.isValid || entity.isDead) {
                iter.remove()
                continue
            }

            val currentLoc = entity.location
            val config = rocket.config

            // Maintain velocity (fireworks naturally decelerate)
            val dir = Vector(config.dirX, config.dirY, config.dirZ).normalize()
            entity.velocity = dir.multiply(config.speed)

            // Update distance from origin
            rocket.distance = rocket.origin.distance(currentLoc)

            // Try to mount nearby player
            if (rocket.passenger == null) {
                for (player in currentLoc.getNearbyPlayers(1.5)) {
                    if (player.isDead || !player.isValid) continue
                    if (player.isInsideVehicle) continue
                    entity.addPassenger(player)
                    rocket.passenger = player
                    if (config.particle != null) {
                        spawnParticle(currentLoc.world ?: return, player.location.add(0.0, 1.0, 0.0), config.particle)
                    }
                    break
                }
            }

            // Check if passenger dismounted early
            if (rocket.passenger != null && !rocket.passenger!!.isValid) {
                rocket.passenger = null
            }
            if (rocket.passenger != null && !entity.passengers.contains(rocket.passenger)) {
                rocket.passenger = null
            }

            // Explosion check
            if (rocket.distance >= config.maxDistance) {
                explodeRocket(rocket)
                iter.remove()
                continue
            }
        }
    }

    private fun explodeRocket(rocket: ActiveRocket) {
        val loc = rocket.entity.location
        val world = loc.world ?: return
        val config = rocket.config

        // Dismount player
        rocket.passenger?.leaveVehicle()

        // Remove firework
        rocket.entity.remove()

        // Explosion effects
        world.createExplosion(loc, config.explosionPower.toFloat(), false, false)

        // Damage nearby entities
        for (entity in world.getNearbyEntities(loc, config.explosionPower * 2.0, config.explosionPower * 2.0, config.explosionPower * 2.0)) {
            if (entity == rocket.passenger) continue
            if (entity is Player) {
                entity.damage(config.damage, rocket.passenger)
            }
        }
    }

    // ==================== Utility ====================

    private fun isInAABB(loc: Location, cfg: Any): Boolean {
        return when (cfg) {
            is BlowerConfig -> loc.x in cfg.minX..cfg.maxX && loc.y in cfg.minY..cfg.maxY && loc.z in cfg.minZ..cfg.maxZ
            is LaserConfig -> loc.x in cfg.minX..cfg.maxX && loc.y in cfg.minY..cfg.maxY && loc.z in cfg.minZ..cfg.maxZ
            else -> false
        }
    }

    private fun spawnParticle(world: World, loc: Location, pc: ParticleConfig) {
        val particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(pc.key)) ?: return
        world.spawnParticle(particle, loc, pc.count, pc.offsetX, pc.offsetY, pc.offsetZ, pc.speed)
    }

    fun cleanupRockets() {
        for (rocket in activeRockets.values) {
            rocket.passenger?.leaveVehicle()
            rocket.entity.remove()
        }
        activeRockets.clear()
    }
}
