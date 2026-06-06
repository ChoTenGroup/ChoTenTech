package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.player.sendComponent
import net.kyori.adventure.text.Component
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onChatMuteCheck(event: AsyncChatEvent) {
    val player = event.player
    val muteEntry = PunishManager.isMuted(player.uniqueId.toString())
    if (muteEntry != null) {
        if (!muteEntry.isExpired) {
            event.isCancelled = true
            val remainingStr = if (muteEntry.isPermanent) "永久" else formatRemainingMs(muteEntry.remainingMs)
            player.sendComponent("<red>你已被禁言！剩余时间: <yellow>$remainingStr")
            if (muteEntry.reason.isNotBlank()) {
                player.sendComponent("<gray>理由: ${muteEntry.reason}")
            }
        } else {
            PunishManager.deactivatePunishment(muteEntry.id)
        }
    }
}

@SubscribeEvent
fun onLoginBanCheck(event: PlayerLoginEvent) {
    val uuid = event.player.uniqueId.toString()
    val punishEntry = PunishManager.isBannedOrAuthLocked(uuid)
    if (punishEntry != null) {
        if (!punishEntry.isExpired) {
            val typeName = when (punishEntry.type) {
                PunishType.BAN -> "封禁"
                PunishType.AUTH -> "身份验证"
                else -> "限制"
            }
            val durationStr = if (punishEntry.isPermanent) "永久" else formatRemainingMs(punishEntry.remainingMs)

            val message = buildString {
                append("你已被$typeName！\n")
                if (punishEntry.reason.isNotBlank()) append("理由: ${punishEntry.reason}\n")
                append("时长: $durationStr")
                if (punishEntry.type == PunishType.AUTH) append("\n请主动联系管理员解除验证")
                if (!punishEntry.isPermanent) append("\n解封时间: ${formatDateTime(punishEntry.expiryTime)}")
            }
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text(message))
        } else {
            PunishManager.deactivatePunishment(punishEntry.id)
        }
    }
}

@SubscribeEvent
fun onJoinPunishNotify(event: PlayerJoinEvent) {
    val player = event.player
    val uuid = player.uniqueId.toString()

    val muteEntry = PunishManager.isMuted(uuid)
    if (muteEntry != null) {
        val remainingStr = if (muteEntry.isPermanent) "永久" else formatRemainingMs(muteEntry.remainingMs)
        player.sendComponent("<red>你当前处于禁言状态！剩余时间: <yellow>$remainingStr")
        if (muteEntry.reason.isNotBlank()) {
            player.sendComponent("<gray>理由: ${muteEntry.reason}")
        }
    }

    val warnCount = PunishManager.getWarnCount(uuid)
    if (warnCount > 0) {
        player.sendComponent("<yellow>你当前有 <red>$warnCount <yellow>条警告记录")
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return sdf.format(java.util.Date(epochMillis))
}
