package io.github.zzzyyylllty.chotentech.manager

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.AttributeType
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.data.PlayerAttributeData
import io.github.zzzyyylllty.chotentech.data.createDefaultPlayerData
import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.event.ChoTenAttributeRegisterEvent
import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.AttributeModifier as BukkitAttributeModifier
import org.bukkit.command.CommandSender
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.platform.util.asLangText
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.expansion.CustomType
import taboolib.expansion.CustomTypeFactory
import taboolib.expansion.db
import taboolib.expansion.mapper
import taboolib.module.database.ColumnTypePostgreSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs


/** modifiers 列表扁平化存储为单列 JSON。PTC 只对"成员字段类型"查 CustomType,不对 mapper 根类型查,因此必须用 List+elementType 注册,否则 MySQL 下会为 List 字段建子表导致读写异常。列类型用 VARCHAR+长度:MySQL 的 TEXT 系列带长度参数会语法错误(DatabaseDialect 的 CustomType 分支总是拼接 length) */
object AttributeModifierListType : CustomType {
    override val type: Class<*> = List::class.java
    override val elementType: Class<*> = AttributeModifier::class.java
    override val typeSQL = ColumnTypeSQL.LONGTEXT
    override val typeSQLite = ColumnTypeSQLite.TEXT
    override val typePostgreSQL = ColumnTypePostgreSQL.TEXT
    // length=0 时 ColumnSQL 不拼接括号参数,输出纯 "LONGTEXT"(TEXT 系列带长度参数在 MySQL 是语法错误)
    override val length = 0

    private val listType = object : com.google.gson.reflect.TypeToken<List<AttributeModifier>>() {}.type

    override fun serialize(value: Any): Any = jsonUtils.toJson(value)

    override fun deserialize(value: Any): Any {
        val raw = value.toString()
        if (raw.isBlank()) return emptyList<AttributeModifier>()
        // If the string is double-encoded JSON (starts and ends with quotes), parse as JsonElement first to unwrap
        val jsonStr = if (raw.startsWith("\"") && raw.endsWith("\"")) {
            jsonUtils.fromJson(raw, String::class.java) ?: raw
        } else {
            raw
        }
        // Guard: if the string doesn't look like a JSON array, return an empty list
        if (!jsonStr.trimStart().startsWith("[")) {
            warningS("AttributeModifierListType: cannot deserialize non-JSON value: $jsonStr")
            return emptyList<AttributeModifier>()
        }
        return jsonUtils.fromJson(jsonStr, listType) ?: emptyList<AttributeModifier>()
    }
}

/** 显式注册自定义类型。自动发现在某些环境下不可靠,兜底保证 modifiers 单列 JSON 存储(否则 MySQL 下会产生子表并读写失败) */
@Awake(LifeCycle.INIT)
fun registerCustomTypeFactory() {
    CustomTypeFactory.registeredCollectionTypes.computeIfAbsent(List::class.java) { ConcurrentHashMap() }[AttributeModifier::class.java] = AttributeModifierListType
}

object AttributeManager {

    // 跟随 config.yml 的 database 配置 (enable=true 时走 MySQL)
    val dataMapper by mapper<PlayerAttributeData>(db("config.yml", "database", "attributes.db"))

    /** 玩家持久化行缓存(uuid -> PlayerAttributeData):
     *  regen/expiry/weight/nutrition 等周期任务每秒×N 玩家读属性行,缓存消除主线程 DB 读。
     *  写路径(savePlayerData)同步更新,玩家退出时由 clearCache 清理,规模与在线玩家数一致 */
    private val persistentDataCache = ConcurrentHashMap<String, PlayerAttributeData>()

    // 缓存最终计算结果。UUID -> (AttributeID -> Value)
    private val attributeCache = ConcurrentHashMap<UUID, Map<String, Double>>()
    // 实体属性缓存（非玩家生物，装备 NBT 计算）
    private val entityCache = ConcurrentHashMap<UUID, Map<String, Double>>()
    // 实体缓存刷新时间戳
    private val entityCacheTimestamps = ConcurrentHashMap<UUID, Long>()
    // 实体缓存 TTL（毫秒）
    private const val ENTITY_CACHE_TTL = 60_000L
    // 实体缓存最大条目数，防止内存泄漏
    private const val ENTITY_CACHE_MAX_SIZE = 2000
    // 缓存 vanillaName 到 Bukkit Attribute 实例的映射（可能为 null 表示找不到）
    private val vanillaAttributeCache = ConcurrentHashMap<String, org.bukkit.attribute.Attribute?>()

    /** 快捷栏切换的解析+计算线程池:单线程保证快速连续切换时结果按提交顺序落定,立即执行不等 tick 调度 */
    private val heldItemPool = Executors.newSingleThreadExecutor { r -> Thread(r, "ChoTenTech-HeldItem") }

    // 周期性任务引用
    private var regenTask: PlatformExecutor.PlatformTask? = null
    private var entityCacheTask: PlatformExecutor.PlatformTask? = null
    private var expiryTask: PlatformExecutor.PlatformTask? = null

    // 属性注册表
    val attributeRegistry = ConcurrentHashMap<String, AttributeType>()

    // 内置属性 ID 集合（不可被注销）
    private val builtinAttributeIds = mutableSetOf<String>()

    // 基础值属性 ID 集合（从 basic-attributes 配置加载）
    private val basicAttributeIds = mutableSetOf<String>()

    // ID 合法性校验：仅允许小写字母、数字、下划线
    private val VALID_ID_PATTERN = Regex("^[a-z0-9_]+$")

