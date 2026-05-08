package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.HideoutPlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutConfig
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.gui.HideoutMainMenu
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.*

@CommandHeader(
    name = "hideout",
    aliases = [],
    permission = "chotentech.command.hideout",
    description = "个人基地系统",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object HideoutCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val open = subCommand {
        execute<Player> { sender, _, _ ->
            openHideout(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: run {
                    sender.sendMessage("§c玩家不在线！")
                    return@execute
                }
                openHideout(target)
            }
        }
    }

    @CommandBody
    val unlock = subCommand {
        execute<Player> { sender, _, _ ->
            HideoutManager.unlockHideout(sender)
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>() ?: run {
                    sender.sendMessage("§c玩家不在线！")
                    return@execute
                }
                HideoutManager.unlockHideout(target)
            }
        }
    }

    @CommandBody
    val manage = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }

            execute<CommandSender> { sender, context, _ ->
                val target = resolveTarget(context["player"])
                if (target == null) {
                    sender.sendMessage("§c未找到该玩家的数据！")
                    return@execute
                }
                sender.sendComponent("<gold>===== ${context["player"]} 的藏身处 =====")
                for ((baseKey, hideout) in target.hideoutData) {
                    sender.sendComponent("<gray>基地: <yellow>$baseKey ${if (hideout.unlocked) "<green>已解锁" else "<red>未解锁"}")
                    sender.sendComponent("<gray>  燃料: <yellow>${"%.1f".format(hideout.fuelAmount)}")
                    if (hideout.moduleLevels.isNotEmpty()) {
                        sender.sendComponent("<gray>  模块等级:")
                        for ((modId, lv) in hideout.moduleLevels) {
                            val mod = HideoutConfig.getModule(modId)
                            sender.sendComponent("   <gray>- ${mod?.name ?: modId} <yellow>Lv.$lv")
                        }
                    }
                }
                if (target.hideoutData.isEmpty()) {
                    sender.sendComponent("<gray>  无藏身处数据")
                }
            }

            literal("setlevel") {
                dynamic("base") {
                    suggestion<CommandSender> { _, _ -> listOf("global", "MEG", "BNTG", "UEC") }
                    dynamic("module") {
                        suggestion<CommandSender> { _, _ -> HideoutConfig.modules.keys.toList() }
                        dynamic("level") {
                            execute<CommandSender> { sender, context, _ ->
                                val target = resolveTarget(context["player"])
                                if (target == null) { sender.sendMessage("§c未找到该玩家的数据！"); return@execute }
                                val baseKey = context["base"].uppercase()
                                val moduleId = context["module"]
                                val level = context["level"].toIntOrNull()
                                if (level == null || level < 0) { sender.sendMessage("§c等级必须 >= 0！"); return@execute }
                                val mod = HideoutConfig.getModule(moduleId)
                                if (mod == null) { sender.sendMessage("§c未知模块: $moduleId"); return@execute }
                                if (level > mod.maxLevel) { sender.sendMessage("§c模块最高等级为 ${mod.maxLevel}！"); return@execute }
                                val hideout = target.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
                                hideout.moduleLevels[moduleId] = level
                                sender.sendMessage("§a已将 ${context["player"]} 的 ${baseKey}.${mod.name} 等级设为 $level")
                            }
                        }
                    }
                }
            }

            literal("setfuel") {
                dynamic("base") {
                    suggestion<CommandSender> { _, _ -> listOf("global", "MEG", "BNTG", "UEC") }
                    dynamic("amount") {
                        execute<CommandSender> { sender, context, _ ->
                            val target = resolveTarget(context["player"])
                            if (target == null) { sender.sendMessage("§c未找到该玩家的数据！"); return@execute }
                            val baseKey = context["base"].uppercase()
                            val amount = context["amount"].toDoubleOrNull()
                            if (amount == null || amount < 0) { sender.sendMessage("§c燃料量必须 >= 0！"); return@execute }
                            val hideout = target.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
                            hideout.unlocked = true
                            hideout.fuelAmount = amount
                            sender.sendMessage("§a已将 ${context["player"]} 的 ${baseKey} 燃料设为 $amount")
                        }
                    }
                }
            }

            literal("reset") {
                dynamic("base") {
                    suggestion<CommandSender> { _, _ -> listOf("global", "MEG", "BNTG", "UEC", "all") }
                    execute<CommandSender> { sender, context, _ ->
                        val target = resolveTarget(context["player"])
                        if (target == null) { sender.sendMessage("§c未找到该玩家的数据！"); return@execute }
                        val baseKey = context["base"].uppercase()
                        if (baseKey == "ALL") {
                            target.hideoutData.clear()
                            sender.sendMessage("§a已重置 ${context["player"]} 的所有基地藏身处数据")
                        } else {
                            target.hideoutData.remove(baseKey)
                            sender.sendMessage("§a已重置 ${context["player"]} 的 ${baseKey} 藏身处数据")
                        }
                    }
                }
            }
        }
    }

    private fun openHideout(player: Player) {
        val data = player.getData() ?: return
        val baseKey = HideoutManager.getCurrentBaseKey(player)
        val hideout = data.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
        if (!hideout.unlocked) {
            player.sendComponent("<red>该基地的个人基地尚未解锁！")
            player.sendComponent("<gray>输入 <white>/hideout unlock<gray> 解锁")
            return
        }
        HideoutMainMenu(baseKey).open(player)
    }

    private fun resolveTarget(name: String): PlayerData? {
        return playerDataMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) || Bukkit.getPlayerExact(it.key)?.name?.equals(name, ignoreCase = true) == true }
            ?.value
            ?: DataManager.getDataByName(name)
    }
}
