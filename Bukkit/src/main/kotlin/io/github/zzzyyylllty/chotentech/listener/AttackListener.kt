package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.feature.crossbow.CrossbowManager
import io.github.zzzyyylllty.chotentech.damage.DamageManager
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.damage.getDamageTypeTag
import io.github.zzzyyylllty.chotentech.damage.removeDamageTypeTag
import io.github.zzzyyylllty.chotentech.logger.sendStringAsComponent
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.asLangText
import kotlin.random.Random

/**
 * 攻击类型属性监听器
 * 处理远程攻击、魔法攻击、火焰攻击等属性效果
 */
object AttackListener {

    /**
     * 获取攻击者玩家（提取现有逻辑）
     */
    private fun getAttackerPlayer(damager: Entity): Player? {
        return when (damager) {
            is Player -> damager
            is Projectile -> {
                // 如果是弹射物，检查发射者
                val shooter = damager.shooter
                if (shooter is Player) shooter else null
            }
            // 检查是否为火焰弹、小火球等
            is Fireball -> {
                val shooter = damager.shooter
                if (shooter is Player) shooter else null
            }
            else -> null
        }
    }

    /**
     * 计算伤害加成
     */
    private fun calculateDamageBonus(player: Player, attackTypes: Set<AttackType>, baseDamage: Double): Double {
        var damage = baseDamage

        // 物理伤害加成对所有攻击类型有效
        val physicalBonus = AttributeManager.getAttributeValue(player, "physical_extra_damage")
        if (physicalBonus != 0.0) {
            damage *= (1.0 + physicalBonus).coerceAtLeast(0.0)
        }

        // 远程伤害加成
        if (attackTypes.contains(AttackType.RANGED)) {
            // 新旧属性取最大值
            val rangedBonusNew = AttributeManager.getAttributeValue(player, "ranged_extra_damage")
            val rangedBonusOld = AttributeManager.getAttributeValue(player, "ranged_attack_damage")
            val rangedBonus = maxOf(rangedBonusNew, rangedBonusOld)
            if (rangedBonus != 0.0) {
                damage *= (1.0 + rangedBonus).coerceAtLeast(0.0)
            }
        }

        // 弓伤害加成
        if (attackTypes.contains(AttackType.BOW)) {
            val bowBonus = AttributeManager.getAttributeValue(player, "bow_extra_damage")
            if (bowBonus != 0.0) {
                damage *= (1.0 + bowBonus).coerceAtLeast(0.0)
            }
        }

        // 弩伤害加成
        if (attackTypes.contains(AttackType.CROSSBOW)) {
            val crossbowBonus = AttributeManager.getAttributeValue(player, "crossbow_extra_damage")
            if (crossbowBonus != 0.0) {
                damage *= (1.0 + crossbowBonus).coerceAtLeast(0.0)
            }
        }

        // 魔法伤害加成
        if (attackTypes.contains(AttackType.MAGIC)) {
            // 新旧属性取最大值
            val magicBonusNew = AttributeManager.getAttributeValue(player, "magic_extra_damage")
            val magicBonusOld = AttributeManager.getAttributeValue(player, "magic_attack_damage")
            val magicBonus = maxOf(magicBonusNew, magicBonusOld)
            if (magicBonus != 0.0) {
                damage *= (1.0 + magicBonus).coerceAtLeast(0.0)
            }
        }

        // 火焰伤害加成（如果攻击是火焰类型）
        if (attackTypes.contains(AttackType.FIRE)) {
            // 新旧属性取最大值
            val fireBonusNew = AttributeManager.getAttributeValue(player, "fire_extra_damage")
            val fireBonusOld = AttributeManager.getAttributeValue(player, "fire_attack_damage")
            val fireBonus = maxOf(fireBonusNew, fireBonusOld)
            if (fireBonus != 0.0) {
                damage *= (1.0 + fireBonus).coerceAtLeast(0.0)
            }
        }

        return damage
    }

