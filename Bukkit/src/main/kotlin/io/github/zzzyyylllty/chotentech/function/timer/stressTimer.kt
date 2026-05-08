package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 全局 BossBar 缓存，供定时器和退出事件共同访问 */
private val stressBossBars = ConcurrentHashMap<UUID, BossBar>()

@Awake(LifeCycle.ACTIVE)
fun stressTimer() {
    val interval = config.getLong("stress.interval", 40L)
    val maxStressGain = config.getDouble("stress.max-stress-gain", 2.0)
    val maxStressReduce = config.getDouble("stress.max-stress-reduce", 1.0)
    val sanityDrainThreshold = config.getDouble("stress.sanity-drain-threshold", 80.0)
    val sanityDrainAmount = config.getDouble("stress.sanity-drain-amount", 2.0)
    val baseStressReduce = config.getDouble("stress.base-stress-reduce", 2.0)
    val darkSanityDrain = config.getDouble("stress.dark-sanity-drain", 0.5)

    submitAsync(period = interval) {
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val data = playerDataMap[uuid.toString()] ?: continue

            val regions = playerRegions[uuid.toString()]
            val inBase = regions?.any { it.startsWith("base-") } == true

            if (inBase) {
                ChoTenDataHelper(player).removeStress(baseStressReduce)
                removeStressBossBar(player, uuid)
                continue
            }

            val lightLevel = player.location.block.lightLevel
            val stressChange = maxStressGain - (maxStressGain + maxStressReduce) * (lightLevel.toDouble() / 15.0)

            if (stressChange > 0) {
                ChoTenDataHelper(player).addStress(stressChange)
            } else {
                ChoTenDataHelper(player).removeStress(-stressChange)
            }

            if (data.stress >= sanityDrainThreshold) {
                ChoTenDataHelper(player).removeSanity(sanityDrainAmount)

                var bar = stressBossBars[uuid]
                if (bar == null) {
                    bar = BossBar.bossBar(
                        Component.text("§c⚠ 压力过高，精神正在流失..."),
                        1.0f,
                        BossBar.Color.RED,
                        BossBar.Overlay.PROGRESS
                    )
                    bar.addViewer(player)
                    stressBossBars[uuid] = bar
                }
                bar.progress((data.sanity / data.maxSanity).toFloat().coerceIn(0f, 1f))
            } else {
                removeStressBossBar(player, uuid)
            }

            // 极度黑暗环境额外掉精神
            if (lightLevel <= 3 && stressChange > 0) {
                ChoTenDataHelper(player).removeSanity(darkSanityDrain)
            }

            // 基地外压力低时精神缓慢回复至 40
            if (data.stress <= 10.0 && data.sanity < 40.0) {
                ChoTenDataHelper(player).addSanity(1.0)
            }
        }

        // 清理离线玩家的 BossBar
        stressBossBars.keys.removeIf { uuid ->
            val p = Bukkit.getPlayer(uuid)
            p == null || !p.isOnline
        }
    }
}

/** 移除玩家的压力 BossBar 并清理缓存 */
private fun removeStressBossBar(player: Player, uuid: UUID) {
    val bar = stressBossBars.remove(uuid)
    if (bar != null) {
        bar.removeViewer(player)
    }
}

/** 玩家退出时主动清理 BossBar，防止重连后残留 */
@SubscribeEvent
fun onPlayerQuitClearBossBar(event: PlayerQuitEvent) {
    val uuid = event.player.uniqueId
    stressBossBars.remove(uuid) // 直接清理缓存，定时器 cleanup 会处理 viewer
}
