package io.github.zzzyyylllty.chotentech.chemdah.addon

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Id
import ink.ptms.chemdah.core.quest.Option
import ink.ptms.chemdah.core.quest.QuestContainer
import ink.ptms.chemdah.core.quest.addon.Addon
import io.github.zzzyyylllty.chotentech.data.getData
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.submit

/**
 * Chemdah addon: "gametimeout"
 *
 * Works like the built-in "timeout" addon but uses game time (60 real seconds = 1 game hour)
 * instead of real wall-clock time. Game time is per-profile and only advances while the
 * player is online.
 *
 * YAML usage:
 *   addon:
 *     gametimeout: "1d"     # fail after 1 game day (24 game hours)
 *     gametimeout: "72h"    # fail after 72 game hours
 *     gametimeout: "2M"     # fail after 2 game months (1440 game hours)
 *     gametimeout: "1y6M"   # fail after 1 game year + 6 game months
 *
 * Suffixes: h (hours, default), d (day = 24h), M (month = 720h), y (year = 8640h)
 */
@Id("gametimeout")
@Option(Option.Type.TEXT)
class AddonGameTimeout(root: String?, questContainer: QuestContainer) : Addon(root, questContainer) {

    val gameDurationHours: Long = parseGameDuration(root ?: "0")

    companion object {

        /** Convenience accessor for other code. */
        fun QuestContainer.getGameDurationHours(): Long {
            return addon<AddonGameTimeout>("gametimeout")?.gameDurationHours ?: 0L
        }

        /**
         * Parse a game-time duration string into hours.
         *
         * Rules:
         *   - Consecutive digits form a number
         *   - The number is followed by an optional single-character suffix
         *   - Suffix: h (hours), d (days = 24h), M (months = 720h), y (years = 8640h)
         *   - No suffix defaults to hours
         *   - Unknown suffix characters are treated as hours
         *
         * Examples:
         *   "24"   → 24 hours
         *   "1d"   → 24 hours
         *   "72h"  → 72 hours
         *   "2M"   → 1440 hours
         *   "1y"   → 8640 hours
         *   "1d12h" → 36 hours
         */
        fun parseGameDuration(input: String): Long {
            if (input.isBlank()) return 0L
            val s = input.trim().lowercase()
            var total = 0L
            var i = 0
            while (i < s.length) {
                val digitStart = i
                while (i < s.length && s[i].isDigit()) i++
                if (digitStart == i) { i++; continue }
                val num = s.substring(digitStart, i).toLongOrNull() ?: run { i++; continue }
                if (i >= s.length) {
                    total += num
                    break
                }
                total += when (s[i]) {
                    'h' -> num
                    'd' -> num * 24L
                    'm' -> num * 720L
                    'y' -> num * 8640L
                    else -> num
                }
                i++
            }
            return total
        }
    }
}

/** Register the addon with Chemdah before templates are loaded. */
@Awake(LifeCycle.INIT)
fun registerGameTimeoutAddon() {
    ChemdahAPI.addQuestAddon("gametimeout", AddonGameTimeout::class.java)
}

/**
 * Periodic game-timeout check.
 *
 * Runs every 20 ticks (≈1 second) asynchronously, matching QuestChecker's cadence.
 * For each quest whose template declares a "gametimeout" addon, this task:
 *  1. Lazily captures the player's [gameTimeHours] as the start time on first check
 *  2. Compares the current [gameTimeHours] against start + configured duration
 *  3. Collects timed-out quests, then submits a sync task to fail them
 *
 * Game time only advances while the player is online, so offline time does not count.
 */
@Schedule(period = 20, async = true)
fun checkGameTimeout() {
    val toFail = mutableListOf<Pair<ink.ptms.chemdah.core.PlayerProfile, ink.ptms.chemdah.core.quest.Quest>>()

    for (player in Bukkit.getOnlinePlayers()) {
        val profile = ChemdahAPI.playerProfile[player.name] ?: continue
        val playerData = player.getData() ?: continue
        val currentHours = playerData.gameTimeHours

        for (quest in profile.getQuests()) {
            // Skip if the quest is currently locked (being processed by QuestChecker)
            // lock is not exposed in the API jar, so catch any failure gracefully

            val addon = quest.template.addon<AddonGameTimeout>("gametimeout") ?: continue
            if (addon.gameDurationHours <= 0) continue

            val startHours = quest.persistentDataContainer["gametimeout_start_hours", 0L].toLong()
            if (startHours <= 0L) {
                // First check — capture current game hours as the quest start point.
                // This is safe because game time doesn't advance while the player is offline,
                // so the first online check will be very close to the actual quest-accept time.
                quest.persistentDataContainer["gametimeout_start_hours"] = playerData.gameTimeHours
                continue
            }

            if (currentHours >= startHours + addon.gameDurationHours) {
                toFail.add(profile to quest)
            }
        }
    }

    // failQuestFuture() calls Bukkit events → must run on the main thread
    if (toFail.isNotEmpty()) {
        submit {
            for ((profile, quest) in toFail) {
                quest.failQuestFuture()
            }
        }
    }
}

@Awake(LifeCycle.ACTIVE)
fun registerGameTimeout() {
    ChemdahAPI.addQuestAddon("gametimeout", AddonGameTimeout::class.java)
}
