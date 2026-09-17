package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.getConsumableConfig
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import kotlin.math.floor
import org.bukkit.GameMode
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

@SubscribeEvent
fun onInstantConsumableInteract(event: PlayerInteractEvent) {
    if (event.hand != EquipmentSlot.HAND) return
    if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = player.inventory.itemInMainHand
    if (item.type.isAir) return

    val sertralineId = Sertraline.api().getId(item) ?: return
    val consumable = getConsumableConfig(sertralineId) ?: return
    if (!consumable.instant) return

    event.isCancelled = true

    // 倒地状态禁止使用医疗物品
    if (consumable.medical && BleedoutManager.isDowned(player.uniqueId)) {
        player.sendActionBar("<#E08A98>倒地状态无法使用医疗物品".toComponent())
        return
    }

    consumable.apply(player)

    val invTag = item.getItemTag() ?: return
    val invData = invTag["sertraline_data"]?.asCompound()
    if (invData?.get("durability") != null) {
        val dur = invData["durability"]?.asInt() ?: 1
        val newDur = dur - 1.0

        if (newDur > 0) {
            if (item.amount > 1) {
                val used = item.clone()
                used.amount = 1
                val usedTag = used.getItemTag() ?: return
                usedTag.putDeep("sertraline_data.durability", floor(newDur))
                usedTag.saveTo(used)
                used.rebuildDisplay(player)
                player.inventory.addItem(used).values.forEach {
                    player.world.dropItem(player.location, it)
                }
                item.amount--
            } else {
                invTag.putDeep("sertraline_data.durability", floor(newDur))
                invTag.saveTo(item)
                item.rebuildDisplay(player)
            }
        } else {
            item.amount--
        }
    } else {
        item.amount--
    }
}
