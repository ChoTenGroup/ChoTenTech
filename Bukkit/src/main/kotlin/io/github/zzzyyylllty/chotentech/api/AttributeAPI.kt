package io.github.zzzyyylllty.chotentech.attribute.api // legacy API

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.AttributeSlot
import io.github.zzzyyylllty.chotentech.data.AttributeType
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.data.RegistrationSource
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 属性系统外部接口
 */
interface ChoTenAttributeAPI {

    /**
     * 获取玩家当前的最终属性值（从缓存读取，性能极高）
     * @param player 玩家
     * @param attributeId 属性 ID (如 "attack_damage")
     * @return 最终数值
     */
    fun getAttributeValue(player: Player, attributeId: String): Double

    /**
     * 为玩家添加一个持久化的属性修饰符（会自动保存到数据库并异步刷新属性）
     * @param uuid 玩家 UUID
     * @param modifier 修饰符对象
     */
    fun addModifier(uuid: UUID, modifier: AttributeModifier)

    /**
     * 移除玩家特定的修饰符
     * @param uuid 玩家 UUID
     * @param modifierUuid 修饰符的唯一标识
     */
    fun removeModifier(uuid: UUID, modifierUuid: UUID)

    /**
     * 强制刷新玩家的属性计算（异步）
     * 通常在装备变动或外部数据变更时调用
     */
    fun updatePlayerAttributes(player: Player)

    /**
     * 获取玩家所有当前的最终属性映射
     */
    fun getAllAttributes(player: Player): Map<String, Double>

    /**
     * 注册一个自定义属性类型
     * @param type 属性定义
     * @return true 注册成功, false 已存在或验证失败
     */
    fun registerAttribute(type: AttributeType): Boolean

    /**
     * 注销一个属性（内置属性不可注销）
     * @param id 属性 ID
     * @return true 移除成功, false 不存在或是内置属性
     */
    fun unregisterAttribute(id: String): Boolean

    /**
     * 查询属性是否已注册
     * @param id 属性 ID
     * @return true 已注册
     */
    fun isRegistered(id: String): Boolean

    /**
     * 获取所有已注册的属性类型
     */
    fun getRegisteredAttributes(): Collection<AttributeType>

