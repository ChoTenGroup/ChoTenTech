package io.github.zzzyyylllty.chotentech.feature.crossbow

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import taboolib.module.nms.getItemTag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 飞行中箭矢的数据（原版弓 / 弩使用）。
 *
 * P2 之后**产生方**已经不是本插件（旧的「箭矢夹 + arrows/ 配置」体系已整体删除，
 * 弩改走 Sertraline `gun` 物品 + [io.github.zzzyyylllty.chotentech.feature.gun.GunShoot] 自研弹道）。
 * 本数据类与 [CrossbowManager.registerFlyingArrow] 作为**公开 API 保留**：
 * [io.github.zzzyyylllty.chotentech.listener.AttackListener] 仍在用它做
 * 「原版箭矢穿透 → 护甲额外耐久损耗」的结算，P5 的 `ArmorManager` 接手之前不能动。
 */
data class ArrowData(
    val velocity: Double,
    val damage: Double,
    val penetrate: Double
)

/**
 * 穿透计算结果
 */
data class PenetrationResult(
    val finalDamage: Double,
    val extraDurability: Double,
    val effectiveDefense: Double
)

/**
 * 弩箭机制的**残留管理层**（P2 瘦身后）。
 *
 * ### P2 删掉的部分
 * 旧弩/弹匣体系（`arrows/` `mags/` `crossbows/` 三套 YAML 配置、`cmag` NBT、
 * 箭矢夹 GUI、低头装填/抬头检视、原版弩发射拦截）已整体迁移到新的枪械系统，
 * 对应成员全部移除：`ArrowData` 之外的 `MagazineData` / `CrossbowData`、
 * `arrowConfigs` / `magazineConfigs` / `crossbowConfigs` 与三个 `load*Configs()`、
 * `isArrowItem` / `getArrowDataFromItem` / `isArrowMagazine` / `getMagazineCapacity`、
 * `getMagazineContents` / `setMagazineContents` / `getFirstArrow` / `consumeFirstArrow`、
 * `loadArrowFromInventory` / `buildArrowItem` / `getFillPercent` / `getFillLevelText` /
 * `getTopArrowName` / `sendMagazineStatus` / `sendMagazineHint`（含它们专用的 `sendActionBar`）。
 *
 * ### 保留的部分（**一个签名都不能改**）
 * [AttackListener][io.github.zzzyyylllty.chotentech.listener.AttackListener] 仍然依赖：
 * `getFlyingArrowData` / `removeFlyingArrow` / `getEntityDefense` / `calculatePenetration` /
 * `applyExtraDurabilityDamage` / `isHighDurabilityEnabled` / `getHighDurabilityRatio` /
 * `applyHighDurabilityDamage` / `PenetrationResult`，以及它们内部用到的
 * `collectDefenseBySlot` / `invalidateDefenseCache` / `damageItem` / `readDefenseFromArmor` / 槽位映射。
 * 这一段是「原版弓弩的穿透与护甲耐久」逻辑，P5 的 `ArmorManager` 落地之前必须继续工作。
 *
 * ### 废弃但保留的配置键（**不要**去改 `config.yml`，那是 GBK 历史文件）
 * - `crossbow-mechanism.penetration-durability-ratio`：穿透溢出 → 额外耐久的系数，
 *   仍由 `AttackListener` 直接读取（[getPenetrationDurabilityRatio] 是它的取值封装）。
 * - `high-durability-mode.enabled` / `high-durability-mode.ratio`：高耐久模式开关与系数。
 * - `crossbow-mechanism.*` 里的其它键（旧弩机制）**已废弃**，代码不再读取。
 * 这些键目前实际落在 `attribute-config.yml`（`ChoTenTech.config` 合并加载）；`config.yml` 里没有对应节点，
 * 所以两个开关默认都是关闭状态。
 */
object CrossbowManager {

    // ============ 飞行中箭矢追踪 ============

    /**
     * 飞行中的箭矢 UUID -> 箭矢数据。
     *
     * 写入方目前只有 [registerFlyingArrow] 的调用方（P2 之后本插件内部暂无调用点，
     * 旧弩装填系统是唯一写入方）。**读取方** `AttackListener` 仍在用，所以注册表与清理
     * 逻辑（[FlyingArrowListener]）一并保留，避免有人重新注册后 map 泄漏。
     */
    private val flyingArrows = ConcurrentHashMap<UUID, ArrowData>()

