package io.github.zzzyyylllty.chotentech.globalmarket

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.returnItems
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.compat.replacePlaceholder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GlobalMarketMenu : Listener {

    private val awaitingPrice = ConcurrentHashMap<UUID, MarketListingSession>()

    init {
        try {
            org.bukkit.Bukkit.getPluginManager().registerEvents(this, taboolib.platform.BukkitPlugin.getInstance())
        } catch (_: Exception) {}
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val p = event.player
        GlobalMarketManager.removeListingSession(p)
        awaitingPrice.remove(p.uniqueId)
    }

    // ── Main Browse GUI ───────────────────────────────────────────

    fun openMain(player: Player) {
        val session = GlobalMarketManager.getListingSession(player)
        session.step = ListingStep.NONE

        val title = "<black><shift:-16><image:kangelicon:chest><shift:-176><white><font:minecraft:fusionpixel10>¹ 全球市场"

        player.openMenu<PageableChest<MarketListing>>(
            mmJsonUtil.serialize(title.toComponent())
        ) {
            rows(6)
            page(session.currentPage)
            onPageChange { session.currentPage = page }

            map(
                "MMMMMMMMM",
                "IIIIIIIII",
                "IIIIIIIII",
                "IIIIIIIII",
                "IIIIIIIII",
                "MCS---<>>"
            )

            slotsBy('I')

            elements { GlobalMarketManager.getActiveListings() }

            onGenerate { _, entry, _, _ ->
                buildListingItem(player, entry)
            }

            onClick { event, entry ->
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                submit(delay = 1) { openConfirmBuy(clicker, entry) }
            }

            set('M', buildButton(player, listOf(
                "<yellow><b>我的上架",
                "<!i><gray>点击查看你上架的商品"
            ), "CHEST")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                session.currentPage = page
                submit(delay = 1) { openMyListings(clicker) }
            }

            set('C', buildButton(player, listOf(
                "<green><b>上架物品",
                "<!i><gray>点击上架新物品",
                "<!i><gray>当前: ${GlobalMarketManager.getPlayerListingCount(player.uniqueId)}/${GlobalMarketManager.config.maxListingsPerPlayer}"
            ), "HOPPER")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (GlobalMarketManager.getPlayerListingCount(clicker.uniqueId) >= GlobalMarketManager.config.maxListingsPerPlayer) {
                    clicker.sendMessage("<red>你的上架数量已达上限!".toComponent())
                    return@set
                }
                clicker.closeInventory()
                session.currentPage = page
                submit(delay = 1) { openListItem(clicker) }
            }

            set('S', buildButton(player, listOf(
                "<gold><b>领取收益",
                "<!i><gray>点击领取出售所得"
            ), "GOLD_NUGGET")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                val result = GlobalMarketManager.claimProceeds(clicker)
                clicker.sendMessage(result.message.toComponent())
                if (result.success) {
                    clicker.playSound(clicker.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            }

            // < at slot 51 (row 5, col 6)
            setPreviousPage(51) { _, hasPrev ->
                buildButton(player, listOf(
                    if (hasPrev) "<white><b>上一页" else "<gray><b>已是第一页",
                    "<!i><gray>点击翻页"
                ), "ARROW")
            }

            // > at slot 52 (row 5, col 7)
            setNextPage(52) { _, hasNext ->
                buildButton(player, listOf(
                    if (hasNext) "<white><b>下一页" else "<gray><b>已是最后一页",
                    "<!i><gray>点击翻页"
                ), "ARROW")
            }
        }
    }

    // ── Confirm Buy GUI ───────────────────────────────────────────

    private fun openConfirmBuy(player: Player, listing: MarketListing) {
        player.openMenu<Chest>(
            mmJsonUtil.serialize("<black><shift:-16><image:kangelicon:chest><shift:-176><white><font:minecraft:fusionpixel10>¹ 确认购买".toComponent())
        ) {
            rows(3)

            map(
                "---------",
                "--A---B--",
                "----C----"
            )

            set('A', buildListingItem(player, listing))

            set('B', buildButton(player, listOf(
                "<gold><b>价格信息",
                "<!i><gray>单价: ${listing.price.toLong()} ${CurrencyManager.displayName(listing.currency)}",
                "<!i><gray>你的余额: ${CurrencyManager.getBalance(player, listing.currency)} ${CurrencyManager.displayName(listing.currency)}"
            ), "GOLD_NUGGET"))

            set('C', buildButton(player, listOf(
                "<green><b>确认购买",
                "<!i><gray>花费 ${listing.price.toLong()} ${CurrencyManager.displayName(listing.currency)}",
                "<!i><gray>购买此物品",
                "<!i><dark_red>确认后将无法退款"
            ), "GREEN_CONCRETE")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                val result = GlobalMarketManager.executePurchase(clicker, listing.id)
                clicker.sendMessage(result.message.toComponent())
                if (result.success) {
                    clicker.playSound(clicker.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            }
        }
    }

    // ── My Listings GUI ──────────────────────────────────────────

    private fun openMyListings(player: Player) {
        player.openMenu<PageableChest<MarketListing>>(
            mmJsonUtil.serialize("<black><shift:-16><image:kangelicon:chest><shift:-176><white><font:minecraft:fusionpixel10>¹ 我的上架 - 右键下架".toComponent())
        ) {
            rows(4)
            page(0)

            map(
                "IIIIIIIII",
                "IIIIIIIII",
                "IIIIIIIII",
                "----<->--"
            )

            slotsBy('I')

            elements {
                GlobalMarketManager.getListingsBySeller(player.uniqueId)
                    .filter { !it.sold && !it.cancelled }
            }

            onGenerate { _, entry, _, _ ->
                buildListingItem(player, entry)
            }

            onClick { event, entry ->
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (event.clickEvent().click == ClickType.RIGHT) {
                    GlobalMarketManager.cancelListing(entry.id, clicker)
                    resetElementsCache()
                } else {
                    clicker.sendMessage("<gray>右键点击可下架该商品".toComponent())
                }
            }

            // < at slot 30 (row 3, col 3)
            setPreviousPage(30) { _, hasPrev ->
                buildButton(player, listOf(
                    if (hasPrev) "<white><b>上一页" else "<gray><b>已是第一页"
                ), "ARROW")
            }

            // > at slot 31 (row 3, col 4)
            setNextPage(31) { _, hasNext ->
                buildButton(player, listOf(
                    if (hasNext) "<white><b>下一页" else "<gray><b>已是最后一页"
                ), "ARROW")
            }
        }
    }

    // ── List Item GUI ────────────────────────────────────────────

    private fun openListItem(player: Player) {
        val session = GlobalMarketManager.getListingSession(player)
        session.reset()
        session.step = ListingStep.WAITING_ITEM

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<black><shift:-16><image:kangelicon:chest><shift:-176><white><font:minecraft:fusionpixel10>¹ 放入要上架的物品".toComponent())
        ) {
            rows(2)
            handLocked(false)

            map(
                "--A----B-",
                "---------"
            )

            onClick(lock = false) { event ->
                val rawSlot = event.rawSlot
                if (rawSlot < 18 && rawSlot == 2) {
                    val item = event.currentItem
                    if (item != null && !item.type.isAir) {
                        session.listedItem = item.clone()
                        session.step = ListingStep.WAITING_ITEM
                    }
                }
            }

            set('B', buildButton(player, listOf(
                "<green><b>确认物品",
                "<!i><gray>将物品放入左侧格子后点击此处",
                "<!i><yellow>物品将被立即收取!"
            ), "GREEN_CONCRETE")) {
                val inv = clicker.openInventory.topInventory
                val item = inv.getItem(2)

                if (item == null || item.type.isAir) {
                    clicker.sendMessage("<red>请先将要上架的物品放入格子中".toComponent())
                    return@set
                }

                // ANTI-DUPE: remove from slot immediately before anything else
                inv.setItem(2, ItemStack(Material.AIR))
                val serialized = GlobalMarketManager.serializeItem(item)
                if (serialized == null) {
                    clicker.inventory.addItem(item).values.forEach { leftover ->
                        clicker.world.dropItem(clicker.location, leftover)
                    }
                    clicker.sendMessage("<red>物品序列化失败，已返还".toComponent())
                    return@set
                }

                session.listedItem = item.clone()
                session.listedItemData = serialized
                session.step = ListingStep.WAITING_PRICE

                clicker.closeInventory()
                submit(delay = 1) { openPriceSelector(clicker) }
            }

            onClose {
                if (session.step != ListingStep.CONFIRMING) {
                    val held = session.listedItem
                    if (held != null) {
                        it.player.inventory.addItem(held).values.forEach { leftover ->
                            it.player.world.dropItem(it.player.location, leftover)
                        }
                        session.reset()
                    }
                }
            }
        }
    }

    // ── Price Selector GUI ───────────────────────────────────────

    private fun openPriceSelector(player: Player) {
        val session = GlobalMarketManager.getListingSession(player)
        if (session.step != ListingStep.WAITING_PRICE) return

        player.openMenu<Chest>(
            mmJsonUtil.serialize("<black><shift:-16><image:kangelicon:chest><shift:-176><white><font:minecraft:fusionpixel10>¹ 设置价格".toComponent())
        ) {
            rows(4)

            map(
                "---------",
                "-ABCDEF-",
                "---GHI---",
                "----J----"
            )

            val presets = listOf(10.0, 50.0, 100.0, 500.0, 1000.0, 5000.0, 10000.0, 50000.0, 100000.0)
            val presetIntSlots = listOf(10, 11, 12, 13, 14, 15, 21, 22, 23)

            for ((idx, slot) in presetIntSlots.withIndex()) {
                if (idx >= presets.size) break
                val price = presets[idx]
                set(slot, buildButton(player, listOf(
                    "<gold><b>${price.toLong()}",
                    "<!i><gray>点击设置价格为 ${price.toLong()} ${CurrencyManager.displayName(session.currency)}"
                ), "GOLD_NUGGET")) {
                    clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    session.price = price
                    updatePriceDisplay(clicker)
                }
            }

            set(16, buildButton(player, listOf(
                "<aqua><b>自定义价格",
                "<!i><gray>点击后在聊天栏输入价格",
                "<!i><gray>当前: ${session.price.toLong()} ${CurrencyManager.displayName(session.currency)}"
            ), "WRITABLE_BOOK")) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                clicker.closeInventory()
                clicker.sendMessage("<aqua>请在聊天栏输入你想要的单价（整数），输入 <red>cancel <aqua>取消".toComponent())
                awaitingPrice[clicker.uniqueId] = session
            }

            val confirmStack = buildButton(player, listOf(
                "<green><b>确认上架",
                "<!i><gray>物品: ${session.listedItem?.type?.name ?: "未知"}",
                "<!i><gray>数量: ${session.listedItem?.amount ?: 0}",
                "<!i><gray>价格: ${session.price.toLong()} ${CurrencyManager.displayName(session.currency)}",
                "<!i><dark_red>确认后将收取手续费并上架"
            ), "GREEN_CONCRETE")
            confirmStack.itemMeta?.let { m ->
                NamespacedKey.fromString("kangelitem:void")?.let { m.itemModel = it }
                confirmStack.setItemMeta(m)
            }
            set(31, confirmStack) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                if (session.price <= 0) {
                    clicker.sendMessage("<red>价格必须大于0".toComponent())
                    return@set
                }
                clicker.closeInventory()
                completeListing(clicker, session)
            }
        }
    }

    private fun updatePriceDisplay(player: Player) {
        val session = GlobalMarketManager.getListingSession(player)
        val inv = player.openInventory.topInventory
        if (inv.size != 36) return
        val confirmStack = buildButton(player, listOf(
            "<green><b>确认上架",
            "<!i><gray>物品: ${session.listedItem?.type?.name ?: "未知"}",
            "<!i><gray>数量: ${session.listedItem?.amount ?: 0}",
            "<!i><gray>价格: ${session.price.toLong()} ${CurrencyManager.displayName(session.currency)}",
            "<!i><dark_red>确认后将收取手续费并上架"
        ), "GREEN_CONCRETE")
        confirmStack.itemMeta?.let { m ->
            NamespacedKey.fromString("kangelitem:void")?.let { m.itemModel = it }
            confirmStack.setItemMeta(m)
        }
        inv.setItem(31, confirmStack)
    }

    private fun completeListing(player: Player, session: MarketListingSession) {
        val item = session.listedItem ?: run {
            player.sendMessage("<red>物品数据丢失".toComponent())
            session.reset()
            return
        }
        val itemData = session.listedItemData ?: run {
            player.inventory.addItem(item).values.forEach { leftover ->
                player.world.dropItem(player.location, leftover)
            }
            player.sendMessage("<red>物品数据丢失，已返还".toComponent())
            session.reset()
            return
        }

        val now = System.currentTimeMillis()
        val expireDays = GlobalMarketManager.config.listingExpiryDays
        val listing = MarketListing(
            id = UUID.randomUUID().toString(),
            sellerUuid = player.uniqueId.toString(),
            sellerName = player.name,
            itemData = itemData,
            itemType = item.type.name,
            itemAmount = item.amount,
            price = session.price,
            currency = session.currency,
            listedAt = now,
            expireAt = if (expireDays > 0) now + expireDays * 86400000L else -1L
        )

        val fee = session.price * (GlobalMarketManager.config.listingFee / 100.0)
        if (fee > 0) {
            CurrencyManager.removeBalance(player, session.currency, fee.toLong())
        }

        session.step = ListingStep.CONFIRMING
        if (GlobalMarketManager.addListing(listing)) {
            player.sendMessage("<green>[全球市场] 上架成功! ".toComponent())
            player.sendMessage("<green>价格: ${session.price.toLong()} ${CurrencyManager.displayName(session.currency)}${if (fee > 0) " (手续费: $fee)" else ""}".toComponent())
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } else {
            player.inventory.addItem(item).values.forEach { leftover ->
                player.world.dropItem(player.location, leftover)
            }
            player.sendMessage("<red>[全球市场] 上架失败，物品已返还".toComponent())
            if (fee > 0) {
                CurrencyManager.addBalance(player, session.currency, fee.toLong())
            }
        }
        session.reset()
    }

    // ── Chat listener for custom price ────────────────────────────

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val session = awaitingPrice[player.uniqueId] ?: return
        event.isCancelled = true

        val msg = event.message.trim()
        if (msg.equals("cancel", ignoreCase = true)) {
            submit(delay = 0) {
                player.sendMessage("<red>已取消自定义价格".toComponent())
                awaitingPrice.remove(player.uniqueId)
                val s = GlobalMarketManager.getListingSession(player)
                val held = s.listedItem
                if (held != null) {
                    player.inventory.addItem(held).values.forEach { leftover ->
                        player.world.dropItem(player.location, leftover)
                    }
                }
                s.reset()
            }
            return
        }

        val price = msg.toDoubleOrNull()
        if (price == null || price <= 0) {
            player.sendMessage("<red>请输入有效的正整数价格，或输入 cancel 取消".toComponent())
            return
        }

        submit(delay = 0) {
            session.price = price
            awaitingPrice.remove(player.uniqueId)
            player.sendMessage("<green>已设置价格为 $price ${CurrencyManager.displayName(session.currency)}".toComponent())
            openPriceSelector(player)
        }
    }

    // ── Item Builders ─────────────────────────────────────────────

    private fun buildListingItem(player: Player, listing: MarketListing): ItemStack {
        val item = ItemSerializer.deserializeNbt(listing.itemData) ?: ItemStack(Material.BARRIER)
        item.amount = listing.itemAmount
        val meta = item.itemMeta ?: return item

        val lore = mutableListOf(
            "<!i><dark_gray><st>${" ".repeat(60)}</st>",
            "<!i><yellow>上架者: <white>${listing.sellerName}",
            "<!i><yellow>价格: <white>${listing.price.toLong()} ${CurrencyManager.displayName(listing.currency)}",
            "<!i><yellow>数量: <white>${listing.itemAmount}"
        )
        if (listing.expireAt > 0) {
            val remaining = (listing.expireAt - System.currentTimeMillis()) / 86400000L
            lore.add("<!i><gray>剩余 ${remaining} 天")
        }
        lore.add("<!i><dark_gray><st>${" ".repeat(60)}</st>")
        lore.add("<!i><green>点击购买")

        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        item.setItemMeta(meta)
        return item
    }

    private fun buildButton(player: Player, lines: List<String>, defaultMaterial: String): ItemStack {
        val mat = try {
            XMaterial.valueOf(defaultMaterial.uppercase())
        } catch (_: Exception) {
            XMaterial.STONE
        }
        val result = mat.parseItem() ?: ItemStack(Material.STONE)
        val meta = result.itemMeta ?: return result

        meta.displayName((lines.first() + "<white>").replacePlaceholder(player).toComponent())
        if (lines.size > 1) {
            val loreLines = lines.drop(1).map { it.replacePlaceholder(player) }
            meta.lore(loreLines.toComponent())
        }
        result.setItemMeta(meta)
        return result
    }
}
