package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.damage.DamageManager
import io.github.zzzyyylllty.chotentech.damage.DamageType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.metadata.FixedMetadataValue
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap
import taboolib.platform.BukkitPlugin
import kotlin.random.Random

/**
 * 子弹命中结算。
 *
 * P1 的结算链（护甲段 **不做**，留给 P5）：
 * 1. 距离衰减
 * 2. 暴击（`ammo.critChance`，×1.5）
 * 3. 通用物理减免 [DamageManager.calculateDamage]（走现有伤害类型系统）
 * 4. `target.damage(...)`，施加前后挂/摘 `chotentech_gun_hit` metadata，
 *    让 [io.github.zzzyyylllty.chotentech.listener.AttackListener] 不再二次结算
 * 5. 由通用致命伤拦截器结算肢体韧性
 * 6. 沿弹道方向的轻微击退（`damage.knockback`，默认 0.08）
 */
object GunDamage {

    /** 命中时给受害者打的标记：AttackListener 看到它就跳过自己那套结算。 */
    const val HIT_METADATA = "chotentech_gun_hit"
    /** 仅用于标识损坏枪械的固定枪托伤害，供通用监听器跳过属性改写。 */
    const val MELEE_METADATA = "chotentech_gun_broken_melee"

    // ==================== 诊断计数 ====================

    /** 进入结算的次数（含被取消的）。 */
    @Volatile
    var settlements: Long = 0L
        private set

    /** 真正生效（事件没被取消、没抛异常）的次数。 */
    @Volatile
    var appliedCount: Long = 0L
        private set

    /** 因为伤害算出 0 / 非有限值而直接放弃的次数。 */
    @Volatile
    var droppedZero: Long = 0L
        private set

    /** 一次 damage() 调用的取消状态；嵌套伤害必须各自拥有独立上下文。 */
    private data class HitContext(
        val targetId: java.util.UUID,
        var cancelled: Boolean = false,
    )

    /**
     * Bukkit 的 damage() 在主线程同步派发事件。使用线程局部栈而不是单个布尔值，
     * 保证监听器触发嵌套伤害时，内层调用不会覆盖或清理外层调用的状态。
     */
    private val hitContexts = ThreadLocal.withInitial { ArrayDeque<HitContext>() }

    /** 在 Bukkit damage() 同步派发完成后，只更新当前目标对应的最内层上下文。 */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun observeDamage(event: EntityDamageEvent) {
        if (!event.entity.hasMetadata(HIT_METADATA) || !event.isCancelled) return
        val context = hitContexts.get().lastOrNull { it.targetId == event.entity.uniqueId }
            ?: return
        // metadata 存在期间可能有嵌套的环境伤害；只有当前枪手造成的实体伤害
        // 或直接 damage() 产生的无 damager 事件，才属于枪械命中上下文。
        if (event is EntityDamageByEntityEvent && event.damager !is Player) return
        context.cancelled = true
    }

    /**
     * 距离衰减系数。
     *
     * ```
     * d <= range          → 1.0
     * range < d <= 2range → 1.0 - 0.6 * (d - range) / range
     * d > 2range          → 0.4 * max(0, 1 - (d - 2range) / (2range))
     * ```
     */
    fun distanceFactor(distance: Double, range: Double): Double {
        val r = range.coerceAtLeast(1.0)
        val d = distance.coerceAtLeast(0.0)
        return when {
            d <= r -> 1.0
            d <= 2.0 * r -> (1.0 - 0.6 * (d - r) / r).coerceIn(0.0, 1.0)
            else -> (0.4 * (1.0 - (d - 2.0 * r) / (2.0 * r))).coerceIn(0.0, 1.0)
        }
    }

