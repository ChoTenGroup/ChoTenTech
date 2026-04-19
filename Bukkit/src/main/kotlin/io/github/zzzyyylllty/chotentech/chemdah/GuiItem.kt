package io.github.zzzyyylllty.chotentech.chemdah

import io.github.zzzyyylllty.chotentech.util.asListEnhanced
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial


data class GuiItem(
    val nodeName: String,
    val material: String,
    val name: String,
    val lore: List<String> = emptyList(),
)



fun GuiItem.build(): ItemStack {
    val item = ItemStack(XMaterial.valueOf(material.uppercase()).get() ?: Material.STONE)
    val itemMeta = item.itemMeta
    val lore = lore.asListEnhanced()?.toComponent()
    itemMeta.displayName(name.toComponent())
    itemMeta.lore(lore)
    item.setItemMeta(itemMeta)
    return item
}