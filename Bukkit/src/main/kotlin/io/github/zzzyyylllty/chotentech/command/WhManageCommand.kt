package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.UserDataRow
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
                    sender.sendComponent("<#E08A98>玩家 $playerName 不存在或没有数据！")
                    return@execute
                }

                sender.sendComponent("<#DFBD7C>===== ${playerName} 仓库一览 =====")
                for ((baseIdef, contents) in data.warehouseContents) {
                    val baseName = BaseManager.baseMap[baseIdef]?.name ?: baseIdef
                    sender.sendComponent("<#EFDF8F>$baseName ($baseIdef): <#A9B4C3>${contents.size} 个物品")
                    contents.entries.forEachIndexed { index, (id, _) ->
                        sender.sendComponent("  <#A9B4C3>#${index + 1} (ID: $id)")
                    }
                }
                if (data.warehouseContents.isEmpty()) {
                    sender.sendComponent("<#A9B4C3>（空）")
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
                            sender.sendComponent("<#E08A98>索引必须大于 0！")
                            return@execute
                        }

                        modifyWarehouse(playerName) { data ->
                            val contents = data.warehouseContents[baseIdef] ?: linkedMapOf()
                            val entry = contents.entries.toList().getOrNull(index - 1)
                            if (entry != null) {
                                data.warehouseContents[baseIdef]?.remove(entry.key)
                                sender.sendComponent("<#8FD4A6>已移除 ${playerName} 的 $baseIdef 仓库第 $index 个物品")
                            } else {
                                sender.sendComponent("<#E08A98>索引 $index 超出范围（共 ${contents.size} 个物品）！")
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
                    sender.sendComponent("<#8FD4A6>已清空 ${playerName} 的所有仓库内容")
                }
            }
            dynamic("base") {
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["player"]
                    val baseIdef = context["base"].uppercase()
                    modifyWarehouse(playerName) { data ->
                        if (data.warehouseContents.remove(baseIdef) != null) {
                            sender.sendComponent("<#8FD4A6>已清空 ${playerName} 的 $baseIdef 仓库")
                        } else {
                            sender.sendComponent("<#E08A98>该基地没有仓库数据！")
                        }
                    }
                }
                suggestion<CommandSender> { _, _ ->
                    BaseManager.baseMap.keys.toList()
                }
            }
        }
    }

    @CommandBody
    val reset = subCommand {
        dynamic("player") {
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["player"]
                resetWarehouse(playerName, null, sender)
            }
            dynamic("base") {
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["player"]
                    val baseIdef = context["base"].uppercase()
                    resetWarehouse(playerName, baseIdef, sender)
                }
                suggestion<CommandSender> { _, _ ->
                    BaseManager.baseMap.keys.toList()
                }
            }
        }
    }

    // ------- 工具方法 -------

    private fun resetWarehouse(playerName: String, baseIdef: String?, sender: CommandSender) {
        val online = Bukkit.getPlayerExact(playerName)
        if (online != null) {
            val data = online.getData()
            if (data == null) {
                sender.sendComponent("<#E08A98>玩家 $playerName 没有数据！")
                return
            }
            if (baseIdef != null) {
                if (BaseManager.resetBaseWarehouse(online, data, baseIdef)) {
                    sender.sendComponent("<#8FD4A6>已重置 ${playerName} 的 $baseIdef 仓库(已重新发放初始物资)")
                } else {
                    sender.sendComponent("<#E08A98>基地 $baseIdef 不存在！")
                }
            } else {
                BaseManager.resetAllWarehouses(online, data)
                sender.sendComponent("<#8FD4A6>已重置 ${playerName} 的所有仓库(已重新发放初始物资)")
            }
            DataManager.saveData(online)
            return
        }

        // 离线:读-改-写,无法立即发放,auto-grant 基地(ALP)下次进服自动补发
        val raw = DataManager.getRawDataByName(playerName) ?: run {
            sender.sendComponent("<#E08A98>玩家 $playerName 不存在或没有数据！")
            return
        }
        val data = try {
            jsonUtils.fromJson(raw, PlayerData::class.java)
        } catch (_: Exception) {
            sender.sendComponent("<#E08A98>玩家 $playerName 数据解析失败！")
            return
        } ?: run {
            sender.sendComponent("<#E08A98>玩家 $playerName 数据为空！")
            return
        }
        if (baseIdef != null) {
            if (BaseManager.resetBaseWarehouse(null, data, baseIdef)) {
                sender.sendComponent("<#8FD4A6>已重置 ${playerName} 的 $baseIdef 仓库(auto-grant 基地下次进服自动补发)")
            } else {
                sender.sendComponent("<#E08A98>基地 $baseIdef 不存在！")
            }
        } else {
            BaseManager.resetAllWarehouses(null, data)
            sender.sendComponent("<#8FD4A6>已重置 ${playerName} 的所有仓库(auto-grant 基地下次进服自动补发)")
        }
        val json = jsonUtils.toJson(data, PlayerData::class.java)
        DataManager.saveUserDataRow(UserDataRow().also {
            it.uuid = data.uuid
            it.playerName = data.playerName
            it.cid = data.cid.id.toString()
            it.cidType = data.cid.type
            it.data = json
        })
    }

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
                ChoTenTech.consoleSender.sendComponent("<#E08A98>玩家 $playerName 没有数据！")
            }
            return
        }

        // 离线玩家：从数据库读取 -> 修改 -> 写回
        val raw = DataManager.getRawDataByName(playerName) ?: run {
            ChoTenTech.consoleSender.sendComponent("<#E08A98>玩家 $playerName 不存在或没有数据！")
            return
        }
        val data = try {
            jsonUtils.fromJson(raw, PlayerData::class.java)
        } catch (_: Exception) {
            ChoTenTech.consoleSender.sendComponent("<#E08A98>玩家 $playerName 数据解析失败！")
            return
        } ?: run {
            ChoTenTech.consoleSender.sendComponent("<#E08A98>玩家 $playerName 数据为空！")
            return
        }

        action(data)

        val json = jsonUtils.toJson(data, PlayerData::class.java)
        DataManager.saveUserDataRow(UserDataRow().also {
            it.uuid = data.uuid
            it.playerName = data.playerName
            it.cid = data.cid.id.toString()
            it.cidType = data.cid.type
            it.data = json
        })
    }
}
