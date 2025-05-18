package io.github.zzzyyylllty.chotentech.data

import java.time.LocalDateTime

data class Loot(
    val searchAmount: String,
    val lootItems: MutableList<LootItem>,
    val refreshTime: String?,
    val refreshTimeEnd: LocalDateTime?,
    val items: LinkedHashMap<Int, String>
)

data class LootItem(
    val item: String,
    val weight: Double
)