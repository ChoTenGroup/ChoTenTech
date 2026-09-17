package io.github.zzzyyylllty.chotentech.damage

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.NamespacedKey
import org.bukkit.entity.*
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.nms.getItemTag

/**
 * 原版伤害映射 + 伤害类型判定监听器
 *
 * 在 AttackListener(HIGH) 之前运行 (LOWEST)：
 * 1. 检测伤害类型（通过原版原因映射、弹射物类型、玩家武器NBT/属性）
 * 2. 通过受害者的 PDC（PersistentDataContainer）标记伤害类型
 * 3. AttackListener 的扩展部分会读取此标记进行防御计算和效果应用
 *
 * 非实体伤害事件（火焰 tick、窒息等）同样在 LOWEST 处理。
 */
object CustomDamageListener {

    private val enabled: Boolean
        get() = ChoTenTech.config.getBoolean("damage-system.enabled", false)

    /** 原版伤害原因 → 类型ID 映射表（从 config 加载 + 内置默认） */
    private val damageCauseMapping: Map<EntityDamageEvent.DamageCause, String>
        get() {
            val map = mutableMapOf<EntityDamageEvent.DamageCause, String>()

            // 物理伤害
            map[EntityDamageEvent.DamageCause.ENTITY_ATTACK] = "physical"
            map[EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK] = "physical"
            map[EntityDamageEvent.DamageCause.PROJECTILE] = "physical"
            map[EntityDamageEvent.DamageCause.CONTACT] = "physical"
            map[EntityDamageEvent.DamageCause.SUFFOCATION] = "physical"
            map[EntityDamageEvent.DamageCause.FALL] = "physical"
            map[EntityDamageEvent.DamageCause.FLY_INTO_WALL] = "physical"
            map[EntityDamageEvent.DamageCause.FALLING_BLOCK] = "physical"
            map[EntityDamageEvent.DamageCause.CRAMMING] = "physical"
            map[EntityDamageEvent.DamageCause.THORNS] = "physical"
            map[EntityDamageEvent.DamageCause.BLOCK_EXPLOSION] = "physical"
            map[EntityDamageEvent.DamageCause.ENTITY_EXPLOSION] = "physical"

            // 火焰 / 灼烧
            map[EntityDamageEvent.DamageCause.FIRE] = "burn"
            map[EntityDamageEvent.DamageCause.FIRE_TICK] = "burn"
            map[EntityDamageEvent.DamageCause.LAVA] = "burn"
            map[EntityDamageEvent.DamageCause.HOT_FLOOR] = "burn"
            map[EntityDamageEvent.DamageCause.MELTING] = "burn"

            // 电击
            map[EntityDamageEvent.DamageCause.LIGHTNING] = "shock"
            map[EntityDamageEvent.DamageCause.SONIC_BOOM] = "shock"

            // 霜寒
            map[EntityDamageEvent.DamageCause.FREEZE] = "frost"

            // 侵蚀
            map[EntityDamageEvent.DamageCause.DROWNING] = "erosion"
            map[EntityDamageEvent.DamageCause.DRAGON_BREATH] = "erosion"

            // 衰退
            map[EntityDamageEvent.DamageCause.POISON] = "decay"
            map[EntityDamageEvent.DamageCause.WITHER] = "decay"
            map[EntityDamageEvent.DamageCause.STARVATION] = "decay"

            // 精神
            map[EntityDamageEvent.DamageCause.VOID] = "psychic"
            map[EntityDamageEvent.DamageCause.WORLD_BORDER] = "psychic"
            map[EntityDamageEvent.DamageCause.MAGIC] = "psychic"

            // 配置覆盖
            val section = ChoTenTech.config.getConfigurationSection("damage-system.vanilla-mapping") ?: return map
            for (key in section.getKeys(false)) {
                try {
                    val cause = EntityDamageEvent.DamageCause.valueOf(key.uppercase())
                    val value = section.getString(key, "") ?: ""
                    if (value.equals("none", ignoreCase = true)) {
                        map.remove(cause)
                    } else {
                        map[cause] = value
                    }
                } catch (_: IllegalArgumentException) { }
            }
            return map
        }

