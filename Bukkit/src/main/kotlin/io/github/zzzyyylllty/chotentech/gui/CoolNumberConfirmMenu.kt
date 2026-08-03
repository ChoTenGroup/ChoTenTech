package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.CoolNumberInfo
import io.github.zzzyyylllty.chotentech.data.CoolNumberTier
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.IDData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.CoolNumberManager
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submitAsync
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.loreDash
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection
import io.github.zzzyyylllty.chotentech.util.loreSpace

class CoolNumberConfirmMenu(
    private val info: CoolNumberInfo
) {
    private var paymentMethod: PaymentMethod = PaymentMethod.POINT
    private var tapeUpgradeLevels: Int = 0
    private var canUpgrade: Boolean = false

    enum class PaymentMethod(val displayName: String) {
        POINT("贸易点"),
        TAPE("磁带")
    }

    fun open(player: Player) {
        val currentTier = CoolNumberManager.getPlayerCurrentTier(player)
        canUpgrade = CoolNumberManager.canUpgradeTier(currentTier)

        player.openMenu<Chest>(
            mmJsonUtil.serialize(
                "<black><font:minecraft:fusionpixel10>确认购买".toComponent()
            )
        ) {
            rows(6)

            map(
                "#########",
                "####A####",
                "#########",
                "X##W#S##V",
                "#########",
                "#########"
            )

            // fill background
            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            // ── A: Info item (row 1, slot 4) ──
            set('A', buildInfoItem(player, currentTier))

            // ── X (close) row 3, slot 0 ──
            set('X', guiItem(XMaterial.BARRIER, "<red>关闭")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                CoolNumberMenu().open(clicker)
            }

            // ── W: Toggle payment (row 3, slot 3) ──
            set('W', buildPaymentToggleItem()) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                paymentMethod = if (paymentMethod == PaymentMethod.POINT) PaymentMethod.TAPE else PaymentMethod.POINT
                open(clicker)
            }

            // ── S: Tape upgrade (row 3, slot 5) ──
            set('S', buildUpgradeItem(currentTier)) {
                if (!canUpgrade) {
                    clicker.sendMessage(" §c当前等级已是黑金及以上，无法继续升级")
                    return@set
                }
                val resultingTier = CoolNumberManager.getResultingTier(currentTier, info.score, tapeUpgradeLevels + 1)
                if (resultingTier.ordinal >= CoolNumberTier.BLACKGOLD.ordinal) {
                    // still allow upgrade to the tier right before crystal
                }
                if (!CoolNumberManager.canAffordTape(clicker, CoolNumberManager.TAPE_UPGRADE_COST)) {
                    clicker.sendMessage(" §c磁带不足，需要 ${CoolNumberManager.TAPE_UPGRADE_COST} 磁带")
                    return@set
                }
                clicker.playSound(clicker.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                tapeUpgradeLevels++
                open(clicker)
            }

            // ── V: Confirm ──
            set('V', buildConfirmItem()) {
                clicker.playSound(clicker.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
                confirmPurchase(clicker, currentTier)
            }
        }
    }

    // ── Item builders ──

    private fun buildInfoItem(player: Player, currentTier: CoolNumberTier): ItemStack {
        val item = XMaterial.FILLED_MAP.parseItem() ?: ItemStack(Material.FILLED_MAP)
        val meta = item.itemMeta ?: return item

        val resultingTier = CoolNumberManager.getResultingTier(currentTier, info.score, tapeUpgradeLevels)
        val pointPrice = info.pointPrice
        val tapePrice = info.tapePrice + CoolNumberManager.TAPE_UPGRADE_COST * tapeUpgradeLevels
        val canAffordPoint = CoolNumberManager.canAffordPoint(player, pointPrice)
        val canAffordTape = CoolNumberManager.canAffordTape(player, tapePrice)

        meta.displayName("${info.tier.color}<b>${info.number}".toComponent())
        meta.lore(listOf(
            loreInfo("分数", "<yellow>${info.score}"),
            loreInfo("基础等级", "${info.tier.color}${info.tier.displayName}"),
            loreInfo("你的当前等级", "${currentTier.color}${currentTier.displayName}"),
            loreInfo("购买后等级", "${resultingTier.color}${resultingTier.displayName}"),
            loreSpace(),
            loreInfo("付款方式", "<green>${paymentMethod.displayName}"),
            loreSpace(),
            loreInfo("贸易点价格", "<green>$pointPrice ${if (canAffordPoint) "✓" else "✗"}"),
            loreInfo("磁带价格", "<aqua>$tapePrice ${if (canAffordTape) "✓" else "✗"}"),
            loreSpace(),
            loreInfo("磁带升级", "<aqua>+${CoolNumberManager.TAPE_UPGRADE_COST * tapeUpgradeLevels}"),
            loreDash("使用 ${tapeUpgradeLevels} 级升级 (已选)"),
            loreSpace(),
            "<!i><green>W: 切换付款方式",
            "<!i><green>S: +1888磁带升一级",
            "<!i><green>V: 确认购买"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildPaymentToggleItem(): ItemStack {
        val item = XMaterial.GOLD_NUGGET.parseItem() ?: ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta ?: return item
        meta.displayName("<gold><b>付款方式: ${paymentMethod.displayName}".toComponent())
        meta.lore(listOf(
            loreInfo("当前", "<green>${paymentMethod.displayName}"),
            "<!i> <yellow>点击 <green>切换为 <aqua>${
                if (paymentMethod == PaymentMethod.POINT) PaymentMethod.TAPE.displayName
                else PaymentMethod.POINT.displayName
            }",
            loreSpace(),
            loreInfo("贸易点价格", "<green>${info.pointPrice}"),
            loreInfo("磁带价格", "<aqua>${info.tapePrice + CoolNumberManager.TAPE_UPGRADE_COST * tapeUpgradeLevels}")
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildUpgradeItem(currentTier: CoolNumberTier): ItemStack {
        val item = (if (canUpgrade) XMaterial.EXPERIENCE_BOTTLE.parseItem() else XMaterial.GUNPOWDER.parseItem())
            ?: ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta ?: return item

        if (canUpgrade) {
            val nextTier = CoolNumberManager.getResultingTier(currentTier, info.score, tapeUpgradeLevels + 1)
            meta.displayName("<b><aqua>磁带升级 (已选: $tapeUpgradeLevels 级)".toComponent())
            meta.lore(listOf(
                "<!i><gray>消耗 ${CoolNumberManager.TAPE_UPGRADE_COST} 磁带升一级",
                loreInfo("当前等级", "${currentTier.color}${currentTier.displayName}"),
                loreInfo("升级后", "${nextTier.color}${nextTier.displayName}"),
                loreSpace(),
                "<!i> <yellow>点击 <green>选择升级"
            ).toComponent())
        } else {
            meta.displayName("<b><red>无法升级".toComponent())
            meta.lore(listOf(
                loreSection("说明"),
                "<!i><gray>当前等级已是黑金及以上",
                "<!i><gray>无法继续使用磁带升级"
            ).toComponent())
        }
        item.setItemMeta(meta)
        return item
    }

    private fun buildConfirmItem(): ItemStack {
        val item = XMaterial.LIME_DYE.parseItem() ?: ItemStack(Material.LIME_DYE)
        val meta = item.itemMeta ?: return item
        meta.displayName("<b><green>确认购买</b>".toComponent())
        meta.lore(listOf(
            loreInfo("号码", info.number),
            loreInfo("价格", "<green>${getCurrentPrice()}"),
            loreSpace(),
            "<!i> <yellow>点击 <green>确认购买此靓号"
        ).toComponent())
        item.setItemMeta(meta)
        return item
    }

    // ── Purchase logic ──

    private fun getCurrentPrice(): String {
        return if (paymentMethod == PaymentMethod.POINT) {
            "${info.pointPrice} 贸易点"
        } else {
            "${info.tapePrice + CoolNumberManager.TAPE_UPGRADE_COST * tapeUpgradeLevels} 磁带"
        }
    }

    private fun confirmPurchase(player: Player, currentTier: CoolNumberTier) {
        // final availability check (DB)
        submitAsync {
            if (DataManager.isIDExist(info.number)) {
                player.sendMessage(" §c该号码已被其他玩家使用！")
                return@submitAsync
            }
            if (CoolNumberManager.bannedNumbers.any { info.number.contains(it) }) {
                player.sendMessage(" §c该号码已被禁止选择！")
                return@submitAsync
            }

            val resultingTier = CoolNumberManager.getResultingTier(currentTier, info.score, tapeUpgradeLevels)

            when (paymentMethod) {
                PaymentMethod.POINT -> {
                    if (info.pointPrice > CoolNumberManager.MAX_POINT_PRICE) {
                        player.sendMessage(" §c该号码贸易点价格过高 (超过 $CoolNumberManager.MAX_POINT_PRICE)，无法使用贸易点购买")
                        return@submitAsync
                    }
                    if (!CoolNumberManager.deductPoint(player, info.pointPrice)) {
                        player.sendMessage(" §c贸易点不足！需要 ${info.pointPrice} 贸易点")
                        return@submitAsync
                    }
                }
                PaymentMethod.TAPE -> {
                    val totalTape = info.tapePrice + CoolNumberManager.TAPE_UPGRADE_COST * tapeUpgradeLevels
                    if (!CoolNumberManager.deductTape(player, totalTape)) {
                        player.sendMessage(" §c磁带不足！需要 $totalTape 磁带")
                        return@submitAsync
                    }
                }
            }

            // Apply CID change
            val playerData = player.getData() ?: run {
                player.sendMessage(" §c玩家数据异常！")
                return@submitAsync
            }

            val newCid = IDData(
                id = info.number.toLong(),
                type = resultingTier.tag
            )

            DataManager.modifyUserIDData(newCid, playerData)
            player.sendMessage(" §a靓号购买成功！")
            player.sendMessage(" §7新号码: ${resultingTier.color}${info.number}")
            player.sendMessage(" §7当前等级: ${resultingTier.color}${resultingTier.displayName}")
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
    }
}
