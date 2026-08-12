package io.github.zzzyyylllty.chotentech.data.backpack

import taboolib.expansion.ColumnType
import taboolib.expansion.Id
import taboolib.expansion.Length
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite

/**
 * PTC Object 数据类 — 映射数据库表 backpack_data
 *
 * @Id backpackId  背包物品 UUID（每个物理背包唯一）
 */
data class PlayerBackpack(
    @Id
    @Length(64)
    val backpackId: String,

    /** 背包类型 ID（如 "default", "vip"） */
    @Length(32)
    val type: String,

    /** 序列化后的背包物品 JSON，LONGTEXT 避免长度限制 */
    @ColumnType(sql = ColumnTypeSQL.LONGTEXT, sqlite = ColumnTypeSQLite.TEXT)
    @Length(-1)
    var content: String,

    /** 该背包的格子数 */
    var slotCount: Int,

    /** 是否因疑似刷物被封禁 */
    var disabled: Boolean = false,

    /** 打开过该背包的玩家 UUID 列表（JSON） */
    @Length(-1)
    var openedBy: String = "[]",

    /** 涉嫌刷物的玩家 UUID 列表（JSON） */
    @Length(-1)
    var suspectedPlayers: String = "[]"
)
