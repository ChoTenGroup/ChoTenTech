package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.compat.PlatformCompat
import io.github.zzzyyylllty.sertraline.data.ModernSItem
import io.github.zzzyyylllty.sertraline.impl.setComponentNMS
import io.github.zzzyyylllty.sertraline.util.VersionHelper
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.NMSItemTag.Companion.asBukkitCopy
import taboolib.module.nms.NMSItemTag.Companion.asNMSCopy
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import kotlin.math.abs

/**
 * model 段落处理器。
 *
 * YAML 结构（键是状态名）：
 * ```
 * model:
 *   general: "kangelweapon:ofn_pistol"    # 主模型
 *   aiming: "kangelweapon:ofn_pistol_aim" # 可选状态模型
 *   reload: ...
 *   empty: ...
 *   jammed: ...
 * ```
 * 主模型写到物品本身（1.20.5+ 写 `item_model` 组件，低版本写 CustomModelData），
 * **全部**状态写到 NBT `sertraline_data.model`，具体切换逻辑由运行时（其他插件/脚本）负责。
 */
fun modelAdapter(item: ItemStack, sItem: ModernSItem, player: Player?): ItemStack {
    val map = (sItem.config["model"] as? Map<*, *>) ?: return item
    if (map.isEmpty()) return item

    // 状态名 -> 模型路径，全部转成字符串方便写 NBT
    val states = linkedMapOf<String, Any?>()
    map.forEach { (state, path) ->
        if (state != null && path != null) states[state.toString()] = path.toString()
    }
    if (states.isEmpty()) return item

    // 先把全部状态模型写进 NBT（sertraline_data.model），供运行时切换
    val tag = item.getItemTag(true)
    tag.putDeep("sertraline_data.model", states)
    var result = item.setItemTag(tag, true)

    // 主模型：general 写到物品本身
    val general = states["general"]?.toString()
    if (general != null) {
        if (VersionHelper.isOrAbove12005()) {
            // 1.20.5+ 的 item_model 组件，值形如 kangelweapon:ofn_pistol
            var nmsItem = asNMSCopy(result)
            nmsItem.setComponentNMS("item_model", general)?.let { nmsItem = it }
            result = asBukkitCopy(nmsItem)
        } else {
            // 低版本：路径 hash 成正整数写入 CustomModelData（0 不合法，MIN_VALUE 取绝对值仍为负）
            val meta = result.itemMeta
            if (meta == null) {
                devLog("model: item meta is null, custom model data skipped.")
            } else {
                val hash = abs(general.hashCode())
                PlatformCompat.setCustomModelData(meta, if (hash <= 0) 1 else hash)
                result.itemMeta = meta
            }
        }
    }

    return result
}