    /**
     * 注册内置属性，插件启动时调用一次
     * 覆盖 Minecraft 全部 11 个原生 Attribute (RegistryKey<Attribute>)
     */
    fun registerBuiltinAttributes() {
        val builtins = listOf(
            AttributeType("max_health", "生命上限", "max_health", 100.0),
            AttributeType("max_absorption", "伤害吸收上限", "max_absorption", 0.0),
            AttributeType("movement_speed", "移动速度", "movement_speed", 0.1),
            AttributeType("attack_damage", "攻击力", "attack_damage", 1.0),
            AttributeType("attack_speed", "攻击速度", "attack_speed", 4.0),
            AttributeType("armor", "护甲值", "armor", 0.0),
            AttributeType("armor_toughness", "护甲韧性", "armor_toughness", 0.0),
            AttributeType("attack_knockback", "攻击击退", "attack_knockback", 0.0),
            AttributeType("knockback_resistance", "击退抗性", "knockback_resistance", 0.0),
            AttributeType("luck", "幸运", "luck", 0.0),
            AttributeType("fall_damage_multiplier", "跌落伤害倍率", "fall_damage_multiplier", 1.0),
            AttributeType("burning_time", "燃烧时间", "burning_time", 1.0),
            AttributeType("explosion_knockback_resistance", "爆炸击退抗性", "explosion_knockback_resistance", 0.0),
            AttributeType("mining_efficiency", "挖掘效率", "mining_efficiency", 0.0),
            AttributeType("mining_power", "挖掘强度", null, 1.0, minValue = 0.0),
            AttributeType("movement_efficiency", "移动效率", "movement_efficiency", 0.0),
            AttributeType("jump_strength", "跳跃强度", "jump_strength", 0.42),
            AttributeType("oxygen_bonus", "氧气加成", "oxygen_bonus", 0.0),
            AttributeType("player_block_break_speed", "方块破坏速度", "player_block_break_speed", 1.0),
            AttributeType("player_block_interaction_range", "方块交互距离", "player_block_interaction_range", 4.5),
            AttributeType("player_entity_interaction_range", "实体交互距离", "player_entity_interaction_range", 3.0),
            AttributeType("player_submerged_mining_speed", "水下挖掘速度", "player_submerged_mining_speed", 0.2),
            AttributeType("player_mining_speed", "挖掘速度", "player_mining_speed", 1.0),
            AttributeType("gravity", "重力", "gravity", 0.08),
            AttributeType("safe_fall_distance", "安全跌落距离", "safe_fall_distance", 3.0),
            AttributeType("scale", "缩放", "scale", 1.0),
            AttributeType("step_height", "跨步高度", "step_height", 0.6),
            AttributeType("spawn_reinforcements", "僵尸增援概率", "spawn_reinforcements", 0.0),
            // 自定义扩展属性 (无 vanillaName 映射)
            AttributeType("max_sanity", "精神上限", null, 100.0, minValue = 0.0, maxValue = 1000.0),
            AttributeType("sanity_regeneration", "精神回复", null, 0.0, isPercentage = false),
            AttributeType("max_darkness", "阴暗上限", null, 100.0, minValue = 0.0, maxValue = 1000.0),
            AttributeType("max_custom_hunger", "饱食度上限", null, 100.0, minValue = 0.0, maxValue = 1000.0),
            AttributeType("max_custom_saturation", "饱和度上限", null, 100.0, minValue = 0.0, maxValue = 1000.0),
            AttributeType("external_injury", "外伤", null, 100.0, minValue = 0.0, maxValue = 100.0),
            AttributeType("internal_injury", "内伤", null, 100.0, minValue = 0.0, maxValue = 100.0),
            AttributeType("external_injury_regen", "外伤恢复", null, 0.0, isPercentage = false),
            AttributeType("internal_injury_regen", "内伤恢复", null, 0.0, isPercentage = false),
            AttributeType("defense", "综合防御", null, 0.0),
            AttributeType("crit_chance", "暴击率", null, 0.0, isPercentage = true),
            AttributeType("crit_damage", "暴击伤害", null, 0.0, isPercentage = true),
            AttributeType("lifesteal", "生命偷取", null, 0.0, isPercentage = true),
            AttributeType("dodge_chance", "闪避率", null, 0.0, isPercentage = true),
            // 格挡相关属性
            AttributeType("block_chance", "格挡概率", null, 0.0, isPercentage = true),
            AttributeType("block_defense", "格挡防御", null, 0.0, isPercentage = false),
            AttributeType("block_defense_percent", "格挡防御百分比", null, 0.0, isPercentage = true),
            // 新增攻击类型属性
            AttributeType("ranged_damage", "远程伤害", null, 0.0, isPercentage = false),
            AttributeType("magic_attack_damage", "魔法攻击伤害", null, 0.0, isPercentage = true),
            AttributeType("magic_damage", "魔法伤害", null, 0.0, isPercentage = false),
            AttributeType("fire_attack_damage", "火焰攻击伤害", null, 0.0, isPercentage = true),
            AttributeType("fire_attack_chance", "火焰攻击几率", null, 0.0, isPercentage = true),
            // 伤害增益属性（攻击者）
            AttributeType("physical_extra_damage", "物理伤害加成", null, 0.0, isPercentage = true),
            AttributeType("ranged_extra_damage", "远程伤害加成", null, 0.0, isPercentage = true),
            AttributeType("magic_extra_damage", "魔法伤害加成", null, 0.0, isPercentage = true),
            AttributeType("fire_extra_damage", "火焰伤害加成", null, 0.0, isPercentage = true),
            // 伤害抗性属性（受害者）
            AttributeType("physical_damage_reduction", "物理伤害减免", null, 0.0, isPercentage = true),
            AttributeType("ranged_damage_reduction", "远程伤害减免", null, 0.0, isPercentage = true),
            // 弓/弩专用属性
            AttributeType("bow_extra_damage", "弓伤害加成", null, 0.0, isPercentage = true),
            AttributeType("crossbow_extra_damage", "弩伤害加成", null, 0.0, isPercentage = true),
            AttributeType("bow_damage_reduction", "弓伤害减免", null, 0.0, isPercentage = true),
            AttributeType("crossbow_damage_reduction", "弩伤害减免", null, 0.0, isPercentage = true),
            AttributeType("magic_damage_reduction", "魔法伤害减免", null, 0.0, isPercentage = true),
            AttributeType("fire_damage_reduction", "火焰伤害减免", null, 0.0, isPercentage = true),
            AttributeType("all_damage_reduction", "全伤害减免", null, 0.0, isPercentage = true),
            // ===== 伤害类型系统属性（7种类型的防御/加成） =====
            // 物理
            AttributeType("physical_defense", "物理防御", null, 0.0),
            AttributeType("physical_damage", "物理伤害", null, 0.0),
            // 灼烧
            AttributeType("burn_defense", "灼烧防御", null, 0.0),
            AttributeType("burn_extra_damage", "灼烧伤害加成", null, 0.0, isPercentage = true),
            AttributeType("burn_damage", "灼烧伤害", null, 0.0),
            // 霜寒
            AttributeType("frost_defense", "霜寒防御", null, 0.0),
            AttributeType("frost_extra_damage", "霜寒伤害加成", null, 0.0, isPercentage = true),
            AttributeType("frost_damage", "霜寒伤害", null, 0.0),
            // 电击
            AttributeType("shock_defense", "电击防御", null, 0.0),
            AttributeType("shock_extra_damage", "电击伤害加成", null, 0.0, isPercentage = true),
            AttributeType("shock_damage", "电击伤害", null, 0.0),
            // 精神
            AttributeType("psychic_defense", "精神防御", null, 0.0),
            AttributeType("psychic_extra_damage", "精神伤害加成", null, 0.0, isPercentage = true),
            AttributeType("psychic_damage", "精神伤害", null, 0.0),
            // 侵蚀
            AttributeType("erosion_defense", "侵蚀防御", null, 0.0),
            AttributeType("erosion_extra_damage", "侵蚀伤害加成", null, 0.0, isPercentage = true),
            AttributeType("erosion_damage", "侵蚀伤害", null, 0.0),
            // 衰退
            AttributeType("decay_defense", "衰退防御", null, 0.0),
            AttributeType("decay_extra_damage", "衰退伤害加成", null, 0.0, isPercentage = true),
            AttributeType("decay_damage", "衰退伤害", null, 0.0),
            // 体力上限（用于营养系统和负重系统）
            AttributeType("stamina_max", "体力上限", null, 100.0, minValue = 0.0, maxValue = 10000.0),
            // 营养相关属性
            AttributeType("stamina_regen_multiplier", "体力回复倍率", null, 1.0, minValue = 0.0),
            AttributeType("health_regen_multiplier", "生命回复倍率", null, 1.0, minValue = 0.0),
            AttributeType("health_regen", "生命恢复", null, 0.2, minValue = 0.0),
            // 防御韧性：高伤害时防御效果打折，韧性减少折扣
            AttributeType("defense_toughness", "防御韧性", null, 0.0, minValue = 0.0),
            // 负重限制倍率：负重系统阈值乘以此值，默认1.0
            AttributeType("weight_limit_multiplier", "负重限制", null, 1.0, minValue = 0.0),
            // 禁止移动:值 >= 1.0 时拦截 PlayerMoveEvent
            AttributeType("deny_move", "禁止移动", null, 0.0, minValue = 0.0),
        )
        for (type in builtins) {
            registerAttribute(type, RegistrationSource.BUILTIN)
            builtinAttributeIds.add(type.id)
        }
    }

