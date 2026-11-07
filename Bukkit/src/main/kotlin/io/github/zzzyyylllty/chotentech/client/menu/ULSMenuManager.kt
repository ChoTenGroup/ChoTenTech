package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSClientManager
import io.github.zzzyyylllty.chotentech.client.ULSProtocol
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.logger.warningS
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 菜单的下发与动作回收。
 *
 * ## 两条轨
 *
 * 同一个菜单有两套呈现：
 *
 * - **增强轨**：玩家装了模组、握手成功、且支持这个菜单需要的全部能力 →
 *   下发 [ULSMenuDefinition]，客户端渲染原生界面。
 * - **经典轨**：其余所有情况 → 走 [ULSMenuClassicRail]（原版箱子 UI）。
 *
 * **哪条轨由服务端决定，不由客户端请求。** 客户端不能被要求「先问问
 * 服务端我能不能用增强 UI」——那等于让不可信的一方发起协商。
 *
 * ## 线程
 *
 * 下发可以从任意线程调用；[sendToClient] 内部会切到主线程，
 * 因为 Bukkit 的 `sendPluginMessage` 不是线程安全的（它要读玩家的
 * 连接状态，而那个状态在主线程被改）。
 */
object ULSMenuManager {

    /** 每个玩家当前菜单的轨道与动作快照；按实例身份识别排队中的旧事件。 */
    private class OpenMenu(
        val menuId: String,
        val rail: ULSMenuRail,
        val actionIds: Set<String>,
        val nonce: Long,
        val handshakeGeneration: Long?,
    )

    private val openMenus = ConcurrentHashMap<UUID, OpenMenu>()
    /** 状态公布与封包排队原子化，避免 data 跑到 open 前、close 跑到替换后。 */
    private val menuSequenceLock = Any()
    /** 随机起点 + 单调递增：跨重启不可预测、同进程内不复用已打开的编号。 */
    private val nextNonce = java.util.concurrent.atomic.AtomicLong(java.security.SecureRandom().nextLong())

    /** 替换增强菜单前关闭旧实例（包括改走经典轨）；旧 nonce 不会关闭新屏幕。 */
    private fun closeReplacedEnhanced(player: Player, previous: OpenMenu?) {
        if (previous?.rail != ULSMenuRail.ENHANCED) return
        enqueueSend(player, ULSProtocol.PKT_MENU_CLOSE, ULSMenuCodec.encodeClose(previous.menuId, previous.nonce))
    }

