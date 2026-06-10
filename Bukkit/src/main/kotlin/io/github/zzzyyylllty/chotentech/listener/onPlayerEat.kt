package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.getData
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

@SubscribeEvent
fun onPlayerEat(event: PlayerItemConsumeEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item
    event.isCancelled = true

    val data = player.getData() ?: return

    // Check NBT for custom hunger/saturation values
    val tag = item.getItemTag()
    val customHunger = tag?.get("hunger")?.asDouble()
    val customSaturation = tag?.get("saturation")?.asDouble()

    if (customHunger != null || customSaturation != null) {
        if (customHunger != null) {
            data.customHunger = minOf(data.customHunger + customHunger, data.maxCustomHunger)
        }
        if (customSaturation != null) {
            data.customSaturation = minOf(data.customSaturation + customSaturation, data.maxCustomHunger, data.customHunger)
        }
    } else {
        // Fall back to vanilla food component
        val food = item.getData(DataComponentTypes.FOOD) ?: return
        val nutrition = food.nutrition()
        val saturation = food.saturation().toDouble()
        data.customHunger = minOf(data.customHunger + nutrition * 5.0, data.maxCustomHunger)
        data.customSaturation = minOf(data.customSaturation + saturation * 5.0, data.maxCustomHunger, data.customHunger)
    }

    // Remove the consumed item from hand
    item.amount -= 1
}
