package io.github.zzzyyylllty.chotentech.quetiapine.data

/** 方块挖掘分类 */
enum class BlockCategory(val displayName: String) {
    MINERAL("矿物"),
    WOOD("木材"),
    CROP("作物"),
    EARTH("泥土");

    companion object {
        fun fromString(s: String): BlockCategory? = entries.find {
            it.name.equals(s, ignoreCase = true)
        }
    }
}