    private fun enqueueSend(player: Player, packetId: String, body: ByteArray) {
        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
            if (player.isOnline) ULSClientManager.sendToClient(player, packetId, body)
        })
    }

    private fun sendIfCurrent(player: Player, current: OpenMenu, packetId: String, body: ByteArray) {
        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
            if (player.isOnline && openMenus[player.uniqueId] === current &&
                ULSClientManager.isUsingMod(player) &&
                ULSClientManager.handshakeGeneration(player) == current.handshakeGeneration
            ) {
                ULSClientManager.sendToClient(player, packetId, body)
            }
        })
    }

    private fun opened(
        menu: ULSMenuDefinition, rail: ULSMenuRail,
        nonce: Long = 0L, handshakeGeneration: Long? = null,
    ): OpenMenu = OpenMenu(
        menu.menuId,
        rail,
        menu.flatten().mapNotNull { it.actionId?.takeIf(String::isNotEmpty) }.toSet(),
        nonce,
        handshakeGeneration,
    )

    /** 每个玩家的动作时间戳滑动窗口，用于服务端侧限流。 */
    private val actionWindow = ConcurrentHashMap<UUID, ArrayDeque<Long>>()

    /** 每个玩家最后一次动作的时间，用于最小间隔判定。 */
    private val lastActionMs = ConcurrentHashMap<UUID, Long>()

    // ==================================================================
    // 下发
    // ==================================================================

    /**
     * 给玩家打开一个菜单。
     *
     * 会自动选择轨道。返回实际使用了增强轨还是经典轨，方便调用方
     * （例如命令反馈）如实告知。
     */
    fun open(player: Player, menu: ULSMenuDefinition): ULSMenuRail {
        // 先取会话轮次，再探测能力；握手重置可能在两次读取之间发生。
        // 若读取顺序相反，旧会话能力 + 新会话轮次会被错误拼接成有效快照。
        val handshakeGeneration = ULSClientManager.handshakeGeneration(player)
        var rail = chooseRail(player, menu)

        if (rail == ULSMenuRail.ENHANCED) {
            // 校验所有 actionId 都已登记。写错一个字母的后果是
            // 「按钮点了没反应」，在这里就暴露出来比线上排查便宜得多。
            for (element in menu.flatten()) {
                val actionId = element.actionId ?: continue
                if (!ULSMenuActionRegistry.exists(actionId)) {
                    devLog(
                        "菜单 ${menu.menuId} 的元素引用了未登记的动作 ID: $actionId —— " +
                                "该按钮点击后不会生效。"
                    )
                }
            }

            // 先编码，成功了再登记。
            //
            // 反过来写（先 openMenus 再 encodeOpen）会留下一个很隐蔽的
            // 状态：encodeOpen 对超限的定义（>512 元素或 >16 层）会抛
            // IllegalArgumentException，异常没人接 → 玩家什么都没收到，
            // 但 openMenus 里已经有了条目。之后对该玩家的 updateData
            // 会通过校验、把 menu_data 推给一个从未打开过菜单的客户端，
            // 还返回 true —— 管理员看到的诊断是健康的。
            //
            // 「失败必须留下可观测的痕迹」在这里的落地：要么两件事都成，
            // 要么两件事都不做。
            val nonce = nextNonce.incrementAndGet()
            val body = try {
                ULSMenuCodec.encodeOpen(menu, nonce)
            } catch (ex: IllegalArgumentException) {
                warningS("菜单 ${menu.menuId} 无法编码，改走经典轨: ${ex.message}")
                rail = ULSMenuRail.CLASSIC
                null
            }

            if (body != null) {
                val current = opened(menu, ULSMenuRail.ENHANCED, nonce, handshakeGeneration)
                synchronized(menuSequenceLock) {
                    // chooseRail 与完成编码之间可能收到新握手。此时绝不能把
                    // 旧会话增强菜单注册为新会话的当前菜单；改走经典轨。
                    if (ULSClientManager.isUsingMod(player) &&
                        ULSClientManager.handshakeGeneration(player) == handshakeGeneration
                    ) {
                        val previous = openMenus.put(player.uniqueId, current)
                        closeReplacedEnhanced(player, previous)
                        sendIfCurrent(player, current, ULSProtocol.PKT_MENU_OPEN, body)
                        return ULSMenuRail.ENHANCED
                    }
                }
                rail = ULSMenuRail.CLASSIC
            }
        }

        // 经典轨也必须登记 openMenus。
        //
        // 以前这里是 `openMenus.remove(...)`——理由是「经典轨不需要
        // 收客户端的动作，所以没有『当前菜单』的概念」。但经典轨的
        // 点击现在也走 [executeAction] 的 openMenus 校验，remove 掉
        // 等于让箱子界面上**每一个按钮都点了没反应**。
        //
        // 登记在前、`ULSMenuClassicRail.open` 在后是对的：那个方法是
        // 异步切主线程才真正 `openInventory`（中间隔着若干 tick），
        // 提前登记才能保证玩家点下第一个按钮时 openMenus 已经就位。
        val current = opened(menu, ULSMenuRail.CLASSIC)
        synchronized(menuSequenceLock) {
            val previous = openMenus.put(player.uniqueId, current)
            closeReplacedEnhanced(player, previous)
            ULSMenuClassicRail.open(player, menu, current)
        }
        return rail
    }

    /**
     * 只更新变量，不重发结构。
     *
     * 这是菜单「动态数据」的主路径：结构只在打开时发一次，
     * 之后每秒最多几次的小更新走这里。相比整体重发，
     * 省掉的是整个元素树（通常几百字节到几 KB）。
     *
     * 玩家当前打开的不是这个菜单时静默忽略——那说明他早就关掉了，
     * 发过去只会让客户端缓存一份陈旧数据。
     */
    fun updateData(player: Player, menuId: String, variables: Map<String, String>): Boolean =
        synchronized(menuSequenceLock) {
            val current = openMenus[player.uniqueId] ?: return@synchronized false
            if (current.menuId != menuId || current.rail != ULSMenuRail.ENHANCED ||
                !ULSClientManager.isUsingMod(player)
            ) return@synchronized false
            val body = ULSMenuCodec.encodeData(menuId, current.nonce, variables)
            sendIfCurrent(player, current, ULSProtocol.PKT_MENU_DATA, body)
            true
        }

    /** 关闭菜单。增强轨会真的关掉界面，经典轨什么都不做（玩家自己关）。 */
    fun close(player: Player) {
        // 先确认这一位是不是增强轨玩家，再决定动不动 openMenus。
        //
        // 原先是「先 remove 再判断」，于是原版玩家的箱子界面**不会被关**
        // （玩家自己关），但 openMenus 已经被清空 —— 而经典轨的点击要过
        // executeAction 的 openMenus 校验，于是那个箱子界面上每一个按钮
        // 都点了没反应。拒绝路径全走 devLog（生产默认关闭），管理员
        // 看不到任何提示，只能看到「界面开着但按钮是死的」。
        synchronized(menuSequenceLock) {
            val current = openMenus[player.uniqueId] ?: return
            if (current.rail != ULSMenuRail.ENHANCED) return
            if (!openMenus.remove(player.uniqueId, current)) return
            enqueueSend(player, ULSProtocol.PKT_MENU_CLOSE, ULSMenuCodec.encodeClose(current.menuId, current.nonce))
        }
    }

    /**
     * 选轨道。
     *
     * 判据是「装了模组 + 已握手 + 接受 + 支持这个菜单用到的每一项能力」。
     * 缺任何一项都整体降级——**不存在「部分发过去」这种中间态**：
     * 一个缺了按钮的菜单比一个箱子界面更难用，而且会让玩家以为功能坏了。
     */
    fun chooseRail(player: Player, menu: ULSMenuDefinition): ULSMenuRail {
        if (!ULSClientManager.isUsingMod(player)) return ULSMenuRail.CLASSIC
        if (!ULSClientManager.clientSupports(player, ULSProtocol.CAP_MENU)) return ULSMenuRail.CLASSIC
        for (capability in menu.requiredCapabilities()) {
            if (!ULSClientManager.clientSupports(player, capability)) return ULSMenuRail.CLASSIC
        }
        return ULSMenuRail.ENHANCED
    }

    // ==================================================================
    // 动作回收（客户端 → 服务端）
    // ==================================================================

    /** 处理网络动作；只允许当前增强菜单拥有的 actionId，业务调用留在主线程。 */
    fun handleAction(player: Player, body: ByteArray) {
        val payload = ULSMenuCodec.decodeAction(body)
        if (payload == null) {
            devLog(
                "收到无法解析的 action 载荷 (${body.size} 字节)，已忽略。玩家=${player.name}"
            )
            return
        }

        // 限流**只在这一层**，也就是「不可信输入真正进入的那道门」。
        //
        // 为什么不放进 executeAction：那个方法还有第二个调用方——经典轨的
        // 箱子界面点击。而箱子点击是**服务端自己产生的事件**：只要玩家
        // 没开着这个界面就根本收不到，客户端无法伪造，也就没有限流的必要。
        //
        // 之前把限流放在 executeAction 里，后果是原版客户端连点十下以上
        // 就会被判超频——而原版根本没有点击节流，这是正常操作。
        // 被封掉的那次点击界面已经取消（物品拿不走），玩家看到的是
        // 「按钮点了没反应」，而且拒绝日志走 devLog、生产环境默认关着，
        // 于是整件事完全不可见。
        //
        // 一句话：**反伪造的限流只该用在可被伪造的路径上。**
        val expected = openMenus[player.uniqueId] ?: return
        if (expected.rail != ULSMenuRail.ENHANCED || expected.menuId != payload.menuId ||
            expected.nonce != payload.nonce || payload.actionId !in expected.actionIds
        ) return
        if (isRateLimited(player)) {
            devLog("玩家 ${player.name} 的动作请求过于频繁，已丢弃（动作=${payload.actionId}）。")
            return
        }

        // Paper 实际回调所在线程依服务端实现而异；所有 Bukkit/player 和
        // 菜单处理函数必须在主线程运行。排队后重新核对打开的菜单实例，
        // 防止旧按钮在相同 menuId 的新一轮打开中被执行。
        val dispatch = Runnable {
            if (!player.isOnline || openMenus[player.uniqueId] !== expected) return@Runnable
            executeAction(player, payload.menuId, payload.actionId, payload.payload)
        }
        if (Bukkit.isPrimaryThread()) dispatch.run()
        else Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), dispatch)
    }

    /** 玩家主动关闭增强菜单；旧事件不得清除随后打开的另一个菜单实例。 */
    fun handleDismiss(player: Player, payload: ULSMenuCodec.DismissPayload) {
        val expected = openMenus[player.uniqueId] ?: return
        if (expected.rail != ULSMenuRail.ENHANCED || expected.menuId != payload.menuId ||
            expected.nonce != payload.nonce
        ) return
        val dismiss = Runnable {
            if (player.isOnline) openMenus.remove(player.uniqueId, expected)
        }
        if (Bukkit.isPrimaryThread()) dismiss.run()
        else Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), dismiss)
    }

    /** 当前排队中的经典界面是否仍然应该打开。 */
    internal fun isCurrentClassic(player: Player, marker: Any): Boolean =
        player.isOnline && openMenus[player.uniqueId] === marker &&
                (marker as? OpenMenu)?.rail == ULSMenuRail.CLASSIC

    /** 经典箱子关闭时仅清除对应那一轮打开的状态。 */
    internal fun onClassicClose(player: Player, marker: Any?) {
        if (marker != null) openMenus.remove(player.uniqueId, marker)
    }

    /** 经典界面的按钮须来自当前那一轮打开的 InventoryHolder。 */
    internal fun executeClassicAction(player: Player, marker: Any?, menuId: String, actionId: String): Boolean {
        if (marker == null || openMenus[player.uniqueId] !== marker) return false
        return executeAction(player, menuId, actionId, "")
    }

    /**
     * 动作执行的**唯一入口**。
     *
     * 增强轨（客户端菜单回传）与经典轨（箱子界面点击）都走这里，
     * 保证两条轨的**功能判定**完全一致：菜单必须当前打开着、
     * 动作必须在白名单里、处理函数统一 try/catch。曾经两条轨各写一份，
     * 于是经典轨连白名单都不查——「同一个动作在两条轨上受到的约束不同」
     * 从来不是会不会发生的问题。
     *
     * **限流不在这里**，见 [handleAction] 的说明：箱子点击不可伪造，
     * 对它限流只会误伤正常操作。
     *
     * @return true 表示这次动作被受理并交给了处理函数，false 表示被丢弃。
     */
    fun executeAction(player: Player, menuId: String, actionId: String, payload: String): Boolean {
        // 1. 必须针对当前打开的菜单。
        //
        //    这一条挡住的是「菜单早就关了，客户端还在发动作」。它**不是**
        //    权限检查——不能因为「菜单开着」就认为这次动作合法。
        val current = openMenus[player.uniqueId]
        if (current == null || current.menuId != menuId || actionId !in current.actionIds) {
            devLog(
                "玩家 ${player.name} 的动作指向未打开的菜单 " +
                        "($menuId，当前=${current?.menuId ?: "无"})，已丢弃。"
            )
            return false
        }

        // 3. 白名单。未登记的动作一律不执行。
        val action = ULSMenuActionRegistry.get(actionId)
        if (action == null) {
            devLog(
                "玩家 ${player.name} 触发了未登记的动作 $actionId（菜单 $menuId），已丢弃。"
            )
            return false
        }

        // 4. 执行。处理函数自己负责重新校验权限与状态。
        try {
            action.handler(
                ULSMenuActionRegistry.ULSMenuActionContext(
                    player = player,
                    menuId = menuId,
                    actionId = actionId,
                    payload = payload,
                )
            )
        } catch (t: Throwable) {
            // 单个动作出错不应该影响别的动作，也不该把异常抛回网络线程
            devLog(
                "执行菜单动作 $actionId 时出错（玩家 ${player.name}）: ${t.message}"
            )
        }
        return true
    }

    /**
     * 服务端侧限流。
     *
     * 双重判据：最小间隔 + 每秒上限。只用最小间隔的话，
     * 玩家可以用「恰好卡在间隔上」的频率刷很久；只用每秒上限的话，
     * 一次爆发仍然能在同一 tick 内挤进 10 个动作。
     */
    private fun isRateLimited(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId

        // 先把「每秒上限」判完，再写最小间隔的时间戳。
        //
        // 反过来的话有一个很隐蔽的后果：玩家在每秒窗口里被拒的那次请求
        // 也会刷新 lastActionMs，于是紧接着的合法请求又被最小间隔挡掉
        // ——被拒绝的请求反而延长了封禁时间。限流应当只惩罚它真正
        // 丢掉的那一次。
        val window = actionWindow.computeIfAbsent(uuid) { ArrayDeque() }
        synchronized(window) {
            // 清理 1 秒之前的记录
            while (window.isNotEmpty() && now - window.first() > 1000L) {
                window.removeFirst()
            }
            if (window.size >= ULSProtocol.ACTION_MAX_PER_SECOND) return true

            val last = lastActionMs[uuid]
            if (last != null && now - last < ULSProtocol.ACTION_MIN_INTERVAL_MS) return true

            // 两个判据都通过，才认为这次请求被受理。
            window.addLast(now)
            lastActionMs[uuid] = now
        }
        return false
    }

    // ==================================================================
    // 状态查询与清理
    // ==================================================================

    /** 该玩家当前打开的菜单 ID，没有返回 null。 */
    fun currentMenu(player: Player): String? = openMenus[player.uniqueId]?.menuId

    /** 该玩家是否正开着指定菜单。 */
    fun isViewing(player: Player, menuId: String): Boolean =
        openMenus[player.uniqueId]?.menuId == menuId

    /** 握手重置时清理过期的增强轨，但保留当前经典箱子轨的按钮。 */
    fun invalidateEnhanced(player: Player) {
        synchronized(menuSequenceLock) {
            val current = openMenus[player.uniqueId] ?: return
            if (current.rail == ULSMenuRail.ENHANCED && openMenus.remove(player.uniqueId, current)) {
                // 即使原 open 任务被取消，也要按旧 nonce 排队 close；不会误关新屏幕。
                enqueueSend(player, ULSProtocol.PKT_MENU_CLOSE, ULSMenuCodec.encodeClose(current.menuId, current.nonce))
            }
        }
    }

    /** 玩家退出时清理。必须调用，否则 [openMenus] 会一直涨。 */
    fun clearPlayer(player: Player) {
        val uuid = player.uniqueId
        synchronized(menuSequenceLock) { openMenus.remove(uuid) }
        actionWindow.remove(uuid)
        lastActionMs.remove(uuid)
    }

    /** 清空所有状态。用于 onDisable / reload。 */
    fun clear() {
        synchronized(menuSequenceLock) { openMenus.clear() }
        actionWindow.clear()
        lastActionMs.clear()
    }
}

/** 菜单实际走了哪条轨。 */
enum class ULSMenuRail {
    /** 增强轨：客户端原生渲染。 */
    ENHANCED,

    /** 经典轨：原版箱子 UI。 */
    CLASSIC,
}
