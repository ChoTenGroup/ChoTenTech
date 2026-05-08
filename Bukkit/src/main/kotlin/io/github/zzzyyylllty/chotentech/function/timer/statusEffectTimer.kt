package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.*
import org.bukkit.Bukkit
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync

@Awake(LifeCycle.ACTIVE)
fun statusEffectTimer() {
    submitAsync(period = 20) {
        for (player in Bukkit.getOnlinePlayers()) {
            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            val currentEffects = data.statusEffects
            if (currentEffects.isEmpty()) continue

            val helper = ChoTenDataHelper(player)
            val remaining = mutableListOf<PlayerStatusEffect>()
            val syncEffects = mutableListOf<PlayerStatusEffect>()

            for (effect in currentEffects) {
                effect.remainingDuration -= 1000L

                if (effect.remainingDuration <= 0) continue

                when (effect.type) {
                    SANITY_REGEN -> helper.addSanity(effect.level * 0.5)
                    SANITY_DRAIN -> helper.removeSanity(effect.level * 0.5)
                    STRESS_RESISTANCE -> helper.removeStress(effect.level * 1.0)
                    STRESS_IMMUNITY -> helper.setStress(0.0)
                }

                // 需要同步线程的操作
                if (effect.type in listOf(BLEEDING, HEAVY_BLEEDING, PAIN, MUSCLE_WEAKNESS)) {
                    syncEffects.add(effect)
                }

                remaining.add(effect)
            }

            data.statusEffects = remaining

            if (syncEffects.isNotEmpty()) {
                submit {
                    for (effect in syncEffects) {
                        when (effect.type) {
                            BLEEDING -> player.damage(effect.level * 0.5)
                            HEAVY_BLEEDING -> player.damage(effect.level * 1.0)
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
