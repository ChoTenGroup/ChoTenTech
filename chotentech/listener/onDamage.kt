package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.function.timer.antiOverflow
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import taboolib.common.platform.event.SubscribeEvent
import kotlin.collections.get

@SubscribeEvent
fun onPlayerDamage(event: EntityDamageEvent) {

    if (event.entity !is Player) return
    val player: Player = event.entity as Player

    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val data = dataMap[player.uniqueId.toString()] ?: return
    val dtype = event.cause
    var multiplier = 0.1

    if (dtype == EntityDamageEvent.DamageCause.VOID) {
        multiplier = 0.0
    } else if (dtype == EntityDamageEvent.DamageCause.STARVATION || dtype == EntityDamageEvent.DamageCause.THORNS || dtype == EntityDamageEvent.DamageCause.FIRE_TICK) {
        multiplier = 0.05
    } else if (dtype == EntityDamageEvent.DamageCause.MAGIC || dtype == EntityDamageEvent.DamageCause.FIRE) {
        multiplier = 0.2
    } else if (dtype == EntityDamageEvent.DamageCause.LAVA || dtype == EntityDamageEvent.DamageCause.DROWNING || dtype == EntityDamageEvent.DamageCause.SUFFOCATION || dtype == EntityDamageEvent.DamageCause.FALL) {
        multiplier = 1.0
    } else if (dtype == EntityDamageEvent.DamageCause.WORLD_BORDER) {
        multiplier = 5.0
    }
    dataMap[player.uniqueId.toString()]?.sanity = (data.sanity - multiplier)
    if (data.sanity <= 60) {
        dataMap[player.uniqueId.toString()]?.stress =
            (data.stress - antiOverflow(event.finalDamage * (0.6 - data.sanity / 100), 8.0))
    }
}