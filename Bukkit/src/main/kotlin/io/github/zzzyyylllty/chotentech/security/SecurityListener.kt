package io.github.zzzyyylllty.chotentech.security

import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.punish.PunishEntry
import io.github.zzzyyylllty.chotentech.punish.PunishManager
import io.github.zzzyyylllty.chotentech.punish.PunishType
import io.github.zzzyyylllty.chotentech.util.devLog
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync

private val MULTI_SPACE = Regex("\\s+")

object SecurityListener {

    /** 归一化命令: 移除前导 /，合并连续空格为单个空格 */
    private fun normalizeCommand(raw: String): String {
        val noSlash = if (raw.startsWith("/")) raw.substring(1) else raw
        return noSlash.replace(MULTI_SPACE, " ").trim()
    }

    /**
     * 处理安全检查匹配后的动作:
     * 1. 事件已在调用方被取消
     * 2. 若非宽松检查且 safemode=true → AUTH 永久惩罚 + 踢出
     * 3. 若宽松 → 仅提示拦截
     * 4. 若非宽松但 safemode=false → 仅提示拦截
     */
    private fun handleViolation(player: Player, result: SecurityManager.CheckResult) {
        val prefix = if (result.isRelaxed) "[宽松]" else "[严格]"
        devLog("SecurityListener: ${prefix} ${player.name} triggered check '${result.checkName}' (${result.matchContent})")

        if (!result.isRelaxed && result.safemode) {
            // AUTH 永久惩罚
            submitAsync {
                try {
                    val entry = PunishEntry(
                        targetUuid = player.uniqueId.toString(),
                        targetName = player.name,
                        operatorUuid = null,
                        operatorName = "SYSTEM",
                        type = PunishType.AUTH,
                        reason = "尝试对服务器进行危险操作，您需要身份验证才能解除。",
                        durationMs = -1L,
                        issueTime = System.currentTimeMillis(),
                        expiryTime = -1L
                    )
                    PunishManager.issue(entry)

                    // 在主线程踢出玩家
                    submit {
                        if (player.isOnline) {
                            player.kick(Component.text(
                                "§c你已被要求身份验证！\n§e理由: 尝试对服务器进行危险操作\n§6请主动联系管理员解除验证"
                            ))
                        }
                    }
                } catch (e: Exception) {
                    devLog("SecurityListener: failed to issue AUTH punish for ${player.name}: ${e.message}")
                }
            }
        } else {
            // 宽松检查或 safemode=false: 仅提示
            player.sendComponent("<red>该操作已被安全系统拦截。</red>")
            if (!result.isRelaxed) {
                player.sendComponent("<gray>（检查组: ${result.checkName}）</gray>")
            }
        }
    }

    @SubscribeEvent
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player

        // 归一化空格防止绕过，如有变化更新事件消息
        val normalized = normalizeCommand(event.message)
        val normalizedWithSlash = "/$normalized"
        if (event.message != normalizedWithSlash) {
            event.message = normalizedWithSlash
        }

        // 安全检查
        val result = SecurityManager.checkCommand(player, normalized)
        if (result != null && result.matched) {
            event.isCancelled = true
            handleViolation(player, result)
            return
        }

        // OP 变更检测: /op、/deop 命令
        val firstPart = normalized.split(" ").first().lowercase()
        if (firstPart == "op" || firstPart == "deop") {
            val opResult = SecurityManager.checkOp(player)
            if (opResult != null && opResult.matched) {
                event.isCancelled = true
                handleViolation(player, opResult)
                return
            }
        }
    }

    @SubscribeEvent
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val player = event.player
        val result = SecurityManager.checkGameMode(player, event.newGameMode)
        if (result != null && result.matched) {
            event.isCancelled = true
            handleViolation(player, result)
        }
    }

    /**
     * 清除玩家缓存（权限变更时外部调用）
     */
    fun clearPlayerCache(player: Player) {
        SecurityManager.invalidatePlayerCache(player.name)
    }
}
