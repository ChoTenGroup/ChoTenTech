package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * 经典轨：不支持增强 UI 的玩家看到的原版箱子界面。
 *
 * ## 为什么要有这一轨
 *
 * 服务器是纯插件环境，模组完全可选。绝大多数玩家不会装模组，
 * 而他们**不应该**因此少掉任何功能——只是界面朴素一点。
 *
 * ## 这条轨必须是一等公民，不是「降级凑合」
 *
 * 一个常见的错误做法是「先做增强 UI，箱子界面后面补」。结果是
 * 主路径在装了模组的开发者机器上跑得很爽，而线上 90% 的玩家
 * 看到的是一个半成品。所以这里的要求是：**同一个
 * [ULSMenuDefinition] 必须能在两条轨上表达出等价的功能**，
 * 增强轨只是把它渲染得更自由。
 *
 * ## 映射规则
 *
 * 箱子界面只有「一堆格子」这一种表达，所以映射是有损的：
 *
 * - `BUTTON` → 一个物品，点击触发它的 actionId
 * - `TEXT`   → 一个无动作的物品（用改名物品承载文字）
 * - `SHAPE` / `PROGRESS` / `IMAGE` → 忽略（没有对应概念）
 * - `GRID` / `SCROLL` / `ROOT` → 递归展开它们的子元素
 *
 * 这条映射刻意写得很朴素：与其硬凑一个「像增强 UI 的箱子界面」，
 * 不如老实做一个能用的箱子界面。
 */
object ULSMenuClassicRail {

    /** 箱子界面的固定尺寸（54 格 = 大箱子）。 */
    private const val SLOT_COUNT = 54

