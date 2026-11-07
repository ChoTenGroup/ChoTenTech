package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmPlainUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 枪械菜单 / 弹匣菜单的公共标记。
 *
 * [GunAction] 用它判断「玩家开的 GUI 是不是我们自己的」：我们自己的菜单开着时
 * 读条**不能**被打断（弹匣菜单的压弹、退弹就在菜单开着的时候读条），
 * 而任何第三方容器（箱子 / 工作台 / 铁砧……）都必须打断读条。
 *
 * 用接口而不是「按标题匹配」：标题是玩家可见文本，会被其它插件、被颜色代码改，
 * 对象身份骗不了人。
 */
interface CTGunMenuHolder : InventoryHolder

/**
 * 枪械操作菜单（27 格 = 3 行，固定布局）。
 *
 * | 槽 | 内容 |
 * |---|---|
 * | 0 | 预览（枪械外观 + 口径/弹匣/膛内/热量/耐久/故障/模式/配件） |
 * | 2 | 换弹匣 |
 * | 3 | 卸下弹匣 |
 * | 4 | 拉栓 / 退膛 |
 * | 5 | 检视弹仓 |
 * | 6 | 排障 |
 * | 8 | 切换开火模式 |
 * | 13 | 武器改装（P2 只发提示） |
 * | 15 | 检视弹匣 |
 * | 18 | 状态行 |
 * | 20~22 | 热量条 |
 * | 26 | 关闭 |
 *
 * **本菜单不提供压弹 / 退弹**（那是弹匣菜单的事）。
 *
 * **本菜单也没有「出枪」按钮**：出枪是切到枪械时自动触发的
 * （见 [GunListener] 的 `PlayerItemHeldEvent` 分支与 [GunAim.beginDraw]），
 * 手动重播读条只要切走再切回来即可，按钮属于纯冗余入口。
 */
object GunMenu {

    /** 菜单总格数（3 行）。 */
    const val SIZE = 27

    const val SLOT_PREVIEW = 0
    const val SLOT_RELOAD = 2
    const val SLOT_REMOVE_MAG = 3
    const val SLOT_BOLT = 4
    const val SLOT_INSPECT_CHAMBER = 5
    const val SLOT_FIX = 6
    const val SLOT_FIRE_MODE = 8
    const val SLOT_MOD = 13
    const val SLOT_INSPECT_MAG = 15
    const val SLOT_STATUS = 18
    const val SLOT_HEAT_FIRST = 20
    const val SLOT_HEAT_LAST = 22
    const val SLOT_CLOSE = 26

    /** 菜单 Holder（`inventory` 不能叫 `inventory`，会与 [InventoryHolder.getInventory] 的 JVM 签名撞车）。 */
    class Holder : CTGunMenuHolder {
        private var backing: Inventory? = null

        fun attach(inventory: Inventory) {
            backing = inventory
        }

        override fun getInventory(): Inventory = backing
            ?: throw IllegalStateException("GunMenuHolder 尚未绑定背包，说明有人在 attach 之前读取了它")
    }

    // ==================== 打开 / 渲染 ====================

