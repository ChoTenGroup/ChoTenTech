package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.data.ModernSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/** addon 段处理器：写入配件标记，配件本体不可堆叠。 */
fun addonAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
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
    addonTag["v"] = 1
    addonTag["slot"] = slot
    addonTag["gunType"] = gunTypes
    dataTag["addon"] = addonTag
    tag["sertraline_data"] = dataTag
    val result = item.setItemTag(tag, true)
    result.applyMaxStackSize(1, "addon")
    return result
}
