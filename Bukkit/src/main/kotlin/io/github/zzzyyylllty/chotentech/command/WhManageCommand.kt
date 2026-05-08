package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "whmanage",
    aliases = ["whm"],
    permission = "chotentech.command.whmanage",
    description = "管理玩家仓库",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object WhManageCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val list = subCommand {
        dynamic("player") {
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["player"]
                val data = loadPlayerData(playerName)
                if (data == null) {
                    sender.sendComponent("<red>玩家 $playerName 不存在或没有数据！")
                    return@execute
                }

                sender.sendComponent("<gold>===== ${playerName} 仓库一览 =====")
                for ((baseIdef, contents) in data.warehouseContents) {
                    val baseName = BaseManager.baseMap[baseIdef]?.name ?: baseIdef
                    sender.sendComponent("<yellow>$baseName ($baseIdef): <gray>${contents.size} 个物品")
                    contents.entries.forEachIndexed { index, (id, _) ->
                        sender.sendComponent("  <gray>#${index + 1} (ID: $id)")
                    }
                }
                if (data.warehouseContents.isEmpty()) {
                    sender.sendComponent("<gray>（空）")
                }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        dynamic("player") {
            dynamic("base") {
                dynamic("index") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val baseIdef = context["base"].uppercase()
                        val index = context["index"].toIntOrNull()
                        if (index == null || index < 1) {
                            sender.sendComponent("<red>索引必须大于 0！")
                            return@execute
                        }

                        modifyWarehouse(playerName) { data ->
                            val contents = data.warehouseContents[baseIdef] ?: linkedMapOf()
                            val entry = contents.entries.toList().getOrNull(index - 1)
                            if (entry != null) {
                                data.warehouseContents[baseIdef]?.remove(entry.key)
                                sender.sendComponent("<green>已移除 ${playerName} 的 $baseIdef 仓库第 $index 个物品")
                            } else {
                                sender.sendComponent("<red>索引 $index 超出范围（共 ${contents.size} 个物品）！")
                            }
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val clear = subCommand {
        dynamic("player") {
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["player"]
                modifyWarehouse(playerName) { data ->
                    data.warehouseContents.clear()
                    sender.sendComponent("<green>已清空 ${playerName} 的所有仓库内容")
                }
            }
            dynamic("base") {
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["player"]
                    val baseIdef = context["base"].uppercase()
                    modifyWarehouse(playerName) { data ->
                        if (data.warehouseContents.remove(baseIdef) != null) {
                            sender.sendComponent("<green>已清空 ${playerName} 的 $baseIdef 仓库")
                        } else {
                            sender.sendComponent("<red>该基地没有仓库数据！")
                        }
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    BaseManager.baseMap.keys.toList()
                }
            }
        }
    }

    // ------- 工具方法 -------

    private fun loadPlayerData(playerName: String): PlayerData? {
        // 优先在线玩家
        val online = Bukkit.getPlayerExact(playerName)
        if (online != null) {
            return online.getData()
        }
        // 离线：从数据库读取
        val raw = DataManager.getRawDataByName(playerName) ?: return null
        return try {
            jsonUtils.fromJson(raw, PlayerData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun modifyWarehouse(playerName: String, action: (PlayerData) -> Unit) {
        val online = Bukkit.getPlayerExact(playerName)
        if (online != null) {
            // 在线玩家：直接修改内存数据并保存
            val data = online.getData()
            if (data != null) {
                action(data)
                DataManager.saveData(online)
            } else {
                ChoTenTech.consoleSender.sendComponent("<red>玩家 $playerName 没有数据！")
            }
            return
        }

        // 离线玩家：从数据库读取 -> 修改 -> 写回
        val raw = DataManager.getRawDataByName(playerName) ?: run {
            ChoTenTech.consoleSender.sendComponent("<red>玩家 $playerName 不存在或没有数据！")
            return
        }
        val data = try {
            jsonUtils.fromJson(raw, PlayerData::class.java)
        } catch (_: Exception) {
            ChoTenTech.consoleSender.sendComponent("<red>玩家 $playerName 数据解析失败！")
            return
        } ?: run {
            ChoTenTech.consoleSender.sendComponent("<red>玩家 $playerName 数据为空！")
            return
        }

        action(data)

        val json = jsonUtils.toJson(data, PlayerData::class.java)
        DataManager.table.insert(DataManager.dataSource, "player_name", "uuid", "cid", "cid_type", "data") {
            value(data.playerName, data.uuid, data.cid.id, data.cid.type, json)
            onDuplicateKeyUpdate { update("data", json) }
        }
    }
}
