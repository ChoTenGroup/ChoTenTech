package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.data.AbstractSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/** addon 段处理器：写入配件标记，配件本体不可堆叠。 */
fun addonAdapter(item: ItemStack, sItem: AbstractSItem, player: Player?): ItemStack {
    val map = sItem.config["addon"] as? Map<*, *> ?: return item
    return applyAddonNbt(item, map)
}

/** ammo 冒充 addon 的兼容写法也走这里，保证两种 YAML 得到完全相同的标记。 */
internal fun applyAddonNbt(item: ItemStack, map: Map<*, *>): ItemStack {
    val slot = map["slot"]?.toString()?.trim()?.uppercase().orEmpty()
    if (slot.isEmpty()) {
        devLog("addon: required field 'slot' is missing, skipped.")
        return item
    }
    val gunTypes = when (val raw = map["gunType"]) {
        is Collection<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        is Array<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        null -> emptyList()
        else -> listOf(raw.toString().trim()).filter(String::isNotEmpty)
    }

    val tag = item.getItemTag(true)
    val dataTag = tag.compoundOrNew("sertraline_data")
    val addonTag = dataTag.compoundOrNew("addon")
    // 只写一个「这是配件」的标记：装/卸配件本身由 GunInstance 的 `addons` 列表记录，
    // 槽位与适用枪型一律在运行时按 GunRegistry 的 YAML 判定（本模块不读这些键）。
    addonTag["v"] = 1
    if (addonTag["slot"] == null) addonTag["slot"] = slot
    if (addonTag["gunType"] == null) addonTag["gunType"] = gunTypes
    dataTag["addon"] = addonTag
    tag["sertraline_data"] = dataTag
    val result = item.setItemTag(tag, true)
    result.applyMaxStackSize(1, "addon")
    return result
}
