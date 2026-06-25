package io.github.zzzyyylllty.chotentech.function.hideout

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest

class HideoutMainMenu(private val baseKey: String) {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val hideout = data.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
        if (!hideout.unlocked) {
            player.sendComponent("<red>该基地的个人基地尚未解锁！")
            return
        }
        val modules = HideoutConfig.modules.values.toList()

        player.openMenu<PageableChest<HideoutModule>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>个人基地 - ${baseKey.uppercase()}".toComponent())
        ) {
            rows(6)
            map(
                "CCC    <>",
                "         ",
                " ####### ",
                " ####### ",
                " ####### ",
                "         "
            )
            slotsBy('#')

            elements { modules }

            onGenerate { _, mod, _, _ ->
                buildModuleItem(mod, hideout)
            }

            onClick { event, mod ->
                event.isCancelled = true
                event.clicker.closeInventory()
                openModuleDetail(event.clicker, hideout, mod)
            }

            set('C', ItemStack(Material.COMPASS).apply {
                editMeta { m ->
                    m.displayName("<b><aqua>菜单".toComponent())
                    m.lore(listOf("<gray>点击打开菜单".toComponent()))
                    m.itemModel = NamespacedKey.fromString("kangelitem:void")
                }
            }) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            setPreviousPage(7) { _, page ->
                ItemStack(Material.ARROW).apply {
                    editMeta { m ->
                        m.displayName("<aqua><b>上一页".toComponent())
                        m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:previous" else "kangelitem:previous_no")
                    }
                }
            }

            setNextPage(8) { _, page ->
                ItemStack(Material.ARROW).apply {
                    editMeta { m ->
                        m.displayName("<aqua><b>下一页".toComponent())
                        m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:next" else "kangelitem:next_no")
                    }
                }
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }
        }
    }

    private fun buildModuleItem(mod: HideoutModule, hideout: HideoutPlayerData): ItemStack {
        val level = hideout.getModuleLevel(mod.id)
        val built = level > 0
        val upgrading = hideout.pendingUpgrades.any { it.moduleId == mod.id }
        val mat = if (upgrading) {
            Material.FURNACE
        } else if (built) {
            try { Material.valueOf(mod.material.uppercase()) } catch (_: Exception) { Material.STONE }
        } else Material.GRAY_STAINED_GLASS_PANE

        return ItemStack(mat).apply {
            editMeta { meta ->
                if (upgrading) {
                    val pending = hideout.pendingUpgrades.first { it.moduleId == mod.id }
                    meta.displayName("<gold>${mod.name} <yellow>⬆ Lv.${pending.targetLevel}".toComponent())
                    meta.lore(listOf(
                        "<!i><yellow>升级中...".toComponent(),
                        "<!i><gray>剩余时间: <yellow>${pending.remainingTime} <gray>秒".toComponent()
                    ))
                } else if (!built) {
                    meta.displayName("<dark_gray><b>???</b>".toComponent())
                    meta.lore(listOf(
                        "<!i><gray>尚未建造".toComponent(),
                        "<!i><gray>点击查看条件".toComponent()
                    ))
                } else {
                    val lore = mutableListOf<Component>()
                    lore += "<!i><#cccccc>${mod.description}".toComponent()
                    val lvCfg = mod.getLevelConfig(level)
                    if (lvCfg != null) appendModuleStats(lvCfg, hideout, lore)
                    lore += Component.empty()
                    if (mod.usage > 0) lore += "<!i><gray>电力消耗: <yellow>${mod.usage}/s".toComponent()
                    lore += "<!i><gray>点击管理".toComponent()
                    meta.displayName("<gold>${mod.name} <gray>Lv.$level".toComponent())
                    meta.lore(lore)
                }
                mod.itemModel?.let { meta.itemModel = NamespacedKey.fromString(it) }
            }
        }
    }

    private fun appendModuleStats(lvCfg: HideoutModuleLevel, hideout: HideoutPlayerData, lore: MutableList<Component>) {
        if (lvCfg.fuelCapacity > 0) {
            lore += "<!i><gray>燃料容量: <yellow>${"%.0f".format(lvCfg.fuelCapacity)}".toComponent()
            lore += "<!i><gray>燃料效率: <yellow>x${lvCfg.fuelEfficiency}".toComponent()
            lore += "<!i><gray>当前燃料: <yellow>${"%.1f".format(hideout.fuelAmount)}<gray>/<yellow>${"%.0f".format(HideoutManager.getFuelCapacity(hideout))}".toComponent()
        }
        if (lvCfg.craftSpeed > 0) {
            lore += "<!i><gray>合成速度: <yellow>x${lvCfg.craftSpeed}".toComponent()
            lore += "<!i><gray>队列: <yellow>${hideout.craftingQueues.size}".toComponent()
        }
        if (lvCfg.productionRate > 0 && lvCfg.productionItem != null) {
            val acc = hideout.productionAccumulators[lvCfg.productionItem] ?: 0.0
            lore += "<!i><gray>产出率: <yellow>${"%.2f".format(lvCfg.productionRate)}/s".toComponent()
            lore += "<!i><gray>累计: <yellow>${"%.1f".format(acc)}".toComponent()
        }
        if (lvCfg.productionRate > 0 && lvCfg.productionItem == null) {
            val acc = hideout.productionAccumulators["medical_station"] ?: 0.0
            lore += "<!i><gray>产出率: <yellow>${"%.2f".format(lvCfg.productionRate)}/s".toComponent()
            lore += "<!i><gray>累计点: <yellow>${"%.1f".format(acc)}".toComponent()
        }
        if (lvCfg.fuelSavePercent > 0) {
            lore += "<!i><gray>燃料节省: <green>${lvCfg.fuelSavePercent}%".toComponent()
        }
        if (lvCfg.gpuSlots > 0) {
            lore += "<!i><gray>显卡槽: <yellow>${hideout.minerGpuItems.size}<gray>/<yellow>${lvCfg.gpuSlots}".toComponent()
            val acc = hideout.productionAccumulators["mine"] ?: 0.0
            lore += "<!i><gray>累计CitCoin: <yellow>${"%.1f".format(acc)}".toComponent()
        }
        if (lvCfg.buffDuration > 0) {
            lore += "<!i><gray>休息增益: <yellow>${lvCfg.buffDuration / 60} 分 (等级 ${lvCfg.buffAmplifier + 1})".toComponent()
        }
    }

    private fun openModuleDetail(player: Player, hideout: HideoutPlayerData, mod: HideoutModule) {
        val level = hideout.getModuleLevel(mod.id)
        val built = level > 0
        val lvCfg = mod.getLevelConfig(level)
        val upgrading = hideout.pendingUpgrades.any { it.moduleId == mod.id }
        val pendingUpgrade = hideout.pendingUpgrades.firstOrNull { it.moduleId == mod.id }

        player.openMenu<Chest>(mmJsonUtil.serialize(
            "<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${mod.name}".toComponent()
        )) {
            rows(6)
            handLocked(false)

            map(
                "CCC    B",
                "         ",
                " ####### ",
                "   ^^^   ",
                "   AAA   ",
                "         "
            )

            set('C', ItemStack(Material.COMPASS).apply {
                editMeta { m ->
                    m.displayName("<b><aqua>返回".toComponent())
                    m.lore(listOf("<gray>返回基地总览".toComponent()))
                    m.itemModel = NamespacedKey.fromString("kangelitem:void")
                }
            }) {
                clicker.closeInventory()
                HideoutMainMenu(baseKey).open(clicker)
            }

            onBuild { _, inv ->
                // 右上角 - 信息书
                val infoItem = buildInfoBook(mod, hideout, level, built, lvCfg)
                inv.setItem(8, infoItem)

                if (upgrading && pendingUpgrade != null) {
                    val upgItem = ItemStack(Material.FURNACE).apply {
                        editMeta { m ->
                            m.displayName("<gold>${mod.name} <yellow>⬆ 升级中".toComponent())
                            m.lore(listOf(
                                "<!i><yellow>升级到 Lv.${pendingUpgrade.targetLevel}".toComponent(),
                                "<!i><gray>剩余时间: <yellow>${pendingUpgrade.remainingTime} <gray>秒".toComponent(),
                                "<!i><gray>升级完成后会自动生效".toComponent()
                            ))
                        }
                    }
                    inv.setItem(22, upgItem)
                } else if (built && level < mod.maxLevel) {
                    val nextLv = level + 1
                    val nextCfg = mod.getLevelConfig(nextLv) ?: return@onBuild

                    // 升级材料 - 以 Sertraline 物品形式展示
                    val costSlots = listOf(19, 20, 21, 22, 23, 24, 25)
                    var slotIdx = 0
                    for (cost in nextCfg.upgradeCost) {
                        if (slotIdx >= costSlots.size) break
                        val sertId = cost.sertralineId ?: continue
                        val item = io.github.zzzyyylllty.sertraline.Sertraline.api()
                            .buildItem(sertId, player, amount = cost.amount) ?: continue
                        val has = countItem(player, sertId)
                        item.editMeta { m ->
                            m.lore(listOf(
                                "<!i><gray>需要: <white>${cost.amount}".toComponent(),
                                "<!i><gray>拥有: ${if (has >= cost.amount) "<green>" else "<red>"}$has".toComponent()
                            ))
                        }
                        inv.setItem(costSlots[slotIdx], item)
                        slotIdx++
                    }
                    // 空位填充
                    for (i in slotIdx until costSlots.size) {
                        inv.setItem(costSlots[i], ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                            editMeta { m ->
                                m.displayName(" ".toComponent())
                                m.itemModel = NamespacedKey.fromString("kangelitem:void")
                            }
                        })
                    }

                    // 升级按钮
                    val upgItem = ItemStack(Material.ANVIL).apply {
                        editMeta { m ->
                            m.displayName("<gold>⬆ 升级到 Lv.$nextLv".toComponent())
                            val uLore = mutableListOf<Component>()
                            buildUpgradeStats(nextCfg, uLore)
                            if (nextCfg.currencyCost.isNotEmpty()) {
                                uLore += Component.empty()
                                uLore += "<!i><gray>所需货币:".toComponent()
                                for (cc in nextCfg.currencyCost) {
                                    val bal = CurrencyUtil.getBalance(player, cc.currency)
                                    val curName = CurrencyUtil.getCurrency(cc.currency)?.displayName ?: cc.currency
                                    uLore += "<!i> <gray>- <yellow>$curName <gray>x${"%.0f".format(cc.amount)} ${if (bal >= cc.amount) "<green>✓" else "<red>✗"}".toComponent()
                                }
                            }
                            m.lore(uLore)
                        }
                    }
                    inv.setItem(31, upgItem)
                }

                // 操作按钮 (slot 38-40)
                if (built && !upgrading) {
                    buildActionItems(inv, player, hideout, mod, level, lvCfg)
                }

                // 合成按钮
                if (built && !upgrading && HideoutConfig.getRecipesForModule(mod.id).isNotEmpty()) {
                    inv.setItem(33, ItemStack(Material.CRAFTING_TABLE).apply {
                        editMeta { m ->
                            m.displayName("<gold>合成".toComponent())
                            m.lore(listOf("<gray>打开合成界面".toComponent()))
                        }
                    })
                }
            }

            onClick(lock = false) { event ->
                val raw = event.rawSlot
                if (raw < 0 || raw >= event.view.topInventory.size) return@onClick
                event.isCancelled = true

                when (raw) {
                    31 -> {
                        if (!upgrading) {
                            handleUpgradeClick(event.clicker, hideout, mod, level, lvCfg)
                        }
                    }
                    in 38..40 -> handleActionClick(event.clicker, hideout, mod, lvCfg)
                    33 -> {
                        if (built && !upgrading && HideoutConfig.getRecipesForModule(mod.id).isNotEmpty()) {
                            event.clicker.closeInventory()
                            HideoutCraftMenu(baseKey).open(event.clicker)
                        }
                    }
                }
            }
        }
    }

    private fun buildInfoBook(mod: HideoutModule, hideout: HideoutPlayerData, level: Int, built: Boolean, lvCfg: HideoutModuleLevel?): ItemStack {
        return ItemStack(Material.BOOK).apply {
            editMeta { m ->
                m.displayName("<gold>${mod.name} <gray>Lv.$level".toComponent())
                val lore = mutableListOf<Component>()
                lore += "<!i><#cccccc>${mod.description}".toComponent()
                if (built && lvCfg != null) {
                    appendModuleStats(lvCfg, hideout, lore)
                } else {
                    lore += "<!i><red>未建造".toComponent()
                }
                if (!built) {
                    val lv1 = mod.getLevelConfig(1)
                    if (lv1 != null) {
                        lore += Component.empty()
                        lore += "<!i><gold>建造所需:".toComponent()
                        for (cost in lv1.upgradeCost) {
                            val itemName = cost.sertralineId?.let { ItemNameHelper.getDisplayName(it) } ?: cost.material
                            lore += "<!i> <gray>- <white>$itemName <gray>x${cost.amount}".toComponent()
                        }
                        for (cc in lv1.currencyCost) {
                            val curName = CurrencyUtil.getCurrency(cc.currency)?.displayName ?: cc.currency
                            lore += "<!i> <gray>- <yellow>$curName <gray>x${"%.0f".format(cc.amount)}".toComponent()
                        }
                    }
                }
                m.lore(lore)
            }
        }
    }

    private fun buildUpgradeStats(nextCfg: HideoutModuleLevel, lore: MutableList<Component>) {
        if (nextCfg.upgradeTime > 0) lore += "<!i><gray>升级耗时: <yellow>${nextCfg.upgradeTime} 秒</yellow> (可离线)".toComponent()
        if (nextCfg.fuelCapacity > 0) lore += "<!i><gray>燃料容量: <green>${"%.0f".format(nextCfg.fuelCapacity)}".toComponent()
        if (nextCfg.fuelEfficiency > 1) lore += "<!i><gray>燃料效率: <green>x${nextCfg.fuelEfficiency}".toComponent()
        if (nextCfg.craftSpeed > 0) lore += "<!i><gray>合成速度: <green>x${nextCfg.craftSpeed}".toComponent()
        if (nextCfg.productionRate > 0) lore += "<!i><gray>产出率: <green>${"%.2f".format(nextCfg.productionRate)}/s".toComponent()
        if (nextCfg.fuelSavePercent > 0) lore += "<!i><gray>燃料节省: <green>${nextCfg.fuelSavePercent}%".toComponent()
        if (nextCfg.gpuSlots > 0) lore += "<!i><gray>显卡槽: <green>${nextCfg.gpuSlots}".toComponent()
        if (nextCfg.buffDuration > 0) lore += "<!i><gray>休息增益: <green>${nextCfg.buffDuration / 60} 分".toComponent()
    }

    private fun buildActionItems(inv: org.bukkit.inventory.Inventory, player: Player, hideout: HideoutPlayerData, mod: HideoutModule, level: Int, lvCfg: HideoutModuleLevel?) {
        val actionSlot = 40
        when {
            lvCfg != null && lvCfg.fuelCapacity > 0 -> {
                inv.setItem(actionSlot, ItemStack(Material.FURNACE).apply {
                    editMeta { m ->
                        m.displayName("<gold>燃料管理".toComponent())
                        m.lore(listOf(
                            "<gray>点击管理发电机燃料".toComponent(),
                            "<gray>当前燃料: <yellow>${"%.1f".format(hideout.fuelAmount)}".toComponent()
                        ))
                    }
                })
            }
            mod.id == "bedroom" && lvCfg != null -> {
                val cd = 8 * 60 * 60 * 1000L
                val elapsed = System.currentTimeMillis() - hideout.bedroomLastUse
                val ready = elapsed >= cd
                inv.setItem(actionSlot, ItemStack(if (ready) Material.GREEN_BED else Material.RED_BED).apply {
                    editMeta { m ->
                        m.displayName("<gold>休息".toComponent())
                        val lore = mutableListOf(
                            "<gray>每8小时可休息一次".toComponent(),
                            "<gray>获得生命恢复 ${if (lvCfg.buffAmplifier > 0) "Lv.${lvCfg.buffAmplifier + 1}" else ""} (${lvCfg.buffDuration / 60}分)".toComponent(),
                        )
                        if (!ready) {
                            val remaining = (cd - elapsed) / 1000
                            lore += "<red>冷却中: ${remaining / 60}分${remaining % 60}秒".toComponent()
                        } else {
                            lore += "<green>✓ 可以休息".toComponent()
                        }
                        m.lore(lore)
                    }
                })
            }
            mod.id == "medical_station" && lvCfg != null -> {
                val points = hideout.productionAccumulators["medical_station"] ?: 0.0
                inv.setItem(actionSlot, ItemStack(Material.POTION).apply {
                    editMeta { m ->
                        m.displayName("<gold>治疗".toComponent())
                        m.lore(listOf(
                            "<gray>消耗1医疗点数恢复1生命值".toComponent(),
                            "<gray>累计点数: <yellow>${"%.1f".format(points)}".toComponent()
                        ))
                    }
                })
            }
            mod.id == "mine" && lvCfg != null -> {
                val acc = hideout.productionAccumulators["mine"] ?: 0.0
                inv.setItem(actionSlot, ItemStack(Material.GOLD_NUGGET).apply {
                    editMeta { m ->
                        m.displayName("<gold>提取CitCoin".toComponent())
                        m.lore(listOf(
                            "<gray>累计: <yellow>${"%.1f".format(acc)}".toComponent(),
                            "<gray>可提取: <yellow>${acc.toInt()}".toComponent(),
                            "<gray>点击提取".toComponent()
                        ))
                    }
                })
            }
            lvCfg != null && lvCfg.productionRate > 0 && lvCfg.productionItem != null -> {
                val acc = hideout.productionAccumulators[mod.id] ?: 0.0
                inv.setItem(actionSlot, ItemStack(Material.DISPENSER).apply {
                    editMeta { m ->
                        m.displayName("<gold>提取产出".toComponent())
                        m.lore(listOf(
                            "<gray>累计: <yellow>${"%.1f".format(acc)}".toComponent(),
                            "<gray>点击提取".toComponent()
                        ))
                    }
                })
            }
        }
    }

    private fun handleUpgradeClick(player: Player, hideout: HideoutPlayerData, mod: HideoutModule, level: Int, lvCfg: HideoutModuleLevel?) {
        if (level > 0 && level < mod.maxLevel) {
            player.closeInventory()
            if (HideoutManager.upgradeModule(player, mod.id)) {
                HideoutMainMenu(baseKey).open(player)
            } else {
                openModuleDetail(player, hideout, mod)
            }
        } else if (level <= 0) {
            player.closeInventory()
            if (HideoutManager.upgradeModule(player, mod.id)) {
                HideoutMainMenu(baseKey).open(player)
            } else {
                openModuleDetail(player, hideout, mod)
            }
        }
    }

    private fun handleActionClick(player: Player, hideout: HideoutPlayerData, mod: HideoutModule, lvCfg: HideoutModuleLevel?) {
        player.closeInventory()
        when {
            lvCfg != null && lvCfg.fuelCapacity > 0 -> {
                HideoutGeneratorMenu(baseKey).open(player)
            }
            mod.id == "bedroom" -> {
                HideoutManager.useBedroom(player)
                HideoutMainMenu(baseKey).open(player)
            }
            mod.id == "medical_station" -> {
                if (HideoutManager.healAtMedicalStation(player)) {
                    // done
                }
                HideoutMainMenu(baseKey).open(player)
            }
            mod.id == "mine" -> {
                if (HideoutManager.collectMinerOutput(player)) {
                    // done
                }
                HideoutMainMenu(baseKey).open(player)
            }
            lvCfg != null && lvCfg.productionRate > 0 && lvCfg.productionItem != null -> {
                HideoutManager.collectProduction(player, mod.id)
                HideoutMainMenu(baseKey).open(player)
            }
            else -> {
                HideoutMainMenu(baseKey).open(player)
            }
        }
    }

    private fun countItem(player: Player, sertralineId: String): Int {
        var count = 0
        for (item in player.inventory.contents) {
            if (item == null || item.type.isAir) continue
            val id = io.github.zzzyyylllty.sertraline.Sertraline.api().getId(item)
            if (id == sertralineId) count += item.amount
        }
        return count
    }
}
