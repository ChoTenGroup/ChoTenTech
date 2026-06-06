package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
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
    data.consumption += 5.0
    data.stamina = maxOf(data.stamina - 3.0, 0.0)
}
