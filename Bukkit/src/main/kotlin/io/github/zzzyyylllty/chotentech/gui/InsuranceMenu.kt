package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.DataManager.saveData
import io.github.zzzyyylllty.chotentech.function.InsuranceManager
import io.github.zzzyyylllty.chotentech.util.EconomyUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import io.github.zzzyyylllty.chotentech.util.guiItem

class InsuranceMenu {

    private val insuranceTitle by lazy { config.getString("insurance.insurance-title", "物品投保") ?: "物品投保" }

    fun open(player: Player) {
        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${insuranceTitle}".toComponent())) {
            rows(6)

            map(
                "CCC      ",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " B  C  B "
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<gray>点击打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            onBuild { _, inv ->
                // Left info (slot 46)
                inv.setItem(46, guiItem(XMaterial.BOOK, "<gold>投保说明", listOf(
                    "<gray>将工具或盔甲放入中间格子",
                    "<gray>点击 <gold>[确认投保] <gray>完成投保",
                    "<gray>仅工具和盔甲可以投保",
                    "<gray>投保物品死亡不掉落"
                )))

                // Right info — price list (slot 52)
                val priceLore = mutableListOf<String>()
                val tiers = config.getConfigurationSection("insurance.tiers")
                if (tiers != null) {
                    for (key in tiers.getKeys(false)) {
                        priceLore += "<gray>$key: <gold>${tiers.getDouble(key, 0.0).toLong()}"
                    }
                }
                inv.setItem(52, guiItem(XMaterial.GOLD_INGOT, "<yellow>价格表", priceLore))

                // Confirm button (slot 49)
                inv.setItem(49, guiItem(XMaterial.ANVIL, "<gold>■ 确认投保", listOf(
                    "<gray>投保中间区域的所有物品",
                    "<gray>点击后自动返还无法投保的物品"
                )))
            }

            // Deposit slots: allow free interaction
            onClick(lock = false) { event ->
                val slot = event.rawSlot
                val topSize = event.view.topInventory.size
                if (slot !in 0 until topSize) return@onClick

                // Confirm button
                if (slot == 49) {
                    event.isCancelled = true
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                    processInsuranceAndClose(player, event.view.topInventory)
                    return@onClick
                }

                // Allow deposit slots
                val depositSlots = (19..25) + (28..34) + (37..43)
                if (slot in depositSlots) return@onClick

                // Everything else — locked
                event.isCancelled = true
            }

            onClose { event ->
                val p = event.player as? Player ?: return@onClose
                val dropSlots = (19..25) + (28..34) + (37..43)
                for (slot in dropSlots) {
                    val item = event.inventory.getItem(slot)
                    if (item != null && !item.type.isAir) {
                        p.inventory.addItem(item).values.forEach { leftover ->
                            p.world.dropItemNaturally(p.location, leftover)
                        }
                    }
                }
            }
        }
    }

    private fun processInsuranceAndClose(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val depositSlots = (19..25) + (28..34) + (37..43)

        // 收集可投保物品
        val items = depositSlots.mapNotNull { slot ->
            val item = inventory.getItem(slot)
            if (item != null && !item.type.isAir && InsuranceManager.isInsurable(item) && !InsuranceManager.isAlreadyInsured(item)) item
            else null
        }

        if (items.isEmpty()) {
            player.sendMessage(mmUtil.deserialize("<red>请放入需要投保的物品！"))
            return
        }

        // 计算总费用
        val totalCost = InsuranceManager.calculateTotalCost(items)
        if (totalCost > 0) {
            if (!EconomyUtil.has(player, totalCost)) {
                player.sendMessage(
                    mmUtil.deserialize(
                        "<red>余额不足！需要 <yellow>$totalCost</yellow>，当前余额 <yellow>${EconomyUtil.getEconomy()?.getBalance(player)?.toLong() ?: 0}</yellow>"
                    )
                )
                return
            }
        }

        // 逐个投保
        var insured = 0
        var skipped = 0
        var totalPaid = 0.0

        for (slot in depositSlots) {
            val item = inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue

            if (!InsuranceManager.isInsurable(item)) {
                skipped++
                continue
            }
            if (InsuranceManager.isAlreadyInsured(item)) {
                skipped++
                continue
            }

            val cost = InsuranceManager.getInsuranceCost(item)
            if (InsuranceManager.tryInsureItem(player, item.clone())) {
                totalPaid += cost
                insured++
                // 投保成功：替换为已标记版本，后续返还逻辑会还回给玩家
                inventory.setItem(slot, InsuranceManager.tagItem(item.clone(), player))
            } else {
                skipped++
            }
        }

        // 返还所有剩余物品（非投保物品）
        for (slot in depositSlots) {
            val item = inventory.getItem(slot)
            if (item == null || item.type.isAir) continue
            player.inventory.addItem(item).values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover)
            }
            inventory.setItem(slot, ItemStack(Material.AIR))
        }

        saveData(player)

        // 发送消息
        if (insured > 0) {
            val msg = buildString {
                append("<green>✔ 成功投保 <yellow>$insured</yellow> 个物品")
                if (totalPaid > 0) append("，花费 <yellow>${EconomyUtil.format(totalPaid)}</yellow>")
                append("！")
            }
            player.sendMessage(mmUtil.deserialize(msg))
        }
        if (skipped > 0) {
            player.sendMessage(mmUtil.deserialize("<gray>⚠ $skipped 个物品无法投保（非工具/盔甲或已投保）"))
        }

        // 立即关闭页面
        player.closeInventory()
    }
}
