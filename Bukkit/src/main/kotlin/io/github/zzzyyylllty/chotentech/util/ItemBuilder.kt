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

// ── 任务GUI风格 lore 助手 ──

/** 段落标题（青色），用于 2 行及以上描述的开头，如：| 简介 */
fun loreSection(title: String): String = "<!i><#F2F5FA><b>|<reset> <!i><#84D1CB>$title"

/** 单项内容行（白色 标签: 值），如：| 状态: 在线 */
fun loreInfo(label: String, value: String = ""): String =
    if (value.isEmpty()) "<!i><#F2F5FA><b>|<reset> <!i><#F2F5FA>$label"
    else "<!i><#F2F5FA><b>|<reset> <!i><#F2F5FA>$label: $value"

/** 分隔空行 */
fun loreSpace(): String = "<!i> "

/** 段落内容列表项，如：- 目标 5/10 */
fun loreDash(content: String): String = "<!i> <#6E7A8C>- $content"
