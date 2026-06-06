package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.parseHealConfig
import io.github.zzzyyylllty.chotentech.function.HealingManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

@SubscribeEvent
fun onUseMedicalItem(event: PlayerInteractEvent) {
    if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
    if (event.hand != EquipmentSlot.HAND) return

    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item ?: return
    val tag = item.getItemTag() ?: return
    val config = tag.parseHealConfig() ?: return

    event.isCancelled = true

    // Pitch check: looking too far up = can't enter healing
    if (player.pitch < -80f) {
        player.sendActionBar("<red>请先低头再进入治疗模式".toComponent())
        return
    }

    // Consume item: reduce tag durability, remove only if depleted
    if (player.gameMode != GameMode.CREATIVE) {
        val durability = tag["heal"]?.asCompound()?.get("durability")?.asDouble() ?: 0.0
        if (durability > 1.0) {
            tag["heal"]?.asCompound()?.put("durability", durability - 1.0)
            tag.saveTo(item)
        } else {
            item.amount = item.amount - 1
        }
    }

    HealingManager.enter(player, config)
}

// ======= Cancel conditions =======

@SubscribeEvent
fun onHealingMove(event: PlayerMoveEvent) {
    val player = event.player
    val state = HealingManager.getState(player.uniqueId) ?: return
    val cancel = state.config.cancelHeal

    // Pitch > 80 (looking up) exits healing
    if (player.pitch < -80f) {
        HealingManager.exit(player)
        player.sendActionBar("<green>已退出治疗模式".toComponent())
        return
    }

    // Walk cancel
    if (cancel.contains("walk") && event.hasExplicitlyChangedPosition()) {
        HealingManager.exit(player)
        player.sendActionBar("<red>移动导致治疗中断".toComponent())
        return
    }

    // Sprint cancel
    if (cancel.contains("sprint") && player.isSprinting) {
        HealingManager.exit(player)
        player.sendActionBar("<red>疾跑导致治疗中断".toComponent())
        return
    }



    // Jump cancel: upward movement while not on ground
    if (cancel.contains("jump") && event.to.y > event.from.y && !player.isOnGround) {
        HealingManager.exit(player)
        player.sendActionBar("<red>跳跃导致治疗中断".toComponent())
        return
    }
}

@SubscribeEvent
fun onHealingAttacked(event: EntityDamageByEntityEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    val state = HealingManager.getState(player.uniqueId) ?: return
    if (!state.config.cancelHeal.contains("attack")) return

    // Only cancel if attacked by a living entity (not poison/wither/etc)
    if (event.damager is LivingEntity) {
        HealingManager.exit(player)
        player.sendActionBar("<red>受到攻击，治疗中断".toComponent())
    }
}

@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onHealingFallDamage(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    val state = HealingManager.getState(player.uniqueId) ?: return
    if (!state.config.cancelHeal.contains("fall")) return
    if (event.cause != EntityDamageEvent.DamageCause.FALL) return

    HealingManager.exit(player)
    player.sendActionBar("<red>摔落导致治疗中断".toComponent())
}

@SubscribeEvent
fun onHealingSwitchItem(event: PlayerItemHeldEvent) {
    val player = event.player
    if (!HealingManager.isHealing(player.uniqueId)) return
    HealingManager.exit(player)
    player.sendActionBar("<green>已切换物品，退出治疗模式".toComponent())
}
