package io.github.zzzyyylllty.chotentech.data.backpack

import io.github.zzzyyylllty.chotentech.ChoTenTech
import taboolib.common.platform.function.info

/**
 * 单个背包类型的配置定义
 */
data class BackpackType(
    val id: String,
    val display: String,
    val slots: Int,
    val condition: String,
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
    var denySlotMessage: String = "<red>该物品不能放入此背包！"
        private set

    /** 所有已注册的背包类型 */
    val types = mutableMapOf<String, BackpackType>()

    fun load() {
        val config = ChoTenTech.config
        val section = config.getConfigurationSection("backpack") ?: return

        tag = section.getString("tag", "cho-backpack") ?: "cho-backpack"
        denySlotMessage = section.getString("deny-slot-message", "<red>该物品不能放入此背包！") ?: "<red>该物品不能放入此背包！"
        rows = section.getInt("rows", 6).coerceIn(1, 6)

        types.clear()
        val typesSection = section.getConfigurationSection("types") ?: return
        for (key in typesSection.getKeys(false)) {
            val typeSection = typesSection.getConfigurationSection(key) ?: continue
            val display = typeSection.getString("display", "&6背包") ?: "&6背包"
            val slots = typeSection.getInt("slots", 27).coerceIn(1, 54)
            val condition = typeSection.getString("condition", "true") ?: "true"
            val layoutRaw = typeSection.getStringList("layout").takeIf { it.isNotEmpty() }
            types[key] = BackpackType(key, display, slots, condition, layoutRaw)
            info("  Loaded backpack type: $key (${slots} slots${if (layoutRaw != null) ", layout" else ""})")
        }
    }

    fun getType(id: String): BackpackType? = types[id]
}
