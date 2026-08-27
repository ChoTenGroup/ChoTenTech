package io.github.zzzyyylllty.chotentech.globalmarket

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object GlobalMarketManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val listingsFile: File by lazy { File(getDataFolder(), "globalmarket_data.json") }

    val config: MarketConfig = MarketConfig()

    // Thread-safe listing storage
    private val allListings = CopyOnWriteArrayList<MarketListing>()
    private var expiryTask: PlatformExecutor.PlatformTask? = null

    // Player pending balances (unclaimed proceeds from sales): uuidString -> (currency -> amount)
    private val pendingBalances = ConcurrentHashMap<String, MutableMap<String, Double>>()

    // Player listing sessions for the "list item" flow
    val listingSessions = ConcurrentHashMap<UUID, MarketListingSession>()

    // In-memory lock for purchase transactions per listing ID
    private val transactionLocks = ConcurrentHashMap<String, Any>()

    private var loaded = false

    fun loadAll() {
        if (loaded) return
        if (listingsFile.exists()) {
            try {
                val text = listingsFile.readText()
                if (text.isNotBlank()) {
                    val type = object : TypeToken<ListingStorage>() {}.type
                    val storage: ListingStorage = gson.fromJson(text, type)

                    allListings.addAll(storage.listings)
                    pendingBalances.putAll(storage.pendingBalances.mapValues { it.value.toMutableMap() })

                    // Clean up expired listings on load
                    val now = System.currentTimeMillis()
                    val activeBefore = allListings.size
                    allListings.removeAll { it.expireAt > 0 && it.expireAt <= now && !it.sold && !it.cancelled }
                    val removed = activeBefore - allListings.size
                    if (removed > 0) {
                        saveToDisk()
                        info("§e[GlobalMarket] Cleaned up $removed expired listings on load")
                    }

                    info("§a[GlobalMarket] Loaded ${allListings.size} listings")
                    info("§a[GlobalMarket] Pending balances for ${pendingBalances.size} players")
                }
            } catch (e: Exception) {
                info("§c[GlobalMarket] Failed to load data: ${e.message}")
                e.printStackTrace()
            }
        }

        startExpiryTask()
        loaded = true
    }

    fun saveToDisk() {
        try {
            val storage = ListingStorage(
                listings = ArrayList(allListings),
                pendingBalances = pendingBalances.mapValues { it.value.toMap() }
            )
            listingsFile.writeText(gson.toJson(storage))
        } catch (e: Exception) {
            info("§c[GlobalMarket] Failed to save data: ${e.message}")
            e.printStackTrace()
        }
    }

    // ── Listing CRUD ──────────────────────────────────────────────

    fun getActiveListings(): List<MarketListing> = allListings.filter { !it.sold && !it.cancelled }

    fun getListingsBySeller(uuid: UUID): List<MarketListing> =
        allListings.filter { it.sellerUuid == uuid.toString() }

    fun getListing(id: String): MarketListing? = allListings.find { it.id == id }

    /**
     * Add a listing. The item has already been removed from the player's inventory
     * by the caller (MarketMenu). This simply persists the listing.
     */
    fun addListing(listing: MarketListing): Boolean {
        allListings.add(listing)
        saveToDisk()
        return true
    }

    /**
     * Cancel a listing and return the item to the seller (if online).
     * Returns the deserialized item stack for offline handling.
     */
    fun cancelListing(listingId: String, seller: Player?): ItemStack? {
        val listing = allListings.find { it.id == listingId && !it.sold && !it.cancelled } ?: return null
        listing.cancelled = true
        saveToDisk()

        val item = deserializeItem(listing)
        if (seller != null && item != null) {
            seller.inventory.addItem(item).values.forEach { leftover ->
                seller.world.dropItem(seller.location, leftover)
            }
            seller.sendMessage("<#8FD4A6>[全球市场] 已取消上架 <#F2F5FA>${listing.itemType}<#8FD4A6>，物品已返还".toComponent())
        }
        return item
    }

    // ── Transactions ──────────────────────────────────────────────

    /**
     * Execute a purchase. Anti-dupe guarantees:
     * 1. Listing is validated and marked sold on disk FIRST
     * 2. Then currency is taken from buyer
     * 3. Seller is credited
     * 4. Item is given to buyer
     * If server crashes after step 1, listing is sold and no dupe occurs
     * (admin may need to verify edge cases).
     */
    fun executePurchase(buyer: Player, listingId: String): PurchaseResult {
        val lock = transactionLocks.computeIfAbsent(listingId) { Any() }
        synchronized(lock) {
            try {
                val listing = allListings.find { it.id == listingId } ?: return PurchaseResult(false, "<#E08A98>该商品不存在")
                if (listing.sold) return PurchaseResult(false, "<#E08A98>该商品已售出")
                if (listing.cancelled) return PurchaseResult(false, "<#E08A98>该商品已下架")
                if (listing.sellerUuid == buyer.uniqueId.toString()) return PurchaseResult(false, "<#E08A98>不能购买自己的商品")
                if (listing.expireAt > 0 && listing.expireAt <= System.currentTimeMillis()) {
                    listing.cancelled = true
                    saveToDisk()
                    return PurchaseResult(false, "<#E08A98>该商品已过期")
                }

                // Check affordability
                if (!CurrencyManager.hasBalance(buyer, listing.currency, listing.price.toLong())) {
                    return PurchaseResult(false, "<#E08A98>${CurrencyManager.displayName(listing.currency)}不足")
                }

                // STEP 1: Mark listing as sold on disk first (crash-safe: prevents dupe)
                listing.sold = true
                saveToDisk()

                // STEP 2: Take currency from buyer
                val tax = listing.price * (config.salesTax / 100.0)
                val sellerPayout = listing.price - tax

                CurrencyManager.removeBalance(buyer, listing.currency, listing.price.toLong())

                // STEP 3: Credit seller's pending balance
                pendingBalances
                    .computeIfAbsent(listing.sellerUuid) { mutableMapOf() }
                    .merge(listing.currency, sellerPayout, Double::plus)
                saveToDisk()

                // STEP 4: Give item to buyer
                val item = deserializeItem(listing)
                if (item != null) {
                    item.amount = listing.itemAmount
                    buyer.inventory.addItem(item).values.forEach { leftover ->
                        buyer.world.dropItem(buyer.location, leftover)
                        buyer.sendMessage("<#EFDF8F>[全球市场] 背包已满，部分物品掉落在地".toComponent())
                    }
                }

                buyer.sendMessage("<#8FD4A6>[全球市场] 购买成功！花费 <#F2F5FA>${listing.price} ${CurrencyManager.displayName(listing.currency)}".toComponent())
                return PurchaseResult(true, "<#8FD4A6>购买成功")
            } catch (e: Exception) {
                info("§c[GlobalMarket] Purchase error: ${e.message}")
                e.printStackTrace()
                return PurchaseResult(false, "<#E08A98>购买失败，请联系管理员")
            }
        }
    }

    // ── Pending Balances (Seller Proceeds) ────────────────────────

    fun getPendingBalance(player: Player, currency: String): Double {
        return pendingBalances[player.uniqueId.toString()]?.get(currency) ?: 0.0
    }

    fun getPendingBalances(player: Player): Map<String, Double> {
        return pendingBalances[player.uniqueId.toString()]?.toMap() ?: emptyMap()
    }

    fun hasPendingBalance(player: Player): Boolean {
        return pendingBalances[player.uniqueId.toString()]?.isNotEmpty() == true
    }

    /**
     * Claim all pending proceeds for a player.
     */
    fun claimProceeds(player: Player): ClaimResult {
        val uuid = player.uniqueId.toString()
        val balances = pendingBalances[uuid] ?: return ClaimResult(false, "<#E08A98>没有待领取的收益")

        val amounts = balances.toMap()
        for ((currency, amount) in amounts) {
            CurrencyManager.addBalance(player, currency, amount.toLong())
        }
        balances.clear()
        if (balances.isEmpty()) pendingBalances.remove(uuid)
        saveToDisk()

        val summary = amounts.entries.joinToString(", ") {
            "${it.value} ${CurrencyManager.displayName(it.key)}"
        }
        return ClaimResult(true, "成功领取: $summary")
    }

    // ── Listing Sessions (for the list-item flow) ─────────────────

    fun getListingSession(player: Player): MarketListingSession {
        return listingSessions.computeIfAbsent(player.uniqueId) {
            MarketListingSession(player)
        }
    }

    /** 返还会话中已收取但尚未上架的物品（防止中途退出丢物） */
    fun refundListingItem(player: Player, session: MarketListingSession) {
        val item = session.listedItemData?.let { ItemSerializer.deserializeNbt(it) } ?: session.listedItem
        if (item != null) {
            player.inventory.addItem(item).values.forEach { leftover ->
                player.world.dropItem(player.location, leftover)
            }
        }
        session.reset()
    }

    fun removeListingSession(player: Player) {
        listingSessions.remove(player.uniqueId)?.let { session ->
            if (session.step != ListingStep.CONFIRMING) {
                refundListingItem(player, session)
            }
        }
    }

    fun getPendingBalancesCount(): Int = pendingBalances.size

    fun getPlayerListingCount(uuid: UUID): Int {
        return allListings.count { it.sellerUuid == uuid.toString() && !it.sold && !it.cancelled }
    }

    // ── Utility ───────────────────────────────────────────────────

    fun serializeItem(item: ItemStack): String? {
        return ItemSerializer.serializeNbt(item)
    }

    private fun deserializeItem(listing: MarketListing): ItemStack? {
        return ItemSerializer.deserializeNbt(listing.itemData)
    }

    fun startExpiryTask() {
        expiryTask = submit(async = true, period = config.expiryCheckInterval * 20L) {
            try {
                val now = System.currentTimeMillis()
                var expiredCount = 0
                for (listing in allListings) {
                    if (!listing.sold && !listing.cancelled && listing.expireAt > 0 && listing.expireAt <= now) {
                        listing.cancelled = true
                        expiredCount++

                        // Notify seller if online
                        val seller = Bukkit.getPlayer(UUID.fromString(listing.sellerUuid))
                        if (seller != null && seller.isOnline) {
                            seller.sendMessage("<#EFDF8F>[全球市场] 你的商品 <#F2F5FA>${listing.itemType}<#EFDF8F> 已过期，请重新上架".toComponent())
                        }
                    }
                }
                if (expiredCount > 0) {
                    submit(async = false) { saveToDisk() }
                }
            } catch (_: Exception) {}
        }
    }

    fun cleanup() {
        expiryTask?.cancel()
        expiryTask = null
        listingSessions.clear()
        pendingBalances.clear()
        transactionLocks.clear()
    }
}

data class ListingStorage(
    val listings: List<MarketListing> = emptyList(),
    val pendingBalances: Map<String, Map<String, Double>> = emptyMap()
)

data class PurchaseResult(
    val success: Boolean,
    val message: String
)

data class ClaimResult(
    val success: Boolean,
    val message: String
)

data class MarketListingSession(
    val playerUuid: UUID,
    var listedItem: ItemStack? = null,
    var listedItemData: String? = null,
    var price: Double = 0.0,
    var currency: String = "money",
    var step: ListingStep = ListingStep.NONE,
    var currentPage: Int = 0
) {
    constructor(player: Player) : this(player.uniqueId)

    fun reset() {
        listedItem = null
        listedItemData = null
        price = 0.0
        currency = "money"
        step = ListingStep.NONE
        currentPage = 0
    }
}

enum class ListingStep {
    NONE,
    WAITING_ITEM,
    WAITING_PRICE,
    CONFIRMING
}
