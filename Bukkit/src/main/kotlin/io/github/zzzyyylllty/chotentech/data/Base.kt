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
    val region: String,
    val spawn: BaseSpawn,
)

data class BaseSpawn(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
)