    /**
     * 打开枪械菜单。
     *
     * 优先用**原生对话框**（Paper 1.21.6+）—— 不开背包、不打断读条、按钮带 tooltip；
     * 老服务端自动回退到下面的箱子界面，玩家看到的功能完全一致。
     *
     * 主线程调用；手持不是枪械时只发提示。
     */
    fun open(player: Player) {
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held)
        if (gun == null) {
            menuActionBar(player, "<#E08A98>主手不是枪械，无法打开武器操作")
            return
        }
        if (GunDialog.open(player)) return
        openChest(player, gun)
    }

    /** 箱子版菜单（老服务端的回退路径，也是对话框不可用时的唯一实现）。 */
    private fun openChest(player: Player, gun: GunData) {
        val holder = Holder()
        val inventory = Bukkit.createInventory(
            holder,
            SIZE,
            mmUtil.deserialize("<gold>武器操作 - ${displayName(player, gun)}</gold>"),
        )
        holder.attach(inventory)
        render(player, inventory)
        player.openInventory(inventory)
        GunMenuListener.onMenuOpened(player, inventory)
    }

    /**
     * 渲染（也用于每 10 tick 的刷新）。
     *
     * 刷新时**不重开**菜单，只是把格子里的按钮按当前状态重画一遍，
     * 所以玩家读条时看到的进度和可用状态都是实时的。
     */
    fun render(player: Player, inventory: Inventory) {
        fillBackground(inventory)

        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held)
        if (gun == null) {
            // 手持物被换掉了：菜单已经没有意义，直接关掉（close 会清掉刷新任务）
            try {
                player.closeInventory()
            } catch (_: Exception) {
            }
            return
        }
        val state = GunInstance.readEnsured(held, gun) ?: return
        val magazine = GunRegistry.mag(state.mag)
        val capacity = magazine?.capacity ?: 0

        // ---------- 可用条件 ----------
        val reloadCandidate = GunAction.findReloadCandidate(player, gun)
        val isBroken = state.durability <= 0.0
        // P3：损坏状态禁止换弹（GunAction.reloadReject 同源），也禁止卸匣之外的操作
        val canReload = reloadCandidate != null && !isBroken && state.jam < 2
        val canRemoveMag = state.mag.isNotEmpty()
        val isJamStuck = state.jam == 1
        // 排障条件放宽为「严重故障（jam=2）**或** 耐久归零」，且**不消耗任何材料**
        val needsFix = GunAction.needsFix(state)
        val canFix = needsFix
        val canFireMode = gun.modes.size > 1
        val canInspectMag = state.mag.isNotEmpty()

        // ---------- 0 预览 ----------
        inventory.setItem(SLOT_PREVIEW, previewItem(player, held, gun, state, magazine, capacity))

        // ---------- 2 换弹匣 ----------
        inventory.setItem(
            SLOT_RELOAD,
            button(
                Material.IRON_HOE,
                "换弹匣",
                canReload,
                listOf(
                    "<#A9B4C3>从背包里装上一个匹配口径的弹匣",
                    "<#A9B4C3>当前备选：<#EFDF8F>${reloadCandidate?.mag?.id ?: "无"}",
                ),
                if (isBroken) "枪械已损坏，无法换弹"
                else "背包里没有匹配 ${gun.caliber} 的弹匣",
            ),
        )

        // ---------- 3 卸下弹匣 ----------
        inventory.setItem(
            SLOT_REMOVE_MAG,
            button(
                Material.TRIPWIRE_HOOK,
                "卸下弹匣",
                canRemoveMag,
                listOf(
                    "<#A9B4C3>把弹匣（含剩余子弹）收回背包",
                    "<#A9B4C3>当前弹匣：<#EFDF8F>${state.mag.ifEmpty { "无" }}",
                ),
                "枪上没有弹匣",
            ),
        )

        // ---------- 4 拉栓 / 退膛 ----------
        inventory.setItem(
            SLOT_BOLT,
            button(
                Material.LEVER,
                if (isJamStuck) "拉栓（卡壳！）" else "拉栓 / 退膛",
                isJamStuck,
                listOf(
                    "<#A9B4C3>清除卡壳并把一发子弹推进膛",
                    "<#A9B4C3>当前状态：<#EFDF8F>${jamText(state.jam)}",
                ),
                "膛线正常，无需拉栓",
            ),
        )

        // ---------- 5 检视弹仓 ----------
        inventory.setItem(
            SLOT_INSPECT_CHAMBER,
            button(
                Material.GLASS_PANE,
                "检视弹仓",
                true,
                listOf(
                    "<#A9B4C3>查看膛内是否有弹与故障状态",
                    "<#A9B4C3>当前膛内：<#EFDF8F>${if (state.chamber > 0) "有弹" else "空膛"}",
                ),
                "",
            ),
        )

        // ---------- 6 排障 ----------
        inventory.setItem(
            SLOT_FIX,
            button(
                Material.ANVIL,
                if (needsFix) "排障（修复）" else "排障",
                canFix,
                listOf(
                    "<#A9B4C3>把「严重故障」降级为卡壳并修复到至少 50% 耐久",
                    "<#A9B4C3>不消耗材料，读条结束后<#EFDF8F>再拉栓一次<#A9B4C3>即可恢复",
                ),
                "当前不需要排障",
            ),
        )

        // ---------- 8 切换开火模式 ----------
        inventory.setItem(
            SLOT_FIRE_MODE,
            button(
                Material.REPEATER,
                "切换开火模式",
                canFireMode,
                listOf(
                    "<#A9B4C3>可用模式：<#EFDF8F>${gun.modes.joinToString(" / ").ifEmpty { "SEMI" }}",
                    "<#A9B4C3>当前模式：<#EFDF8F>${GunAction.fireModeName(gun, state.fireMode)}",
                ),
                "该枪械只有一种开火模式",
            ),
        )

        // ---------- 13 武器改装 ----------
        inventory.setItem(
            SLOT_MOD,
            button(
                Material.SMITHING_TABLE,
                "武器改装",
                true,
                listOf("<#A9B4C3>配件槽：<#EFDF8F>${gun.addonSlots.joinToString(" / ").ifEmpty { "无" }}"),
                "",
            ),
        )

        // ---------- 15 检视弹匣 ----------
        inventory.setItem(
            SLOT_INSPECT_MAG,
            button(
                Material.SPYGLASS,
                "检视弹匣",
                canInspectMag,
                listOf(
                    "<#A9B4C3>查看弹匣装填情况",
                    if (state.mag.isEmpty()) "<#A9B4C3>当前：<#6E7A8C>无弹匣"
                    else "<#A9B4C3>当前：<#EFDF8F>${state.magAmmo.size} / $capacity",
                ),
                "枪上没有弹匣",
            ),
        )

        // ---------- 18 状态行 ----------
        inventory.setItem(SLOT_STATUS, statusItem(player, gun, state))

        // ---------- 20~22 热量条 ----------
        drawHeatBar(inventory, gun, state.heat)

        // ---------- 26 关闭 ----------
        inventory.setItem(
            SLOT_CLOSE,
            menuItem(
                Material.BARRIER,
                "<#E08A98>关闭",
                listOf("<#A9B4C3>关闭这个界面"),
            ),
        )
    }

    // ==================== 点击派发 ====================

    /** 处理菜单内的点击（背包区域由监听器统一取消，不会走到这里）。 */
    fun handleClick(player: Player, slot: Int) {
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held)
        if (gun == null) {
            player.closeInventory()
            return
        }
        when (slot) {
            SLOT_CLOSE -> player.closeInventory()

            SLOT_RELOAD -> closeThen(player) {
                GunAction.reloadStandard(player, player.inventory.itemInMainHand)
            }

            SLOT_REMOVE_MAG -> closeThen(player) {
                GunAction.removeMag(player, player.inventory.itemInMainHand)
            }

            SLOT_BOLT -> {
                val state = GunInstance.readEnsured(held, gun)
                if (state == null || state.jam != 1) {
                    menuActionBar(player, "<#DFBD7C>膛线正常，无需拉栓")
                    return
                }
                closeThen(player) {
                    GunAction.bolt(player, player.inventory.itemInMainHand)
                }
            }

            SLOT_INSPECT_CHAMBER -> closeThen(player) {
                GunAction.inspectChamber(player, player.inventory.itemInMainHand)
            }

            SLOT_FIX -> {
                val state = GunInstance.readEnsured(held, gun)
                // 严重故障 **或** 耐久归零都能排障（损坏的枪必须修得回来），且不消耗材料
                if (state == null || !GunAction.needsFix(state)) {
                    menuActionBar(player, "<#DFBD7C>当前不需要排障")
                    return
                }
                closeThen(player) {
                    GunAction.fix(player, player.inventory.itemInMainHand)
                }
            }

            SLOT_FIRE_MODE -> closeThen(player) {
                GunAction.cycleFireMode(player, player.inventory.itemInMainHand)
            }

            SLOT_MOD -> closeThen(player) { ModMenu.open(player) }

            SLOT_INSPECT_MAG -> closeThen(player) {
                GunAction.inspectMag(player, player.inventory.itemInMainHand)
            }

            else -> Unit
        }
    }

    // ==================== 渲染小工具 ====================

    private fun fillBackground(inventory: Inventory) {
        val filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, "<#2A3038> </#2A3038>")
        for (slot in 0 until SIZE) {
            inventory.setItem(slot, filler)
        }
    }

    /** 预览：把玩家手里那把枪**原样**放进第 0 格，再补上状态 Lore。 */
    private fun previewItem(
        player: Player,
        held: ItemStack,
        gun: GunData,
        state: GunState,
        magazine: MagData?,
        capacity: Int,
    ): ItemStack {
        val preview = held.clone()
        val meta = preview.itemMeta ?: return preview
        val existing = meta.lore() ?: emptyList()
        val lines = mutableListOf<String>()
        lines += "<#6E7A8C>─────────────────────"
        lines += "<#A9B4C3>口径 <#EFDF8F>${gun.caliber}"
        lines += if (state.mag.isEmpty()) {
            "<#A9B4C3>弹匣 <#6E7A8C>无弹匣"
        } else {
            "<#A9B4C3>弹匣 <#F2F5FA>${state.magAmmo.size}<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity" +
                "</#F2F5FA> <#6E7A8C>(${state.mag})"
        }
        lines += "<#A9B4C3>膛内 <#F2F5FA>${if (state.chamber > 0) 1 else 0}<#6E7A8C>/</#6E7A8C><#F2F5FA>1</#F2F5FA>"
        lines += "<#A9B4C3>热量 <#F2F5FA>${GunSettings.heatTierText(GunSettings.heatTier(state.heat))}" +
            " <#6E7A8C>(${heatPercent(gun, state.heat)}%)"
        lines += "<#A9B4C3>耐久 <#F2F5FA>${num(state.durability)}" +
            "<#6E7A8C>/</#6E7A8C><#F2F5FA>${num(AddonManager.effectiveAttributes(gun, held).durability)}</#F2F5FA>" +
            if (state.durability <= 0.0) " <#E08A98>（损坏）" else ""
        lines += "<#A9B4C3>故障 <#F2F5FA>${jamText(state.jam)}"
        lines += "<#A9B4C3>射击模式 <#F2F5FA>${GunAction.fireModeName(gun, state.fireMode)}"
        lines += "<#A9B4C3>已装配件 <#F2F5FA>${addonsText(player, held)}"
        lines += "<#A9B4C3>检视精度 <#F2F5FA>${magazine?.ammoInspectionAccuracy ?: 0}"
        lines += "<#6E7A8C>─────────────────────"
        meta.lore(existing + lines.map { mmUtil.deserialize(it) })
        preview.itemMeta = meta
        // 预览只是展示：用不到数量，压到 1 避免显示堆叠数字
        preview.amount = 1
        return preview
    }

    private fun statusItem(player: Player, gun: GunData, state: GunState): ItemStack {
        val lore = mutableListOf<String>()
        val snapshot = GunAction.snapshot(player)
        if (snapshot != null) {
            lore += "<#8FD4A6>进行中：${snapshot.type.label} ${snapshot.percent}%"
        } else {
            lore += "<#8FD4A6>就绪"
        }
        if (state.jam != 0) {
            lore += "<#E08A98>故障：${jamText(state.jam)}"
        }
        if (state.durability <= 0.0) {
            lore += "<#E08A98>枪械已损坏：只能当钝器近战，需要排障修复"
        }
        if (state.mag.isEmpty()) {
            lore += "<#DFBD7C>未装弹匣"
        } else if (state.magAmmo.isEmpty() && state.chamber <= 0) {
            lore += "<#DFBD7C>弹匣与膛内都没有子弹"
        }
        lore += "<#6E7A8C>口径 ${gun.caliber} <#6E7A8C>| <#6E7A8C>模式 ${GunAction.fireModeName(gun, state.fireMode)}"
        return menuItem(Material.PAPER, "<gold>状态</gold>", lore)
    }

    /**
     * 热量条：20~22 三格，`RED_STAINED_GLASS_PANE` 的**堆叠数量**就是这一格
     * 承载的热量百分点（每格 33~34 点），空格用浅灰玻璃板占位。
     */
    private fun drawHeatBar(inventory: Inventory, gun: GunData, heat: Double) {
        val percent = heatPercent(gun, heat)
        for (index in 0..(SLOT_HEAT_LAST - SLOT_HEAT_FIRST)) {
            val bandStart = index * 100 / 3
            val bandEnd = (index + 1) * 100 / 3
            val covered = (percent - bandStart).coerceIn(0, bandEnd - bandStart)
            val slot = SLOT_HEAT_FIRST + index
            inventory.setItem(
                slot,
                if (covered > 0) {
                    menuItem(
                        Material.RED_STAINED_GLASS_PANE,
                        "<#E08A98>热量 $percent%",
                        listOf(
                            "<#A9B4C3>档位：<#F2F5FA>${GunSettings.heatTierText(GunSettings.heatTier(heat))}",
                            "<#A9B4C3>本格承载：<#F2F5FA>$covered%",
                        ),
                        amount = covered,
                    )
                } else {
                    menuItem(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "<#6E7A8C>热量 $percent%",
                        listOf(
                            "<#A9B4C3>档位：<#F2F5FA>${GunSettings.heatTierText(GunSettings.heatTier(heat))}",
                            "<#6E7A8C>本格未达",
                        ),
                    )
                },
            )
        }
    }

    /** 热量百分比（0..100）。**对话框菜单复用**，因此是 internal。 */
    internal fun heatPercent(gun: GunData, heat: Double): Int {
        val capacity = if (gun.heatCapacity > 0.0) gun.heatCapacity else GunSettings.heat.capacity
        if (capacity <= 0.0) return 0
        return ((heat / capacity) * 100.0).roundToInt().coerceIn(0, 100)
    }

    internal fun jamText(jam: Int): String = when (jam) {
        1 -> "<#E08A98>卡壳"
        2 -> "<#E08A98>需排障"
        else -> "<#8FD4A6>正常"
    }

    /** 已装配件：直接读 `sertraline_data.gun.addons`（P0 写入的复合标签）。 */
    internal fun addonsText(player: Player, held: ItemStack): String {
        val addons = AddonManager.readAddons(held)
        if (addons.isEmpty()) return "无"
        return addons.entries.joinToString("、") { (slot, id) ->
            val name = try {
                io.github.zzzyyylllty.sertraline.Sertraline.api().buildItem(id, player)
                    ?.itemMeta?.displayName()?.let(mmPlainUtil::serialize)?.trim()?.ifEmpty { id } ?: id
            } catch (_: Exception) { id }
            "$slot=$name"
        }
    }

    internal fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /**
     * 标题里的枪名：取物品显示名的纯文本，取不到就用 Sertraline ID。
     *
     * 同样要剥掉 [GunNameplate] 写进去的耐久后缀，否则标题会变成
     * `武器操作 - OFN-12手枪 [873/1000]`。
     */
    internal fun displayName(player: Player, gun: GunData): String {
        return try {
            val meta = player.inventory.itemInMainHand.itemMeta
            val component = meta?.displayName()
            if (component != null) {
                val plain = GunNameplate.stripDurabilitySuffix(mmPlainUtil.serialize(component).trim())
                if (plain.isNotEmpty()) return plain
            }
            gun.id
        } catch (_: Exception) {
            gun.id
        }
    }
}