    /**
     * 计算伤害减免
     */
    private fun calculateDamageReduction(victim: Player, attackTypes: Set<AttackType>, damage: Double): Double {
        var remainingDamage = damage

        // 全伤害减免对所有攻击类型有效
        val allReduction = AttributeManager.getAttributeValue(victim, "all_damage_reduction")
        if (allReduction > 0) {
            val reduction = allReduction.coerceAtMost(1.0) // 限制在100%以内
            remainingDamage *= (1.0 - reduction)
        }

        // 物理伤害减免（包含近战和远程物理伤害；若同时包含火焰伤害，物理减免仍然生效）
        if (attackTypes.contains(AttackType.MELEE) || attackTypes.contains(AttackType.RANGED)) {
            val physicalReduction = AttributeManager.getAttributeValue(victim, "physical_damage_reduction")
            if (physicalReduction > 0) {
                val reduction = physicalReduction.coerceAtMost(1.0)
                remainingDamage *= (1.0 - reduction)
            }
        }

        // 远程伤害减免
        if (attackTypes.contains(AttackType.RANGED)) {
            val rangedReduction = AttributeManager.getAttributeValue(victim, "ranged_damage_reduction")
            if (rangedReduction > 0) {
                val reduction = rangedReduction.coerceAtMost(1.0)
                remainingDamage *= (1.0 - reduction)
            }
        }

        // 弓伤害减免
        if (attackTypes.contains(AttackType.BOW)) {
            val bowReduction = AttributeManager.getAttributeValue(victim, "bow_damage_reduction")
            if (bowReduction > 0) {
                remainingDamage *= (1.0 - bowReduction.coerceAtMost(1.0))
            }
        }

        // 弩伤害减免
        if (attackTypes.contains(AttackType.CROSSBOW)) {
            val crossbowReduction = AttributeManager.getAttributeValue(victim, "crossbow_damage_reduction")
            if (crossbowReduction > 0) {
                remainingDamage *= (1.0 - crossbowReduction.coerceAtMost(1.0))
            }
        }

        // 魔法伤害减免
        if (attackTypes.contains(AttackType.MAGIC)) {
            val magicReduction = AttributeManager.getAttributeValue(victim, "magic_damage_reduction")
            if (magicReduction > 0) {
                val reduction = magicReduction.coerceAtMost(1.0)
                remainingDamage *= (1.0 - reduction)
            }
        }

        // 火焰伤害减免
        if (attackTypes.contains(AttackType.FIRE)) {
            val fireReduction = AttributeManager.getAttributeValue(victim, "fire_damage_reduction")
            if (fireReduction > 0) {
                val reduction = fireReduction.coerceAtMost(1.0)
                remainingDamage *= (1.0 - reduction)
            }
        }

        // 确保伤害不为负数
        return remainingDamage.coerceAtLeast(0.0)
    }

