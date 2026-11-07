package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.sertraline.data.ModernSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/** armor 段处理器：合并写入静态防御值，并只在首次构建时初始化动态耐久。 */
fun armorAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
    val map = sItem.config["armor"] as? Map<*, *> ?: return item
    val tag = item.getItemTag(true)
    tag.putDeep("sertraline_data.armor.v", 1)
    tag.putDeep(
        "sertraline_data.armor.defense",
        map["defense"]?.toString()?.trim()?.toDoubleOrNull() ?: 0.0,
    )
    if (tag.getDeep("sertraline_data.armor.durability") == null) {
        tag.putDeep(
            "sertraline_data.armor.durability",
            map["durability"]?.toString()?.trim()?.toDoubleOrNull() ?: 500.0,
        )
    }
    return item.setItemTag(tag, true)
}
