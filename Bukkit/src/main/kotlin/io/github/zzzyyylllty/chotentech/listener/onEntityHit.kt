package io.github.zzzyyylllty.chotentech.listener

import org.bukkit.GameMode
import org.bukkit.damage.DamageType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import taboolib.common.platform.event.SubscribeEvent

fun entityDeathEvent(e: EntityDeathEvent) {
    if (e.damageSource.damageType == DamageType.MAGIC) e.isCancelled = true
}


@SubscribeEvent
fun onPlayerDamageWithDisabledEntities(event: EntityDamageByEntityEvent) {


    val player: Player = event.damager as? Player ?: return
    val entity = event.entity.name.uppercase() ?: return

    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    if (entity.contains("PAINT") || entity.contains("TEXT")) {
        event.isCancelled = true
    }
}

