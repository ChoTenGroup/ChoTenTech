package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.Achievement
import io.github.zzzyyylllty.chotentech.data.AchievementProgress
import io.github.zzzyyylllty.chotentech.data.Criterion
import io.github.zzzyyylllty.chotentech.data.Operator
import io.github.zzzyyylllty.chotentech.util.NotificationQueue
import io.github.zzzyyylllty.chotentech.util.devLog
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import java.io.File

object AchievementManager {

    val achievements: LinkedHashMap<String, Achievement> = linkedMapOf()
    val categories: LinkedHashMap<String, CategoryDef> = linkedMapOf()

    data class CategoryDef(val name: String, val icon: String)

    data class AchievementEntry(
        val id: String,
        val achievement: Achievement,
        val progress: AchievementProgress?
    )

    private var checkTask: PlatformExecutor.PlatformTask? = null

    fun load(files: List<File>) {
        achievements.clear()
        categories.clear()

        for (file in files) {
            if (!file.name.endsWith(".yml") && !file.name.endsWith(".yaml")) continue
            try {
                val conf = Configuration.loadFromFile(file)
                // 加载分类
                conf.getConfigurationSection("categories")?.let { catSection ->
                    for (key in catSection.getKeys(false)) {
                        val section = catSection.getConfigurationSection(key)
                        if (section != null) {
                            categories[key] = CategoryDef(
                                name = section.getString("name") ?: key,
                                icon = section.getString("icon") ?: "BARRIER"
                            )
                        }
                    }
                }
                // 加载成就
                conf.getConfigurationSection("achievements")?.let { achSection ->
                    for (key in achSection.getKeys(false)) {
                        try {
                            val section = achSection.getConfigurationSection(key) ?: continue
                            val criteria = parseCriteria(section.getConfigurationSection("criteria"))
                            achievements[key] = Achievement(
                                id = key,
                                name = section.getString("name") ?: key,
                                description = section.getStringList("description"),
                                icon = section.getString("icon") ?: "BARRIER",
                                model = section.getString("model"),
                                doneModel = section.getString("done-model") ?: section.getString("doneModel"),
                                lockedModel = section.getString("locked-model") ?: section.getString("lockedModel"),
                                category = section.getString("category") ?: "EXPLORATION",
                                howto = section.getStringList("howto"),
                                criteria = criteria
                            )
                        } catch (e: Exception) {
                            devLog("[成就] 文件 ${file.name} 中加载成就 $key 失败: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                devLog("[成就] 加载文件 ${file.name} 失败: ${e.message}")
            }
        }

        devLog("[成就] 已加载 ${achievements.size} 个成就 (来自 ${files.size} 个文件), ${categories.size} 个分类")
        startAutoCheck()
    }

    fun unload() {
        stopAutoCheck()
        NotificationQueue.clearAll()
        achievements.clear()
        categories.clear()
    }

    // ========== 准则解析 ==========

    private fun parseCriteria(section: taboolib.library.configuration.ConfigurationSection?): List<Criterion> {
        if (section == null) return listOf(Criterion.Impossible)

        val list = mutableListOf<Criterion>()
        for (key in section.getKeys(false)) {
            val entry = section.getConfigurationSection(key) ?: continue
            val type = entry.getString("type") ?: "impossible"
            when (type.lowercase()) {
                "papi" -> {
                    val placeholder = entry.getString("placeholder") ?: ""
                    val operator = entry.getString("operator") ?: "=="
                    val value = entry.getString("value") ?: ""
                    if (placeholder.isNotBlank()) {
                        list.add(
                            Criterion.PAPI(
                                placeholder = placeholder,
                                operator = Operator.fromSymbol(operator),
                                value = value
                            )
                        )
                    }
                }
                "impossible" -> {
                    list.add(Criterion.Impossible)
                }
            }
        }
        return list.ifEmpty { listOf(Criterion.Impossible) }
    }

    // ========== 定时自动检测 ==========

    private fun startAutoCheck() {
        stopAutoCheck()
        val hasAuto = achievements.values.any { ach -> ach.criteria.any { it is Criterion.PAPI } }
        if (!hasAuto) return
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            devLog("[成就] PlaceholderAPI 未启用，跳过自动检测")
            return
        }

        checkTask = submit(period = 100L) {
            for (player in Bukkit.getOnlinePlayers()) {
                tryAutoComplete(player)
            }
        }
        devLog("[成就] 自动检测任务已启动 (每 5 秒)")
    }

    private fun stopAutoCheck() {
        checkTask?.cancel()
        checkTask = null
    }

    /** 尝试自动完成该玩家所有可自动完成的成就 */
    fun tryAutoComplete(player: Player) {
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        for ((id, achievement) in achievements) {
            if (data.achievements[id]?.completed == true) continue
            if (achievement.criteria.none { it is Criterion.PAPI }) continue
            if (checkCriteria(player, achievement)) {
                data.achievements[id] = AchievementProgress(
                    completed = true,
                    completedTime = System.currentTimeMillis()
                )
                devLog("[成就] 玩家 ${player.name} 自动完成成就: ${achievement.name}")
                NotificationQueue.enqueue(player.uniqueId, "<#8FD4A6>[成就] 恭喜你完成了成就: <#EFDF8F>${achievement.name}<#8FD4A6>！")
            }
        }
    }

    /** 检查单个成就的所有准则是否满足 */
    fun checkCriteria(player: Player, achievement: Achievement): Boolean {
        for (criterion in achievement.criteria) {
            when (criterion) {
                is Criterion.PAPI -> {
                    if (!checkPAPICriterion(player, criterion)) return false
                }
                is Criterion.Impossible -> return false
            }
        }
        return true
    }

    private fun checkPAPICriterion(player: Player, criterion: Criterion.PAPI): Boolean {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return false
            val resolved = PlaceholderAPI.setPlaceholders(player, criterion.placeholder)
            val target = PlaceholderAPI.setPlaceholders(player, criterion.value)
            return criterion.operator.compareString(resolved, target)
        } catch (_: Exception) {
            return false
        }
    }

    // ========== 查询与操作 ==========

    fun getProgress(player: Player, achievementId: String): AchievementProgress? {
        val data = playerDataMap[player.uniqueId.toString()] ?: return null
        return data.achievements[achievementId]
    }

    fun isCompleted(player: Player, achievementId: String): Boolean {
        return getProgress(player, achievementId)?.completed == true
    }

    /** 完成成就，返回 true 表示成功 */
    fun complete(player: Player, achievementId: String): Boolean {
        val data = playerDataMap[player.uniqueId.toString()] ?: return false
        if (achievements[achievementId] == null) return false
        if (data.achievements[achievementId]?.completed == true) return false

        data.achievements[achievementId] = AchievementProgress(
            completed = true,
            completedTime = System.currentTimeMillis()
        )
        devLog("[成就] 玩家 ${player.name} 通过指令完成成就: ${achievements[achievementId]?.name}")
        return true
    }

    fun getCompletedCount(player: Player): Int {
        val data = playerDataMap[player.uniqueId.toString()] ?: return 0
        return data.achievements.count { it.value.completed }
    }

    fun getTotalCount(): Int = achievements.size

    fun getSortedEntries(player: Player): List<AchievementEntry> {
        val data = playerDataMap[player.uniqueId.toString()]

        return achievements.map { (id, ach) ->
            AchievementEntry(
                id = id,
                achievement = ach,
                progress = data?.achievements?.get(id)
            )
        }.sortedWith(compareBy(
            { categories[it.achievement.category]?.name ?: "" },
            { it.id }
        ))
    }
}