    fun registerFlyingArrow(entityId: UUID, data: ArrowData) {
        flyingArrows[entityId] = data
    }

    fun getFlyingArrowData(entityId: UUID): ArrowData? {
        return flyingArrows[entityId]
    }

    fun removeFlyingArrow(entityId: UUID) {
        flyingArrows.remove(entityId)
    }

    fun clearFlyingArrows() {
        flyingArrows.clear()
    }

    /** 当前登记的飞行箭矢数量（调试用）。 */
    fun flyingArrowCount(): Int = flyingArrows.size

    // ============ 生物防御计算 ============

    fun getEntityDefense(victim: LivingEntity): Double {
        return AttributeManager.getAttributeValue(victim, "defense")
    }

    // ============ 穿透/防御/耐久度 ============

    fun calculatePenetration(
        incomingDamage: Double,
        penetrate: Double,
        targetDefense: Double,
        durabilityRatio: Double = 1.0
    ): PenetrationResult {
        when {
            penetrate < targetDefense -> {
                val effectiveDefense = targetDefense - penetrate
                val finalDamage = (incomingDamage - effectiveDefense).coerceAtLeast(0.0)
                return PenetrationResult(finalDamage, 0.0, effectiveDefense)
            }
            penetrate == targetDefense -> {
                return PenetrationResult(incomingDamage, 0.0, 0.0)
            }
            else -> {
                val overflow = penetrate - targetDefense
                val extraDurability = overflow * durabilityRatio
                return PenetrationResult(incomingDamage, extraDurability, 0.0)
            }
        }
    }

    /** `high-durability-mode.enabled`（保留键，默认关闭）。 */
    fun isHighDurabilityEnabled(): Boolean {
        return ChoTenTech.config.getBoolean("high-durability-mode.enabled", false)
    }

    /** `high-durability-mode.ratio`（保留键，默认 1.0）。 */
    fun getHighDurabilityRatio(): Double {
        return ChoTenTech.config.getDouble("high-durability-mode.ratio", 1.0)
    }

    /** `crossbow-mechanism.penetration-durability-ratio`（保留键，默认 1.0）。 */
    fun getPenetrationDurabilityRatio(): Double {
        return ChoTenTech.config.getDouble("crossbow-mechanism.penetration-durability-ratio", 1.0)
    }

    fun applyHighDurabilityDamage(
        player: Player,
        incomingDamage: Double,
        ratio: Double
    ) {
        val defenseBySlot = collectDefenseBySlot(player)
        if (defenseBySlot.isEmpty()) return

        val totalDefense = defenseBySlot.values.sum()
        if (totalDefense <= 0) return

        val slotArmorMap = mapOf(
            AttributeSlot.HEAD to player.inventory.helmet,
            AttributeSlot.CHEST to player.inventory.chestplate,
            AttributeSlot.LEGS to player.inventory.leggings,
            AttributeSlot.FEET to player.inventory.boots
        )

        for ((slot, defense) in defenseBySlot) {
            val armor = slotArmorMap[slot] ?: continue
            if (armor == null || armor.type.isAir) continue
            val percentage = defense / totalDefense
            val durabilityLoss = (incomingDamage * percentage * ratio).toInt()
            if (durabilityLoss > 0) {
                damageItem(armor, durabilityLoss)
            }
        }
    }

    // ============ Defense-by-slot 缓存 ============

    private val defenseBySlotCache = ConcurrentHashMap<UUID, Map<AttributeSlot, Double>>()
    private val defenseBySlotCacheTimestamps = ConcurrentHashMap<UUID, Long>()
    private const val DEFENSE_CACHE_TTL = 30_000L

    fun collectDefenseBySlot(player: Player): Map<AttributeSlot, Double> {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val lastUpdate = defenseBySlotCacheTimestamps[uuid] ?: 0L
        if (now - lastUpdate <= DEFENSE_CACHE_TTL) {
            val cached = defenseBySlotCache[uuid]
            if (cached != null) return cached
        }
        return computeAndCacheDefenseBySlot(player)
    }