    /** 每个经典菜单对应的 actionId 数组，按槽位下标索引。 */
    private val layoutByHolder = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<ClassicHolder, Array<String?>>()
    )

    /**
     * 打开经典轨菜单。
     *
     * 必须在主线程调用（会创建并打开 Inventory）。
     * 如果 [ULSMenuManager] 是从网络线程调到这里的，会自己切线程。
     */
    fun open(player: Player, menu: ULSMenuDefinition, marker: Any) {
        // 切主线程：Bukkit 的 Inventory 操作不是线程安全的。
        // 已经在主线程时也要走一遍调度，否则调用点会分叉成两条路径，
        // 而「有时候崩有时候不崩」是最难查的一类问题。
        Bukkit.getScheduler().runTask(
            taboolib.platform.BukkitPlugin.getInstance(),
            Runnable {
                if (ULSMenuManager.isCurrentClassic(player, marker)) openSync(player, menu, marker)
            },
        )
    }

    private fun openSync(player: Player, menu: ULSMenuDefinition, marker: Any) {
        val holder = ClassicHolder(menu.menuId, marker)
        val title = net.kyori.adventure.text.Component.text(
            menu.title.ifEmpty { menu.menuId }
        )
        val inventory = Bukkit.createInventory(holder, SLOT_COUNT, title)
        holder.attach(inventory)

        val actions = arrayOfNulls<String>(SLOT_COUNT)
        var index = 0
        // 因为装满 54 格而丢掉的元素数。用于事后告警——见方法末尾。
        var dropped = 0

        for (element in menu.flatten()) {
            if (index >= SLOT_COUNT) {
                dropped++
                continue
            }
            when (element.type) {
                ULSMenuElementType.BUTTON -> {
                    val stack = itemFor(element, interactive = true)
                    if (stack != null) {
                        inventory.setItem(index, stack)
                        actions[index] = element.actionId
                        index++
                    }
                }

                ULSMenuElementType.TEXT -> {
                    val stack = itemFor(element, interactive = false)
                    if (stack != null) {
                        inventory.setItem(index, stack)
                        index++
                    }
                }

                // 容器类只是结构，展开成扁平序列
                ULSMenuElementType.GRID,
                ULSMenuElementType.SCROLL,
                ULSMenuElementType.ROOT,
                -> Unit

                // 没有对应概念，忽略
                ULSMenuElementType.SHAPE,
                ULSMenuElementType.PROGRESS,
                ULSMenuElementType.IMAGE,
                ULSMenuElementType.UNKNOWN,
                -> Unit

                // 尚未实现交互的组件，先给个提示性物品，
                // 总比「这个菜单里什么都没有」好排查
                ULSMenuElementType.CHECKBOX,
                ULSMenuElementType.TEXTFIELD,
                ULSMenuElementType.SLOT,
                -> {
                    val stack = placeholderItem(element)
                    inventory.setItem(index, stack)
                    index++
                }
            }
        }

        if (!ULSMenuManager.isCurrentClassic(player, marker)) return
        layoutByHolder[holder] = actions
        player.openInventory(inventory)

        devLog("玩家 ${player.name} 以经典轨打开了菜单 ${menu.menuId}（${index} 个条目）。")

        // 装不下就必须说出来。
        //
        // `continue` 而不是 `break`：用 break 的话循环提前结束，
        // 连「一共丢了多少个」都数不出来。代价只是把剩下的元素走一遍
        // ——它们本来就已经在内存里了。
        //
        // 这条日志刻意用 warningS 而不是 devLog：devLog 受 devMode 控制、
        // 生产环境默认关闭，而「菜单被截断了」是运维必须知道的事
        // ——玩家看到的是一个少了一半按钮的箱子界面，他不会来报告，
        // 只会觉得这个功能是坏的。
        if (dropped > 0) {
            warningS(
                "菜单 ${menu.menuId} 在经典轨上被截断：箱子界面只有 $SLOT_COUNT 格，" +
                        "有 $dropped 个元素无法显示（玩家 ${player.name} 未安装模组，" +
                        "或能力不足已降级）。请精简该菜单，或引导玩家安装模组客户端。"
            )
        }
    }

    /**
     * 处理一次点击。由外部的事件监听器调用。
     *
     * 返回 true 表示这次点击属于本系统且已被消费。
     */
    fun handleClick(event: InventoryClickEvent): Boolean {
        val holder = event.inventory.holder as? ClassicHolder ?: return false

        // 无论如何先取消——箱子界面的物品不应该能被拿走
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return true
        if (event.clickedInventory !== event.inventory) return true

        val slot = event.rawSlot
        if (slot < 0 || slot >= SLOT_COUNT) return true

        val actions = layoutByHolder[holder] ?: return true
        val actionId = actions.getOrNull(slot) ?: return true

        // 交给共享的执行入口，而不是在这里自己走一遍白名单查找 + handler 调用。
        //
        // 这一处**曾经**是两条轨各写一份：增强轨要过 openMenus 校验与
        // 限流，经典轨什么都没有。后果是同一个动作在两条轨上
        // 受到的约束不同——「某个漏洞只在某条轨上存在」从来不是
        // 会不会发生的问题，只是还没被发现。
        //
        // 现在统一走 [ULSMenuManager.executeAction]，它负责：
        // openMenus 校验 → 白名单查找 → try/catch 调用 handler。
        //
        // **限流不在那里**：限流是给「可伪造的输入」准备的，而箱子点击
        // 只可能发生在玩家正开着这个界面的时候，伪造不了；对它限流只会
        // 误伤原版玩家的正常连点（他们每点一格就是一次事件，没有节流）。
        // 反伪造的那道闸在 [ULSMenuManager.handleAction]，也就是增强轨
        // 的封包入口。
        //
        // 经典轨的 payload 恒为空串（箱子界面没有输入框）。
        ULSMenuManager.executeClassicAction(player, holder.marker, holder.menuId, actionId)
        return true
    }

    /** 玩家关闭界面时清理。 */
    fun forget(holder: InventoryHolder?) {
        if (holder is ClassicHolder) layoutByHolder.remove(holder)
    }

    /** 清空所有记录。用于 reload。 */
    fun clear() {
        layoutByHolder.clear()
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 标记用的 holder。
     *
     * 用自定义 holder 而不是「按标题字符串匹配」来判断这个界面是不是我们的：
     * 标题是玩家可见文本，可能被其他插件、被翻译、被颜色代码改得对不上，
     * 而 holder 是对象身份，骗不了。
     */
    class ClassicHolder(val menuId: String, internal val marker: Any) : InventoryHolder {

        /**
         * 底层背包。
         *
         * **不能叫 `inventory`**：Kotlin 会为它生成 `getInventory()`，
         * 与 [InventoryHolder.getInventory] 的 JVM 签名撞车
         * （`Platform declaration clash`）。
         */
        private var backing: Inventory? = null

        internal fun attach(inventory: Inventory) {
            backing = inventory
        }

        /**
         * 返回底层背包。
         *
         * ## 为什么不是 `lateinit` + 直接返回
         *
         * 这个 holder 在构造之后、[attach] 之前就已经交给
         * `Bukkit.createInventory` 了，而 `InventoryHolder#getInventory`
         * 是**公开可达**的：任何拿到这个 Inventory 的插件、任何
         * 在 `createInventory` 内部的实现细节、甚至事件里的
         * `event.inventory.holder.getInventory()`，都可能在 attach
         * 之前调到它。
         *
         * 用 `lateinit` 的话那一刻会抛 `UninitializedPropertyAccessException`，
         * 而这是一个**只会在时序特殊时出现**的崩溃——正是最难查的那类。
         * 返回 null 至少让调用方按「暂时还没有背包」处理。
         * （`InventoryHolder#getInventory` 声明为非空，但 Bukkit 的
         * 实现里确实存在返回 null 的 holder，调用方本来就得容忍。）
         */
        override fun getInventory(): Inventory {
            return backing ?: throw IllegalStateException(
                "ClassicHolder 尚未绑定背包（menuId=$menuId）。" +
                        "这通常意味着有人在 attach 之前读取了 holder 的背包。"
            )
        }
    }

    private fun itemFor(element: ULSMenuElement, interactive: Boolean): ItemStack? {
        val text = element.props["text"] ?: return null
        if (text.isEmpty()) return null
        return buildItem(
            name = text,
            lore = element.props["tooltip"],
            interactive = interactive,
        )
    }

    private fun placeholderItem(element: ULSMenuElement): ItemStack = buildItem(
        name = element.props["text"] ?: element.type.id,
        lore = element.props["tooltip"],
        interactive = false,
    )

    /**
     * 造一个承载文字的物品。
     *
     * 用 `PAPER` / `NAME_TAG` 这类无属性的物品，而不是
     * 有耐久条、有附魔光效的物品——后者会被玩家（和部分客户端模组）
     * 解读成「状态」。
     */
    private fun buildItem(name: String, lore: String?, interactive: Boolean): ItemStack {
        val material = if (interactive) {
            org.bukkit.Material.PAPER
        } else {
            org.bukkit.Material.NAME_TAG
        }
        val stack = ItemStack(material)

        // 直接用 Adventure 组件，避免 `§` 风格代码在 1.21.4 上被
        // 当作普通字符原样显示（那会让菜单看起来像坏了）。
        val meta = stack.itemMeta ?: return stack
        meta.displayName(net.kyori.adventure.text.Component.text(name))

        if (!lore.isNullOrEmpty()) {
            val lines = TooltipText.splitLines(lore)
            if (lines.isNotEmpty()) {
                meta.lore(lines.map { net.kyori.adventure.text.Component.text(it) })
            }
        }
        stack.itemMeta = meta
        return stack
    }
}
