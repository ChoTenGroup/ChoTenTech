package io.github.zzzyyylllty.chotentech.shop

data class GiftPackConfig(
    val key: String,
    val name: String,
    val price: Long,
    val currency: String = "point",
    val categories: LinkedHashMap<String, GiftPackCategory>,
    val priceModifiers: Map<String, Long> = emptyMap()
)

data class GiftPackCategory(
    val displayName: String,
    val items: List<GiftPackSelectableItem>,
    val maxSelect: Int = 1
)

data class GiftPackSelectableItem(
    val id: String,
    val displayName: String
)