    /**
     * 命中结算（由 [GunBallistics] 在主线程调用）。
     */
    fun applyHit(
        bullet: Bullet,
        target: LivingEntity,
        hitPoint: Location,
        hitDistance: Double = bullet.traveled,
    ) {
        val range = bullet.gun.attributes.range
        var damage = bullet.damage * distanceFactor(hitDistance, range)

        // 暴击
        val crit = Random.nextDouble() * 100.0 < bullet.ammo.critChance
        if (crit) {
            damage *= 1.5
        }

        // P5：mitigate 只计算，耐久由这里显式落盘。关闭替代模式时仍计算本次护甲损耗，
        // 但最终伤害保持 P1 的通用物理减免，且不使用护甲减伤。
        val raw = damage
        val mitigation = ArmorManager.mitigate(raw, bullet.ammo, target, hitPoint, crit)
        val part = ArmorManager.hitPart(target, hitPoint)
        // 直接复用 mitigate 内部的护甲件：再调一次 pieces(target) 等于每发子弹多扫一遍
        // 装备槽 + NBT（霰弹 8 颗弹丸就是 16 次），两次结果还可能不一致。
        val covered = mitigation.pieces

        if (GunSettings.armor.replacesAttributeDefense) {
            // 新链：距离/暴击 → 护甲减伤 → damage；不再走 defense 属性，避免二次减伤。
            damage = mitigation.damage
        } else {
            // 兼容链：保留 P1 的通用物理减免；护甲只承受耐久损耗，不重复减伤。
            damage = try {
                DamageManager.calculateDamage(raw, DamageType.PHYSICAL, target)
            } catch (_: Exception) {
                raw
            }
        }

        if (!damage.isFinite() || damage <= 0.0) {
            droppedZero++
            devLog(
                "[Gun] 命中结算被丢弃：伤害=$damage（原始=$raw，衰减后=${"%.2f".format(damage)} " +
                    "护甲减伤=${mitigation.damage}，替换防御=${GunSettings.armor.replacesAttributeDefense}）",
            )
            return
        }

        val plugin = try {
            BukkitPlugin.getInstance()
        } catch (_: Throwable) {
            null
        }
        val shooter = try {
            Bukkit.getPlayer(bullet.owner)
        } catch (_: Exception) {
            null
        }

        // 施加伤害（打标记 → 结算 → 摘标记），以当前这一次同步 damage() 调用确认事件是否取消。
        // Bukkit 的生命值不是可靠的“命中结果”：吸收心、无敌帧和其它伤害插件
        // 都可能让生命值不下降，但伤害调用本身仍已被处理。
        //
        // 但 `damage()` 的**布尔返回值**必须用上：它返回 false 只有两种情况 ——
        // 目标处于创造模式 / 无敌（`invulnerable`），或本次调用被 `EntityDamageEvent` 取消。
        // 前者根本不会派发事件，`observeDamage` 也就永远看不到，于是原先这套逻辑会把
        // 「对着创造模式玩家开枪」当成命中生效：扣护甲耐久、推击退、播命中音效。
        // 目标不可伤害时整体作废：原版对创造模式玩家 / `invulnerable` 实体根本不会派发
        // `EntityDamageEvent`，`observeDamage` 收不到取消信号，于是后面的护甲损耗、
        // 击退、命中音效都会错误发生。
        //
        // 为什么在调用前判断而不是看 `damage()` 的返回值：这个 API 版本里
        // `damage(double, Entity)` 返回 `Unit`（只有 `damage(double)` 返回 Boolean），
        // 带着射手调用时拿不到结果，所以必须提前判定。
        if (target.isInvulnerable || (target is Player && target.gameMode.isInvulnerable)) {
            devLog("[Gun] 命中目标不可伤害，跳过结算 target=${target.type} 伤害=$damage")
            return
        }

        val context = HitContext(target.uniqueId)
        settlements++
        val contexts = hitContexts.get()
        contexts.addLast(context)
        val outcome = try {
            if (plugin != null) {
                target.setMetadata(HIT_METADATA, FixedMetadataValue(plugin, true))
                target.setMetadata("chotentech_body_hit", FixedMetadataValue(plugin, hitPoint.clone()))
            }
            // 同 tick 的多颗弹丸（霰弹）会撞上原版无敌帧：LivingEntity.hurt() 在
            // noDamageTicks > invulnerableDuration/2 且 amount <= lastDamage 时直接返回、且
            // 不触发 EntityDamageEvent，于是第 2..N 颗弹丸完全无效（鹿弹 8×9 实际只结算 9）。
            // 每颗弹丸结算前清零无敌帧，让多弹丸按颗数叠加。
            if (target.noDamageTicks > 0) target.noDamageTicks = 0
            if (shooter != null) {
                target.damage(damage, shooter)
            } else {
                target.damage(damage)
            }
            !context.cancelled
        } catch (_: Exception) {
            false
        } finally {
            if (contexts.isNotEmpty() && contexts.last() === context) {
                contexts.removeLast()
            } else {
                contexts.remove(context)
            }
            if (contexts.isEmpty()) hitContexts.remove()
            try {
                if (plugin != null) {
                    target.removeMetadata(HIT_METADATA, plugin)
                    target.removeMetadata("chotentech_body_hit", plugin)
                }
            } catch (_: Exception) {
            }
        }
        // 此前已在调用前判定目标可伤害，所以这里只需看事件是否被取消。
        if (!outcome) {
            devLog(
                "[Gun] 命中结算被打断 target=${target.type} 伤害=$damage " +
                    "cancelled=${context.cancelled}（事件被其它监听器取消，或 damage() 抛异常）",
            )
            return
        }
        appliedCount++
        devLog("[Gun] 命中生效 target=${target.type} 伤害=${"%.2f".format(damage)} 暴击=$crit")

        // 只有伤害实际生效才消耗护甲耐久。
        ArmorManager.applyDurabilityLoss(target, part, mitigation.armorDurabilityLoss, covered)

        // 击退：沿弹道水平方向
        try {
            val knockback = GunSettings.damage.knockback
            if (knockback > 0.0) {
                val dir = org.bukkit.util.Vector(bullet.velocity.x, 0.0, bullet.velocity.z)
                if (dir.lengthSquared() > 1e-6) {
                    val impulse = dir.normalize().multiply(knockback)
                    target.velocity = target.velocity.clone().add(impulse)
                }
            }
        } catch (_: Exception) {
        }

        // 命中音效
        try {
            hitPoint.world?.playSound(hitPoint, GunSettings.sounds.hit, 1.0f, 1.0f)
        } catch (_: Exception) {
        }
    }
}
