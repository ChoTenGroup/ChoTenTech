package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/** 猎犬病毒最后触发时间记录 */
private val houndVirusLastProc = ConcurrentHashMap<String, Long>()

/** 骨折 modifier key，用于清理 */
private val FRACTURE_KEY = NamespacedKey("chotentech", "fracture")

/** 当前有骨折效果的玩家 */
private val fractureActive = ConcurrentHashMap<String, Boolean>()

private var statusEffectTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun statusEffectTimer() {
    statusEffectTask = submit(period = 20) {
        val fractureCleanup = mutableListOf<String>()
        val pendingSyncEffects = mutableMapOf<String, List<PlayerStatusEffect>>()

        for (player in Bukkit.getOnlinePlayers()) {
            val playerId = player.uniqueId.toString()
            val data = playerDataMap[playerId] ?: continue
            val currentEffects = data.statusEffects
            if (currentEffects.isEmpty()) {
                if (fractureActive.remove(playerId) != null) fractureCleanup.add(playerId)
                continue
            }

            val helper = ChoTenDataHelper(player)
            val remaining = mutableListOf<PlayerStatusEffect>()
            val syncEffects = mutableListOf<PlayerStatusEffect>()

            for (effect in currentEffects) {
                effect.remainingDuration -= 1000L

                if (effect.remainingDuration <= 0) {
                    if (effect.type == HOUND_VIRUS) houndVirusLastProc.remove(playerId)
                    continue
                }

                when (effect.type) {
                    SANITY_REGEN -> helper.addSanity(effect.level * 0.2)
                    SANITY_DRAIN -> helper.removeSanity(effect.level * 0.5)
                    STAMINA_REGEN -> helper.addStamina(effect.level * 1.0)
                    STRESS_RESISTANCE -> helper.removeStress(effect.level * 1.0)
                    STRESS_IMMUNITY -> helper.setStress(0.0)
                    HOUND_VIRUS -> {
                        if (!helper.hasStatusEffect(IMMUNITY)) {
                            val now = System.currentTimeMillis()
                            val lastProc = houndVirusLastProc[playerId]
                            if (lastProc == null) {
                                houndVirusLastProc[playerId] = now
                            } else if (now - lastProc >= 10000) {
                                houndVirusLastProc[playerId] = now
                                syncEffects.add(effect)
                            }
                        }
                    }
                }

                if (effect.type in listOf(FRACTURE, PAIN, MUSCLE_WEAKNESS)) {
                    syncEffects.add(effect)
                }

                remaining.add(effect)
            }

            data.statusEffects = remaining

            val hasFracture = remaining.any { it.type == FRACTURE }
            val hadFracture = fractureActive[playerId] == true
            fractureActive[playerId] = hasFracture
            if (hadFracture && !hasFracture) fractureCleanup.add(playerId)

            if (syncEffects.isNotEmpty()) {
                pendingSyncEffects[playerId] = syncEffects.toList()
            }
        }

        // Single main-thread submit to handle all fracture cleanup + sync effects
        if (pendingSyncEffects.isNotEmpty() || fractureCleanup.isNotEmpty()) {
            submit {
                // Fracture cleanup
                for (cleanupId in fractureCleanup) {
                    val p = Bukkit.getPlayer(java.util.UUID.fromString(cleanupId)) ?: continue
                    val attr = p.getAttribute(Attribute.MOVEMENT_SPEED) ?: continue
                    attr.modifiers.filter { it.key == FRACTURE_KEY }.forEach { attr.removeModifier(it) }
                }

                // Sync effects per player
                for ((playerId, effects) in pendingSyncEffects) {
                    val player = Bukkit.getPlayer(java.util.UUID.fromString(playerId)) ?: continue
                    val helper = ChoTenDataHelper(player)

                    for (effect in effects) {
                        when (effect.type) {
                            FRACTURE -> {
                                val attr = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: continue
                                attr.modifiers.filter { it.key == FRACTURE_KEY }.forEach { attr.removeModifier(it) }
                                val reduction = 1.0 - 0.7.pow(effect.level)
                                attr.addModifier(AttributeModifier(
                                    FRACTURE_KEY,
                                    -reduction,
                                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
                                ))
                            }
                            HOUND_VIRUS -> {
                                player.damage(effect.level.toDouble() * effect.level.toDouble())
                                helper.removeInternalInjury(effect.level.toDouble())
                            }
                            PAIN -> {
                                player.addPotionEffect(
                                    PotionEffect(PotionEffectType.SLOWNESS, 60, (effect.level - 1).coerceAtLeast(0))
                                )
                                player.addPotionEffect(
                                    PotionEffect(PotionEffectType.MINING_FATIGUE, 60, (effect.level - 1).coerceAtLeast(0))
                                )
                            }
                            MUSCLE_WEAKNESS -> {
                                player.addPotionEffect(
                                    PotionEffect(PotionEffectType.WEAKNESS, 60, (effect.level - 1).coerceAtLeast(0))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelStatusEffectTimer() {
    statusEffectTask?.cancel()
    houndVirusLastProc.clear()
    fractureActive.clear()
}
