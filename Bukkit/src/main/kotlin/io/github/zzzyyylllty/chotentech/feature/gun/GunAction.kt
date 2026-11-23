package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.sertraline.Sertraline
import net.kyori.adventure.audience.Audience
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 枪械「读条动作」总入口（P2）：换弹 / 卸弹匣 / 拉栓 / 检视 / 压弹 / 退弹 / 排障 / 切模式。
 *
 * ### 为什么所有动作都要走 [timedAction]
 * 设计文档要求「同一时刻只允许一个动作」，而上面这些动作的触发点分散在
 * 键位监听（Q / SHIFT+Q）、枪械菜单、弹匣菜单、调试命令里。如果每个入口各自
 * `submit` 一个计时器，就会出现「换弹的同时还能压弹」「两个换弹同时结算、
 * 弹匣凭空翻倍」这类竞态。所以这里把**计时器收敛成唯一一份**：
 *
 * - [actions] 以玩家 UUID 为键，**天然只有一个**；
 * - 新动作进来先 [cancel] 旧动作，且旧动作**不结算**（`onFinish` 不会被调用）；
 * - 中断条件（移动超距离 / 换主手槽位 / 死亡 / 打开别的 GUI）一律不结算。
 *
 * ### 为什么没把计时塞进 [GunStateManager] 的 tick 循环
 * 需求给了两个选项，这里选了「[GunAction] 自己 `submit(period = 1)` + 集中登记」：
 * 动作的 tick 逻辑（进度条、中断判定）与动作内容（换弹结算）强耦合，
 * 放进 [GunStateManager] 会让那个循环继续膨胀；而「同一时刻只有一个」这条
 * 由 [actions] 的键保证，与计时器放在哪里无关。[GunStateManager.clear] /
 * [GunStateManager.stop] 已接线到这里，避免状态清了而读条还在跑。
 *
 * ### 塔科夫 +1（膛内那发）
 * 换弹结算时：
 * - 新弹匣**非空**且 `chamber >= 1` → 弹匣一发不少，膛内仍为 1；
 * - 新弹匣**非空**且 `chamber == 0` → 弹匣移除末位并 `chamber = 1`（这一发被推进膛）；
 * - 新弹匣**为空** → 膛内保持原状，只提示「空弹匣」。
 */
object GunAction {

    /** 读条动作种类（ActionBar / 菜单状态行显示用）。 */
    enum class ActionType(val label: String) {
        RELOAD("换弹"),
        RELOAD_FAST("快速换弹"),
        REMOVE_MAG("卸弹匣"),
        BOLT("拉栓"),
        INSPECT_CHAMBER("检视弹仓"),
        INSPECT_MAG("检视弹匣"),
        INSERT_AMMO("压弹"),
        REMOVE_AMMO("退弹"),
        FIX("排障"),
        FIRE_MODE("切换模式"),
    }

    /** 供 HUD / 菜单读取的动作快照。 */
    data class ActionSnapshot(val type: ActionType, val progress: Double, val percent: Int)

    /** 进度条总格数。 */
    private const val BAR_SEGMENTS = 10

    /**
     * 起读条后的 GUI 判定期宽限（tick）。
     *
     * 点菜单按钮起读条时，「关界面」与「读条第一 tick」可能差半拍，
     * 这一拍里 `openInventory` 还指着旧界面。宽限几 tick 让这类竞态不会误杀读条。
     */
    private const val GUI_GRACE_TICKS = 5

    /** 关菜单时豁免「刚起读条」的 tick 数（见 [cancelUnlessJustStarted]）。 */
    private const val MENU_CLOSE_GRACE_TICKS = 3

    /** 缺省耗时来源（没手持枪械时，压弹/退弹按 [UsageTimes] 的默认值算）。 */
    private val DEFAULT_USAGE = UsageTimes()

    /** 进行中的动作（uuid → 动作），同一玩家最多一个。 */
    private val actions = ConcurrentHashMap<UUID, ActiveAction>()

