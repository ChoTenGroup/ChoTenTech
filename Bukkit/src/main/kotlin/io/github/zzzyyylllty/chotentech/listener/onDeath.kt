package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.timer.antiOverflow
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.SubscribeEvent
import kotlin.collections.get

@SubscribeEvent
fun onPlayerDeath(event: PlayerDeathEvent) {

    val k = event.player.uniqueId.toString()

    ChoTenTech.dataMap[k]?.stress = 5.0
    ChoTenTech.dataMap[k]?.sanity = 40.0
    ChoTenTech.dataMap[k]?.darkness = 15.0
}