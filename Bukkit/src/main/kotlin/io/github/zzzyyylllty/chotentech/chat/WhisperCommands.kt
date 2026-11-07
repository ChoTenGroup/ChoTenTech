package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private object WhisperUtil {

    val cooldowns = ConcurrentHashMap<UUID, Long>()

    /**
     * 玩家退出时清掉其私聊冷却记录。
     * 此前无清理路径：每个进过服务器并私聊过的玩家都在这里
     * 留下一个 Long 条目，长期运行下无界增长。冷却对已离线玩家
     * 无意义，直接移除。由 ChoTenTech 的玩家退出监听调用。
     */
    fun clearCooldowns(playerUuid: UUID) {
        cooldowns.remove(playerUuid)
    }

    fun format(key: String, def: String): String =
        ChoTenTech.chatSettings.getString("whisper.$key", def) ?: def

    fun send(sender: Player, target: Player, message: String) {
        if (sender.uniqueId in ChatPlayerDataManager.get(target).ignoredPlayers) {
            sender.sendComponent("<#E08A98>对方已将你屏蔽，无法发送私聊。")
            return
        }

        // 私聊冷却
        val cooldown = ChoTenTech.chatSettings.getDouble("whisper.cooldown", 1.0)
        if (cooldown > 0 && !sender.hasPermission("chotentech.chat.cooldown.whisper.bypass")) {
            val last = cooldowns[sender.uniqueId] ?: 0L
            val elapsedSec = (System.currentTimeMillis() - last) / 1000.0
            if (elapsedSec < cooldown) {
                sender.sendComponent("<#E08A98>私聊冷却中，请 <#EFDF8F>${"%.1f".format(cooldown - elapsedSec)}<#E08A98> 秒后再试。")
                return
            }
        }

        val filtered = TabooFilter.filter(sender, message)
        if (filtered.isBlank()) return
        val messageComponent = ChatFormatter.parseMessage(sender, filtered)

        val sendComponent = ChatFormatter.buildWithTags(
            formatStr = format("send", "<#A9B4C3>[私聊 → <#F2F5FA><target></#F2F5FA>]</#A9B4C3> <message>"),
            player = sender,
            messageComponent = messageComponent,
            tags = mapOf("target" to target.displayName()),
        )
        val receiveComponent = ChatFormatter.buildWithTags(
            formatStr = format("receive", "<#A9B4C3>[私聊 ← <#F2F5FA><sender></#F2F5FA>]</#A9B4C3> <message>"),
            player = target,
            messageComponent = messageComponent,
            tags = mapOf("sender" to sender.displayName()),
        )

        // 回复目标（双方互写，便于 /reply）
        val senderData = ChatPlayerDataManager.get(sender)
        senderData.lastWhisperTarget = target.uniqueId
        val targetData = ChatPlayerDataManager.get(target)
        targetData.lastWhisperTarget = sender.uniqueId
        ChatPlayerDataManager.save(sender.uniqueId)
        ChatPlayerDataManager.save(target.uniqueId)

        sender.sendMessage(sendComponent)
        target.sendMessage(receiveComponent)
        cooldowns[sender.uniqueId] = System.currentTimeMillis()
        Bukkit.getConsoleSender().sendMessage("[私聊] ${sender.name} -> ${target.name}: $filtered")
    }
}

@CommandHeader(
    name = "msg",
    aliases = ["whisper", "w", "pm"],
    permission = "chotentech.command.msg",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object MsgCommand {

    @CommandBody
    val main = mainCommand {
        player("target") {
            dynamic("message") {
                execute<Player> { sender, context, _ ->
                    val target = context.player("target") as? Player
                    if (target == null) {
                        sender.sendComponent("<#E08A98>目标玩家不在线。")
                        return@execute
                    }
                    if (target == sender) {
                        sender.sendComponent("<#E08A98>不能给自己发送私聊。")
                        return@execute
                    }
                    WhisperUtil.send(sender, target, context["message"])
                }
            }
        }
    }
}

@CommandHeader(
    name = "reply",
    aliases = ["r"],
    permission = "chotentech.command.msg",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object ReplyCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("message") {
            execute<Player> { sender, context, _ ->
                val targetId = ChatPlayerDataManager.get(sender).lastWhisperTarget
                val target = targetId?.let { Bukkit.getPlayer(it) }
                if (target == null || !target.isOnline) {
                    sender.sendComponent("<#E08A98>没有可回复的对象。")
                    return@execute
                }
                WhisperUtil.send(sender, target, context["message"])
            }
        }
    }
}

@CommandHeader(
    name = "ignore",
    aliases = ["block"],
    permission = "chotentech.command.msg",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object IgnoreCommand {

    @CommandBody
    val main = mainCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player
                if (target == null) {
                    sender.sendComponent("<#E08A98>目标玩家不在线。")
                    return@execute
                }
                if (target == sender) {
                    sender.sendComponent("<#E08A98>不能屏蔽自己。")
                    return@execute
                }
                val data = ChatPlayerDataManager.get(sender)
                if (!data.ignoredPlayers.add(target.uniqueId)) {
                    sender.sendComponent("<#EFDF8F>你已屏蔽 <#F2F5FA>${target.name}<#EFDF8F>。")
                    return@execute
                }
                ChatPlayerDataManager.save(sender.uniqueId)
                sender.sendComponent("<#8FD4A6>已屏蔽 <#F2F5FA>${target.name}<#8FD4A6>，将不再看到其频道消息与私聊。")
            }
        }
    }
}

@CommandHeader(
    name = "unignore",
    aliases = ["unblock"],
    permission = "chotentech.command.msg",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object UnignoreCommand {

    @CommandBody
    val main = mainCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player
                if (target == null) {
                    sender.sendComponent("<#E08A98>目标玩家不在线。")
                    return@execute
                }
                val data = ChatPlayerDataManager.get(sender)
                if (!data.ignoredPlayers.remove(target.uniqueId)) {
                    sender.sendComponent("<#EFDF8F>你没有屏蔽 <#F2F5FA>${target.name}<#EFDF8F>。")
                    return@execute
                }
                ChatPlayerDataManager.save(sender.uniqueId)
                sender.sendComponent("<#8FD4A6>已解除屏蔽 <#F2F5FA>${target.name}<#8FD4A6>。")
            }
        }
    }
}

@CommandHeader(
    name = "ignorelist",
    aliases = ["listignores"],
    permission = "chotentech.command.msg",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false,
)
object IgnoreListCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            val ignored = ChatPlayerDataManager.get(sender).ignoredPlayers
            if (ignored.isEmpty()) {
                sender.sendComponent("<#A9B4C3>你当前没有屏蔽任何玩家。")
                return@execute
            }
            sender.sendComponent("<#DFBD7C>===== 屏蔽列表 (${ignored.size}) =====")
            ignored.forEach { id ->
                val name = Bukkit.getOfflinePlayer(id).name ?: id.toString()
                sender.sendComponent("<#A9B4C3>- <#F2F5FA>$name")
            }
        }
    }
}

/**
 * 文件内桥接：WhisperUtil 是 private object，ChatListener 无法直接引用，
 * 通过这个顶层函数转发玩家退出时的冷却清理。
 */
internal fun clearWhisperCooldowns(playerUuid: UUID) {
    WhisperUtil.clearCooldowns(playerUuid)
}
