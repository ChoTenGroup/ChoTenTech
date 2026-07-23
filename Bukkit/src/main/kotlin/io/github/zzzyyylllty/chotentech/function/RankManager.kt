package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.RankConfig
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.chotentech.kether.evalKetherBoolean
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submitAsync
import taboolib.module.configuration.Configuration
import java.io.File

object RankManager {

    val ranks: LinkedHashMap<String, LinkedHashMap<String, RankConfig>> = linkedMapOf()

    private var checkTask: Any? = null

    fun load() {
        ranks.clear()

        val rankDir = File(getDataFolder(), "ranks")
        if (!rankDir.exists()) {
            releaseResourceFolder("ranks")
        }

        val typeDirs = rankDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        for (typeDir in typeDirs) {
            val type = typeDir.name
            val typeRanks = linkedMapOf<String, RankConfig>()
            val files = mutableListOf<File>()
            collectYamlFiles(typeDir, files)

            for (file in files) {
                try {
                    val conf = Configuration.loadFromFile(file)
                    for (key in conf.getKeys(false)) {
                        val section = conf.getConfigurationSection(key) ?: continue
                        val displaySection = section.getConfigurationSection("display")
                        val display = linkedMapOf<String, String>()
                        if (displaySection != null) {
                            for (dk in displaySection.getKeys(false)) {
                                display[dk] = displaySection.getString(dk) ?: ""
                            }
                        }
                        val condition = section.getString("condition") ?: "true"
                        typeRanks[key] = RankConfig(
                            id = key,
                            type = type,
                            display = display,
                            name = section.getString("name") ?: key,
                            desc = section.getString("desc") ?: "",
                            get = section.getString("get") ?: "",
                            condition = condition,
                            priority = section.getInt("priority", 0)
                        )
                    }
                } catch (e: Exception) {
                    devLog("[称号] 加载文件 ${file.name} 失败: ${e.message}")
                }
            }

            if (typeRanks.isNotEmpty()) {
                ranks[type] = typeRanks
                devLog("[称号] 已加载 ${typeRanks.size} 个 ${type} 称号")
            }
        }

        devLog("[称号] 共加载 ${ranks.values.sumOf { it.size }} 个称号")
        startAutoCheck()
    }

    fun unload() {
        stopAutoCheck()
        ranks.clear()
    }

    private fun collectYamlFiles(dir: File, result: MutableList<File>) {
        val list = dir.listFiles() ?: return
        for (file in list) {
            if (file.isDirectory) {
                collectYamlFiles(file, result)
            } else if (file.name.endsWith(".yml") || file.name.endsWith(".yaml")) {
                result.add(file)
            }
        }
    }

    /** Ranks the player can manually select (based on permission or condition check) */
    fun getEligibleRanks(player: Player, type: String): List<RankConfig> {
        val typeRanks = ranks[type] ?: return emptyList()
        return typeRanks.values.filter { checkCondition(player, it) }
            .sortedByDescending { it.priority }
    }

    fun getPlayerActiveRank(player: Player, type: String): RankConfig? {
        val shared = ProfileManager.getSharedData(player.uniqueId.toString())
        val activeId = shared.activeRanks[type] ?: return null
        return ranks[type]?.get(activeId)
    }

    fun setPlayerRank(player: Player, type: String, rankId: String?): Boolean {
        val shared = ProfileManager.getSharedData(player.uniqueId.toString())
        if (rankId == null) {
            shared.activeRanks.remove(type)
            return true
        }
        val rank = ranks[type]?.get(rankId) ?: return false
        if (!checkCondition(player, rank)) return false
        shared.activeRanks[type] = rankId
        return true
    }

    fun getRankDisplay(player: Player, type: String, displayKey: String = "current"): String {
        val rank = getPlayerActiveRank(player, type)
        return rank?.display?.get(displayKey) ?: ""
    }

    fun getRankName(player: Player, type: String): String {
        return getPlayerActiveRank(player, type)?.name ?: ""
    }

    fun getRankDesc(player: Player, type: String): String {
        return getPlayerActiveRank(player, type)?.desc ?: ""
    }

    fun checkCondition(player: Player, rank: RankConfig): Boolean {
        // Permission override: chotentech.rank.<type>.<id> bypasses condition check
        val perm = "chotentech.rank.${rank.type}.${rank.id}"
        if (player.hasPermission(perm)) return true
        // Fall back to condition field
        return rank.condition.evalKetherBoolean(player)
    }

    private fun startAutoCheck() {
        stopAutoCheck()
        checkTask = submitAsync(period = 1200L) {
            for (player in Bukkit.getOnlinePlayers()) {
                try {
                    checkPlayerRanks(player)
                } catch (_: Exception) {}
            }
        }
        devLog("[称号] 条件检测任务已启动 (每 60 秒)")
    }

    private fun stopAutoCheck() {
        if (checkTask is org.bukkit.scheduler.BukkitTask) {
            (checkTask as org.bukkit.scheduler.BukkitTask).cancel()
        }
        checkTask = null
    }

    fun checkPlayerRanks(player: Player) {
        val shared = ProfileManager.getSharedData(player.uniqueId.toString())
        var changed = false
        val iterator = shared.activeRanks.entries.iterator()
        while (iterator.hasNext()) {
            val (type, rankId) = iterator.next()
            val rank = ranks[type]?.get(rankId)
            if (rank == null) {
                iterator.remove()
                changed = true
                continue
            }
            if (!checkCondition(player, rank)) {
                iterator.remove()
                changed = true
                player.sendMessage(
                    " <#ff6666>你的称号「${rank.name}」因条件不满足已自动撤下。"
                        .toComponent()
                )
                devLog("[称号] 玩家 ${player.name} 的 ${type} 称号「${rank.name}」条件不满足，已自动撤下")
            }
        }
        if (changed) {
            player.playerListName(buildPlayerListName(player).toComponent())
        }
    }

    fun buildPlayerListName(player: Player): String {
        val prefix = getRankDisplay(player, "prefix")
        val suffix = getRankDisplay(player, "suffix")
        return "$prefix${player.name}$suffix"
    }

    /** Get display string from the highest-priority eligible rank (condition-based only) */
    fun getEligibleRankDisplay(player: Player, type: String, displayKey: String = "current"): String {
        return getEligibleRanks(player, type).firstOrNull()?.display?.get(displayKey) ?: ""
    }

    fun getTypes(): Set<String> = ranks.keys
}
