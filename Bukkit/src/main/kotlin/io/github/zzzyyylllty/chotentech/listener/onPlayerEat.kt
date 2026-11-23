package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.ConsumableVals
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


    val api = Sertraline.api()
    val sertralineId = api.getId(item)
    val vals = if (sertralineId != null) {
        ConsumableVals.read { key -> api.getValItemStack(item, key, player) }
    } else null
    var valsApplied = false

    // 食用数值统一读取 sertraline.vals，不再读取 restore NBT。
    if (vals != null && vals.configured) {
        // 倒地状态禁止使用医疗物品（按 ID 前缀兜底识别医疗物品）
        val isMedical = sertralineId != null &&
            io.github.zzzyyylllty.chotentech.function.SkillManager.medicalItemPrefixes.any { sertralineId.lowercase().contains(it) }
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
        vals.hunger?.let { h ->
            data.customHunger = (data.customHunger + h).coerceIn(0.0, data.maxCustomHunger)
        }
        // saturation
        vals.saturation?.let { s ->
            data.customSaturation = (data.customSaturation + s).coerceIn(0.0, minOf(data.maxCustomHunger, data.customHunger))
        }
        // water: 正数补水、负数扣水；复用水分边界及脱水状态清理。
        vals.water?.takeIf { it != 0.0 }?.let { ChoTenDataHelper(player).addWater(it) }
        // health (can be negative — damages player)
        vals.health?.let { h ->
            if (h >= 0) {
                player.health = minOf(player.health + h, player.maxHealth)
            } else {
                player.damage(-h)
            }
        }
        // san (sanity, can be negative)
        vals.san?.let { san ->
            data.sanity = (data.sanity + san).coerceIn(0.0, data.effectiveMaxSanity)
        }
        // nutrition map
        vals.nutrition.let { n ->
            n["grain"]?.let { data.grain = (data.grain + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["vitamin"]?.let { data.vitamin = (data.vitamin + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["protein"]?.let { data.protein = (data.protein + it).coerceIn(0.0, 100.0); nutritionChanged = true }
            n["fat"]?.let { data.fat = (data.fat + it).coerceIn(0.0, 100.0); nutritionChanged = true }
        }
        // potion effects
        vals.effects.forEach { effect ->
            val type = PotionEffectType.getByName(effect.id) ?: return@forEach
            player.addPotionEffect(PotionEffect(type, effect.ticks, effect.amplifier, true, true, true))
        }

        if (nutritionChanged) NutritionManager.sync(player)
        valsApplied = true
    }

    // ===== Check consumable config by sertraline_id =====
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

            // vals 分支已经处理过耐久,这里不重复扣减
            if (!valsApplied) {
                event.replacement = applyDurability(item, player)
            }
            return
        }
    }

    // vals 物品到此结束:不再落到原版食物回退,避免饥饿/饱食被重复计算
    if (valsApplied) return

    val data = player.getData() ?: return

    // Check NBT for custom hunger/saturation/health values
    val tag = item.getItemTag()
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
