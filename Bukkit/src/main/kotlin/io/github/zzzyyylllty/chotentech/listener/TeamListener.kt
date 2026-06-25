package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.team.TeamManager
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

object TeamListener {

    /**
     * Reduce damage between teammates by 50%.
     */
    @SubscribeEvent
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.isCancelled) return

        val damager = when (val d = event.damager) {
            is Player -> d
            is Projectile -> {
                val shooter = d.shooter
                if (shooter is Player) shooter else return
            }
            else -> return
        }

        val victim = event.entity
        if (victim !is Player) return

        // Check if both are in the same team
        val damagerTeam = TeamManager.getTeam(damager.uniqueId) ?: return
        val victimTeam = TeamManager.getTeam(victim.uniqueId) ?: return
        if (damagerTeam !== victimTeam) return

        // Reduce damage by 50%
        event.damage *= 0.5
    }

    /**
     * Auto-disband team when the leader quits.
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        TeamManager.handleLeaderQuit(player.uniqueId)
    }
}