    /**
     * 注册一个属性类型
     * @return true 注册成功, false 已存在或验证失败
     */
    fun registerAttribute(type: AttributeType, source: RegistrationSource = RegistrationSource.API): Boolean {
        // 验证 id 非空且合法
        if (type.id.isBlank() || !VALID_ID_PATTERN.matches(type.id)) {
            warningS("AttributeManager: invalid attribute id '${type.id}' (only [a-z0-9_] allowed)")
            return false
        }
        // 检查是否已存在
        if (attributeRegistry.containsKey(type.id)) {
            return false
        }
        // 验证 vanillaName（若非 null）
        if (type.vanillaName != null) {
            try {
                var key = NamespacedKey.minecraft(type.vanillaName)
                var attr = Registry.ATTRIBUTE.get(key)
                // 如果原始名称未找到且不包含点号，尝试添加 generic. 前缀
                if (attr == null && !type.vanillaName.contains('.')) {
                    key = NamespacedKey.minecraft("generic.${type.vanillaName}")
                    attr = Registry.ATTRIBUTE.get(key)
                }
                if (attr == null) {
                    warningS("AttributeManager: vanillaName '${type.vanillaName}' not found in Registry.ATTRIBUTE (tried '${type.vanillaName}' and 'generic.${type.vanillaName}')")
                    return false
                }
                // 验证成功，将找到的属性实例存入缓存，避免后续重复查找
                vanillaAttributeCache[type.vanillaName] = attr
            } catch (e: Exception) {
                warningS("AttributeManager: invalid vanillaName '${type.vanillaName}': ${e.message}")
                return false
            }
        }
        attributeRegistry[type.id] = type
        // 事件
        ChoTenAttributeRegisterEvent(type, source).call()
        return true
    }

    /**
     * 注销一个属性
     * @return true 移除成功, false 不存在或是内置属性
     */
    fun unregisterAttribute(id: String): Boolean {
        if (id in builtinAttributeIds) {
            warningS("AttributeManager: cannot unregister builtin attribute '$id'")
            return false
        }
        val removed = attributeRegistry.remove(id) != null
        if (removed) {
            // 清理所有在线玩家缓存中该属性的值
            for (uuid in attributeCache.keys) {
                attributeCache.computeIfPresent(uuid) { _, cache ->
                    cache.toMutableMap().apply { remove(id) }
                }
            }
        }
        return removed
    }

    /**
     * 查询属性是否已注册
     */
    fun isRegistered(id: String): Boolean {
        return attributeRegistry.containsKey(id)
    }

    /**
     * 获取所有已注册的属性
     */
    fun getRegisteredAttributes(): Collection<AttributeType> {
        return attributeRegistry.values.toList()
    }

    /**
     * 获取属性的本地化显示名称
     * @param sender 用于确定语言的发送者，null 则返回硬编码名称
     * @param attributeId 属性 ID
     */
    fun getAttributeDisplayName(sender: CommandSender?, attributeId: String): String {
        val type = attributeRegistry[attributeId] ?: return attributeId
        if (sender == null) return type.name
        val key = "Attribute_${attributeId}_Name"
        val localized = sender.asLangText(key)
        return localized
    }

    /**
     * 获取属性的本地化描述
     * @param sender 用于确定语言的发送者，null 则返回硬编码描述
     * @param attributeId 属性 ID
     */
    fun getAttributeDescription(sender: CommandSender?, attributeId: String): String {
        val type = attributeRegistry[attributeId] ?: return ""
        if (sender == null) return type.description
        val key = "Attribute_${attributeId}_Description"
        val localized = sender.asLangText(key)
        return localized
    }

    /**
     * 从配置文件加载属性定义
     * 检查 getDataFolder()/attributes 目录，不存在则从 resources 释放默认文件
     */
    fun loadAttributeFiles() {
        val plugin = io.github.zzzyyylllty.chotentech.ChoTenTech
        val dataFolder = taboolib.common.platform.function.getDataFolder()
        val attrDir = java.io.File(dataFolder, "attributes")

        if (!attrDir.exists()) {
            attrDir.mkdirs()
            // 从 jar 释放默认配置文件
            val defaultResource = plugin::class.java.classLoader.getResource("attributes/default.yml")
            if (defaultResource != null) {
                val targetFile = java.io.File(attrDir, "default.yml")
                defaultResource.openStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                devLog(("AttributeManager: released default attribute config to ${targetFile.path}"))
            }
        }

        val files = attrDir.listFiles { f -> f.extension in listOf("yml", "yaml") } ?: return
        for (file in files) {
            loadAttributeFile(file)
        }
    }

