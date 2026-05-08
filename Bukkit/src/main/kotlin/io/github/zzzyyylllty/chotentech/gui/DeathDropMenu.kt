package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.DeathDropData
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.entity.Player
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.math.ceil

class DeathDropMenu(private val dropData: DeathDropData) {

    fun open(player: Player) {
        val maxSlots = dropData.items.size
        val rows = ceil(maxSlots / 9.0).toInt().coerceIn(1, 6)

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${DeathDropManager.getDropTitle()}".toComponent())) {
            rows(rows.coerceAtMost(6))

            onBuild { _, inv ->
                dropData.items.forEach { (slot, serialized) ->
                    val item = ItemSerializer.deserialize(serialized)
                    if (item != null) {
                        inv.addItem(item)
                    }
                }
            }

            onClick(lock = false) { event ->
                val slot = event.rawSlot
                if (slot < 0 || slot >= event.view.topInventory.size) return@onClick

                // 玩家取走物品时，从数据中移除
                val item = event.view.topInventory.getItem(slot)
                if (item != null && !item.type.isAir) {
                    dropData.items.entries.removeAll { it.value == ItemSerializer.serialize(item) }
                }
            }

            onClose {
                // 重置超时（有人打开过，重新计时）
                DeathDropManager.resetTimeout(dropData.entityUuid)

                // 如果没有剩余物品，立即清理
                if (dropData.items.isEmpty()) {
                    DeathDropManager.cleanup(dropData.entityUuid)
                }
            }
        }
    }
}
