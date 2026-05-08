package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.gui.SubmitMenu
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "submititem",
    permissionDefault = PermissionDefault.OP,
    description = "打开提交物品GUI",
)
object SubmitItemCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("id") {
            execute<Player> { sender, context, _ ->
                SubmitMenu().open(sender, context["id"])
            }
            dynamic("player") {
                execute<CommandSender> { sender, context, _ ->
                    val target = Bukkit.getPlayerExact(context["player"])
                    if (target == null) {
                        sender.sendMessage("§c玩家 ${context["player"]} 不在线！")
                        return@execute
                    }
                    SubmitMenu().open(target, context["id"])
                }
            }
        }
    }
}
