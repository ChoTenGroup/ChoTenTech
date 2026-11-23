package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.mmPlainUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import java.time.Duration

/**
 * 弹匣菜单的**对话框（Dialog）版**入口 —— 与枪械菜单（[GunDialog]）同一套能力探测与回退策略。
 *
 * ### 为什么弹匣菜单也要有对话框版
 * 箱子版菜单在 Paper 1.21.6+ 上会把背包顶掉、还要靠 10 tick 重画来保活；
 * 原生对话框不开背包、不打断读条、按钮带 tooltip，和枪械菜单手感一致。
 *
 * ### 交互模型的差别（重要）
 * 箱子版的「压弹模式 / 退弹模式」是**点击自己背包里的子弹**逐发装填的 ——
 * 对话框是客户端界面，玩家在它开着的时候没法点背包。所以对话框版把这两步做成按钮：
 *
 * | 按钮 | 行为 |
 * |---|---|
 * | 压入一发 | 从背包找第一种匹配口径的子弹压入一发（可连点） |
 * | 一键压满 | [GunAction.bulkInsert]：链式压到满 / 弹尽 |
 * | 退出一发 | 退出一发到背包 |
 * | 一键退空 | [GunAction.bulkRemove]：链式退到空 |
 * | 检视弹匣 | 走 `mag.ammoInspectionTime` 的检视读条 |
 *
 * 对话框**点完不关**（`afterAction = NONE`），并且在每一步读条结束后**重新弹一次**，
 * 把装填数字刷新成最新值 —— 否则连点压弹时玩家看到的永远是打开那一刻的旧数字。
 *
 * 不支持对话框的服务端（< 1.21.6 / adventure 太旧）自动回退到 [MagMenu] 的箱子界面，
 * 那里保留原来的「点背包子弹」交互模型。
 */
object MagDialog {

    /** 当前服务端是否支持原生对话框（与枪械菜单同一份探测结果）。 */
    fun supported(): Boolean = GunDialog.supported()

    /**
     * 用对话框打开弹匣菜单。
     *
     * @return true = 已用对话框打开；false = 不支持或出错（调用方应回退到 [MagMenu]）
     */
    fun open(player: Player): Boolean {
        if (!supported()) return false
        val opened = try {
            MagDialogImpl.open(player)
        } catch (e: Throwable) {
            // NoClassDefFoundError / NoSuchMethodError 都走这里：探测漏判时不能让玩家点不动
            devLog("[Gun] 打开弹匣对话框菜单失败，回退到箱子菜单: ${e::class.simpleName}: ${e.message}")
            false
        }
        // 登记「屏幕上是我们自己的对话框」：否则对话框开着时的压弹 / 退弹读条
        // 会被 GunAction 的中断判定当成「打开了其它界面」打断（一键退空会连环中招）。
        if (opened) GunDialogSessions.mark(player)
        return opened
    }
}

/**
 * 对话框版弹匣菜单的**实际构建**代码。
 *
 * 所有类型都来自 Paper 1.21.6+ / adventure 4.24+，
 * 单独成类是为了让它只在 [MagDialog.supported] 为 true 时才被类加载器加载。
 */
private object MagDialogImpl {

    /** 按钮面板列数。 */
    private const val COLUMNS = 2

    /** 按钮宽度（像素，客户端默认 150）。 */
    private const val BUTTON_WIDTH = 150

    /** 正文宽度（像素）。 */
    private const val BODY_WIDTH = 220

    /** 同一个按钮允许点的次数（点完会重开一次对话框，回调随之刷新）。 */
    private const val CLICK_USES = Int.MAX_VALUE

    fun open(player: Player): Boolean {
        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held) ?: return false
        val ammo = MagazineCore.readAmmo(held)
        val capacity = mag.capacity.coerceAtLeast(0)
        val matchingAmmo = GunAction.firstMatchingAmmo(player, mag)
        val matchingCount = if (matchingAmmo == null) 0 else GunAction.countAmmo(player, matchingAmmo)
        val notFull = ammo.size < capacity
        val canInsert = matchingAmmo != null && matchingCount > 0 && notFull
        val hasAmmo = ammo.isNotEmpty()

