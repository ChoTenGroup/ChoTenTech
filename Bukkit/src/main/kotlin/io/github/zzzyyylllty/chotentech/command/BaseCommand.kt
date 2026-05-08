package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.gui.BaseCodexMenu
import io.github.zzzyyylllty.chotentech.gui.FactionSelectMenu
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "chotentech-base",
    aliases = ["cttbase", "cttb"],
    permission = "chotentech.command.base",
    description = "基地系统管理",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object BaseCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        execute<Player> { sender, _, _ ->
            FactionSelectMenu().open(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: return@execute
                FactionSelectMenu().open(target)
            }
        }
    }

    @CommandBody
    val codex = subCommand {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) BaseCodexMenu().open(sender)
            else sender.sendMessage("§c请指定玩家: /cttbase codex <player>")
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = Bukkit.getPlayerExact(context["target"])
                if (target == null) {
                    sender.sendMessage("§c玩家 ${context["target"]} 不在线！")
                    return@execute
                }
                BaseCodexMenu().open(target)
            }
        }
    }

    @CommandBody
    val join = subCommand {
        dynamic("idef") {
            execute<Player> { sender, context, _ ->
                val idef = context["idef"].uppercase()
                BaseManager.joinBase(sender, idef)
            }
            suggestion<CommandSender> { _, _ ->
                BaseManager.baseMap.keys.toList()
            }
        }
    }

    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val bases = BaseManager.baseMap.values
            sender.sendComponent("<gold>===== 基地列表 (${bases.size}) =====")
            for ((faction, group) in bases.groupBy { it.faction }) {
                sender.sendComponent("<${factionColor(faction)}><b>$faction</b> <gray>${group.size}个基地")
                group.forEach { base ->
                    val status = if (base.enable) "<green>可用" else "<red>不可用"
                    sender.sendComponent(" <gray>${base.idef} <white>- ${base.name} $status")
                }
            }
        }
        dynamic("faction") {
            execute<CommandSender> { sender, context, _ ->
                val faction = context["faction"].uppercase()
                val bases = BaseManager.getBasesByFaction(faction)
                if (bases.isEmpty()) {
                    sender.sendComponent("<red>未找到阵营 $faction 的基地")
                    return@execute
                }
                sender.sendComponent("<gold>${faction} 基地列表:")
                bases.forEach { base ->
                    val status = if (base.enable) "<green>可用" else "<red>不可用"
                    sender.sendComponent(" <${factionColor(faction)}>${base.idef} <white>- ${base.name} $status")
                    sender.sendComponent("   <gray>区域: ${base.region}")
                }
            }
            suggestion<CommandSender> { _, _ ->
                listOf("MEG", "BNTG", "UEC")
            }
        }
    }

    private fun factionColor(faction: String): String {
        return when (faction.uppercase()) {
            "MEG" -> "#ffdd00"
            "BNTG" -> "#33ffaa"
            "UEC" -> "#ff3d00"
            else -> "white"
        }
    }
}
