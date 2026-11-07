package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagType
import java.lang.reflect.Method

/**
 * 段落处理器共用的小工具（Gun/Weapon system 的 mag / ammo 段使用）。
 *
 * 这里只放两件事：
 * 1. [compoundOrNew]：安全地取出（或新建）一个嵌套 compound，用于「合并写入」而不覆盖兄弟节点；
 * 2. [applyMaxStackSize]：反射设置最大堆叠，同 [itemdefAdapter] 里的做法。
 */

/**
 * 取出 [key] 下的 compound 节点；不存在或类型不对（例如 sertraline 段没写 vars 时留下的 END）
 * 时返回一个全新的空 [ItemTag]。
 *
 * 注意：[taboolib.module.nms.ItemTagData.asCompound] 是硬 cast，
 * 对 END / LIST / 字符串节点直接调用会抛 ClassCastException，所以必须先判 [ItemTagType]。
 */
internal fun ItemTag.compoundOrNew(key: String): ItemTag {
    val existing = this[key] ?: return ItemTag()
    return if (existing.type == ItemTagType.COMPOUND) existing.asCompound() else ItemTag()
}

/**
 * `ItemMeta#setMaxStackSize` 是 1.20.5+ 的 Bukkit API，在 legacy12（MC 1.12.2 编译面）上不存在，
 * 因此在编译期不能直接调用，只能反射；方法缺失时静默跳过。
 */
private val sharedMaxStackSizeSetter: Method? by lazy<Method?> {
    try {
        ItemMeta::class.java.getMethod("setMaxStackSize", Integer.TYPE)
    } catch (_: Throwable) {
        null
    }
}

/** 反射设置最大堆叠；成功返回 true。 */
private fun applyMaxStackSizeReflectively(meta: ItemMeta, stack: Int, source: String): Boolean {
    val setter = sharedMaxStackSizeSetter ?: run {
        devLog("$source: ItemMeta#setMaxStackSize is unavailable on this server, stack size $stack skipped.")
        return false
    }
    return try {
        setter.invoke(meta, stack)
        true
    } catch (e: Exception) {
        devLog("$source: failed to apply max stack size $stack: ${e.message}")
        false
    }
}

/**
 * 把物品的最大堆叠设置成 [stack]（读回 meta -> 改 -> 写回）。
 * meta 缺失或 API 不可用时只写日志，不动物品。[source] 仅用于日志前缀。
 */
internal fun ItemStack.applyMaxStackSize(stack: Int, source: String) {
    val meta = this.itemMeta ?: run {
        devLog("$source: item meta is null, max stack size $stack skipped.")
        return
    }
    if (applyMaxStackSizeReflectively(meta, stack, source)) {
        this.itemMeta = meta
    }
}
