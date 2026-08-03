package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.function.WeightManager
import org.bukkit.GameMode
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerStatisticIncrementEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onPlayerJump(event: PlayerStatisticIncrementEvent) {
    if (event.statistic != Statistic.JUMP) return
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val data = playerDataMap[player.uniqueId.toString()] ?: return

    // 负重体力消耗倍率：0-20kg 不变，之后每 kg +2.5%
    val weightMult = WeightManager.getCachedWeight(player.uniqueId)
        ?.let { w -> if (w > 20) 1.0 + (w - 20) * 0.025 else 1.0 } ?: 1.0

    data.consumption += 5.0
    data.stamina = maxOf(data.stamina - 3.0 * weightMult, 0.0)
}
