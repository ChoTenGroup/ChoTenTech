package io.github.zzzyyylllty.chotentech.shop

import ink.ptms.chemdah.api.ChemdahAPI
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.ExternalItemHelper
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.returnItems
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.compat.replacePlaceholder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ShopMenu : Listener {

    private val itemOutputNames = ConcurrentHashMap<String, String>()

    init {
        try {
            org.bukkit.Bukkit.getPluginManager().registerEvents(this, taboolib.platform.BukkitPlugin.getInstance())
        } catch (_: Exception) {}
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        PlayerShopSession.remove(event.player)
    }

    fun loadItemOutputNames() {
        itemOutputNames.clear()
        val file = File(getDataFolder(), "itemoutputnames.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        for (key in config.getKeys(false)) {
            val name = config.getString(key) ?: continue
            itemOutputNames[key] = name
        }
    }

    /** Strip plugin prefix and look up display name from itemoutputnames.yml */
    private fun formatItemName(itemId: String): String {
        val key = if (itemId.contains(":")) itemId.substringAfter(":") else itemId
        return itemOutputNames[key] ?: itemId
    }

    /** 供其他模块查询物品显示名(itemoutputnames.yml),查不到时返回原 ID */
    fun lookupItemName(itemId: String): String {
        // 缓存懒加载只发生在商店 GUI 打开时,其他模块(如切出点条件提示)可能先于商店访问,
        // 这里自行确保已加载,否则永远查不到名称
        if (itemOutputNames.isEmpty()) loadItemOutputNames()
        return formatItemName(itemId)
    }

    fun open(player: Player, shopId: String) {
        if (itemOutputNames.isEmpty()) loadItemOutputNames()
        val shop = ShopManager.getShop(shopId) ?: run {
            player.sendMessage("<red>商店 <yellow>$shopId<white> <red>不存在<white>".toComponent())
            return
        }
        val session = PlayerShopSession.get(player)
        session.currentShopId = shopId
        session.clearSelection()
        openShop(player, shop, session)
    }

    private fun computeTitleStatus(session: PlayerShopSession, items: List<ShopItemEntry>, player: Player, shop: ShopConfig): String {
        if (session.selectedItems.isEmpty()) {
            return if (session.isBuyMode) "buyshop_notsel" else "sellshop_notsel"
        }
        val anyUnmet = session.selectedItems.keys.any { itemKey ->
            val entry = items.find { it.key == itemKey } ?: return@any false
            if (session.isBuyMode) {
                val reqs = entry.buyRequirements
                if (reqs.isNotEmpty() && reqs.any { !checkRequirement(player, it, shop) }) return@any true
                if (!canAffordAny(player, entry)) return@any true
            } else {
                val reqs = entry.sellRequirements
                if (reqs.isNotEmpty() && reqs.any { !checkRequirement(player, it, shop) }) return@any true
                val count = countItem(player, entry.id)
                entry.sell.forEach { reward ->
                    if (reward.type == CostType.ITEM && count < reward.amount) return@any true
                }
            }
            false
        }
        return if (anyUnmet) {
            if (session.isBuyMode) "buyshop_notmet" else "sellshop_notmet"
        } else {
            if (session.isBuyMode) "buyshop" else "sellshop"
        }
    }

    private fun openShop(player: Player, shop: ShopConfig, session: PlayerShopSession) {
        val items = shop.items.values.toList()
        val titleStr = "<white><shift:-16><image:kangelicon:${computeTitleStatus(session, items, player, shop)}><shift:-176><aqua><font:minecraft:fusionpixel10>${shop.name}"

        player.openMenu<PageableChest<ShopItemEntry>>(
            mmJsonUtil.serialize(titleStr.toComponent())
        ) {
            rows(6)
            page(session.currentPage)
            onPageChange { session.currentPage = page }

            val layout = if (session.isBuyMode) shop.guiBuy.split("\n") else shop.guiSell.split("\n")
            map(*layout.toTypedArray())

            slotsBy('I')

            elements { getFilteredItems(player, shop, session) }

            onGenerate { p, entry, _, _ ->
                buildShopItem(p, entry, session.isSelected(entry.key), shop)
            }

            onClick { event, entry ->
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.currentPage = page

                // Block selection if mode-specific requirements not met
                val reqs = if (session.isBuyMode) entry.buyRequirements else entry.sellRequirements
                if (!checkAllRequirements(clicker, reqs, shop)) {
                    return@onClick
                }

                // Gift pack: open sub-menu instead of normal selection
                if (entry.type == "giftpack") {
                    clicker.closeInventory()
                    submit(delay = 1) {
                        GiftPackMenu().open(clicker, entry.giftPack ?: return@submit)
                    }
                    return@onClick
                }

                val click = event.clickEvent().click
                when (click) {
                    ClickType.LEFT -> session.addItem(entry.key, 1)
                    ClickType.SHIFT_LEFT -> session.addItem(entry.key, 10)
                    ClickType.RIGHT -> session.removeItem(entry.key, 1)
                    ClickType.SHIFT_RIGHT -> session.deselectItem(entry.key)
                    else -> {}
                }

                resetElementsCache()
                refreshItemSlots(clicker, shop, layout, session)
                refreshTitle(clicker, shop, session)
            }

            // Mode switch: slots 0-2 = buy mode, slots 3-5 = sell mode
            run {
                val buyIsCurrent = session.isBuyMode
                val sellIsCurrent = !session.isBuyMode

                // Build buy mode indicator (slots 0, 1, 2)
                val buyStack = buildDisplayItem(player, shop.guiItems.modeBuy, "BOOK")
                buyStack.itemMeta?.let { meta ->
                    val lore = mutableListOf<String>()
                    if (shop.guiItems.modeBuy.lore.isNotBlank()) {
                        shop.guiItems.modeBuy.lore.split("\n").forEach { lore.add("<!i><gray>$it") }
                    }
                    lore.add("")
                    lore.add(if (buyIsCurrent) "<!i><green>▶ 当前模式" else "<!i><gray>点击切换")
                    meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
                    NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
                    buyStack.setItemMeta(meta)
                }
                for (slot in 0..2) {
                    set(slot, buyStack) {
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        if (buyIsCurrent) return@set
                        session.isBuyMode = true
                        session.clearSelection()
                        session.currentPage = 0
                        clicker.closeInventory()
                        submit(delay = 1) { openShop(clicker, shop, session) }
                    }
                }

                // Build sell mode indicator (slots 3, 4, 5)
                val sellStack = buildDisplayItem(player, shop.guiItems.modeSell, "BOOK")
                sellStack.itemMeta?.let { meta ->
                    val lore = mutableListOf<String>()
                    if (shop.guiItems.modeSell.lore.isNotBlank()) {
                        shop.guiItems.modeSell.lore.split("\n").forEach { lore.add("<!i><gray>$it") }
                    }
                    lore.add("")
                    lore.add(if (sellIsCurrent) "<!i><green>▶ 当前模式" else "<!i><gray>点击切换")
                    meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
                    NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
                    sellStack.setItemMeta(meta)
                }
                for (slot in 3..5) {
                    set(slot, sellStack) {
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        if (sellIsCurrent) return@set
                        session.isBuyMode = false
                        session.clearSelection()
                        session.currentPage = 0
                        clicker.closeInventory()
                        submit(delay = 1) { openShop(clicker, shop, session) }
                    }
                }
            }

            // Previous page (<)
            setPreviousPage(getSlotFor('<', layout)) { _, hasPrev ->
                val btn = if (hasPrev) shop.guiItems.previous.have else shop.guiItems.previous.not
                val stack = buildDisplayItem(player, btn, "ARROW")
                val modelKey = if (hasPrev) "kangelitem:previous" else "kangelitem:previous_no"
                stack.itemMeta?.let { m ->
                    NamespacedKey.fromString(modelKey)?.let { m.itemModel = it }
                    stack.setItemMeta(m)
                }
                stack
            }

            // Next page (>)
            setNextPage(getSlotFor('>', layout)) { _, hasNext ->
                val btn = if (hasNext) shop.guiItems.next.have else shop.guiItems.next.not
                val stack = buildDisplayItem(player, btn, "ARROW")
                val modelKey = if (hasNext) "kangelitem:next" else "kangelitem:next_no"
                stack.itemMeta?.let { m ->
                    NamespacedKey.fromString(modelKey)?.let { m.itemModel = it }
                    stack.setItemMeta(m)
                }
                stack
            }

            // Knowledge/info button (N)
            set('N', buildKnowledgeItem(player, shop, session)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // Confirm button (C) — left=confirm, right=clear selection
            set('C', buildConfirmItem(player, shop, session)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (clickEvent().click == ClickType.RIGHT) {
                    session.clearSelection()
                    resetElementsCache()
                    refreshItemSlots(clicker, shop, layout, session)
                    refreshTitle(clicker, shop, session)
                } else {
                    processConfirm(clicker, shop, session)
                }
            }

            // O = sell placement mode (top-right slot)
            val sellLore = if (session.isBuyMode) "只在出售模式下可用" else "将物品放入格子中出售"
            val sellModel = if (session.isBuyMode) "kangelicon:sellgui_not" else "kangelicon:sellgui"
            val sellStack = buildDisplayItem(player, DisplayItem("<white><b>出售物品", sellLore), "HOPPER")
            sellStack.itemMeta?.let { m ->
                NamespacedKey.fromString(sellModel)?.let { m.itemModel = it }
                sellStack.setItemMeta(m)
            }
            set('O', sellStack) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (session.isBuyMode) {
                    clicker.sendMessage("<red>该功能只在出售模式下可用<white>".toComponent())
                    return@set
                }
                clicker.closeInventory()
                submit(delay = 1) {
                    openSellPlacementMenu(clicker, shop)
                }
            }

            // Filter 1: requirement filter (slot 46)
            set(46, buildFilterItem(player, session.filterReq.displayName, FilterRequirement.entries.map { it.displayName }, "条件筛选", "COMPARATOR", filterReqModel(session.filterReq))) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.filterReq = cycleFilter(session.filterReq, FilterRequirement.entries.toTypedArray())
                session.currentPage = 0
                resetElementsCache()
                val inv = clicker.openInventory.topInventory
                inv.setItem(46, buildFilterItem(clicker, session.filterReq.displayName, FilterRequirement.entries.map { it.displayName }, "条件筛选", "COMPARATOR", filterReqModel(session.filterReq)))
                inv.setItem(47, buildFilterItem(clicker, session.filterInv.displayName, FilterInventory.entries.map { it.displayName }, "物品筛选", "HOPPER", filterInvModel(session.filterInv)))
                inv.setItem(48, buildFilterItem(clicker, session.filterAfford.displayName, FilterAffordability.entries.map { it.displayName }, "购买力筛选", "GOLD_NUGGET", filterAffordModel(session.filterAfford)))
                refreshFilteredSlots(clicker, shop, layout, session)
            }

            // Filter 2: inventory filter (slot 47)
            set(47, buildFilterItem(player, session.filterInv.displayName, FilterInventory.entries.map { it.displayName }, "物品筛选", "HOPPER", filterInvModel(session.filterInv))) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.filterInv = cycleFilter(session.filterInv, FilterInventory.entries.toTypedArray())
                session.currentPage = 0
                resetElementsCache()
                val inv = clicker.openInventory.topInventory
                inv.setItem(46, buildFilterItem(clicker, session.filterReq.displayName, FilterRequirement.entries.map { it.displayName }, "条件筛选", "COMPARATOR", filterReqModel(session.filterReq)))
                inv.setItem(47, buildFilterItem(clicker, session.filterInv.displayName, FilterInventory.entries.map { it.displayName }, "物品筛选", "HOPPER", filterInvModel(session.filterInv)))
                inv.setItem(48, buildFilterItem(clicker, session.filterAfford.displayName, FilterAffordability.entries.map { it.displayName }, "购买力筛选", "GOLD_NUGGET", filterAffordModel(session.filterAfford)))
                refreshFilteredSlots(clicker, shop, layout, session)
            }

            // Filter 3: affordability filter (slot 48)
            set(48, buildFilterItem(player, session.filterAfford.displayName, FilterAffordability.entries.map { it.displayName }, "购买力筛选", "GOLD_NUGGET", filterAffordModel(session.filterAfford))) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.filterAfford = cycleFilter(session.filterAfford, FilterAffordability.entries.toTypedArray())
                session.currentPage = 0
                resetElementsCache()
                val inv = clicker.openInventory.topInventory
                inv.setItem(46, buildFilterItem(clicker, session.filterReq.displayName, FilterRequirement.entries.map { it.displayName }, "条件筛选", "COMPARATOR", filterReqModel(session.filterReq)))
                inv.setItem(47, buildFilterItem(clicker, session.filterInv.displayName, FilterInventory.entries.map { it.displayName }, "物品筛选", "HOPPER", filterInvModel(session.filterInv)))
                inv.setItem(48, buildFilterItem(clicker, session.filterAfford.displayName, FilterAffordability.entries.map { it.displayName }, "购买力筛选", "GOLD_NUGGET", filterAffordModel(session.filterAfford)))
                refreshFilteredSlots(clicker, shop, layout, session)
            }
        }
    }

    /** Update the open inventory title to reflect current state */
    private fun refreshTitle(player: Player, shop: ShopConfig, session: PlayerShopSession) {
        val items = shop.items.values.toList()
        val status = computeTitleStatus(session, items, player, shop)
        val newTitle = "<white><shift:-16><image:kangelicon:$status><shift:-176><aqua><font:minecraft:fusionpixel10>${shop.name}"
        val view = player.openInventory
        val oldTop = view.topInventory
        val newTop = org.bukkit.Bukkit.createInventory(oldTop.holder, oldTop.size, newTitle.toComponent())
        for (i in 0 until oldTop.size) {
            newTop.setItem(i, oldTop.getItem(i))
        }
        player.openInventory(newTop)
    }

    /** Compute the sellgui status based on whether any items are placed in I slots */
    private fun computeSellGuiStatus(player: Player, shop: ShopConfig, layout: List<String>): String {
        val inv = player.openInventory.topInventory
        val iSlots = getAllSlotsFor('I', layout)
        for (slot in iSlots) {
            val item = inv.getItem(slot) ?: continue
            if (!item.type.isAir) return "sellshop"
        }
        return "sellshop_notsel"
    }

    /** Refresh the sell placement menu title to reflect current item state */
    private fun refreshSellPlacementTitle(player: Player, shop: ShopConfig, layout: List<String>) {
        val status = computeSellGuiStatus(player, shop, layout)
        val newTitle = "<white><shift:-16><image:kangelicon:$status><shift:-176><aqua><font:minecraft:fusionpixel10>${shop.name}"
        val view = player.openInventory
        val oldTop = view.topInventory
        val newTop = org.bukkit.Bukkit.createInventory(oldTop.holder, oldTop.size, newTitle.toComponent())
        for (i in 0 until oldTop.size) {
            newTop.setItem(i, oldTop.getItem(i))
        }
        player.openInventory(newTop)
    }

    /** Update all confirm and info button slots in-place without closing the UI */
    private fun updateDynamicSlots(player: Player, shop: ShopConfig, layout: List<String>) {
        val inv = player.openInventory.topInventory
        val session = PlayerShopSession.get(player)
        for (slot in getAllSlotsFor('C', layout)) {
            inv.setItem(slot, buildConfirmItem(player, shop, session))
        }
        for (slot in getAllSlotsFor('N', layout)) {
            inv.setItem(slot, buildKnowledgeItem(player, shop, session))
        }
    }

    /** Update 'I' slots using filtered items (does not close the UI) */
    private fun refreshFilteredSlots(player: Player, shop: ShopConfig, layout: List<String>, session: PlayerShopSession) {
        val inv = player.openInventory.topInventory
        val items = getFilteredItems(player, shop, session)
        val iSlots = getISlotIndices(layout)
        val startIndex = session.currentPage * iSlots.size

        for ((offset, slot) in iSlots.withIndex()) {
            val idx = startIndex + offset
            val entry = items.getOrNull(idx)
            if (entry != null) {
                inv.setItem(slot, buildShopItem(player, entry, session.isSelected(entry.key), shop))
            } else {
                inv.setItem(slot, ItemStack(Material.AIR))
            }
        }

        updateDynamicSlots(player, shop, layout)
    }

    /** Directly update all element ('I') slots in the open inventory */
    private fun refreshItemSlots(player: Player, shop: ShopConfig, layout: List<String>, session: PlayerShopSession) {
        val inv = player.openInventory.topInventory
        val items = shop.items.values.toList()
        val iSlots = getISlotIndices(layout)
        val startIndex = session.currentPage * iSlots.size

        for ((offset, slot) in iSlots.withIndex()) {
            val idx = startIndex + offset
            val entry = items.getOrNull(idx)
            if (entry != null) {
                inv.setItem(slot, buildShopItem(player, entry, session.isSelected(entry.key), shop))
            } else {
                inv.setItem(slot, ItemStack(Material.AIR))
            }
        }

        // Also refresh confirm/knowledge buttons
        updateDynamicSlots(player, shop, layout)
    }

    /** Get all indices for a given layout character */
    private fun getAllSlotsFor(char: Char, layout: List<String>): List<Int> {
        val slots = mutableListOf<Int>()
        for ((row, line) in layout.withIndex()) {
            for ((col, c) in line.withIndex()) {
                if (c == char) slots.add(row * 9 + col)
            }
        }
        return slots
    }

    /** Get all 'I' slot indices in layout order */
    private fun getISlotIndices(layout: List<String>): List<Int> {
        val slots = mutableListOf<Int>()
        for ((row, line) in layout.withIndex()) {
            for ((col, char) in line.withIndex()) {
                if (char == 'I') {
                    slots.add(row * 9 + col)
                }
            }
        }
        return slots
    }

    /** Check all requirements for an item, send failure message if any not met */
    private fun checkAllRequirements(player: Player, requirements: List<Requirement>, shop: ShopConfig? = null): Boolean {
        for (req in requirements) {
            if (!checkRequirement(player, req, shop)) {
                player.sendMessage("<red>需要: ${req.display}<white>".toComponent())
                return false
            }
        }
        return true
    }

    private fun buildShopItem(player: Player, entry: ShopItemEntry, selected: Boolean, shop: ShopConfig): ItemStack {
        // ── Gift pack special display ──
        if (entry.type == "giftpack") {
            val pack = entry.giftPack?.let { GiftPackManager.getPack(it) }
            val stack = resolveItem(player, entry.id) ?: ItemStack(Material.CHEST)
            val m = stack.itemMeta ?: return stack

            val glore = mutableListOf<String>()
            glore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
            glore.add("<!i><gold><bold>礼包商品")
            if (pack != null) {
                glore.add("<!i><white>${pack.name}")
                glore.add("<!i> ")
                glore.add("<!i><yellow>基础价格: <white>${pack.price} ${CurrencyManager.displayName(pack.currency)}")
                for ((itemId, modifier) in pack.priceModifiers) {
                    glore.add("<!i>  <red>※ ${itemId.substringAfterLast(":")} +$modifier ${CurrencyManager.displayName(pack.currency)}")
                }
                glore.add("<!i> ")
                glore.add("<!i><gray>内含物品:")
                for ((_, cat) in pack.categories) {
                    val names = cat.items.joinToString(", ") { it.displayName.removePrefix("<white>").removePrefix("<green>").removePrefix("<gold>") }
                    glore.add("<!i> <aqua>${cat.displayName.removePrefix("<white>").removePrefix("<green>").removePrefix("<gold>")}: <white>$names")
                }
            }
            glore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
            glore.add("<!i><green>点击打开礼包菜单")
            glore.add("<!i><gray>可在菜单中选择具体物品")

            m.lore(glore.map { it.replacePlaceholder(player).toComponent() })
            stack.setItemMeta(m)
            return stack
        }

        val itemStack = resolveItem(player, entry.id) ?: ItemStack(Material.BARRIER)
        val meta = itemStack.itemMeta ?: return itemStack

        if (selected) {
            meta.setEnchantmentGlintOverride(true)
        }

        val lore = mutableListOf<String>()
        lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")

        val session = PlayerShopSession.get(player)

        // Requirements display (mode-specific)
        val displayReqs = if (session.isBuyMode) entry.buyRequirements else entry.sellRequirements
        if (displayReqs.isNotEmpty()) {
            for (req in displayReqs) {
                val met = checkRequirement(player, req, shop)
                val color = if (met) "<green>" else "<red>"
                lore.add("<!i>${color}条件: ${req.display}")
            }
            lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
        }

        // Costs display (buy mode) or rewards (sell mode)
        if (session.isBuyMode && entry.buy.isNotEmpty()) {
            lore.add("<!i><yellow>价格:")
            for (cost in entry.buy) {
                when (cost.type) {
                    CostType.CURRENCY -> {
                        val currencyName = CurrencyManager.displayName(cost.currency ?: "money")
                        val amount = if (cost.dynamicPricing != null) getDynamicPrice(cost, entry.key) else cost.amount
                        val dynStr = if (cost.dynamicPricing != null) " <gray>(浮动 ${cost.dynamicPricing.min}-${cost.dynamicPricing.max})" else ""
                        lore.add("<!i> <aqua>● $currencyName: <white>$amount$dynStr")
                    }
                    CostType.ITEM -> {
                        lore.add("<!i> <aqua>● ${formatItemName(cost.item ?: "?")} <gray>x${cost.amount}")
                    }
                }
            }
        } else if (!session.isBuyMode && entry.sell.isNotEmpty()) {
            lore.add("<!i><yellow>出售可得:")
            for (reward in entry.sell) {
                when (reward.type) {
                    CostType.CURRENCY -> {
                        val currencyName = CurrencyManager.displayName(reward.currency ?: "money")
                        lore.add("<!i> <aqua>● $currencyName: <white>${reward.amount}")
                    }
                    CostType.ITEM -> {
                        lore.add("<!i> <aqua>● ${formatItemName(reward.item ?: "?")} <gray>x${reward.amount}")
                    }
                }
            }
        }

        // Limit display
        if (entry.limit != null) {
            if (entry.limit.player > 0) {
                ShopManager.checkAndResetPlayerLimit(entry.key, player.uniqueId.toString(), entry.limit.playerReset)
                val bought = ShopManager.getPlayerPurchaseCount(entry.key, player.uniqueId.toString())
                lore.add("<!i><gray>个人限购: $bought/${entry.limit.player}")
            }
            if (entry.limit.global > 0) {
                ShopManager.checkAndResetGlobalLimit(entry.key, entry.limit.globalReset)
                val globalBought = ShopManager.getGlobalPurchaseCount(entry.key)
                lore.add("<!i><gray>全服已购: $globalBought/${entry.limit.global}")
            }
        }

        lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")

        // Check if adding 1 more is affordable or meets conditions
        val qtyNow = session.getQuantity(entry.key)
        val cannotAddMore = if (session.isBuyMode) {
            // Check conditions
            entry.buyRequirements.any { !checkRequirement(player, it, shop) } ||
            // Check affordability: current total cost + 1 more of this item
            !canAffordTotalWithExtra(player, shop, session, entry, 1)
        } else {
            entry.sellRequirements.any { !checkRequirement(player, it, shop) } ||
            countItem(player, entry.id) < qtyNow + 1
        }
        if (cannotAddMore) {
            lore.add("<!i><red>条件不足")
        }

        if (selected) {
            val qty = PlayerShopSession.get(player).getQuantity(entry.key)
            lore.add("<!i><green>已选择: $qty")
            lore.add("<!i><gray>左键+1 · 潜行左键+10 · 右键-1 · 潜行右键取消")
        } else {
            lore.add("<!i><green>点击选择")
        }

        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        itemStack.setItemMeta(meta)
        return itemStack
    }

    private fun buildDisplayItem(player: Player, item: DisplayItem, defaultMaterial: String): ItemStack {
        val mat = try {
            item.material?.let { XMaterial.valueOf(it.uppercase()) } ?: XMaterial.valueOf(defaultMaterial.uppercase())
        } catch (_: Exception) {
            XMaterial.valueOf(defaultMaterial.uppercase())
        }
        val result = mat.parseItem() ?: ItemStack(Material.BARRIER)
        val meta = result.itemMeta ?: return result

        meta.displayName((item.name + "<white>").replacePlaceholder(player).toComponent())
        if (item.lore.isNotBlank()) {
            val loreLines = item.lore.split("\n").map {
                "<!i><gray>${it.replacePlaceholder(player)}"
            }
            meta.lore(loreLines.toComponent())
        }
        if (item.model != null) {
            NamespacedKey.fromString(item.model)?.let { meta.itemModel = it }
        }
        result.setItemMeta(meta)
        return result
    }

    /** Determine the player's current merchant level for this shop */
    private fun getCurrentMerchantLevel(player: Player, shop: ShopConfig): Int {
        if (shop.levels.isEmpty()) return 0
        val maxLevel = shop.levels.keys.maxOrNull() ?: return 0
        for (level in 1..maxLevel) {
            val reqs = shop.levels[level] ?: continue
            if (!meetsLevelRequirements(player, shop, reqs)) {
                return level - 1
            }
        }
        return maxLevel
    }

    private fun meetsSingleRequirement(player: Player, shop: ShopConfig, req: LevelRequirement): Boolean {
        val current = when (req.type) {
            "level" -> getPlayerLevel(player).toLong()
            "affection" -> ReputationManager.getAffection(player, req.affection)
            "quota" -> ShopManager.getPlayerSales(shop.idef, player.uniqueId.toString())
            else -> 0L
        }
        return current >= req.value
    }

    private fun meetsLevelRequirements(player: Player, shop: ShopConfig, reqs: List<LevelRequirement>): Boolean {
        for (req in reqs) {
            when (req.type) {
                "level" -> {
                    val current = getPlayerLevel(player)
                    if (current < req.value) return false
                }
                "affection" -> {
                    val current = ReputationManager.getAffection(player, req.affection)
                    if (current < req.value) return false
                }
                "quota" -> {
                    val current = ShopManager.getPlayerSales(shop.idef, player.uniqueId.toString())
                    if (current < req.value) return false
                }
            }
        }
        return true
    }

    private fun getPlayerLevel(player: Player): Int {
        return "%player_level%".replacePlaceholder(player).toIntOrNull() ?: 0
    }

    /** Format number with Chinese ten-thousands grouping (万位): 123456789 → 1,2345,6789 */
    private fun formatChineseNumber(number: Long): String {
        val str = number.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in str.lastIndex downTo 0) {
            if (count > 0 && count % 4 == 0) {
                sb.insert(0, ' ')
            }
            sb.insert(0, str[i])
            count++
        }
        return sb.toString()
    }

    private fun buildKnowledgeItem(player: Player, shop: ShopConfig, session: PlayerShopSession): ItemStack {
        val item = shop.guiItems.knowledge
        val mat = try {
            item.material?.let { XMaterial.valueOf(it.uppercase()) } ?: XMaterial.KNOWLEDGE_BOOK
        } catch (_: Exception) {
            XMaterial.KNOWLEDGE_BOOK
        }
        val result = mat.parseItem() ?: ItemStack(Material.KNOWLEDGE_BOOK)
        val meta = result.itemMeta ?: return result
        NamespacedKey.fromString("kangelicon:book")?.let { meta.itemModel = it }

        val modeStr = if (session.isBuyMode) "购买" else "出售"
        val moneyBalance = CurrencyManager.getBalance(player, "money")
        val pointBalance = CurrencyManager.getBalance(player, "point")
        val tapeBalance = CurrencyManager.getBalance(player, "tape")

        val moneyName = CurrencyManager.displayName("money")
        val pointName = CurrencyManager.displayName("point")
        val tapeName = CurrencyManager.displayName("tape")

        meta.displayName((item.name + "<white>").replacePlaceholder(player).toComponent())

        val loreLines = mutableListOf<String>()
        if (item.lore.isNotBlank()) {
            item.lore.split("\n").forEach { loreLines.add("<!i><gray>$it") }
        }
        loreLines.add("")
        loreLines.add("<!i><gold>当前模式: <white>$modeStr")
        loreLines.add("<!i><gold>你的货币:")
        loreLines.add("<!i> <aqua>$moneyName: <white>$moneyBalance")
        loreLines.add("<!i> <aqua>$pointName: <white>$pointBalance")
        loreLines.add("<!i> <aqua>$tapeName: <white>$tapeBalance")

        // Merchant level display
        if (shop.levels.isNotEmpty()) {
            loreLines.add("")
            val currentLevel = getCurrentMerchantLevel(player, shop)
            val maxLevel = shop.levels.keys.maxOrNull() ?: 0
            val levelBar = (0..maxLevel).joinToString(" ") { lvl ->
                when {
                    lvl < currentLevel -> "<dark_gray>[$lvl]</dark_gray>"
                    lvl == currentLevel -> "<green>[$lvl]</green>"
                    else -> "<gray>[$lvl]"
                }
            }
            loreLines.add("<!i><gray>当前商人等级: $levelBar")

            val nextLevel = currentLevel + 1
            if (nextLevel <= maxLevel) {
                val reqs = shop.levels[nextLevel] ?: emptyList()
                if (reqs.isNotEmpty()) {
                    loreLines.add("")
                    loreLines.add("<!i><gray>下一级需求:")
                    for (req in reqs) {
                        val met = meetsSingleRequirement(player, shop, req)
                        val style = if (met) "<i><gray>" else "<white>"
                        when (req.type) {
                            "level" -> {
                                val current = getPlayerLevel(player)
                                loreLines.add("<!i> ${style}玩家等级 $current / ${req.value}")
                            }
                            "affection" -> {
                                val current = ReputationManager.getAffection(player, req.affection)
                                loreLines.add("<!i> ${style}信任度 $current / ${req.value}")
                            }
                            "quota" -> {
                                val current = ShopManager.getPlayerSales(shop.idef, player.uniqueId.toString())
                                loreLines.add("<!i> ${style}有效出售 ${formatChineseNumber(current)} / ${formatChineseNumber(req.value.toLong())}")
                            }
                        }
                    }
                }
            }
        }

        meta.lore(loreLines.map { it.replacePlaceholder(player).toComponent() })
        result.setItemMeta(meta)
        return result
    }

    private fun buildConfirmItem(player: Player, shop: ShopConfig, session: PlayerShopSession): ItemStack {
        val selected = session.selectedItems.entries.filter { it.value > 0 }

        if (selected.isEmpty()) {
            val cfg = if (session.isBuyMode) shop.guiItems.confirmBuy.not else shop.guiItems.confirmSell.not
            val stack = buildDisplayItem(player, cfg, "GREEN_CONCRETE")
            stack.itemMeta?.let { m ->
                NamespacedKey.fromString("kangelitem:void")?.let { m.itemModel = it }
                stack.setItemMeta(m)
            }
            return stack
        }

        val totalCurrency = mutableMapOf<String, Long>()
        val totalItems = mutableMapOf<String, Long>()
        var canAfford = true

        if (session.isBuyMode) {
            for ((itemKey, qty) in selected) {
                val entry = shop.items[itemKey] ?: continue
                for (cost in entry.buy) {
                    val unitPrice = if (cost.dynamicPricing != null) getDynamicPrice(cost, itemKey) else cost.amount
                    val amount = unitPrice * qty
                    when (cost.type) {
                        CostType.CURRENCY -> {
                            val currency = cost.currency ?: "money"
                            totalCurrency.merge(currency, amount, Long::plus)
                        }
                        CostType.ITEM -> {
                            totalItems.merge(cost.item ?: "?", amount, Long::plus)
                        }
                    }
                }
            }
            for ((currency, amount) in totalCurrency) {
                if (!CurrencyManager.hasBalance(player, currency, amount)) {
                    canAfford = false
                    break
                }
            }
            if (canAfford) {
                for ((item, amount) in totalItems) {
                    if (countItem(player, item) < amount) {
                        canAfford = false
                        break
                    }
                }
            }
        } else {
            for ((itemKey, qty) in selected) {
                val entry = shop.items[itemKey] ?: continue
                val count = countItem(player, entry.id)
                if (count < qty) {
                    canAfford = false
                }
                for (reward in entry.sell) {
                    val amount = reward.amount * qty
                    when (reward.type) {
                        CostType.CURRENCY -> {
                            totalCurrency.merge(reward.currency ?: "money", amount, Long::plus)
                        }
                        CostType.ITEM -> {
                            totalItems.merge(reward.item ?: "?", amount, Long::plus)
                        }
                    }
                }
            }
        }

        val confirmCfg = if (session.isBuyMode) {
            when {
                canAfford -> shop.guiItems.confirmBuy.have
                else -> shop.guiItems.confirmBuy.have.copy(
                    name = "<red><b>货币不足",
                    lore = "缺少足够的货币或物品\n无法购买"
                )
            }
        } else {
            when {
                canAfford -> shop.guiItems.confirmSell.have
                else -> shop.guiItems.confirmSell.have.copy(
                    name = "<red><b>物品不足",
                    lore = "你没有足够的物品\n无法出售"
                )
            }
        }

        val mat = try {
            confirmCfg.material?.let { XMaterial.valueOf(it.uppercase()) } ?: XMaterial.GREEN_CONCRETE
        } catch (_: Exception) {
            XMaterial.GREEN_CONCRETE
        }
        val result = mat.parseItem() ?: ItemStack(Material.GREEN_CONCRETE)
        val meta = result.itemMeta ?: return result
        NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }

        meta.displayName(confirmCfg.name.replacePlaceholder(player).toComponent())

        val loreLines = mutableListOf<String>()
        if (confirmCfg.lore.isNotBlank()) {
            confirmCfg.lore.split("\n").forEach { loreLines.add("<!i><gray>$it") }
        }

        loreLines.add("")
        loreLines.add("<!i><gold>已选物品:")
        for ((itemKey, qty) in selected) {
            val itemName = formatItemName(shop.items[itemKey]?.id ?: itemKey)
            loreLines.add("<!i> <white>$itemName <gray>x$qty")
        }

        if (session.isBuyMode) {
            loreLines.add("")
            loreLines.add("<!i><yellow>需要支付:")
            for ((currency, amount) in totalCurrency) {
                loreLines.add("<!i> <red>● ${CurrencyManager.displayName(currency)}: $amount")
            }
            for ((item, amount) in totalItems) {
                loreLines.add("<!i> <red>● ${formatItemName(item)} x$amount")
            }
            if (!canAfford) {
                loreLines.add("")
                loreLines.add("<!i><red><bold>货币不足!")
            } else {
                loreLines.add("")
                loreLines.add("<!i><green>点击确认购买")
            }
        } else {
            loreLines.add("")
            loreLines.add("<!i><yellow>可获得:")
            for ((currency, amount) in totalCurrency) {
                loreLines.add("<!i> <green>● ${CurrencyManager.displayName(currency)}: $amount")
            }
            for ((item, amount) in totalItems) {
                loreLines.add("<!i> <green>● ${formatItemName(item)} x$amount")
            }
            if (!canAfford) {
                loreLines.add("")
                loreLines.add("<!i><red><bold>物品不足!")
            } else {
                loreLines.add("")
                loreLines.add("<!i><green>点击确认出售")
            }
        }

        loreLines.add("<!i><gray>右键取消所有选择")

        meta.lore(loreLines.map { it.replacePlaceholder(player).toComponent() })

        if (!canAfford) {
            meta.setEnchantmentGlintOverride(false)
        }

        result.setItemMeta(meta)
        return result
    }

    private fun processConfirm(player: Player, shop: ShopConfig, session: PlayerShopSession) {
        val selected = session.selectedItems.entries.filter { it.value > 0 }
        if (selected.isEmpty()) {
            player.sendMessage("<red>你没有选择任何物品".toComponent())
            return
        }
        if (session.isBuyMode) processBuy(player, shop, session)
        else processSell(player, shop, session)
    }

    private fun processBuy(player: Player, shop: ShopConfig, session: PlayerShopSession) {
        val selected = session.selectedItems.entries.filter { it.value > 0 }
        val costs = mutableListOf<Pair<String, Long>>()
        val itemsToTake = mutableListOf<Pair<String, Long>>()

        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue

            // 兜底:未标注购买价格的商品不允许免费购买
            if (entry.buy.isEmpty()) {
                player.sendMessage("<red>商品 ${formatItemName(entry.id)} 未标注购买价格,无法购买<white>".toComponent())
                return
            }

            for (req in entry.buyRequirements) {
                if (!checkRequirement(player, req, shop)) {
                    player.sendMessage("<red>不满足购买 <yellow>${formatItemName(entry.id)} <red>的条件: ${req.display}<white>".toComponent())
                    return
                }
            }

            if (entry.limit != null) {
                if (entry.limit.player > 0) {
                    ShopManager.checkAndResetPlayerLimit(entry.key, player.uniqueId.toString(), entry.limit.playerReset)
                    val bought = ShopManager.getPlayerPurchaseCount(entry.key, player.uniqueId.toString())
                    if (bought + qty > entry.limit.player) {
                        player.sendMessage("<red>已达到个人限购数量 ($bought/${entry.limit.player})<white>".toComponent())
                        return
                    }
                }
                if (entry.limit.global > 0) {
                    ShopManager.checkAndResetGlobalLimit(entry.key, entry.limit.globalReset)
                    val globalBought = ShopManager.getGlobalPurchaseCount(entry.key)
                    if (globalBought + qty > entry.limit.global) {
                        player.sendMessage("<red>该商品全服已售罄".toComponent())
                        return
                    }
                }
            }

            for (cost in entry.buy) {
                val unitPrice = if (cost.dynamicPricing != null) getDynamicPrice(cost, itemKey) else cost.amount
                val amount = unitPrice * qty
                when (cost.type) {
                    CostType.CURRENCY -> costs.add((cost.currency ?: "money") to amount)
                    CostType.ITEM -> itemsToTake.add((cost.item ?: "?") to amount)
                }
            }
        }

        // Verify affordability
        for ((currency, amount) in costs) {
            if (!CurrencyManager.hasBalance(player, currency, amount)) {
                player.sendMessage("<red>${CurrencyManager.displayName(currency)}<red>不足! 需要 $amount<white>".toComponent())
                return
            }
        }
        for ((itemId, amount) in itemsToTake) {
            val count = countItem(player, itemId)
            if (count < amount) {
                player.sendMessage("<red>${formatItemName(itemId)}<red> 不足! 需要 $amount, 你有 $count<white>".toComponent())
                return
            }
        }

        // Pre-flight: item must be resolvable, otherwise giveItem would silently fail after payment
        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue
            if (resolveItem(player, entry.id) == null) {
                player.sendMessage("<red>商品 ${formatItemName(entry.id)} 无法发放，请联系管理员<white>".toComponent())
                return
            }
        }

        // Process payment
        for ((currency, amount) in costs) {
            CurrencyManager.removeBalance(player, currency, amount)
        }
        for ((itemId, amount) in itemsToTake) {
            takeItem(player, itemId, amount)
        }

        // Give purchased items
        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue
            val given = giveItem(player, entry.id, qty.toInt())
            if (given > 0) {
                player.sendMessage("<green>获得 <yellow>${formatItemName(entry.id)} <green>x$given<white>".toComponent())
                ShopManager.recordPurchase(entry.key, player.uniqueId.toString(), qty)
            }
        }

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.sendMessage("<green>购买成功!".toComponent())
        session.clearSelection()
        session.currentPage = 0
        submit(delay = 1) { openShop(player, shop, session) }
    }

    private fun processSell(player: Player, shop: ShopConfig, session: PlayerShopSession) {
        val selected = session.selectedItems.entries.filter { it.value > 0 }

        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue

            // 兜底:未标注出售价格的商品不允许白送
            if (entry.sell.isEmpty()) {
                player.sendMessage("<red>商品 ${formatItemName(entry.id)} 未标注出售价格,无法出售<white>".toComponent())
                return
            }

            for (req in entry.sellRequirements) {
                if (!checkRequirement(player, req, shop)) {
                    player.sendMessage("<red>不满足出售 <yellow>${formatItemName(entry.id)} <red>的条件: ${req.display}<white>".toComponent())
                    return
                }
            }
            val count = countItem(player, entry.id)
            if (count < qty) {
                player.sendMessage("<red>你没有足够的 <yellow>${formatItemName(entry.id)}<red>! 需要 $qty, 你有 $count<white>".toComponent())
                return
            }
        }

        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue
            takeItem(player, entry.id, qty)
        }

        val rewardSummary = mutableListOf<String>()
        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue
            for (reward in entry.sell) {
                val amount = reward.amount * qty
                when (reward.type) {
                    CostType.CURRENCY -> {
                        CurrencyManager.addBalance(player, reward.currency ?: "money", amount)
                        val name = CurrencyManager.displayName(reward.currency ?: "money")
                        rewardSummary.add("<yellow>$name <green>x$amount")
                    }
                    CostType.ITEM -> {
                        giveItem(player, reward.item ?: "?", amount.toInt())
                        rewardSummary.add("<yellow>${formatItemName(reward.item ?: "?")} <green>x$amount")
                    }
                }
            }
        }

        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        player.sendMessage("<green>出售成功! 获得: ${rewardSummary.joinToString(", ")}<white>".toComponent())

        // Record sales for level quota tracking
        for ((itemKey, qty) in selected) {
            val entry = shop.items[itemKey] ?: continue
            for (reward in entry.sell) {
                val amount = reward.amount * qty
                if (reward.type == CostType.CURRENCY) {
                    ShopManager.recordSale(shop.idef, player.uniqueId.toString(), amount)
                }
            }
        }

        session.clearSelection()
        session.currentPage = 0
        submit(delay = 1) { openShop(player, shop, session) }
    }

    private fun openSellPlacementMenu(player: Player, shop: ShopConfig) {
        val layout = shop.guiSell.split("\n")
        val iSlots = getAllSlotsFor('I', layout)
        val session = PlayerShopSession.get(player)

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:sellshop_notsel><shift:-176><aqua><font:minecraft:fusionpixel10>${shop.name}".toComponent())) {
            rows(6)
            map(*layout.toTypedArray())
            handLocked(false)

            // O = back to main shop
            val backStack = buildDisplayItem(player, DisplayItem("<white><b>返回商店", "点击返回商店"), "BARRIER")
            backStack.itemMeta?.let { m ->
                NamespacedKey.fromString("kangelicon:sellgui_activate")?.let { m.itemModel = it }
                backStack.setItemMeta(m)
            }
            set('O', backStack) {
                isCancelled = true
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                submit(delay = 1) { openShop(clicker, shop, PlayerShopSession.get(clicker)) }
            }

            // M slots = mode switch (same as main shop)
            run {
                val buyIsCurrent = false
                val sellIsCurrent = true

                val buyStack = buildDisplayItem(player, shop.guiItems.modeBuy, "BOOK")
                buyStack.itemMeta?.let { meta ->
                    val lore = mutableListOf<String>()
                    if (shop.guiItems.modeBuy.lore.isNotBlank()) {
                        shop.guiItems.modeBuy.lore.split("\n").forEach { lore.add("<!i><gray>$it") }
                    }
                    lore.add("")
                    lore.add("<!i><gray>点击切换")
                    meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
                    NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
                    buyStack.setItemMeta(meta)
                }
                for (slot in 0..2) {
                    set(slot, buyStack) {
                        isCancelled = true
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        session.isBuyMode = true
                        session.clearSelection()
                        session.currentPage = 0
                        clicker.closeInventory()
                        submit(delay = 1) { openShop(clicker, shop, session) }
                    }
                }

                val sellStack = buildDisplayItem(player, shop.guiItems.modeSell, "BOOK")
                sellStack.itemMeta?.let { meta ->
                    val lore = mutableListOf<String>()
                    if (shop.guiItems.modeSell.lore.isNotBlank()) {
                        shop.guiItems.modeSell.lore.split("\n").forEach { lore.add("<!i><gray>$it") }
                    }
                    lore.add("")
                    lore.add("<!i><green>▶ 当前模式")
                    meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
                    NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
                    sellStack.setItemMeta(meta)
                }
                for (slot in 3..5) {
                    set(slot, sellStack) {
                        isCancelled = true
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        clicker.closeInventory()
                        submit(delay = 1) { openShop(clicker, shop, session) }
                    }
                }
            }

            // C = confirm sell
            val sellConfirmStack = buildDisplayItem(player, DisplayItem("<green><b>确认出售", "出售所有已放入的物品\n无法出售的物品将返还背包"), "GREEN_CONCRETE")
            sellConfirmStack.itemMeta?.let { m ->
                NamespacedKey.fromString("kangelitem:void")?.let { m.itemModel = it }
                sellConfirmStack.setItemMeta(m)
            }
            set('C', sellConfirmStack) {
                isCancelled = true
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                processSellPlacement(clicker, shop, layout)
                submit(delay = 1) { refreshSellPlacementTitle(clicker, shop, layout) }
            }

            // N = info (currency display)
            set('N', buildKnowledgeItem(player, shop, PlayerShopSession.get(player))) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // Lock non-I slots and refresh title on any click that might change items
            onClick(lock = false) { event ->
                val rawSlot = event.rawSlot
                val rows = 6
                if (rawSlot < rows * 9) {
                    val row = rawSlot / 9
                    val col = rawSlot % 9
                    val charAtSlot = layout.getOrNull(row)?.getOrNull(col) ?: ' '
                    if (charAtSlot != 'I') {
                        event.isCancelled = true
                    }
                }
                // Refresh title after any click (including shift-clicks from player inventory)
                submit(delay = 1) {
                    val p = event.clicker ?: return@submit
                    if (!p.isOnline) return@submit
                    // Guard: only refresh if the sell placement is still open
                    val top = p.openInventory.topInventory
                    if (top.size != rows * 9) return@submit
                    refreshSellPlacementTitle(p, shop, layout)
                }
            }

            // Return unsold items on close
            onClose {
                it.returnItems(iSlots)
            }
        }
    }

    private fun processSellPlacement(player: Player, shop: ShopConfig, layout: List<String>) {
        val inv = player.openInventory.topInventory
        val iSlots = getAllSlotsFor('I', layout)
        var soldCount = 0
        val rewardSummary = mutableListOf<String>()

        for (slot in iSlots) {
            val item = inv.getItem(slot) ?: continue
            if (item.type.isAir) continue

            val entry = shop.items.values.find { matchesItemId(item, it.id) }

            if (entry != null && entry.sell.isNotEmpty()) {
                // Check sell requirements before processing
                if (entry.sellRequirements.any { !checkRequirement(player, it, shop) }) {
                    player.inventory.addItem(item).values.forEach { leftover ->
                        player.world.dropItem(player.location, leftover)
                    }
                    continue
                }
                for (reward in entry.sell) {
                    val amount = reward.amount * item.amount
                    when (reward.type) {
                        CostType.CURRENCY -> {
                            CurrencyManager.addBalance(player, reward.currency ?: "money", amount)
                            val name = CurrencyManager.displayName(reward.currency ?: "money")
                            rewardSummary.add("<yellow>$name <green>x$amount")
                            ShopManager.recordSale(shop.idef, player.uniqueId.toString(), amount)
                        }
                        CostType.ITEM -> {
                            giveItem(player, reward.item ?: "?", amount.toInt())
                            rewardSummary.add("<yellow>${formatItemName(reward.item ?: "?")} <green>x$amount")
                        }
                    }
                }
                soldCount++
            } else {
                player.inventory.addItem(item).values.forEach { leftover ->
                    player.world.dropItem(player.location, leftover)
                }
            }
            inv.setItem(slot, ItemStack(Material.AIR))
        }

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        if (soldCount > 0) {
            player.sendMessage("<green>出售成功! 获得: ${rewardSummary.joinToString(", ")}<white>".toComponent())
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } else {
            player.sendMessage("<red>没有可出售的物品".toComponent())
        }
    }

    private fun getSlotFor(char: Char, layout: List<String>): Int {
        for ((row, line) in layout.withIndex()) {
            val col = line.indexOf(char)
            if (col >= 0) return row * 9 + col
        }
        return -1
    }

    // ── Filter helpers ──────────────────────────────────────────────

    private fun canAffordTotalWithExtra(player: Player, shop: ShopConfig, session: PlayerShopSession, extraEntry: ShopItemEntry, extraQty: Long): Boolean {
        val totals = mutableMapOf<String, Long>() // currency -> total amount
        val itemTotals = mutableMapOf<String, Long>() // item id -> total amount
        for ((itemKey, qty) in session.selectedItems.entries.filter { it.value > 0 }) {
            val entry = shop.items[itemKey] ?: continue
            for (cost in entry.buy) {
                val unitPrice = if (cost.dynamicPricing != null) getDynamicPrice(cost, entry.key) else cost.amount
                when (cost.type) {
                    CostType.CURRENCY -> {
                        val key = cost.currency ?: "money"
                        totals[key] = (totals[key] ?: 0L) + unitPrice * qty
                    }
                    CostType.ITEM -> {
                        val id = cost.item ?: "?"
                        itemTotals[id] = (itemTotals[id] ?: 0L) + unitPrice * qty
                    }
                }
            }
        }
        // Add the extra item
        for (cost in extraEntry.buy) {
            val unitPrice = if (cost.dynamicPricing != null) getDynamicPrice(cost, extraEntry.key) else cost.amount
            when (cost.type) {
                CostType.CURRENCY -> {
                    val key = cost.currency ?: "money"
                    totals[key] = (totals[key] ?: 0L) + unitPrice * extraQty
                }
                CostType.ITEM -> {
                    val id = cost.item ?: "?"
                    itemTotals[id] = (itemTotals[id] ?: 0L) + unitPrice * extraQty
                }
            }
        }
        return totals.all { (currency, amount) -> CurrencyManager.hasBalance(player, currency, amount) } &&
            itemTotals.all { (id, amount) -> countItem(player, id) >= amount }
    }

    private fun <T : Enum<T>> cycleFilter(current: T, values: Array<T>): T {
        val idx = values.indexOf(current)
        return values[(idx + 1) % values.size]
    }

    private fun canAffordAny(player: Player, entry: ShopItemEntry): Boolean {
        if (entry.buy.isEmpty()) return true
        for (cost in entry.buy) {
            when (cost.type) {
                CostType.CURRENCY -> {
                    val amount = if (cost.dynamicPricing != null) getDynamicPrice(cost, entry.key) else cost.amount
                    if (!CurrencyManager.hasBalance(player, cost.currency ?: "money", amount)) return false
                }
                CostType.ITEM -> {
                    val count = countItem(player, cost.item ?: "?")
                    if (count < cost.amount) return false
                }
            }
        }
        return true
    }

    private fun getFilteredItems(player: Player, shop: ShopConfig, session: PlayerShopSession): List<ShopItemEntry> {
        val items = shop.items.values.toList()
        return items.filter { entry ->
            // 未标注购买价格/出售价格的商品直接不渲染(防止零元购/白送物品)
            if (session.isBuyMode && entry.buy.isEmpty()) return@filter false
            if (!session.isBuyMode && entry.sell.isEmpty()) return@filter false
            // 解锁过滤：有 unlock 但玩家未解锁的配方不显示
            val unlock = entry.unlock
            if (unlock != null) {
                val data = playerDataMap[player.uniqueId.toString()]
                if (data == null || unlock !in data.unlockedShopItems) return@filter false
            }
            // 强制过滤：商人等级不足的商品直接不显示
            val reqs = if (session.isBuyMode) entry.buyRequirements else entry.sellRequirements
            reqs.none { it.mode == "level" && !checkRequirement(player, it, shop) }
        }.filter { entry ->
            val reqs = if (session.isBuyMode) entry.buyRequirements else entry.sellRequirements
            when (session.filterReq) {
                FilterRequirement.ALL -> true
                FilterRequirement.MET -> reqs.isEmpty() || reqs.all { checkRequirement(player, it, shop) }
                FilterRequirement.NOT_MET -> reqs.any { !checkRequirement(player, it, shop) }
            }
        }.filter { entry ->
            when (session.filterInv) {
                FilterInventory.ALL -> true
                FilterInventory.HAVE -> countItem(player, entry.id) > 0
                FilterInventory.NOT_HAVE -> countItem(player, entry.id) <= 0
            }
        }.filter { entry ->
            if (!session.isBuyMode) return@filter true
            when (session.filterAfford) {
                FilterAffordability.ALL -> true
                FilterAffordability.CAN_BUY -> canAffordAny(player, entry)
                FilterAffordability.CANNOT_BUY -> !canAffordAny(player, entry)
            }
        }
    }

    private fun buildFilterItem(player: Player, currentName: String, allNames: List<String>, label: String, material: String, model: String? = null): ItemStack {
        val mat = try { XMaterial.valueOf(material) } catch (_: Exception) { XMaterial.HOPPER }
        val result = mat.parseItem() ?: ItemStack(Material.HOPPER)
        val meta = result.itemMeta ?: return result
        meta.displayName("<!i><white><b>$label".replacePlaceholder(player).toComponent())
        val lore = allNames.map { name ->
            if (name == currentName) "<!i><green>▶ <white>$name"
            else "<!i><gray>$name"
        }
        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        model?.let { NamespacedKey.fromString(it)?.let { key -> meta.itemModel = key } }
        result.setItemMeta(meta)
        return result
    }

    private fun filterReqModel(v: FilterRequirement): String = "kangelicon:filter_condition_" + when (v) {
        FilterRequirement.ALL -> "all"
        FilterRequirement.MET -> "met"
        FilterRequirement.NOT_MET -> "not_met"
    }
    private fun filterInvModel(v: FilterInventory): String = "kangelicon:filter_item_" + when (v) {
        FilterInventory.ALL -> "all"
        FilterInventory.HAVE -> "have"
        FilterInventory.NOT_HAVE -> "not_have"
    }
    private fun filterAffordModel(v: FilterAffordability): String = "kangelicon:filter_bought_" + when (v) {
        FilterAffordability.ALL -> "all"
        FilterAffordability.CAN_BUY -> "have"
        FilterAffordability.CANNOT_BUY -> "not_have"
    }

    private fun checkRequirement(player: Player, req: Requirement, shop: ShopConfig? = null): Boolean {
        return when (req.mode) {
            "papi" -> {
                val resolved = req.papi.replacePlaceholder(player)
                val expected = req.value.removePrefix(">=").removePrefix("<=").removePrefix("==").removePrefix(">").removePrefix("<").trim()
                val actual = resolved.toDoubleOrNull() ?: return false
                val expectedVal = expected.toDoubleOrNull() ?: return false
                when {
                    req.value.startsWith(">=") -> actual >= expectedVal
                    req.value.startsWith("<=") -> actual <= expectedVal
                    req.value.startsWith(">") -> actual > expectedVal
                    req.value.startsWith("<") -> actual < expectedVal
                    req.value.startsWith("==") -> actual == expectedVal
                    else -> actual == expectedVal
                }
            }
            "level" -> {
                val s = shop ?: return true
                val required = req.level.toIntOrNull() ?: return true
                getCurrentMerchantLevel(player, s) >= required
            }
            "unlock" -> {
                if (req.value.isBlank()) return true
                val data = playerDataMap[player.uniqueId.toString()]
                data != null && req.value in data.unlockedShopItems
            }
            "quest" -> {
                if (req.value.isBlank()) return true
                try {
                    val profile = ChemdahAPI.playerProfile[player.name] ?: return false
                    val quest = profile.getQuests().firstOrNull { it.id == req.value }
                    quest != null && quest.isCompleted
                } catch (_: Exception) {
                    false
                }
            }
            else -> true
        }
    }

    private fun getDynamicPrice(cost: BuyCost, itemKey: String): Long {
        val dp = cost.dynamicPricing ?: return cost.amount
        if (dp.stock > 0) {
            val sold = ShopManager.getGlobalPurchaseCount(itemKey)
            val ratio = (sold.toDouble() / dp.stock.toDouble()).coerceIn(0.0, 1.0)
            val range = dp.max - dp.min
            return (dp.max - range.toDouble() * ratio).toLong().coerceIn(dp.min, dp.max)
        }
        return cost.amount
    }

    private fun resolveItem(player: Player, itemId: String): ItemStack? {
        // Try ExternalItemHelper (ItemBridge) for plugin:name format
        if (itemId.contains(":")) {
            val parts = itemId.split(":", limit = 2)
            if (parts.size == 2) {
                try {
                    val result = ExternalItemHelper.build(player, parts[0], parts[1])
                    if (result != null && !result.type.isAir) return result
                } catch (_: Exception) { /* fall through */ }
            }
        }

        // Try as Bukkit Material
        try {
            val mat = Material.valueOf(itemId.uppercase())
            return ItemStack(mat)
        } catch (_: Exception) { /* fall through */ }

        // Try as XMaterial
        try {
            val xmat = XMaterial.valueOf(itemId.uppercase())
            return xmat.parseItem()
        } catch (_: Exception) { /* fall through */ }

        return null
    }

    private fun matchesItemId(item: ItemStack, itemId: String): Boolean {
        // Check via sertraline item ID
        try {
            val clazz = Class.forName("io.github.zzzyyylllty.sertraline.util.ItemStackUtil")
            val method = clazz.getMethod("getItemId", ItemStack::class.java)
            val id = method.invoke(null, item) as? String
            if (id == itemId) return true
        } catch (_: Exception) { /* fall through */ }

        // Check by material name
        return item.type.name.equals(itemId.uppercase(), ignoreCase = true)
    }

    private fun countItem(player: Player, itemId: String): Long {
        var count = 0L
        for (item in player.inventory.contents) {
            if (item == null || item.type.isAir) continue
            if (matchesItemId(item, itemId)) {
                count += item.amount
            }
        }
        return count
    }

    private fun takeItem(player: Player, itemId: String, amount: Long) {
        var remaining = amount
        for (i in player.inventory.contents.indices) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue
            if (!matchesItemId(item, itemId)) continue

            if (item.amount <= remaining) {
                remaining -= item.amount
                player.inventory.setItem(i, null)
            } else {
                item.amount = (item.amount - remaining).toInt()
                player.inventory.setItem(i, item)
                remaining = 0
            }
            if (remaining <= 0) break
        }
    }

    private fun giveItem(player: Player, itemId: String, amount: Int): Int {
        val template = resolveItem(player, itemId) ?: return 0
        val maxStack = template.maxStackSize
        var remaining = amount
        var given = 0

        while (remaining > 0) {
            val stackSize = minOf(remaining, maxStack)
            val give = template.clone()
            give.amount = stackSize
            val leftover = player.inventory.addItem(give)
            val added = stackSize - (leftover.values.firstOrNull()?.amount ?: 0)
            given += added
            remaining -= added
            if (leftover.isNotEmpty()) break
        }
        if (remaining > 0) {
            player.sendMessage("<red>背包已满! 有 $remaining 个 ${formatItemName(itemId)}<red> 掉落在了地上。<white>".toComponent())
            player.world.dropItem(player.location, template.clone().apply { this.amount = remaining })
        }
        return given
    }
}
