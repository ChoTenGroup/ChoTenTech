package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.ChunkPos
import io.github.zzzyyylllty.chotentech.data.IDData
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.DataManager.initData
import io.github.zzzyyylllty.chotentech.data.PowerManager
import io.github.zzzyyylllty.chotentech.data.generateID
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.int
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync
import kotlin.math.max
import kotlin.math.min

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
                sender.sendComponent("${DataManager.isIDExist(context["id"])}")
            }
        }
    }
    @CommandBody
    val getDataById = subCommand {
        dynamic("id"){
            execute<CommandSender> { sender, context, argument ->
                sender.sendComponent("${DataManager.getDataByCID(context["id"])}")
            }
        }
    }

    @CommandBody
    val chunks = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendMessage(loadedChunk.toString())
        }
    }

    @CommandBody
    val powers = subCommand {
        execute<CommandSender> { sender, context, argument ->
            sender.sendMessage(PowerManager.getRuntimeMap().toString())
        }
    }

    @CommandBody
    val chunkCurrent = subCommand {
        execute<CommandSender> { sender, context, argument ->
            val chunk = (sender as Player).chunk
            sender.sendMessage(loadedChunk[ChunkPos(chunk.world.name, chunk.x, chunk.z)].toString())
        }
    }

    @CommandBody
    val chunkSave = subCommand {
        int("x1") {
            int("z1") {
                int("x2") {
                    int("z2") {
                        dynamic("mode") {
                            execute<CommandSender> { sender, context, argument ->
                                submitAsync{
                                    val x1 = min(context["x1"].toDouble(), context["x2"].toDouble())
                                    val x2 = max(context["x1"].toDouble(), context["x2"].toDouble())
                                    val z1 = min(context["z1"].toDouble(), context["z2"].toDouble())
                                    val z2 = max(context["z1"].toDouble(), context["z2"].toDouble())
                                    val player = (sender as? Player?)!!
                                    val firstChunk = Location(player.world, x1, 100.0, z1).chunk
                                    val lastChunk = Location(player.world, x2, 100.0, z2).chunk
                                    sender.sendComponent("<#84D1CB>开始加载区块，请稍等。")
                                    for (chunkX in firstChunk.x..lastChunk.x) {
                                        for (chunkZ in firstChunk.z..lastChunk.z) {
                                            val chunk = player.world.getChunkAt(chunkX, chunkZ)
                                            sender.sendComponent("<#EFDF8F>开始执行 $chunkX $chunkZ")
                                            when (context["mode"]) {
                                                "CLEAR" -> DataManager.clearChunkData(chunk)
                                                "LOG_UNBREAK" -> DataManager.logUnbreakChunkData(chunk)
                                            }
                                            sender.sendComponent("<#8FD4A6>开始执行 $chunkX $chunkZ 完成。")
                                        }
                                    }
                                }
                            }
                            suggestion<CommandSender> { sender, context ->
                                listOf("CLEAR", "LOG_UNBREAK")
                            }
                        }
                    }
                }
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
                sender.sendComponent("${DataManager.getData(bukkitPlayer?.uniqueId.toString())}")
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
                    if (DataManager.isIDExist(context["id"]))
                        sender.sendComponent("userId must be unique")
                    else {
                        val data = bukkitPlayer?.let { DataManager.getData(it.uniqueId.toString()) }
                        bukkitPlayer?.let {
                            val id = context["id"].toLong()
                            if (data == null) sender.sendComponent("<#E08A98>未找到玩家数据")
                            data?.let { playerData -> DataManager.modifyUserID(id, playerData) }
                        }
                    }
                }
                dynamic("type"){
                    execute<CommandSender> { sender, context, argument ->
                        val user = context.player("user")
                        // 转化为Bukkit的Player
                        val bukkitPlayer = user.castSafely<Player>()
                        if (DataManager.isIDExist(context["id"]))
                            sender.sendComponent("userId must be unique")
                        else {
                            val data = bukkitPlayer?.let { DataManager.getData(it.uniqueId.toString()) }
                            bukkitPlayer?.let {
                                val id = context["id"].toLong()
                                val type = context["type"]
                                if (data == null) sender.sendComponent("<#E08A98>未找到玩家数据")
                                data?.let { playerData -> DataManager.modifyUserIDData(IDData(id, type), playerData) }
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