    /**
     * 加载单个属性配置文件
     */
    private fun loadAttributeFile(file: java.io.File) {
        try {
            val config = taboolib.module.configuration.Configuration.loadFromFile(file)
            val registered = mutableListOf<String>()
            val skipped = mutableListOf<String>()

            for (id in config.getKeys(false)) {
                val section = config.getConfigurationSection(id) ?: continue
                val name = section.getString("name", id) ?: id
                val vanillaName = section.getString("vanillaName")
                val defaultValue = section.getDouble("defaultValue", 0.0)
                val minValue = if (section.contains("minValue")) section.getDouble("minValue") else null
                val maxValue = if (section.contains("maxValue")) section.getDouble("maxValue") else null
                val isPercentage = section.getBoolean("isPercentage", false)
                val description = section.getString("description", "") ?: ""
                val icon = section.getString("icon")

                val type = AttributeType(
                    id = id,
                    name = name,
                    vanillaName = vanillaName,
                    defaultValue = defaultValue,
                    minValue = minValue,
                    maxValue = maxValue,
                    isPercentage = isPercentage,
                    description = description,
                    icon = icon
                )

                if (registerAttribute(type, RegistrationSource.CONFIG)) {
                    registered.add(id)
                } else {
                    skipped.add(id)
                }
            }

            if (registered.isNotEmpty()) {
                devLog("AttributeManager: loaded ${registered.size} attribute(s) from ${file.name}: ${registered.joinToString()}")
            }
            if (skipped.isNotEmpty()) {
                warningS("AttributeManager: skipped ${skipped.size} attribute(s) from ${file.name}: ${skipped.joinToString()}")
            }
        } catch (e: Exception) {
            warningS("AttributeManager: failed to load attribute file ${file.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 从主配置加载基础值属性（basic-attributes）
     */
    fun loadBasicAttributes() {
        val config = io.github.zzzyyylllty.chotentech.ChoTenTech.config
        val section = config.getConfigurationSection("basic-attributes") ?: return
        val registered = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // 清空基础属性ID集合
        basicAttributeIds.clear()

        for (id in section.getKeys(false)) {
            val attrSection = section.getConfigurationSection(id) ?: continue
            val defaultSection = attrSection.getConfigurationSection("default") ?: continue

            val min = defaultSection.getDouble("min", 0.0)
            val max = defaultSection.getDouble("max", 100.0)
            val regeneration = defaultSection.getDouble("regeneration", 0.0)

            // 添加到基础属性ID集合
            basicAttributeIds.add(id)

            // 注册四个衍生属性
            val minAttr = AttributeType(
                id = "${id}_min",
                name = "${id}最小值",
                vanillaName = null,
                defaultValue = min,
                isPercentage = false,
                description = "${id}最小值"
            )
            val maxAttr = AttributeType(
                id = "${id}_max",
                name = "${id}最大值",
                vanillaName = null,
                defaultValue = max,
                isPercentage = false,
                description = "${id}最大值"
            )
            val regenAttr = AttributeType(
                id = "${id}_regeneration",
                name = "${id}恢复速度",
                vanillaName = null,
                defaultValue = regeneration,
                isPercentage = false,
                description = "${id}每秒恢复速度"
            )
            val currentAttr = AttributeType(
                id = "${id}_current",
                name = "${id}当前值",
                vanillaName = null,
                defaultValue = 0.0, // 基础值为0，当前值由修饰符提供
                isPercentage = false,
                description = "${id}当前值"
            )

            if (registerAttribute(minAttr, RegistrationSource.CONFIG)) {
                registered.add(minAttr.id)
            } else {
                skipped.add(minAttr.id)
            }
            if (registerAttribute(maxAttr, RegistrationSource.CONFIG)) {
                registered.add(maxAttr.id)
            } else {
                skipped.add(maxAttr.id)
            }
            if (registerAttribute(regenAttr, RegistrationSource.CONFIG)) {
                registered.add(regenAttr.id)
            } else {
                skipped.add(regenAttr.id)
            }
            if (registerAttribute(currentAttr, RegistrationSource.CONFIG)) {
                registered.add(currentAttr.id)
            } else {
                skipped.add(currentAttr.id)
            }
        }

        if (registered.isNotEmpty()) {
            devLog("AttributeManager: loaded ${registered.size} basic attribute(s) from config: ${registered.joinToString()}")
        }
        if (skipped.isNotEmpty()) {
            warningS("AttributeManager: skipped ${skipped.size} basic attribute(s) from config: ${skipped.joinToString()}")
        }
    }

    /**
     * 初始化玩家的基础属性当前值修饰符
     * 如果玩家没有当前值修饰符，则创建一个，初始值为最大值
     */
    fun initBasicAttributeCurrentForPlayer(uuid: UUID) {
        if (basicAttributeIds.isEmpty()) return
        val data = getPlayerPersistentData(uuid)
        val modifiers = data.modifiers.toMutableList()
        var modified = false

        for (basicId in basicAttributeIds) {
            val currentAttrId = "${basicId}_current"
            // 检查是否已存在当前值修饰符（来源为 OTHER）
            val existing = modifiers.find { it.attributeId == currentAttrId && it.source == ModifierSource.OTHER }
            if (existing == null) {
                // 获取最大值属性
                val maxAttrId = "${basicId}_max"
                val maxValue = attributeRegistry[maxAttrId]?.defaultValue ?: 100.0
                // 创建当前值修饰符
                val currentModifier = AttributeModifier(
                    attributeId = currentAttrId,
                    value = maxValue,
                    type = ModifierType.ADD,
                    source = ModifierSource.OTHER
                )
                modifiers.add(currentModifier)
                modified = true
                devLog("AttributeManager: added current modifier for $basicId = $maxValue for player $uuid")
            }
        }

        if (modified) {
            data.modifiers = modifiers
            savePlayerData(data)
        }
    }

    /**
     * 更新所有在线玩家的基础属性恢复
     * 每秒调用一次
     */
    fun updateRegeneration() {
        if (basicAttributeIds.isEmpty()) return
        for (player in org.bukkit.Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val data = getPlayerPersistentData(uuid)
            val modifiers = data.modifiers.toMutableList()
            var modified = false

            for (basicId in basicAttributeIds) {
                val currentAttrId = "${basicId}_current"
                val minAttrId = "${basicId}_min"
                val maxAttrId = "${basicId}_max"
                val regenAttrId = "${basicId}_regeneration"

                val min = attributeRegistry[minAttrId]?.defaultValue ?: 0.0
                val max = attributeRegistry[maxAttrId]?.defaultValue ?: 100.0
                val regen = attributeRegistry[regenAttrId]?.defaultValue ?: 0.0

                // 找到当前值修饰符
                val existingIndex = modifiers.indexOfFirst { it.attributeId == currentAttrId && it.source == ModifierSource.OTHER }
                if (existingIndex == -1) continue
                val existing = modifiers[existingIndex]
                var newValue = existing.value + regen
                // 限制在最小值和最大值之间
                if (newValue > max) newValue = max
                if (newValue < min) newValue = min
                // 如果值有变化，更新修饰符
                if (newValue != existing.value) {
                    val newModifier = existing.copy(value = newValue)
                    modifiers[existingIndex] = newModifier
                    modified = true
                    devLog("AttributeManager: updated $currentAttrId from ${existing.value} to $newValue for player ${player.name}")
                }
            }

            if (modified) {
                data.modifiers = modifiers
                savePlayerData(data)
                updatePlayer(player, async = false)
            }
        }
    }


    @Awake(LifeCycle.ACTIVE)
    fun startRegenerationTask() {
        regenTask = submit(period = 20L) { // 20 ticks = 1 second
            updateRegeneration()
        }
        devLog("AttributeManager: started regeneration task")

        // 实体属性后台刷新（每 30 秒，懒加载为主，后台为辅）
        entityCacheTask = submit(period = 600L) { // 600 ticks = 30 seconds
            refreshEntityCache()
        }
        devLog("AttributeManager: started entity attribute cache task (period=30s)")

        // 周期性过期修饰符清理（每 1 秒）：一次性延迟任务在 tick 边界触发时可能早于 expireOn 几毫秒,
        // isExpired 判定为 false 后没有任何兜底重试,修饰符会永久残留,因此必须周期扫描
        expiryTask = submit(period = 20L) {
            for (player in org.bukkit.Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val data = getPlayerPersistentData(uuid)
                val mods = data.modifiers.toMutableList()
                if (mods.removeIf { it.isExpired }) {
                    data.modifiers = mods
                    savePlayerData(data)
                    updatePlayer(player)
                }
            }
        }
        devLog("AttributeManager: started expired modifier cleanup task (period=1s)")
    }

    @Awake(LifeCycle.DISABLE)
    fun cancelTasks() {
        regenTask?.cancel()
        entityCacheTask?.cancel()
        expiryTask?.cancel()
        regenTask = null
        entityCacheTask = null
        expiryTask = null
        heldItemPool.shutdown()
    }

    /**
     * 重载属性：保留内置属性，移除所有非内置属性并重新从配置加载
     */
    fun reloadAttributes() {
        // 移除非内置属性
        val toRemove = attributeRegistry.keys.filter { it !in builtinAttributeIds }
        for (id in toRemove) {
            attributeRegistry.remove(id)
            // 清理在线玩家缓存
            for (uuid in attributeCache.keys) {
                attributeCache.computeIfPresent(uuid) { _, cache ->
                    cache.toMutableMap().apply { remove(id) }
                }
            }
        }
        // 重新从配置加载
        loadAttributeFiles()
        loadBasicAttributes()
        // 刷新所有在线玩家
        for (player in org.bukkit.Bukkit.getOnlinePlayers()) {
            updatePlayer(player)
        }
    }

    /**
     * 获取玩家数据
     */
    fun getPlayerPersistentData(uuid: UUID): PlayerAttributeData {
        val key = uuid.toString()
        persistentDataCache[key]?.let { return it }
        val data = dataMapper.findById(uuid) ?: createDefaultPlayerData(uuid)
        persistentDataCache[key] = data
        return data
    }

    /**
     * 保存玩家数据
     * filter 必须绑定 uuid 的字符串形式:insertOrUpdate 的 check-then-act 里主键条件会经 Any.value() 转成 String,
     * 若此处绑定原始 UUID 对象,JDBC 驱动(MySQL 按 BINARY(16),SQLite 序列化成字节)与 char(36) 列永不相等,
     * 导致每次保存都走 INSERT,同一 uuid 堆积多行且 findById 永远读到最旧行。
     */
    fun savePlayerData(data: PlayerAttributeData) {
        data.lastUpdate = System.currentTimeMillis()
        dataMapper.insertOrUpdate(data) { "uuid" eq data.uuid.toString() }
        persistentDataCache[data.uuid.toString()] = data
    }

    /**
     * 更新玩家属性
     */
    fun updatePlayer(player: Player, async: Boolean = true) {
        if (async && !Bukkit.isPrimaryThread()) {
            submit(async = false) {
                doUpdate(player)
            }
        } else {
            doUpdate(player)
        }
    }

    /**
     * 切换快捷栏专用更新:主线程立即从新槽位快照物品(事件时序中 inventory.selected 可能尚未提交,
     * 直接读 equipment 会拿到上一格的旧物品),NBT 解析与计算在专用线程池立即执行
     * (submitAsync 延迟 0 也要等下一个 tick 才启动),计算完成即回主线程同步原生属性。
     * 单线程池保证连续快速切换时任务按提交顺序执行,最终同步的总是最新槽位。
     */
    fun updatePlayerHeldItem(player: Player, newSlot: Int) {
        if (!player.isOnline) return
        if (!Bukkit.isPrimaryThread()) {
            submit(async = false) { updatePlayerHeldItem(player, newSlot) }
            return
        }
        val uuid = player.uniqueId
        // 新槽位可能为空(切到空手):照常重算以移除旧物品属性
        val newItem = player.inventory.getItem(newSlot)?.takeIf { !it.type.isAir }
        val snapshot = equipmentSnapshot(player, newItem)
        heldItemPool.execute {
            try {
                if (!player.isOnline) return@execute
                val data = getPlayerPersistentData(uuid)
                val allModifiers = mutableListOf<AttributeModifier>()
                allModifiers.addAll(data.modifiers)
                allModifiers.addAll(getEquipmentModifiers(snapshot))
                val finalValues = calculateFinalValues(allModifiers)
                submit {
                    if (!player.isOnline) return@submit
                    attributeCache[uuid] = finalValues
                    syncToVanillaInternal(player, finalValues)
                }
            } catch (e: Exception) {
                devLog("AttributeManager: 切快捷栏异步更新失败 ${player.name}: ${e.message}")
                submit { if (player.isOnline) doUpdate(player) }
            }
        }
    }

    private fun doUpdate(player: Player) {
        val uuid = player.uniqueId
        val data = getPlayerPersistentData(uuid)

        // DEBUG
        devLog(("Updating player $uuid, raw modifiers size: ${data.modifiers.size}"))
        data.modifiers.forEachIndexed { index, any ->
            devLog(("  - Modifier[$index]: class=${any?.javaClass?.name}, value=$any"))
        }

        // 1. 处理过期修饰符
        val mods = data.modifiers.toMutableList()
        val expiredRemoved = mods.removeIf { it.isExpired }
        if (expiredRemoved) {
            data.modifiers = mods
            savePlayerData(data)
        }

        // 2. 汇总所有修饰符
        val allModifiers = mutableListOf<AttributeModifier>()
        allModifiers.addAll(mods)
        allModifiers.addAll(getEquipmentModifiers(player))

        // 3. 计算最终值
        val finalValues = calculateFinalValues(allModifiers)

        // 4. 更新内存缓存
        attributeCache[uuid] = finalValues

        taboolib.common.platform.function.debug("AttributeManager: calculated final values for ${player.name}: ${finalValues.entries.joinToString { "${it.key}=${it.value}" }}")

        // 5. 同步到 Minecraft 原生属性系统（直接调用，调用方已保证在主线程）
        syncToVanillaInternal(player, finalValues)
    }

    /** 按注册的属性类型汇总修饰符并计算最终值(纯计算,可异步线程调用) */
    private fun calculateFinalValues(allModifiers: List<AttributeModifier>): Map<String, Double> {
        val finalValues = mutableMapOf<String, Double>()
        attributeRegistry.values.forEach { type ->
            val modsForAttr = allModifiers.filter { it.attributeId == type.id }
            if (modsForAttr.isNotEmpty() || type.defaultValue != 0.0) {
                val calculated = calculateFinal(type.defaultValue, modsForAttr)
                val clamped = type.clamp(calculated)
                if (clamped != calculated) {
                    taboolib.common.platform.function.debug("AttributeManager: clamped ${type.id} from $calculated to $clamped [${type.minValue}, ${type.maxValue}]")
                }
                finalValues[type.id] = clamped
                if (modsForAttr.isNotEmpty()) {
                    taboolib.common.platform.function.debug("AttributeManager: calculated $type.id: base=${type.defaultValue} + ${modsForAttr.size} modifiers = $calculated, clamped=$clamped")
                }
            }
        }
        return finalValues
    }

    private fun calculateFinal(base: Double, modifiers: List<AttributeModifier>): Double {
        var add = 0.0
        var percent = 0.0
        var multiply = 1.0

        for (mod in modifiers) {
            when (mod.type) {
                ModifierType.ADD -> add += mod.value
                ModifierType.PERCENT -> percent += mod.value
                ModifierType.MULTIPLY -> multiply *= mod.value
            }
        }

        return (base + add) * (1.0 + percent) * multiply
    }

    private fun syncToVanillaInternal(player: Player, values: Map<String, Double>) {
        if (!player.isOnline) return

        // 确保在主线程执行
        if (!Bukkit.isPrimaryThread()) {
            submit(async = false) {
                syncToVanillaInternal(player, values)
            }
            return
        }

        taboolib.common.platform.function.debug("AttributeManager: syncing vanilla attributes for ${player.name} (${values.size} calculated values)")

        // 遍历所有有 vanillaName 的已注册属性
        for (type in attributeRegistry.values) {
            val vanillaName = type.vanillaName ?: continue
            val attributeId = type.id

            // 目标值：优先使用计算值，否则使用默认值
            val rawTarget = values[attributeId] ?: type.defaultValue
            val targetValue = if (io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry.removeVanillaArmor &&
                (attributeId == "armor" || attributeId == "armor_toughness")
            ) 0.0 else rawTarget

            taboolib.common.platform.function.debug("AttributeManager: processing $attributeId ($vanillaName) target=$targetValue from values=${values[attributeId] != null}, default=${type.defaultValue}")

            // 获取 Bukkit Attribute 实例（带缓存）
            val bukkitAttribute = getVanillaAttribute(vanillaName)
            if (bukkitAttribute == null) {
                taboolib.common.platform.function.debug("AttributeManager: cannot sync $attributeId ($vanillaName) - bukkit attribute not found")
                continue
            }

            // 获取玩家的属性实例
            val instance = player.getAttribute(bukkitAttribute)
            if (instance == null) {
                taboolib.common.platform.function.debug("AttributeManager: cannot sync $attributeId ($vanillaName) - player attribute instance not found")
                continue
            }

            // 无条件覆盖基础值:重生/加入时 Minecraft 会重建实体并把 baseValue 重置为默认值,
            // 若依赖"值未变化则跳过"的增量判断,会导致 vanilla 实际值与缓存永久不一致(如攻击速度残留)
            val oldBaseValue = instance.baseValue
            instance.baseValue = targetValue
            taboolib.common.platform.function.debug("AttributeManager: set $attributeId ($vanillaName) to $targetValue (base: $oldBaseValue -> ${instance.baseValue}) for ${player.name}")
        }
    }

    /**
     * 通过 vanillaName 获取 Bukkit Attribute 实例（带缓存）
     * 主要尝试直接使用 vanillaName，如果失败则尝试添加 generic. 前缀作为备选
     */
    private fun getVanillaAttribute(vanillaName: String): org.bukkit.attribute.Attribute? {
        return vanillaAttributeCache.getOrPut(vanillaName) {
            try {
                // 首先尝试直接使用 vanillaName
                val key = NamespacedKey.minecraft(vanillaName)
                var attr = Registry.ATTRIBUTE.get(key)

                if (attr == null && !vanillaName.contains('.')) {
                    // 如果直接查找失败且名称不包含点号，尝试添加 generic. 前缀
                    val genericKey = NamespacedKey.minecraft("generic.$vanillaName")
                    attr = Registry.ATTRIBUTE.get(genericKey)
                    if (attr != null) {
                        taboolib.common.platform.function.debug("AttributeManager: found vanilla attribute 'generic.$vanillaName' (fallback)")
                    }
                }

                if (attr == null) {
                    warningS("AttributeManager: vanilla attribute '$vanillaName' not found in registry (tried '$vanillaName' and 'generic.$vanillaName')")
                } else {
                    taboolib.common.platform.function.debug("AttributeManager: found vanilla attribute '$vanillaName'")
                }
                attr
            } catch (e: Exception) {
                warningS("AttributeManager: failed to get vanilla attribute '$vanillaName': ${e.message}")
                null
            }
        }
    }

    /**
     * 为玩家添加持久化修饰符
     */
    fun addModifier(uuid: UUID, modifier: AttributeModifier) {
        taboolib.common.platform.function.debug("AttributeManager: adding modifier for $uuid: ${modifier.attributeId}=${modifier.value} ${modifier.type}")
        val data = getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        mods.add(modifier)
        data.modifiers = mods
        savePlayerData(data)

        val player = Bukkit.getPlayer(uuid)
        if (player != null) {
            taboolib.common.platform.function.debug("AttributeManager: player online, calling doUpdate for ${player.name}")
            doUpdate(player)
        } else {
            taboolib.common.platform.function.debug("AttributeManager: player offline, update will happen on next login")
        }
    }

    fun removeModifierByUuid(uuid: UUID, modifierUuid: UUID) {
        val data = getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        if (mods.removeAll { it.uuid == modifierUuid }) {
            data.modifiers = mods
            savePlayerData(data)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) doUpdate(player)
        }
    }

    fun removeDeathMarkedModifiers(uuid: UUID) {
        val data = getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        val removed = mods.removeAll { it.deathMark }
        if (removed) {
            data.modifiers = mods
            savePlayerData(data)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) doUpdate(player)
        }
    }

    fun removeModifiersByAttribute(uuid: UUID, attributeId: String, source: ModifierSource? = null) {
        val data = getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        val removed = if (source != null) {
            mods.removeAll { it.attributeId == attributeId && it.source == source }
        } else {
            mods.removeAll { it.attributeId == attributeId }
        }
        if (removed) {
            data.modifiers = mods
            savePlayerData(data)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) doUpdate(player)
        }
    }

