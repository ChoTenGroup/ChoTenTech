package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.data.DataManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

private val DURATION_TOKEN = Regex("""^(\d+)\s*(mo|[wdhMs]|m(?![oO]))""")

private fun toMs(value: Long, unit: String): Long = when (unit) {
    "s" -> value * 1_000L
    "m" -> value * 60_000L
    "h" -> value * 3_600_000L
    "d" -> value * 86_400_000L
    "w" -> value * 604_800_000L
    "mo", "M" -> value * 2_592_000_000L
    else -> 0L
}

private val PERMANENT_TOKENS = setOf("永久", "perm", "permanent", "forever", "-1")

data class ParsedArgs(
    val durationMs: Long = 0L,
    val reason: String = "",
    val silent: Boolean = false,
    val skipAuth: Boolean = false,
    val scope: String? = null
)

fun parseDurationTokens(input: String): Pair<Long, String> {
    var remaining = input.trimStart()
    var totalMs = 0L
    var matched = false

    while (remaining.isNotEmpty()) {
        val m = DURATION_TOKEN.find(remaining) ?: break
        val value = m.groupValues[1].toLong()
        val unit = m.groupValues[2]
        totalMs += toMs(value, unit)
        matched = true
        remaining = remaining.substring(m.range.last + 1).trimStart()
    }

    return if (matched) Pair(totalMs, remaining) else Pair(0L, input)
}

fun parsePermanentAndDuration(input: String): Pair<Long, String> {
    val trimmed = input.trimStart()
    for (token in PERMANENT_TOKENS) {
        if (trimmed.equals(token, ignoreCase = true)) {
            return Pair(-1L, "")
        }
        if (trimmed.startsWith(token, ignoreCase = true) && (trimmed.length == token.length || trimmed[token.length].isWhitespace())) {
            return Pair(-1L, trimmed.substring(token.length).trim())
        }
    }
    val (durationMs, remaining) = parseDurationTokens(trimmed)
    return if (durationMs > 0) Pair(durationMs, remaining) else Pair(0L, trimmed)
}

fun parseMuteArgs(argument: String, preconfiguredReasons: Map<String, Long>): ParsedArgs {
    var remaining = argument.trim()
    var silent = false
    var durationMs = 0L
    var reason = ""

    // Extract -s flag
    while (remaining.startsWith("-s")) {
        silent = true
        remaining = remaining.substring(2).trimStart()
        if (remaining.startsWith("-s")) continue
    }

    if (remaining.isEmpty()) return ParsedArgs(durationMs = 0L, reason = "", silent = silent)

    // Try parse explicit duration from head
    val (parsedMs, afterDuration) = parseDurationTokens(remaining)
    if (parsedMs > 0) {
        durationMs = parsedMs
        reason = afterDuration
    } else {
        // Check permanent
        val (permMs, afterPerm) = parsePermanentAndDuration(remaining)
        if (permMs == -1L) {
            durationMs = -1L
            reason = afterPerm
        } else {
            // Check preconfigured reasons
            reason = remaining
            durationMs = 0L // Will be resolved by caller using preconfigured reasons
        }
    }

    return ParsedArgs(durationMs = durationMs, reason = reason, silent = silent)
}

fun parseKickArgs(argument: String): ParsedArgs {
    var remaining = argument.trim()
    var silent = false

    while (remaining.startsWith("-s")) {
        silent = true
        remaining = remaining.substring(2).trimStart()
    }

    return ParsedArgs(durationMs = 0L, reason = remaining, silent = silent)
}

fun parseWarnArgs(argument: String): ParsedArgs {
    return ParsedArgs(durationMs = 0L, reason = argument.trim())
}

fun parseBanArgs(argument: String): ParsedArgs {
    var remaining = argument.trim()
    var skipAuth = false

    while (remaining.startsWith("-n")) {
        skipAuth = true
        remaining = remaining.substring(2).trimStart()
        if (remaining.startsWith("-n")) continue
    }

    var silent = false
    while (remaining.startsWith("-s")) {
        silent = true
        remaining = remaining.substring(2).trimStart()
    }

    if (remaining.isEmpty()) return ParsedArgs(durationMs = 0L, reason = "", skipAuth = skipAuth, silent = silent)

    val (durationMs, reason) = parsePermanentAndDuration(remaining)
    return ParsedArgs(durationMs = durationMs, reason = reason, skipAuth = skipAuth, silent = silent)
}

fun parseAuthArgs(argument: String): ParsedArgs {
    var remaining = argument.trim()
    var silent = false

    while (remaining.startsWith("-s")) {
        silent = true
        remaining = remaining.substring(2).trimStart()
    }

    if (remaining.isEmpty()) return ParsedArgs(durationMs = -1L, reason = "", silent = silent)

    val (durationMs, reason) = parsePermanentAndDuration(remaining)
    return ParsedArgs(durationMs = if (durationMs == 0L) -1L else durationMs, reason = reason, silent = silent)
}

fun parseWipeArgs(argument: String): ParsedArgs {
    val trimmed = argument.trim()
    if (trimmed.isEmpty()) return ParsedArgs()

    val parts = trimmed.split("\\s+".toRegex(), 2)
    val scope = parts[0]
    val reason = if (parts.size > 1) parts[1] else ""

    return ParsedArgs(durationMs = 0L, reason = reason, scope = scope)
}

fun resolvePlayer(nameOrUuid: String): Pair<String, String>? {
    // Try online player by name
    val online = Bukkit.getPlayerExact(nameOrUuid)
    if (online != null) return Pair(online.uniqueId.toString(), online.name)

    // Try online player by UUID
    try {
        val uuid = UUID.fromString(nameOrUuid)
        val onlineByUuid = Bukkit.getPlayer(uuid)
        if (onlineByUuid != null) return Pair(onlineByUuid.uniqueId.toString(), onlineByUuid.name)
        // UUID is valid but player offline - use the UUID as-is, try DB for name
        val data = DataManager.getData(uuid.toString())
        if (data != null) return Pair(uuid.toString(), data.playerName)
        return Pair(uuid.toString(), nameOrUuid)
    } catch (_: IllegalArgumentException) {
        // Not a UUID
    }

    // Try DB by name
    val data = DataManager.getDataByName(nameOrUuid)
    if (data != null) return Pair(data.uuid, data.playerName)

    // Try online players with partial match
    for (player in Bukkit.getOnlinePlayers()) {
        if (player.name.equals(nameOrUuid, ignoreCase = true)) {
            return Pair(player.uniqueId.toString(), player.name)
        }
    }

    return null
}

fun getOnlinePlayerByName(name: String): Player? {
    return Bukkit.getPlayerExact(name)
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "永久"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return buildString {
        if (days > 0) append("${days}天")
        if (hours % 24 > 0) append("${hours % 24}时")
        if (minutes % 60 > 0) append("${minutes % 60}分")
        if (seconds % 60 > 0 && days == 0L && hours == 0L) append("${seconds % 60}秒")
        if (isEmpty()) append("永久")
    }
}

fun formatRemainingMs(ms: Long): String {
    if (ms < 0) return "永久"
    return formatDurationMs(ms)
}
