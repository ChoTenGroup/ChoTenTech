package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.SubscribeEvent
import kotlin.collections.get

@SubscribeEvent
fun onPlayerDeath(event: PlayerDeathEvent) {

    val player = event.player
    ChoTenDataHelper(player).resetSanity()
    ChoTenDataHelper(player).resetDarkness()
}