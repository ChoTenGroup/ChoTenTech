package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 负重系统管理器
 *
 * 每 1 秒异步计算在线玩家背包总重量，并根据重量区间施加属性修正：
 *   < 20KG → +移速（每KG +1%，上限 +15%）  +体力上限（每KG +2，无上限）
 *   20-30KG → 无效果（舒适区间）
 *   >= 35KG → -体力回复（每KG -2%）
 *   >= 45KG → 在上一条基础上，-移速（每KG -2%）
 */
object WeightManager {

    // ==================== 配置 ====================
    private val itemWeights = ConcurrentHashMap<String, Double>()
    private val materialWeights = ConcurrentHashMap<String, Double>()
    private var defaultWeight = 0.2

    // ==================== 玩家缓存 ====================
    private val playerTotalWeight = ConcurrentHashMap<UUID, Double>()

    // ==================== 固定修饰符 UUID（用于定位并替换已存在的负重修饰符）====================
    private val WEIGHT_SPEED_UUID = UUID.nameUUIDFromBytes("weight:movement_speed".toByteArray())
    private val WEIGHT_STAMINA_REGEN_UUID = UUID.nameUUIDFromBytes("weight:stamina_regen".toByteArray())
    private val WEIGHT_STAMINA_MAX_UUID = UUID.nameUUIDFromBytes("weight:stamina_max".toByteArray())

    private val weightModifierUuids = setOf(WEIGHT_SPEED_UUID, WEIGHT_STAMINA_REGEN_UUID, WEIGHT_STAMINA_MAX_UUID)

    private var weightTask: PlatformExecutor.PlatformTask? = null

    // ==================== 公开 API ====================

    /** 获取某物品的重量（KG），含堆叠倍率 */
    fun getItemWeight(item: ItemStack): Double {
        if (item.type.isAir || item.amount <= 0) return 0.0
        val perUnit = getItemUnitWeight(item)
        return perUnit * item.amount
    }

    /** 获取玩家上次缓存的重量（KG），避免重复计算 */
    fun getCachedWeight(uuid: UUID): Double? = playerTotalWeight[uuid]

    /** 获取单件物品重量 */
    private fun getItemUnitWeight(item: ItemStack): Double {
        // 尝试按自定义物品 ID 查找
        try {
            val sertralineId = Sertraline.api().getId(item)
            if (sertralineId != null) {
                itemWeights[sertralineId]?.let { return it }
            }
        } catch (_: Exception) { /* Sertraline not available */ }

        // 按 Material 名称查找
        materialWeights[item.type.name]?.let { return it }

        // 默认值
        return defaultWeight
    }

    /** 计算玩家背包总重量 */
    fun getPlayerTotalWeight(player: Player): Double {
        val inv = player.inventory
        var total = 0.0

        // 物品栏 (hotbar 0-8 + 主背包 9-35)，盔甲和副手单独处理避免重复
        for (i in 0..35) {
            val item = inv.getItem(i) ?: continue
            total += getItemWeight(item)
        }
        // 盔甲栏
        for (item in inv.armorContents) {
            if (item != null && !item.type.isAir) {
                total += getItemWeight(item)
            }
        }
        // 副手
        val offhand = inv.itemInOffHand
        if (!offhand.type.isAir) {
            total += getItemWeight(offhand)
        }

        return kotlin.math.round(total * 10.0) / 10.0 // 保留一位小数
    }

    /** 立即强制刷新某玩家的负重修饰符 */
    fun forceUpdate(player: Player) {
        applyWeightEffects(player)
    }