        val title = mmUtil.deserialize(
            "<gold>弹匣操作</gold> <#6E7A8C>·</#6E7A8C> <#F2F5FA>${displayName(player, mag)}</#F2F5FA>",
        )
        val bodies = buildBodies(player, held, mag, ammo, capacity, matchingAmmo, matchingCount)
        val buttons = buildButtons(
            player = player,
            mag = mag,
            canInsert = canInsert,
            hasAmmo = hasAmmo,
            notFull = notFull,
            matchingAmmo = matchingAmmo,
            matchingCount = matchingCount,
            ammoCount = ammo.size,
            capacity = capacity,
        )
        val exit = ActionButton.builder(mmUtil.deserialize("<#E08A98>关闭</#E08A98>"))
            .tooltip(mmUtil.deserialize("<#A9B4C3>关掉这个界面，回到游戏"))
            .width(BUTTON_WIDTH)
            .action(
                DialogAction.customClick(
                    { _, _ -> closeQuietly(player) },
                    options(),
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
                        // 点完**不关**：压弹是要连点的，关了就得每次重新按 F
                        .afterAction(DialogBase.DialogAfterAction.NONE)
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
        mag: MagData,
        ammo: List<String>,
        capacity: Int,
        matchingAmmo: String?,
        matchingCount: Int,
    ): List<DialogBody> {
        val bodies = mutableListOf<DialogBody>()
        try {
            val preview = held.clone()
            preview.amount = 1
            bodies += DialogBody.item(preview, null, true, true, 16, 16)
        } catch (_: Throwable) {
            // 物品渲染失败不能连累整个菜单
        }
        bodies += DialogBody.plainMessage(
            mmUtil.deserialize(statusText(player, mag, ammo, capacity, matchingAmmo, matchingCount)),
            BODY_WIDTH,
        )
        val snapshot = GunAction.snapshot(player)
        val footer = if (snapshot != null) {
            "<#8FD4A6>进行中：</#8FD4A6><#EFDF8F>${snapshot.type.label} ${snapshot.percent}%</#EFDF8F>"
        } else {
            "<#6E7A8C>点按钮装填 / 退弹，每次操作结束后数字会自动刷新</#6E7A8C>"
        }
        bodies += DialogBody.plainMessage(mmUtil.deserialize(footer), BODY_WIDTH)
        return bodies
    }

    private fun statusText(
        player: Player,
        mag: MagData,
        ammo: List<String>,
        capacity: Int,
        matchingAmmo: String?,
        matchingCount: Int,
    ): String {
        val ratio = MagazineCore.percent(ammo, capacity)
        val lines = mutableListOf<String>()
        lines += "<#A9B4C3>口径 <#F2F5FA>${mag.caliber}"
        lines += "<#A9B4C3>装填 <#F2F5FA>${ammo.size}<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity" +
            "</#F2F5FA> <#6E7A8C>(${(ratio * 100.0).toInt()}%)"
        lines += "<#A9B4C3>档位 ${MagazineCore.fillText(ratio)}"
        if (ammo.isEmpty()) {
            lines += "<#A9B4C3>弹种 <#6E7A8C>无"
        } else {
            val kinds = ammo.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }
                .take(5)
            for ((index, entry) in kinds.withIndex()) {
                val prefix = if (index == 0) "<#A9B4C3>弹种 " else "<#A9B4C3>     "
                lines += "$prefix<#F2F5FA>${entry.key}</#F2F5FA> <#6E7A8C>×</#6E7A8C><#F2F5FA>${entry.value}</#F2F5FA>"
            }
            if (kinds.size >= 5) lines += "<#6E7A8C>    ……（最多显示 5 条）"
        }
        lines += "<#A9B4C3>匹配子弹 <#EFDF8F>${matchingAmmo ?: "无"} <#A9B4C3>×<#EFDF8F>$matchingCount"
        lines += "<#A9B4C3>快速换弹 " + if (mag.allowFastReload) {
            "<#8FD4A6>允许"
        } else {
            "<#E08A98>不允许"
        }
        lines += "<#A9B4C3>检视耗时 <#EFDF8F>${GunMenu.num(mag.ammoInspectionTime.takeIf { it > 0.0 } ?: 2.0)}s"
        // 用真正的换行符：MiniMessage 会原样保留字面换行，少一层标签解析
        return lines.joinToString("\n")
    }

    // ==================== 按钮 ====================

