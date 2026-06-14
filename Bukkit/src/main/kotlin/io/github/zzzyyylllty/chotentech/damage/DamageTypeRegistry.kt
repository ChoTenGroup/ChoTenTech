package io.github.zzzyyylllty.chotentech.damage

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.AttributeType
import io.github.zzzyyylllty.chotentech.data.RegistrationSource
import io.github.zzzyyylllty.chotentech.damage.handlers.*
import io.github.zzzyyylllty.chotentech.logger.infoS
import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * 伤害类型注册表
 * 管理所有伤害类型的注册、启用/禁用、属性注册、处理器映射
 */
object DamageTypeRegistry {

    // ==================== 注册表 ====================

    /** 注册类型数据：DamageType.id -> RegisteredDamageType */
    private val registry = linkedMapOf<String, RegisteredDamageType>()

    /** 处理器映射：DamageType.id -> DamageTypeHandler */
    private val handlers = linkedMapOf<String, DamageTypeHandler>()

    /** 获取所有已注册的伤害类型 */
    fun getAll(): Collection<RegisteredDamageType> = registry.values.toList()

    /** 获取所有已启用的伤害类型 */
    fun getActive(): Collection<RegisteredDamageType> = registry.values.filter { it.enabled }

    /** 根据 ID 获取注册类型 */
    fun get(id: String): RegisteredDamageType? = registry[id]

    /** 根据 ID 获取处理器 */
    fun getHandler(id: String): DamageTypeHandler? = handlers[id]

    /** 根据类型获取处理器 */
    fun getHandler(type: DamageType): DamageTypeHandler? = handlers[type.id]

    /** 是否已启用 */
    fun isActive(id: String): Boolean = registry[id]?.enabled == true
    fun isActive(type: DamageType): Boolean = registry[type.id]?.enabled == true

    // ==================== 处理器注册 ====================

    /**
     * 注册一个伤害类型处理器
     * 同时自动注册对应的属性
     */
    fun registerHandler(handler: DamageTypeHandler): Boolean {
        val id = handler.type.id
        if (handlers.containsKey(id)) return false

        handlers[id] = handler
        registerAttributes(handler.type)
        return true
    }

    // ==================== 属性注册 ====================

    private fun registerAttributes(type: DamageType) {
        val t = type
        AttributeManager.registerAttribute(
            AttributeType(id = "${t.id}_defense", name = "${t.displayName}防御", vanillaName = null, defaultValue = 0.0),
            RegistrationSource.API
        )
        AttributeManager.registerAttribute(
            AttributeType(id = "${t.id}_extra_damage", name = "${t.displayName}伤害加成", vanillaName = null, defaultValue = 0.0, isPercentage = true),
            RegistrationSource.API
        )
        AttributeManager.registerAttribute(
            AttributeType(id = "${t.id}_damage", name = "${t.displayName}伤害", vanillaName = null, defaultValue = 0.0),
            RegistrationSource.API
        )
    }

    // ==================== 公式常量（可从 config 加载） ====================

    /** 通用防御公式分母 */
    var defenseDenominator: Double = 100.0
        private set
    /** 属性防御公式分母 */
    var specificDenominator: Double = 50.0
        private set
    /** 精神转化比率 */
    var sanityConversionRatio: Double = 10.0
        private set
    /** 霜寒叠加窗口（tick） */
    var frostWindowTicks: Long = 100
        private set
    /** 霜寒阈值（中量/大量/极大量） */
    var frostThresholds: Triple<Double, Double, Double> = Triple(15.0, 30.0, 50.0)
        private set
    /** 电击基础禁锢概率 */
    var shockBaseChance: Double = 0.2
        private set
    /** 电击每点伤害额外概率 */
    var shockDamageChanceRatio: Double = 0.005
        private set
    /** 电击禁锢最短秒数 */
    var shockMinDuration: Int = 1
        private set
    /** 电击禁锢最长秒数 */
    var shockMaxDuration: Int = 5
        private set
    /** 衰退最大百分比 */
    var decayMaxPercent: Double = 0.5
        private set

    // ==================== 配置加载 ====================

    fun loadConfig() {
        val section = ChoTenTech.config.getConfigurationSection("damage-system") ?: return

        defenseDenominator = section.getDouble("defense-denominator", 100.0)
        specificDenominator = section.getDouble("specific-denominator", 50.0)
        sanityConversionRatio = section.getDouble("sanity-conversion-ratio", 10.0)
        frostWindowTicks = section.getLong("frost-window-ticks", 100)
        frostThresholds = Triple(
            section.getDouble("frost-medium-threshold", 15.0),
            section.getDouble("frost-high-threshold", 30.0),
            section.getDouble("frost-extreme-threshold", 50.0)
        )
        shockBaseChance = section.getDouble("shock-base-chance", 0.2)
        shockDamageChanceRatio = section.getDouble("shock-damage-chance-ratio", 0.005)
        shockMinDuration = section.getInt("shock-min-duration", 1)
        shockMaxDuration = section.getInt("shock-max-duration", 5)
        decayMaxPercent = section.getDouble("decay-max-percent", 0.5)

        // 加载类型启用/禁用
        val typeSection = section.getConfigurationSection("types")
        if (typeSection != null) {
            for (key in typeSection.getKeys(false)) {
                val enabled = typeSection.getConfigurationSection(key)?.getBoolean("enabled", true) ?: true
                val existing = registry[key]
                if (existing != null) {
                    registry[key] = existing.copy(enabled = enabled)
                } else {
                    warningS("DamageTypeRegistry: unknown damage type '$key' in config")
                }
            }
        }

        val active = getActive()
        infoS("DamageTypeRegistry: ${active.size}/${registry.size} damage types active: ${active.map { it.id }}")
    }

    /** 计算伤害（委托给处理器） */
    fun computeDamage(baseDamage: Double, type: DamageType, victim: LivingEntity): Double {
        val handler = handlers[type.id] ?: return baseDamage
        return handler.compute(baseDamage, victim)
    }

    /** 应用效果（委托给处理器） */
    fun applyEffects(victim: Player, type: DamageType, finalDamage: Double, baseDamage: Double) {
        val handler = handlers[type.id] ?: return
        handler.apply(victim, finalDamage, baseDamage)
    }

    // ==================== 初始化 ====================

    fun init() {
        registry.clear()
        handlers.clear()

        // 注册所有 7 个内置处理器
        val builtinHandlers = listOf(
            PhysicalHandler(),
            BurnHandler(),
            FrostHandler(),
            ShockHandler(),
            PsychicHandler(),
            ErosionHandler(),
            DecayHandler()
        )
        for (h in builtinHandlers) {
            registerHandler(h)
            registry[h.type.id] = RegisteredDamageType(type = h.type, enabled = true)
        }

        loadConfig()
    }
}
