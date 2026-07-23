package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.listener.checkPlayerIsInNoclipPoint
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SideInfoManager {

    private val GARBLED = "!@#$%^?+~=|;:.".toCharArray()
    private val random = java.util.Random()

    private data class PlayerState(
        var countdown: Int = 0,
        var inProximity: Boolean = false,
        var prevInProximity: Boolean = false,
        var proximityFadeTimer: Int = 0,
        var proximityFadingIn: Boolean = true,
        var animStartTime: Long = 0L
    )

    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private var tickTask: PlatformExecutor.PlatformTask? = null

    fun getSideData(player: Player): String {
        val state = playerStates[player.uniqueId] ?: return "idle"
        if (state.proximityFadeTimer > 0) {
            return if (state.proximityFadingIn) "fadein" else "fadeout"
        }
        if (state.inProximity) return "proximity"
        if (state.countdown > 0) {
            return when {
                state.countdown >= 12 -> "fadein"
                state.countdown >= 3 -> "show"
                else -> "fadeout"
            }
        }
        return "idle"
    }

    fun isShowing(player: Player): Boolean {
        val state = playerStates[player.uniqueId] ?: return false
        return state.inProximity || state.proximityFadeTimer > 0 || state.countdown > 0
    }

    fun getClipData(player: Player): String {
        val state = playerStates[player.uniqueId] ?: return ""
        val phase = getSideData(player)

        // Hints only visible during stable show phases (not during intro/outro animation)
        val withHints = phase == "show" || phase == "proximity" || phase == "fadeout"
        val fullText = buildClipText(player, withHints)
        if (fullText.isEmpty()) return ""

        val now = System.currentTimeMillis()

        return when (phase) {
            "fadein" -> {
                val progress = ((now - state.animStartTime) / 1000.0 / 2.0).coerceIn(0.0, 1.0)
                val revealCount = (fullText.length * progress).toInt().coerceIn(0, fullText.length)
                val revealed = revealedWithGarbledTags(fullText, revealCount)
                if (revealCount < fullText.length) revealed + "<dark_gray>" + garbled(4) else revealed
            }
            "fadeout" -> {
                val progress = ((now - state.animStartTime) / 1000.0 / 2.0).coerceIn(0.0, 1.0)
                val remaining = (fullText.length * (1.0 - progress)).toInt().coerceIn(0, fullText.length)
                val kept = revealedWithGarbledTags(fullText, remaining)
                if (remaining in 1 until fullText.length) kept + "<dark_gray>" + garbled(4) else kept
            }
            else -> fullText
        }
    }

    fun getCountdown(player: Player): Int = playerStates[player.uniqueId]?.countdown ?: 0

    private fun garbled(length: Int): String =
        (1..length).map { GARBLED[random.nextInt(GARBLED.size)] }.joinToString("")

    /**
     * Reveal [count] characters from [fullText], but if the cut position
     * lands inside a MiniMessage tag (e.g. `<#66`), replace the partial
     * tag with garbled text instead of showing broken markup.
     */
    private fun revealedWithGarbledTags(fullText: String, count: Int): String {
        val sb = StringBuilder()
        var consumed = 0
        var i = 0
        while (i < fullText.length && consumed < count) {
            if (fullText[i] == '<') {
                val tagEnd = fullText.indexOf('>', i)
                if (tagEnd != -1) {
                    val tagLen = tagEnd - i + 1
                    if (consumed + tagLen <= count) {
                        // Whole tag fits — keep it
                        sb.append(fullText, i, tagEnd + 1)
                        consumed += tagLen
                        i = tagEnd + 1
                    } else {
                        // Tag is cut off — replace remaining budget with garbled
                        sb.append(garbled(count - consumed))
                        consumed = count
                        break
                    }
                } else {
                    // Unclosed '<' — treat as a single garbled char
                    sb.append(garbled(1))
                    consumed++
                    i++
                }
            } else {
                sb.append(fullText[i])
                consumed++
                i++
            }
        }
        return sb.toString()
    }

    private fun buildClipText(player: Player, includeHints: Boolean): String {
        val data = player.getData() ?: return ""
        val level = data.activeLevel ?: return ""
        val noclips = level.noclips
//        if (noclips.isEmpty()) return ""

        val lines = mutableListOf<String>()
        val header = if (level.number != null) "Level ${level.number} ${level.name}" else level.name
        lines.add("<aqua>$header")
        lines.add("")

        for ((index, np) in noclips.withIndex()) {
            val pointName = np.name ?: "切出点${String.format("%02d", index + 1)}"
            val isInRange = checkPlayerIsInNoclipPoint(player, np)
            val hasCoop = np.conditions?.coop != null
            val hasOtherConditions = np.conditions != null && (
                np.conditions.item != null || np.conditions.car != null ||
                np.conditions.signal != null || np.conditions.dropbag != null ||
                np.conditions.condition != null
            )
            val hasEleCond = np.elevatorOnly
            val elePowerOn = !hasEleCond || PowerManager.hasElevatorPowerActiveFor(np)

            val (idColor, nameColor) = when {
                isInRange && hasEleCond && !elePowerOn -> Pair("<#e02e22>", "<red>")
                isInRange -> Pair("<#e0fe22>", "<yellow>")
                hasCoop || hasOtherConditions || (hasEleCond && !elePowerOn) -> Pair("<#e02e22>", "<gray>")
                else -> Pair("<#99ce92>", "<white>")
            }

            lines.add("${idColor}切出#${index + 1} $nameColor$pointName")

            if (includeHints) {
                if (isInRange && data.noclipping) {
                    lines.add(" <gray>-> <aqua>停留在切出点内...")
                } else if (isInRange && hasEleCond && !elePowerOn) {
                    lines.add(" <gray>-> <gray>需要激活电源")
                } else if (isInRange && hasEleCond && elePowerOn) {
                    lines.add(" <gray>-> <yellow>按下上升按钮")
                } else if (hasCoop) {
                    lines.add(" <gray>-> <gray>需要与其他人合作切出...")
                } else if (hasOtherConditions) {
                    lines.add(" <gray>-> <gray>需要满足条件")
                }
            }
        }

        return lines.joinToString("\n")
    }

    @Awake(LifeCycle.ACTIVE)
    fun start() {
        tickTask = submit(period = 20) {
            for (player in Bukkit.getOnlinePlayers()) {
                val data = player.getData() ?: continue
                val level = data.activeLevel ?: continue
                val uuid = player.uniqueId
                val state = playerStates.computeIfAbsent(uuid) { PlayerState() }

                val inAnyNoclip = level.noclips.any { np ->
                    checkPlayerIsInNoclipPoint(player, np)
                }
                state.inProximity = inAnyNoclip

                // Head-down trigger (looking straight down ≈ pitch 90)
                // Head-up trigger → 立即收回
                if (player.location.pitch <= -80 && state.countdown > 0) {
                    state.countdown = 0
                    state.proximityFadeTimer = 0
                }

                if (player.location.pitch >= 85 && state.countdown <= 0) {
                    state.countdown = 15
                    state.proximityFadeTimer = 0
                    state.animStartTime = System.currentTimeMillis()
                }

                // Proximity fade transitions
                if (state.inProximity && !state.prevInProximity && state.countdown <= 0) {
                    state.proximityFadeTimer = 3
                    state.proximityFadingIn = true
                    state.animStartTime = System.currentTimeMillis()
                } else if (!state.inProximity && state.prevInProximity && state.countdown <= 0) {
                    state.proximityFadeTimer = 3
                    state.proximityFadingIn = false
                    state.animStartTime = System.currentTimeMillis()
                }

                if (state.proximityFadeTimer > 0) {
                    state.proximityFadeTimer--
                }

                state.prevInProximity = state.inProximity

                // Countdown transitions — reset anim timer for fade-out
                if (state.countdown == 3) {
                    state.animStartTime = System.currentTimeMillis()
                }

                if (state.countdown > 0) {
                    state.countdown--
                }
            }
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun stop() {
        tickTask?.cancel()
        tickTask = null
        playerStates.clear()
    }

    @SubscribeEvent
    fun onPlayerQuit(e: PlayerQuitEvent) {
        playerStates.remove(e.player.uniqueId)
    }
}
