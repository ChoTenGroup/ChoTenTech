package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.TeamMenu
import io.github.zzzyyylllty.chotentech.team.TeamManager
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "team",
    aliases = ["td", "party", "dui"],
    permission = "chotentech.command.team",
    permissionDefault = PermissionDefault.TRUE,
    permissionMessage = "",
    newParser = false
)
object TeamCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    // ── /team gui ──
    @CommandBody
    val gui = subCommand {
        execute<Player> { sender, _, _ ->
            TeamMenu().open(sender)
        }
    }

    // ── /team info ──
    @CommandBody
    val info = subCommand {
        execute<Player> { sender, _, _ ->
            val team = TeamManager.getTeam(sender.uniqueId)
            if (team == null) {
                sender.sendMessage("<red>你不在队伍中".toComponent())
                return@execute
            }
            val leaderName = Bukkit.getOfflinePlayer(team.leader).name ?: "未知"
            val online = team.onlinePlayers().size
            val total = team.allMembers.size
            sender.sendMessage("<gold>===== 队伍信息 =====".toComponent())
            sender.sendMessage("<gray>队长: <white>$leaderName".toComponent())
            sender.sendMessage("<gray>人数: <green>$online<gray>/<white>$total".toComponent())
            sender.sendMessage("<gray>队员:".toComponent())
            for (memberId in team.allMembers) {
                val name = Bukkit.getOfflinePlayer(memberId).name ?: "未知"
                val isOnline = Bukkit.getPlayer(memberId) != null
                val tag = if (isOnline) "<green>●" else "<gray>●"
                val role = if (memberId == team.leader) "<gold>[队长]" else "<white>"
                sender.sendMessage(" $tag $role $name".toComponent())
            }
        }
    }

    // ── /team create ──
    @CommandBody
    val create = subCommand {
        execute<Player> { sender, _, _ ->
            if (TeamManager.isInTeam(sender.uniqueId)) {
                sender.sendMessage("<red>你已经在队伍中了，请先退出或解散当前队伍".toComponent())
                return@execute
            }
            TeamManager.createTeam(sender)
            sender.sendMessage("<green>队伍创建成功! 你是队长".toComponent())
            sender.sendMessage("<gray>使用 <yellow>/team invite <player> <gray>邀请玩家加入".toComponent())
        }
    }

    // ── /team invite <player> ──
    @CommandBody
    val invite = subCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player ?: run {
                    sender.sendMessage("<red>目标玩家不在线".toComponent())
                    return@execute
                }
                if (target == sender) {
                    sender.sendMessage("<red>不能邀请自己".toComponent())
                    return@execute
                }
                val team = TeamManager.getTeam(sender.uniqueId)
                if (team == null) {
                    sender.sendMessage("<red>你不在队伍中，请先使用 <yellow>/team create".toComponent())
                    return@execute
                }
                if (!team.isLeader(sender.uniqueId)) {
                    sender.sendMessage("<red>只有队长可以邀请玩家".toComponent())
                    return@execute
                }
                if (TeamManager.isInTeam(target.uniqueId)) {
                    sender.sendMessage("<red>该玩家已在其他队伍中".toComponent())
                    return@execute
                }
                val maxSize = TeamManager.getMaxTeamSize(sender)
                if (team.allMembers.size >= maxSize) {
                    sender.sendMessage("<red>队伍已满 (最多 $maxSize 人)".toComponent())
                    return@execute
                }
                if (TeamManager.invite(sender, target)) {
                    sender.sendMessage("<green>已邀请 <yellow>${target.name}</yellow> 加入队伍".toComponent())
                    target.sendMessage("<gold>${sender.name} <yellow>邀请你加入队伍".toComponent())
                    target.sendMessage("<gray>使用 <yellow>/team accept ${sender.name} <gray>接受邀请".toComponent())
                    target.sendMessage("<gray>使用 <yellow>/team deny ${sender.name} <gray>拒绝邀请".toComponent())
                }
            }
        }
    }

    // ── /team accept <player> ──
    @CommandBody
    val accept = subCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player ?: run {
                    sender.sendMessage("<red>目标玩家不在线".toComponent())
                    return@execute
                }
                if (TeamManager.isInTeam(sender.uniqueId)) {
                    sender.sendMessage("<red>你已经在队伍中了".toComponent())
                    return@execute
                }
                if (TeamManager.accept(sender, target.uniqueId)) {
                    sender.sendMessage("<green>你已加入 <yellow>${target.name}</yellow> 的队伍!".toComponent())
                    val team = TeamManager.getTeam(sender.uniqueId)
                    for (mp in team?.onlinePlayers() ?: emptyList()) {
                        if (mp.uniqueId != sender.uniqueId) {
                            mp.sendMessage("<green>${sender.name} <gray>加入了队伍".toComponent())
                        }
                    }
                } else {
                    sender.sendMessage("<red>邀请已过期或不存在，请联系 <yellow>${target.name} <red>重新邀请".toComponent())
                }
            }
        }
    }

    // ── /team deny <player> ──
    @CommandBody
    val deny = subCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player ?: run {
                    sender.sendMessage("<red>目标玩家不在线".toComponent())
                    return@execute
                }
                if (TeamManager.deny(sender, target.uniqueId)) {
                    sender.sendMessage("<gray>已拒绝 <yellow>${target.name}</yellow> 的邀请".toComponent())
                    target.sendMessage("<yellow>${sender.name} <gray>拒绝了你的邀请".toComponent())
                } else {
                    sender.sendMessage("<red>没有来自 <yellow>${target.name} <red>的邀请".toComponent())
                }
            }
        }
    }

    // ── /team leave ──
    @CommandBody
    val leave = subCommand {
        execute<Player> { sender, _, _ ->
            val team = TeamManager.getTeam(sender.uniqueId)
            if (team == null) {
                sender.sendMessage("<red>你不在队伍中".toComponent())
                return@execute
            }
            if (team.isLeader(sender.uniqueId)) {
                sender.sendMessage("<yellow>你是队长，请使用 <gold>/team disband <yellow>解散队伍，或使用 /team transfer <player> 移交队长".toComponent())
                return@execute
            }
            if (TeamManager.leave(sender)) {
                sender.sendMessage("<green>你已退出队伍".toComponent())
                val leader = Bukkit.getPlayer(team.leader)
                if (leader != null && leader.isOnline) {
                    leader.sendMessage("<yellow>${sender.name} <gray>已退出队伍".toComponent())
                }
            }
        }
    }

    // ── /team kick <player> ──
    @CommandBody
    val kick = subCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player ?: run {
                    sender.sendMessage("<red>目标玩家不在线".toComponent())
                    return@execute
                }
                if (target == sender) {
                    sender.sendMessage("<red>不能踢出自己，请使用 <yellow>/team leave".toComponent())
                    return@execute
                }
                val team = TeamManager.getTeam(sender.uniqueId)
                if (team == null) {
                    sender.sendMessage("<red>你不在队伍中".toComponent())
                    return@execute
                }
                if (!team.isLeader(sender.uniqueId)) {
                    sender.sendMessage("<red>只有队长可以踢人".toComponent())
                    return@execute
                }
                if (TeamManager.kick(sender, target)) {
                    sender.sendMessage("<green>已踢出 <yellow>${target.name}</yellow>".toComponent())
                    target.sendMessage("<red>你被队长踢出了队伍".toComponent())
                    for (mp in team.onlinePlayers()) {
                        if (mp.uniqueId != sender.uniqueId && mp.uniqueId != target.uniqueId) {
                            mp.sendMessage("<yellow>${target.name} <gray>被队长踢出了队伍".toComponent())
                        }
                    }
                } else {
                    sender.sendMessage("<red>该玩家不在你的队伍中".toComponent())
                }
            }
        }
    }

    // ── /team transfer <player> ──
    @CommandBody
    val transfer = subCommand {
        player("target") {
            execute<Player> { sender, context, _ ->
                val target = context.player("target") as? Player ?: run {
                    sender.sendMessage("<red>目标玩家不在线".toComponent())
                    return@execute
                }
                if (target == sender) {
                    sender.sendMessage("<red>不能移交给自己".toComponent())
                    return@execute
                }
                val team = TeamManager.getTeam(sender.uniqueId)
                if (team == null) {
                    sender.sendMessage("<red>你不在队伍中".toComponent())
                    return@execute
                }
                if (!team.isLeader(sender.uniqueId)) {
                    sender.sendMessage("<red>只有队长可以移交队长".toComponent())
                    return@execute
                }
                if (TeamManager.transfer(sender, target)) {
                    sender.sendMessage("<green>已将队长移交给 <yellow>${target.name}</yellow>".toComponent())
                    target.sendMessage("<gold>你已成为队伍的新队长!".toComponent())
                    for (mp in team.onlinePlayers()) {
                        if (mp.uniqueId != sender.uniqueId && mp.uniqueId != target.uniqueId) {
                            mp.sendMessage("<yellow>队长已变更为 ${target.name}".toComponent())
                        }
                    }
                } else {
                    sender.sendMessage("<red>该玩家不在你的队伍中".toComponent())
                }
            }
        }
    }

    // ── /team disband ──
    @CommandBody
    val disband = subCommand {
        execute<Player> { sender, _, _ ->
            val team = TeamManager.getTeam(sender.uniqueId)
            if (team == null) {
                sender.sendMessage("<red>你不在队伍中".toComponent())
                return@execute
            }
            if (!team.isLeader(sender.uniqueId)) {
                sender.sendMessage("<red>只有队长可以解散队伍".toComponent())
                return@execute
            }
            val members = team.allMembers.toList()
            if (TeamManager.disband(sender)) {
                for (memberId in members) {
                    val mp = Bukkit.getPlayer(memberId)
                    if (mp != null && mp.isOnline && mp.uniqueId != sender.uniqueId) {
                        mp.sendMessage("<red>队伍已被队长解散".toComponent())
                    }
                }
                sender.sendMessage("<green>队伍已解散".toComponent())
            }
        }
    }
}
