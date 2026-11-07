package io.github.zzzyyylllty.chotentech.client.menu

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.entity.Player
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/**
 * 经典轨（箱子 UI）的事件处理。
 *
 * ## 为什么优先级是 HIGHEST
 *
 * 其它插件（保护类、反作弊类）也可能取消背包点击。我们不希望
 * 「因为别的插件取消了，所以我的按钮不触发」——但也不希望
 * 「因为我先处理了，所以别的插件的取消失效」。
 *
 * 取 HIGHEST 是因为我们**无条件取消**事件（箱子界面本来就只读），
 * 所以对其它插件而言结果是确定的：这个界面里的物品谁也拿不走。
 * 用 LOWEST 的话，一旦某个插件在更早的阶段取消并 `return`，
 * 我们的动作就永远不触发，表现为「按钮偶尔点了没反应」。
 *
 * ## 读 `rawSlot` 而不是 `slot`
 *
 * `rawSlot` 是「在打开的整个界面里的编号」，`slot` 是「在玩家背包
 * 或容器里的局部编号」。玩家自己背包的格子也会有 `slot` 值，
 * 用错了就会把「点自己背包的第 3 格」当成「点菜单第 3 个按钮」。
 */
object ULSMenuClassicListener {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        ULSMenuClassicRail.handleClick(event)
    }

    /**
     * 挡住拖拽。
     *
     * 只处理点击是不够的：玩家可以按住鼠标把物品**拖**过若干个格子。
     * 拖拽事件与点击事件是两个不同的事件，只取消其中一个会留下
     * 「物品能被拖出来」的漏洞。这里一并挡掉。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is ULSMenuClassicRail.ClassicHolder) return
        event.isCancelled = true
    }

    /**
     * 界面关闭时释放记录。
     *
     * 这里用 `WeakHashMap` 存布局，理论上不清理也会被回收，
     * 但那要等 GC，而且中间任何一次遍历都可能撞上过期条目。
     * 玩家关闭界面时显式清掉更可预测。
     */
    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        ULSMenuClassicRail.forget(holder)
        val player = event.player as? Player ?: return
        if (holder is ULSMenuClassicRail.ClassicHolder) {
            ULSMenuManager.onClassicClose(player, holder.marker)
        }
    }
}
