package io.github.zzzyyylllty.chotentech.damage

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * 伤害类型处理器接口
 * 每个伤害类型实现此接口，注册到 DamageTypeRegistry
 *
 * compute 负责防御计算（返回值受防御减免后的伤害）
 * apply 负责伤害类型特有效果（DoT、debuff、精神扣减等）
 */
interface DamageTypeHandler {

    /** 对应的伤害类型 */
    val type: DamageType

    /**
     * 计算防御减免后的伤害
     * @param baseDamage 原始伤害（已含攻击者加成）
     * @param victim 受害者
     * @return 减免后的最终伤害
     */
    fun compute(baseDamage: Double, victim: LivingEntity): Double

    /**
     * 应用伤害类型效果
     * 仅在受害者是 Player 时调用
     * @param player 受害者玩家
     * @param finalDamage 经过防御计算后的伤害
     * @param baseDamage 原始伤害（未减免，用于概率/阈值计算）
     */
    fun apply(player: Player, finalDamage: Double, baseDamage: Double)
}
