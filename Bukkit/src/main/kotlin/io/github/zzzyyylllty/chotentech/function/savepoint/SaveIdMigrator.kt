package io.github.zzzyyylllty.chotentech.function.savepoint

import io.github.zzzyyylllty.chotentech.data.PlayerData

/**
 * 存档点 ID 迁移:旧版存档点以中文名作 ID,新版改为英文 ID(name 不变)。
 * 玩家数据中失效的旧 ID 按 name 匹配新存档点,替换为其英文 ID;匹配不到则保留原值。
 */
object SaveIdMigrator {

    /** 转换单个 ID:已是有效 ID 原样返回;否则按 name(=旧 ID)查找同 name 存档点,优先同类型 */
    fun migrateId(id: String?, isBase: Boolean): String? {
        if (id.isNullOrBlank()) return null
        if (SavePointConfig.get(id) != null) return id
        val candidates = SavePointConfig.getAll().filter { it.name == id }
        return (candidates.firstOrNull { it.isBase == isBase } ?: candidates.firstOrNull())?.id ?: id
    }

    /** 就地转换 PlayerData 的 baseSave/normalSave,返回是否有改动 */
    fun convert(data: PlayerData): Boolean {
        var changed = false
        val newBase = migrateId(data.baseSave, isBase = true)
        if (newBase != data.baseSave) {
            data.baseSave = newBase
            changed = true
        }
        val newNormal = migrateId(data.normalSave, isBase = false)
        if (newNormal != data.normalSave) {
            data.normalSave = newNormal
            changed = true
        }
        return changed
    }
}
