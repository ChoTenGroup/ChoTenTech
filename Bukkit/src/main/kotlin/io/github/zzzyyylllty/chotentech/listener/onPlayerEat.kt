package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.NutritionManager
import io.github.zzzyyylllty.chotentech.data.getConsumableConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.sertraline.Sertraline
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.GameMode
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.event.player.PlayerItemConsumeEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

@SubscribeEvent
fun onPlayerEat(event: PlayerItemConsumeEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item
    val tag = item.getItemTag()

    // ===== Check for "restore" compound in ItemTag =====
    val restoreTag = tag?.get("restore")?.asCompound()
    if (restoreTag != null) {
        val data = player.getData() ?: return
        var nutritionChanged = false

        // hunger
        restoreTag["hunger"]?.asDouble()?.let { h ->
            data.customHunger = minOf(data.customHunger + h, data.maxCustomHunger)
        }
        // saturation
        restoreTag["saturation"]?.asDouble()?.let { s ->
            data.customSaturation = minOf(data.customSaturation + s, data.maxCustomHunger, data.customHunger)
        }
        // san (sanity, can be negative)
        restoreTag["san"]?.asDouble()?.let { san ->
            data.sanity = (data.sanity + san).coerceIn(0.0, data.effectiveMaxSanity)
        }
        // nutrition compound
        restoreTag["nutrition"]?.asCompound()?.let { n ->
            n["grain"]?.asDouble()?.let { data.grain = (data.grain + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["vitamin"]?.asDouble()?.let { data.vitamin = (data.vitamin + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["protein"]?.asDouble()?.let { data.protein = (data.protein + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["fat"]?.asDouble()?.let { data.fat = (data.fat + it).coerceIn(0.0, 100.0); nutritionChanged = true }
        }
        // potion effects
        restoreTag["effects"]?.asList()?.forEach { entry ->
            val compound = entry.asCompound() ?: return@forEach
            val effectId = compound["effect"]?.asString() ?: return@forEach
            val tick = compound["tick"]?.asInt() ?: 40
            val level = (compound["level"]?.asInt() ?: 1) - 1
            val type = PotionEffectType.getByName(effectId) ?: return@forEach
            player.addPotionEffect(PotionEffect(type, tick, level, true, true, true))
        }

        if (nutritionChanged) NutritionManager.sync(player)
        return
    }

    // ===== Check consumable config by sertraline_id =====
    val sertralineId = Sertraline.api().getId(item)
    if (sertralineId != null) {
        val consumable = getConsumableConfig(sertralineId)
        if (consumable != null) {
            consumable.apply(player)
            return
        }
    }

    val data = player.getData() ?: return

    // Check NBT for custom hunger/saturation values
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
}
