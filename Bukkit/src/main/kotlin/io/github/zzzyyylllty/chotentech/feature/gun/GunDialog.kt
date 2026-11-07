package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import java.util.UUID

/**
 * 枪械菜单的**对话框（Dialog）版**入口。
 *
 * ### 为什么要有这一层
 * Paper 1.21.6 起提供了原生对话框 API（`io.papermc.paper.dialog.Dialog`），
 * 它是客户端原生界面：不用开背包、不会打断读条、支持按钮 tooltip、中键/ESC 行为由客户端管，
 * 比「27 格箱子 + 每 10 tick 重画」自然得多。
 *
 * ### 兼容策略
 * 老服务端（< 1.21.6 或 adventure 太旧）里 `Dialog` 类根本不存在，
 * 所以这里做**两段式**：
 * 1. [GunDialog.supported] 只用反射探测能力，**不加载**任何 Dialog 类型；
 * 2. 真正的构建代码全部在 [GunDialogImpl] 里 —— 它是独立的类，
 *    只有确认支持后才会被第一次调用而加载，老服务端不会因此报 `NoClassDefFoundError`。
 *
 * 不支持时调用方回退到 [GunMenu] 的箱子界面，玩家看到的功能完全一致。
 */
object GunDialog {

    /** 能力探测结果（只算一次）。 */
    private val supportState: Boolean by lazy { probe() }

    /** 当前服务端是否支持原生对话框。 */
    fun supported(): Boolean = supportState

    /**
     * 用对话框打开枪械菜单。
     *
     * @return true = 已用对话框打开；false = 不支持或出错（调用方应回退到 [GunMenu]）
     */
    fun open(player: Player): Boolean {
        if (!supportState) return false
        val opened = try {
            GunDialogImpl.open(player)
        } catch (e: Throwable) {
            // NoClassDefFoundError / NoSuchMethodError 都走这里：版本探测漏判时不能让玩家点不动
            devLog("[Gun] 打开对话框菜单失败，回退到箱子菜单: ${e::class.simpleName}: ${e.message}")
            false
        }
        if (opened) GunDialogSessions.mark(player)
        return opened
    }

