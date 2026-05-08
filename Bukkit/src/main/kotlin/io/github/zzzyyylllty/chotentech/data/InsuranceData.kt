package io.github.zzzyyylllty.chotentech.data

data class InsuredItem(
    val uuid: String,
    val itemData: String,
    val owner: String,
    val insuredAt: Long,
    val expiresAt: Long
)

data class DeathDropData(
    val entityUuid: String,
    val ownerUuid: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val items: LinkedHashMap<Int, String>
)