    /**
     * 处理实体伤害事件，应用攻击类型属性加成
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val victim = event.entity

        // 1. 获取攻击者玩家
        val attacker = getAttackerPlayer(damager)
        if (attacker == null || victim.isDead) return

        val originalDamage = event.damage
        var modifiedDamage = originalDamage

        // 2. 检测攻击类型
        val attackTypes = detectAttackTypes(damager, attacker).toMutableSet()

        // 2.1 检查事件原因是否为火焰伤害
        val isFireDamage = attackTypes.contains(AttackType.FIRE) ||
                          event.cause.name.contains("FIRE", ignoreCase = true) ||
                          event.cause.name.contains("LAVA", ignoreCase = true) ||
                          event.cause.name.contains("HOT_FLOOR", ignoreCase = true) ||
                          event.cause.name.contains("MELTING", ignoreCase = true)
        if (isFireDamage) {
            attackTypes.add(AttackType.FIRE)
        }

        // 3. 计算伤害加成（攻击者）
        modifiedDamage = calculateDamageBonus(attacker, attackTypes, modifiedDamage)

        // 4. 计算伤害减免（受害者，如果是玩家）
        if (victim is Player) {
            modifiedDamage = calculateDamageReduction(victim, attackTypes, modifiedDamage)
        }

        // 4.5 伤势效果：内伤增加承受伤害，外伤增加对方暴击概率
        if (victim is Player) {
            val victimData = playerDataMap[victim.uniqueId.toString()]
            if (victimData != null) {
                // 内伤：0%时对方攻击伤害+100%，线性插值
                val internalInjuryRatio = 1.0 - (victimData.internalInjury / victimData.maxInternalInjury).coerceIn(0.0, 1.0)
                if (internalInjuryRatio > 0) {
                    modifiedDamage *= (1.0 + internalInjuryRatio)
                }
                // 外伤：0%时对方暴击率+100%，线性插值
                val externalInjuryRatio = 1.0 - (victimData.externalInjury / victimData.maxExternalInjury).coerceIn(0.0, 1.0)
                if (externalInjuryRatio > 0) {
                    val baseCritChance = AttributeManager.getAttributeValue(attacker, "crit_chance")
                    val totalCritChance = (baseCritChance + externalInjuryRatio).coerceAtMost(1.0)
                    if (totalCritChance > 0 && Random.nextDouble() < totalCritChance) {
                        val critDamage = AttributeManager.getAttributeValue(attacker, "crit_damage")
                        val effectiveCritDmg = if (critDamage > 0) critDamage else 0.5
                        modifiedDamage *= (1.0 + effectiveCritDmg)
                    }
                }
            }
        }

        // 5. 火焰攻击几率逻辑（现有逻辑保持不变）
        // 注意：火焰伤害加成已经在calculateDamageBonus中处理，但火焰攻击几率可能触发额外加成
        // 应用火焰攻击几率（对非火焰攻击有一定几率附加火焰效果）
        if (!isFireDamage && victim is LivingEntity) {
            val fireChance = AttributeManager.getAttributeValue(attacker, "fire_attack_chance")
            if (fireChance > 0 && Random.nextDouble() < fireChance) {
                // 附加火焰效果
                victim.fireTicks = 100 // 5秒燃烧时间

                // 如果是火焰攻击几率触发的火焰效果，也应用火焰攻击伤害加成
                // 这里需要应用火焰伤害加成，因为攻击现在变成了火焰类型
                val fireBonusNew = AttributeManager.getAttributeValue(attacker, "fire_extra_damage")
                val fireBonusOld = AttributeManager.getAttributeValue(attacker, "fire_attack_damage")
                val fireBonus = maxOf(fireBonusNew, fireBonusOld)
                if (fireBonus != 0.0) {
                    modifiedDamage *= (1.0 + fireBonus).coerceAtLeast(0.0)
                }

                // 如果受害者是玩家，应用火焰伤害减免（因为攻击现在变成了火焰类型）
                if (victim is Player) {
                    val fireReduction = AttributeManager.getAttributeValue(victim, "fire_damage_reduction")
                    if (fireReduction > 0) {
                        val reduction = fireReduction.coerceAtMost(1.0)
                        modifiedDamage *= (1.0 - reduction)
                    }
                    // 注意：全伤害减免已经在前面的calculateDamageReduction中应用
                    // 不需要重复应用
                }
            }
        }

        // 6. 应用固定值远程伤害（弓/弩的基础伤害）
        if (attackTypes.contains(AttackType.RANGED)) {
            val rangedDamage = AttributeManager.getAttributeValue(attacker, "ranged_damage")
            if (rangedDamage > 0) {
                modifiedDamage += rangedDamage
            }
        }

        // 7. 应用固定值魔法伤害
        val magicDamage = AttributeManager.getAttributeValue(attacker, "magic_damage")
        if (magicDamage > 0) {
            modifiedDamage += magicDamage
        }

        // 确保伤害不为负数
        modifiedDamage = modifiedDamage.coerceAtLeast(0.0)

        // 8. 防御/穿透计算
        // 仅对弹射物攻击应用（含弩箭等），读取CrossbowManager中已注册的箭矢数据
        if (damager is AbstractArrow) {
            val arrowData = CrossbowManager.getFlyingArrowData(damager.uniqueId)
            if (arrowData != null) {
                val targetDefense = if (victim is LivingEntity) {
                    CrossbowManager.getEntityDefense(victim)
                } else {
                    0.0
                }
                val result = CrossbowManager.calculatePenetration(
                    incomingDamage = modifiedDamage,
                    penetrate = arrowData.penetrate,
                    targetDefense = targetDefense,
                    durabilityRatio = ChoTenTech.config.getDouble("crossbow-mechanism.penetration-durability-ratio", 1.0)
                )
                modifiedDamage = result.finalDamage

                // 穿透溢出额外耐久损耗
                if (result.extraDurability > 0 && victim is Player) {
                    CrossbowManager.applyExtraDurabilityDamage(victim as Player, result.extraDurability)
                }

                // 清理飞行箭矢记录
                CrossbowManager.removeFlyingArrow(damager.uniqueId)
            }
        }

        // 8.5 伤害类型系统：防御计算与效果
        val dmgSysEnabled = ChoTenTech.config.getBoolean("damage-system.enabled", false)
        if (dmgSysEnabled && victim is LivingEntity) {
            val damageTypeId = event.getDamageTypeTag()
            if (damageTypeId != null) {
                val rdt = DamageTypeRegistry.get(damageTypeId)
                if (rdt != null && rdt.enabled) {
                    val beforeDefense = modifiedDamage
                    modifiedDamage = DamageManager.calculateDamage(beforeDefense, rdt.type, victim)

                    if (victim is Player && modifiedDamage > 0) {
                        DamageManager.applyEffects(victim, rdt.type, modifiedDamage, beforeDefense)
                    }
                }
            }
            event.removeDamageTypeTag()
        }

        // 9. 闪避与格挡判定（受害者）
        // 注意：此处的判定基于前面计算出的modifiedDamage，不依赖攻击类型
        if (victim is Player) {
            val bdEnabled = ChoTenTech.config.getBoolean("block-dodge-settings.enabled", true)
            if (bdEnabled) {
                // 闪避判定：完全取消伤害
                val dodgeChance = AttributeManager.getAttributeValue(victim, "dodge_chance")
                if (dodgeChance > 0 && Random.nextDouble() < dodgeChance) {
                    modifiedDamage = 0.0
                    victim.sendStringAsComponent(victim.asLangText("Dodge_Triggered"))
                } else {
                    // 格挡判定：抵消部分伤害
                    val blockChance = AttributeManager.getAttributeValue(victim, "block_chance")
                    if (blockChance > 0 && Random.nextDouble() < blockChance) {
                        val blockDefense = AttributeManager.getAttributeValue(victim, "block_defense")
                        val blockDefensePercent = AttributeManager.getAttributeValue(victim, "block_defense_percent")
                        val blocked = blockDefense + modifiedDamage * blockDefensePercent
                        modifiedDamage = (modifiedDamage - blocked).coerceAtLeast(0.0)
                        victim.sendStringAsComponent(victim.asLangText("Block_Triggered"))
                    }
                }
            }
        }

        // 10. 高耐久度模式：根据护甲defense分布额外扣除耐久
        if (CrossbowManager.isHighDurabilityEnabled() && victim is Player && modifiedDamage > 0) {
            val ratio = CrossbowManager.getHighDurabilityRatio()
            CrossbowManager.applyHighDurabilityDamage(victim as Player, modifiedDamage, ratio)
        }

        // 11. 更新伤害值
        if (modifiedDamage != originalDamage) {
            event.damage = modifiedDamage
        }
    }

    /**
     * 检测远程武器类型（弓或弩）
     */
    private fun detectRangedWeaponType(damager: Entity, shooter: Player): AttackType? {
        if (damager !is AbstractArrow) return null

        // Paper 1.21+ API: 直接从弹射物获取发射武器
        val weapon = try {
            damager.getWeapon()
        } catch (_: NoSuchMethodError) {
            null
        }

        val weaponType = if (weapon != null && !weapon.type.isAir) {
            weapon.type
        } else {
            // 回退方案：检查射击者主手物品
            shooter.inventory.itemInMainHand.type
        }

        return when (weaponType) {
            org.bukkit.Material.BOW -> AttackType.BOW
            org.bukkit.Material.CROSSBOW -> AttackType.CROSSBOW
            else -> null
        }
    }

