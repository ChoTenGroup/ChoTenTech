package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.dataMap
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.id.generateUserID
import io.github.zzzyyylllty.chotentech.function.sender.sendComponent
import io.github.zzzyyylllty.chotentech.function.sender.sendDebug
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync
import taboolib.expansion.createHelper

@CommandHeader(
    name = "chotentech-debug",
    aliases = ["cttdebug"],
    permission = "chotentech.command.debug",
    description = "Debug Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object SubDebugCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val getDataMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(Json.encodeToString(dataMap.entries))
        }
    }

    @CommandBody
    val getLootMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(Json.encodeToString(ChoTenTech.lootList.entries))
        }
    }
    @CommandBody
    val getSDClassMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(Json.encodeToString(ChoTenTech.sdClassList.entries))
        }
    }
    @CommandBody
    val getIdExists = subCommand {
        dynamic("id"){
            execute<CommandSender> { sender, context, argument ->
                sender.sendDebug("${SQLDataBase().getIDExists(context["id"])}")
            }
        }
    }
    @CommandBody
    val getDataById = subCommand {
        dynamic("id"){
            execute<CommandSender> { sender, context, argument ->
                sender.sendDebug("${SQLDataBase().getUserInDatabaseByUserID(context["id"])}")
            }
        }
    }
    @CommandBody
    val getDataByPlayer = subCommand {
        player("user") {
            execute<CommandSender> { sender, context, argument ->
                val user = context.player("user")
                // 转化为Bukkit的Player
                val bukkitPlayer = user.castSafely<Player>()
                sender.sendDebug("${SQLDataBase().getUserInDatabase(bukkitPlayer)}")
            }
        }
    }
    @CommandBody
    val randomGenerateID = subCommand {
        execute<CommandSender> { sender, context, argument ->
            submitAsync {
                repeat(10) {
                    sender.sendDebug(generateUserID())
                }
            }
        }
    }

}