    private fun getEquipmentModifiers(player: Player): List<AttributeModifier> =
        getEquipmentModifiers(equipmentSnapshot(player, null))

    /** 主线程调用的装备快照(物品均已克隆,可安全交给异步线程解析 NBT) */
    private fun equipmentSnapshot(player: Player, mainHandOverride: ItemStack?): List<Pair<String, ItemStack>> {
        val equipment = player.equipment ?: return emptyList()
        return listOfNotNull(
            "HEAD" to equipment.helmet,
            "CHEST" to equipment.chestplate,
            "LEGS" to equipment.leggings,
            "FEET" to equipment.boots,
            "MAIN_HAND" to (mainHandOverride ?: equipment.itemInMainHand),
            "OFF_HAND" to equipment.itemInOffHand,
        ).mapNotNull { (slot, item) ->
            item?.takeIf { !it.type.isAir }?.let { slot to it.clone() }
        }
    }

    private fun getEquipmentModifiers(snapshot: List<Pair<String, ItemStack>>): List<AttributeModifier> {
        val modifiers = mutableListOf<AttributeModifier>()

        val sourceBySlot = mapOf(
            "HEAD" to ModifierSource.ARMOR,
            "CHEST" to ModifierSource.ARMOR,
            "LEGS" to ModifierSource.ARMOR,
            "FEET" to ModifierSource.ARMOR,
            "MAIN_HAND" to ModifierSource.MAINHAND,
            "OFF_HAND" to ModifierSource.OFFHAND
        )

        for ((actualSlot, item) in snapshot) {
            val chotenatb = readChotenatbTag(item) ?: continue
            val source = sourceBySlot[actualSlot] ?: ModifierSource.OTHER
            for (entry in chotenatb) {
                if (!entry.matchesSlot(actualSlot)) continue
                if (entry.fixAttackSpeed && actualSlot == "MAIN_HAND") {
                    modifiers.add(AttributeModifier(
                        attributeId = "attack_speed",
                        value = -4.0,
                        type = ModifierType.ADD,
                        source = source
                    ))
                }
                for ((attrId, attrValue) in entry.attributes) {
                    modifiers.add(AttributeModifier(
                        attributeId = attrId,
                        value = attrValue,
                        type = ModifierType.ADD,
                        source = source
                    ))
                }
            }
        }
        return modifiers
    }

