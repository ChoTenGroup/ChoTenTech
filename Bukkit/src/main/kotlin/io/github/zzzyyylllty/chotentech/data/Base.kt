package io.github.zzzyyylllty.chotentech.data

data class Base(
    val idef: String,
    val name: String,
    val faction: String,
    val model: String,
    val enable: Boolean,
    val dev: String,
    val lore: List<String>,
    val start: String,
    // 进入基地标题的副标题行(可留空)
    val title: String = "",
    val region: String,
    val spawn: BaseSpawn,
    val savePointRegion: String? = null,
    // 不加入基地也自动发放初始物资(auto-grant: true)
    val autoGrant: Boolean = false,
)

data class BaseSpawn(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)
