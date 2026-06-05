package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onPlayerAttack(event: EntityDamageByEntityEvent) {
    val player = event.damager as? Player ?: return
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    // DMZ PvP check
    val target = event.entity as? LivingEntity ?: return
    if (target is Player && target !== player) {
        val level = player.getPlayerLevel()
        if (level?.dmz == true) {
            val attackerData = playerDataMap[player.uniqueId.toString()]
            val victimData = playerDataMap[target.uniqueId.toString()]
            val attackerPvp = attackerData?.pvpEnabled == true
            val victimPvp = victimData?.pvpEnabled == true
            if (!attackerPvp || !victimPvp) {
                event.isCancelled = true
                if (!attackerPvp) {
                    player.sendComponent("<red>你处于非军事区，需要开启PVP模式才能攻击玩家。使用 <yellow>/pvp <red>开启")
                } else {
                    player.sendComponent("<red>对方未开启PVP模式，无法攻击。")
                }
                return
            }
        }
    }

    if (target === player) return

    // Add consumption for attacking
    playerDataMap[player.uniqueId.toString()]?.let { it.consumption += 5.0 }

    val damage = event.finalDamage
    val currentHealth = target.health
    val maxHealth = target.maxHealth
    val ratio = (currentHealth / maxHealth).coerceIn(0.0, 1.0)

    val targetName = when (target) {
        is Player -> target.name
        else -> target.customName()?.let { mmUtil.serialize(it) } ?: target.type.name
    }

    val color = healthColor(ratio)

    player.sendActionBar(
        MiniMessage.miniMessage().deserialize(
            "<white>$targetName <reset><$color>${currentHealth.toInt()}<dark_gray>/${maxHealth.toInt()} <gray>(<yellow>-${"%.1f".format(damage)}</yellow>)</gray>"
        )
    )
}

private fun healthColor(ratio: Double): String {
    val r = if (ratio > 0.5) ((1 - ratio) * 2 * 255).toInt() else 255
    val g = if (ratio < 0.5) (ratio * 2 * 255).toInt() else 255
    return "#%02X%02X00".format(r.coerceIn(0, 255), g.coerceIn(0, 255))
}
