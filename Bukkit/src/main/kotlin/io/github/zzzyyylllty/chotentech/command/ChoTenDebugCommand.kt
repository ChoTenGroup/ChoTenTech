package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.IDData
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager.initData
import io.github.zzzyyylllty.chotentech.data.generateID
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "chotentech-debug",
    aliases = ["cttdebug"],
    permission = "chotentech.command.debug",
    description = "Debug Command for ChoTenTech.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ChoTenDebugCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val getDataMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(playerDataMap.entries.toString())
        }
    }

    @CommandBody
    val getLevelMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(levelsList.entries.toString())
        }
    }

    @CommandBody
    val getSDClassMap = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendComponent(sdClassList.entries.toString())
        }
    }
    @CommandBody
    val getIdExists = subCommand {
        dynamic("id"){
            execute<CommandSender> { sender, context, argument ->
                sender.sendComponent("${PlayerDataManager.isIDExist(context["id"])}")
            }
        }
    }
    @CommandBody
    val getDataById = subCommand {
        dynamic("id"){
            execute<CommandSender> { sender, context, argument ->
                sender.sendComponent("${PlayerDataManager.getDataByCID(context["id"])}")
            }
        }
    }
    @CommandBody
    val loadData = subCommand {
        execute<CommandSender> { sender, context, argument ->
            // 转化为Bukkit的Player
            val player = sender as Player
            player?.let { initData(it) }
        }
    }
    @CommandBody
    val getDataByPlayer = subCommand {
        player("user") {
            execute<CommandSender> { sender, context, argument ->
                val user = context.player("user")
                // 转化为Bukkit的Player
                val bukkitPlayer = user.castSafely<Player>()
                sender.sendComponent("${PlayerDataManager.getData(bukkitPlayer?.uniqueId.toString())}")
            }
        }
    }
    @CommandBody
    val changePlayerId = subCommand {
        player("user") {
            dynamic("id"){
                execute<CommandSender> { sender, context, argument ->
                    val user = context.player("user")
                    // 转化为Bukkit的Player
                    val bukkitPlayer = user.castSafely<Player>()
                    if (PlayerDataManager.isIDExist(context["id"]))
                        sender.sendComponent("userId must be unique")
                    else {
                        val data = bukkitPlayer?.let { PlayerDataManager.getData(it.uniqueId.toString()) }
                        bukkitPlayer?.let {
                            val id = context["id"].toLong()
                            if (data == null) sender.sendComponent("<red>未找到玩家数据")
                            data?.let { playerData -> PlayerDataManager.modifyUserID(id, playerData) }
                        }
                    }
                }
                dynamic("type"){
                    execute<CommandSender> { sender, context, argument ->
                        val user = context.player("user")
                        // 转化为Bukkit的Player
                        val bukkitPlayer = user.castSafely<Player>()
                        if (PlayerDataManager.isIDExist(context["id"]))
                            sender.sendComponent("userId must be unique")
                        else {
                            val data = bukkitPlayer?.let { PlayerDataManager.getData(it.uniqueId.toString()) }
                            bukkitPlayer?.let {
                                val id = context["id"].toLong()
                                val type = context["type"]
                                if (data == null) sender.sendComponent("<red>未找到玩家数据")
                                data?.let { playerData -> PlayerDataManager.modifyUserIDData(IDData(id, type), playerData) }
                            }
                        }
                    }
                }
            }
        }
    }
    @CommandBody
    val randomGenerateID = subCommand {
        execute<CommandSender> { sender, context, argument ->
            submitAsync {
                repeat(10) {
                    sender.sendComponent(generateID().toString())
                }
            }
        }
    }

}
