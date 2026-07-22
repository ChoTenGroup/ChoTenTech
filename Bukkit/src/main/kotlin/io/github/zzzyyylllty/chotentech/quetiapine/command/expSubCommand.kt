package io.github.zzzyyylllty.chotentech.quetiapine.command

import io.github.zzzyyylllty.embiancomponent.EmbianComponent.SafetyComponentSetter
import io.github.zzzyyylllty.embiancomponent.tools.getComponentsNMSFiltered
import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.command.createModernHelper
import io.github.zzzyyylllty.chotentech.quetiapine.data.DataUtil
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.quetiapine.logger.infoS
import io.github.zzzyyylllty.chotentech.quetiapine.logger.infoS
import io.github.zzzyyylllty.chotentech.quetiapine.logger.sendStringAsComponent
import io.github.zzzyyylllty.chotentech.quetiapine.logger.severeS
import io.github.zzzyyylllty.chotentech.quetiapine.util.minimessage.mmUtil
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.bool
import taboolib.common.platform.command.component.CommandComponent
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync
import taboolib.common.util.asList

import taboolib.module.nms.NMSItemTag.Companion.asNMSCopy
import taboolib.module.nms.getItemTag
import taboolib.platform.util.asLangText
import taboolib.platform.util.giveItem
import kotlin.run
import kotlin.text.replace
import kotlin.text.toDouble


