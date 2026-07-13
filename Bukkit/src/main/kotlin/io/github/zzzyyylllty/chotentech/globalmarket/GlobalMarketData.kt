package io.github.zzzyyylllty.chotentech.globalmarket

data class MarketListing(
    val id: String,
    val sellerUuid: String,
    val sellerName: String,
    val itemData: String,
    val itemType: String,
    val itemAmount: Int,
    val price: Double,
    val currency: String,
    val listedAt: Long,
    val expireAt: Long,
    var sold: Boolean = false,
    var cancelled: Boolean = false
)

data class MarketConfig(
    val listingFee: Double = 0.0,
    val salesTax: Double = 0.0,
    val listingExpiryDays: Int = 30,
    val maxListingsPerPlayer: Int = 10,
    val defaultCurrency: String = "money",
    val expiryCheckInterval: Int = 300,
    val maxListingsPerPage: Int = 45
)

enum class MarketSort(val displayName: String) {
    NEWEST("最新上架"),
    OLDEST("最早上架"),
    CHEAPEST("价格最低"),
    MOST_EXPENSIVE("价格最高")
}

enum class MarketFilter(val displayName: String) {
    ALL("全部"),
    MY_LISTINGS("我的上架"),
    BOUGHT("已购买")
}