    companion object {
        /**
         * 默认 API 实现实例
         */
        @JvmStatic
        val instance: ChoTenAttributeAPI = ChoTenAttributeAPIImpl()

        // ===== JS 便捷方法 (仅使用基础数据类型) =====

        /**
         * 获取玩家属性值
         * @param playerNameOrUuid 玩家名称或 UUID 字符串
         * @param attributeId 属性 ID
         */
        @JvmStatic
        fun getAttribute(playerNameOrUuid: String, attributeId: String): Double {
            val player = resolvePlayer(playerNameOrUuid) ?: return 0.0
            return instance.getAttributeValue(player, attributeId)
        }

        /**
         * 添加属性修饰符 (基础类型参数)
         * @param playerUuid 玩家 UUID 字符串
         * @param attributeId 属性 ID
         * @param value 数值
         * @param type 修饰类型: "ADD" / "PERCENT" / "MULTIPLY"
         * @param source 来源: "HAND"/"MAINHAND"/"OFFHAND"/"MELEE"/"RANGED"/"ARMOR"/"SKILL"/"POTION"/"OTHER"
         * @param slot 槽位: "HEAD"/"CHEST"/"LEGS"/"FEET"/"INVENTORY"/"MAINHAND"/"OFFHAND"/"OTHER"
         * @param modifierUuid 修饰符 UUID 字符串 (可选, 默认随机)
         * @param expireOn 过期时间戳 (毫秒, -1 或 null 为永久)
         */
        @JvmStatic
        @JvmOverloads
        fun addModifier(
            playerUuid: String,
            attributeId: String,
            value: Double,
            type: String = "ADD",
            source: String = "OTHER",
            slot: String = "OTHER",
            modifierUuid: String? = null,
            expireOn: Long? = null
        ) {
            val uuid = safeUuid(playerUuid) ?: return
            val mod = AttributeModifier(
                uuid = modifierUuid?.let { safeUuid(it) } ?: UUID.randomUUID(),
                attributeId = attributeId,
                value = value,
                type = parseEnum(type, ModifierType.ADD),
                source = parseEnum(source, ModifierSource.OTHER),
                slot = parseEnum(slot, AttributeSlot.OTHER),
                expireOn = if (expireOn != null && expireOn <= 0) null else expireOn
            )
            instance.addModifier(uuid, mod)
        }

        /**
         * 添加临时属性修饰符 (秒级持续时间, JS 便利方法)
         * @param playerUuid 玩家 UUID 字符串
         * @param attributeId 属性 ID
         * @param value 数值
         * @param durationSeconds 持续时间 (秒, -1 或 null 为永久)
         * @param type 修饰类型 (可选, 默认 "ADD")
         * @param source 来源 (可选, 默认 "OTHER")
         * @param slot 槽位 (可选, 默认 "OTHER")
         * @param modifierUuid 修饰符 UUID 字符串 (可选, 默认随机)
         */
        @JvmStatic
        @JvmOverloads
        fun addTimedModifier(
            playerUuid: String,
            attributeId: String,
            value: Double,
            durationSeconds: Double,
            type: String = "ADD",
            source: String = "OTHER",
            slot: String = "OTHER",
            modifierUuid: String? = null
        ) {
            val expireMs = if (durationSeconds <= 0) null else System.currentTimeMillis() + (durationSeconds * 1000).toLong()
            addModifier(playerUuid, attributeId, value, type, source, slot, modifierUuid, expireMs)
        }

        /**
         * 移除属性修饰符
         * @param playerUuid 玩家 UUID 字符串
         * @param modifierUuid 修饰符 UUID 字符串
         */
        @JvmStatic
        fun removeModifier(playerUuid: String, modifierUuid: String) {
            val pUuid = safeUuid(playerUuid) ?: return
            val mUuid = safeUuid(modifierUuid) ?: return
            instance.removeModifier(pUuid, mUuid)
        }

        /**
         * 刷新玩家属性
         * @param playerNameOrUuid 玩家名称或 UUID 字符串
         */
        @JvmStatic
        fun updatePlayer(playerNameOrUuid: String) {
            val player = resolvePlayer(playerNameOrUuid) ?: return
            instance.updatePlayerAttributes(player)
        }

        /**
         * 获取玩家所有属性映射
         * @param playerNameOrUuid 玩家名称或 UUID 字符串
         */
        @JvmStatic
        fun getAllAttributes(playerNameOrUuid: String): Map<String, Double> {
            val player = resolvePlayer(playerNameOrUuid) ?: return emptyMap()
            return instance.getAllAttributes(player)
        }

        /**
         * 注册自定义属性 (基础类型参数)
         * @param id 属性 ID
         * @param name 属性名称
         * @param defaultValue 默认值
         * @param minValue 最小值 (可选)
         * @param maxValue 最大值 (可选)
         * @param isPercentage 是否为百分比
         * @param description 描述
         * @param vanillaName 原版属性名 (可选)
         */
        @JvmStatic
        @JvmOverloads
        fun registerAttribute(
            id: String,
            name: String,
            defaultValue: Double = 0.0,
            minValue: Double? = null,
            maxValue: Double? = null,
            isPercentage: Boolean = false,
            description: String = "",
            vanillaName: String? = null
        ): Boolean {
            val type = AttributeType(
                id = id,
                name = name,
                vanillaName = vanillaName,
                defaultValue = defaultValue,
                minValue = minValue,
                maxValue = maxValue,
                isPercentage = isPercentage,
                description = description
            )
            return instance.registerAttribute(type)
        }

        /**
         * 检查属性是否已注册
         */
        fun isRegistered(id: String): Boolean = instance.isRegistered(id)

        /**
         * 注销属性
         */
        fun unregisterAttribute(id: String): Boolean = instance.unregisterAttribute(id)

        // ===== 内部辅助方法 =====

        private fun resolvePlayer(nameOrUuid: String): Player? {
            val uuid = safeUuid(nameOrUuid)
            if (uuid != null) {
                return org.bukkit.Bukkit.getPlayer(uuid)
            }
            return org.bukkit.Bukkit.getPlayerExact(nameOrUuid)
        }

        private fun safeUuid(str: String): UUID? {
            return try {
                UUID.fromString(str)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T {
            return try {
                enumValueOf<T>(name.uppercase())
            } catch (_: IllegalArgumentException) {
                default
            }
        }
    }
}

/**
 * 接口实现类
 */
class ChoTenAttributeAPIImpl : ChoTenAttributeAPI {

    override fun getAttributeValue(player: Player, attributeId: String): Double {
        return AttributeManager.getAttributeValue(player, attributeId)
    }

    override fun addModifier(uuid: UUID, modifier: AttributeModifier) {
        AttributeManager.addModifier(uuid, modifier)
    }

    override fun removeModifier(uuid: UUID, modifierUuid: UUID) {
        val data = AttributeManager.getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        if (mods.removeIf { it.uuid == modifierUuid }) {
            data.modifiers = mods
            AttributeManager.savePlayerData(data)
            // 尝试更新在线玩家
            org.bukkit.Bukkit.getPlayer(uuid)?.let { AttributeManager.updatePlayer(it) }
        }
    }

    override fun updatePlayerAttributes(player: Player) {
        AttributeManager.updatePlayer(player)
    }

    override fun getAllAttributes(player: Player): Map<String, Double> {
        return AttributeManager.attributeRegistry.keys.associateWith {
            getAttributeValue(player, it)
        }
    }

    override fun registerAttribute(type: AttributeType): Boolean {
        return AttributeManager.registerAttribute(type, RegistrationSource.API)
    }

    override fun unregisterAttribute(id: String): Boolean {
        return AttributeManager.unregisterAttribute(id)
    }

    override fun isRegistered(id: String): Boolean {
        return AttributeManager.isRegistered(id)
    }

    override fun getRegisteredAttributes(): Collection<AttributeType> {
        return AttributeManager.getRegisteredAttributes()
    }
}
