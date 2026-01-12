package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
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
        multiplier = 100.0
    }
    ChoTenDataHelper(player).removeSanity(multiplier)
}