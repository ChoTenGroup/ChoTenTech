package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.logger.sendStringAsComponent
import io.github.zzzyyylllty.chotentech.attribute.api.ChoTenAttributeAPI
import io.github.zzzyyylllty.chotentech.manager.AttributeManager.getAttributeDisplayName
import org.bukkit.entity.Player
import taboolib.common.platform.command.*
import taboolib.platform.util.asLangText

@CommandHeader(
    name = "chotenattribute-debug",
    aliases = ["attributedebug", "atbdebug"],
    permission = "chotenattribute.command.debug",
    description = "Debug Command for ChoTenAttribute.",
    permissionDefault = PermissionDefault.OP,
    newParser = false
)
object ChoTenAttributeDebugCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val show = subCommand {
        execute<Player> { player, _, _ ->
            val all = ChoTenAttributeAPI.instance.getAllAttributes(player)
            player.sendStringAsComponent(player.asLangText("Command_Debug_Show_Header"))
            all.forEach { (id, value) ->
                val displayName = getAttributeDisplayName(player, id)
                player.sendStringAsComponent(player.asLangText("Command_Debug_Show_Item", displayName, value))
            }
        }
    }
}
