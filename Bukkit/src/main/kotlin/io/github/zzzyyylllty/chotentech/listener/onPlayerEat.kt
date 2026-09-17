package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.NutritionManager
import io.github.zzzyyylllty.chotentech.data.getConsumableConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.item.rebuildDisplay
import io.papermc.paper.datacomponent.DataComponentTypes
import kotlin.math.floor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.inventory.ItemStack
import org.bukkit.event.player.PlayerItemConsumeEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

@SubscribeEvent
fun onPlayerEat(event: PlayerItemConsumeEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val item = event.item


    val tag = item.getItemTag()

    var restoreApplied = false

    // ===== Check for "restore" compound in ItemTag =====
    val restoreTag = tag?.get("restore")?.asCompound()
    if (restoreTag != null) {
        // 倒地状态禁止使用医疗物品（按 ID 前缀兜底识别医疗物品）
        val restoreSertralineId = Sertraline.api().getId(item)?.lowercase()
        val isMedical = restoreSertralineId != null &&
            io.github.zzzyyylllty.chotentech.function.SkillManager.medicalItemPrefixes.any { restoreSertralineId.contains(it) }
        if (isMedical && BleedoutManager.isDowned(player.uniqueId)) {
            event.isCancelled = true
            player.sendActionBar("<#E08A98>倒地状态无法使用医疗物品".toComponent())
            return
        }
        // 先确定 replacement:医疗物品绝不残留玻璃瓶(原版会把整格按饮品替换成瓶子)
        // 带耐久的医疗物品同样走耐久扣减:单个扣耐久留在手上,堆叠分出一个扣过耐久的
        event.replacement = applyDurability(item, player)
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
        restoreApplied = true
    }

    // ===== Check consumable config by sertraline_id =====
    val sertralineId = Sertraline.api().getId(item)
    if (sertralineId != null) {
        val consumable = getConsumableConfig(sertralineId)
        if (consumable != null) {
            // 倒地状态禁止使用医疗物品
            if (consumable.medical && BleedoutManager.isDowned(player.uniqueId)) {
                event.isCancelled = true
                player.sendActionBar("<#E08A98>倒地状态无法使用医疗物品".toComponent())
                return
            }
            consumable.apply(player)

            // restore 分支已经处理过耐久,这里不重复扣减
            if (!restoreApplied) {
                event.replacement = applyDurability(item, player)
            }
            return
        }
    }

    // restore 物品到此结束:不再落到原版食物回退,避免饥饿/饱食被重复计算
    if (restoreApplied) return

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

/**
 * 带耐久的物品使用后扣 1 次耐久,耐久耗尽才被消耗:
 * - 单个:扣耐久后留在手上
 * - 堆叠:分出一个扣过耐久的(背包满则掉地上),手上保留剩余数量
 * - 耗尽:按剩余数量消耗(单件消失/堆叠减一),绝不残留玻璃瓶
 */
private fun applyDurability(item: ItemStack, player: Player): ItemStack {
    val invTag = item.getItemTag() ?: return consumeReplacement(item)
    val dur = invTag.get("sertraline_data")?.asCompound()?.get("durability")
        ?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() }
        ?: return consumeReplacement(item)
    val newDur = dur - 1.0
    return when {
        newDur > 0 && item.amount > 1 -> {
            val used = item.clone().apply { amount = 1 }
            // 克隆自带 tag,防御性判空:即使失败也要继续走分出去逻辑
            used.getItemTag()?.let { usedTag ->
                usedTag.putDeep("sertraline_data.durability", floor(newDur))
                usedTag.saveTo(used)
                used.rebuildDisplay(player)
            }
            // 优先放入背包,放不进去则把扣过耐久的单件作为 replacement
            val leftover = player.inventory.addItem(used)
            if (leftover.isEmpty()) {
                // 放入成功：返回原物品减1作为 replacement
                consumeReplacement(item)
            } else {
                // 背包满：直接返回扣过耐久的单件，保留耐久变化
                used
            }
        }
        newDur > 0 -> {
            invTag.putDeep("sertraline_data.durability", floor(newDur))
            invTag.saveTo(item)
            item.rebuildDisplay(player)
            item
        }
        else -> consumeReplacement(item)
    }
}

/** 医疗物品消耗后的剩余:叠加保留 amount-1,单个清空(绝不产生玻璃瓶) */
private fun consumeReplacement(item: ItemStack): ItemStack =
    if (item.amount > 1) item.clone().apply { amount = item.amount - 1 } else ItemStack(Material.AIR)
