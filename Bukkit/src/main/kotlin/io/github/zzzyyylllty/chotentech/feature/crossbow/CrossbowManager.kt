package io.github.zzzyyylllty.chotentech.feature.crossbow

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.sertraline.Sertraline
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Configuration
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 箭矢配置（从 YAML 加载）
 */
data class ArrowData(
    val velocity: Double,
    val damage: Double,
    val penetrate: Double
)

/**
 * 弹匣配置（从 mags/ 加载）
 */
data class MagazineData(
    val id: String,
    val capacity: Int = 18
)

/**
 * 弩配置（从 crossbows/ 加载，预留扩展）
 */
data class CrossbowData(
    val id: String
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
 * 弩箭机制管理器
 * 负责：箭矢/弹匣/弩配置注册、弹匣 ID 存储、飞行中箭矢追踪
 */
object CrossbowManager {

    // ==================== Config Registries ====================

    private val arrowConfigs = ConcurrentHashMap<String, ArrowData>()
    private val magazineConfigs = ConcurrentHashMap<String, MagazineData>()
    private val crossbowConfigs = ConcurrentHashMap<String, CrossbowData>()

    fun getArrowData(id: String): ArrowData? = arrowConfigs[id]
    fun hasArrow(id: String): Boolean = arrowConfigs.containsKey(id)

    fun getMagazineConfig(id: String): MagazineData? = magazineConfigs[id]
    fun isKnownMagazine(id: String): Boolean = magazineConfigs.containsKey(id)

    fun getCrossbowConfig(id: String): CrossbowData? = crossbowConfigs[id]

    // ==================== Config Loading ====================

    /**
     * 从 arrows/ 目录加载箭矢 YAML 配置
     */
    fun loadArrowConfigs() {
        arrowConfigs.clear()
        val dir = File(getDataFolder(), "arrows")
        if (!dir.exists()) releaseResourceFolder("arrows")

        val files = dir.listFiles { f -> f.extension in listOf("yml", "yaml") } ?: return
        for (file in files) {
            try {
                val config = Configuration.loadFromFile(file)
                for (key in config.getKeys(false)) {
                    val sec = config.getConfigurationSection(key) ?: continue
                    val id = sec.getString("id") ?: continue
                    arrowConfigs[id] = ArrowData(
                        velocity = sec.getDouble("velocity", 1.0),
                        damage = sec.getDouble("damage", 10.0),
                        penetrate = sec.getDouble("penetrate", 1.0)
                    )
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * 从 mags/ 目录加载弹匣 YAML 配置
     */
    fun loadMagazineConfigs() {
        magazineConfigs.clear()
        val dir = File(getDataFolder(), "mags")
        if (!dir.exists()) releaseResourceFolder("mags")

        val files = dir.listFiles { f -> f.extension in listOf("yml", "yaml") } ?: return
        for (file in files) {
            try {
                val config = Configuration.loadFromFile(file)
                for (key in config.getKeys(false)) {
                    val sec = config.getConfigurationSection(key) ?: continue
                    val id = sec.getString("id") ?: continue
                    magazineConfigs[id] = MagazineData(
                        id = id,
                        capacity = sec.getInt("capacity", 18)
                    )
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * 从 crossbows/ 目录加载弩 YAML 配置（预留）
     */
    fun loadCrossbowConfigs() {
        crossbowConfigs.clear()
        val dir = File(getDataFolder(), "crossbows")
        if (!dir.exists()) releaseResourceFolder("crossbows")

        val files = dir.listFiles { f -> f.extension in listOf("yml", "yaml") } ?: return
        for (file in files) {
            try {
                val config = Configuration.loadFromFile(file)
                for (key in config.getKeys(false)) {
                    val sec = config.getConfigurationSection(key) ?: continue
                    val id = sec.getString("id") ?: continue
                    crossbowConfigs[id] = CrossbowData(id = id)
                }
            } catch (_: Exception) { }
        }
    }

    // ============ Detection ============

    /**
     * 通过 sertraline_id 检测物品是否为可装填箭矢
     */
    fun isArrowItem(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || item.amount <= 0) return false
        val id = Sertraline.api().getId(item) ?: return false
        return arrowConfigs.containsKey(id)
    }

    /**
     * 从物品读取箭矢数据（通过 sertraline_id 查配置）
     */
    fun getArrowDataFromItem(item: ItemStack): ArrowData? {
        val id = Sertraline.api().getId(item) ?: return null
        return arrowConfigs[id]
    }

    /**
     * 检测物品是否为弹匣：优先 sertraline_id，其次 cmag NBT（兼容旧物品）
     */
    fun isArrowMagazine(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || item.amount <= 0) return false
        val id = Sertraline.api().getId(item)
        if (id != null && magazineConfigs.containsKey(id)) return true
        return try {
            item.getItemTag()["cmag"] != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取弹匣容量：优先查找配置，否则默认 18
     */
    fun getMagazineCapacity(magazine: ItemStack): Int {
        val id = Sertraline.api().getId(magazine)
        if (id != null) {
            val magData = magazineConfigs[id]
            if (magData != null) return magData.capacity
        }
        return 18
    }

    // ============ Magazine NBT (ID-based) ============

    /**
     * 读取弹匣中的箭矢 ID 列表（按填装顺序）
     */
    fun getMagazineContents(magazine: ItemStack): List<String> {
        if (!isArrowMagazine(magazine)) return emptyList()
        return try {
            val tag = magazine.getItemTag()
            val cmag = tag["cmag"]?.asCompound() ?: return emptyList()
            val filledStr = cmag["filled"]?.asString() ?: return emptyList()
            filledStr.split(",").filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取弹匣里第一支箭的 ID
     */
    fun getFirstArrow(magazine: ItemStack): String? {
        return getMagazineContents(magazine).firstOrNull()
    }

    /**
     * 消耗第一支箭（移除 ID），返回新物品
     */
    fun consumeFirstArrow(magazine: ItemStack): ItemStack {
        val ids = getMagazineContents(magazine).toMutableList()
        if (ids.isEmpty()) return magazine
        ids.removeAt(0)
        return saveMagazineIds(magazine, ids)
    }

    /**
     * 设置弹匣内容物（存入箭矢 ID 列表）
     */
    fun setMagazineContents(magazine: ItemStack, arrowIds: List<String>): ItemStack {
        return saveMagazineIds(magazine, arrowIds)
    }

    /**
     * 将箭矢 ID 列表写入弹匣 NBT，保留其他 NBT
     */
    private fun saveMagazineIds(magazine: ItemStack, ids: List<String>): ItemStack {
        val result = magazine.clone().apply { amount = 1 }
        val existingTag = result.getItemTag()
        val newTag = ItemTag()
        for (entry in existingTag.entries) {
            if (entry.key != "cmag") {
                newTag[entry.key] = entry.value
            }
        }
        val filledStr = ids.joinToString(",")
        newTag.putDeep("cmag.filled", filledStr)
        return newTag.saveTo(result)
    }

    // ============ 飞行中箭矢追踪 ============

    /** 飞行中的箭矢 UUID -> 箭矢数据 */
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

    fun isHighDurabilityEnabled(): Boolean {
        return ChoTenTech.config.getBoolean("high-durability-mode.enabled", false)
    }

    fun getHighDurabilityRatio(): Double {
        return ChoTenTech.config.getDouble("high-durability-mode.ratio", 1.0)
    }

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

    // ============ 弹匣容量与装填 ============

    /**
     * 从玩家背包中寻找箭矢并装入弹匣（1发）
     * 通过 sertraline_id 识别箭矢
     */
    fun loadArrowFromInventory(player: Player, magazineSlot: Int): Boolean {
        val magazine = when {
            magazineSlot in 0..35 -> player.inventory.getItem(magazineSlot)
            magazineSlot == 40 -> player.inventory.itemInOffHand
            else -> return false
        } ?: return false

        if (!isArrowMagazine(magazine)) return false

        val contents = getMagazineContents(magazine)
        val capacity = getMagazineCapacity(magazine)
        if (contents.size >= capacity) {
            sendActionBar(player, "<yellow>弹匣已满")
            return false
        }

        for (i in 0 until 36) {
            if (i == magazineSlot) continue
            val item = player.inventory.getItem(i) ?: continue
            val arrowId = Sertraline.api().getId(item) ?: continue
            if (!arrowConfigs.containsKey(arrowId)) continue

            val newContents = contents + arrowId
            val newMag = setMagazineContents(magazine, newContents)
            player.inventory.setItem(magazineSlot, newMag)

            item.amount -= 1
            if (item.amount <= 0) {
                player.inventory.setItem(i, null)
            }
            player.updateInventory()
            return true
        }

        sendActionBar(player, "<red>没有可装填的箭矢")
        return false
    }

    // ============ 工具方法 ============

    fun buildArrowItem(arrowId: String, player: Player): ItemStack? {
        return Sertraline.api().buildItem(arrowId, player)
    }

    fun getFillPercent(arrowIds: List<String>, capacity: Int): Double {
        if (arrowIds.isEmpty()) return 0.0
        return arrowIds.size.toDouble() / capacity.toDouble()
    }

    fun getFillLevelText(percent: Double): String {
        return when {
            percent <= 0.0 -> "<red>空</red>"
            percent <= 0.25 -> "<gold>较少</gold>"
            percent <= 0.50 -> "<yellow>近一半</yellow>"
            percent <= 0.75 -> "<green>较多</green>"
            percent < 1.0 -> "<dark_green>几乎全满</dark_green>"
            else -> "<green>满弹</green>"
        }
    }

    fun getTopArrowName(player: Player, arrowIds: List<String>): String {
        val first = arrowIds.firstOrNull() ?: return "空"
        val item = Sertraline.api().buildItem(first, player)
        if (item != null) {
            val meta = item.itemMeta
            if (meta != null && meta.hasDisplayName()) return meta.displayName
        }
        return first
    }

    fun sendMagazineStatus(player: Player, magazine: ItemStack) {
        val arrowIds = getMagazineContents(magazine)
        val capacity = getMagazineCapacity(magazine)
        val percent = getFillPercent(arrowIds, capacity)
        val topArrow = getTopArrowName(player, arrowIds)
        val fillText = getFillLevelText(percent)
        sendActionBar(player, "<yellow>$topArrow<white>箭 <gray>|</gray> $fillText")
    }

    fun sendMagazineHint(player: Player) {
        sendActionBar(player, "<yellow>低头以装入箭矢，抬头以检视弹匣")
    }

    fun sendActionBar(player: Player, message: String) {
        try {
            (player as Audience).sendActionBar(mmUtil.deserialize(message.legacyToMiniMessage()))
        } catch (_: Exception) {}
    }
}
