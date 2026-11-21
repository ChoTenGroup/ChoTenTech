package io.github.zzzyyylllty.chotentech.feature.spear

import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.damage.DamageType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import kotlin.math.abs

/**
 * 长矛冲锋（Spear Charge）属性支持。
 *
 * 原版长矛（1.21.11+）右键蓄力进入冲锋姿态；冲锋命中伤害由冲击速度决定
 * （`minecraft:kinetic_weapon` 组件在 `ItemStack#onUseTick` 中结算）。本插件在此基础上提供两个属性：
 *
 * - [ATTR_CHARGE_LEVEL] `spear_charge_level` 长矛穿刺强度：
 *   冲锋命中伤害的**独立乘区**，最终伤害 = 原伤害 × 该值（默认 1.0，不改变原版数值）。
 *   在 [onSpearChargeHit] 里以 HIGHEST 优先级统一乘算，排在伤害减免 / 闪避格挡之后，
 *   因此不会和物理加成、减免等其它乘区互相叠加（是独立的一层）。
 * - [ATTR_CHARGE_SPEED] `spear_charge_speed` 长矛冲锋速度：
 *   手持长矛进入冲锋（使用中）状态时，按百分比提升移动速度（0.25 = +25%，默认 0%）。
 *   通过 `MOVEMENT_SPEED` 的乘法修饰符实现，退出冲锋立刻移除；不改 walkSpeed，
 *   避免和 1.21.2+ 的 fovModifier / 枪械瞄准等机制打架。1
 *
 * 冲锋状态判定：`LivingEntity#hasActiveItem()` 为真且使用中的物品属于 `minecraft:spears`
 * 物品标签（木/石/铜/金/铁/钻石/下界合金长矛）。周期性轮询而不是监听右键事件，
 * 因为蓄力可以被武器切换、受伤、死亡、下马等多种方式打断，轮询状态最稳。
 *
 * ### 为什么要区分「冲锋」和「戳刺」
 * 长矛物品把 `minecraft:damage_type` 组件固定成 `minecraft:spear`，所以戳刺（jab）
 * 和冲锋命中的伤害类型**都是** `minecraft:spear`。只有冲锋是在「使用中」（蓄力）状态下
 * 由 `onUseTick` 结算的，所以伤害监听器必须额外要求攻击者此刻正手持长矛蓄力，
 * 否则戳刺也会被当成冲锋吃加成。
 */
object SpearChargeManager {

    /** 长矛穿刺强度：冲锋命中伤害的独立乘区，默认 1.0 */
    const val ATTR_CHARGE_LEVEL = "spear_charge_level"

    /** 长矛冲锋速度：手持长矛冲锋时增加的移动速度（百分比，默认 0） */
    const val ATTR_CHARGE_SPEED = "spear_charge_speed"

    /** 冲锋加速修饰符的 key（必须用 key 而不是旧版 name/UUID 构造器，否则无法可靠移除） */
    private val SPEED_MODIFIER_KEY = NamespacedKey("chotentech", "spear_charge_speed")

    private var tickTask: PlatformExecutor.PlatformTask? = null

    // ==================== 生命周期 ====================

    @Awake(LifeCycle.ACTIVE)
    fun start() {
        tickTask = submit(period = 2L) { tick() }
    }

    @Awake(LifeCycle.DISABLE)
    fun stop() {
        tickTask?.cancel()
        tickTask = null
        for (player in Bukkit.getOnlinePlayers()) {
            clearSpeed(player)
        }
    }

    // ==================== 冲锋状态 ====================

    /**
     * 是否正在手持长矛冲锋：`hasActiveItem()` 为真（正在使用物品）且使用中的物品是长矛。
     *
     * 玩家和生物都走同一条判断：原版冲锋伤害由 `ItemStack#onUseTick` 结算，只有在
     * 「使用中」状态才会发生，所以这个判断能可靠地把冲锋和戳刺区分开。
     */
    fun isChargingWithSpear(entity: LivingEntity): Boolean {
        if (entity.isDead || !entity.isValid) return false
        if (entity is Player && !entity.isOnline) return false
        if (!entity.hasActiveItem()) return false
        return isSpear(entity.activeItem.type)
    }