// ==================== 两个菜单共用的小工具 ====================

/** 造一个带 MiniMessage 名称 / Lore 的按钮物品。 */
internal fun menuItem(
    material: Material,
    name: String,
    lore: List<String> = emptyList(),
    amount: Int = 1,
): ItemStack {
    val stack = ItemStack(material, amount.coerceIn(1, 64))
    val meta = stack.itemMeta ?: return stack
    meta.displayName(mmUtil.deserialize(name))
    if (lore.isNotEmpty()) {
        meta.lore(lore.map { mmUtil.deserialize(it) })
    }
    stack.itemMeta = meta
    return stack
}

/**
 * 造一个「可用 / 灰显」按钮：
 * 可用时名称正常 + 「点击执行」；不可用时名称变灰 + 写清不可用原因。
 */
internal fun button(
    material: Material,
    name: String,
    enabled: Boolean,
    enabledLore: List<String>,
    disabledReason: String,
): ItemStack = if (enabled) {
    menuItem(
        material,
        "<#F2F5FA>$name</#F2F5FA>",
        enabledLore + listOf("<#6E7A8C>点击执行"),
    )
} else {
    menuItem(
        material,
        "<#6E7A8C>$name</#6E7A8C>",
        listOf("<#E08A98>✗ $disabledReason"),
    )
}

/** 关掉菜单，**下一 tick** 再执行动作：避免在点击事件里重开/改背包造成的时序问题。 */
internal fun closeThen(player: Player, action: () -> Unit) {
    try {
        player.closeInventory()
    } catch (_: Exception) {
    }
    submit(delay = 1, async = false) {
        if (!player.isOnline) return@submit
        try {
            action()
        } catch (_: Exception) {
        }
    }
}

/** 菜单里的 ActionBar 提示（与 [GunHud] 同一个序列化入口）。 */
internal fun menuActionBar(player: Player, message: String) {
    try {
        GunPrompt.show(player, message)
    } catch (_: Exception) {
    }
}