    /**
     * 「下一个动作来自菜单按钮」的标记（[closeThen] 打、[timedAction] 消费）。
     *
     * 关菜单时要回收菜单自己发起的读条，但不能连带杀掉 Q 键换弹这种与菜单无关的动作。
     */
    private val menuOriginated = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<UUID, Boolean>(),
    )

    /** 批量压弹 / 退弹的「代次」，代次失配即停止链式循环。 */
    private val bulkTokens = ConcurrentHashMap<UUID, Long>()

    /** 代次自增序号（只在主线程自增，无需原子）。 */
    private var bulkSeq: Long = 0L

    /**
     * 读条被打断的原因。
     *
     * 以前这里只有一句统一文案（`换弹被打断`），玩家和服主都不知道是「谁」打断的：
     * 站着不动也被打断时，看不出是移动判定、物品判定还是 GUI 判定误伤。
     * 现在每个分支都带自己的原因，提示里直接写出来，便于现场定位。
     */
    private enum class Interrupt(val reason: String) {
        OFFLINE("你已离线"),
        DEAD("你已倒下"),
        SLOT("切换了主手槽位"),
        ITEM_CHANGED("主手物品被换掉"),
        MOVED("移动过远"),
        GUI("打开了其它界面"),
    }

    /**
     * 背包里「能放弹匣」的槽位：快捷栏 + 主背包 0..35。
     *
     * `PlayerInventory.getSize()` 返回 41（36 存储 + 4 护甲 + 1 副手），
     * 直接 `0 until size` 会把**护甲槽和副手槽**也当成候选 ——
     * 护甲槽里的弹匣会被从装备栏里「换」走，与文档写的「0..35」也不一致。
     */
    /** 玩家背包存储槽：0..8 快捷栏 + 9..35 主背包。 */
    private val STORAGE_SLOTS: IntRange = 0..35

    /** 副手槽。`PlayerInventory` 的 41 格里，36..39 是护甲、40 是副手。 */
    private const val OFFHAND_SLOT = 40

    /**
     * 换弹候选要扫描的槽位：存储槽 + 副手槽，**单独排除护甲槽**。
     *
     * 早期版本直接扫 `0 until inventory.size`（41 格），会把塞进护甲槽的弹匣也当候选，
     * 相当于从装备栏里把东西「换」走 —— 那确实是隐患。但一刀切成 `0..35` 又把
     * 「副手放弹匣、主手换弹」这个正常玩法砍掉了。所以这里显式列出存储槽与副手槽，
     * 只排除 36..39 的护甲槽。
     *
     * 槽位读走 `getItem` / `setItem`（40 号走的是副手），与改动前的行为一致。
     */
    private val RELOAD_SCAN_SLOTS: List<Int> = STORAGE_SLOTS.toList() + OFFHAND_SLOT

    private class ActiveAction(
        val type: ActionType,
        val startLocation: Location,
        val startSlot: Int,
        val startItem: ItemStack,
        /**
         * 起始物品的**身份**（枪械 / 弹匣的 `instanceId`，前缀区分种类）。
         *
         * 中断判定不能用 `ItemStack#isSimilar`：读条期间 [GunModel] 会把模型切成
         * `reload` / `bolt`（改 `custom_model_data` 或 `item_model`），[GunNameplate] 也可能
         * 改写显示名 —— 这些都让 `isSimilar` 变成 false，于是**每一条读条都会被自己打断**
         * 并弹出「换弹被打断」。用实例身份判断才对应真正想表达的意思：同一件物品没被换掉。
         */
        val startIdentity: String,
        val totalTicks: Int,
        /** 是否由菜单按钮发起（[closeThen] 打标记）：只有这类动作会在关菜单时被回收。 */
        val fromMenu: Boolean = false,
    ) {
        var elapsed: Int = 0
        var task: PlatformExecutor.PlatformTask? = null
    }

    /**
     * 物品身份串：枪械优先，其次弹匣；都不是就返回空串。
     *
     * 前缀区分种类，避免「枪的 instanceId」和「弹匣的 instanceId」恰好相等时误判。
     */
    private fun identityOf(item: ItemStack): String {
        if (item.type.isAir) return ""
        GunInstance.read(item)?.instanceId?.takeIf { it.isNotEmpty() }?.let { return "g:$it" }
        // 纯读：换弹起读条 / 每 tick 中断判定都会调这里，不能顺手给弹匣生成并落盘身份
        MagazineCore.instanceIdOrNull(item)?.let { return "m:$it" }
        return ""
    }

    // ==================== 统一读条 ====================

    /**
     * 统一读条执行体。
     *
     * @param player 执行者
     * @param seconds 时长（秒），不足 1 tick 按 1 tick
     * @param onTick 每 tick 回调（progress = 0.0 ~ 1.0），可为空
     * @param onFinish 结算回调：**只有正常读完才会被调用**（被打断一律不结算）
     * @param type 动作种类（ActionBar / HUD 文案用）
     * @param reject 开跑前的前置校验：返回非空字符串表示「拒绝执行」，此时只发提示、不读条
     */
    fun timedAction(
        player: Player,
        seconds: Double,
        onTick: ((progress: Double) -> Unit)? = null,
        onFinish: () -> Unit,
        type: ActionType = ActionType.RELOAD,
        reject: (() -> String?)? = null,
    ) {
        // 新动作打断旧动作，旧动作不结算
        cancel(player)
        GunShoot.resetTrigger(player)
        if (!player.isOnline) return

        reject?.invoke()?.let { reason ->
            sendActionBar(player, reason)
            return
        }

        val totalTicks = (seconds * 20.0).roundToInt().coerceAtLeast(1)
        val startItem = player.inventory.itemInMainHand
        val action = ActiveAction(
            type = type,
            startLocation = player.location.clone(),
            startSlot = player.inventory.heldItemSlot,
            startItem = startItem,
            startIdentity = identityOf(startItem),
            totalTicks = totalTicks,
            fromMenu = menuOriginated.remove(player.uniqueId) != null,
        )
        actions[player.uniqueId] = action
        action.task = submit(period = 1, async = false) {
            tickAction(player, action, onTick, onFinish)
        }
    }

    /** 打断并**不结算**玩家当前动作（不在读条时是空操作）。 */
    fun cancel(player: Player) {
        actions.remove(player.uniqueId)?.task?.cancel()
        // 新动作会打断旧动作，旧批量链也不能在读条结束后继续结算。
        bulkTokens.remove(player.uniqueId)
    }

    /**
     * 关菜单时打断读条，但**刚起 [graceTicks] tick 内的读条不算**。
     *
     * 「点菜单按钮 → 关菜单 → 下一 tick 起读条」这条链上，关菜单事件可能和读条第一 tick
     * 落在同一 tick（`closeInventory()` 在部分服务端是延后处理的）。此时无条件 [cancel]
     * 会把刚起来的换弹 / 卸弹匣当场掐死，而且 `cancel` 是静默的 ——
     * 玩家看到的现象就是「点了『换弹匣』/『卸下弹匣』，什么也没发生」。
     *
     * 菜单自己发起的读条（压弹 / 退弹）在关菜单时早就跑了不止几 tick，照样会被掐掉。
     */
    fun cancelUnlessJustStarted(player: Player, graceTicks: Int = MENU_CLOSE_GRACE_TICKS) {
        val action = actions[player.uniqueId] ?: return
        // 只回收「菜单按钮发起」的动作：Q 键换弹与菜单毫无关系，关菜单不该把它静默掐掉。
        if (!action.fromMenu) return
        if (action.elapsed <= graceTicks) return
        cancel(player)
    }

    /** 标记「接下来这个动作是菜单按钮发起的」（由 [closeThen] 调用，[timedAction] 消费后立即清除）。 */
    fun markMenuOriginated(player: Player) {
        menuOriginated.add(player.uniqueId)
    }

    /** 打断全部玩家的动作（关服 / 卸载插件）。 */
    fun cancelAll() {
        for (uuid in ArrayList(actions.keys)) {
            actions.remove(uuid)?.task?.cancel()
        }
        actions.clear()
        bulkTokens.clear()
    }

    /** 是否正在读条。 */
    fun isBusy(player: Player): Boolean = actions.containsKey(player.uniqueId)

    /** 当前动作快照（HUD / 菜单状态行用），没有动作返回 null。 */
    fun snapshot(player: Player): ActionSnapshot? {
        val action = actions[player.uniqueId] ?: return null
        val progress = progressOf(action)
        return ActionSnapshot(action.type, progress, (progress * 100.0).roundToInt())
    }

    /** 进度条文本（ActionBar 与 HUD 共用，避免两处样式不一致导致闪烁感）。 */
    fun progressText(type: ActionType, progress: Double): String {
        val p = progress.coerceIn(0.0, 1.0)
        val percent = (p * 100.0).roundToInt()
        return "<gray>${type.label}中 ${progressBar(p)}<gray> $percent%"
    }

    // ==================== 读条内部 ====================

    private fun progressOf(action: ActiveAction): Double {
        if (action.totalTicks <= 0) return 1.0
        return (action.elapsed.toDouble() / action.totalTicks).coerceIn(0.0, 1.0)
    }

    private fun progressBar(progress: Double): String {
        val filled = (progress * BAR_SEGMENTS).roundToInt().coerceIn(0, BAR_SEGMENTS)
        return buildString {
            append("<#8FD4A6>")
            append("▋".repeat(filled))
            append("</#8FD4A6><#5C6B7A>")
            append("▋".repeat(BAR_SEGMENTS - filled))
            append("</#5C6B7A>")
        }
    }

    private fun tickAction(
        player: Player,
        action: ActiveAction,
        onTick: ((Double) -> Unit)?,
        onFinish: () -> Unit,
    ) {
        // 已被别的动作顶掉 / 被 cancel：静默收掉自己这个计时器
        if (actions[player.uniqueId] !== action) {
            action.task?.cancel()
            return
        }

        // 中断：死亡 / 切主手槽位 / 移动超距离 / 打开别的 GUI —— 一律不结算
        if (!player.isOnline) {
            abort(player, action, Interrupt.OFFLINE)
            return
        }
        if (player.isDead) {
            abort(player, action, Interrupt.DEAD)
            return
        }
        if (player.inventory.heldItemSlot != action.startSlot) {
            abort(player, action, Interrupt.SLOT)
            return
        }
        val currentItem = player.inventory.itemInMainHand
        // 只用「种类 + 实例身份」判断换没换物品：读条期间模型会切成 reload / bolt、
        // 名称会被 GunNameplate 改写，拿 isSimilar 比会把每一条读条都判成被换掉。
        if (currentItem.type != action.startItem.type) {
            abort(player, action, Interrupt.ITEM_CHANGED)
            return
        }
        if (identityChanged(action.startIdentity, currentItem)) {
            abort(player, action, Interrupt.ITEM_CHANGED)
            return
        }
        if (movedTooFar(player, action.startLocation)) {
            abort(player, action, Interrupt.MOVED)
            return
        }
        // 刚起读条的前 [GUI_GRACE_TICKS] tick 不判 GUI：从菜单点按钮起读条时，
        // 「关掉旧界面」与「读条第一 tick」在部分服务端/版本上会差半拍到一拍，
        // 那一拍里 openInventory 还指着旧界面，会把读条当场判死（表现为「一点就提示被打断」）。
        if (action.elapsed >= GUI_GRACE_TICKS && hasForeignGuiOpen(player)) {
            abort(player, action, Interrupt.GUI)
            return
        }

        action.elapsed++
        val progress = progressOf(action)
        try {
            onTick?.invoke(progress)
        } catch (e: Exception) {
            devLog("[Gun] 读条 onTick 异常（${action.type.label}）: ${e.message}")
        }
        // 进度是**连续反馈**，必须留在 ActionBar，不能走副标题（那是给一次性提示用的）：
        // 每 tick 发一条副标题会让屏幕中央不停重播淡入，而且会把注意力从准星上拉走。
        // 主手是枪时由 [GunHud] 把「热量段」换成同一份 progressText 一起渲染（同源、不会两种样式），
        // 这里就不再重复写，避免两个写入者每 tick 互相顶掉。
        if (GunRegistry.gunOf(player.inventory.itemInMainHand) == null) {
            sendProgress(player, progressText(action.type, progress))
        }

        if (action.elapsed < action.totalTicks) return

        // 正常读完：先从表里摘掉，再结算，避免结算里再开动作时把自己顶掉
        if (actions[player.uniqueId] !== action) return
        actions.remove(player.uniqueId)
        action.task?.cancel()
        try {
            onFinish()
        } catch (e: Exception) {
            devLog("[Gun] 读条结算异常（${action.type.label}）: ${e.message}")
        }
    }

    private fun abort(player: Player, action: ActiveAction, interrupt: Interrupt) {
        if (actions[player.uniqueId] !== action) return
        actions.remove(player.uniqueId)
        action.task?.cancel()
        // 服务端日志留一条：玩家报「一换弹就被打断」时，这里能直接看出是哪个判定误伤。
        devLog(
            "[Gun] 读条被打断: type=${action.type.name}, reason=${interrupt.name}, " +
                "player=${player.name}, elapsed=${action.elapsed}/${action.totalTicks}" +
                if (interrupt == Interrupt.GUI) ", gui=${describeOpenGui(player)}" else "",
        )
        if (player.isOnline) {
            sendActionBar(player, "<#E08A98>${action.type.label}被打断 <#6E7A8C>· ${interrupt.reason}")
        }
    }

    /** 当前打开的界面是什么（只用于日志定位「打开了其它界面」到底是哪个界面）。 */
    private fun describeOpenGui(player: Player): String {
        return try {
            val view = player.openInventory
            val holder = view.topInventory.holder
            val holderName = holder?.let { it::class.java.simpleName } ?: "null"
            "${view.type}/holder=$holderName/size=${view.topInventory.size}"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * 主手物品的实例身份是否**确实**换了。
     *
     * 只在两侧都拿得到实例身份、且不相等时才判「换掉了」：
     * 实例身份来自物品 NBT，取不到（`""`）时一律当作「同一件物品」——
     * 拿不到身份不等于换了物品，按它判死读条会把正常换弹误杀成「换弹被打断」。
     */
    private fun identityChanged(startIdentity: String, current: ItemStack): Boolean {
        if (startIdentity.isEmpty()) return false
        val currentIdentity = identityOf(current)
        if (currentIdentity.isEmpty()) return false
        return currentIdentity != startIdentity
    }

    /**
     * 位移是否超过 `magazine.interrupt-distance`。
     *
     * **只算水平位移**：三维距离会把「原地起跳」「上下台阶」「从半格台阶走下来」
     * 都算成移动（一次跳跃 Y 就有 1.2 格，加上水平分量轻松越过 2 格阈值），
     * 于是玩家站着不动、只是跳了一下，读条就判「移动过远」被打断。
     */
    private fun movedTooFar(player: Player, start: Location): Boolean {
        val limit = GunSettings.magazine.interruptDistance.coerceAtLeast(0.0)
        if (limit <= 0.0) return false
        val now = player.location
        if (now.world != start.world) return true
        return try {
            val dx = now.x - start.x
            val dz = now.z - start.z
            dx * dx + dz * dz > limit * limit
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 是否打开了「别人的」GUI。
     *
     * 下面这些**不算**打断，因为读条本来就该在它们开着的时候跑：
     * - 我们自己的箱子菜单（`CTGunMenuHolder`）：弹匣菜单的压弹 / 退弹就在菜单开着时读条；
     * - 玩家自己的背包 / 创造模式物品栏（`topInventory.holder is HumanEntity`）；
     * - 我们自己的**原生对话框**（[GunDialogSessions]）：对话框是纯客户端界面，
     *   服务端容器不变，但屏幕上确实是我们的界面 —— 弹匣对话框点完不关，
     *   `一键退空 / 一键压满` 全程开着它连读，不豁免就会被判成「打开了其它界面」。
     *
     * 除此之外任何容器（箱子、工作台、铁砧……）都会中断读条。
     */
    private fun hasForeignGuiOpen(player: Player): Boolean {
        return try {
            val view = player.openInventory
            val holder = view.topInventory.holder
            when {
                holder is CTGunMenuHolder -> false
                holder is org.bukkit.entity.HumanEntity -> false
                GunDialogSessions.isShown(player) -> false
                else -> view.type != InventoryType.CRAFTING && view.type != InventoryType.CREATIVE
            }
        } catch (_: Exception) {
            false
        }
    }

    // ==================== 换弹 ====================

    /**
     * 快捷栏里的一个候选弹匣（[findReloadCandidate] 的返回值，菜单预览要用到 [mag] 的 id）。
     */
    data class MagCandidate(
        val slot: Int,
        val item: ItemStack,
        val mag: MagData,
        val ammoCount: Int,
        /** Physical magazine identity and ammo snapshot bind the timed reload to its original item. */
        val instanceId: String,
        val ammoSnapshot: List<String>,
    )

    /**
     * 在**整个背包**里找匹配弹匣（快捷栏 0..8 优先，其次主背包 9..35，最后副手 40）。
     *
     * P7 修正：早期版本只搜快捷栏 9 格，弹匣放在主背包时 Q 会表现为「完全没反应」，
     * 而这正是最常见的玩家操作习惯。排序依次为：
     * 是否在快捷栏 → 子弹数降序 → `machineEfficiency` 降序（惩罚小者优先）→ 槽位靠前。
     *
     * 护甲槽（36..39）不在扫描范围内，见 [RELOAD_SCAN_SLOTS]。
     */
    fun findReloadCandidate(player: Player, gun: GunData): MagCandidate? {
        val handSlot = player.inventory.heldItemSlot
        val candidates = ArrayList<MagCandidate>(37)
        for (slot in RELOAD_SCAN_SLOTS) {
            if (slot == handSlot) continue
            val item = try {
                player.inventory.getItem(slot)
            } catch (_: Exception) {
                null
            } ?: continue
            if (item.type.isAir) continue
            val mag = GunRegistry.magOf(item) ?: continue
            if (!mag.caliber.equals(gun.caliber, ignoreCase = true)) continue
            val ammoSnapshot = MagazineCore.readAmmo(item).toList()
            // 扫描只读身份：`instanceId()` 会为没有身份的弹匣生成并落盘，
            // 而这段代码在菜单开着时每 10 tick 就会跑一遍（41 格全扫）。
            val instanceId = MagazineCore.instanceIdOrNull(item).orEmpty()
            candidates += MagCandidate(
                slot = slot,
                item = item,
                mag = mag,
                ammoCount = ammoSnapshot.size,
                instanceId = instanceId,
                ammoSnapshot = ammoSnapshot,
            )
        }
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareByDescending<MagCandidate> { it.slot < 9 }
                .thenByDescending { it.ammoCount }
                .thenByDescending { it.mag.machineEfficiency }
                .thenBy { it.slot }
        ).first()
    }

    /**
     * 换弹失败时给玩家的**具体**原因（Q 没反应时最容易一头雾水）。
     *
     * 扫描范围必须与 [findReloadCandidate] 一致（见 [RELOAD_SCAN_SLOTS]）：否则弹匣
     * 只放在护甲槽时会提示「背包里有 XX 口径弹匣」，而换弹实际上永远不会用它。
     */
    fun reloadBlockReason(player: Player, gun: GunData): String {
        val calibers = HashSet<String>()
        for (slot in RELOAD_SCAN_SLOTS) {
            val item = try {
                player.inventory.getItem(slot)
            } catch (_: Exception) {
                null
            } ?: continue
            if (item.type.isAir) continue
            val mag = GunRegistry.magOf(item) ?: continue
            calibers += mag.caliber
        }
        if (calibers.isEmpty()) {
            return "<#E08A98>背包里没有弹匣 <#A9B4C3>| <#EFDF8F>需要 ${gun.caliber} 口径"
        }
        return "<#E08A98>没有 ${gun.caliber} 口径的弹匣 <#A9B4C3>| <#EFDF8F>背包里有：${calibers.joinToString("、")}"
    }

    /** 换弹时长：`usage.reload * (1.6 - 0.6*MEratio) * (1 - loadingSpeed*0.05)`。 */
    private fun reloadDuration(player: Player, gun: GunData, mag: MagData, fast: Boolean): Double {
        val base = if (fast) gun.usage.reloadFast else gun.usage.reload
        return base.coerceAtLeast(0.0) * (1.6 - 0.6 * meRatio(player, gun, mag)) * loadingSpeedFactor(mag)
    }

    private fun meRatio(player: Player, gun: GunData?, mag: MagData): Double {
        val effectiveMe = gun?.let {
            // 带 player：层级环境会改人机功效，换弹/拉栓耗时应当跟着环境走。
            AddonManager.effectiveAttributes(it, player.inventory.itemInMainHand, player).machineEfficiency
        } ?: 100.0
        val gunMe = (effectiveMe + ArmorManager.totalMachineEfficiencyModifier(player)).coerceIn(0.0, 100.0)
        return ((gunMe + mag.machineEfficiency) / 100.0).coerceIn(0.0, 1.5)
    }

    private fun loadingSpeedFactor(mag: MagData): Double =
        (1.0 - mag.loadingSpeed * 0.05).coerceIn(0.1, 2.0)

    /**
     * 标准换弹（Q）。
     *
     * @param instant 仅调试命令 `/ctt gun reload` 使用：跳过读条，直接结算
     */
    fun reloadStandard(player: Player, held: ItemStack, instant: Boolean = false) {
        val gun = GunRegistry.gunOf(held) ?: return
        val initialState = GunInstance.readEnsured(held, gun) ?: return

        val candidate = findReloadCandidate(player, gun)
        if (candidate == null) {
            sendActionBar(player, reloadBlockReason(player, gun))
            return
        }

        val finish = { performReload(player, gun, initialState.instanceId, candidate, fast = false) }
        if (instant) {
            finish()
            return
        }
        timedAction(
            player = player,
            seconds = reloadDuration(player, gun, candidate.mag, fast = false),
            onFinish = { finish() },
            type = ActionType.RELOAD,
            reject = { reloadReject(player, gun) },
        )
    }

    /**
     * 快速换弹（SHIFT+Q）：旧弹匣**直接丢在脚下**并打上 100 tick 拾取冷却。
     *
     * `mag.allowFastReload == false` 时回落到 [reloadStandard] 并提示。
     */
    fun reloadFast(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        val initialState = GunInstance.readEnsured(held, gun) ?: return

        val candidate = findReloadCandidate(player, gun)
        if (candidate == null) {
            sendActionBar(player, reloadBlockReason(player, gun))
            return
        }
        if (!candidate.mag.allowFastReload) {
            sendActionBar(player, "<#DFBD7C>这个弹匣不支持快速换弹，改用标准换弹")
            reloadStandard(player, held)
            return
        }

        timedAction(
            player = player,
            seconds = reloadDuration(player, gun, candidate.mag, fast = true),
            onFinish = { performReload(player, gun, initialState.instanceId, candidate, fast = true) },
            type = ActionType.RELOAD_FAST,
            reject = { reloadReject(player, gun) },
        )
    }

    private fun reloadReject(player: Player, gun: GunData): String? {
        val held = player.inventory.itemInMainHand
        if (GunRegistry.gunOf(held)?.id != gun.id) return "<#E08A98>主手已经不是这把枪了"
        val state = GunInstance.read(held) ?: return "<#E08A98>枪械数据丢失，无法换弹"
        if (state.durability <= 0.0) return "<#E08A98>枪械已损坏，无法换弹"
        if (state.jam >= 2) return "<#E08A98>枪械存在严重故障，请先排障"
        return null
    }

    private fun performReload(
        player: Player,
        gun: GunData,
        instanceId: String,
        candidate: MagCandidate,
        fast: Boolean,
    ) {
        val held = player.inventory.itemInMainHand
        val currentGun = GunRegistry.gunOf(held)
        if (currentGun == null) {
            sendActionBar(player, "<#E08A98>主手已经不是枪械，换弹已取消")
            return
        }
        if (currentGun.id != gun.id) {
            sendActionBar(player, "<#E08A98>换弹被打断 <#6E7A8C>· 主手换成了别的武器")
            return
        }
        // 实例身份只在「两侧都拿得到」时才算数：取不到身份（NBT 被别的插件重建过）
        // 不能当成「换了另一把枪」，否则读条走完也会被这一句判死。
        val nowInstanceId = GunInstance.read(held)?.instanceId.orEmpty()
        if (instanceId.isNotEmpty() && nowInstanceId.isNotEmpty() && nowInstanceId != instanceId) {
            sendActionBar(player, "<#E08A98>换弹被打断 <#6E7A8C>· 这把枪已经被换掉了")
            return
        }
        // 结算前重新读一次：读条期间可能开过火（热量/弹匣内容都变了）
        val state = GunInstance.readEnsured(held, gun) ?: return
        if (state.durability <= 0.0 || state.jam >= 2) {
            sendActionBar(
                player,
                if (state.durability <= 0.0) "<#E08A98>枪械已损坏，无法换弹"
                else "<#E08A98>枪械存在严重故障，请先排障",
            )
            return
        }

        // 复核新弹匣还在不在那个快捷栏槽位
        val slotItem = try {
            player.inventory.getItem(candidate.slot)
        } catch (_: Exception) {
            null
        }
        val slotMag = if (slotItem == null) null else GunRegistry.magOf(slotItem)
        if (slotItem == null || slotMag == null || slotMag.id != candidate.mag.id) {
            sendActionBar(player, "<#E08A98>弹匣已经不在快捷栏里了")
            return
        }

        if (MagazineCore.instanceId(slotItem) != candidate.instanceId) {
            sendActionBar(player, "<#E08A98>弹匣已被替换，换弹已取消")
            return
        }
        val currentAmmo = MagazineCore.readAmmo(slotItem).toList()
        if (currentAmmo != candidate.ammoSnapshot) {
            sendActionBar(player, "<#E08A98>弹匣内容已改变，换弹已取消")
            return
        }
        if (currentAmmo.any { ammoId ->
                val ammo = GunRegistry.ammo(ammoId)
                ammo == null || !ammo.caliber.equals(gun.caliber, ignoreCase = true)
            }) {
            sendActionBar(player, "<#E08A98>弹匣内含无效或口径不匹配的弹药，换弹已取消")
            return
        }

        val capacity = candidate.mag.capacity.coerceAtLeast(0)
        val newAmmo = currentAmmo.toMutableList()
        val overflowAmmo = if (newAmmo.size > capacity) newAmmo.take(newAmmo.size - capacity) else emptyList()
        val overflowItems = overflowAmmo.map { buildAmmoItem(player, it) }
        if (overflowItems.any { it == null }) {
            sendActionBar(player, "<#E08A98>超容量弹药无法构建，换弹已取消")
            return
        }
        val retainedAmmo = if (capacity > 0) newAmmo.takeLast(capacity) else emptyList()
        newAmmo.clear()
        newAmmo.addAll(retainedAmmo)

        val emptyMag = newAmmo.isEmpty()
        var chamber = if (state.chamber > 0) 1 else 0
        var chamberAmmoId = state.chamberAmmoId.takeIf { chamber > 0 }
        if (!emptyMag && chamber == 0) {
            // 只有空膛才从新弹匣取一发；已有膛内弹时新弹匣必须完整保留。
            chamberAmmoId = newAmmo.removeAt(newAmmo.lastIndex)
            chamber = 1
        }

        // 旧弹匣的剩余子弹要先读出来，否则造回物品后就是空匣
        val oldMagId = state.mag
        val oldMag = GunRegistry.mag(oldMagId)
        val oldCapacity = oldMag?.capacity?.coerceAtLeast(0) ?: 0
        val oldAmmo = state.magAmmo.toList()
        if (oldMagId.isNotEmpty() && oldMag == null) {
            sendActionBar(player, "<#E08A98>旧弹匣配置失效，换弹已取消")
            return
        }
        val oldOverflow = if (oldAmmo.size > oldCapacity) oldAmmo.take(oldAmmo.size - oldCapacity) else emptyList()
        val oldOverflowItems = oldOverflow.map { buildAmmoItem(player, it) }
        if (oldOverflowItems.any { it == null }) {
            sendActionBar(player, "<#E08A98>旧弹匣超容量弹药无法构建，换弹已取消")
            return
        }
        val oldAmmoForMag = oldAmmo.takeLast(oldCapacity)

        // 事务第一步：旧弹匣及其 ammo NBT 必须先完整构建成功，失败则枪和背包都不动。
        val oldItem = if (oldMagId.isNotEmpty()) buildMagItem(player, oldMagId, oldAmmoForMag) else null
        if (oldMagId.isNotEmpty() && oldItem == null) {
            sendActionBar(player, "<#E08A98>旧弹匣构建失败，换弹已取消")
            return
        }

        // 第二步提交枪状态并回读确认，第三步才消耗快捷栏里的新弹匣。
        val newState = state.copy(
            chamber = chamber,
            chamberAmmoId = chamberAmmoId,
            mag = candidate.mag.id,
            magAmmo = newAmmo,
        )
        if (!GunInstance.write(held, newState)) {
            GunInstance.write(held, state)
            sendActionBar(player, "<#E08A98>枪械状态写入失败，换弹已取消")
            return
        }
        if (GunInstance.read(held) != newState) {
            GunInstance.write(held, state)
            sendActionBar(player, "<#E08A98>枪械状态写入失败，换弹已取消")
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
            player.inventory.setItem(candidate.slot, null)
        } catch (e: Exception) {
            devLog("[Gun] 提交换弹物品事务失败: ${e.message}")
            GunInstance.write(held, state)
            try {
                player.inventory.setItemInMainHand(held)
                player.inventory.setItem(candidate.slot, slotItem)
            } catch (_: Exception) {
            }
            sendActionBar(player, "<#E08A98>换弹物品提交失败，换弹已取消")
            return
        }

        if (oldItem != null) {
            val handBack = if (fast) {
                // 快速换弹：旧弹匣直接掉在脚下（带冷却），不占背包。
                if (dropAtFeet(player, oldItem, withCooldown = true)) HandBack.OVERFLOWED else HandBack.FAILED
            } else {
                giveBack(player, oldItem)
            }
            if (handBack == HandBack.FAILED) {
                // 只有「一点都没交出去」才走兜底。此时新枪状态和候选弹匣已经提交，
                // 不能再盲目回滚：回滚可能与已经发生的背包/掉落副作用叠加，造成弹匣复制。
                val recovered = dropAtFeet(player, oldItem, withCooldown = fast)
                if (!recovered) {
                    sendActionBar(player, "<#E08A98>旧弹匣交付失败，请立即检查物品栏")
                    devLog("[Gun] 换弹后旧弹匣无法交付 (gun=${gun.id}, mag=$oldMagId)")
                }
            } else if (handBack == HandBack.OVERFLOWED && !fast) {
                sendActionBar(player, "<#DFBD7C>背包已满，旧弹匣掉在了脚下")
            }
        }
        for (item in oldOverflowItems.filterNotNull() + overflowItems.filterNotNull()) {
            if (giveBack(player, item) == HandBack.FAILED) {
                // 不回滚已经提交的枪械状态；再次尝试掉落，避免溢出弹药静默丢失。
                val recovered = try {
                    player.world.dropItemNaturally(player.location, item)
                    true
                } catch (_: Exception) {
                    false
                }
                if (!recovered) {
                    sendActionBar(player, "<#E08A98>超容量弹药交付失败，请管理员检查日志")
                    devLog("[Gun] 换弹后溢出弹药无法交付 (gun=${gun.id}, ammo=${item.type})")
                }
            }
        }

        if (emptyMag) {
            sendActionBar(player, "<#DFBD7C>换弹完成，但这是一个空弹匣")
        } else {
            sendActionBar(
                player,
                "<#8FD4A6>换弹完成 <#A9B4C3>| <#EFDF8F>${candidate.mag.id} " +
                    "<#A9B4C3>${newAmmo.size}/${capacity} <#6E7A8C>(膛内 $chamber)"
            )
        }
        GunHud.update(player)
    }

    // ==================== 卸弹匣 ====================

    /** 卸下弹匣（`usage.removeMag`），弹匣连同剩余子弹进背包（满则掉地，无拾取冷却）。 */
    fun removeMag(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        val state = GunInstance.readEnsured(held, gun) ?: return
        if (state.mag.isEmpty()) {
            sendActionBar(player, "<#E08A98>枪上没有弹匣")
            return
        }
        timedAction(
            player = player,
            seconds = gun.usage.removeMag,
            onFinish = { finishRemoveMag(player, gun, state.instanceId) },
            type = ActionType.REMOVE_MAG,
        )
    }

    private fun finishRemoveMag(player: Player, gun: GunData, instanceId: String) {
        val held = player.inventory.itemInMainHand
        val currentGun = GunRegistry.gunOf(held)
        if (currentGun == null) {
            sendActionBar(player, "<#E08A98>主手已经不是枪械，卸匣已取消")
            return
        }
        if (currentGun.id != gun.id) {
            sendActionBar(player, "<#E08A98>卸弹匣被打断 <#6E7A8C>· 主手换成了别的武器")
            return
        }
        val nowInstanceId = GunInstance.read(held)?.instanceId.orEmpty()
        if (instanceId.isNotEmpty() && nowInstanceId.isNotEmpty() && nowInstanceId != instanceId) {
            sendActionBar(player, "<#E08A98>卸弹匣被打断 <#6E7A8C>· 这把枪已经被换掉了")
            return
        }
        val state = GunInstance.readEnsured(held, gun) ?: return
        if (state.mag.isEmpty()) {
            sendActionBar(player, "<#E08A98>枪上没有弹匣")
            return
        }

        val magId = state.mag
        val ammo = state.magAmmo.toList()
        // 先造出带完整 ammo NBT 的旧弹匣，构建失败绝不清空枪上的弹匣。
        val item = buildMagItem(player, magId, ammo)
        if (item == null) {
            sendActionBar(player, "<#E08A98>弹匣物品构建失败，卸匣已取消")
            return
        }

        val removedState = state.copy(mag = "", magAmmo = mutableListOf())
        GunInstance.write(held, removedState)
        if (GunInstance.read(held) != removedState) {
            GunInstance.write(held, state)
            sendActionBar(player, "<#E08A98>枪械状态写入失败，卸匣已取消")
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (e: Exception) {
            devLog("[Gun] 卸匣写回主手失败: ${e.message}")
            GunInstance.write(held, state)
            sendActionBar(player, "<#E08A98>卸匣提交失败，已保留原弹匣")
            return
        }

        when (giveBack(player, item)) {
            HandBack.BAGGED -> sendActionBar(player, "<#8FD4A6>已卸下弹匣 <#EFDF8F>$magId")
            // 溢出部分已经掉在脚下：弹匣有归宿，只是没全进背包。
            HandBack.OVERFLOWED -> sendActionBar(player, "<#DFBD7C>背包已满，弹匣 <#EFDF8F>$magId</#EFDF8F><#DFBD7C> 掉在了脚下")
            // 枪上的 mag/magAmmo 已经在上面清空了：归还与掉落兜底双失败时弹匣是真的没了，
            // 不能还报「已卸下」。另两处归还（换上弹匣 / 退弹）都有失败分支，这里漏了。
            HandBack.FAILED -> {
                devLog("[Gun] 卸匣归还失败，弹匣丢失 id=$magId")
                sendActionBar(player, "<#E08A98>背包已满且掉落失败，弹匣 <#EFDF8F>$magId</#EFDF8F><#E08A98> 已丢失")
            }
        }
        GunHud.update(player)
    }

    // ==================== 拉栓 / 排障 / 切模式 ====================

    /**
     * 拉栓：`usage.bolt` 秒后执行 [completeBolt]（菜单里的「拉栓/退膛」）。
     *
     * **P3 语义修正**：拉栓是「退掉旧壳 + 上一发新弹」，所以完成时会
     * `jam = 0` 并尝试从弹匣末位消耗一发推进膛（见 [completeBolt]）。
     */
    fun bolt(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        val state = GunInstance.readEnsured(held, gun) ?: return
        timedAction(
            player = player,
            seconds = gun.usage.bolt,
            onFinish = { completeBolt(player, gun, state.instanceId) },
            type = ActionType.BOLT,
        )
    }

    /**
     * 拉栓结算（P3）：菜单路径（[bolt]）与右键路径（`GunListener`）**共用这一份**。
     *
     * ```
     * jam = 0
     * chamber 已有弹 → 只解除卡壳，膛内与弹匣均不变
     * chamber 为空且弹匣非空 → 消耗末位一发，chamber = 1
     * chamber 为空且弹匣为空 → chamber 保持 0，提示「弹匣已空，无法上膛」
     * ```
     * 注意这里**不检查** `jam`：调用方负责「只有 jam == 1 才允许拉栓」这一层
     * （菜单按钮 / 右键分支都做了），否则 `jam == 2` 的严重故障会被拉栓白嫖掉。
     */
    fun completeBolt(player: Player, gun: GunData, instanceId: String) {
        val held = player.inventory.itemInMainHand
        if (GunRegistry.gunOf(held)?.id != gun.id || GunInstance.read(held)?.instanceId != instanceId) return
        val state = GunInstance.read(held) ?: return
        if (state.jam >= 2) {
            sendActionBar(player, "<#E08A98>严重故障需要排障，不能直接拉栓")
            return
        }

        val ammo = state.magAmmo.toMutableList()
        // chamber=1 但没有有效弹种属于损坏/旧格式状态，按空膛恢复，避免永久卡死。
        var chamber = if (state.chamber > 0 && !state.chamberAmmoId.isNullOrBlank()) 1 else 0
        var chamberAmmoId = state.chamberAmmoId.takeIf { chamber > 0 }
        // 卡壳发生在膛内已有弹时，拉栓只解除卡壳，不覆盖或额外消耗弹匣。
        // 只有空膛时才从弹匣末位取一发上膛。
        if (chamber == 0 && ammo.isNotEmpty()) {
            chamberAmmoId = ammo.removeAt(ammo.lastIndex)
            chamber = 1
        }
        if (!GunInstance.write(
                held,
                state.copy(jam = 0, chamber = chamber, chamberAmmoId = chamberAmmoId, magAmmo = ammo),
            )
        ) {
            sendActionBar(player, "<#E08A98>拉栓失败，枪械状态未改变")
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (_: Exception) {
        }

        if (chamber <= 0) {
            sendActionBar(player, "<#E08A98>弹匣已空，无法上膛")
        } else if (ammo.isEmpty()) {
            sendActionBar(player, "<#8FD4A6>拉栓完成 <#A9B4C3>| <#EFDF8F>膛内已上膛（弹匣已空）")
        } else {
            sendActionBar(player, "<#8FD4A6>拉栓完成 <#A9B4C3>| <#EFDF8F>膛内已上膛")
        }
        GunHud.update(player)
    }

    /**
     * 排障：**不消耗任何材料**（用户拍板），流程是
     * 「菜单里点排障 → 读条 `usage.fix` 秒 → 故障降级为卡壳（`jam = 1`）→ 拉栓一次彻底解决」。
     *
     * 设计取舍：
     * - 排障只把 `jam` 从 2 降到 1，**不直接清零**，这样「拉栓」这一步不会被跳过，
     *   与玩家在菜单里看到的操作顺序一致；
     * - 耐久归零时顺手把耐久抬到上限的 50%，避免修好故障却还是把废枪；
     * - 故意**不走** [timedAction] 的 `reject` 钩子（换弹用的 `reloadReject`
     *   会拒绝「枪械已损坏」），否则损坏的枪永远修不好。
     */
    fun fix(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        val state = GunInstance.readEnsured(held, gun) ?: return
        if (!needsFix(state)) {
            sendActionBar(player, "<#DFBD7C>当前不需要排障")
            return
        }
        timedAction(
            player = player,
            seconds = gun.usage.fix,
            onFinish = { finishFix(player, gun, state.instanceId) },
            type = ActionType.FIX,
        )
    }

    /** 是否需要排障（P3 宽松条件：严重故障 **或** 耐久归零）。菜单可用条件也复用它。 */
    fun needsFix(state: GunState): Boolean = state.jam >= 2 || state.durability <= 0.0

    private fun finishFix(player: Player, gun: GunData, instanceId: String) {
        val held = player.inventory.itemInMainHand
        if (GunRegistry.gunOf(held)?.id != gun.id || GunInstance.read(held)?.instanceId != instanceId) return
        val state = GunInstance.read(held) ?: return
        if (!needsFix(state)) return
        val maxDurability = AddonManager.effectiveAttributes(gun, held).durability.coerceAtLeast(0.0)
        val repaired = maxOf(state.durability, maxDurability * 0.5)
        // 排障只把严重故障降级成「卡壳」，最后一步拉栓由玩家自己完成（不消耗材料）。
        val nextJam = if (state.jam >= 2) 1 else state.jam
        if (!GunInstance.write(held, state.copy(jam = nextJam, durability = repaired, worn = 0.0))) {
            sendActionBar(player, "<#E08A98>排障失败，枪械状态未改变")
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (_: Exception) {
        }
        // 耐久与故障状态都变了：同步刷新物品名称上的耐久/标记
        GunNameplate.refreshHeld(player, held, gun)
        sendActionBar(
            player,
            "<#8FD4A6>排障完成 <#A9B4C3>| <#EFDF8F>耐久 ${num(repaired)}/${num(maxDurability)}" +
                if (nextJam == 1) "<#A9B4C3> | <#EFDF8F>再拉栓一次即可恢复" else "",
        )
        GunHud.update(player)
    }

    /** 切换射击模式：`usage.fireMode` 后 `fireMode` 循环 +1。 */
    fun cycleFireMode(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        if (gun.modes.size <= 1) {
            sendActionBar(player, "<#DFBD7C>该枪械没有其它射击模式")
            return
        }
        val state = GunInstance.readEnsured(held, gun) ?: return
        timedAction(
            player = player,
            seconds = gun.usage.fireMode,
            onFinish = { finishFireMode(player, gun, state.instanceId) },
            type = ActionType.FIRE_MODE,
        )
    }

    private fun finishFireMode(player: Player, gun: GunData, instanceId: String) {
        val held = player.inventory.itemInMainHand
        if (GunRegistry.gunOf(held)?.id != gun.id || GunInstance.read(held)?.instanceId != instanceId) return
        val state = GunInstance.read(held) ?: return
        val modes = gun.modes
        if (modes.size <= 1) return
        val next = ((state.fireMode % modes.size) + 1) % modes.size
        GunShoot.resetTrigger(player)
        if (!GunInstance.write(held, state.copy(fireMode = next))) {
            sendActionBar(player, "<#E08A98>射击模式写入失败")
            return
        }
        try {
            player.inventory.setItemInMainHand(held)
        } catch (_: Exception) {
        }
        sendActionBar(player, "<#8FD4A6>射击模式 <#EFDF8F>${modes[next]}")
        GunHud.update(player)
    }

    /** 当前射击模式名（HUD / 菜单用）。 */
    fun fireModeName(gun: GunData, fireMode: Int): String {
        if (gun.modes.isEmpty()) return "SEMI"
        return gun.modes[fireMode.coerceIn(0, gun.modes.size - 1)]
    }

    // ==================== 检视 ====================

    /** 检视弹仓：报「膛内有弹 / 空膛」+ 故障状态。 */
    fun inspectChamber(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        GunInstance.readEnsured(held, gun) ?: return
        timedAction(
            player = player,
            seconds = gun.usage.inspectChamber,
            onFinish = { finishInspectChamber(player) },
            type = ActionType.INSPECT_CHAMBER,
        )
    }

    private fun finishInspectChamber(player: Player) {
        val held = player.inventory.itemInMainHand
        GunRegistry.gunOf(held) ?: return
        val state = GunInstance.read(held) ?: return
        val chamber = if (state.chamber > 0) "<#8FD4A6>膛内有弹" else "<#DFBD7C>空膛"
        val jam = when (state.jam) {
            1 -> " <gray>|</gray> <#E08A98>卡壳"
            2 -> " <gray>|</gray> <#E08A98>需排障"
            else -> " <gray>|</gray> <#8FD4A6>击发正常"
        }
        sendActionBar(player, "<gray>检视弹仓 <gray>|</gray> $chamber$jam")
        GunHud.update(player)
    }

    /** 检视弹匣：时长用 `mag.ammoInspectionTime` 覆盖 `usage.inspectMag`。 */
    fun inspectMag(player: Player, held: ItemStack) {
        val gun = GunRegistry.gunOf(held) ?: return
        val state = GunInstance.readEnsured(held, gun) ?: return
        if (state.mag.isEmpty()) {
            sendActionBar(player, "<#E08A98>枪上没有弹匣")
            return
        }
        val mag = GunRegistry.mag(state.mag)
        val seconds = mag?.ammoInspectionTime?.takeIf { it > 0.0 } ?: gun.usage.inspectMag
        timedAction(
            player = player,
            seconds = seconds,
            onFinish = { finishInspectMag(player) },
            type = ActionType.INSPECT_MAG,
        )
    }

    private fun finishInspectMag(player: Player) {
        val held = player.inventory.itemInMainHand
        GunRegistry.gunOf(held) ?: return
        val state = GunInstance.read(held) ?: return
        if (state.mag.isEmpty()) {
            sendActionBar(player, "<#E08A98>枪上没有弹匣")
            return
        }
        sendActionBar(player, MagazineCore.inspectTextOf(GunRegistry.mag(state.mag), state.magAmmo))
        GunHud.update(player)
    }

    /**
     * 检视**手里这个弹匣物品**（弹匣菜单用；与枪械菜单的检视共用
     * [MagazineCore.inspectTextOf] 的判定，所以两处读数规则完全一致）。
     *
     * 时长取 `mag.ammoInspectionTime`，没有配置时回落到 `usage.inspectMag` 的默认值。
     */
    fun inspectMagItem(player: Player, magItem: ItemStack) {
        val mag = GunRegistry.magOf(magItem) ?: return
        val seconds = mag.ammoInspectionTime.takeIf { it > 0.0 } ?: DEFAULT_USAGE.inspectMag
        timedAction(
            player = player,
            seconds = seconds,
            onFinish = { finishInspectMagItem(player, mag.id) },
            type = ActionType.INSPECT_MAG,
        )
    }

    private fun finishInspectMagItem(player: Player, magId: String) {
        val target = resolveHeldMag(player)
        if (target == null) {
            sendActionBar(player, "<#E08A98>弹匣已经不在主手了")
            return
        }
        if (GunRegistry.magOf(target)?.id != magId) return
        sendActionBar(player, MagazineCore.inspectText(player, target))
        GunHud.update(player)
    }

    // ==================== 压弹 / 退弹 ====================

    /**
     * 压一发子弹进弹匣（弹匣菜单单发用）。
     *
     * 时长 `usage.insertAmmo * (1 - loadingSpeed*0.05) * (1.0 + (1-MEratio)*0.5)`。
     *
     * @param onDone 结算完成后的回调（批量压弹靠它链式循环）；被打断时不会调用
     * @return false 表示前置校验就没过（没读条）
     */
    fun insertRound(
        player: Player,
        magItem: ItemStack,
        ammoId: String,
        onDone: (() -> Unit)? = null,
    ): Boolean {
        val mag = GunRegistry.magOf(magItem) ?: run {
            sendActionBar(player, "<#E08A98>这不是一个可识别的弹匣")
            return false
        }
        val ammo = GunRegistry.ammo(ammoId) ?: run {
            sendActionBar(player, "<#E08A98>未知子弹：$ammoId")
            return false
        }
        if (!ammo.caliber.equals(mag.caliber, ignoreCase = true)) {
            sendActionBar(
                player,
                "<#E08A98>口径不匹配（弹匣 <#EFDF8F>${mag.caliber}</#EFDF8F><#E08A98> / " +
                    "子弹 <#EFDF8F>${ammo.caliber}</#EFDF8F><#E08A98>）"
            )
            return false
        }
        val capacity = mag.capacity.coerceAtLeast(0)
        if (capacity <= 0) {
            sendActionBar(player, "<#E08A98>这个弹匣没有容量")
            return false
        }
        if (MagazineCore.readAmmo(magItem).size >= capacity) {
            sendActionBar(player, "<#DFBD7C>弹匣已满")
            return false
        }
        if (countAmmo(player, ammoId) <= 0) {
            sendActionBar(player, "<#E08A98>背包里没有 <#EFDF8F>$ammoId</#EFDF8F><#E08A98> 了")
            return false
        }

        val seconds = ammoMoveDuration(player, mag, insert = true)
        val boundIdentity = MagazineCore.instanceIdOrNull(magItem).orEmpty()
        timedAction(
            player = player,
            seconds = seconds,
            onFinish = {
                if (finishInsertRound(player, mag.id, boundIdentity, ammoId, capacity)) onDone?.invoke()
            },
            type = ActionType.INSERT_AMMO,
        )
        return true
    }

    /**
     * 压弹结算。
     *
     * @param magInstanceId 起读条时那个弹匣的实例身份。只比 `mag.id` 是不够的：
     *   玩家有两个同型号弹匣时，读条期间把主手换成另一个也会通过校验，子弹就这么进了别的弹匣。
     *   退弹 / 批量链路（[finishRemoveRound]、[stepBulkRemove]）一直是双重校验，这里对齐。
     */
    private fun finishInsertRound(
        player: Player,
        magId: String,
        magInstanceId: String,
        ammoId: String,
        capacity: Int,
    ): Boolean {
        val target = resolveHeldMag(player)
        if (target == null) {
            sendActionBar(player, "<#E08A98>弹匣已经不在主手了")
            return false
        }
        if (GunRegistry.magOf(target)?.id != magId) return false
        if (magInstanceId.isNotEmpty() &&
            MagazineCore.instanceIdOrNull(target).orEmpty() != magInstanceId
        ) {
            sendActionBar(player, "<#E08A98>弹匣已被替换，压弹已取消")
            return false
        }
        if (MagazineCore.readAmmo(target).size >= capacity) return false

        if (!MagazineCore.push(target, ammoId, capacity)) {
            sendActionBar(player, "<#DFBD7C>弹匣已满")
            return false
        }
        if (!consumeAmmo(player, ammoId, 1)) {
            // 扣不到子弹就把刚压进去的那发退回来，绝不让玩家凭空丢子弹
            val rolledBack = MagazineCore.pop(target)
            if (rolledBack != ammoId) devLog("[Gun] 压弹失败后回滚子弹失败 (id=$ammoId)")
            try {
                player.inventory.setItemInMainHand(target)
            } catch (_: Exception) {
            }
            sendActionBar(player, "<#E08A98>背包里没有 <#EFDF8F>$ammoId</#EFDF8F><#E08A98> 了")
            return false
        }
        try {
            player.inventory.setItemInMainHand(target)
        } catch (_: Exception) {
        }
        val now = MagazineCore.readAmmo(target).size
        sendActionBar(
            player,
            "<#8FD4A6>压入一发 <#EFDF8F>$ammoId <#A9B4C3>| <#F2F5FA>$now/$capacity"
        )
        GunHud.update(player)
        return true
    }

    /**
     * 从弹匣退一发子弹给玩家（背包满则掉地，无拾取冷却）。
     *
     * @param onDone 结算完成后的回调（批量退弹靠它链式循环）
     */
    fun removeRound(player: Player, magItem: ItemStack, onDone: (() -> Unit)? = null): Boolean {
        val mag = GunRegistry.magOf(magItem) ?: run {
            sendActionBar(player, "<#E08A98>这不是一个可识别的弹匣")
            return false
        }
        if (MagazineCore.readAmmo(magItem).isEmpty()) {
            sendActionBar(player, "<#DFBD7C>弹匣已经空了")
            return false
        }
        val instanceId = MagazineCore.instanceId(magItem)
        timedAction(
            player = player,
            seconds = ammoMoveDuration(player, mag, insert = false),
            onFinish = {
                if (finishRemoveRound(player, mag.id, instanceId)) onDone?.invoke()
            },
            type = ActionType.REMOVE_AMMO,
        )
        return true
    }

    private fun finishRemoveRound(player: Player, magId: String, instanceId: String): Boolean {
        val target = resolveHeldMag(player)
        if (target == null) {
            sendActionBar(player, "<#E08A98>弹匣已经不在主手了")
            return false
        }
        if (GunRegistry.magOf(target)?.id != magId || MagazineCore.instanceId(target) != instanceId) return false
        val before = MagazineCore.readAmmo(target)
        val ammoId = before.lastOrNull() ?: run {
            sendActionBar(player, "<#DFBD7C>弹匣已经空了")
            return false
        }
        // 子弹物品必须先构建成功，失败时不能 pop。
        val item = buildAmmoItem(player, ammoId)
        if (item == null) {
            sendActionBar(player, "<#E08A98>子弹物品构建失败，退弹已取消")
            return false
        }
        val popped = MagazineCore.pop(target)
        if (popped == null) {
            sendActionBar(player, "<#DFBD7C>弹匣已经空了")
            return false
        }
        if (MagazineCore.readAmmo(target) != before.dropLast(1)) {
            // MagazineCore 会吞 NBT 异常；未确认扣减前绝不能交付构建出的子弹。
            val restored = MagazineCore.writeAmmo(target, before) && MagazineCore.readAmmo(target) == before
            if (!restored) {
                devLog("[Gun][ERROR] 退弹提交失败且无法恢复弹匣内容 (mag=$magId, ammo=$ammoId)")
                sendActionBar(player, "<#E08A98>退弹失败且弹匣恢复异常，请检查弹匣")
            } else {
                sendActionBar(player, "<#E08A98>弹匣写入失败，退弹已取消")
            }
            return false
        }
        val handBack = giveBack(player, item)
        if (handBack != HandBack.BAGGED) {
            // 注意：子弹此时**已经有归属**（要么溢出掉了、要么彻底丢失），
            // 绝不能压回弹匣，否则就是「地上一个 + 弹匣里一个」的复制。
            try {
                player.inventory.setItemInMainHand(target)
            } catch (_: Exception) {
            }
            GunHud.update(player)
            sendActionBar(
                player,
                if (handBack == HandBack.OVERFLOWED)
                    "<#DFBD7C>背包已满，这发 <#EFDF8F>$ammoId</#EFDF8F><#DFBD7C> 掉在了脚下"
                else "<#E08A98>这发 <#EFDF8F>$ammoId</#EFDF8F><#E08A98> 交付失败，请检查物品栏"
            )
            return true
        }
        try {
            player.inventory.setItemInMainHand(target)
        } catch (_: Exception) {
        }
        val now = MagazineCore.readAmmo(target).size
        val capacity = MagazineCore.capacity(target)
        sendActionBar(
            player,
            "<#8FD4A6>退出一发 <#EFDF8F>$ammoId <#A9B4C3>| <#F2F5FA>$now/$capacity"
        )
        GunHud.update(player)
        return true
    }

    /** 压弹 / 退弹的时长（两者共用同一套 ME / loadingSpeed 修正）。 */
    private fun ammoMoveDuration(player: Player, mag: MagData, insert: Boolean): Double {
        val heldGun = GunRegistry.gunOf(player.inventory.itemInMainHand)
        val base = if (insert) {
            heldGun?.usage?.insertAmmo ?: DEFAULT_USAGE.insertAmmo
        } else {
            heldGun?.usage?.removeAmmo ?: DEFAULT_USAGE.removeAmmo
        }
        val ratio = meRatio(player, heldGun, mag)
        return base.coerceAtLeast(0.0) * loadingSpeedFactor(mag) * (1.0 + (1.0 - ratio) * 0.5)
    }

    // ==================== 批量压弹 / 退弹 ====================

    /**
     * 批量压入：从背包里找到第一种匹配口径的子弹，然后**链式**调用 [insertRound]
     * 直到「弹匣满」或「子弹用完」。
     *
     * 链式而不是一个长读条，是为了让「每一步都可打断」：玩家移动 / 关菜单 /
     * 点别的按钮都会打断当前这一步，而打断的步不会结算、也不会继续循环。
     *
     * @param onStopped 批量链**结束时**回调一次（满 / 没子弹 / 被打断都算）；对话框菜单靠它刷新数字
     * @return false 表示一发都压不了（没子弹 / 弹匣满）
     */
    fun bulkInsert(player: Player, magItem: ItemStack, onStopped: (() -> Unit)? = null): Boolean {
        val mag = GunRegistry.magOf(magItem) ?: return false
        val capacity = mag.capacity.coerceAtLeast(0)
        if (capacity <= 0) {
            sendActionBar(player, "<#E08A98>这个弹匣没有容量")
            return false
        }
        val ammoId = firstMatchingAmmo(player, mag) ?: run {
            sendActionBar(player, "<#E08A98>背包里没有匹配 <#EFDF8F>${mag.caliber}</#EFDF8F><#E08A98> 的子弹")
            return false
        }
        val token = nextBulkToken(player)
        stepBulkInsert(player, ammoId, capacity, token, onStopped)
        return true
    }

    private fun stepBulkInsert(
        player: Player,
        ammoId: String,
        capacity: Int,
        token: Long,
        onStopped: (() -> Unit)? = null,
    ) {
        val stop = {
            stopBulk(player)
            onStopped?.invoke()
        }
        if (!player.isOnline || bulkTokens[player.uniqueId] != token) {
            onStopped?.invoke()
            return
        }
        val target = resolveHeldMag(player) ?: run {
            onStopped?.invoke()
            return
        }
        if (MagazineCore.readAmmo(target).size >= capacity) {
            sendActionBar(player, "<#DFBD7C>弹匣已满")
            stop()
            return
        }
        if (countAmmo(player, ammoId) <= 0) {
            sendActionBar(player, "<#E08A98>子弹已用完")
            stop()
            return
        }
        val started = insertRound(player, target, ammoId, onDone = {
            stepBulkInsert(player, ammoId, capacity, token, onStopped)
        })
        // timedAction 启动时会 cancel 旧动作及旧 token；本步成功启动后恢复当前代次。
        if (started && player.isOnline) bulkTokens[player.uniqueId] = token else stop()
    }

    /** 批量退出：链式调用 [removeRound] 直到弹匣空。 */
    fun bulkRemove(player: Player, magItem: ItemStack, onStopped: (() -> Unit)? = null): Boolean {
        GunRegistry.magOf(magItem) ?: return false
        if (MagazineCore.readAmmo(magItem).isEmpty()) {
            sendActionBar(player, "<#DFBD7C>弹匣已经空了")
            return false
        }
        val magId = GunRegistry.magOf(magItem)?.id ?: return false
        val instanceId = MagazineCore.instanceId(magItem)
        val token = nextBulkToken(player)
        stepBulkRemove(player, magId, instanceId, token, onStopped)
        return true
    }

    private fun stepBulkRemove(
        player: Player,
        magId: String,
        instanceId: String,
        token: Long,
        onStopped: (() -> Unit)? = null,
    ) {
        val stop = {
            stopBulk(player)
            onStopped?.invoke()
        }
        if (!player.isOnline || bulkTokens[player.uniqueId] != token) {
            onStopped?.invoke()
            return
        }
        val target = resolveHeldMag(player) ?: run {
            onStopped?.invoke()
            return
        }
        if (GunRegistry.magOf(target)?.id != magId || MagazineCore.instanceId(target) != instanceId ||
            MagazineCore.readAmmo(target).isEmpty()
        ) {
            stop()
            return
        }
        val started = removeRound(player, target, onDone = {
            stepBulkRemove(player, magId, instanceId, token, onStopped)
        })
        if (started && player.isOnline) bulkTokens[player.uniqueId] = token else stop()
    }

    /** 终止批量循环（关菜单 / 切模式 / 打开其它 GUI 时调用）。 */
    fun cancelBulk(player: Player) {
        stopBulk(player)
    }

    private fun stopBulk(player: Player) {
        bulkTokens.remove(player.uniqueId)
    }

    private fun nextBulkToken(player: Player): Long {
        val next = ++bulkSeq
        bulkTokens[player.uniqueId] = next
        return next
    }

    // ==================== 物品 / 背包工具 ====================

    /** 用 Sertraline 造弹匣物品，并把剩余子弹写进去（读条换下来的旧弹匣靠这个保住弹药）。 */
    private fun buildMagItem(player: Player, magId: String, ammo: List<String>): ItemStack? {
        val item = try {
            Sertraline.api().buildItem(magId, player)
        } catch (e: Exception) {
            devLog("[Gun] 构建弹匣物品失败 (id=$magId): ${e.message}")
            null
        } ?: return null
        return try {
            // 空列表也要写，避免模板自带弹药；回读确认吞异常时不会误判成功。
            MagazineCore.writeAmmo(item, ammo)
            if (MagazineCore.readAmmo(item) != ammo.toList()) {
                devLog("[Gun] 弹匣 ammo 写回校验失败 (id=$magId)")
                null
            } else item
        } catch (e: Exception) {
            devLog("[Gun] 写入弹匣物品失败 (id=$magId): ${e.message}")
            null
        }
    }

    private fun buildAmmoItem(player: Player, ammoId: String): ItemStack? = try {
        Sertraline.api().buildItem(ammoId, player, amount = 1)
    } catch (e: Exception) {
        devLog("[Gun] 构建子弹物品失败 (id=$ammoId): ${e.message}")
        null
    }

    /**
     * 归还物品的三种结果。
     *
     * 用枚举而不是 Boolean，是因为「全进背包」「进一部分、其余掉在脚下」「彻底失败」
     * 这三种情况调用方要给不同反馈，而旧的 Boolean 让调用方无法区分后两者 ——
     * 于是出现过「函数内部已经掉落、调用方又整件掉一次」的复制漏洞。
     */
    private enum class HandBack {
        /** 全部进了背包。 */
        BAGGED,

        /** 进了一部分，其余已经带拾取冷却掉在脚下（物品已有唯一归属，不要再交付）。 */
        OVERFLOWED,

        /** 一点都没交出去，调用方可以再尝试一次掉落兜底。 */
        FAILED,
    }

    /**
     * 进背包，装不下的带拾取冷却掉在脚下。
     *
     * 返回值语义见 [HandBack]：只有 [HandBack.FAILED] 才代表这件物品还没被安置、
     * 调用方可以再掉一次；其余两种情况都**不能**再交付，否则就是复制。
     */
    private fun giveBack(player: Player, item: ItemStack): HandBack {
        return try {
            val leftover = player.inventory.addItem(item)
            if (leftover.isEmpty()) return HandBack.BAGGED
            var droppedAll = true
            for (rest in leftover.values) {
                if (!dropAtFeet(player, rest, withCooldown = true)) droppedAll = false
            }
            devLog(
                "[Gun] 背包已满，溢出的 ${item.type} ×${leftover.values.sumOf { it.amount }} " +
                    if (droppedAll) "已掉在脚下" else "掉落失败，需管理员检查"
            )
            // 掉落也失败说明溢出部分没有归属，交给调用方兜底；否则视为已安置。
            if (droppedAll) HandBack.OVERFLOWED else HandBack.FAILED
        } catch (e: Exception) {
            devLog("[Gun] 归还物品失败: ${e.message}")
            try {
                player.world.dropItemNaturally(player.location, item)
                HandBack.OVERFLOWED
            } catch (dropError: Exception) {
                devLog("[Gun] 归还物品且掉落兜底均失败: ${dropError.message}")
                HandBack.FAILED
            }
        }
    }

    /** 掉在脚下；[withCooldown] 为 true 时打上拾取冷却标记。 */
    private fun dropAtFeet(player: Player, item: ItemStack, withCooldown: Boolean): Boolean {
        return try {
            if (withCooldown) MagazineCore.markDropped(item, player.world.fullTime)
            player.world.dropItem(player.location.clone().add(0.0, 0.5, 0.0), item)
            true
        } catch (e: Exception) {
            devLog("[Gun] 丢出弹匣失败: ${e.message}")
            false
        }
    }

    private fun resolveHeldMag(player: Player): ItemStack? {
        val held = try {
            player.inventory.itemInMainHand
        } catch (_: Exception) {
            null
        } ?: return null
        return if (GunRegistry.magOf(held) != null) held else null
    }

    /** 背包里某种子弹的总发数。 */
    fun countAmmo(player: Player, ammoId: String): Int {
        var total = 0
        val inv = player.inventory
        for (i in 0 until inv.size) {
            val stack = try {
                inv.getItem(i)
            } catch (_: Exception) {
                null
            } ?: continue
            if (stack.type.isAir) continue
            if (GunRegistry.ammoOf(stack)?.id != ammoId) continue
            total += stack.amount
        }
        return total
    }

    /** 从背包扣掉 [amount] 发 [ammoId]（从靠前的槽位开始扣）。 */
    private fun consumeAmmo(player: Player, ammoId: String, amount: Int): Boolean {
        var need = amount
        if (need <= 0) return true
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (need <= 0) break
            val stack = try {
                inv.getItem(i)
            } catch (_: Exception) {
                null
            } ?: continue
            if (stack.type.isAir) continue
            if (GunRegistry.ammoOf(stack)?.id != ammoId) continue
            val take = minOf(need, stack.amount)
            stack.amount = stack.amount - take
            need -= take
            if (stack.amount <= 0) {
                inv.setItem(i, null)
            } else {
                inv.setItem(i, stack)
            }
        }
        return need <= 0
    }

    /** 背包里第一种匹配该弹匣口径的子弹 ID。 */
    fun firstMatchingAmmo(player: Player, mag: MagData): String? {
        val inv = player.inventory
        for (i in 0 until inv.size) {
            val stack = try {
                inv.getItem(i)
            } catch (_: Exception) {
                null
            } ?: continue
            if (stack.type.isAir) continue
            val ammo = GunRegistry.ammoOf(stack) ?: continue
            if (!ammo.caliber.equals(mag.caliber, ignoreCase = true)) continue
            return ammo.id
        }
        return null
    }

    // ==================== 维修材料 ====================

    /** 背包里是否凑齐了维修材料。 */
    fun hasMaterials(player: Player, need: Map<String, Int>): Boolean {
        if (need.isEmpty()) return true
        return need.all { (name, count) -> countMaterial(player, name) >= count }
    }

    /** 维修材料的中文提示文本。 */
    fun materialText(need: Map<String, Int>): String =
        if (need.isEmpty()) "无"
        else need.entries.joinToString("、") { "${it.key}×${it.value}" }

    private fun countMaterial(player: Player, name: String): Int {
        val type = materialOf(name) ?: return 0
        var total = 0
        val inv = player.inventory
        for (i in 0 until inv.size) {
            val stack = try {
                inv.getItem(i)
            } catch (_: Exception) {
                null
            } ?: continue
            if (stack.type == type) total += stack.amount
        }
        return total
    }

    private fun consumeMaterials(player: Player, need: Map<String, Int>): Boolean {
        if (!hasMaterials(player, need)) return false
        val inv = player.inventory
        for ((name, count) in need) {
            val type = materialOf(name) ?: continue
            var left = count
            for (i in 0 until inv.size) {
                if (left <= 0) break
                val stack = try {
                    inv.getItem(i)
                } catch (_: Exception) {
                    null
                } ?: continue
                if (stack.type != type) continue
                val take = minOf(left, stack.amount)
                stack.amount = stack.amount - take
                left -= take
                if (stack.amount <= 0) {
                    inv.setItem(i, null)
                } else {
                    inv.setItem(i, stack)
                }
            }
        }
        return true
    }

    private fun materialOf(name: String): Material? = try {
        Material.matchMaterial(name)
    } catch (_: Exception) {
        null
    }

    // ==================== 文本 ====================

    /** 数值格式化（整数不带小数点，其余保留一位），给耐久等展示用。 */
    private fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    private fun sendActionBar(player: Player, message: String) {
        try {
            // 一次性提示（拒绝原因 / 完成 / 被打断）→ 副标题，避免顶掉 HUD 状态栏
            GunPrompt.show(player, message)
        } catch (_: Exception) {
        }
    }

    /** 连续进度 → ActionBar（与 [GunHud] 同一通道、同一份文案）。 */
    private fun sendProgress(player: Player, message: String) {
        try {
            (player as Audience).sendActionBar(mmUtil.deserialize(message.legacyToMiniMessage()))
        } catch (_: Exception) {
        }
    }
}
