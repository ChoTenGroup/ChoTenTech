package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.profile.SubscriptionData
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionDef
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionManager
import io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionPricing
import io.github.zzzyyylllty.chotentech.function.subscription.TokenGrant
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 用黑色玻璃板填充指定槽位区间，作为菜单背景。
 */
private fun Chest.fillBackground(size: Int) {
    for (slot in 0 until size) {
        set(slot, guiItem(XMaterial.BLACK_STAINED_GLASS_PANE, " "))
    }
}

/**
 * 订阅中心：列出所有 subscriptions 目录中定义的订阅，展示状态 / 权益 / 加成池余量 / 售价。
 * 点击订阅进入「选择时长」，确认后消耗 tape 购买。
 */
class SubscriptionMenu {

    companion object {
        /** 订阅条目的展示槽位（6 行菜单的中间三行） */
        val SUBSCRIPTION_SLOTS = intArrayOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
    }

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val shared = SubscriptionManager.shared(uuid)
        val defs = SubscriptionManager.all()
        val now = System.currentTimeMillis()

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<gradient:#DC96FF:#AC06EF><b>订阅中心</gradient>".toComponent())
        ) {
            rows(6)
            fillBackground(54)

            // 顶部：钱包概览
            set(4, buildWalletItem(player))

            if (defs.isEmpty()) {
                set(22, guiItem(
                    XMaterial.BARRIER,
                    "<#E08A98><b>尚未配置任何订阅",
                    listOf(
                        loreSection("说明"),
                        loreInfo("目录", "<#F2F5FA>plugins/ChoTenTech/subscriptions/"),
                        loreDash("<#A9B4C3>放入订阅 yml 后重载插件即可生效"),
                    )
                ))
            } else {
                for ((index, def) in defs.withIndex()) {
                    if (index >= SUBSCRIPTION_SLOTS.size) break
                    val data = shared.subscriptions[def.id]
                    set(SUBSCRIPTION_SLOTS[index], buildSubscriptionItem(def, data, now)) {
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        SubscriptionDurationMenu(def).open(clicker)
                    }
                }
            }

            set(45, guiItem(
                XMaterial.FILLED_MAP,
                "<#84D1CB><b>订阅说明",
                listOf(
                    loreSection("加成池"),
                    loreDash("<#A9B4C3>生效期间获得的免费 Token 按池余量翻倍"),
                    loreDash("<#A9B4C3>6 小时池与每周池独立计算、各自滚动回满"),
                    loreSpace(),
                    loreSection("续费"),
                    loreDash("<#A9B4C3>同订阅续费只延长到期时间"),
                    loreDash("<#A9B4C3>不同订阅可同时生效，奖励累加"),
                )
            ))

            set(49, guiItem(
                XMaterial.BARRIER,
                "<#E08A98><b>关闭",
                listOf(loreDash("<#A9B4C3>点击关闭订阅中心"))
            )) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
            }
        }
    }

    // ------------------------------------------------------------------
    // 条目构建
    // ------------------------------------------------------------------

    private fun buildSubscriptionItem(def: SubscriptionDef, data: SubscriptionData?, now: Long): ItemStack {
        val owned = data != null
        val active = data?.isActive(now) == true
        val stateText = when {
            !owned -> "<#A9B4C3>未拥有"
            active -> "<#8FD4A6>生效中"
            else -> "<#E08A98>已过期"
        }
        val timedName = if (def.timedPaid) "付费" else "免费"

        val lore = mutableListOf<String>()
        lore += loreSection("订阅状态")
        lore += loreInfo("状态", stateText)
        if (data != null) {
            lore += loreInfo("到期时间", "<#EFDF8F>${SubscriptionManager.formatTime(data.expireTime)}")
            lore += loreInfo(if (active) "剩余时长" else "过期于", "${if (active) "<#8FD4A6>" else "<#A9B4C3>"}${SubscriptionManager.remainText(data, now)}")
        }

        lore += loreSpace()
        lore += loreSection("订阅权益")
        if (def.instant.isNotEmpty()) {
            lore += loreDash("<#8FD4A6>立即获得 <#F2F5FA>${grantsText(def.instant)} <#A9B4C3>(仅首次购买)")
        }
        if (def.dailyLogin.isNotEmpty()) {
            lore += loreDash("<#8FD4A6>每日登录 <#F2F5FA>${grantsText(def.dailyLogin)}")
        }
        if (def.sixHourBonus > 0L) {
            lore += loreDash("<#8FD4A6>6 小时加成池 <#F2F5FA>${def.sixHourBonus}<#A9B4C3> $timedName Token")
        }
        if (def.weekBonus > 0L) {
            lore += loreDash("<#8FD4A6>每周加成池 <#F2F5FA>${def.weekBonus}<#A9B4C3> $timedName Token")
        }
        if (!def.timedPaid) {
            lore += loreDash("<#A9B4C3>加成池只翻倍免费 Token 收入")
        }

        if (data != null) {
            lore += loreSpace()
            lore += loreSection("加成池余量")
            if (def.sixHourBonus > 0L) {
                val remain = SubscriptionManager.sixHourRemaining(def, data, now)
                val reset = SubscriptionManager.sixHourResetAt(def, data, now)
                lore += loreInfo("6 小时池", "<#8FD4A6>$remain<#6E7A8C>/${def.sixHourBonus} <#A9B4C3>(${SubscriptionManager.formatDuration(reset - now)} 后回满)")
            }
            if (def.weekBonus > 0L) {
                val remain = SubscriptionManager.weekRemaining(def, data, now)
                val reset = SubscriptionManager.weekResetAt(def, data, now)
                lore += loreInfo("每周池", "<#8FD4A6>$remain<#6E7A8C>/${def.weekBonus} <#A9B4C3>(${SubscriptionManager.formatDuration(reset - now)} 后回满)")
            }
            if (!active) {
                lore += loreDash("<#E08A98>已过期：不再产出，重新购买后窗口重新起算")
            }
        }

        lore += loreSpace()
        lore += loreSection("购买时长")
        if (def.pricing.isEmpty()) {
            lore += loreDash("<#E08A98>未配置价格")
        } else {
            for (pricing in def.pricing) {
                lore += loreDash("<#EFDF8F>${pricing.durationText} <#6E7A8C>(${pricing.key}) <#F2F5FA>${pricing.price} <#DFBD7C>磁带")
            }
        }
        lore += loreSpace()
        lore += "<!i> <#EFDF8F>点击 <#8FD4A6>选择订阅时长"

        return guiItem(def.icon, def.iconFallback, "${def.colorTag}<b>${def.displayName}", lore)
    }

    private fun grantsText(grants: List<TokenGrant>): String =
        grants.joinToString(" <#6E7A8C>+ ") { it.shortText }

    private fun buildWalletItem(player: Player): ItemStack {
        val ltoken = CurrencyManager.getBalance(player, CurrencyManager.LTOKEN)
        val ptoken = CurrencyManager.getBalance(player, CurrencyManager.PTOKEN)
        val tape = CurrencyManager.getBalance(player, "tape")
        return guiItem(
            XMaterial.SUNFLOWER,
            "<#DFBD7C><b>我的钱包",
            listOf(
                loreSection("余额"),
                loreInfo("免费 Token", "<#8FD4A6>$ltoken"),
                loreInfo("付费 Token", "<#FFD166>$ptoken"),
                loreInfo("磁带", "<#DFBD7C>$tape"),
                loreSpace(),
                loreDash("<#A9B4C3>购买订阅消耗磁带"),
            )
        )
    }
}

