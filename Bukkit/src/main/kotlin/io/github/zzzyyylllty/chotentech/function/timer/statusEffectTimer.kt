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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/** 猎犬病毒最后触发时间记录 */
private val houndVirusLastProc = ConcurrentHashMap<String, Long>()

/** 骨折 modifier key，用于清理 */
private val FRACTURE_KEY = NamespacedKey("chotentech", "fracture")

/** 当前有骨折效果的玩家 */
private val fractureActive = ConcurrentHashMap<String, Boolean>()

@Awake(LifeCycle.ACTIVE)
fun statusEffectTimer() {
    submitAsync(period = 20) {
        val fractureCleanup = mutableListOf<String>()

        for (player in Bukkit.getOnlinePlayers()) {
            val playerId = player.uniqueId.toString()
            val data = playerDataMap[playerId] ?: continue
            val currentEffects = data.statusEffects
            if (currentEffects.isEmpty()) {
                // 无效果 → 检查是否需要清理骨折
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

                // 需要同步线程的操作
                if (effect.type in listOf(FRACTURE, PAIN, MUSCLE_WEAKNESS)) {
                    syncEffects.add(effect)
                }

                remaining.add(effect)
            }

            data.statusEffects = remaining

            // 追踪骨折状态以便清理
            val hasFracture = remaining.any { it.type == FRACTURE }
            val hadFracture = fractureActive[playerId] == true
            fractureActive[playerId] = hasFracture
            if (hadFracture && !hasFracture) fractureCleanup.add(playerId)

            if (syncEffects.isNotEmpty()) {
                submit {
                    // 先清理已过期的骨折 modifier
                    for (cleanupId in fractureCleanup) {
                        val p = Bukkit.getPlayer(java.util.UUID.fromString(cleanupId)) ?: continue
                        val attr = p.getAttribute(Attribute.MOVEMENT_SPEED) ?: continue
                        attr.modifiers.filter { it.key == FRACTURE_KEY }.forEach { attr.removeModifier(it) }
                    }
                    fractureCleanup.clear()

                    for (effect in syncEffects) {
                        when (effect.type) {
                            FRACTURE -> {
                                val attr = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: continue
                                // 移除旧的骨折 modifier
                                attr.modifiers.filter { it.key == FRACTURE_KEY }.forEach { attr.removeModifier(it) }
                                // 叠加计算：L1=30% L2=51% L3=65.7% ...
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
            } else if (fractureCleanup.isNotEmpty()) {
                // 仅有清理任务时也需要执行 submit
                submit {
                    for (cleanupId in fractureCleanup) {
                        val p = Bukkit.getPlayer(java.util.UUID.fromString(cleanupId)) ?: continue
                        val attr = p.getAttribute(Attribute.MOVEMENT_SPEED) ?: continue
                        attr.modifiers.filter { it.key == FRACTURE_KEY }.forEach { attr.removeModifier(it) }
                    }
                    fractureCleanup.clear()
                }
            }
        }
    }
}
