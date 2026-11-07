package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.data.ModernSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/** ammo 段缺省的最大堆叠（子弹可以堆）。 */
private const val DEFAULT_AMMO_STACK = 64

/**
 * ammo 段落处理器：把子弹段写进物品 NBT `sertraline_data.ammo`。
 *
 * YAML 结构：
 * ```
 * ammo:
 *   caliber: 5x14     # 必需
 *   damage: 55        # 弹道数据，本处理器不写，运行时读配置
 *   penetration: 65
 *   stack: 64         # 可选：本物品自己的最大堆叠
 * ```
 *
 * 本段不属于任何模块的 `data`，只能从 [ModernSItem.config] 读取原始 YAML。
 */
fun ammoAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
    val map = (sItem.config["ammo"] as? Map<*, *>) ?: run {
        devLog("ammo: section is missing or not a map, skipped.")
        return item
    }

    // ── 「配件冒充 ammo」：直接改写 addon NBT，而不是留下一件没有标记的物品 ──
    if (map.containsKey("gunType") && map.containsKey("attributes") &&
        !map.containsKey("damage") && !map.containsKey("penetration")
    ) {
        return applyAddonNbt(item, map)
    }

    // ── caliber：必需字段，缺失时完全不写 NBT ────────────────────────────────
    val caliber = map["caliber"]?.toString()?.trim().orEmpty()
    if (caliber.isEmpty()) {
        devLog("ammo: required field 'caliber' is missing, skipped.")
        return item
    }

    // ── NBT：合并写入 sertraline_data.ammo，绝不整段覆盖 sertraline_data ────────
    val tag = item.getItemTag(true)
    val dataTag = tag.compoundOrNew("sertraline_data")
    val ammoTag = dataTag.compoundOrNew("ammo")

    ammoTag["v"] = 1
    ammoTag["caliber"] = caliber

    dataTag["ammo"] = ammoTag
    tag["sertraline_data"] = dataTag
    var result = item.setItemTag(tag, true)

    // ── 物品本体：优先 ammo.stack，其次 itemdef.stack（已由 itemdef 处理），最后 64 ──
    val stackSize = resolveStackSize(map, sItem)
    if (stackSize != null) {
        devLog("ammo: applying max stack size $stackSize (caliber=$caliber).")
        result.applyMaxStackSize(stackSize, "ammo")
    }

    return result
}

/**
 * 解析子弹的最大堆叠。
 *
 * 返回 null 表示「不要动」——即 `ammo.stack` 没写而 `itemdef.stack` 写了：
 * 那种情况 itemdef 处理器已经设过最大堆叠，这里再覆盖只会打架。
 */
private fun resolveStackSize(map: Map<*, *>, sItem: ModernSItem): Int? {
    val ammoStack = (map["stack"] as? Number)?.toInt()
    if (ammoStack != null) {
        if (ammoStack > 0) return ammoStack
        devLog("ammo: invalid 'stack' ($ammoStack), falling back to $DEFAULT_AMMO_STACK.")
        return DEFAULT_AMMO_STACK
    }

    val itemdefStack = ((sItem.config["itemdef"] as? Map<*, *>)?.get("stack") as? Number)?.toInt()
    if (itemdefStack != null && itemdefStack > 0) {
        devLog("ammo: 'stack' not set, itemdef.stack ($itemdefStack) is already applied by the itemdef processor.")
        return null
    }

    return DEFAULT_AMMO_STACK
}