/**
 * 选择订阅时长。
 */
class SubscriptionDurationMenu(private val def: SubscriptionDef) {

    companion object {
        /** 时长条目槽位（3 行菜单中间一行起） */
        val DURATION_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16)
    }

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = SubscriptionManager.getData(uuid, def.id)
        val now = System.currentTimeMillis()
        val active = data?.isActive(now) == true
        val tape = CurrencyManager.getBalance(player, "tape")

        player.openMenu<Chest>(
            mmJsonUtil.serialize("${def.gradientTag}<b>${def.displayName} · 选择时长</gradient>".toComponent())
        ) {
            rows(3)
            fillBackground(27)

            if (def.pricing.isEmpty()) {
                set(13, guiItem(
                    XMaterial.BARRIER,
                    "<#E08A98><b>该订阅未配置价格",
                    listOf(
                        loreSection("说明"),
                        loreDash("<#A9B4C3>请在 subscriptions/${def.id}.yml 中配置 pricing"),
                    )
                ))
            }

            for ((index, pricing) in def.pricing.withIndex()) {
                if (index >= DURATION_SLOTS.size) break
                set(DURATION_SLOTS[index], buildDurationItem(pricing, tape, active, data, now)) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    SubscriptionConfirmMenu(def, pricing).open(clicker)
                }
            }

            set(18, guiItem(
                XMaterial.ARROW,
                "<#8FD4A6><b>返回",
                listOf(loreDash("<#A9B4C3>返回订阅中心")),
                model = "kangelitem:previous_no"
            )) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                SubscriptionMenu().open(clicker)
            }

            set(26, guiItem(
                XMaterial.BARRIER,
                "<#E08A98><b>关闭",
                listOf(loreDash("<#A9B4C3>点击关闭"))
            )) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
            }
        }
    }

    private fun buildDurationItem(
        pricing: SubscriptionPricing,
        tape: Long,
        active: Boolean,
        data: SubscriptionData?,
        now: Long,
    ): ItemStack {
        val affordable = tape >= pricing.price
        val lore = mutableListOf<String>()
        lore += loreSection("购买信息")
        lore += loreInfo("订阅", "${def.colorTag}${def.displayName}")
        lore += loreInfo("时长", "<#EFDF8F>${pricing.durationText} <#6E7A8C>(${pricing.key})")
        lore += loreInfo("价格", "<#DFBD7C>${pricing.price} <#F2F5FA>磁带")
        lore += loreInfo("余额", "${if (affordable) "<#8FD4A6>" else "<#E08A98>"}$tape ${if (affordable) "✓" else "✗"}")
        lore += loreSpace()
        lore += loreSection("到期后")
        if (data == null) {
            lore += loreDash("<#A9B4C3>首次购买：立即获得首购奖励")
            lore += loreDash("<#A9B4C3>到期时间自购买时起算")
        } else if (active) {
            lore += loreDash("<#A9B4C3>续费：在当前到期时间上叠加 ${pricing.durationText}")
            lore += loreInfo("当前到期", "<#EFDF8F>${SubscriptionManager.formatTime(data.expireTime)}")
        } else {
            lore += loreDash("<#A9B4C3>重新订阅：自购买时重新起算，加成池回满")
            lore += loreDash("<#E08A98>首购奖励不会重复发放")
        }
        lore += loreSpace()
        lore += "<!i> <#EFDF8F>点击 <#8FD4A6>进入确认购买"

        return guiItem(def.icon, def.iconFallback, "${def.colorTag}<b>${pricing.durationText}", lore)
    }
}