@CommandHeader(
    name = "quetiapinelevel",
    aliases = ["rpglevel", "chotentechlevel", "cttlevel"],
    permission = "quetiapine.command.level",
    description = "level Command of Quetiapine.",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object LevelCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val help = subCommand {
        createModernHelper()
    }

    @CommandBody
    val setLevel = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final = PlayerDataManager(player).setLevel(key, value.toLong())
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerLevel_Reset_Console",
                                    player.name,
                                    levelName,
                                    final
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerLevel_Reset_Player",
                                    levelName,
                                    final
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            val final = PlayerDataManager(player).setLevel(key, value.toLong())
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerLevel_Reset_Console",
                                        player.name,
                                        levelName,
                                        final
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerLevel_Reset_Player",
                                        levelName,
                                        final
                                    )
                                )
                            }
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

    @CommandBody
    val addLevel = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val final = PlayerDataManager(player).addLevel(key, value.toLong())
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerLevel_Modify_Console",
                                    player.name,
                                    levelName,
                                    final
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerLevel_Modify_Player",
                                    levelName,
                                    final
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val final = PlayerDataManager(player).addLevel(key, value.toLong())
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerLevel_Modify_Console",
                                        player.name,
                                        levelName,
                                        final
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerLevel_Modify_Player",
                                        levelName,
                                        final
                                    )
                                )
                            }
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

    @CommandBody
    val removeLevel = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final =
                            PlayerDataManager(player).removeLevel(key, value.toLong())
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText("Operation_Cancelled"),
                                false
                            )
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerLevel_Modify_Console",
                                    player.name,
                                    levelName,
                                    final
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerLevel_Modify_Player",
                                    levelName,
                                    final
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            val final =
                                PlayerDataManager(player).removeLevel(key, value.toLong())
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText("Operation_Cancelled"),
                                    false
                                )
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerLevel_Modify_Console",
                                        player.name,
                                        levelName,
                                        final
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerLevel_Modify_Player",
                                        levelName,
                                        final
                                    )
                                )
                            }
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

    @CommandBody
    val resetLevel = subCommand {
        dynamic("key") {
            execute<CommandSender> { sender, context, argument ->
                val key = context["key"]
                val player = sender as? Player? ?: run {
                    sender.severeS(sender.asLangText("Player_Only_Command"))
                    return@execute
                }
                val isSilent = context.option("silent", "s") == ""
                val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                submitAsync {
                    val levelName = levels[key]?.getDefaultName() ?: run {
                        sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                        return@submitAsync
                    }
                    val final = PlayerDataManager(player).resetLevel(key)
                    if (final == null) {
                        if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                    } else {
                        if (!isConsoleSilent) sender.infoS(
                            sender.asLangText(
                                "PlayerLevel_Reset_Console",
                                player.name,
                                levelName,
                                final
                            ), false
                        )
                        if (!isSilent) player.sendStringAsComponent(
                            player.asLangText(
                                "PlayerLevel_Reset_Player",
                                levelName,
                                final
                            )
                        )
                    }
                }
            }
            player("player") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val tabooPlayer = context.player("player")
                    val player = tabooPlayer.castSafely<Player>() ?: run {
                        sender.severeS(sender.asLangText("Player_Not_Exist"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final = PlayerDataManager(player).resetLevel(key)
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerLevel_Reset_Console",
                                    player.name,
                                    levelName,
                                    final
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerLevel_Reset_Player",
                                    levelName,
                                    final
                                )
                            )
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }


    @CommandBody
    val setExp = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final = PlayerDataManager(player).setExp(key, value.toDouble())
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerExp_Modify_Console",
                                    player.name,
                                    levelName,
                                    final
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerExp_Modify_Player",
                                    levelName,
                                    final
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            val final = PlayerDataManager(player).setExp(key, value.toDouble())
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerExp_Modify_Console",
                                        player.name,
                                        levelName,
                                        final
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerExp_Modify_Player",
                                        levelName,
                                        final
                                    )
                                )
                            }
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

    @CommandBody
    val addExp = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final = PlayerDataManager(player).addExp(key, value.toDouble())
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerExp_Add_Console",
                                    player.name,
                                    levelName,
                                    value.toDouble()
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerExp_Add_Player",
                                    levelName,
                                    value.toDouble()
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            val final = PlayerDataManager(player).addExp(key, value.toDouble())
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerExp_Add_Console",
                                        player.name,
                                        levelName,
                                        value.toDouble()
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerExp_Add_Player",
                                        levelName,
                                        value.toDouble()
                                    )
                                )
                            }
                        }
                    }
                }
            }
            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

    @CommandBody
    val removeExp = subCommand {
        dynamic("key") {
            dynamic("value") {
                execute<CommandSender> { sender, context, argument ->
                    val key = context["key"]
                    val value = context["value"]
                    val player = sender as? Player? ?: run {
                        sender.severeS(sender.asLangText("Player_Only_Command"))
                        return@execute
                    }
                    val isSilent = context.option("silent", "s") == ""
                    val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                    submitAsync {
                        val levelName = levels[key]?.getDefaultName() ?: run {
                            sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                            return@submitAsync
                        }
                        val final = PlayerDataManager(player).removeExp(key, value.toDouble())
                        if (final == null) {
                            if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                        } else {
                            if (!isConsoleSilent) sender.infoS(
                                sender.asLangText(
                                    "PlayerExp_Remove_Console",
                                    player.name,
                                    levelName,
                                    value.toDouble()
                                ), false
                            )
                            if (!isSilent) player.sendStringAsComponent(
                                player.asLangText(
                                    "PlayerExp_Remove_Player",
                                    levelName,
                                    value.toDouble()
                                )
                            )
                        }
                    }
                }
                player("player") {
                    execute<CommandSender> { sender, context, argument ->
                        val key = context["key"]
                        val value = context["value"]
                        val tabooPlayer = context.player("player")
                        val player = tabooPlayer.castSafely<Player>() ?: run {
                            sender.severeS(sender.asLangText("Player_Not_Exist"))
                            return@execute
                        }
                        val isSilent = context.option("silent", "s") == ""
                        val isConsoleSilent = context.option("consolesilent", "cs", "csilent") == ""

                        submitAsync {
                            val levelName = levels[key]?.getDefaultName() ?: run {
                                sender.severeS(sender.asLangText("Level_Not_Exist"), false)
                                return@submitAsync
                            }
                            val final = PlayerDataManager(player).removeExp(key, value.toDouble())
                            if (final == null) {
                                if (!isConsoleSilent) sender.infoS(sender.asLangText("Operation_Cancelled"), false)
                            } else {
                                if (!isConsoleSilent) sender.infoS(
                                    sender.asLangText(
                                        "PlayerExp_Remove_Console",
                                        player.name,
                                        levelName,
                                        value.toDouble()
                                    ), false
                                )
                                if (!isSilent) player.sendStringAsComponent(
                                    player.asLangText(
                                        "PlayerExp_Remove_Player",
                                        levelName,
                                        value.toDouble()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            suggestion<CommandSender>(uncheck = true) { sender, context ->
                levels.keys.asList()
            }
        }
    }

}