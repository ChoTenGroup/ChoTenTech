package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.sertraline.data.AbstractSItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag

/**
 * armor 段处理器：只负责初始化**动态耐久**。
 *
 * 早期版本还会写 `armor.v = 1` 与 `armor.defense`，但 `ArmorManager` 从头到尾都只读
 * `armor.durability` —— 防御值/等级/速度全部来自 `gun` 段（YAML 才是唯一真相）。
 * 这些「写了没人读」的键会让人误以为改 NBT 就能改防御，故删除。
 */
fun armorAdapter(item: ItemStack, sItem: AbstractSItem, player: Player?): ItemStack {
    val map = sItem.config["armor"] as? Map<*, *> ?: return item
    val tag = item.getItemTag(true)
    if (tag.getDeep("sertraline_data.armor.durability") == null) {
        tag.putDeep(
            "sertraline_data.armor.durability",
            map["durability"]?.toString()?.trim()?.toDoubleOrNull() ?: 500.0,
        )
    }
    return item.setItemTag(tag, true)
}