    // ==================== 主监听器 ====================

    

@SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!enabled) return
        if (event.isCancelled) return

        val damageType = resolveDamageType(event)
        if (damageType != null && DamageTypeRegistry.isActive(damageType.id)) {
            event.setDamageTypeTag(damageType.id)
        }
    }

    

@SubscribeEvent(priority = EventPriority.LOWEST)
    fun onNonEntityDamage(event: EntityDamageEvent) {
        if (!enabled) return
        if (event.isCancelled) return
        if (event is EntityDamageByEntityEvent) return

        val cause = event.cause
        val mapped = damageCauseMapping[cause]
        if (mapped != null && DamageTypeRegistry.isActive(mapped)) {
            event.setDamageTypeTag(mapped)
        }
    }

    

@SubscribeEvent(priority = EventPriority.MONITOR)
    fun onEntityDamageCleanup(event: EntityDamageEvent) {
        if (!enabled) return
        event.removeDamageTypeTag()
    }

    // ==================== 伤害类型解析 ====================

    private fun resolveDamageType(event: EntityDamageByEntityEvent): DamageType? {
        val cause = event.cause
        val damager = event.damager

        // 1. 原版伤害原因映射
        val mappedId = damageCauseMapping[cause]
        if (mappedId != null) return DamageType.fromId(mappedId)

        // 2. 弹射物类型映射
        when (damager) {
            is Fireball, is SmallFireball, is DragonFireball -> return DamageType.BURN
            is WitherSkull -> return DamageType.DECAY
        }

        // 3. 玩家攻击 → 通过武器 NBT / 属性判定
        val player = getAttackerPlayer(damager)
        if (player != null) return resolvePlayerDamageType(player)

        // 4. 非玩家攻击默认物理
        return DamageType.PHYSICAL
    }

    private fun resolvePlayerDamageType(player: Player): DamageType {
        // 武器 NBT damage_type 优先
        val weapon = player.inventory.itemInMainHand
        val nbtType = readDamageTypeFromNBT(weapon)
        if (nbtType != null && DamageTypeRegistry.isActive(nbtType.id)) return nbtType

        // 遍历已启用的类型，按优先级取第一个有 extra_damage 的
        val active = DamageTypeRegistry.getActive()
        val priority = active.sortedByDescending { it.type.ordinal } // PSYCHIC > ... > PHYSICAL
        for (rdt in active) {
            if (AttributeManager.getAttributeValue(player, rdt.extraDamageAttribute) > 0) {
                return rdt.type
            }
        }

        return DamageType.PHYSICAL
    }

    /**
     * 从物品 NBT chotenatb 中读取 damage_type
     */
    private fun readDamageTypeFromNBT(item: ItemStack?): DamageType? {
        if (item == null || item.type.isAir) return null
        return try {
            val tag = item.getItemTag()
            val chotenatbData = tag["chotenatb"] ?: return null
            for (entryTag in chotenatbData.asList()) {
                val compound = entryTag.asCompound() ?: continue
                val typeId = compound["damage_type"]?.asString() ?: continue
                DamageType.fromId(typeId)?.let { return it }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun getAttackerPlayer(damager: Any): Player? {
        return when (damager) {
            is Player -> damager
            is Projectile -> {
                val s = damager.shooter
                if (s is Player) s else null
            }
            is Fireball -> {
                val s = damager.shooter
                if (s is Player) s else null
            }
            else -> null
        }
    }
}

// ==================== PDC 工具 ====================

private val TYPE_KEY = NamespacedKey("choten", "damage_type")

fun EntityDamageEvent.getDamageTypeTag(): String? {
    return try {
        entity?.persistentDataContainer?.get(TYPE_KEY, PersistentDataType.STRING)
    } catch (_: Exception) { null }
}

fun EntityDamageEvent.setDamageTypeTag(id: String) {
    try {
        entity?.persistentDataContainer?.set(TYPE_KEY, PersistentDataType.STRING, id)
    } catch (_: Exception) {}
}

fun EntityDamageEvent.removeDamageTypeTag() {
    try {
        entity?.persistentDataContainer?.remove(TYPE_KEY)
    } catch (_: Exception) {}
}