    /**
     * 检测攻击类型（可能同时属于多种类型）
     */
    private fun detectAttackTypes(damager: Entity, player: Player): Set<AttackType> {
        val types = mutableSetOf<AttackType>()

        // 1. 检查是否为远程攻击（弹射物）
        if (damager is Projectile) {
            types.add(AttackType.RANGED)
            // 进一步检测武器类型（弓/弩）
            val weaponType = detectRangedWeaponType(damager, player)
            if (weaponType != null) {
                types.add(weaponType)
            }
        }

        // 2. 检查是否为火焰攻击（火焰弹、小火球等）
        if (damager is Fireball) {
            types.add(AttackType.FIRE)
        }

        // 3. 检查武器是否有火焰附加附魔
        val item = player.inventory.itemInMainHand
        if (item.hasItemMeta() && hasFireAspectEnchantment(item)) {
            types.add(AttackType.FIRE)
        }

        // 4. 检查是否为魔法攻击
        if (isMagicAttack(player)) {
            types.add(AttackType.MAGIC)
        }

        // 5. 如果没有特殊类型，则视为近战攻击
        if (types.isEmpty() && damager is Player) {
            types.add(AttackType.MELEE)
        }

        return types
    }

    /**
     * 检测是否为魔法攻击
     * 基于玩家的 magic_extra_damage（魔法伤害加成百分比）判断
     * magic_damage 是固定值伤害，始终附加到所有攻击（见步骤7），不应作为攻击类型判定依据
     * magic_extra_damage 为百分比加成，仅当攻击类型为 MAGIC 时生效，因此用它判定魔法攻击类型
     */
    private fun isMagicAttack(player: Player): Boolean {
        val magicExtraDamage = AttributeManager.getAttributeValue(player, "magic_extra_damage")
        return magicExtraDamage > 0
    }

    /**
     * 检查物品是否有火焰附加附魔
     */
    private fun hasFireAspectEnchantment(item: ItemStack): Boolean {
        if (!item.hasItemMeta()) return false

        val meta = item.itemMeta!!
        // 检查所有附魔，查找名称中包含"fire"的
        for (enchant in meta.enchants.keys) {
            val key = enchant.key
            if (key.key.contains("fire", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 攻击类型枚举
     */
    private enum class AttackType {
        MELEE,      // 近战攻击
        RANGED,     // 远程攻击
        BOW,        // 弓攻击
        CROSSBOW,   // 弩攻击
        MAGIC,      // 魔法攻击
        FIRE        // 火焰攻击
    }
}