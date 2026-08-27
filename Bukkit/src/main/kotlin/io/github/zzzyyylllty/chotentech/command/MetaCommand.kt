package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.MetaManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.int
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-meta",
    aliases = ["cttmeta"],
    permission = "chotentech.admin",
    description = "Meta标记系统",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object MetaCommand {

    @CommandBody
    val main = mainCommand { createModernHelper() }

    // ==================== Shared (跨 Profile) ====================

    @CommandBody
    val sharedset = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, _ ->
                    val uuid = (sender as? Player)?.uniqueId?.toString()
                        ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                    MetaManager.setShared(uuid, context["key"], context["value"], null)
                    sender.sendComponent("<#8FD4A6>已设置 shared meta ${context["key"]} = ${context["value"]}")
                }
                int("seconds") {
                    suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("60", "300", "3600", "86400") }
                    execute<CommandSender> { sender, context, _ ->
                        val uuid = (sender as? Player)?.uniqueId?.toString()
                            ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                        val seconds = context["seconds"].toLongOrNull()
                        if (seconds == null || seconds < 1) {
                            sender.sendComponent("<#E08A98>时长必须为正整数（秒）")
                            return@execute
                        }
                        MetaManager.setShared(uuid, context["key"], context["value"], System.currentTimeMillis() + seconds * 1000L)
                        sender.sendComponent("<#8FD4A6>已设置 shared meta ${context["key"]} = ${context["value"]}（${seconds}秒后过期）")
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("player").castSafely<Player>()!!
                        MetaManager.setShared(target.uniqueId.toString(), context["key"], context["value"], null)
                        sender.sendComponent("<#8FD4A6>已为 ${target.name} 设置 shared meta ${context["key"]} = ${context["value"]}")
                    }
                    int("seconds") {
                        suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("60", "300", "3600", "86400") }
                        execute<CommandSender> { sender, context, _ ->
                            val target = context.player("player").castSafely<Player>()!!
                            val seconds = context["seconds"].toLongOrNull()
                            if (seconds == null || seconds < 1) {
                                sender.sendComponent("<#E08A98>时长必须为正整数（秒）")
                                return@execute
                            }
                            MetaManager.setShared(target.uniqueId.toString(), context["key"], context["value"], System.currentTimeMillis() + seconds * 1000L)
                            sender.sendComponent("<#8FD4A6>已为 ${target.name} 设置 shared meta ${context["key"]} = ${context["value"]}（${seconds}秒后过期）")
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val sharedunset = subCommand {
        dynamic("key") {
            execute<CommandSender> { sender, context, _ ->
                val uuid = (sender as? Player)?.uniqueId?.toString()
                    ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                if (MetaManager.removeShared(uuid, context["key"])) {
                    sender.sendComponent("<#8FD4A6>已移除 shared meta ${context["key"]}")
                } else {
                    sender.sendComponent("<#EFDF8F>shared meta ${context["key"]} 不存在")
                }
            }
            player("player") {
                execute<CommandSender> { sender, context, _ ->
                    val target = context.player("player").castSafely<Player>()!!
                    val uuid = target.uniqueId.toString()
                    if (MetaManager.removeShared(uuid, context["key"])) {
                        sender.sendComponent("<#8FD4A6>已移除 ${target.name} 的 shared meta ${context["key"]}")
                    } else {
                        sender.sendComponent("<#EFDF8F>${target.name} 的 shared meta ${context["key"]} 不存在")
                    }
                }
            }
        }
    }

    @CommandBody
    val sharedlist = subCommand {
        execute<Player> { sender, _, _ ->
            val metas = MetaManager.listShared(sender.uniqueId.toString())
            if (metas.isEmpty()) {
                sender.sendComponent("<#EFDF8F>暂无 shared meta")
                return@execute
            }
            sender.sendComponent("<#DFBD7C>===== Shared Meta (${metas.size}) =====")
            metas.forEach { (key, entry) ->
                val info = if (entry.expireAt != null) {
                    val remaining = (entry.expireAt - System.currentTimeMillis()) / 1000
                    "<#A9B4C3>(剩余 ${remaining}s)"
                } else {
                    "<#8FD4A6>(永久)"
                }
                sender.sendComponent("<#F2F5FA>$key <#A9B4C3>= <#F2F5FA>${entry.value} $info")
            }
        }
        player("player") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("player").castSafely<Player>()!!
                val metas = MetaManager.listShared(target.uniqueId.toString())
                if (metas.isEmpty()) {
                    sender.sendComponent("<#EFDF8F>${target.name} 暂无 shared meta")
                    return@execute
                }
                sender.sendComponent("<#DFBD7C>===== ${target.name} Shared Meta (${metas.size}) =====")
                metas.forEach { (key, entry) ->
                    val info = if (entry.expireAt != null) {
                        val remaining = (entry.expireAt - System.currentTimeMillis()) / 1000
                        "<#A9B4C3>(剩余 ${remaining}s)"
                    } else {
                        "<#8FD4A6>(永久)"
                    }
                    sender.sendComponent("<#F2F5FA>$key <#A9B4C3>= <#F2F5FA>${entry.value} $info")
                }
            }
        }
    }

    // ==================== Independent (当前 Profile) ====================

    @CommandBody
    val independentset = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, _ ->
                    val uuid = (sender as? Player)?.uniqueId?.toString()
                        ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                    MetaManager.setIndependent(uuid, context["key"], context["value"], null)
                    sender.sendComponent("<#8FD4A6>已设置 independent meta ${context["key"]} = ${context["value"]}")
                }
                int("seconds") {
                    suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("60", "300", "3600", "86400") }
                    execute<CommandSender> { sender, context, _ ->
                        val uuid = (sender as? Player)?.uniqueId?.toString()
                            ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                        val seconds = context["seconds"].toLongOrNull()
                        if (seconds == null || seconds < 1) {
                            sender.sendComponent("<#E08A98>时长必须为正整数（秒）")
                            return@execute
                        }
                        MetaManager.setIndependent(uuid, context["key"], context["value"], System.currentTimeMillis() + seconds * 1000L)
                        sender.sendComponent("<#8FD4A6>已设置 independent meta ${context["key"]} = ${context["value"]}（${seconds}秒后过期）")
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, _ ->
                        val target = context.player("player").castSafely<Player>()!!
                        MetaManager.setIndependent(target.uniqueId.toString(), context["key"], context["value"], null)
                        sender.sendComponent("<#8FD4A6>已为 ${target.name} 设置 independent meta ${context["key"]} = ${context["value"]}")
                    }
                    int("seconds") {
                        suggestion<CommandSender>(uncheck = true) { _, _ -> listOf("60", "300", "3600", "86400") }
                        execute<CommandSender> { sender, context, _ ->
                            val target = context.player("player").castSafely<Player>()!!
                            val seconds = context["seconds"].toLongOrNull()
                            if (seconds == null || seconds < 1) {
                                sender.sendComponent("<#E08A98>时长必须为正整数（秒）")
                                return@execute
                            }
                            MetaManager.setIndependent(target.uniqueId.toString(), context["key"], context["value"], System.currentTimeMillis() + seconds * 1000L)
                            sender.sendComponent("<#8FD4A6>已为 ${target.name} 设置 independent meta ${context["key"]} = ${context["value"]}（${seconds}秒后过期）")
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val independentunset = subCommand {
        dynamic("key") {
            execute<CommandSender> { sender, context, _ ->
                val uuid = (sender as? Player)?.uniqueId?.toString()
                    ?: run { sender.sendComponent("<#E08A98>控制台必须指定玩家"); return@execute }
                if (MetaManager.removeIndependent(uuid, context["key"])) {
                    sender.sendComponent("<#8FD4A6>已移除 independent meta ${context["key"]}")
                } else {
                    sender.sendComponent("<#EFDF8F>independent meta ${context["key"]} 不存在")
                }
            }
            player("player") {
                execute<CommandSender> { sender, context, _ ->
                    val target = context.player("player").castSafely<Player>()!!
                    val uuid = target.uniqueId.toString()
                    if (MetaManager.removeIndependent(uuid, context["key"])) {
                        sender.sendComponent("<#8FD4A6>已移除 ${target.name} 的 independent meta ${context["key"]}")
                    } else {
                        sender.sendComponent("<#EFDF8F>${target.name} 的 independent meta ${context["key"]} 不存在")
                    }
                }
            }
        }
    }

    @CommandBody
    val independentlist = subCommand {
        execute<Player> { sender, _, _ ->
            val metas = MetaManager.listIndependent(sender.uniqueId.toString())
            if (metas.isEmpty()) {
                sender.sendComponent("<#EFDF8F>暂无 independent meta")
                return@execute
            }
            sender.sendComponent("<#DFBD7C>===== Independent Meta (${metas.size}) =====")
            metas.forEach { (key, entry) ->
                val info = if (entry.expireAt != null) {
                    val remaining = (entry.expireAt - System.currentTimeMillis()) / 1000
                    "<#A9B4C3>(剩余 ${remaining}s)"
                } else {
                    "<#8FD4A6>(永久)"
                }
                sender.sendComponent("<#F2F5FA>$key <#A9B4C3>= <#F2F5FA>${entry.value} $info")
            }
        }
        player("player") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("player").castSafely<Player>()!!
                val metas = MetaManager.listIndependent(target.uniqueId.toString())
                if (metas.isEmpty()) {
                    sender.sendComponent("<#EFDF8F>${target.name} 暂无 independent meta")
                    return@execute
                }
                sender.sendComponent("<#DFBD7C>===== ${target.name} Independent Meta (${metas.size}) =====")
                metas.forEach { (key, entry) ->
                    val info = if (entry.expireAt != null) {
                        val remaining = (entry.expireAt - System.currentTimeMillis()) / 1000
                        "<#A9B4C3>(剩余 ${remaining}s)"
                    } else {
                        "<#8FD4A6>(永久)"
                    }
                    sender.sendComponent("<#F2F5FA>$key <#A9B4C3>= <#F2F5FA>${entry.value} $info")
                }
            }
        }
    }
}
