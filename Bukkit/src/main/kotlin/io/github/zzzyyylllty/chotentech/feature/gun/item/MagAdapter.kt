package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.data.ModernSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/**
 * mag 段落处理器：把弹匣段写进物品 NBT `sertraline_data.mag`。
 *
 * YAML 结构：
 * ```
 * mag:
 *   caliber: 5x14          # 必需
 *   capacity: 8            # 必需（缺失/非法只告警，运行时再兜底）
 *   allowFastReload: 1     # 可选，布尔或 0/1
 *   ammoInspectionAccuracy: 0
 *   ammoInspectionTime: 2
 *   ...
 * ```
 *
 * 本段不属于任何模块的 `data`，只能从 [ModernSItem.config] 读取原始 YAML。
 * 只写运行时真正需要的两个键（`v` 与 `ammo`）：其余字段留给运行时按配置读取，
 * 避免 rebuild 时把玩家状态（已装填的子弹）覆盖掉。
 */
fun magAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
    val map = (sItem.config["mag"] as? Map<*, *>) ?: run {
        devLog("mag: section is missing or not a map, skipped.")
        return item
    }

    // ── caliber：必需字段，缺失时完全不写 NBT ────────────────────────────────
    val caliber = map["caliber"]?.toString()?.trim().orEmpty()
    if (caliber.isEmpty()) {
        devLog("mag: required field 'caliber' is missing, skipped.")
        return item
    }

    // ── capacity：缺失/非法只告警，容量由运行时再兜底 ──────────────────────────
    val capacity = (map["capacity"] as? Number)?.toInt()
    if (capacity == null || capacity <= 0) {
        devLog("mag: 'capacity' is missing or invalid (${map["capacity"]}), the runtime will fall back.")
    }

    // ── 可选字段类型校验：类型非法只告警，不中断构建 ──────────────────────────
    map["allowFastReload"]?.let {
        if (!it.isBooleanLike()) {
            devLog("mag: 'allowFastReload' expects a boolean (or 0/1), got '$it', ignored.")
        }
    }
    map["ammoInspectionAccuracy"]?.let {
        if (!it.isNumberLike()) {
            devLog("mag: 'ammoInspectionAccuracy' expects a number, got '$it', ignored.")
        }
    }

    // ── NBT：合并写入 sertraline_data.mag，绝不整段覆盖 sertraline_data ────────
    // sertralineAdapter 已经写过 sertraline_data（vars 等），这里只增补 mag 子节点。
    val tag = item.getItemTag(true)
    val dataTag = tag.compoundOrNew("sertraline_data")
    val magTag = dataTag.compoundOrNew("mag")

    magTag["v"] = 1
    // 只在不存在时初始化 ammo：已存在说明弹匣里可能装着子弹，rebuild 不能清空
    if (magTag["ammo"] == null) {
        magTag["ammo"] = emptyList<String>()
    } else {
        devLog("mag: existing NBT 'sertraline_data.mag.ammo' kept.")
    }

    dataTag["mag"] = magTag
    tag["sertraline_data"] = dataTag
    var result = item.setItemTag(tag, true)

    // ── 物品本体：弹匣不可堆叠（只改最大堆叠，amount 保持原样） ────────────────
    devLog("mag: applying max stack size 1 (caliber=$caliber, capacity=${capacity ?: "?"}).")
    result.applyMaxStackSize(1, "mag")

    return result
}

/** 容错布尔：真布尔、任意数字、或 "true"/"false"/数字字符串都算合法。 */
private fun Any.isBooleanLike(): Boolean = when (this) {
    is Boolean -> true
    is Number -> true
    is String -> equals("true", ignoreCase = true) ||
        equals("false", ignoreCase = true) ||
        toDoubleOrNull() != null
    else -> false
}

/** 容错数字：任意 Number 或可解析为数字的字符串。 */
private fun Any.isNumberLike(): Boolean = when (this) {
    is Number -> true
    is String -> toDoubleOrNull() != null
    else -> false
}
