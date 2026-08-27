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
            player.sendComponent("<#E08A98>你已被禁言！剩余时间: <#EFDF8F>$remainingStr")
            if (muteEntry.reason.isNotBlank()) {
                player.sendComponent("<#A9B4C3>理由: ${muteEntry.reason}")
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

/** 离线时下达的 wipe 在玩家上线时补执行（备份为空说明执行时玩家不在线） */
@SubscribeEvent
fun onJoinExecuteOfflineWipe(event: PlayerJoinEvent) {
    val player = event.player
    val uuid = player.uniqueId.toString()
    for (entry in PunishManager.getActiveByType(uuid, PunishType.WIPE)) {
        val backup = PunishManager.getWipeBackup(entry.id) ?: continue
        if (backup.dataJson != "{}") continue // 已执行过（有真实快照）或已还原
        val scope = entry.meta["scope"] ?: "all"
        PunishManager.clearWipedData(player, scope)
        PunishManager.deactivatePunishment(entry.id)
        player.sendComponent("<#E08A98>你的数据已被管理员清除！范围: $scope，理由: ${entry.reason}")
    }
}

@SubscribeEvent
fun onJoinPunishNotify(event: PlayerJoinEvent) {
    val player = event.player
    val uuid = player.uniqueId.toString()

    val muteEntry = PunishManager.isMuted(uuid)
    if (muteEntry != null) {
        val remainingStr = if (muteEntry.isPermanent) "永久" else formatRemainingMs(muteEntry.remainingMs)
        player.sendComponent("<#E08A98>你当前处于禁言状态！剩余时间: <#EFDF8F>$remainingStr")
        if (muteEntry.reason.isNotBlank()) {
            player.sendComponent("<#A9B4C3>理由: ${muteEntry.reason}")
        }
    }

    val warnCount = PunishManager.getWarnCount(uuid)
    if (warnCount > 0) {
        player.sendComponent("<#EFDF8F>你当前有 <#E08A98>$warnCount <#EFDF8F>条警告记录")
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return sdf.format(java.util.Date(epochMillis))
}
