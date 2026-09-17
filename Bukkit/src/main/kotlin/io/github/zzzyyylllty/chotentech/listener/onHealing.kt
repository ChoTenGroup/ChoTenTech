package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.getHealConfigFromYaml
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.HealingManager
import io.github.zzzyyylllty.chotentech.function.HealingPhase
import io.github.zzzyyylllty.chotentech.function.PostReason
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.GameMode
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
//    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item ?: return
    val sertralineId = Sertraline.api().getId(item) ?: return
    val tag = item.getItemTag() ?: return
    val sData = tag["sertraline_data"]?.asCompound() ?: return
    val durability = sData["durability"]?.let {
        it.asDouble() ?: it.asString()?.toDoubleOrNull()
    } ?: return
    val maxDurability = sData["maxdurability"]?.let {
        it.asDouble() ?: it.asString()?.toDoubleOrNull()
    } ?: durability
    val config = getHealConfigFromYaml(sertralineId, durability, maxDurability) ?: return

    event.isCancelled = true

    // 倒地状态禁止使用医疗物品
    if (BleedoutManager.isDowned(player.uniqueId)) {
        player.sendActionBar("<#E08A98>倒地状态无法使用医疗物品".toComponent())
        return
    }

    // Pitch check: looking too far up = can't enter healing
    if (player.pitch < -80f) {
        player.sendActionBar("<#E08A98>请先低头再进入治疗模式".toComponent())
        return
    }

    HealingManager.enter(player, config, item)
}

// ======= Cancel conditions =======

@SubscribeEvent
fun onHealingMove(event: PlayerMoveEvent) {
    val player = event.player
    val state = HealingManager.getState(player.uniqueId) ?: return
    if (state.phase == HealingPhase.POST) return

    // PRE phase: only pitch-look-up cancels
    if (state.phase == HealingPhase.PRE) {
        if (player.pitch < -80f) {
            HealingManager.cancel(player, PostReason.CANCELLED)
        }
        return
    }

    val cancel = state.config.cancelHeal

    // Pitch > 80 (looking up) cancels
    if (player.pitch < -80f) {
        HealingManager.cancel(player, PostReason.CANCELLED)
        return
    }

    // Walk cancel
    if (cancel.contains("walk") && event.hasExplicitlyChangedPosition()) {
        HealingManager.cancel(player, PostReason.CANCELLED)
        return
    }

    // Sprint cancel
    if (cancel.contains("sprint") && player.isSprinting) {
        HealingManager.cancel(player, PostReason.CANCELLED)
        return
    }

    // Jump cancel: upward movement while not on ground
    if (cancel.contains("jump") && event.to.y > event.from.y && !player.isOnGround) {
        HealingManager.cancel(player, PostReason.CANCELLED)
        return
    }
}

@SubscribeEvent
fun onHealingAttacked(event: EntityDamageByEntityEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    val state = HealingManager.getState(player.uniqueId) ?: return
    if (state.phase == HealingPhase.POST) return
    if (!state.config.cancelHeal.contains("attack")) return

    if (event.damager is LivingEntity) {
        HealingManager.cancel(player, PostReason.CANCELLED)
    }
}



@SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onHealingFallDamage(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    val state = HealingManager.getState(player.uniqueId) ?: return
    if (state.phase == HealingPhase.POST) return
    if (!state.config.cancelHeal.contains("fall")) return
    if (event.cause != EntityDamageEvent.DamageCause.FALL) return

    HealingManager.cancel(player, PostReason.CANCELLED)
}

@SubscribeEvent
fun onHealingSwitchItem(event: PlayerItemHeldEvent) {
    val player = event.player
    if (!HealingManager.isInHealingOrPost(player.uniqueId)) return
    event.isCancelled = true
    player.sendActionBar("<#E08A98>治疗中，无法切换物品".toComponent())
}
