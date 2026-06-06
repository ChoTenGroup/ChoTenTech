package io.github.zzzyyylllty.chotentech.attribute.crossbow

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.attribute.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.attribute.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.attribute.util.mmUtil
import io.github.zzzyyylllty.chotentech.attribute.util.minimessage.legacyToMiniMessage
import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import taboolib.common.platform.function.submit
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.getItemTag
import taboolib.module.nms.setItemTag
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 弩箭机制核心数据类
 * 箭矢的NBT数据: carrow: {velocity: 1, damage: 10, penetrate: 1}
 */
data class ArrowData(
    val velocity: Double,
    val damage: Double,
    val penetrate: Double
) {
    companion object {
        fun fromItemTag(data: ItemTagData): ArrowData? {
            try {
                val compound = data.asCompound() ?: return null
                return ArrowData(
                    velocity = compound["velocity"]?.asDouble() ?: 1.0,
                    damage = compound["damage"]?.asDouble() ?: 10.0,
                    penetrate = compound["penetrate"]?.asDouble() ?: 1.0
                )
            } catch (_: Exception) {
                return null
            }
        }

        fun toItemTag(data: ArrowData): ItemTag {
            return ItemTag().apply {
                this["velocity"] = data.velocity
                this["damage"] = data.damage
                this["penetrate"] = data.penetrate
            }
        }
    }
}

/**
 * 弩箭机制管理器
 * 负责：箭矢夹检测、箭矢装填、物品序列化、飞行中箭矢追踪
 */
object CrossbowManager {

    /** 飞行中的箭矢 -> 自定义箭矢数据映射 */
    private val flyingArrows = ConcurrentHashMap<UUID, ArrowData>()

    // ============ Config ============

