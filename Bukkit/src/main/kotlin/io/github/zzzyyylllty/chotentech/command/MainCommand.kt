package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.runningPlatform
import taboolib.expansion.createHelper
import taboolib.module.nms.MinecraftVersion.minecraftVersion

@CommandHeader(
    name = "chotentech",
    aliases = ["ctt"],
    permission = "sertraline.command.main",
    description = "Main Command of DepazItems.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object MainCommand {

    @CommandBody
    val about = subCommand {
        execute<CommandSender> { sender, context, argument -> //
            sender.sendComponent("<gradient:aqua:yellow>ChoTenTech</gradient> <#ccccff>$pluginVersion")
            sender.sendComponent("<gradient:#6600ff:#aa00aa>Running on:</gradient> <light_purple>${runningPlatform.name} - $minecraftVersion")
            sender.sendComponent("<#660099>Plugin by AkaCandyKAngel.")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent("<gradient:aqua:yellow>reloading...")
            try {
                config.reload()
                sender.sendComponent("<gradient:aqua:yellow>reloading... Complete.")
            } catch (e: Exception) {
                sender.sendComponent("<red>Error thrown. check console for info.</red>")
                sender.sendComponent("<red>$e</red>")
            }
        }
    }

    @CommandBody
    val main = mainCommand {
        createHelper()
    }
    @CommandBody
    val debug = SubDebugCommand

}
