package io.github.zzzyyylllty.chotentech.util

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial

fun guiItem(
    material: XMaterial,
    name: String,
    lore: List<String> = emptyList(),
    model: String? = null
): ItemStack {
    val item = material.parseItem() ?: ItemStack(Material.BARRIER)
    val meta = item.itemMeta ?: return item
    meta.displayName(name.toComponent())
    if (lore.isNotEmpty()) meta.lore(lore.map { it.toComponent() })
    if (model != null) meta.itemModel = NamespacedKey.fromString(model)
    item.setItemMeta(meta)
    return item
}

fun guiItem(
    material: XMaterial,
    fallback: Material,
    name: String,
    lore: List<String> = emptyList(),
    model: String? = null
): ItemStack {
    val item = material.parseItem() ?: ItemStack(fallback)
    val meta = item.itemMeta ?: return item
    meta.displayName(name.toComponent())
    if (lore.isNotEmpty()) meta.lore(lore.map { it.toComponent() })
    if (model != null) meta.itemModel = NamespacedKey.fromString(model)
    item.setItemMeta(meta)
    return item
}
