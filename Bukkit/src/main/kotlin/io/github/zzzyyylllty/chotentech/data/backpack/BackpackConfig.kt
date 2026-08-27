package io.github.zzzyyylllty.chotentech.data.backpack

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.util.ItemTagUtil.parseNBT
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.info
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.nms.getItemTag

/**
 * 单个背包类型的配置定义
 */
data class BackpackType(
    val id: String,
    val display: String,
    val slots: Int,
    val condition: BackpackCondition,
    /** GUI 布局模板，每行 9 字符：O=储物格 X=锁定格，null 则按前 slots 格为储物格 */
    val layout: List<String>? = null
) {
    /** 将布局解析为 格子索引 -> 字符 的映射 */
    fun parseLayout(): Map<Int, Char>? {
        val layout = layout ?: return null
        return layout.flatMapIndexed { row, line ->
            line.mapIndexed { col, char -> row * 9 + col to char }
        }.toMap()
    }
}

/**
 * 一条条件规则：物品满足全部 [tagConditions] 时执行 [action]。
 * action: "allow" 允许放入 / "deny" 拒绝放入
 */
data class BackpackConditionRule(
    val tagConditions: Map<String, List<String>>,
    val action: String
) {
    /** 判断物品是否命中本规则（所有标签条件必须同时满足） */
    fun matches(item: ItemStack): Boolean {
        for ((rawKey, allowed) in tagConditions) {
            val actual = readTagValue(item, rawKey) ?: return false
            // 允许值列表为空：仅要求该标签存在
            if (allowed.isEmpty()) continue
            val hit = when (actual) {
                is List<*> -> actual.any { it?.toString() in allowed }
                else -> actual.toString() in allowed
            }
            if (!hit) return false
        }
        return true
    }

    companion object {
        /**
         * 读取物品标签值。
         * 键在配置中可能写作 "sertraline:id"（命名空间）或 "sertraline_id"（下划线），
         * NBT 实际存储形式不固定，因此两种写法都尝试。
         * sertraline 物品 ID 优先走官方 API，保证可靠。
         */
        private fun readTagValue(item: ItemStack, rawKey: String): Any? {
            val normalized = rawKey.replace(':', '_')
            if (normalized == "sertraline_id") {
                try {
                    io.github.zzzyyylllty.sertraline.Sertraline.api().getId(item)?.let { return it }
                } catch (_: Exception) {
                    // API 不可用则退回原始标签读取
                }
            }
            val variants = if (':' in rawKey) {
                listOf(rawKey, rawKey.replace(':', '_'))
            } else {
                listOf(rawKey, rawKey.replace('_', ':'))
            }
            val tag = item.getItemTag()
            for (variant in variants) {
                tag[variant]?.let { return it.parseNBT() }
            }
            return null
        }
    }
}

/**
 * 背包条件系统：从上到下依次匹配规则，第一个命中的规则决定允许/拒绝。
 * 没有任何规则命中时视为拒绝。
 */
data class BackpackCondition(
    val rules: List<BackpackConditionRule>
) {
    /** 空条件 = 不限制任何物品 */
    fun evaluate(item: ItemStack): Boolean {
        if (rules.isEmpty()) return true
        for (rule in rules) {
            if (rule.matches(item)) return rule.action == "allow"
        }
        return false
    }
}

/**
 * 背包系统的全局配置
 */
object BackpackConfig {

    /** ItemTag 键名，物品通过此标签标记为背包打开物品 */
    var tag: String = "cho-backpack"
        private set

    /** 背包 UUID 的 ItemTag 键名，每个物理背包物品有唯一标识 */
    val uuidTag: String get() = "$tag-uuid"

    /** GUI 行数（默认 6 行 = 54 格） */
    var rows: Int = 6
        private set

    /** 拒绝放入时的提示消息 (MiniMessage 格式) */
    var denySlotMessage: String = "<#E08A98>该物品不能放入此背包！"
        private set

    /** 所有已注册的背包类型 */
    val types = mutableMapOf<String, BackpackType>()

    /** 从独立配置文件 backpack.yml 加载（已在 ChoTenTech 注册 @Config 自动释放资源） */
    fun load() {
        val section = ChoTenTech.backpackConfig.getConfigurationSection("backpack") ?: return

        tag = section.getString("tag", "cho-backpack") ?: "cho-backpack"
        denySlotMessage = section.getString("deny-slot-message", "<#E08A98>该物品不能放入此背包！") ?: "<#E08A98>该物品不能放入此背包！"
        rows = section.getInt("rows", 6).coerceIn(1, 6)

        types.clear()
        val typesSection = section.getConfigurationSection("types") ?: return
        for (key in typesSection.getKeys(false)) {
            val typeSection = typesSection.getConfigurationSection(key) ?: continue
            val display = typeSection.getString("display", "&6背包") ?: "&6背包"
            val slots = typeSection.getInt("slots", 27).coerceIn(1, 54)
            val condition = parseCondition(typeSection)
            val layoutRaw = typeSection.getStringList("layout").takeIf { it.isNotEmpty() }
            types[key] = BackpackType(key, display, slots, condition, layoutRaw)
            info("  Loaded backpack type: $key (${slots} slots, ${condition.rules.size} condition rule(s)${if (layoutRaw != null) ", layout" else ""})")
        }
    }

    fun getType(id: String): BackpackType? = types[id]

    // ==================== 条件解析 ====================

    /**
     * 解析条件列表（从上到下匹配，第一条命中生效，action 为 allow/deny）：
     *
     *   condition:
     *     - if:
     *         tag:
     *           sertraline:id: "diamond"
     *       action: deny
     *     - if:
     *         tag:
     *           sertraline_type: |-
     *             material
     *             medicinal
     *       action: allow
     *     - action: deny
     */
    private fun parseCondition(typeSection: ConfigurationSection): BackpackCondition {
        val rules = mutableListOf<BackpackConditionRule>()
        for (raw in typeSection.getMapList("condition")) {
            val action = (raw["action"]?.toString() ?: "deny").lowercase()
            val tagMap = (raw["if"] as? Map<*, *>)?.get("tag") as? Map<*, *>
            val tagConditions = linkedMapOf<String, List<String>>()
            if (tagMap != null) {
                for ((key, value) in tagMap) {
                    tagConditions[key.toString()] = parseTagValues(value ?: "")
                }
            }
            rules += BackpackConditionRule(tagConditions, action)
        }
        return BackpackCondition(rules)
    }

    /**
     * 标签期望值：支持单个标量 / YAML 列表 / 多行字符串（每行一个允许值）。
     *   sertraline:id: "diamond"          → ["diamond"]
     *   sertraline_type: |-               → ["material", "medicinal"]
     *     material
     *     medicinal
     */
    private fun parseTagValues(value: Any): List<String> = when (value) {
        is List<*> -> value.mapNotNull { it?.toString() }
        is String -> value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        else -> listOf(value.toString())
    }
}