    private fun buildButtons(
        player: Player,
        mag: MagData,
        canInsert: Boolean,
        hasAmmo: Boolean,
        notFull: Boolean,
        matchingAmmo: String?,
        matchingCount: Int,
        ammoCount: Int,
        capacity: Int,
    ): List<ActionButton> {
        val buttons = mutableListOf<ActionButton>()

        val insertDisabledReason = when {
            !notFull -> "弹匣已满"
            else -> "背包里没有匹配 ${mag.caliber} 的子弹"
        }

        buttons += button(
            label = "压入一发",
            enabled = canInsert,
            enabledTip = "压入一发 <#EFDF8F>${matchingAmmo ?: ""}</#EFDF8F><br>背包剩余 ×$matchingCount",
            disabledTip = insertDisabledReason,
            player = player,
        ) { target ->
            val held = target.inventory.itemInMainHand
            val ammoId = matchingAmmo
            if (ammoId == null) {
                GunPrompt.show(target, "<#E08A98>背包里没有匹配 ${mag.caliber} 的子弹")
                reshow(target)
                return@button
            }
            val started = GunAction.insertRound(target, held, ammoId) { reshow(target) }
            if (!started) reshow(target)
        }

        buttons += button(
            label = "一键压满",
            enabled = canInsert,
            enabledTip = "连续压入，直到弹匣满或子弹用尽<br>每一步都可被打断",
            disabledTip = insertDisabledReason,
            player = player,
        ) { target ->
            val started = GunAction.bulkInsert(target, target.inventory.itemInMainHand) { reshow(target) }
            if (!started) reshow(target)
        }

        buttons += button(
            label = "退出一发",
            enabled = hasAmmo,
            enabledTip = "把弹匣里最后一发退回背包（背包满则掉在脚下）",
            disabledTip = "弹匣里没有子弹",
            player = player,
        ) { target ->
            val started = GunAction.removeRound(target, target.inventory.itemInMainHand) { reshow(target) }
            if (!started) reshow(target)
        }

        buttons += button(
            label = "一键退空",
            enabled = hasAmmo,
            enabledTip = "连续退出，直到弹匣空<br>每一步都可被打断",
            disabledTip = "弹匣里没有子弹",
            player = player,
        ) { target ->
            val started = GunAction.bulkRemove(target, target.inventory.itemInMainHand) { reshow(target) }
            if (!started) reshow(target)
        }

        buttons += button(
            label = "检视弹匣",
            enabled = true,
            enabledTip = "当前 <#F2F5FA>$ammoCount<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity</#F2F5FA><br>" +
                "检视耗时 ${GunMenu.num(mag.ammoInspectionTime.takeIf { it > 0.0 } ?: 2.0)}s，读数可能有一档误差",
            disabledTip = "",
            player = player,
        ) { target ->
            GunAction.inspectMagItem(target, target.inventory.itemInMainHand)
            reshow(target)
        }

        return buttons
    }

    /**
     * 造一个按钮。
     *
     * 对话框没有「禁用」状态，所以不可用时照样能点：tooltip 写清原因，
     * 点下去也用 [GunPrompt] 提示同一句 —— 比灰掉不给解释友好。
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
                    options(),
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
            if (GunRegistry.magOf(target.inventory.itemInMainHand) == null) {
                GunPrompt.show(target, "<#E08A98>主手已经不是弹匣了")
                return@submit
            }
            try {
                action(target)
            } catch (e: Throwable) {
                devLog("[Gun] 弹匣对话框按钮执行失败: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    /** 重开一次对话框，把装填数字刷成最新（主手已经不是这个弹匣就不再弹回来）。 */
    private fun reshow(player: Player) {
        if (!player.isOnline) return
        try {
            if (GunRegistry.magOf(player.inventory.itemInMainHand) == null) {
                // 主手已经不是弹匣：把还开着的界面关掉，别留一个点不动的死界面。
                closeQuietly(player)
                GunPrompt.show(player, "<#DFBD7C>主手已不是弹匣，装填界面已关闭")
                return
            }
            open(player)
        } catch (_: Throwable) {
        }
    }

    private fun options(): ClickCallback.Options = ClickCallback.Options.builder()
        // 每次点击后都会 reshow 重建回调，这里的上限只是防御：
        // 取一个不可能达到的值，避免玩家对着失活的旧按钮点 8 次之后「完全没反应」。
        .uses(Int.MAX_VALUE)
        .lifetime(Duration.ofMinutes(10))
        .build()

    private fun displayName(player: Player, mag: MagData): String {
        return try {
            val component = player.inventory.itemInMainHand.itemMeta?.displayName()
            val plain = component?.let { mmPlainUtil.serialize(it).trim() }.orEmpty()
            plain.ifEmpty { mag.id }
        } catch (_: Exception) {
            mag.id
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
