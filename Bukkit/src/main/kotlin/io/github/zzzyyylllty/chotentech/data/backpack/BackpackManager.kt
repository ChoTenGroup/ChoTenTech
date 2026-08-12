package io.github.zzzyyylllty.chotentech.data.backpack

import com.google.gson.reflect.TypeToken
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.inventory.ItemStack
import taboolib.expansion.db
import taboolib.expansion.mapper
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BackpackManager {

    /** PTC Object Mapper: 自动建表、读写数据库（沿用独立插件时代的 backpack.db 保留既有数据） */
    val mapper by mapper<PlayerBackpack>(db("config.yml", "database", "backpack.db")) {
        cache {
            beanCache { maximumSize = 500; expireAfterWrite = 600 }
            queryCache { maximumSize = 100; expireAfterWrite = 300 }
        }
    }

    /** Gson 单例（serializeNulls，空槽位序列化为 null 与旧 Jackson 格式一致） */
    private val gson = jsonUtils

    /** Base64 编码器/解码器 */
    private val base64 = Base64.getEncoder()
    private val base64Decode = Base64.getDecoder()

    /**
     * 当前正在被打开的背包（itemUUID -> playerUUID）
     * 用于检测并发刷物：两个玩家同时打开同一 UUID 的背包
     */
    val activeSessions = ConcurrentHashMap<String, UUID>()

    // ==================== 数据库 CRUD ====================

    /**
     * 获取（或创建）指定 UUID 背包的记录
     */
    fun getOrCreateBackpack(itemUUID: String, typeId: String, slotCount: Int): PlayerBackpack {
        val key = PlayerBackpack(itemUUID, typeId, "[]", slotCount)
        mapper.findOneByKey(key)?.let { return it }

        val bp = PlayerBackpack(itemUUID, typeId, createEmptyContent(slotCount), slotCount)
        mapper.insert(bp)
        return bp
    }

    /** 保存背包物品到数据库 */
    fun saveBackpack(itemUUID: String, typeId: String, contents: Array<ItemStack?>, slotCount: Int) {
        val jsonContent = serializeContents(contents)
        val key = PlayerBackpack(itemUUID, typeId, jsonContent, slotCount)
        // updateByKey 以 key 对象覆盖全部列：先加载现有记录，保留 disabled/openedBy/suspectedPlayers
        val existing = mapper.findOneByKey(key)
        if (existing != null) {
            mapper.updateByKey(existing.copy(content = jsonContent))
        } else {
            mapper.updateByKey(key)
        }
    }

    /** 更新背包的任意字段（如 disabled、openedBy） */
    fun updateBackpack(backpack: PlayerBackpack) {
        mapper.updateByKey(backpack)
    }

    // ==================== 物品序列化 ====================

    fun serializeContents(contents: Array<ItemStack?>): String {
        return gson.toJson(contents.map { item ->
            item?.serializeAsBytes()?.let { base64.encodeToString(it) }
        })
    }

    fun deserializeContents(data: String, size: Int): Array<ItemStack?> {
        if (data.isBlank() || data == "[]" || data == "[null]") {
            return arrayOfNulls(size)
        }

        // 新版 Base64 格式
        try {
            val list: List<String?> = gson.fromJson(data, object : TypeToken<List<String?>>() {}.type)
            val result = arrayOfNulls<ItemStack>(size)
            list.forEachIndexed { index, encoded ->
                if (index < size && encoded != null) {
                    result[index] = try {
                        ItemStack.deserializeBytes(base64Decode.decode(encoded))
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            return result
        } catch (_: Exception) {
            // 向下兼容旧版 Map 格式
        }

        // 旧版格式
        val list: List<Map<String, Any>?> = try {
            gson.fromJson(data, object : TypeToken<List<Map<String, Any>?>>() {}.type)
        } catch (_: Exception) {
            return arrayOfNulls(size)
        }
        val result = arrayOfNulls<ItemStack>(size)
        list.forEachIndexed { index, map ->
            if (index < size && map != null) {
                result[index] = try {
                    ItemStack.deserialize(map)
                } catch (_: Exception) {
                    null
                }
            }
        }
        return result
    }

    // ==================== 刷物检测 ====================

    /**
     * 将玩家加入背包的打开记录
     * @param force 强制加入（即使已记录也更新到 DB）
     */
    fun addOpener(backpack: PlayerBackpack, playerUuid: UUID): PlayerBackpack {
        val list = try {
            gson.fromJson<List<String>>(backpack.openedBy, object : TypeToken<List<String>>() {}.type).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
        val uuidStr = playerUuid.toString()
        if (uuidStr !in list) {
            list.add(uuidStr)
            return backpack.copy(openedBy = gson.toJson(list))
        }
        return backpack
    }

    /**
     * 标记刷物嫌疑：将当前玩家加入 suspectedPlayers 并禁用背包
     */
    fun flagDupe(backpack: PlayerBackpack, currentPlayer: UUID, otherPlayer: UUID): PlayerBackpack {
        val suspected = try {
            gson.fromJson<List<String>>(backpack.suspectedPlayers, object : TypeToken<List<String>>() {}.type).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
        for (uuid in setOf(currentPlayer.toString(), otherPlayer.toString())) {
            if (uuid !in suspected) suspected.add(uuid)
        }
        return backpack.copy(
            disabled = true,
            suspectedPlayers = gson.toJson(suspected)
        )
    }

    // ==================== 工具 ====================

    fun createEmptyContent(slotCount: Int): String {
        return gson.toJson(arrayOfNulls<Any?>(slotCount))
    }
}
