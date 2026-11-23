package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.DependencyHelper
import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.platform.function.pluginVersion

class KAngelAttributesExpansion : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "kangelattributes"
    }

    override fun getAuthor(): String {
        return "zzzyyylllty"
    }

    override fun getVersion(): String {
        return pluginVersion
    }

    override fun persist(): Boolean {
        return true
    }

    override fun canRegister(): Boolean {
        return DependencyHelper.papi
    }

    /**
     * 手动注册（由 `@Awake(LifeCycle.ACTIVE)` 调用）。
     *
     * 先摘旧实例再注册，使 `/ctt reload` 后占位符继续可用。
     * 注意：本扩展是 `class`（非 object），重载时会构造新实例，
     * 旧实例必须显式 unregister，否则 PAPI 会因 identifier 冲突拒绝注册。
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
            Bukkit.getLogger().warning("[ChoTenTech] 注册 kangelattributes 占位符失败: ${e.message}")
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

    override fun onPlaceholderRequest(player: Player, params: String): String? {
        // 处理无需玩家的占位符
        when (params.lowercase()) {
            "version" -> return getVersion()
            "author" -> return getAuthor()
            "identifier" -> return getIdentifier()
        }

        // 玩家相关占位符需要玩家在线
        if (player == null || !player.isOnline) {
            return null
        }
        val onlinePlayer = player.player ?: return null

        // 支持 params 格式:
        // attribute_id -> 返回属性原始值
        // attribute_id_formatted -> 返回格式化后的值（百分比属性添加 % 符号）
        // attribute_id_raw -> 返回原始数值（无格式化）

        val (attributeId, suffix) = parseParams(params)

        // 检查属性是否存在
        if (!AttributeManager.isRegistered(attributeId)) {
            return null
        }

        val value = AttributeManager.getAttributeValue(onlinePlayer, attributeId)

        return when (suffix) {
            "formatted" -> formatValue(attributeId, value)
            else -> value.toString()
        }
    }

    private fun parseParams(params: String): Pair<String, String> {
        // 检查是否以 _formatted 或 _raw 结尾
        return when {
            params.endsWith("_formatted") -> {
                val attributeId = params.removeSuffix("_formatted")
                Pair(attributeId, "formatted")
            }
            params.endsWith("_raw") -> {
                val attributeId = params.removeSuffix("_raw")
                Pair(attributeId, "raw")
            }
            else -> Pair(params, "raw")
        }
    }

    private fun formatValue(attributeId: String, value: Double): String {
        val attribute = AttributeManager.getRegisteredAttributes().find { it.id == attributeId }
        return if (attribute?.isPercentage == true) {
            String.format("%.2f%%", value * 100)
        } else {
            // 根据值大小决定小数位数
            when {
                value == 0.0 -> "0"
                Math.abs(value) < 0.01 -> String.format("%.4f", value)
                Math.abs(value) < 1.0 -> String.format("%.2f", value)
                Math.abs(value) < 1000.0 -> String.format("%.1f", value)
                else -> String.format("%.0f", value)
            }
        }
    }
}