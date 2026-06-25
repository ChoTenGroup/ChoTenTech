package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PAIN_RELIEF
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.getDamageTypeTag
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/** 伤害类型 → 伤势/精神映射 */
private data class InjurySanity(
    val externalRatio: Double,
    val internalRatio: Double,
    val sanityLoss: Double
)

private data class InjuryRatios(
    val externalRatio: Double,
    val internalRatio: Double
)

/** 基于 DamageType 的伤势/精神映射（主路径） */
private val damageTypeInjury = mapOf(
    DamageType.PHYSICAL to InjurySanity(0.8, 0.2, 0.05),
    DamageType.BURN to InjurySanity(0.5, 0.7, 0.2),
    DamageType.FROST to InjurySanity(0.2, 0.6, 0.1),
    DamageType.SHOCK to InjurySanity(0.3, 0.5, 0.15),
    DamageType.PSYCHIC to InjurySanity(0.0, 0.0, 5.0),
    DamageType.EROSION to InjurySanity(0.5, 0.5, 2.0),
    DamageType.DECAY to InjurySanity(0.1, 0.8, 0.5)
)

/** 回退：基于 DamageCause 的伤势映射（无 PDC 标签时使用） */
private val damageRatios = mapOf(
    // === 虚空 / 世界边界：满伤害 ===
    EntityDamageEvent.DamageCause.VOID to InjuryRatios(1.0, 1.0),
    EntityDamageEvent.DamageCause.WORLD_BORDER to InjuryRatios(1.0, 1.0),

    // === 火焰灼烧：外高内高 ===
    EntityDamageEvent.DamageCause.FIRE to InjuryRatios(0.6, 0.8),
    EntityDamageEvent.DamageCause.FIRE_TICK to InjuryRatios(0.4, 0.6),
    EntityDamageEvent.DamageCause.LAVA to InjuryRatios(0.8, 1.0),
    EntityDamageEvent.DamageCause.HOT_FLOOR to InjuryRatios(0.3, 0.5),
    EntityDamageEvent.DamageCause.LIGHTNING to InjuryRatios(0.5, 0.7),

    // === 溺水 / 窒息：内伤为主 ===
    EntityDamageEvent.DamageCause.DROWNING to InjuryRatios(0.0, 1.0),
    EntityDamageEvent.DamageCause.SUFFOCATION to InjuryRatios(0.1, 0.8),

    // === 摔落：外伤为主 ===
    EntityDamageEvent.DamageCause.FALL to InjuryRatios(1.0, 0.2),
    EntityDamageEvent.DamageCause.FLY_INTO_WALL to InjuryRatios(0.8, 0.3),

    // === 饥饿 / 魔法 / 药水：内伤为主 ===
    EntityDamageEvent.DamageCause.STARVATION to InjuryRatios(0.0, 1.0),
    EntityDamageEvent.DamageCause.MAGIC to InjuryRatios(0.1, 0.8),
    EntityDamageEvent.DamageCause.POISON to InjuryRatios(0.0, 0.9),
    EntityDamageEvent.DamageCause.WITHER to InjuryRatios(0.2, 0.8),

    // === 物理攻击：外伤为主，少量内伤 ===
    EntityDamageEvent.DamageCause.ENTITY_ATTACK to InjuryRatios(0.7, 0.2),
    EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK to InjuryRatios(0.6, 0.2),

    // === 弹射物：外伤 ===
    EntityDamageEvent.DamageCause.PROJECTILE to InjuryRatios(0.8, 0.1),

    // === 荆棘 / 仙人掌：反弹外伤 ===
    EntityDamageEvent.DamageCause.THORNS to InjuryRatios(0.5, 0.1),
    EntityDamageEvent.DamageCause.CONTACT to InjuryRatios(0.6, 0.0),

    // === 爆炸：均匀分布 ===
    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION to InjuryRatios(0.6, 0.6),
    EntityDamageEvent.DamageCause.ENTITY_EXPLOSION to InjuryRatios(0.6, 0.6),

    // === 其他 ===
    EntityDamageEvent.DamageCause.DRAGON_BREATH to InjuryRatios(0.3, 0.5),
    EntityDamageEvent.DamageCause.FREEZE to InjuryRatios(0.2, 0.3),
    EntityDamageEvent.DamageCause.SONIC_BOOM to InjuryRatios(0.5, 0.5)
)