    /** 移除所有玩家的负重修饰符（关闭时调用） */
    fun removeAllModifiers() {
        for (uuid in playerTotalWeight.keys) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            removeWeightModifiers(player)
        }
        playerTotalWeight.clear()
    }

    // ==================== 修饰符管理 ====================

    /** 根据重量计算效果 */
    data class WeightEffects(
        val movementSpeedPercent: Double,   // 移速修正（PERCENT 参数）
        val staminaRegenPercent: Double,    // 体力回复修正（PERCENT 参数）
        val staminaMaxAdd: Double           // 体力上限修正（ADD 参数）
    )

    private fun calculateEffects(player: Player, weight: Double): WeightEffects {
        val multiplier = AttributeManager.getAttributeValue(player, "weight_limit_multiplier")
            .coerceAtLeast(0.0)

        val thresholdLight = 20.0 * multiplier
        val thresholdComfort = 30.0 * multiplier
        val thresholdTransition = 35.0 * multiplier
        val thresholdHeavy = 45.0 * multiplier

        val speedPercent: Double
        val staminaRegenPercent: Double
        val staminaMaxAdd: Double

        when {
            weight < thresholdLight -> {
                speedPercent = minOf(weight * 0.01, 0.15)
                staminaMaxAdd = weight * 2.0
                staminaRegenPercent = 0.0
            }
            weight <= thresholdComfort -> {
                // 舒适区间，无效果
                speedPercent = 0.0
                staminaMaxAdd = 0.0
                staminaRegenPercent = 0.0
            }
            weight < thresholdTransition -> {
                // 过渡区间，无效果
                speedPercent = 0.0
                staminaMaxAdd = 0.0
                staminaRegenPercent = 0.0
            }
            else -> {
                staminaRegenPercent = -(weight - thresholdTransition) * 0.02
                staminaMaxAdd = 0.0
                speedPercent = if (weight >= thresholdHeavy) {
                    -(weight - thresholdHeavy) * 0.02
                } else {
                    0.0
                }
            }
        }

        return WeightEffects(speedPercent, staminaRegenPercent, staminaMaxAdd)
    }

    /** 为玩家应用负重修饰符（先移除旧的，再添加新的） */
    private fun applyWeightEffects(player: Player) {
        val weight = playerTotalWeight[player.uniqueId] ?: 0.0
        val effects = calculateEffects(player, weight)

        val attrData = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = attrData.modifiers.toMutableList()

        // 移除旧的负重修饰符
        mods.removeAll { it.uuid in weightModifierUuids }

        // 添加移速修正
        if (effects.movementSpeedPercent != 0.0) {
            mods += AttributeModifier(
                uuid = WEIGHT_SPEED_UUID,
                attributeId = "movement_speed",
                value = effects.movementSpeedPercent,
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // 添加体力回复修正
        if (effects.staminaRegenPercent != 0.0) {
            mods += AttributeModifier(
                uuid = WEIGHT_STAMINA_REGEN_UUID,
                attributeId = "stamina_regen_multiplier",
                value = effects.staminaRegenPercent,
                type = ModifierType.PERCENT,
                source = ModifierSource.OTHER
            )
        }

        // 添加体力上限修正
        if (effects.staminaMaxAdd != 0.0) {
            mods += AttributeModifier(
                uuid = WEIGHT_STAMINA_MAX_UUID,
                attributeId = "stamina_max",
                value = effects.staminaMaxAdd,
                type = ModifierType.ADD,
                source = ModifierSource.OTHER
            )
        }

        // 批量写入并重算
        attrData.modifiers = mods
        AttributeManager.savePlayerData(attrData)
        AttributeManager.updatePlayer(player)
    }

    /** 移除玩家所有负重修饰符 */
    private fun removeWeightModifiers(player: Player) {
        val attrData = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = attrData.modifiers.toMutableList()
        if (mods.removeAll { it.uuid in weightModifierUuids }) {
            attrData.modifiers = mods
            AttributeManager.savePlayerData(attrData)
            AttributeManager.updatePlayer(player)
        }
    }

    // ==================== 定时任务 ====================

    @Awake(LifeCycle.ACTIVE)
    fun startWeightTask() {
        loadConfig()

        weightTask = submitAsync(period = 20) { // 1 秒 = 20 ticks
            tick()
        }
        devLog("[WeightManager] 负重系统已启动")
    }

    @Awake(LifeCycle.DISABLE)
    fun stopWeightTask() {
        weightTask?.cancel()
        weightTask = null
        // 断开时清除负重修饰符，避免重启后有残留
        removeAllModifiers()
        devLog("[WeightManager] 负重系统已关闭")
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isOnline) continue
            // 创造/旁观模式不应用负重效果
            if (player.gameMode == org.bukkit.GameMode.CREATIVE || player.gameMode == org.bukkit.GameMode.SPECTATOR) {
                if (playerTotalWeight.containsKey(player.uniqueId)) {
                    playerTotalWeight.remove(player.uniqueId)
                    removeWeightModifiers(player)
                }
                continue
            }
            val newWeight = getPlayerTotalWeight(player)
            val prevWeight = playerTotalWeight[player.uniqueId]
            // 重量变化超过 0.5KG 时更新，减少不必要的 DB 写入
            if (prevWeight == null || kotlin.math.abs(newWeight - prevWeight) >= 0.5) {
                playerTotalWeight[player.uniqueId] = newWeight
                applyWeightEffects(player)
            }
        }
    }

    // ==================== 配置加载 ====================

    fun loadConfig() {
        itemWeights.clear()
        materialWeights.clear()

        val dir = getDataFolder()
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "weights.yml")
        if (!file.exists()) {
            // 从 JAR 释放默认配置
            try {
                releaseResourceFile("weights.yml")
                devLog("[WeightManager] 已释放默认 weights.yml")
            } catch (_: Exception) {
                devLog("[WeightManager] 释放 weights.yml 失败")
            }
        }
        if (!file.exists()) {
            devLog("[WeightManager] weights.yml 不存在，使用完全默认值")
            defaultWeight = 0.2
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)

        defaultWeight = config.getDouble("default", 0.2)

        // 加载自定义物品重量
        val itemsSection = config.getConfigurationSection("items")
        if (itemsSection != null) {
            for (key in itemsSection.getKeys(false)) {
                itemWeights[key] = itemsSection.getDouble(key, defaultWeight)
            }
        }

        // 加载原版物品重量
        val materialsSection = config.getConfigurationSection("materials")
        if (materialsSection != null) {
            for (key in materialsSection.getKeys(false)) {
                materialWeights[key] = materialsSection.getDouble(key, defaultWeight)
            }
        }

        devLog("[WeightManager] 加载了 ${itemWeights.size} 个自定义物品和 ${materialWeights.size} 个原版物品的负重配置（默认: ${defaultWeight}KG）")
    }
}
