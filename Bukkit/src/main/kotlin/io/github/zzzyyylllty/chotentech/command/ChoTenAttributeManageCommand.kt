package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.attribute.api.ChoTenAttributeAPI
import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager.getAttributeDisplayName
import io.github.zzzyyylllty.chotentech.logger.sendStringAsComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.*
import taboolib.platform.util.asLangText
import java.util.*

@CommandHeader(
    name = "chotenattribute-manage",
    aliases = ["attributemanage", "atbmanage"],
    permission = "chotenattribute.command.manage",
    description = "manage Command for ChoTenAttribute.",
    permissionDefault = PermissionDefault.OP,
    newParser = false
)
object ChoTenAttributeManageCommand {

    @CommandBody
    val main = mainCommand {
        createModernHelper()
    }

    @CommandBody
    val add = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("attrId") {
                suggestion<CommandSender> { _, _ -> AttributeManager.attributeRegistry.keys.toList() }
                dynamic("type") {
                    suggestion<CommandSender> { _, _ -> ModifierType.entries.map { it.name } }
                    dynamic("value") {
                        execute<CommandSender> { sender, context, _ ->
                            addModifier(sender, context["player"], context["attrId"], context["type"], context["value"], null)
                        }
                        dynamic("duration") {
                            execute<CommandSender> { sender, context, _ ->
                                addModifier(sender, context["player"], context["attrId"], context["type"], context["value"], context["duration"].toLongOrNull())
                            }
                        }
                    }
                }
            }
        }
    }

    /** 添加修饰符,duration 为 null 时永久;有持续时间时到期自动刷新移除(过期修饰符只在 updatePlayer 时清理) */
    private fun addModifier(sender: CommandSender, playerName: String, attrId: String, typeStr: String, valueStr: String, duration: Long?) {
        val player = Bukkit.getPlayer(playerName) ?: return sender.sendStringAsComponent(sender.asLangText("Command_Error_Player_Not_Found", playerName))
        val type = runCatching { ModifierType.valueOf(typeStr.uppercase()) }.getOrNull() ?: return sender.sendStringAsComponent(sender.asLangText("Command_Error_Invalid_Type", typeStr))
        val value = valueStr.toDoubleOrNull() ?: return sender.sendStringAsComponent(sender.asLangText("Command_Error_Invalid_Value", valueStr))

        val modifier = AttributeModifier(
            attributeId = attrId,
            value = value,
            type = type,
            source = ModifierSource.OTHER,
            expireOn = duration?.let { System.currentTimeMillis() + it * 1000 }
        )

        ChoTenAttributeAPI.instance.addModifier(player.uniqueId, modifier)
        val displayName = getAttributeDisplayName(sender, attrId)
        sender.sendStringAsComponent(sender.asLangText("Command_Manage_Add_Success", displayName, value, type.name, player.name))

        // 到期移除由 AttributeManager 的周期性过期清理任务兜底(不在此处用一次性延迟任务:
        // tick 边界触发可能早于 expireOn,判定未过期后没有任何重试,修饰符会永久残留)
        if (duration != null) {
            sender.sendStringAsComponent("§7持续 §e$duration §7秒,到期自动移除")
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("attrId") {
                suggestion<CommandSender> { _, _ -> AttributeManager.attributeRegistry.keys.toList() }
                dynamic("type") {
                    suggestion<CommandSender> { _, _ -> ModifierType.entries.map { it.name } }
                    dynamic("value") {
                        execute<CommandSender> { sender, context, _ ->
                            val player = Bukkit.getOfflinePlayer(context["player"])
                            val attrId = context["attrId"]
                            val type = runCatching { ModifierType.valueOf(context["type"].uppercase()) }.getOrNull() ?: return@execute sender.sendStringAsComponent(sender.asLangText("Command_Error_Invalid_Type", context["type"]))
                            val value = context["value"].toDoubleOrNull() ?: return@execute sender.sendStringAsComponent(sender.asLangText("Command_Error_Invalid_Value", context["value"]))

                            val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
                            val mods = data.modifiers.toMutableList()
                            mods.removeIf { it.attributeId == attrId && it.source == ModifierSource.OTHER }

                            val modifier = AttributeModifier(
                                attributeId = attrId,
                                value = value,
                                type = type,
                                source = ModifierSource.OTHER
                            )
                            mods.add(modifier)

                            data.modifiers = mods
                            AttributeManager.savePlayerData(data)

                            Bukkit.getPlayer(player.uniqueId)?.let { AttributeManager.updatePlayer(it) }
                            val displayName = getAttributeDisplayName(sender, attrId)
                            sender.sendStringAsComponent(sender.asLangText("Command_Manage_Set_Success", displayName, value, type.name, player.name ?: "Unknown"))
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val remove = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("uuid") {
                suggestion<CommandSender> { sender, context -> AttributeManager.getPlayerPersistentData(Bukkit.getOfflinePlayer(context["player"]).uniqueId).modifiers.map { it.uuid.toString() } }
                execute<CommandSender> { sender, context, _ ->
                    val player = Bukkit.getOfflinePlayer(context["player"])
                    val uuidString = context["uuid"]
                    val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: return@execute sender.sendMessage("§cInvalid UUID.")

                    ChoTenAttributeAPI.instance.removeModifier(player.uniqueId, uuid)
                    sender.sendStringAsComponent(sender.asLangText("Command_Manage_Remove_Attempt", uuidString, player.name ?: "Unknown"))
                }
            }
        }
    }

    @CommandBody
    val update = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, context, _ ->
                val player = Bukkit.getPlayer(context["player"]) ?: return@execute sender.sendStringAsComponent(sender.asLangText("Command_Error_Player_Not_Found", context["player"]))
                ChoTenAttributeAPI.instance.updatePlayerAttributes(player)
                sender.sendStringAsComponent(sender.asLangText("Command_Manage_Update_Success", player.name))
            }
        }
    }

    @CommandBody
    val list = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, context, _ ->
                val player = Bukkit.getOfflinePlayer(context["player"])
                val data = AttributeManager.getPlayerPersistentData(player.uniqueId)

                sender.sendStringAsComponent(sender.asLangText("Command_Manage_List_Header", player.name ?: "Unknown"))
                if (data.modifiers.isEmpty()) {
                    sender.sendStringAsComponent(sender.asLangText("Command_Manage_List_Empty"))
                } else {
                    data.modifiers.filterIsInstance<AttributeModifier>().forEach {
                        val displayName = getAttributeDisplayName(sender, it.attributeId)
                        sender.sendStringAsComponent(sender.asLangText("Command_Manage_List_Item", displayName, it.value, it.type.name, it.uuid.toString()))
                    }
                }
            }
        }
    }

    @CommandBody
    val clear = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, context, _ ->
                val player = Bukkit.getOfflinePlayer(context["player"])
                val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
                data.modifiers = emptyList()
                AttributeManager.savePlayerData(data)

                Bukkit.getPlayer(player.uniqueId)?.let { AttributeManager.updatePlayer(it) }
                sender.sendStringAsComponent(sender.asLangText("Command_Manage_Clear_Success", player.name ?: "Unknown"))
            }
        }
    }
}