    /** 是否为长矛物品（走原版 `minecraft:spears` 物品标签，自动覆盖全部材质） */
    fun isSpear(type: Material): Boolean = Tag.ITEMS_SPEARS.isTagged(type)

    // ==================== 移动速度 ====================

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.isDead) {
                clearSpeed(player)
                continue
            }
            val bonus = if (isChargingWithSpear(player)) {
                AttributeManager.getAttributeValue(player, ATTR_CHARGE_SPEED).coerceAtLeast(0.0)
            } else {
                0.0
            }
            applySpeed(player, bonus)
        }
    }

    /**
     * 把冲锋加速写到 `MOVEMENT_SPEED` 上（幂等）：
     * - `bonus <= 0`：移除本系统的修饰符
     * - 已有且数值一致：什么都不做
     * - 数值变化：先移除再按新值添加，避免同 key 叠加
     */
    private fun applySpeed(player: Player, bonus: Double) {
        val attribute = try {
            player.getAttribute(Attribute.MOVEMENT_SPEED)
        } catch (_: Exception) {
            null
        } ?: return

        val existing = attribute.modifiers.firstOrNull { it.key == SPEED_MODIFIER_KEY }

        if (bonus <= 1e-9) {
            if (existing != null) {
                runCatching { attribute.removeModifier(SPEED_MODIFIER_KEY) }
            }
            return
        }

        if (existing != null && abs(existing.amount - bonus) < 1e-9) {
            return
        }

        if (existing != null) {
            runCatching { attribute.removeModifier(SPEED_MODIFIER_KEY) }
        }
        runCatching {
            attribute.addModifier(
                org.bukkit.attribute.AttributeModifier(
                    SPEED_MODIFIER_KEY,
                    bonus,
                    org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                )
            )
        }
    }

    /** 清掉某个玩家的冲锋加速（退出 / 死亡 / 关服） */
    fun clearSpeed(player: Player) {
        val attribute = try {
            player.getAttribute(Attribute.MOVEMENT_SPEED)
        } catch (_: Exception) {
            null
        } ?: return
        if (attribute.modifiers.any { it.key == SPEED_MODIFIER_KEY }) {
            runCatching { attribute.removeModifier(SPEED_MODIFIER_KEY) }
        }
    }
}

/**
 * 长矛冲锋命中：应用「长矛穿刺强度」独立乘区。
 *
 * 用 HIGHEST 优先级，等其它监听器（AttackListener 的物理/元素加成、防御减免、闪避格挡）
 * 都改完 `event.damage` 之后再乘，保证这是一个真正独立的乘区。
 *
 * 判定条件：
 * 1. 伤害类型是 `minecraft:spear`（`DamageType.SPEAR`）。
 * 2. 攻击者必须仍处于冲锋姿态（使用中的物品是长矛）。
 *
 * 长矛物品把 `minecraft:damage_type` 固定成 `minecraft:spear`，所以戳刺（jab）也会带这个
 * 伤害类型；冲锋伤害只在 `ItemStack#onUseTick`（使用中）里结算，因此第 2 条才是
 * 「冲锋命中」与「戳刺」的真正分界线。
 */
@SubscribeEvent(priority = EventPriority.HIGHEST)
fun onSpearChargeHit(event: EntityDamageByEntityEvent) {
    if (event.isCancelled) return
    if (event.damageSource.damageType != DamageType.SPEAR) return

    val attacker = event.damager as? LivingEntity ?: return
    if (!SpearChargeManager.isChargingWithSpear(attacker)) return
    val multiplier = AttributeManager.getAttributeValue(attacker, SpearChargeManager.ATTR_CHARGE_LEVEL)
        .coerceAtLeast(0.0)
    if (multiplier == 1.0) return

    event.damage = (event.damage * multiplier).coerceAtLeast(0.0)
}

/** 玩家退出时清掉可能残留的冲锋加速修饰符 */
@SubscribeEvent
fun onSpearChargeQuit(event: PlayerQuitEvent) {
    SpearChargeManager.clearSpeed(event.player)
}
