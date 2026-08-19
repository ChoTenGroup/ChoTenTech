package io.github.zzzyyylllty.chotentech.data

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BaseStartItemsConfig {

    @Config("base-start-items.yml")
    lateinit var config: Configuration

    /** 基地初始物资配置:IDEF -> (物品ID, 数量)。物品 ID 支持 sertraline:xxx / 原版 / XSeries */
    fun getStartItems(idef: String): List<Pair<String, Int>> {
        val raw = config.getList("base-start-items.$idef") ?: return emptyList()
        val result = mutableListOf<Pair<String, Int>>()
        for (entry in raw) {
            when (entry) {
                is String -> result.add(entry.trim() to 1)
                is Map<*, *> -> {
                    val item = entry["item"] as? String ?: continue
                    val amount = (entry["amount"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
                    result.add(item.trim() to amount)
                }
            }
        }
        return result
    }
}
