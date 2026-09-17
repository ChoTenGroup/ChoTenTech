package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.reloadCustomConfig
import io.github.zzzyyylllty.chotentech.command.ChoTenAttributeMainCommand
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
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
    name = "chotentech",
    aliases = ["ctt"],
    permission = "chotentech.command.main",
    description = "Main Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object MainCommand {

    @CommandBody
    val about = subCommand {
        execute<CommandSender> { sender, context, argument -> //
            sender.sendComponent("<gradient:#84D1CB:#EFDF8F>ChoTenTech</gradient> <#d9d9f2>$pluginVersion")
            sender.sendComponent("<gradient:#946ecf:#ad3aad>Running on:</gradient> <#CDAEEF>${runningPlatform.name} - $minecraftVersion")
            sender.sendComponent("<#7d36a1>Plugin by AkaCandyKAngel.")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent("<gradient:#84D1CB:#EFDF8F>reloading...")
            try {
                reloadCustomConfig()
                // saves.yml 热重载(主线程执行,异步 reloadCustomConfig 不包含存档点)
                io.github.zzzyyylllty.chotentech.function.savepoint.SavePointConfig.load()
                io.github.zzzyyylllty.chotentech.function.savepoint.SavePointManager.createHolograms()
                sender.sendComponent("<gradient:#84D1CB:#EFDF8F>reloading... Complete.")
            } catch (e: Exception) {
                sender.sendComponent("<#E08A98>Error thrown. check console for info.</#E08A98>")
                sender.sendComponent("<#E08A98>$e</#E08A98>")
            }
        }
    }

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }
    @CommandBody
    val debug = ChoTenDebugCommand
    @CommandBody
    val base = BaseCommand
//    @CommandBody
//    val coolnumber = CoolNumberCommand
    @CommandBody
    val interfere = InterfereCommand
    @CommandBody
    val pvp = PvpCommand
    @CommandBody
    val attribute = ChoTenAttributeMainCommand
//    @CommandBody
//    val Data = ChoTenDataCommand
    @CommandBody
    val gametime = GameTimeCommand
    @CommandBody
    val profileinfo = ProfileInfoCommand
    @CommandBody
    val ui = ChoTenUICommand
    @CommandBody
    val settings = SettingsCommand
    @CommandBody
    val profile = ProfileAdminCommand

}

fun CommandComponent.createModernHelper(checkPermissions: Boolean = true) {
    execute<ProxyCommandSender> { sender, context, _ ->
        val command = context.command
        val builder = StringBuilder("<gradient:#EFDF8F:#84D1CB>Usage: /${command.name}<gradient>")
        var newline = false

        fun check(children: List<CommandComponent>): List<CommandComponent> {
            val filterChildren = if (checkPermissions) {
                children.filter { sender.hasPermission(it.permission) }
            } else {
                children
            }
            return filterChildren.filter { it !is CommandComponentLiteral || !it.hidden }
        }

        fun space(space: Int): String {
            return (1..space).joinToString("") { " " }
        }

        fun print(compound: CommandComponent, index: Int, size: Int, offset: Int = 8, level: Int = 0, end: Boolean = false, optional: Boolean = false) {
            var option = optional
            var comment = 0
            when (compound) {
                is CommandComponentLiteral -> {
                    if (size == 1) {
                        builder.append(" ").append("<gradient:#bad9e8:#ffffff>${compound.aliases[0]}<>")
                    } else {
                        newline = true
                        builder.appendLine()
                        builder.append(space(offset))
                        if (level > 1) {
                            builder.append(if (end) " " else "<#a7a7a7>│")
                        }
                        builder.append(space(level))
                        if (index + 1 < size) {
                            builder.append("<gradient:#a7a7a7:#d9d9d9>├── </gradient>")
                        } else {
                            builder.append("<gradient:#a7a7a7:#d9d9d9>└── </gradient>")
                        }
                        builder.append("<gradient:#bad9e8:#ffffff>${compound.aliases[0]}</gradient>")
                    }
                    option = false
                    comment = compound.aliases[0].length
                }
                is CommandComponentDynamic -> {
                    val value = if (compound.comment.startsWith("@")) {
                        sender.asLangText(compound.comment.substring(1))
                    } else {
                        compound.comment
                    }
                    comment = if (compound.optional || option) {
                        option = true
                        builder.append(" ").append("<#bae8d9>[<$value>]")
                        compound.comment.length + 4
                    } else {
                        builder.append(" ").append("<#e8d9ba><$value>")
                        compound.comment.length + 2
                    }
                }
            }
            if (level > 0) {
                comment += 1
            }
            val checkedChildren = check(compound.children)
            checkedChildren.forEachIndexed { i, children ->
                if (newline) {
                    print(children, i, checkedChildren.size, offset, level + comment, end, option)
                } else {
                    val length = if (offset == 8) command.name.length + 1 else comment + 1
                    print(children, i, checkedChildren.size, offset + length, level, end, option)
                }
            }
        }
        val checkedChildren = check(context.commandCompound.children)
        val size = checkedChildren.size
        checkedChildren.forEachIndexed { index, children ->
            print(children, index, size, end = index + 1 == size)
        }
        builder.lines().forEach {
            sender.castSafely<CommandSender>()?.sendComponent(it)
        }
    }
}

