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
    var externalInjuryLoss = 0.0
    var internalInjuryLoss = 0.0

    if (dtype == EntityDamageEvent.DamageCause.VOID) {
        multiplier = 0.0
        externalInjuryLoss = 100.0
        internalInjuryLoss = 100.0
    } else if (dtype == EntityDamageEvent.DamageCause.STARVATION || dtype == EntityDamageEvent.DamageCause.THORNS || dtype == EntityDamageEvent.DamageCause.FIRE_TICK) {
        multiplier = 0.05
        externalInjuryLoss = 0.1
        internalInjuryLoss = 0.05
    } else if (dtype == EntityDamageEvent.DamageCause.MAGIC || dtype == EntityDamageEvent.DamageCause.FIRE) {
        multiplier = 0.2
        externalInjuryLoss = 0.3
        internalInjuryLoss = 0.5
    } else if (dtype == EntityDamageEvent.DamageCause.LAVA || dtype == EntityDamageEvent.DamageCause.DROWNING || dtype == EntityDamageEvent.DamageCause.SUFFOCATION || dtype == EntityDamageEvent.DamageCause.FALL) {
        multiplier = 1.0
        externalInjuryLoss = if (dtype == EntityDamageEvent.DamageCause.FALL) 2.0 else 1.0
        internalInjuryLoss = if (dtype == EntityDamageEvent.DamageCause.DROWNING || dtype == EntityDamageEvent.DamageCause.SUFFOCATION) 1.5 else 0.5
    } else if (dtype == EntityDamageEvent.DamageCause.WORLD_BORDER) {
        multiplier = 100.0
        externalInjuryLoss = 100.0
        internalInjuryLoss = 100.0
    } else if (dtype == EntityDamageEvent.DamageCause.ENTITY_ATTACK || dtype == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
        externalInjuryLoss = 0.5
        internalInjuryLoss = 0.2
    } else if (dtype == EntityDamageEvent.DamageCause.PROJECTILE) {
        externalInjuryLoss = 1.0
        internalInjuryLoss = 0.3
    }
    ChoTenDataHelper(player).removeSanity(multiplier)
    if (externalInjuryLoss > 0) ChoTenDataHelper(player).removeExternalInjury(externalInjuryLoss)
    if (internalInjuryLoss > 0) ChoTenDataHelper(player).removeInternalInjury(internalInjuryLoss)
}

