package io.github.zzzyyylllty.chotentech.damage

/**
 * 已注册的伤害类型（含运行时配置）
 * 每个 DamageType 枚举值对应一个 RegisteredDamageType
 */
data class RegisteredDamageType(
    /** 核心类型 */
    val type: DamageType,
    /** 是否启用（由 config.yml 控制） */
    val enabled: Boolean = true,
    /** 额外伤害加成属性 ID（attack_extra_damage，用于攻击者） */
    val extraDamageAttribute: String = "${type.id}_extra_damage",
    /** 防御属性 ID（用于受害者） */
    val defenseAttribute: String = "${type.id}_defense",
    /** 伤害加成属性 ID（用于攻击者） */
    val damageAttribute: String = "${type.id}_damage",
    /** 自定义效果配置 JSON 字符串或 key，由效果逻辑解析 */
    val effectConfig: Map<String, Any> = emptyMap()
) {
    val id: String get() = type.id
    val category: DamageCategory get() = type.category
}
