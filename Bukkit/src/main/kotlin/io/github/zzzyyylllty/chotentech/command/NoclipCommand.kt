package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.reloadCustomConfig
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.command.component.CommandComponentDynamic
import taboolib.common.platform.command.component.CommandComponentLiteral
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.runningPlatform
import taboolib.module.lang.asLangText
import taboolib.module.nms.MinecraftVersion.minecraftVersion

@CommandHeader(
    name = "noclips",
    aliases = ["noclip"],
    permission = "chotentech.command.checknoclips",
    description = "checknoclips Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object NoclipsCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            val sender = sender as? Player? ?: return@execute
            val data = sender.getData() ?: return@execute
            val str = StringBuilder()
            str.append("<gray>${sender.name} - 层级 ${data.activeLevel?.name ?: "???"}")
            str.appendLine()
            var num = 1
            str.appendLine("<white><st>                                   ")
            str.appendLine()
            val noclips = data.activeLevel?.noclips ?: run {
                str.appendLine(" <red>未找到任何可用切出点。")
                str.appendLine()
                sender.sendStringAsComponent(str.toString())
                return@execute
            }
            for (noclip in noclips) {
                str.appendLine(" <dark_gray>| <#47A036>NOCLIP$num</#47A036> <#CECACA>${noclip.name}</#CECACA> <#FFAA00>${noclip.targetLevel?.let { levelsList[it]?.name } ?: "未知"}<reset>")
                num++
            }
            str.appendLine("<white><st>                                   ")
            sender.sendStringAsComponent(str.toString())
        }
    }
}
