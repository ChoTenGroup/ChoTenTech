package io.github.zzzyyylllty.chotentech.chat

import org.bukkit.entity.Player
import taboolib.module.configuration.Configuration

/**
 * 聊天频道定义，对应 channels 目录下的一个 yml 文件。
 * formats: 分组名 -> 格式串（MiniMessage）。key "default" 为默认格式，"console" 为控制台格式。
 * 分组匹配规则：玩家持有 chotentech.chat.format.<分组名> 权限则使用该分组格式，否则回退 default。
 */
data class ChatChannel(
    val key: String,
    val displayName: String,
    val formats: Map<String, String>,
    val quickPrefix: String,
    val radius: Double,
    val cooldown: Double,
    val cooldownOverrides: List<Pair<String, Double>>,
    val party: Boolean,
    val permission: String,
) {

    fun formatFor(player: Player): String {
        for ((group, format) in formats) {
            if (group == "default" || group == "console") continue
            if (player.hasPermission("chotentech.chat.format.$group")) return format
        }
        return formats["default"] ?: "<username><gray>: </gray><message>"
    }

    fun consoleFormat(): String =
        formats["console"] ?: "[%player_name%] <username>: <message>"

    /**
     * 计算玩家的实际冷却（秒）。-1 表示无冷却。
     * 优先级：全局 bypass 权限 > 频道 bypass 权限 > 配置的 cooldown-overrides（按配置顺序）> 频道默认。
     */
    fun effectiveCooldown(player: Player): Double {
        if (player.hasPermission("chotentech.chat.cooldown.bypass")) return -1.0
        if (player.hasPermission("chotentech.chat.cooldown.$key.bypass")) return -1.0
        for ((perm, seconds) in cooldownOverrides) {
            if (player.hasPermission(perm)) return seconds
        }
        return cooldown
    }

    companion object {

        fun load(cfg: Configuration): ChatChannel {
            val key = cfg.getString("key") ?: error("channel config missing 'key'")
            val formats = buildMap {
                cfg.getConfigurationSection("formats")?.getKeys(false)?.forEach { group ->
                    put(group, cfg.getString("formats.$group", "") ?: "")
                }
            }
            val overrides = buildList {
                cfg.getConfigurationSection("cooldown-overrides")?.getKeys(false)?.forEach { perm ->
                    add(perm to cfg.getDouble("cooldown-overrides.$perm", 0.0))
                }
            }
            return ChatChannel(
                key = key,
                displayName = cfg.getString("display-name", key) ?: key,
                formats = formats,
                quickPrefix = cfg.getString("quick-prefix", "") ?: "",
                radius = cfg.getDouble("radius", -1.0),
                cooldown = cfg.getDouble("cooldown", -1.0),
                cooldownOverrides = overrides,
                party = cfg.getBoolean("party", false),
                permission = cfg.getString("permission", "") ?: "",
            )
        }
    }
}
