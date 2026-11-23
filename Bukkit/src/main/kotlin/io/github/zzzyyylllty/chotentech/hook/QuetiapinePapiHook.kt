package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmLegacyAmpersandUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmLegacySectionUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.function.pluginVersion
import io.github.zzzyyylllty.chotentech.util.DependencyHelper
import kotlin.let
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.split
import kotlin.toString

val quetiapineVaildOptions = listOf("ampersand", "adventure", "section")

object QuetiapinePapiHook : PlaceholderExpansion() {

    override fun getIdentifier(): String = "quetiapine"

    override fun getAuthor(): String = "zzzyyylllty"

    override fun getVersion(): String = pluginVersion

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = DependencyHelper.papi

    /**
     * 手动注册（由 `@Awake(LifeCycle.ACTIVE)` 调用）。
     *
     * 与 [CTTPapiHook.registerSafely] 同理：先摘旧实例再注册，支持 `/ctt reload` 后
     * 占位符仍然可用，不需要重启服务器。
     */
    fun registerSafely(): Boolean {
        if (!DependencyHelper.papi) return false
        return try {
            if (PlaceholderAPI.isRegistered(identifier)) unregister()
            val ok = register()
            if (!ok) {
                unregister()
                register()
            } else {
                ok
            }
        } catch (e: Throwable) {
            Bukkit.getLogger().warning("[ChoTenTech] 注册 quetiapine 占位符失败: ${e.message}")
            false
        }
    }

    fun unregisterSafely() {
        if (!DependencyHelper.papi) return
        try {
            if (PlaceholderAPI.isRegistered(identifier)) unregister()
        } catch (_: Throwable) {
        }
    }

    // 处理玩家请求（在线/离线统一走 onRequest，由 PAPI 负责下发）
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val split = params.split(":")

        var option: String? = null

        if (quetiapineVaildOptions.contains(split.last())) {
            option = split.last()
        }

        val parsed = if (option != null) params.removeSuffix(":$option") else params

        val str = quetiapinePlaceholder(player?.player, parsed)

        return when (option) {
            null -> str
            "adventure" -> str.legacyToMiniMessage()
            "ampersand" -> mmLegacyAmpersandUtil.serialize(str.replace("§", "&").toComponent())
            "section" -> mmLegacySectionUtil.serialize(str.replace("§", "&").toComponent())
            else -> str
        }
    }

}

fun quetiapinePlaceholder(player: Player?, args: String): String {

    val parsed = args.removeSurrounding("{", "}")
    var list = parsed.split("_")
    val pm = player?.let { PlayerDataManager(it) }

    when (list[0].lowercase()) {
        "level" -> {
            val levelName = list.getOrNull(1) ?: return "Level not found"
            val section = list.getOrNull(2) ?: return pm?.getLevel(levelName).toString()
            when (section.lowercase()) {
                "name" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return levels[levelName]?.displayNames[display].toString()
                }
                "display" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return pm?.getLevel(levelName)?.let { levels[levelName]?.level?.getCurrentLevelDisplay(it, display) }.toString()
                }
                "raw" -> {
                    val display = list.getOrNull(3) ?: "default"
                    return pm?.getLevel(levelName).toString()
                }
                "min" -> {
                    return levels[levelName]?.level?.min.toString()
                }
                "max" -> {
                    return levels[levelName]?.level?.getMaxLevel(player).toString()
                }
                "rawmax" -> {
                    return levels[levelName]?.level?.max.toString()
                }
                "start" -> {
                    return levels[levelName]?.level?.start.toString()
                }
                "exp" -> {
                    return pm?.getExp(levelName).toString()
                }
                "require" -> {
                    val level = list.getOrNull(3) ?: return pm?.getNextLevelExp(levelName).toString()
                    return levels[levelName]?.level?.getRequiredExp(player, level.toLong()).toString()
                }
                "next" -> {
                    return pm?.getNextLevelExp(levelName).toString()
                }
                "percent" -> {
                    val next = pm?.getNextLevelExp(levelName) ?: return "0"
                    return ((pm?.getExp(levelName) ?: 0.0) / next * 100).toString()
                }
            }
        }
    }
    return "Error"
}
