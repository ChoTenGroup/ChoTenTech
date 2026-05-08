package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onPlayerAttack(event: EntityDamageByEntityEvent) {
    val player = event.damager as? Player ?: return
    val target = event.entity as? LivingEntity ?: return

    if (target === player) return

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