/** 回退：固定精神扣除值（无 PDC 标签时使用） */
private fun getSanityLoss(cause: EntityDamageEvent.DamageCause): Double = when (cause) {
    EntityDamageEvent.DamageCause.VOID -> 0.0
    EntityDamageEvent.DamageCause.STARVATION,
    EntityDamageEvent.DamageCause.THORNS,
    EntityDamageEvent.DamageCause.FIRE_TICK -> 0.05
    EntityDamageEvent.DamageCause.MAGIC,
    EntityDamageEvent.DamageCause.FIRE -> 0.2
    EntityDamageEvent.DamageCause.LAVA,
    EntityDamageEvent.DamageCause.DROWNING,
    EntityDamageEvent.DamageCause.SUFFOCATION,
    EntityDamageEvent.DamageCause.FALL -> 1.0
    EntityDamageEvent.DamageCause.WORLD_BORDER -> 100.0
    else -> 0.1
}

@SubscribeEvent(priority = EventPriority.LOW)
fun onFallSneakProtect(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    if (event.cause != EntityDamageEvent.DamageCause.FALL) return
    val player = event.entity as Player
    if (!player.isSneaking) return

    val protect = AttributeManager.getAttributeValue(player, "sneak_protect").let {
        if (it > 0) it else 20.0
    }
    event.damage *= (1.0 - protect / 100.0).coerceIn(0.0, 1.0)
}

@SubscribeEvent(priority = EventPriority.NORMAL)
fun onPlayerDamage(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    val player = event.entity as Player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    val damage = event.finalDamage
    if (damage <= 0.0) return

    val helper = ChoTenDataHelper(player)

    // 止痛效果：每一级免疫40%精神损失，最高100%
    val painReliefLevel = helper.getStatusEffectLevel(PAIN_RELIEF)
    val sanityReduction = (painReliefLevel * 0.4).coerceIn(0.0, 1.0)
    val sanityReductionPct = (sanityReduction * 100).toInt()

    // 主路径：从 PDC 标签读取伤害类型（由 CustomDamageListener 在 LOWEST 设置）
    val damageTypeId = event.getDamageTypeTag()
    val damageType = damageTypeId?.let { DamageType.fromId(it) }

    if (damageType != null) {
        val mapping = damageTypeInjury[damageType] ?: InjurySanity(0.3, 0.2, 0.1)
        val actualSanityLoss = mapping.sanityLoss * (1.0 - sanityReduction)
        if (actualSanityLoss > 0) helper.removeSanity(actualSanityLoss)
        val extLoss = damage * mapping.externalRatio
        val intLoss = damage * mapping.internalRatio
        if (extLoss > 0) helper.removeExternalInjury(extLoss)
        if (intLoss > 0) helper.removeInternalInjury(intLoss)

        devLog("<gray>[伤害] ${player.name} <red>${damage}💥</red> " +
                "<gold>${damageType.displayName}</gold> " +
                "<white>精神-${"%.2f".format(actualSanityLoss)}</white>" +
                if (sanityReduction > 0) "<dark_gray>(止痛-${sanityReductionPct}%)</dark_gray>" else "" +
                "<green> 外伤-${"%.1f".format(extLoss)}</green> " +
                "<blue>内伤-${"%.1f".format(intLoss)}</blue></gray>")
    } else {
        // 回退：基于 DamageCause 的原始映射
        val ratios = damageRatios[event.cause] ?: InjuryRatios(0.3, 0.2)
        val sanityLoss = getSanityLoss(event.cause)
        val actualSanityLoss = sanityLoss * (1.0 - sanityReduction)
        if (actualSanityLoss > 0) helper.removeSanity(actualSanityLoss)
        val extLoss = damage * ratios.externalRatio
        val intLoss = damage * ratios.internalRatio
        if (extLoss > 0) helper.removeExternalInjury(extLoss)
        if (intLoss > 0) helper.removeInternalInjury(intLoss)

        devLog("<gray>[伤害] ${player.name} <red>${damage}💥</red> <gold>${event.cause.name}</gold> " +
                "<white>精神-${"%.2f".format(actualSanityLoss)}</white>" +
                if (sanityReduction > 0) "<dark_gray>(止痛-${sanityReductionPct}%)</dark_gray>" else "" +
                "<green> 外伤-${"%.1f".format(extLoss)}</green> " +
                "<blue>内伤-${"%.1f".format(intLoss)}</blue></gray>")
    }
}
