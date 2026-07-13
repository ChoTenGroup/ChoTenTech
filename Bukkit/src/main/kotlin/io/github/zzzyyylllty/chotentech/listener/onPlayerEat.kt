package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.NutritionManager
import io.github.zzzyyylllty.chotentech.data.getConsumableConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlin.math.floor
import org.bukkit.GameMode
import org.bukkit.Material
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
        // health (can be negative — damages player)
        restoreTag["health"]?.asDouble()?.let { h ->
            if (h >= 0) {
                player.health = minOf(player.health + h, player.maxHealth)
            } else {
                player.damage(-h)
            }
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

    }

    // ===== Check consumable config by sertraline_id =====
    val sertralineId = Sertraline.api().getId(item)
    if (sertralineId != null) {
        val consumable = getConsumableConfig(sertralineId)
        if (consumable != null) {
            consumable.apply(player)

            val invTag = item.getItemTag() ?: return
            val invData = invTag["sertraline_data"]?.asCompound()
            if (invData?.get("durability") != null) {
                val dur = invData["durability"]?.asInt() ?: 1
                val newDur = dur - 1.0

                if (newDur > 0) {
                    if (item.amount > 1) {
                        // 堆叠: Paper 自然消耗数量-1，额外给一个扣了耐久的
                        val used = item.clone()
                        used.amount = 1
                        val usedTag = used.getItemTag() ?: return
                        usedTag.putDeep("sertraline_data.durability", floor(newDur))
                        usedTag.saveTo(used)
                        used.rebuildDisplay(player)
                        player.inventory.addItem(used).values.forEach {
                            player.world.dropItem(player.location, it)
                        }
                    } else {
                        invTag.putDeep("sertraline_data.durability", floor(newDur))
                        invTag.saveTo(item)
                        item.rebuildDisplay(player)
                        event.replacement = item
                    }
                }
            } else {
                // No durability — item will be consumed normally (stack -1)
            }
            return
        }
    }
    event.replacement = item
    if (event.replacement?.type == Material.GLASS_BOTTLE) {
        event.replacement = null
    }

    val data = player.getData() ?: return

    // Check NBT for custom hunger/saturation/health values
    val customHunger = tag?.get("hunger")?.asDouble()
    val customSaturation = tag?.get("saturation")?.asDouble()
    val customHealth = tag?.get("health")?.asDouble()

    // health applied regardless of whether hunger/saturation tags exist
    if (customHealth != null) {
        if (customHealth >= 0) {
            player.health = minOf(player.health + customHealth, player.maxHealth)
        } else {
            player.damage(-customHealth)
        }
    }

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