    fun getAttributeValue(player: Player, attributeId: String): Double {
        val cache = attributeCache[player.uniqueId]
        if (cache != null) {
            val type = attributeRegistry[attributeId]
            return cache[attributeId] ?: type?.clamp(type.defaultValue) ?: 0.0
        }
        val type = attributeRegistry[attributeId]
        return type?.clamp(type.defaultValue) ?: 0.0
    }

    /**
     * 获取任意生物的属性值
     * - Player: 走现有持久化+装备计算缓存
     * - 其他 LivingEntity: 使用独立的实体缓存（更长刷新间隔）
     */
    fun getAttributeValue(entity: LivingEntity, attributeId: String): Double {
        if (entity is Player) {
            return getAttributeValue(entity as Player, attributeId)
        }
        val uuid = entity.uniqueId
        val now = System.currentTimeMillis()
        val lastUpdate = entityCacheTimestamps[uuid] ?: 0L
        if (now - lastUpdate > ENTITY_CACHE_TTL) {
            computeAndCacheEntityAttributes(entity)
        }
        val cache = entityCache[uuid]
        if (cache != null) {
            val type = attributeRegistry[attributeId]
            return cache[attributeId] ?: type?.clamp(type.defaultValue) ?: 0.0
        }
        val type = attributeRegistry[attributeId]
        return type?.clamp(type.defaultValue) ?: 0.0
    }

