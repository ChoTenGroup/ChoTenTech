package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import org.bukkit.GameMode
import org.bukkit.damage.DamageType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun entityDeathEvent(event: EntityDeathEvent) {
    val entity = event.entity
    val entityName = entity.name.uppercase() ?: return
    if (entityName.contains("PAINT") || entityName.contains("TEXT")) {
        if (event.damageSource.damageType == DamageType.MAGIC)
            event.isCancelled = true
    }

    // Kill tracking
    val killer = entity.killer ?: return
    if (!killer.isOnline) return
    val data = playerDataMap[killer.uniqueId.toString()] ?: return
    val shared = ProfileManager.getSharedData(killer.uniqueId.toString())

    if (entity is Player) {
        data.totalPlayerKills++
        shared.totalPlayerKills++
    } else {
        data.totalMobKills++
        shared.totalMobKills++
    }
}

@SubscribeEvent
fun onPlayerDamageWithDisabledEntities(event: EntityDamageByEntityEvent) {
    val player: Player = event.damager as? Player ?: return
    val entityName = event.entity.name.uppercase() ?: return

    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    if (entityName.contains("PAINT") || entityName.contains("TEXT")) {
        event.isCancelled = true
    }
}
