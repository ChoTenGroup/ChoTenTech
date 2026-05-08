package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.CoverMenu
import io.github.zzzyyylllty.chotentech.gui.InsuranceMenu
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "cttdrop",
    permission = "chotentech.command.cttdrop",
    permissionDefault = PermissionDefault.OP,
)
object CttDropCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§6/cttdrop insurance <player> §7- 打开投保菜单")
            sender.sendMessage("§6/cttdrop cover <player> §7- 取回投保物品")
        }
    }

    @CommandBody
    val insurance = subCommand {
        dynamic("player") {
            execute<CommandSender> { sender, context, _ ->
                val targetName = context["player"]
                val target = Bukkit.getPlayerExact(targetName)
                if (target == null) {
                    sender.sendMessage("§c玩家 $targetName 不在线！")
                    return@execute
                }
                InsuranceMenu().open(target)
                sender.sendMessage("§a已为 §e$targetName §a打开投保菜单。")
            }
        }
    }

    @CommandBody
    val cover = subCommand {
        dynamic("player") {
            execute<CommandSender> { sender, context, _ ->
                val targetName = context["player"]
                val target = Bukkit.getPlayerExact(targetName)
                if (target == null) {
                    // 离线玩家：直接读取数据库数据
                    val offlineData = io.github.zzzyyylllty.chotentech.data.DataManager.getDataByName(targetName)
                    if (offlineData == null || offlineData.returnedInsuranceItems.isEmpty()) {
                        sender.sendMessage("§c玩家 $targetName 没有待取回的物品。")
                        return@execute
                    }
                    sender.sendMessage("§e$targetName §a有 ${offlineData.returnedInsuranceItems.size} 个待取回的物品。")
                    return@execute
                }
                CoverMenu().open(target)
                sender.sendMessage("§a已为 §e$targetName §a打开取回菜单。")
            }
        }
    }
}
