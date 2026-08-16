package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.expansion.Id
import taboolib.expansion.Length
import taboolib.expansion.TableName
import taboolib.expansion.db
import taboolib.expansion.mapper
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 聊天玩家数据（数据库表 chat_player_data） */
@TableName("chat_player_data")
class ChatPlayerRecord {
    @Id
    var uuid: String = ""
    var activeChannel: String = "global"
    @Length(-1)
    var ignoredJson: String = "[]"
    var lastWhisperTarget: String = ""
}

/** 玩家聊天的内存状态（与数据库记录一一对应） */
class ChatPlayerData(val uuid: UUID) {
    var activeChannel: String = "global"
    val ignoredPlayers = mutableSetOf<UUID>()
    var lastWhisperTarget: UUID? = null

    @Volatile
    var persisted: Boolean = false
}

object ChatPlayerDataManager {

    val chatPlayerMapper by mapper<ChatPlayerRecord>(db("config.yml", "database", "chotentech.db"))

    private val cache = ConcurrentHashMap<UUID, ChatPlayerData>()

    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        // 异步加载，避免 MySQL 网络往返阻塞主线程
        submitAsync { get(event.player.uniqueId) }
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        save(uuid)
        cache.remove(uuid)
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        // 停服阶段调度器可能不再执行异步任务，必须同步写库否则最后一次变更会丢失
        cache.forEach { (uuid, data) ->
            runCatching { writeSync(uuid, data) }
                .onFailure { warning("ChoTenTech: Failed to save chat data for $uuid: $it") }
        }
    }

    fun get(player: Player): ChatPlayerData = get(player.uniqueId)

    fun get(uuid: UUID): ChatPlayerData = cache.computeIfAbsent(uuid) { uid ->
        val record = runCatching {
            chatPlayerMapper.findOne { "uuid" eq uid.toString() }
        }.getOrNull()
        ChatPlayerData(uid).apply {
            if (record != null) {
                activeChannel = record.activeChannel.ifBlank { "global" }
                ignoredPlayers.addAll(
                    runCatching {
                        jsonUtils.fromJson(record.ignoredJson, Array<String>::class.java)?.toList() ?: emptyList()
                    }.getOrDefault(emptyList()).mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                )
                lastWhisperTarget = runCatching { UUID.fromString(record.lastWhisperTarget) }.getOrNull()
                persisted = true
            }
        }
    }

    /** 变更后调用，异步写库。首次为 insert，之后为 update。 */
    fun save(uuid: UUID) {
        val data = cache[uuid] ?: return
        submitAsync {
            runCatching { writeSync(uuid, data) }
                .onFailure { warning("ChoTenTech: Failed to save chat data for $uuid: $it") }
        }
    }

    private fun writeSync(uuid: UUID, data: ChatPlayerData) {
        val record = ChatPlayerRecord().apply {
            this.uuid = uuid.toString()
            activeChannel = data.activeChannel
            ignoredJson = jsonUtils.toJson(data.ignoredPlayers.map { it.toString() })
            lastWhisperTarget = data.lastWhisperTarget?.toString() ?: ""
        }
        if (data.persisted) {
            chatPlayerMapper.update(record)
        } else {
            chatPlayerMapper.insert(record)
            data.persisted = true
        }
    }
}