    fun isEnabled(): Boolean {
        return ChoTenTech.config.getBoolean("crossbow-mechanism.enabled", false)
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

    // ============ ItemTag 检测 ============

    /**
     * 检测物品是否为箭矢夹（含有 cmag ItemTag）
     */
    fun isArrowMagazine(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || item.amount <= 0) return false
        return try {
            val tag = item.getItemTag()
            tag["cmag"] != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检测物品是否为可装填箭矢（含有 carrow NBT）
     */
    fun isArrowItem(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || item.amount <= 0) return false
        return try {
            val tag = item.getItemTag()
            tag["carrow"] != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从物品读取箭矢数据
     */
    fun getArrowDataFromItem(item: ItemStack): ArrowData? {
        if (!isArrowItem(item)) return null
        return try {
            val tag = item.getItemTag()
            val carrowTag = tag["carrow"] ?: return null
            ArrowData.fromItemTag(carrowTag)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取箭矢夹的填装内容（按填装顺序返回物品列表）
     */
    fun getMagazineContents(magazine: ItemStack): List<ItemStack> {
        if (!isArrowMagazine(magazine)) return emptyList()
        return try {
            val tag = magazine.getItemTag()
            val cmag = tag["cmag"]?.asCompound() ?: return emptyList()
            val itemsTag = cmag["items"]?.asCompound() ?: return emptyList()
            val filledStr = cmag["filled"]?.asString() ?: return emptyList()

            val itemsMap = mutableMapOf<String, ItemStack>()
            for (entry in itemsTag.entries) {
                val serialized = entry.value.asString() ?: continue
                val item = deserializeItemStack(serialized) ?: continue
                itemsMap[entry.key] = item
            }

            filledStr.split(",").filter { it.isNotEmpty() }.mapNotNull { key ->
                itemsMap[key]
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 获取箭矢夹里第一支箭
     */
    fun getFirstArrow(magazine: ItemStack): ItemStack? {
        val contents = getMagazineContents(magazine)
        return contents.firstOrNull()
    }

    /**
     * 消耗箭矢夹里的第一支箭（射完后调用）
     * 返回消耗后的箭矢夹物品（需要写回玩家背包）
     * 保留物品原有的其他 NBT 数据（显示名称、lore、附魔等）
     */
    fun consumeFirstArrow(magazine: ItemStack): ItemStack {
        if (!isArrowMagazine(magazine)) return magazine
        try {
            val tag = magazine.getItemTag()
            val cmag = tag["cmag"]?.asCompound() ?: return magazine
            val itemsTag = cmag["items"]?.asCompound() ?: return magazine
            val filledStr = cmag["filled"]?.asString() ?: return magazine

            if (filledStr.isEmpty()) return magazine

            val filledList = filledStr.split(",").toMutableList()
            if (filledList.isEmpty()) return magazine
            filledList.removeAt(0)
            val newFilledStr = filledList.joinToString(",")

            // 清理无引用的序列化物品
            val remainingKeys = filledList.toSet()
            val result = magazine.clone().apply { amount = 1 }
            val existingTag = result.getItemTag()
            val newTag = ItemTag()
            // 保留除 cmag 外的所有现有 NBT 数据
            for (existingEntry in existingTag.entries) {
                if (existingEntry.key != "cmag") {
                    newTag[existingEntry.key] = existingEntry.value
                }
            }
            for (entry in itemsTag.entries) {
                if (entry.key in remainingKeys) {
                    newTag.putDeep("cmag.items.${entry.key}", entry.value.asString() ?: continue)
                }
            }
            newTag.putDeep("cmag.filled", newFilledStr)

            return newTag.saveTo(result)
        } catch (_: Exception) {
            return magazine
        }
    }

    /**
     * 设置箭矢夹的内容物（存入NBT）
     * 使用MAP结构存储：crag: {items: {a: "序列化A", b: "序列化B"}, filled: [a, a, b, b, a]}
     * 保留物品原有的其他 NBT 数据（显示名称、lore、附魔等）
     */
    fun setMagazineContents(magazine: ItemStack, arrows: List<ItemStack>): ItemStack {
        val serializedMap = mutableMapOf<String, String>()

        // 去重：相同物品复用同一个 key
        val contentKeys = mutableListOf<String>()
        for (arrow in arrows) {
            val serialized = serializeItemStack(arrow)
            var foundKey: String? = null
            for ((key, value) in serializedMap) {
                if (value == serialized) {
                    foundKey = key
                    break
                }
            }
            if (foundKey == null) {
                val newKey = ('a'..'z').firstOrNull { it.toString() !in serializedMap.keys }?.toString()
                    ?: "z"
                serializedMap[newKey] = serialized
                foundKey = newKey
            }
            contentKeys.add(foundKey!!)
        }

        val filledStr = contentKeys.joinToString(",")

        val result = magazine.clone().apply { amount = 1 }
        val existingTag = result.getItemTag()

        val newTag = ItemTag()
        // 保留除 cmag 外的所有现有 NBT 数据
        for (existingEntry in existingTag.entries) {
            if (existingEntry.key != "cmag") {
                newTag[existingEntry.key] = existingEntry.value
            }
        }
        for ((key, value) in serializedMap) {
            newTag.putDeep("cmag.items.$key", value)
        }
        newTag.putDeep("cmag.filled", filledStr)
        return newTag.saveTo(result)
    }

    // ============ 飞行中箭矢追踪 ============

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

    // ============ 物品序列化 ============

    fun serializeItemStack(item: ItemStack): String {
        ByteArrayOutputStream().use { baos ->
            BukkitObjectOutputStream(baos).use { oos ->
                oos.writeObject(item.serialize())
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }
    }

    fun deserializeItemStack(data: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(data)
            ByteArrayInputStream(bytes).use { bais ->
                BukkitObjectInputStream(bais).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    val map = ois.readObject() as Map<String, Any>
                    ItemStack.deserialize(map)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============ 生物防御计算 ============

    /**
     * 获取生物的防御值（委托给 AttributeManager 的实体缓存）
     */
    fun getEntityDefense(victim: LivingEntity): Double {
        return AttributeManager.getAttributeValue(victim, "defense")
    }

    // ============ 穿透/防御/耐久度 ============

    /**
     * 计算穿透后的实际伤害
     * 规则：
     * - 穿透 < 防御：防御减去穿透后生效
     * - 穿透 == 防御：正常伤害
     * - 穿透 > 防御：正常伤害 + 额外耐久度损耗
     */
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

    /**
     * 高耐久度模式：根据护甲defense分布额外扣除耐久
     * 汇总各 AttributeSlot 的 defense 值，按占比分配耐久度损耗
     */
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

    /** 玩家UUID -> 各槽位defense值缓存 */
    private val defenseBySlotCache = ConcurrentHashMap<UUID, Map<AttributeSlot, Double>>()
    /** 缓存时间戳 */
    private val defenseBySlotCacheTimestamps = ConcurrentHashMap<UUID, Long>()
    /** 缓存 TTL（毫秒） */
    private const val DEFENSE_CACHE_TTL = 30_000L
    /** 缓存最大条目数 */
    private const val DEFENSE_CACHE_MAX_SIZE = 500

    /**
     * 初始化缓存定期刷新任务（由 ChoTenAttributes.onEnable() 调用）
     */
    fun startDefenseCacheTask() {
        submit(async = true, period = 600L) { // 30 秒
            refreshDefenseCache()
        }
    }

    /**
     * 从玩家护甲汇总各槽位的defense值（带缓存）
     * 返回 AttributeSlot -> defense值 的映射
     */
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

    /**
     * 强制重新计算并缓存玩家的defense槽位分布
     */
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

    /**
     * 刷新所有在线玩家的defense缓存（定期异步调用）
     */
    private fun refreshDefenseCache() {
        val now = System.currentTimeMillis()
        for ((uuid, _) in defenseBySlotCache) {
            val lastUpdate = defenseBySlotCacheTimestamps[uuid] ?: 0L
            if (now - lastUpdate < DEFENSE_CACHE_TTL) continue

            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline) {
                invalidateDefenseCache(uuid)
                continue
            }
            computeAndCacheDefenseBySlot(player)
        }
        // 淘汰溢出条目
        if (defenseBySlotCache.size > DEFENSE_CACHE_MAX_SIZE) {
            val overflow = defenseBySlotCache.size - DEFENSE_CACHE_MAX_SIZE
            val toEvict = defenseBySlotCacheTimestamps.entries
                .sortedBy { it.value }
                .take(overflow)
                .map { it.key }
            for (uuid in toEvict) {
                invalidateDefenseCache(uuid)
            }
        }
    }

    /**
     * 清除指定玩家的defense缓存
     */
    fun invalidateDefenseCache(uuid: UUID) {
        defenseBySlotCache.remove(uuid)
        defenseBySlotCacheTimestamps.remove(uuid)
    }

    /**
     * 从单件护甲的chotenatb中读取属于指定AttributeSlot的defense值
     * chotenatb 格式: [{attribute: {defense: 30, ...}, slot: CHEST}, ...]
     * slot 字段与 AttributeSlot 的映射：HEAD/HELMET→HEAD, CHEST/CHESTPLATE→CHEST, 等
     */
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

    /**
     * 将chotenatb中的slot字符串与AttributeSlot进行匹配
     */
    private fun slotMatchesAttributeSlot(slotName: String, expected: AttributeSlot): Boolean {
        if (slotName == "ANY" || slotName == "ALL") return true
        val resolved = resolveSlotName(slotName) ?: return false
        return resolved == expected
    }

    /**
     * 将chotenatb中的slot字符串解析为AttributeSlot
     */
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

    /**
     * 损耗物品耐久
     */
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

    /**
     * 应用额外耐久度损耗到玩家护甲（穿透溢出时）
     */
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
     * 弹匣最大容量
     */
    const val MAGAZINE_CAPACITY = 18

    /**
     * 从玩家背包中寻找箭矢并装入弹匣（1发）
     * @return true 成功装填，false 无箭矢或弹匣已满
     */
    fun loadArrowFromInventory(player: Player, magazineSlot: Int): Boolean {
        val magazine = when {
            magazineSlot in 0..35 -> player.inventory.getItem(magazineSlot)
            magazineSlot == 40 -> player.inventory.itemInOffHand
            else -> return false
        } ?: return false

        if (!isArrowMagazine(magazine)) return false

        val contents = getMagazineContents(magazine)
        if (contents.size >= MAGAZINE_CAPACITY) {
            sendActionBar(player, "<yellow>弹匣已满")
            return false
        }

        // 在玩家背包中寻找箭矢（跳过弹匣所在槽位和副手）
        for (i in 0 until 36) {
            if (i == magazineSlot) continue
            val item = player.inventory.getItem(i) ?: continue
            if (!isArrowItem(item) || item.amount <= 0) continue

            val arrowToAdd = item.clone().apply { amount = 1 }
            val newContents = contents + arrowToAdd
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

    /**
     * 获取弹匣填充百分比
     */
    fun getFillPercent(contents: List<ItemStack>): Double {
        if (contents.isEmpty()) return 0.0
        return contents.size.toDouble() / MAGAZINE_CAPACITY.toDouble()
    }

    /**
     * 获取填充等级文本（空/较少/近一半/较多/几乎全满/满弹）
     */
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

    /**
     * 获取弹匣顶部箭矢的名称
     */
    fun getTopArrowName(contents: List<ItemStack>): String {
        val first = contents.firstOrNull() ?: return "空"
        val meta = first.itemMeta
        return if (meta != null && meta.hasDisplayName()) {
            meta.displayName
        } else {
            val typeName = first.type.name.lowercase().replace('_', ' ')
            typeName.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * 发送弹匣状态到动作栏
     */
    fun sendMagazineStatus(player: Player, magazine: ItemStack) {
        val contents = getMagazineContents(magazine)
        val percent = getFillPercent(contents)
        val topArrow = getTopArrowName(contents)
        val fillText = getFillLevelText(percent)
        sendActionBar(player, "<yellow>$topArrow<white>箭 <gray>|</gray> $fillText")
    }

    /**
     * 发送操作提示到动作栏
     */
    fun sendMagazineHint(player: Player) {
        sendActionBar(player, "<yellow>低头以装入箭矢，抬头以检视弹匣")
    }

    /**
     * 发送 MiniMessage 格式的动作栏消息
     */
    fun sendActionBar(player: Player, message: String) {
        try {
            (player as Audience).sendActionBar(mmUtil.deserialize(message.legacyToMiniMessage()))
        } catch (_: Exception) {}
    }
}

/**
 * 穿透计算结果
 */
data class PenetrationResult(
    val finalDamage: Double,
    val extraDurability: Double,
    val effectiveDefense: Double
)
