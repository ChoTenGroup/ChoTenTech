package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.Chemdah
import ink.ptms.chemdah.core.Data
import ink.ptms.chemdah.core.DataContainer
import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.SimpleDataContainer
import ink.ptms.chemdah.core.database.Database
import ink.ptms.chemdah.core.quest.Quest
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Awake(LifeCycle.LOAD)
fun initChotenChemdahProfile() {
    Chemdah.registerDatabaseImpl<Database>(ChotenChemdahProfile())
    ChotenChemdahProfile.loadVariables()
}

@Awake(LifeCycle.DISABLE)
fun saveChotenChemdahVariables() {
    ChotenChemdahProfile.saveVariables()
}

class ChotenChemdahProfile : Database() {

    companion object {

        private val variables = ConcurrentHashMap<String, String>()
        private const val VAR_FILE = "chemdah_variables.json"

        fun loadVariables() {
            val file = File(getDataFolder(), VAR_FILE)
            if (!file.exists()) return
            try {
                @Suppress("UNCHECKED_CAST")
                val map = jsonUtils.fromJson(file.readText(), Map::class.java) as? Map<String, String>
                if (map != null) variables.putAll(map)
            } catch (_: Exception) {}
        }

        fun saveVariables() {
            try {
                File(getDataFolder(), VAR_FILE).writeText(jsonUtils.toJson(variables))
            } catch (_: Exception) {}
        }
    }

    // ==================== Player Profile ====================

    override fun select(player: Player): PlayerProfile {
        val profile = PlayerProfile(player.uniqueId)
        profile.setup()

        val uuid = player.uniqueId.toString()
        val pData = playerDataMap[uuid] ?: DataManager.getData(uuid)
        if (pData == null) return profile

        // 恢复 profile 级数据容器
        if (!pData.chemdahProfileData.isNullOrBlank()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val map = jsonUtils.fromJson(pData.chemdahProfileData!!, Map::class.java) as? Map<String, Any>
                if (map != null) {
                    profile.persistentDataContainer.unchanged { dc ->
                        map.forEach { (k, v) -> dc[k] = v }
                    }
                }
            } catch (_: Exception) {}
        }

        // 恢复活跃任务
        if (!pData.chemdahQuestData.isNullOrBlank()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val questMap = jsonUtils.fromJson(pData.chemdahQuestData!!, Map::class.java) as? Map<String, Map<String, Any>>
                if (questMap != null) {
                    questMap.forEach { (questId, questData) ->
                        try {
                            val dataMap = questData.mapValues { (_, v) -> Data.Companion.unsafe(v) }
                            val questContainer = SimpleDataContainer(dataMap)
                            profile.registerQuest(Quest(questId, profile, questContainer), newQuest = false)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        return profile
    }

    override fun update(player: Player, playerProfile: PlayerProfile) {
        val pData = playerDataMap[player.uniqueId.toString()] ?: return

        // 保存 profile 数据容器（Map -> JSON）
        pData.chemdahProfileData = jsonUtils.toJson(playerProfile.persistentDataContainer.toMap())

        // 保存活跃任务数据
        val questMap = linkedMapOf<String, Map<String, Any>>()
        playerProfile.getQuests().forEach { quest ->
            quest.newQuest = false
            questMap[quest.id] = quest.persistentDataContainer.toMap()
            quest.persistentDataContainer.flush()
        }
        pData.chemdahQuestData = if (questMap.isNotEmpty()) jsonUtils.toJson(questMap) else null

        // 重置变更追踪
        playerProfile.persistentDataContainer.flush()
        playerProfile.releaseQuests.clear()
    }

    override fun releaseQuest(player: Player, playerProfile: PlayerProfile, quest: Quest) {
        // 任务已完成并从 questMap 移除，完成时间已记录在 profile 的 DataContainer 中
        // 下次 update() 会自动序列化最新状态，无需额外操作
    }

    // ==================== Global Variables ====================

    override fun selectVariable0(key: String): String? = variables[key]
    override fun updateVariable0(key: String, value: String) { variables[key] = value }
    override fun releaseVariable0(key: String) { variables.remove(key) }
    override fun variables(): List<String> = variables.keys.toList()
}
