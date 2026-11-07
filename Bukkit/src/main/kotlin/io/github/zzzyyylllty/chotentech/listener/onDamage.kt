package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.PAIN_RELIEF
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.chotentech.damage.getDamageTypeTag
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.function.WeightManager
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.bleedout.isPlayerInflicted
import io.github.zzzyyylllty.chotentech.isSafeWorld
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/** 安全世界/基地内无敌 —— 玩家在 safe-worlds 或 base- 区域中免疫所有伤害 */


@SubscribeEvent(priority = EventPriority.HIGH)
fun onBaseInvincible(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    if (event.isCancelled) return
    val player = event.entity as Player
    if (isSafeWorld(player)) {
        event.isCancelled = true
        return
    }
    val uuid = player.uniqueId.toString()
    val regions = playerRegions[uuid]
    if (regions.isNullOrEmpty()) return
    if (regions.any { it.startsWith("base-") }) {
        event.isCancelled = true
    }
}

/** 受击特效：音效 + 粒子 */
private data class HitEffect(val sound: Sound, val particle: Particle)

/** 基于 DamageType 的受击特效 */
private val damageTypeHitEffects = mapOf(
    DamageType.PHYSICAL to HitEffect(Sound.ENTITY_PLAYER_HURT, Particle.CRIT),
    DamageType.BURN to HitEffect(Sound.ENTITY_PLAYER_HURT_ON_FIRE, Particle.FLAME),
    DamageType.FROST to HitEffect(Sound.ENTITY_PLAYER_HURT_FREEZE, Particle.SNOWFLAKE),
    DamageType.SHOCK to HitEffect(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Particle.ELECTRIC_SPARK),
    DamageType.PSYCHIC to HitEffect(Sound.ENTITY_WARDEN_SONIC_BOOM, Particle.PORTAL),
    DamageType.EROSION to HitEffect(Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, Particle.DRIPPING_OBSIDIAN_TEAR),
    DamageType.DECAY to HitEffect(Sound.ENTITY_WITHER_HURT, Particle.WITCH),
)

/** 回退：基于 DamageCause 的受击特效 */
private val damageCauseHitEffects = mapOf(
    EntityDamageEvent.DamageCause.FIRE to HitEffect(Sound.ENTITY_PLAYER_HURT_ON_FIRE, Particle.FLAME),
    EntityDamageEvent.DamageCause.FIRE_TICK to HitEffect(Sound.ENTITY_PLAYER_HURT_ON_FIRE, Particle.FLAME),
    EntityDamageEvent.DamageCause.LAVA to HitEffect(Sound.ENTITY_PLAYER_HURT_ON_FIRE, Particle.LAVA),
    EntityDamageEvent.DamageCause.FALL to HitEffect(Sound.ENTITY_PLAYER_HURT, Particle.CRIT),
    EntityDamageEvent.DamageCause.DROWNING to HitEffect(Sound.ENTITY_PLAYER_HURT, Particle.BUBBLE_COLUMN_UP),
    EntityDamageEvent.DamageCause.LIGHTNING to HitEffect(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Particle.ELECTRIC_SPARK),
    EntityDamageEvent.DamageCause.WITHER to HitEffect(Sound.ENTITY_WITHER_HURT, Particle.WITCH),
    EntityDamageEvent.DamageCause.POISON to HitEffect(Sound.ENTITY_WITCH_DRINK, Particle.WITCH),
    EntityDamageEvent.DamageCause.ENTITY_ATTACK to HitEffect(Sound.ENTITY_PLAYER_HURT, Particle.SWEEP_ATTACK),
    EntityDamageEvent.DamageCause.PROJECTILE to HitEffect(Sound.ENTITY_ARROW_HIT, Particle.CRIT),
    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION to HitEffect(Sound.ENTITY_GENERIC_EXPLODE, Particle.EXPLOSION),
    EntityDamageEvent.DamageCause.ENTITY_EXPLOSION to HitEffect(Sound.ENTITY_GENERIC_EXPLODE, Particle.EXPLOSION),
    EntityDamageEvent.DamageCause.FREEZE to HitEffect(Sound.ENTITY_PLAYER_HURT_FREEZE, Particle.SNOWFLAKE),
    EntityDamageEvent.DamageCause.SONIC_BOOM to HitEffect(Sound.ENTITY_WARDEN_SONIC_BOOM, Particle.SONIC_BOOM),
)

