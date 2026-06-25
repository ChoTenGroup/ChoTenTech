package io.github.zzzyyylllty.chotentech.shop

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import java.io.File

object ShopManager {

    val shops = linkedMapOf<String, ShopConfig>()
    private val globalPurchaseCount = mutableMapOf<String, MutableMap<String, Long>>()
    private val playerPurchaseCount = mutableMapOf<String, MutableMap<String, Long>>()
    private val globalResetTimers = mutableMapOf<String, Long>()
    private val playerResetTimers = mutableMapOf<String, MutableMap<String, Long>>()
    private val playerSalesByShop = mutableMapOf<String, MutableMap<String, Long>>()  // shopId -> playerId -> totalSales

    fun loadAll() {
        shops.clear()
        val dir = File(getDataFolder(), "shops")
        if (!dir.exists()) {
            dir.mkdirs()
            info("§e[Shop] Shops directory created, place your shop YAML files in shops/")
            return
        }

        dir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                for (key in config.getKeys(false)) {
                    val sec = config.getConfigurationSection(key) ?: continue
                    val shop = parseShop(key, sec)
                    shops[key] = shop
                    info("§a[Shop] Loaded shop: ${shop.name} ($key)")
                }
            } catch (e: Exception) {
                info("§c[Shop] Failed to load ${file.name}: ${e.message}")
            }
        }
        info("§a[Shop] Loaded ${shops.size} shops total")
    }

    fun reload() {
        loadAll()
        ShopMenu.loadItemOutputNames()
    }

    fun getShop(id: String): ShopConfig? = shops[id]

    private fun parseShop(key: String, sec: ConfigurationSection): ShopConfig {
        val name = sec.getString("name") ?: key
        val guiBuy = sec.getString("gui.buy", "") ?: ""
        val guiSell = sec.getString("gui.sell", "") ?: ""
        val guiItems = parseGuiItems(sec.getConfigurationSection("gui-item") ?: emptySection())
        val items = parseItems(sec.getConfigurationSection("items") ?: emptySection())
        val levels = parseLevels(sec.getConfigurationSection("level"))

        return ShopConfig(key, name, guiBuy, guiSell, guiItems, items, levels)
    }

    private fun parseGuiItems(sec: ConfigurationSection): GuiItemsConfig {
        return GuiItemsConfig(
            modeBuy = parseDisplayItem(sec.getConfigurationSection("mode-buy"), "<white><b>购买模式"),
            modeSell = parseDisplayItem(sec.getConfigurationSection("mode-sell"), "<white><b>出售模式"),
            previous = PageButton(
                have = parseDisplayItem(sec.getConfigurationSection("previous.have"), "<white><b>上一页"),
                not = parseDisplayItem(sec.getConfigurationSection("previous.not"), "<white><b>已是第一页")
            ),
            next = PageButton(
                have = parseDisplayItem(sec.getConfigurationSection("next.have"), "<white><b>下一页"),
                not = parseDisplayItem(sec.getConfigurationSection("next.not"), "<white><b>已是最后一页")
            ),
            knowledge = parseDisplayItem(sec.getConfigurationSection("knowledge"), "<white><b>信息"),
            confirmBuy = parseConfirmConfig(sec.getConfigurationSection("confirm-buy"), "购买"),
            confirmSell = parseConfirmConfig(sec.getConfigurationSection("confirm-sell"), "出售")
        )
    }

    private fun parseDisplayItem(sec: ConfigurationSection?, defaultName: String, defaultLore: String = ""): DisplayItem {
        if (sec == null) return DisplayItem(defaultName, defaultLore)
        return DisplayItem(
            name = sec.getString("name") ?: defaultName,
            lore = sec.getString("lore") ?: defaultLore,
            material = sec.getString("material"),
            model = sec.getString("model")
        )
    }

    private fun parseConfirmConfig(sec: ConfigurationSection?, action: String): ConfirmConfig {
        if (sec == null) return ConfirmConfig(
            have = DisplayItem("<white><b>确认$action"),
            not = DisplayItem("<white><b>确认$action", "您还没有选定任何物品。")
        )
        return ConfirmConfig(
            have = parseDisplayItem(sec.getConfigurationSection("have"), "<white><b>确认$action"),
            notEnough = sec.getConfigurationSection("not-enough")?.let { parseDisplayItem(it, "<white><b>确认$action") },
            not = parseDisplayItem(sec.getConfigurationSection("not"), "<white><b>确认$action", "您还没有选定任何物品。")
        )
    }

    private fun parseItems(sec: ConfigurationSection): LinkedHashMap<String, ShopItemEntry> {
        val items = linkedMapOf<String, ShopItemEntry>()
        for (itemKey in sec.getKeys(false)) {
            val itemSec = sec.getConfigurationSection(itemKey) ?: continue
            val id = itemSec.getString("id") ?: continue

            val buy = parseBuyCosts(itemSec, "buy")
            val sell = parseSellRewards(itemSec, "sell")
            val buyRequirements = parseRequirementsFromList(itemSec, "buy-requirements").ifEmpty {
                parseRequirementsFromList(itemSec, "requirements")
            }
            val sellRequirements = parseRequirementsFromList(itemSec, "sell-requirements").ifEmpty {
                parseRequirementsFromList(itemSec, "requirements")
            }
            val limit = parseLimit(itemSec.getConfigurationSection("limit"))

            val name = itemSec.getString("name")
            val type = itemSec.getString("type") ?: "normal"
            val giftPack = itemSec.getString("gift-pack")
            val unlock = itemSec.getString("unlock")
            items[itemKey] = ShopItemEntry(itemKey, id, name, type, giftPack, unlock, buy, sell, buyRequirements, sellRequirements, limit)
        }
        return items
    }

    private fun parseBuyCosts(sec: ConfigurationSection, path: String): List<BuyCost> {
        val costs = mutableListOf<BuyCost>()
        val raw = sec.get(path)

        when (raw) {
            is String -> {
                val parts = raw.split(":")
                if (parts.size == 2) {
                    costs.add(BuyCost(CostType.CURRENCY, parts[0], amount = parts[1].toLongOrElse(1)))
                }
            }
            is List<*> -> {
                for (entry in raw) {
                    entry?.let { parseSingleBuyCost(it) }?.let { costs.add(it) }
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                parseSingleBuyCost(raw as Map<String, Any>)?.let { costs.add(it) }
            }
            is ConfigurationSection -> {
                parseSingleBuyCost(raw)?.let { costs.add(it) }
            }
        }
        return costs
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSingleBuyCost(entry: Any): BuyCost? {
        return when (entry) {
            is Map<*, *> -> parseCostMap(entry as Map<String, Any>)
            is ConfigurationSection -> parseCostSection(entry)
            else -> null
        }
    }

    private fun parseCostMap(map: Map<String, Any>): BuyCost? {
        return when {
            map.containsKey("currency") -> {
                val dP = map["dynamic-pricing"]
                val dp = when (dP) {
                    is Map<*, *> -> DynamicPricing(
                        max = (dP["max"] as? Number)?.toLong() ?: -1,
                        min = (dP["min"] as? Number)?.toLong() ?: -1,
                        stock = (dP["stock"] as? Number)?.toLong() ?: -1
                    )
                    is ConfigurationSection -> DynamicPricing(
                        max = dP.getLong("max", -1),
                        min = dP.getLong("min", -1),
                        stock = dP.getLong("stock", -1)
                    )
                    else -> null
                }
                BuyCost(
                    type = CostType.CURRENCY,
                    currency = map["currency"] as? String,
                    amount = (map["amount"] as? Number)?.toLong() ?: 1,
                    dynamicPricing = dp
                )
            }
            map.containsKey("item") -> {
                BuyCost(
                    type = CostType.ITEM,
                    item = map["item"] as? String,
                    amount = (map["amount"] as? Number)?.toLong() ?: 1
                )
            }
            else -> null
        }
    }

    private fun parseCostSection(sec: ConfigurationSection): BuyCost? {
        return when {
            sec.contains("currency") -> {
                val dpSec = sec.getConfigurationSection("dynamic-pricing")
                val dp = if (dpSec != null) {
                    DynamicPricing(
                        max = dpSec.getLong("max", -1),
                        min = dpSec.getLong("min", -1),
                        stock = dpSec.getLong("stock", -1)
                    )
                } else null
                BuyCost(
                    type = CostType.CURRENCY,
                    currency = sec.getString("currency"),
                    amount = sec.getLong("amount", 1),
                    dynamicPricing = dp
                )
            }
            sec.contains("item") -> {
                BuyCost(
                    type = CostType.ITEM,
                    item = sec.getString("item"),
                    amount = sec.getLong("amount", 1)
                )
            }
            else -> null
        }
    }

    private fun parseSellRewards(sec: ConfigurationSection, path: String): List<SellReward> {
        val rewards = mutableListOf<SellReward>()
        val raw = sec.get(path)

        when (raw) {
            is String -> {
                val parts = raw.split(":")
                if (parts.size == 2) {
                    rewards.add(SellReward(CostType.CURRENCY, parts[0], amount = parts[1].toLongOrElse(1)))
                }
            }
            is List<*> -> {
                for (entry in raw) {
                    entry?.let { parseSingleSellReward(it) }?.let { rewards.add(it) }
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                parseSingleSellReward(raw as Map<String, Any>)?.let { rewards.add(it) }
            }
            is ConfigurationSection -> {
                parseSingleSellReward(raw)?.let { rewards.add(it) }
            }
        }
        return rewards
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSingleSellReward(entry: Any): SellReward? {
        return when (entry) {
            is Map<*, *> -> parseRewardMap(entry as Map<String, Any>)
            is ConfigurationSection -> parseRewardSection(entry)
            else -> null
        }
    }

    private fun parseRewardMap(map: Map<String, Any>): SellReward? {
        return when {
            map.containsKey("currency") -> SellReward(
                type = CostType.CURRENCY,
                currency = map["currency"] as? String,
                amount = (map["amount"] as? Number)?.toLong() ?: 1
            )
            map.containsKey("item") -> SellReward(
                type = CostType.ITEM,
                item = map["item"] as? String,
                amount = (map["amount"] as? Number)?.toLong() ?: 1
            )
            else -> null
        }
    }

    private fun parseRewardSection(sec: ConfigurationSection): SellReward? {
        return when {
            sec.contains("currency") -> SellReward(
                type = CostType.CURRENCY,
                currency = sec.getString("currency"),
                amount = sec.getLong("amount", 1)
            )
            sec.contains("item") -> SellReward(
                type = CostType.ITEM,
                item = sec.getString("item"),
                amount = sec.getLong("amount", 1)
            )
            else -> null
        }
    }

    private fun parseRequirementsFromList(sec: ConfigurationSection, path: String): List<Requirement> {
        val list = sec.getList(path) ?: return emptyList()
        val reqs = mutableListOf<Requirement>()
        for (entry in list) {
            when (entry) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val m = entry as Map<String, Any>
                    reqs.add(Requirement(
                        mode = m["mode"] as? String ?: "",
                        papi = m["papi"] as? String ?: "",
                        value = m["value"] as? String ?: "",
                        display = m["display"] as? String ?: "",
                        level = m["level"] as? String ?: "",
                        affection = m["affection"] as? String ?: "",
                        affectionValue = (m["affection-value"] as? Number)?.toInt() ?: 0
                    ))
                }
                is ConfigurationSection -> {
                    reqs.add(Requirement(
                        mode = entry.getString("mode") ?: "",
                        papi = entry.getString("papi") ?: "",
                        value = entry.getString("value") ?: "",
                        display = entry.getString("display") ?: "",
                        level = entry.getString("level") ?: "",
                        affection = entry.getString("affection") ?: "",
                        affectionValue = entry.getInt("affection-value", 0)
                    ))
                }
            }
        }
        return reqs
    }

    private fun parseLimit(sec: ConfigurationSection?): LimitConfig? {
        if (sec == null) return null
        return LimitConfig(
            global = sec.getLong("global", -1),
            player = sec.getLong("player", -1),
            globalReset = sec.getString("global-reset", "") ?: "",
            playerReset = sec.getString("player-reset", "") ?: ""
        )
    }

    private fun parseLevels(sec: ConfigurationSection?): Map<Int, List<LevelRequirement>> {
        if (sec == null) return emptyMap()
        val levels = mutableMapOf<Int, List<LevelRequirement>>()
        for (key in sec.getKeys(false)) {
            val levelNum = key.toIntOrNull() ?: continue
            val list = sec.getList(key) ?: continue
            val reqs = mutableListOf<LevelRequirement>()
            for (entry in list) {
                if (entry is Map<*, *>) {
                    val m = entry as Map<String, Any>
                    reqs.add(LevelRequirement(
                        type = m["type"] as? String ?: "",
                        level = m["level"] as? String ?: "",
                        value = (m["value"] as? Number)?.toInt() ?: 0,
                        affection = m["affection"] as? String ?: "",
                        balance = m["balance"] as? String ?: "",
                        quota = (m["value"] as? Number)?.toLong() ?: 0
                    ))
                }
            }
            levels[levelNum] = reqs
        }
        return levels
    }

    fun getGlobalPurchaseCount(itemKey: String): Long {
        return globalPurchaseCount[itemKey]?.values?.sum() ?: 0L
    }

    fun getPlayerPurchaseCount(itemKey: String, playerId: String): Long {
        return playerPurchaseCount[itemKey]?.get(playerId) ?: 0L
    }

    fun recordPurchase(itemKey: String, playerId: String, amount: Long = 1) {
        globalPurchaseCount.computeIfAbsent(itemKey) { mutableMapOf() }.merge("_total", amount, Long::plus)
        playerPurchaseCount.computeIfAbsent(itemKey) { mutableMapOf() }
            .compute(playerId) { _, v -> (v ?: 0L) + amount }
    }

    fun recordSale(shopId: String, playerId: String, amount: Long) {
        playerSalesByShop.computeIfAbsent(shopId) { mutableMapOf() }
            .merge(playerId, amount, Long::plus)
    }

    fun getPlayerSales(shopId: String, playerId: String): Long {
        return playerSalesByShop[shopId]?.get(playerId) ?: 0L
    }

    fun parseDuration(duration: String): Long {
        if (duration.isBlank()) return 0L
        val regex = Regex("(\\d+)([smhd])")
        val match = regex.find(duration) ?: return 0L
        val amount = match.groupValues[1].toLongOrNull() ?: return 0L
        return when (match.groupValues[2]) {
            "s" -> amount * 1000L
            "m" -> amount * 60000L
            "h" -> amount * 3600000L
            "d" -> amount * 86400000L
            else -> 0L
        }
    }

    fun checkAndResetGlobalLimit(itemKey: String, interval: String) {
        if (interval.isBlank()) return
        val intervalMs = parseDuration(interval)
        if (intervalMs <= 0) return
        val now = System.currentTimeMillis()
        val lastReset = globalResetTimers[itemKey]
        if (lastReset == null) {
            globalResetTimers[itemKey] = now
            return
        }
        if (now - lastReset >= intervalMs) {
            globalPurchaseCount.remove(itemKey)
            globalResetTimers[itemKey] = now
        }
    }

    fun checkAndResetPlayerLimit(itemKey: String, playerId: String, interval: String) {
        if (interval.isBlank()) return
        val intervalMs = parseDuration(interval)
        if (intervalMs <= 0) return
        val now = System.currentTimeMillis()
        val playerTimers = playerResetTimers.getOrPut(itemKey) { mutableMapOf() }
        val lastReset = playerTimers[playerId]
        if (lastReset == null) {
            playerTimers[playerId] = now
            return
        }
        if (now - lastReset >= intervalMs) {
            playerPurchaseCount[itemKey]?.remove(playerId)
            playerTimers[playerId] = now
        }
    }

    private fun emptySection(): ConfigurationSection {
        return YamlConfiguration()
    }

    private fun String.toLongOrElse(default: Long): Long {
        return toLongOrNull() ?: default
    }
}