    /**
     * 探测：需要同时满足
     * - Paper 的 `Dialog` 类存在；
     * - adventure 的 `DialogLike` 存在；
     * - `Audience#showDialog(DialogLike)` 真的可调用（4.24 之前没有这个默认方法）。
     */
    private fun probe(): Boolean {
        return try {
            Class.forName("io.papermc.paper.dialog.Dialog")
            val dialogLike = Class.forName("net.kyori.adventure.dialog.DialogLike")
            Audience::class.java.getMethod("showDialog", dialogLike)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * 「屏幕上当前是我们自己弹出的原生对话框」的登记表。
 *
 * ### 为什么需要它
 * Paper 的 `Dialog` 是**纯客户端界面**：服务端只发一个 `ClientboundShowDialogPacket`
 * （`ServerPlayer#openDialog` 不动 `containerMenu`，`getOpenInventory()` 仍然是玩家自己的
 * CRAFTING 视图）。而 [GunAction] 的中断判定只认「我们自己的**箱子**菜单」
 * （`CTGunMenuHolder`），不认对话框 —— 于是**对话框开着时跑读条会被判成「打开了其它界面」**。
 *
 * 弹匣对话框（[MagDialog]）正是这个场景：它点完**不关**，`一键退空 / 一键压满`
 * 要连读好几发，全程对话框都在屏幕上，于是第一发之后的每一步都会被误判打断。
 *
 * 所以这里记一笔「我们自己弹过对话框」，让中断判定把它当自家界面豁免：
 * - 玩家真的打开了别的容器 → [GunListener] 的 `InventoryOpenEvent` 立刻作废登记；
 * - 切枪 / 死亡 / 退出 / 换世界 → [GunStateManager.clear] 作废；
 * - 超过 [TTL_MS] 没再弹过（极端情况漏了事件）→ 自动作废，避免表无限增长。
 */
internal object GunDialogSessions {

    /** 登记有效期（毫秒）：只做防泄漏兜底，正常靠事件作废。 */
    private const val TTL_MS = 5 * 60 * 1000L

    private val shownAt = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    /** 记下「刚给这个玩家弹过对话框」。 */
    fun mark(player: Player) {
        shownAt[player.uniqueId] = System.currentTimeMillis()
    }

    /** 当前是否还算「我们自己的对话框开着」。 */
    fun isShown(player: Player): Boolean {
        val at = shownAt[player.uniqueId] ?: return false
        if (System.currentTimeMillis() - at > TTL_MS) {
            shownAt.remove(player.uniqueId)
            return false
        }
        return true
    }

    /** 作废登记（玩家开了别的界面 / 自己关掉了对话框 / 状态被清）。 */
    fun forget(player: Player) {
        shownAt.remove(player.uniqueId)
    }

    /** 全服作废（关服 / 卸载）。 */
    fun clear() {
        shownAt.clear()
    }
}

/**
 * 对话框版菜单的**实际构建**代码。
 *
 * 这个对象里的每一个类型都来自 Paper 1.21.6+ / adventure 4.24+，
 * 单独成类是为了让它只在 [GunDialog.supported] 为 true 时才被类加载器加载。
 */
private object GunDialogImpl {

    /** 按钮面板列数（2 列在客户端里最平衡，按钮不会窄到挤字）。 */
    private const val COLUMNS = 2

    /** 按钮宽度（像素，客户端默认 150）。 */
    private const val BUTTON_WIDTH = 150

    /** 状态正文宽度（像素，默认 200，留窄一点避免换行难看）。 */
    private const val BODY_WIDTH = 220

    fun open(player: Player): Boolean {
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: return false
        val state = GunInstance.readEnsured(held, gun) ?: return false
        val magazine = GunRegistry.mag(state.mag)
        val capacity = magazine?.capacity ?: 0
        val name = GunMenu.displayName(player, gun)

        val title = mmUtil.deserialize("<gold>武器操作</gold> <#6E7A8C>·</#6E7A8C> <#F2F5FA>$name</#F2F5FA>")
        val bodies = buildBodies(player, held, gun, state, capacity)
        val buttons = buildButtons(player, gun, state, capacity)
        val exit = ActionButton.builder(mmUtil.deserialize("<#E08A98>关闭</#E08A98>"))
            .tooltip(mmUtil.deserialize("<#A9B4C3>关掉这个界面，回到游戏"))
            .width(BUTTON_WIDTH)
            .action(
                DialogAction.customClick(
                    { _, _ -> closeQuietly(player) },
                    ClickCallback.Options.builder().build(),
                ),
            )
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty().apply {
                base(
                    DialogBase.builder(title)
                        .externalTitle(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        // 点完按钮就关：换弹 / 拉栓这些动作要的就是「点完立刻回游戏读条」
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(bodies)
                        .build(),
                )
                type(DialogType.multiAction(buttons, exit, COLUMNS))
            }
        }
        player.showDialog(dialog)
        return true
    }

    // ==================== 正文 ====================

    private fun buildBodies(
        player: Player,
        held: ItemStack,
        gun: GunData,
        state: GunState,
        capacity: Int,
    ): List<DialogBody> {
        val bodies = mutableListOf<DialogBody>()

        // 物品本体：客户端原生渲染，带 tooltip 与装饰（耐久条等）
        try {
            val preview = held.clone()
            preview.amount = 1
            bodies += DialogBody.item(preview, null, true, true, 16, 16)
        } catch (_: Throwable) {
            // 老客户端 / 组件异常都不能让菜单打不开，正文文字还在
        }

        bodies += DialogBody.plainMessage(mmUtil.deserialize(statusText(player, held, gun, state, capacity)), BODY_WIDTH)

        val snapshot = GunAction.snapshot(player)
        val footer = if (snapshot != null) {
            "<#8FD4A6>进行中：</#8FD4A6><#EFDF8F>${snapshot.type.label} ${snapshot.percent}%</#EFDF8F>"
        } else {
            "<#6E7A8C>点下面任意按钮执行对应操作</#6E7A8C>"
        }
        bodies += DialogBody.plainMessage(mmUtil.deserialize(footer), BODY_WIDTH)
        return bodies
    }

    /** 与箱子菜单预览 Lore 同源的一段状态文本（换行由客户端处理）。 */
    private fun statusText(
        player: Player,
        held: ItemStack,
        gun: GunData,
        state: GunState,
        capacity: Int,
    ): String {
        val lines = mutableListOf<String>()
        lines += "<#A9B4C3>口径 <#F2F5FA>${gun.caliber}"
        lines += if (state.mag.isEmpty()) {
            "<#A9B4C3>弹匣 <#6E7A8C>无弹匣"
        } else {
            "<#A9B4C3>弹匣 <#F2F5FA>${state.magAmmo.size}<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity" +
                "</#F2F5FA> <#6E7A8C>(${state.mag})"
        }
        lines += "<#A9B4C3>膛内 <#F2F5FA>${if (state.chamber > 0) 1 else 0}<#6E7A8C>/</#6E7A8C><#F2F5FA>1"
        lines += "<#A9B4C3>热量 <#F2F5FA>${GunSettings.heatTierText(GunSettings.heatTier(state.heat))}" +
            " <#6E7A8C>(${GunMenu.heatPercent(gun, state.heat)}%)"
        lines += "<#A9B4C3>耐久 <#F2F5FA>${GunMenu.num(state.durability)}" +
            "<#6E7A8C>/</#6E7A8C><#F2F5FA>${GunMenu.num(AddonManager.effectiveAttributes(gun, held).durability)}" +
            (if (state.durability <= 0.0) " <#E08A98>（损坏）" else "")
        lines += "<#A9B4C3>故障 <#F2F5FA>${GunMenu.jamText(state.jam)}"
        lines += "<#A9B4C3>射击模式 <#F2F5FA>${GunAction.fireModeName(gun, state.fireMode)}" +
            " <#6E7A8C>(${gun.modes.joinToString(" / ").ifEmpty { "SEMI" }})"
        lines += "<#A9B4C3>已装配件 <#F2F5FA>${GunMenu.addonsText(player, held)}"
        // 用真正的换行符而不是 <newline> 标签：MiniMessage 会把字面换行原样保留，
        // 少一层标签解析，也避免标签在 strict 模式下被判非法。
        return lines.joinToString("\n")
    }

    // ==================== 按钮 ====================

    private fun buildButtons(
        player: Player,
        gun: GunData,
        state: GunState,
        capacity: Int,
    ): List<ActionButton> {
        val reloadCandidate = GunAction.findReloadCandidate(player, gun)
        val isBroken = state.durability <= 0.0
        val canReload = reloadCandidate != null && !isBroken && state.jam < 2
        val canRemoveMag = state.mag.isNotEmpty()
        val canBolt = state.jam == 1
        val needsFix = GunAction.needsFix(state)
        val canFireMode = gun.modes.size > 1
        val canInspectMag = state.mag.isNotEmpty()

        val buttons = mutableListOf<ActionButton>()

        buttons += button(
            label = "换弹匣",
            enabled = canReload,
            enabledTip = "从背包里装上一个匹配口径的弹匣<br>当前备选：${reloadCandidate?.mag?.id ?: "无"}",
            disabledTip = if (isBroken) "枪械已损坏，无法换弹" else "背包里没有匹配 ${gun.caliber} 的弹匣",
            player = player,
        ) { target ->
            GunAction.reloadStandard(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = "卸下弹匣",
            enabled = canRemoveMag,
            enabledTip = "把弹匣（含剩余子弹）收回背包",
            disabledTip = "枪上没有弹匣",
            player = player,
        ) { target ->
            GunAction.removeMag(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = if (canBolt) "拉栓（卡壳！）" else "拉栓 / 退膛",
            enabled = canBolt,
            enabledTip = "清除卡壳并把一发子弹推进膛",
            disabledTip = "膛线正常，无需拉栓",
            player = player,
        ) { target ->
            GunAction.bolt(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = "检视弹仓",
            enabled = true,
            enabledTip = if (state.chamber > 0) "膛内有弹" else "空膛",
            disabledTip = "",
            player = player,
        ) { target ->
            GunAction.inspectChamber(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = if (needsFix) "排障（修复）" else "排障",
            enabled = needsFix,
            enabledTip = "把严重故障降级为卡壳并修到至少 50% 耐久<br>不消耗材料，结束后再拉栓一次",
            disabledTip = "当前不需要排障",
            player = player,
        ) { target ->
            GunAction.fix(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = "切换开火模式",
            enabled = canFireMode,
            enabledTip = "当前：${GunAction.fireModeName(gun, state.fireMode)}",
            disabledTip = "该枪械只有一种开火模式",
            player = player,
        ) { target ->
            GunAction.cycleFireMode(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = "检视弹匣",
            enabled = canInspectMag,
            enabledTip = if (canInspectMag) "当前：${state.magAmmo.size} / $capacity" else "",
            disabledTip = "枪上没有弹匣",
            player = player,
        ) { target ->
            GunAction.inspectMag(target, target.inventory.itemInMainHand)
        }

        buttons += button(
            label = "武器改装",
            enabled = true,
            enabledTip = "配件槽：${gun.addonSlots.joinToString(" / ").ifEmpty { "无" }}",
            disabledTip = "",
            player = player,
        ) { target ->
            ModMenu.open(target)
        }

        return buttons
    }

    /**
     * 造一个按钮。
     *
     * 对话框没有「禁用」状态，所以不可用时按钮照样能点，只是 tooltip 写清原因、
     * 点下去也用 [GunPrompt] 提示同一个原因 —— 比灰掉不给解释友好。
     */
    private fun button(
        label: String,
        enabled: Boolean,
        enabledTip: String,
        disabledTip: String,
        player: Player,
        action: (Player) -> Unit,
    ): ActionButton {
        val tip = if (enabled) enabledTip else "<#E08A98>$disabledTip"
        val tipComponent = mmUtil.deserialize(if (tip.isEmpty()) "<#6E7A8C>点击执行" else tip)
        val labelComponent = if (enabled) {
            mmUtil.deserialize("<#F2F5FA>$label</#F2F5FA>")
        } else {
            mmUtil.deserialize("<#6E7A8C>$label</#6E7A8C>")
        }
        return ActionButton.builder(labelComponent)
            .tooltip(tipComponent)
            .width(BUTTON_WIDTH)
            .action(
                DialogAction.customClick(
                    { _, audience ->
                        runAction(audience as? Player ?: player, enabled, disabledTip, action)
                    },
                    ClickCallback.Options.builder().build(),
                ),
            )
            .build()
    }

    /** 点击回调统一入口：不可用只提示；可用则执行（异常不外泄到客户端回调线程）。 */
    private fun runAction(
        target: Player,
        enabled: Boolean,
        disabledReason: String,
        action: (Player) -> Unit,
    ) {
        if (!target.isOnline) return
        if (!enabled) {
            if (disabledReason.isNotEmpty()) GunPrompt.show(target, "<#E08A98>✗ $disabledReason")
            return
        }
        // 回调仍要走主线程：对话框回调可能来自网络线程（不同实现不一样，统一转一次最稳）
        submit(delay = 1, async = false) {
            if (!target.isOnline) return@submit
            val held = target.inventory.itemInMainHand
            if (GunRegistry.gunOf(held) == null) {
                GunPrompt.show(target, "<#E08A98>主手已经不是枪械了")
                return@submit
            }
            try {
                action(target)
            } catch (e: Throwable) {
                devLog("[Gun] 对话框按钮执行失败: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun closeQuietly(player: Player) {
        GunDialogSessions.forget(player)
        try {
            player.closeDialog()
        } catch (_: Throwable) {
        }
    }
}
