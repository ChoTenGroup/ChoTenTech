package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "chatreload",
    aliases = ["chreload"],
    permission = "chotentech.chat.admin",
    permissionDefault = PermissionDefault.OP,
    permissionMessage = "",
    newParser = false,
)
object ChatAdminCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent("<yellow>正在重载聊天配置...")
            try {
                io.github.zzzyyylllty.chotentech.ChoTenTech.reloadCustomConfig()
                sender.sendComponent("<green>聊天配置重载完成。")
            } catch (e: Exception) {
                sender.sendComponent("<red>重载失败: $e")
            }
        }
    }
}
