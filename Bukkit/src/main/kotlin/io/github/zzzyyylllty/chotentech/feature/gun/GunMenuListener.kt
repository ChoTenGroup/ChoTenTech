package io.github.zzzyyylllty.chotentech.feature.gun

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 枪械菜单 / 弹匣菜单的事件入口 + 10 tick 定时刷新。
 *
 * ### 为什么统一在 HIGHEST 取消
 * 菜单里的东西一件都不该被拿走（包括预览里那把「真的枪」）。其它保护类 / 反作弊
 * 插件也可能取消点击，取 HIGHEST 并**无条件取消**能让结果对所有人确定。
 *
 * ### 为什么读 `rawSlot`
 * `rawSlot` 是「在整个界面里的编号」，`slot` 是「在玩家背包或容器里的局部编号」。
 * 用 `slot` 会把「点自己背包第 3 格」当成「点菜单第 3 个按钮」。
 *
 * ### 刷新
 * 打开菜单时 `submit(delay = 10, period = 10)` 起一个任务，每 10 tick 重画一次按钮；
 * 关闭 / 换界面 / 下线时取消。这样读条进度、可用状态、热量条都是实时的，
 * 又不需要每次点击都整表重画。
 */
object GunMenuListener {

    /** 刷新间隔（tick）。 */
    private const val REFRESH_INTERVAL = 10L

    private class Session(val inventory: Inventory) {
        var task: PlatformExecutor.PlatformTask? = null
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    // ==================== 生命周期 ====================

    /** 菜单被打开后调用：登记会话并启动刷新任务。 */
    fun onMenuOpened(player: Player, inventory: Inventory) {
        stopRefresh(player)
        val session = Session(inventory)
        sessions[player.uniqueId] = session
        session.task = submit(delay = REFRESH_INTERVAL, period = REFRESH_INTERVAL, async = false) {
            refreshTick(player, session)
        }
    }

    /**
     * 关服 / 卸载插件：关掉所有还开着的菜单并清掉会话。
     *
     * `closeInventory()` 会走 [onInventoryClose]，那里会顺手把读条与批量循环停掉。
     */
    fun shutdown() {
        val uuids = ArrayList(sessions.keys)
        for (uuid in uuids) {
            sessions.remove(uuid)?.task?.cancel()
            val player = Bukkit.getPlayer(uuid) ?: continue
            try {
                val holder = player.openInventory.topInventory.holder
                if (holder is CTGunMenuHolder) player.closeInventory()
            } catch (_: Exception) {
            }
        }
        sessions.clear()
    }

    private fun refreshTick(player: Player, session: Session) {
        if (!player.isOnline) {
            stopRefresh(player)
            return
        }
        val top = try {
            player.openInventory.topInventory
        } catch (_: Exception) {
            null
        }
        if (top !== session.inventory) {
            stopRefresh(player)
            return
        }
        when (val holder = session.inventory.holder) {
            is GunMenu.Holder -> GunMenu.render(player, session.inventory)
            is MagMenuHolder -> {
                // 移动过远要退出压弹 / 退弹模式，顺便把界面重画成「浏览」态
                MagMenu.checkMode(player, holder)
                MagMenu.render(player, session.inventory)
            }
            is ModMenu.Holder -> ModMenu.render(player, session.inventory)

            else -> stopRefresh(player)
        }
    }

    private fun stopRefresh(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.task?.cancel()
    }

    // ==================== 事件 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? CTGunMenuHolder ?: return

        // 菜单里的一切交互都不允许改变物品（含预览里那把真枪）
        event.isCancelled = true

        // 所有按钮与压弹/退弹只接受普通左键；数字键、副手交换、双击、Shift、
        // 中键等仍保持取消以锁住物品，但绝不能进入业务派发。
        if (event.click != ClickType.LEFT || event.isShiftClick) return

        val player = event.whoClicked as? Player ?: return
        val raw = event.rawSlot
        if (raw < 0) return

        val topSize = try {
            event.view.topInventory.size
        } catch (_: Exception) {
            0
        }

        if (raw < topSize) {
            when (holder) {
                is GunMenu.Holder -> GunMenu.handleClick(player, raw)
                is MagMenuHolder -> MagMenu.handleClick(player, holder, raw)
                is ModMenu.Holder -> ModMenu.handleClick(player, raw)
                else -> Unit
            }
        } else {
            // 玩家背包区域：只有弹匣菜单的压弹 / 退弹模式才放行
            if (holder is MagMenuHolder) {
                MagMenu.handleInventoryClick(player, holder, event)
            }
        }
    }

    /** 拖拽也挡掉：只取消点击会留下「把物品拖出去」的漏洞。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is CTGunMenuHolder) {
            event.isCancelled = true
        }
    }

    /**
     * 关闭菜单：结束压弹 / 退弹模式、终止批量循环与读条、取消刷新任务。
     *
     * 注意「关菜单再执行按钮动作」的顺序：`closeInventory()` 内部会同步触发本事件
     * （先把旧的读条停掉），按钮动作由 [closeThen] 放到下一 tick 才开始，
     * 所以不会被这里误杀。
     */
    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? CTGunMenuHolder ?: return
        val player = event.player as? Player ?: return

        stopRefresh(player)
        if (holder is MagMenuHolder) MagMenu.exitMode(holder)
        GunAction.cancelBulk(player)
        // 不能用无条件 cancel：`closeThen` 的按钮动作在下一 tick 才开始，
        // 而关菜单事件可能和那条读条的第一 tick 撞在同一 tick，
        // 无条件取消会把「点按钮 → 起读条」当场掐死（表现为点了没反应）。
        GunAction.cancelUnlessJustStarted(player)
    }
}