    private fun computeAndCacheDefenseBySlot(player: Player): Map<AttributeSlot, Double> {
        val result = mutableMapOf<AttributeSlot, Double>()

        val slotArmorMap = mapOf(
            AttributeSlot.HEAD to player.inventory.helmet,
            AttributeSlot.CHEST to player.inventory.chestplate,
            AttributeSlot.LEGS to player.inventory.leggings,
            AttributeSlot.FEET to player.inventory.boots
        )

        for ((slot, armor) in slotArmorMap) {
            if (armor == null || armor.type.isAir) continue
            val defense = readDefenseFromArmor(armor, slot)
            if (defense > 0) {
                result[slot] = (result[slot] ?: 0.0) + defense
            }
        }

        defenseBySlotCache[player.uniqueId] = result
        defenseBySlotCacheTimestamps[player.uniqueId] = System.currentTimeMillis()
        return result
    }

    fun invalidateDefenseCache(uuid: UUID) {
        defenseBySlotCache.remove(uuid)
        defenseBySlotCacheTimestamps.remove(uuid)
    }

    private fun readDefenseFromArmor(armor: ItemStack, expectedSlot: AttributeSlot): Double {
        return try {
            val tag = armor.getItemTag()
            val chotenatbData = tag["chotenatb"] ?: return 0.0
            val list = chotenatbData.asList()
            var totalDefense = 0.0
            for (entryTag in list) {
                val compound = entryTag.asCompound() ?: continue
                val slotName = compound["slot"]?.asString() ?: "ANY"
                if (!slotMatchesAttributeSlot(slotName, expectedSlot)) continue
                val attrCompound = compound["attribute"]?.asCompound() ?: continue
                totalDefense += attrCompound["defense"]?.asDouble() ?: 0.0
            }
            totalDefense
        } catch (_: Exception) {
            0.0
        }
    }

    private fun slotMatchesAttributeSlot(slotName: String, expected: AttributeSlot): Boolean {
        if (slotName == "ANY" || slotName == "ALL") return true
        val resolved = resolveSlotName(slotName) ?: return false
        return resolved == expected
    }

    private fun resolveSlotName(name: String): AttributeSlot? {
        return when (name.uppercase()) {
            "HEAD", "HELMET" -> AttributeSlot.HEAD
            "CHEST", "CHESTPLATE" -> AttributeSlot.CHEST
            "LEGS", "LEGGINGS" -> AttributeSlot.LEGS
            "FEET", "BOOTS", "FOOT" -> AttributeSlot.FEET
            "MAINHAND", "MAIN_HAND" -> AttributeSlot.MAINHAND
            "OFFHAND", "OFF_HAND" -> AttributeSlot.OFFHAND
            "INVENTORY" -> AttributeSlot.INVENTORY
            "OTHER" -> AttributeSlot.OTHER
            else -> null
        }
    }

    private fun damageItem(item: ItemStack, amount: Int) {
        val meta = item.itemMeta
        if (meta is Damageable) {
            val current = meta.damage
            val maxDurability = item.type.maxDurability.toInt()
            if (maxDurability <= 0) return
            val newDamage = current + amount
            if (newDamage >= maxDurability) {
                item.amount = 0
                item.type = org.bukkit.Material.AIR
            } else {
                meta.damage = newDamage
                item.itemMeta = meta
            }
        }
    }

    fun applyExtraDurabilityDamage(player: Player, extraDurability: Double) {
        if (extraDurability <= 0) return
        val durabilityAmount = extraDurability.toInt().coerceAtLeast(1)

        val armors = listOfNotNull(
            player.inventory.helmet,
            player.inventory.chestplate,
            player.inventory.leggings,
            player.inventory.boots
        ).filter { !it.type.isAir }

        if (armors.isEmpty()) return

        val perArmor = durabilityAmount / armors.size
        var remainder = durabilityAmount % armors.size

        for (armor in armors) {
            var dmg = perArmor
            if (remainder > 0) {
                dmg++
                remainder--
            }
            if (dmg > 0) {
                damageItem(armor, dmg)
            }
        }
    }
}
