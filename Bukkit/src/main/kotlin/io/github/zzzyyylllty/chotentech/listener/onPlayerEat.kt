package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.getData
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onPlayerEat(event: PlayerItemConsumeEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item
    val food = item.getData(DataComponentTypes.FOOD) ?: return

    event.isCancelled = true

    val nutrition = food.nutrition()
    val saturation = food.saturation().toDouble()

    val data = player.getData() ?: return
    data.customHunger = minOf(data.customHunger + nutrition * 5.0, data.maxCustomHunger)
    data.customSaturation = minOf(data.customSaturation + saturation * 5.0, data.maxCustomHunger, data.customHunger)

    // Remove the consumed item from hand
    val consumedItem = item.clone()
    consumedItem.amount = 1
    player.inventory.removeItem(consumedItem)
}