/**
 * 确认购买。
 */
class SubscriptionConfirmMenu(
    private val def: SubscriptionDef,
    private val pricing: SubscriptionPricing,
) {

    fun open(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = SubscriptionManager.getData(uuid, def.id)
        val now = System.currentTimeMillis()
        val active = data?.isActive(now) == true
        val tape = CurrencyManager.getBalance(player, "tape")
        val affordable = tape >= pricing.price

        player.openMenu<Chest>(
            mmJsonUtil.serialize("${def.gradientTag}<b>确认购买</gradient>".toComponent())
        ) {
            rows(3)
            fillBackground(27)

            set(13, buildInfoItem(tape, affordable, active, data, now))

            if (affordable) {
                set(11, guiItem(
                    XMaterial.LIME_DYE,
                    "<#8FD4A6><b>确认购买",
                    listOf(
                        loreInfo("订阅", "${def.colorTag}${def.displayName}"),
                        loreInfo("时长", "<#EFDF8F>${pricing.durationText}"),
                        loreInfo("花费", "<#DFBD7C>${pricing.price} <#F2F5FA>磁带"),
                        loreSpace(),
                        "<!i> <#EFDF8F>点击 <#8FD4A6>确认购买",
                    )
                )) {
                    val result = SubscriptionManager.purchase(clicker, def.id, pricing.key)
                    when (result) {
                        SubscriptionManager.PurchaseResult.SUCCESS -> {
                            clicker.playSound(clicker.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                            SubscriptionMenu().open(clicker)
                        }
                        SubscriptionManager.PurchaseResult.NOT_ENOUGH_TAPE -> {
                            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                            clicker.sendComponent("<#E08A98>磁带不足，需要 <#EFDF8F>${pricing.price} <#E08A98>磁带")
                        }
                        SubscriptionManager.PurchaseResult.UNKNOWN_SUBSCRIPTION -> {
                            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                            clicker.sendComponent("<#E08A98>该订阅不存在或已被移除")
                        }
                        SubscriptionManager.PurchaseResult.UNKNOWN_DURATION -> {
                            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                            clicker.sendComponent("<#E08A98>该时长不可用")
                        }
                    }
                }
            }

            set(15, guiItem(
                XMaterial.BARRIER,
                "<#E08A98><b>取消",
                listOf(loreDash("<#A9B4C3>返回选择时长"))
            )) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                SubscriptionDurationMenu(def).open(clicker)
            }
        }
    }

    private fun buildInfoItem(
        tape: Long,
        affordable: Boolean,
        active: Boolean,
        data: SubscriptionData?,
        now: Long,
    ): ItemStack {
        val lore = mutableListOf<String>()
        lore += loreSection("订单")
        lore += loreInfo("订阅", "${def.colorTag}${def.displayName}")
        lore += loreInfo("时长", "<#EFDF8F>${pricing.durationText} <#6E7A8C>(${pricing.key})")
        lore += loreInfo("价格", "<#DFBD7C>${pricing.price} <#F2F5FA>磁带")
        lore += loreInfo("余额", "${if (affordable) "<#8FD4A6>" else "<#E08A98>"}$tape")
        lore += loreSpace()
        lore += loreSection("购买后")
        when {
            data == null -> {
                lore += loreDash("<#8FD4A6>立即获得首购奖励")
                lore += loreDash("<#A9B4C3>到期时间: <#EFDF8F>${SubscriptionManager.formatTime(now + pricing.durationMs)}")
            }
            active -> {
                lore += loreDash("<#A9B4C3>续费后到期: <#EFDF8F>${SubscriptionManager.formatTime(data.expireTime + pricing.durationMs)}")
            }
            else -> {
                lore += loreDash("<#A9B4C3>重新订阅到期: <#EFDF8F>${SubscriptionManager.formatTime(now + pricing.durationMs)}")
                lore += loreDash("<#A9B4C3>加成池回满")
            }
        }
        return guiItem(def.icon, def.iconFallback, "${def.colorTag}<b>${def.displayName}", lore)
    }
}
