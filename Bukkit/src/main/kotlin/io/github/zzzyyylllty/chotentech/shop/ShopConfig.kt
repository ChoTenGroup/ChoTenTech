package io.github.zzzyyylllty.chotentech.shop

data class ShopConfig(
    val idef: String,
    val name: String,
    val guiBuy: String,
    val guiSell: String,
    val guiItems: GuiItemsConfig,
    val items: LinkedHashMap<String, ShopItemEntry>,
    val levels: Map<Int, List<LevelRequirement>> = emptyMap()
)

data class GuiItemsConfig(
    val modeBuy: DisplayItem,
    val modeSell: DisplayItem,
    val previous: PageButton,
    val next: PageButton,
    val knowledge: DisplayItem,
    val confirmBuy: ConfirmConfig,
    val confirmSell: ConfirmConfig
)

data class DisplayItem(
    val name: String,
    val lore: String = "",
    val material: String? = null,
    val model: String? = null
)

data class PageButton(
    val have: DisplayItem,
    val not: DisplayItem
)

data class ConfirmConfig(
    val have: DisplayItem,
    val notEnough: DisplayItem? = null,
    val not: DisplayItem
)

data class ShopItemEntry(
    val key: String,
    val id: String,
    val name: String? = null,
    val type: String = "normal",
    val giftPack: String? = null,
    val unlock: String? = null,
    val buy: List<BuyCost> = emptyList(),
    val sell: List<SellReward> = emptyList(),
    val buyRequirements: List<Requirement> = emptyList(),
    val sellRequirements: List<Requirement> = emptyList(),
    val limit: LimitConfig? = null
)

data class BuyCost(
    val type: CostType,
    val currency: String? = null,    // for currency type
    val item: String? = null,        // for item type
    val amount: Long = 1,
    val dynamicPricing: DynamicPricing? = null
)

data class SellReward(
    val type: CostType,
    val currency: String? = null,    // for currency type
    val item: String? = null,        // for item type
    val amount: Long = 1
)

enum class CostType {
    CURRENCY, ITEM
}

data class DynamicPricing(
    val max: Long,
    val min: Long,
    val stock: Long = -1
)

data class LimitConfig(
    val global: Long = -1,
    val player: Long = -1,
    val globalReset: String = "",
    val playerReset: String = ""
)

data class Requirement(
    val mode: String,
    val papi: String = "",
    val value: String = "",
    val display: String = "",
    val level: String = "",
    val affection: String = "",
    val affectionValue: Int = 0
)

data class LevelRequirement(
    val type: String,
    val level: String = "",
    val value: Int = 0,
    val affection: String = "",
    val balance: String = "",
    val quota: Long = 0
)
