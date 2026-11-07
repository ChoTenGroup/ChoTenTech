package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.compat.PlatformCompat
import io.github.zzzyyylllty.sertraline.config.asListEnhanced
import io.github.zzzyyylllty.sertraline.data.ModernSItem
import io.github.zzzyyylllty.sertraline.util.VersionHelper
import io.github.zzzyyylllty.sertraline.util.loreformat.performPlaceholders
import io.github.zzzyyylllty.sertraline.util.minimessage.toComponent
import io.github.zzzyyylllty.sertraline.util.toBooleanTolerance
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.lang.reflect.Method

/**
 * itemdef 段落处理器：把一段简化的物品定义（name / desc / material / unbreakable / glow / stack）
 * 直接写到 ItemStack 上。
 *
 * 这一段不属于任何模块的 `data`，所以只能从 [ModernSItem.config] 读取原始 YAML。
 * 与 xbuilder 的差别：itemdef 只做“最基础的物品外观定义”，不参与 XItemStack 反序列化。
 */
fun itemdefAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
    val map = (sItem.config["itemdef"] as? Map<*, *>) ?: return item
    if (map.isEmpty()) return item

    // ── material：**不在这里改** ────────────────────────────────────────────
    // Sertraline 构建物品时已经按 `xbuilder:material` → `minecraft:material` → `itemdef.material`
    // 决定基础材质（见 Sertraline 的 itemBuilder#itemSource）。在这里再 setType 一次不但多余，
    // 还会把前面处理器写好的 `custom_data`（含 `sertraline_id`）整块重置掉 ——
    // 一旦发生，物品就再也无法被识别成枪械/弹匣。需要换基材请只改 YAML 的 itemdef.material。

    val meta = item.itemMeta ?: run {
        devLog("itemdef: item meta is null, skipped.")
        return item
    }

    // ── name：MiniMessage 名称，缺省则不动名称 ────────────────────────────────
    map["name"]?.toString()?.toComponent()?.let { PlatformCompat.setDisplayName(meta, it) }

    // ── desc：多行描述，作为 Lore 的前置行；原有 Lore 保留在后面 ───────────────
    val descLore = map["desc"].asListEnhanced()
        ?.performPlaceholders(sItem, player)
        ?.map { it.toComponent() }
        ?: emptyList()
    if (descLore.isNotEmpty()) {
        val originLore = PlatformCompat.getLore(item) ?: emptyList()
        PlatformCompat.setLore(meta, descLore + originLore)
    }

    // ── unbreakable ─────────────────────────────────────────────────────────
    map["unbreakable"]?.let { meta.isUnbreakable = it.toBooleanTolerance() }

    // ── glow：耐久 I + HIDE_ENCHANTS（与 xbuilder glow 的做法保持一致）──────────
    if (map["glow"]?.toBooleanTolerance() == true) {
        // 1.20.5 起 Bukkit 附魔名改为 UNBREAKING，1.12.2 时代是 DURABILITY，两个都试一遍
        val unbreaking = Enchantment.getByName("UNBREAKING") ?: Enchantment.getByName("DURABILITY")
        if (unbreaking != null) {
            meta.addEnchant(unbreaking, 1, true)
        } else {
            devLog("itemdef: unbreaking enchantment not found, glow skipped.")
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
    }

    // ── stack：1.20.5+ 才有最大堆叠数量，低版本忽略 ───────────────────────────
    (map["stack"] as? Number)?.toInt()?.let { stack ->
        if (stack <= 0) {
            devLog("itemdef: invalid stack size $stack, skipped.")
        } else if (!VersionHelper.isOrAbove12005()) {
            devLog("itemdef: max stack size is a 1.20.5+ feature, skipped.")
        } else {
            applyMaxStackSize(meta, stack)
        }
    }

    item.itemMeta = meta
    return item
}

/**
 * `ItemMeta#setMaxStackSize` 是 1.20.5+ 的 Bukkit API，在 legacy12（MC 1.12.2 编译面）上不存在，
 * 因此与 spigot 侧 PlatformCompat 的做法一致：反射调用，方法缺失时静默跳过。
 */
private val maxStackSizeSetter: Method? by lazy<Method?> {
    try {
        ItemMeta::class.java.getMethod("setMaxStackSize", Integer.TYPE)
    } catch (_: Throwable) {
        null
    }
}

private fun applyMaxStackSize(meta: ItemMeta, stack: Int) {
    val setter = maxStackSizeSetter ?: run {
        devLog("itemdef: ItemMeta#setMaxStackSize is unavailable on this server, stack skipped.")
        return
    }
    try {
        setter.invoke(meta, stack)
    } catch (e: Exception) {
        devLog("itemdef: failed to apply max stack size $stack: ${e.message}")
    }
}