    /**
     * 从实体装备 NBT（chotenatb 结构）计算所有属性值并缓存
     *
     * NBT 格式：
     * chotenatb: [
     *   {attack_damage: 8, attack_speed: 1.6, slot: "MAIN_HAND", allowed: ["hand"]},
     *   {defense: 5, slot: "HEAD"}
     * ]
     * 其中 slot、allowed 和 fix_attack_speed 为保留键名，
     * fix_attack_speed 为标记位，存在时主手攻击速度 -4。
     */
    private fun computeAndCacheEntityAttributes(entity: LivingEntity) {
        val uuid = entity.uniqueId
        if (!entity.isValid) {
            entityCache.remove(uuid)
            entityCacheTimestamps.remove(uuid)
            return
        }
        val equipment = entity.equipment
        if (equipment == null) {
            entityCache.remove(uuid)
            entityCacheTimestamps.remove(uuid)
            return
        }

        // 实体所有装备槽位及对应的物品
        val slotItems = listOfNotNull(
            "HEAD" to equipment.helmet,
            "CHEST" to equipment.chestplate,
            "LEGS" to equipment.leggings,
            "FEET" to equipment.boots,
            "MAIN_HAND" to equipment.itemInMainHand.takeIf { !it.type.isAir },
            "OFF_HAND" to equipment.itemInOffHand.takeIf { !it.type.isAir }
        )

        val values = mutableMapOf<String, Double>()
        // 初始化所有已注册属性的默认值
        for (type in attributeRegistry.values) {
            values[type.id] = type.defaultValue
        }
        // 遍历装备，解析 chotenatb
        for ((actualSlot, item) in slotItems) {
            val chotenatb = readChotenatbTag(item) ?: continue
            for (entry in chotenatb) {
                if (!entry.matchesSlot(actualSlot)) continue
                if (entry.fixAttackSpeed && actualSlot == "MAIN_HAND") {
                    values["attack_speed"] = (values["attack_speed"] ?: 4.0) - 4.0
                }
                for ((attrId, attrValue) in entry.attributes) {
                    values[attrId] = (values[attrId] ?: 0.0) + attrValue
                }
            }
        }
        // 钳制到合法范围
        for (type in attributeRegistry.values) {
            values[type.id] = type.clamp(values[type.id] ?: type.defaultValue)
        }
        entityCache[uuid] = values
        entityCacheTimestamps[uuid] = System.currentTimeMillis()
    }

