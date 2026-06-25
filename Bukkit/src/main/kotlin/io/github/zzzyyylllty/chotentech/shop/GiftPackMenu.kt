package io.github.zzzyyylllty.chotentech.shop

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.util.ExternalItemHelper
import io.github.zzzyyylllty.chotentech.util.guiItem
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.compat.replacePlaceholder

class GiftPackMenu {

    companion object {
        // 3 rows × 7 slots = 21 item slots (row 1-3, col 1-7)
        private val ITEM_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        )
        // Row 5 columns 1-3
        private val CATEGORY_SLOTS = listOf(46, 47, 48)
        // Row 5 column 5
        private const val CONFIRM_SLOT = 50
        // Row 5 column 7
        private const val INFO_SLOT = 52

        private val CATEGORY_MATERIALS = mapOf(
            "armor" to "DIAMOND_CHESTPLATE",
            "weapon" to "DIAMOND_AXE",
            "medical" to "REDSTONE"
        )

        private val UI_CLICK = net.kyori.adventure.sound.Sound.sound(
            org.bukkit.Sound.UI_BUTTON_CLICK.key(),
            net.kyori.adventure.sound.Sound.Source.MASTER,
            1f, 1f
        )
        private val LEVELUP = net.kyori.adventure.sound.Sound.sound(
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key(),
            net.kyori.adventure.sound.Sound.Source.MASTER,
            1f, 1f
        )
    }

    fun open(player: Player, giftPackKey: String) {
        val pack = GiftPackManager.getPack(giftPackKey) ?: run {
            player.sendMessage("<red>礼包 <yellow>$giftPackKey<white> <red>不存在".toComponent())
            return
        }

        val categoryKeys = pack.categories.keys.toList()
        if (categoryKeys.isEmpty()) return

        // State
        var currentCategoryIdx = 0
        val selectedIds = mutableMapOf<String, MutableSet<String>>()

        // Pre-select single-item categories
        for ((catKey, cat) in pack.categories) {
            selectedIds[catKey] = if (cat.items.size == 1 && cat.maxSelect >= 1) {
                mutableSetOf(cat.items.first().id)
            } else {
                mutableSetOf()
            }
        }

        val titleStr = "<white><shift:-16><image:kangelicon:buyshop><shift:-176><aqua><font:minecraft:fusionpixel10>${pack.name}"

        player.openMenu<Chest>(mmJsonUtil.serialize(titleStr.toComponent())) {
            rows(6)

            map(
                "AAA   <>",
                "         ",
                "         ",
                "         ",
                "         ",
                "         "
            )

            // Close buttons (row 0, col 0-2)
            val closeItem = guiItem(XMaterial.BARRIER, "<red>关闭", listOf("<gray>点击关闭"), model = "kangelitem:void")
            for (slot in 0..2) set(slot, closeItem)

            // Previous page (placeholder)
            set(6, guiItem(XMaterial.ARROW, "<green>上一页", model = "kangelitem:previous_no"))
            // Next page (placeholder)
            set(7, guiItem(XMaterial.ARROW, "<green>下一页", model = "kangelitem:next_no"))

            // ── Item slot refresh ──
            fun refreshItemSlots() {
                for (slot in ITEM_SLOTS) set(slot, ItemStack(Material.AIR))

                val catKey = categoryKeys[currentCategoryIdx]
                val cat = pack.categories[catKey] ?: return
                val currentSelected = selectedIds[catKey] ?: emptySet()

                for ((i, entry) in cat.items.withIndex()) {
                    if (i >= ITEM_SLOTS.size) break
                    val slot = ITEM_SLOTS[i]
                    val isSelected = entry.id in currentSelected
                    set(slot, buildItemStack(player, entry, isSelected))
                }
            }

            // ── Confirm button refresh ──
            fun refreshConfirm() {
                var allSelected = true
                var totalPrice = pack.price

                for ((catKey, cat) in pack.categories) {
                    val count = selectedIds[catKey]?.size ?: 0
                    if (count < cat.maxSelect) allSelected = false
                    for (itemId in selectedIds[catKey] ?: emptySet()) {
                        totalPrice += pack.priceModifiers[itemId] ?: 0
                    }
                }

                val currencyName = CurrencyManager.displayName(pack.currency)
                val hasBalance = CurrencyManager.hasBalance(player, pack.currency, totalPrice)

                val matName = if (allSelected && hasBalance) "GREEN_CONCRETE" else "GRAY_CONCRETE"
                val mat = tryParseMaterial(matName) ?: XMaterial.GRAY_CONCRETE
                val item = mat.parseItem() ?: ItemStack(Material.GRAY_CONCRETE)
                val meta = item.itemMeta ?: return

                meta.displayName(
                    (if (allSelected && hasBalance) "<green><b>确认购买" else "<gray><b>选择未完成")
                        .replacePlaceholder(player).toComponent()
                )

                val confirmLore = mutableListOf<String>()
                confirmLore.add("<!i><gold>总价: <white>$totalPrice $currencyName")
                if (!hasBalance) {
                    confirmLore.add("<!i><red>货币不足!")
                } else if (allSelected) {
                    confirmLore.add("<!i><green>点击确认购买")
                } else {
                    confirmLore.add("<!i><red>请完成所有分类的选择")
                }
                confirmLore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
                confirmLore.add("<!i><gray>已选:")
                for ((catKey, cat) in pack.categories) {
                    val names = (selectedIds[catKey] ?: emptySet()).map { id ->
                        cat.items.find { it.id == id }?.displayName ?: id.substringAfterLast(":")
                    }.ifEmpty { listOf("<red>未选择") }
                    confirmLore.add("<!i> <white>${cat.displayName}: <gray>${names.joinToString(", ")}")
                }
                if (totalPrice > pack.price) {
                    confirmLore.add("")
                    confirmLore.add("<!i><red>含特殊加价: ${totalPrice - pack.price} $currencyName")
                }

                meta.lore(confirmLore.map { it.replacePlaceholder(player).toComponent() })
                NamespacedKey.fromString("kangelitem:void")?.let { meta.itemModel = it }
                item.setItemMeta(meta)
                set(CONFIRM_SLOT, item)
            }

            // ── Info button refresh ──
            fun refreshInfo() {
                val currencyName = CurrencyManager.displayName(pack.currency)
                val balance = CurrencyManager.getBalance(player, pack.currency)

                var totalPrice = pack.price
                for ((catKey, _) in pack.categories) {
                    for (itemId in selectedIds[catKey] ?: emptySet()) {
                        totalPrice += pack.priceModifiers[itemId] ?: 0
                    }
                }

                val mat = XMaterial.KNOWLEDGE_BOOK
                val item = mat.parseItem() ?: ItemStack(Material.KNOWLEDGE_BOOK)
                val meta = item.itemMeta ?: return
                NamespacedKey.fromString("kangelicon:book")?.let { meta.itemModel = it }
                meta.displayName("<gold><b>礼包信息".replacePlaceholder(player).toComponent())

                val infoLore = mutableListOf<String>()
                infoLore.add("<!i><gray>礼包商品详情")
                infoLore.add("")
                infoLore.add("<!i><gold>礼包: <white>${pack.name}")
                infoLore.add("<!i><gold>基础价格: <white>${pack.price} $currencyName")
                for ((itemId, modifier) in pack.priceModifiers) {
                    val shortName = itemId.substringAfterLast(":")
                    infoLore.add("<!i>  <red>※ ${shortName} +$modifier $currencyName")
                }
                infoLore.add("<!i><gold>当前总价: <white>$totalPrice $currencyName")
                infoLore.add("")
                infoLore.add("<!i><gold>你的余额: <white>$balance $currencyName")

                meta.lore(infoLore.map { it.replacePlaceholder(player).toComponent() })
                item.setItemMeta(meta)
                set(INFO_SLOT, item)
            }

            // ── Full rebuild ──
            fun refreshAll() {
                // Category buttons
                for ((i, catKey) in categoryKeys.withIndex()) {
                    if (i >= CATEGORY_SLOTS.size) break
                    val slot = CATEGORY_SLOTS[i]
                    val cat = pack.categories[catKey] ?: continue
                    val isCurrent = i == currentCategoryIdx
                    val selectedCount = selectedIds[catKey]?.size ?: 0

                    val matName = CATEGORY_MATERIALS[catKey] ?: "BOOK"
                    val mat = tryParseMaterial(matName) ?: XMaterial.BOOK
                    val item = mat.parseItem() ?: ItemStack(Material.BOOK)
                    val meta = item.itemMeta ?: continue

                    meta.displayName(
                        (if (isCurrent) "<green><b>▶ ${cat.displayName}" else "<white>${cat.displayName}")
                            .replacePlaceholder(player).toComponent()
                    )
                    val catLore = mutableListOf(
                        "<!i><gray>点击切换品类",
                        "<!i><white>可选 ${cat.items.size} 种",
                        "<!i><gold>最多选择: ${cat.maxSelect}",
                        "<!i><green>已选: $selectedCount/${cat.maxSelect}"
                    )
                    if (isCurrent) {
                        catLore.add("")
                        catLore.add("<!i><green>▶ 当前品类")
                        meta.setEnchantmentGlintOverride(true)
                    } else {
                        meta.setEnchantmentGlintOverride(false)
                    }
                    meta.lore(catLore.map { it.replacePlaceholder(player).toComponent() })
                    item.setItemMeta(meta)
                    set(slot, item)
                }

                // Clear remaining category slots
                for (i in categoryKeys.size until CATEGORY_SLOTS.size) {
                    set(CATEGORY_SLOTS[i], ItemStack(Material.AIR))
                }

                refreshItemSlots()
                refreshConfirm()
                refreshInfo()
            }

            // Initial render
            refreshAll()

            // ── Global click handler ──
            onClick { event ->
                val clicker = event.view.player as Player
                val slot = event.rawSlot
                if (slot !in 0..53) return@onClick

                // Close buttons
                if (slot in 0..2) {
                    clicker.closeInventory()
                    return@onClick
                }

                // Item slot click
                val itemIdx = ITEM_SLOTS.indexOf(slot)
                if (itemIdx >= 0) {
                    val catKey = categoryKeys[currentCategoryIdx]
                    val cat = pack.categories[catKey] ?: return@onClick
                    val items = cat.items
                    if (itemIdx >= items.size) return@onClick

                    val clicked = items[itemIdx]
                    val currentSelected = selectedIds.getOrPut(catKey) { mutableSetOf() }

                    if (clicked.id in currentSelected) {
                        currentSelected.remove(clicked.id)
                    } else if (currentSelected.size < cat.maxSelect) {
                        currentSelected.add(clicked.id)
                    } else {
                        clicker.sendMessage("<red>该分类最多选择 ${cat.maxSelect} 个物品!".toComponent())
                        return@onClick
                    }

                    clicker.playSound(UI_CLICK)
                    refreshAll()
                    return@onClick
                }

                // Category switch click
                val catIdx = CATEGORY_SLOTS.indexOf(slot)
                if (catIdx >= 0 && catIdx < categoryKeys.size) {
                    if (catIdx == currentCategoryIdx) return@onClick
                    currentCategoryIdx = catIdx
                    clicker.playSound(UI_CLICK)
                    refreshAll()
                    return@onClick
                }

                // Confirm button
                if (slot == CONFIRM_SLOT) {
                    clicker.playSound(UI_CLICK)
                    processPurchase(clicker, pack, selectedIds)
                    return@onClick
                }

                // Info button — no action, just sound
                if (slot == INFO_SLOT) {
                    clicker.playSound(UI_CLICK)
                    return@onClick
                }
            }
        }
    }

    // ── Item building ──

    private fun buildItemStack(player: Player, entry: GiftPackSelectableItem, isSelected: Boolean): ItemStack {
        var display = resolveItem(player, entry.id)
        if (display == null || display.type.isAir) {
            display = ItemStack(Material.PAPER)
        }
        val meta = display.itemMeta ?: return display

        meta.displayName(entry.displayName.replacePlaceholder(player).toComponent())

        val lore = mutableListOf<String>()
        lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
        if (isSelected) {
            lore.add("<!i><green>✓ 已选择")
            lore.add("<!i><gray>点击取消选择")
            meta.setEnchantmentGlintOverride(true)
        } else {
            lore.add("<!i><gray>点击选择")
            meta.setEnchantmentGlintOverride(false)
        }
        lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        display.setItemMeta(meta)
        return display
    }

    // ── Purchase logic ──

    private fun processPurchase(player: Player, pack: GiftPackConfig, selectedIds: Map<String, Set<String>>) {
        // Validate all categories are filled
        for ((catKey, cat) in pack.categories) {
            val count = selectedIds[catKey]?.size ?: 0
            if (count < cat.maxSelect) {
                player.sendMessage("<red>请完成所有分类的选择! (${cat.displayName} 需要选 ${cat.maxSelect} 个)".toComponent())
                return
            }
        }

        // Calculate total price
        var totalPrice = pack.price
        val allItems = mutableListOf<String>()
        for ((catKey, _) in pack.categories) {
            for (itemId in selectedIds[catKey] ?: emptySet()) {
                allItems.add(itemId)
                totalPrice += pack.priceModifiers[itemId] ?: 0
            }
        }

        // Check balance
        val currencyName = CurrencyManager.displayName(pack.currency)
        if (!CurrencyManager.hasBalance(player, pack.currency, totalPrice)) {
            player.sendMessage("<red>$currencyName 不足! 需要 $totalPrice".toComponent())
            return
        }

        // Take payment
        CurrencyManager.removeBalance(player, pack.currency, totalPrice)

        // Give items
        for (itemId in allItems) {
            val given = giveItem(player, itemId, 1)
            if (given > 0) {
                val dn = findDisplayName(pack, itemId)
                player.sendMessage("<green>获得 <yellow>$dn".toComponent())
            }
        }

        player.playSound(LEVELUP)
        player.sendMessage("<green>礼包购买成功! 花费: $totalPrice $currencyName".toComponent())

        submit(delay = 1) { player.closeInventory() }
    }

    private fun findDisplayName(pack: GiftPackConfig, itemId: String): String {
        for ((_, cat) in pack.categories) {
            for (item in cat.items) {
                if (item.id == itemId) return item.displayName
            }
        }
        return itemId.substringAfterLast(":")
    }

    // ── Item helpers (mirrors ShopMenu logic) ──

    private fun resolveItem(player: Player, itemId: String): ItemStack? {
        if (itemId.contains(":")) {
            val parts = itemId.split(":", limit = 2)
            if (parts.size == 2) {
                try {
                    val result = ExternalItemHelper.build(player, parts[0], parts[1])
                    if (result != null && !result.type.isAir) return result
                } catch (_: Exception) { /* fall through */ }
            }
        }
        try {
            val mat = Material.valueOf(itemId.uppercase())
            return ItemStack(mat)
        } catch (_: Exception) { /* fall through */ }
        try {
            val xmat = XMaterial.valueOf(itemId.uppercase())
            return xmat.parseItem()
        } catch (_: Exception) { /* fall through */ }
        return null
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
            player.world.dropItem(player.location, template.clone().apply { this.amount = remaining })
        }
        return given
    }

    private fun tryParseMaterial(name: String): XMaterial? {
        return try {
            XMaterial.valueOf(name.uppercase())
        } catch (_: Exception) {
            null
        }
    }
}
