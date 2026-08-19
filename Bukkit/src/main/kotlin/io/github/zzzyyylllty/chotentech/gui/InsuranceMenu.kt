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
import io.github.zzzyyylllty.chotentech.util.loreInfo
import io.github.zzzyyylllty.chotentech.util.loreSection

class InsuranceMenu {

    private val insuranceTitle by lazy { config.getString("insurance.insurance-title", "物品投保") ?: "物品投保" }

    fun open(player: Player) {
        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${insuranceTitle}".toComponent())) {
            rows(6)

            map(
                "CCC------",
                "---------",
                "-@@@@@@@-",
                "-@@@@@@@-",
                "-@@@@@@@-",
                "-B--C--B-"
            )

            set('C', guiItem(XMaterial.COMPASS, "<aqua><bold>菜单", listOf("<!i> <yellow>点击 <green>打开菜单"), model = "kangelitem:void")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            onBuild { _, inv ->
                // Left info (slot 46)
                inv.setItem(46, guiItem(XMaterial.BOOK, "<gold>投保说明", listOf(
                    loreSection("说明"),
                    "<!i><gray>将工具或盔甲放入中间格子",
                    "<!i><gold>[确认投保] <gray>完成投保",
                    "<!i><gray>仅工具和盔甲可以投保",
                    "<!i><gray>投保物品死亡不掉落"
                )))

                // Right info — price list (slot 52)
                val priceLore = mutableListOf<String>()
                val tiers = config.getConfigurationSection("insurance.tiers")
                if (tiers != null) {
                    for (key in tiers.getKeys(false)) {
                        priceLore += loreInfo("$key", "<gold>${tiers.getDouble(key, 0.0).toLong()}")
                    }
                }
                inv.setItem(52, guiItem(XMaterial.GOLD_INGOT, "<yellow>价格表", priceLore))

                // Confirm button (slot 49)
                inv.setItem(49, guiItem(XMaterial.ANVIL, "<gold>■ 确认投保", listOf(
                    loreSection("说明"),
                    "<!i><gray>投保中间区域的所有物品",
                    "<!i><gray>无法投保的物品将自动返还"
                )))
            }

            // Deposit slots: allow free interaction
            onClick(lock = false) { event ->
                val slot = event.rawSlot
                val topSize = event.view.topInventory.size
                if (slot !in 0 until topSize) {
                    event.isCancelled = true
                    return@onClick
                }

                // Confirm button：给中间物品打投保标记，不关闭 GUI，物品可继续拿出
                if (slot == 49) {
                    event.isCancelled = true
                    player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1f)
                    processInsurance(player, event.view.topInventory)
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
                val topInv = event.view.topInventory
                for (slot in (19..25) + (28..34) + (37..43)) {
                    val item = topInv.getItem(slot)
                    if (item != null && !item.type.isAir) {
                        p.inventory.addItem(item).values.forEach { leftover ->
                            p.world.dropItemNaturally(p.location, leftover)
                        }
                        topInv.setItem(slot, ItemStack(Material.AIR))
                    }
                }
            }
        }
    }

    /** 只给中间区域的可投保物品打上保险标记并扣费，不关闭 GUI，物品可随时取出 */
    private fun processInsurance(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val depositSlots = (19..25) + (28..34) + (37..43)

        // 收集需要投保的物品
        val items = depositSlots.mapNotNull { slot ->
            val item = inventory.getItem(slot)
            if (item != null && !item.type.isAir && InsuranceManager.isInsurable(item) && !InsuranceManager.isAlreadyInsured(item)) item
            else null
        }

        if (items.isEmpty()) {
            player.sendMessage(mmUtil.deserialize("<red>请放入需要投保的物品！"))
            return
        }

        // 计算总费用并校验余额
        val totalCost = InsuranceManager.calculateTotalCost(items)
        if (totalCost > 0) {
            if (!EconomyUtil.has(player, totalCost)) {
                player.sendMessage(
                    mmUtil.deserialize(
                        "<red>余额不足！需要 <yellow>$totalCost</yellow>，当前余额 <yellow>${EconomyUtil.getBalance(player)}</yellow>"
                    )
                )
                return
            }
        }

        // 逐个投保，原位替换为带标记的物品
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
                // 原位替换为带标记版本，玩家可直接取出
                inventory.setItem(slot, InsuranceManager.tagItem(item.clone(), player))
            } else {
                skipped++
            }
        }

        saveData(player)

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
    }
}
