package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.CosmeticSlot
import io.github.zzzyyylllty.chotentech.data.cosmeticConfigs
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.toComponent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

private val orbitAngles = ConcurrentHashMap<String, Double>()

/**
 * Aura particle timer — runs every 4 ticks for smooth particle effects.
 */
private fun startAuraTimer() {
    submit(period = 4) {
        for (player in Bukkit.getOnlinePlayers()) {
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val auraId = data.cosmetics[CosmeticSlot.AURA.name] ?: continue
            val cosmetic = cosmeticConfigs[auraId] ?: continue
            val particleName = cosmetic.particle ?: continue
            if (particleName.isBlank()) continue

            val particle = runCatching { Particle.valueOf(particleName.uppercase()) }.getOrNull() ?: continue

            val loc = player.location.clone()
            val count = cosmetic.particleCount
            val speed = cosmetic.particleSpeed.toDouble()
            val radius = cosmetic.particleRadius
            val height = cosmetic.particleHeight

            try {
                when (cosmetic.particlePattern) {
                    "orbit" -> {
                        val uuid = player.uniqueId.toString()
                        val angle = orbitAngles.getOrDefault(uuid, 0.0)
                        val nextAngle = angle + speed * 0.5
                        orbitAngles[uuid] = if (nextAngle > Math.PI * 2) nextAngle - Math.PI * 2 else nextAngle
                        for (i in 0 until count) {
                            val a = angle + (Math.PI * 2 * i / count)
                            val x = loc.x + cos(a) * radius
                            val z = loc.z + sin(a) * radius
                            player.world.spawnParticle(particle, x, loc.y + height, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                    "halo" -> {
                        for (i in 0 until count) {
                            val a = Math.PI * 2 * i / count
                            val x = loc.x + cos(a) * radius
                            val z = loc.z + sin(a) * radius
                            player.world.spawnParticle(particle, x, loc.y + height, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                    "spiral" -> {
                        val uuid = player.uniqueId.toString()
                        val angle = orbitAngles.getOrDefault(uuid, 0.0)
                        val nextAngle = angle + speed * 0.4
                        orbitAngles[uuid] = if (nextAngle > Math.PI * 2) nextAngle - Math.PI * 2 else nextAngle
                        for (i in 0 until count) {
                            val a = angle + (Math.PI * 2 * i / count)
                            val x = loc.x + cos(a) * radius
                            val z = loc.z + sin(a) * radius
                            val yOff = height + sin(angle * 3 + i) * 0.5
                            player.world.spawnParticle(particle, x, loc.y + yOff, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                    "wave" -> {
                        val uuid = player.uniqueId.toString()
                        val angle = orbitAngles.getOrDefault(uuid, 0.0)
                        val nextAngle = angle + speed * 0.3
                        orbitAngles[uuid] = if (nextAngle > Math.PI * 2) nextAngle - Math.PI * 2 else nextAngle
                        for (i in 0 until count) {
                            val a = Math.PI * 2 * i / count
                            val x = loc.x + cos(a) * radius
                            val z = loc.z + sin(a) * radius
                            val yOff = height + sin(angle + a * 2) * 0.4
                            player.world.spawnParticle(particle, x, loc.y + yOff, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                    else -> {
                        // scatter (default)
                        player.world.spawnParticle(particle, loc.add(0.0, height, 0.0), count, radius, 0.1, radius, speed)
                    }
                }
            } catch (_: Exception) {
                // skip particles that require extra data
            }
        }
    }
}

@SubscribeEvent
fun onPlayerMoveForFootstep(event: PlayerMoveEvent) {
    val player = event.player
    val from = event.from
    val to = event.to ?: return

    // only when actually moving on ground
    if (from.x == to.x && from.z == to.z) return
    if (!player.isOnGround()) return

    val data = playerDataMap[player.uniqueId.toString()] ?: return
    val footstepId = data.cosmetics[CosmeticSlot.FOOTSTEP.name] ?: return
    val cosmetic = cosmeticConfigs[footstepId] ?: return
    val particleName = cosmetic.particle ?: return
    if (particleName.isBlank()) return

    val particle = runCatching { Particle.valueOf(particleName.uppercase()) }.getOrNull() ?: return

    val loc = player.location.clone().add(0.0, 0.05, 0.0)
    try {
        player.world.spawnParticle(particle, loc, cosmetic.particleCount, 0.2, 0.0, 0.2, cosmetic.particleSpeed.toDouble())
    } catch (_: Exception) { }
}

@SubscribeEvent
fun onEntityDeathForCosmetics(event: EntityDeathEvent) {
    val entity = event.entity
    val killer = entity.killer ?: return
    if (!killer.isOnline) return

    val data = playerDataMap[killer.uniqueId.toString()] ?: return
    val deathLoc = entity.location

    // Kill particle
    val particleId = data.cosmetics[CosmeticSlot.KILL_PARTICLE.name]
    if (particleId != null) {
        val cosmetic = cosmeticConfigs[particleId]
        if (cosmetic != null) {
            val particleName = cosmetic.particle
            if (!particleName.isNullOrBlank()) {
                val particle = runCatching { Particle.valueOf(particleName.uppercase()) }.getOrNull()
                if (particle != null) {
                    try {
                        killer.world.spawnParticle(particle, deathLoc, cosmetic.particleCount, 0.5, 0.5, 0.5, cosmetic.particleSpeed.toDouble())
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // Kill message
    val msgId = data.cosmetics[CosmeticSlot.KILL_MESSAGE.name]
    if (msgId != null) {
        val cosmetic = cosmeticConfigs[msgId]
        if (cosmetic != null) {
            val format = cosmetic.messageFormat
            if (!format.isNullOrBlank()) {
                val targetName = if (entity is Player) entity.name else entity.getCustomName() ?: entity.type.name
                val message = format
                    .replace("%player%", killer.name)
                    .replace("%target%", targetName)
                    .legacyToMiniMessage()
                killer.sendMessage(message.toComponent())
            }
        }
    }
}

/**
 * Start the aura timer on plugin active.
 */
fun initCosmeticEffects() {
    startAuraTimer()
}
