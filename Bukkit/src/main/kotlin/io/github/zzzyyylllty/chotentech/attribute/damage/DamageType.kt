package io.github.zzzyyylllty.chotentech.attribute.damage

/**
 * 伤害类型分类
 */
enum class DamageCategory {
    /** 普通伤害：走通用防御 + 对应属性防御 */
    NORMAL,
    /** 特殊伤害：仅走对应属性防御 */
    SPECIAL
}

/**
 * 伤害类型枚举（内置 7 种核心类型）
 * 可通过 config.yml 的 damage-system.types 启用/禁用
 */
enum class DamageType(
    val id: String,
    val displayName: String,
    val category: DamageCategory,
    val color: String
) {
    PHYSICAL("physical", "物理", DamageCategory.NORMAL, "<red>"),
    BURN("burn", "灼烧", DamageCategory.NORMAL, "<gold>"),
    FROST("frost", "霜寒", DamageCategory.NORMAL, "<blue>"),
    SHOCK("shock", "电击", DamageCategory.NORMAL, "<yellow>"),
    PSYCHIC("psychic", "精神", DamageCategory.SPECIAL, "<dark_aqua>"),
    EROSION("erosion", "侵蚀", DamageCategory.SPECIAL, "<dark_purple>"),
    DECAY("decay", "衰退", DamageCategory.SPECIAL, "<black>");

    companion object {
        fun fromId(id: String): DamageType? = entries.find { it.id == id }
    }
}