private fun playHitEffect(player: Player, effect: HitEffect) {
    val cfg = ChoTenTech.config.getConfigurationSection("hit-effects") ?: return
    if (!cfg.getBoolean("enabled", true)) return
    player.world.playSound(
        player.location,
        effect.sound,
        cfg.getDouble("sound-volume", 1.0).toFloat(),
        cfg.getDouble("sound-pitch", 1.0).toFloat()
    )
    val count = cfg.getInt("particle-count", 20)
    if (count <= 0) return
    val offset = cfg.getDouble("particle-offset", 0.5)
    val speed = cfg.getDouble("particle-speed", 0.05)
    player.world.spawnParticle(effect.particle, player.location.clone().add(0.0, 1.0, 0.0), count, offset, offset, offset, speed)
}

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

/** 负重增加摔落伤害：20KG起算，每超过1KG +0.2x */


@SubscribeEvent(priority = EventPriority.LOW)
fun onWeightFallDamage(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    if (event.cause != EntityDamageEvent.DamageCause.FALL) return
    if (event.isCancelled) return
    val player = event.entity as Player
    if (player.gameMode == org.bukkit.GameMode.CREATIVE || player.gameMode == org.bukkit.GameMode.SPECTATOR) return
    val weight = WeightManager.getPlayerTotalWeight(player)
    if (weight <= 20.0) return
    val multiplier = 1.0 + (weight - 20.0) * 0.2
    event.damage *= multiplier
}



@SubscribeEvent(priority = EventPriority.NORMAL)
fun onPlayerDamage(event: EntityDamageEvent) {
    if (event.entity !is Player) return
    if (event.isCancelled) return
    val player = event.entity as Player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

    // 休克免疫伤害与精神损失
    if (BleedoutManager.isShocked(player.uniqueId)) return

    // 基地内无敌
    val regions = playerRegions[player.uniqueId.toString()]
    if (!regions.isNullOrEmpty() && regions.any { it.startsWith("base-") }) return

    val damage = event.finalDamage
    if (damage <= 0.0) return

    val helper = ChoTenDataHelper(player)

    // 止痛效果：每一级免疫40%精神损失，最高100%
    val painReliefLevel = helper.getStatusEffectLevel(PAIN_RELIEF)
    val sanityReduction = (painReliefLevel * 0.4).coerceIn(0.0, 1.0)

    // 主路径：从 PDC 标签读取伤害类型（由 CustomDamageListener 在 LOWEST 设置）
    val damageTypeId = event.getDamageTypeTag()
    val damageType = damageTypeId?.let { DamageType.fromId(it) }

    if (damageType != null) {
        val actualSanityLoss = (when (damageType) {
            DamageType.PHYSICAL -> .05
            DamageType.BURN -> .2
            DamageType.FROST -> .1
            DamageType.SHOCK -> .15
            DamageType.PSYCHIC -> 5.0
            DamageType.EROSION -> 2.0
            DamageType.DECAY -> .5
        }) * (1.0 - sanityReduction)
        if (actualSanityLoss > 0) helper.removeSanity(actualSanityLoss)

        // 播放受击特效
        damageTypeHitEffects[damageType]?.let { playHitEffect(player, it) }

    } else {
        val sanityLoss = getSanityLoss(event.cause)
        val actualSanityLoss = sanityLoss * (1.0 - sanityReduction)
        if (actualSanityLoss > 0) helper.removeSanity(actualSanityLoss)
        // 播放受击特效（回退）
        damageCauseHitEffects[event.cause]?.let { playHitEffect(player, it) }

    }
}