    /**
     * chotenatb 条目：属性映射 + 插槽过滤
     */
    private data class ChotenatbEntry(
        val attributes: Map<String, Double>,
        val slot: String,
        val allowed: List<String> = emptyList(),
        val fixAttackSpeed: Boolean = false
    ) {
        fun matchesSlot(actualSlot: String): Boolean {
            if (!matchesSlotInternal(actualSlot)) return false
            if (allowed.isNotEmpty()) return matchesAllowed(actualSlot)
            return true
        }

        private fun matchesSlotInternal(actualSlot: String): Boolean {
            return when (slot) {
                "ANY", "ALL" -> true
                "ANY_HAND", "HAND" -> actualSlot in listOf("MAIN_HAND", "OFF_HAND")
                "MAIN_HAND", "MAINHAND" -> actualSlot == "MAIN_HAND"
                "OFF_HAND", "OFFHAND" -> actualSlot == "OFF_HAND"
                "HEAD", "HELMET" -> actualSlot == "HEAD"
                "CHEST", "CHESTPLATE" -> actualSlot == "CHEST"
                "LEGS", "LEGGINGS" -> actualSlot == "LEGS"
                "FEET", "BOOTS", "FOOT" -> actualSlot == "FEET"
                else -> slot == actualSlot
            }
        }

        private fun matchesAllowed(actualSlot: String): Boolean {
            return allowed.any {
                when (it.uppercase()) {
                    "ANY", "ALL" -> true
                    "HAND", "ANY_HAND" -> actualSlot in listOf("MAIN_HAND", "OFF_HAND")
                    "MAIN_HAND", "MAINHAND" -> actualSlot == "MAIN_HAND"
                    "OFF_HAND", "OFFHAND" -> actualSlot == "OFF_HAND"
                    "HEAD", "HELMET" -> actualSlot == "HEAD"
                    "CHEST", "CHESTPLATE" -> actualSlot == "CHEST"
                    "LEGS", "LEGGINGS" -> actualSlot == "LEGS"
                    "FEET", "BOOTS" -> actualSlot == "FEET"
                    else -> it.uppercase() == actualSlot
                }
            }
        }
    }

    /**
     * 从物品读取 chotenatb 标签，支持多种数值类型
     *
     * NBT 格式（getItemTag 已返回 custom_data 内容）：
     * {attack_damage: 8, slot: "MAIN_HAND", allowed: ["hand"]}
     * slot、allowed 和 fix_attack_speed 为保留键，其余直接作为属性 ID。
     * 数值可以是 int / long / float / double / byte / short / string。
     */
    private fun readChotenatbTag(item: ItemStack): List<ChotenatbEntry>? {
        return try {
            val tag = item.getItemTag()
            val chotenatbData = tag["chotenatb"] ?: return null
            val list = chotenatbData.asList()
            list.mapNotNull { entryTag ->
                val compound = entryTag.asCompound() ?: return@mapNotNull null
                val attrs = mutableMapOf<String, Double>()
                var fixAttackSpeed = false
                for (entry in compound.entries) {
                    val key = entry.key
                    if (key == "slot" || key == "allowed") continue
                    if (key == "fix_attack_speed") {
                        fixAttackSpeed = true
                        continue
                    }
                    val value = entry.value.asDouble() ?: entry.value.asString()?.toDoubleOrNull()
                    if (value != null) {
                        attrs[key] = value
                    }
                }
                if (attrs.isEmpty() && !fixAttackSpeed) return@mapNotNull null
                val slot = (compound["slot"]?.asString() ?: "ANY").uppercase()
                val allowed = compound["allowed"]?.asList()?.mapNotNull { it.asString() }
                    ?: listOfNotNull(compound["allowed"]?.asString())
                ChotenatbEntry(attrs, slot, allowed, fixAttackSpeed)
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 刷新实体属性缓存（后台定时调用）
     * 仅刷新缓存中已有的实体（被查询过的），不遍历全图生物
     * 同时清理超过最大缓存数的旧条目，防止内存泄漏
     */
    private fun refreshEntityCache() {
        val now = System.currentTimeMillis()
        for ((uuid, _) in entityCache) {
            // 只刷新超过 TTL 的实体
            val lastUpdate = entityCacheTimestamps[uuid] ?: 0L
            if (now - lastUpdate < ENTITY_CACHE_TTL) continue

            val entity = Bukkit.getEntity(uuid)
            if (entity !is LivingEntity || !entity.isValid) {
                entityCache.remove(uuid)
                entityCacheTimestamps.remove(uuid)
                continue
            }
            computeAndCacheEntityAttributes(entity)
        }
        // 如果缓存超过最大限制，淘汰最早的条目
        if (entityCache.size > ENTITY_CACHE_MAX_SIZE) {
            val overflow = entityCache.size - ENTITY_CACHE_MAX_SIZE
            val toEvict = entityCacheTimestamps.entries
                .sortedBy { it.value }
                .take(overflow)
                .map { it.key }
            for (uuid in toEvict) {
                entityCache.remove(uuid)
                entityCacheTimestamps.remove(uuid)
            }
        }
    }

    fun clearCache(uuid: UUID) {
        persistentDataCache.remove(uuid.toString())
        attributeCache.remove(uuid)
        entityCache.remove(uuid)
        entityCacheTimestamps.remove(uuid)
    }
}
