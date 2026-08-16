package io.github.zzzyyylllty.chotentech.punish

import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.function.submitAsync

@CommandHeader(
    name = "chotentech-unpunish",
    aliases = ["cttunpunish", "unpunish"],
    permission = "chotentech.command.unpunish",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object UnpunishCommand {
    @CommandBody
    val main = mainCommand {
        dynamic("target") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, argument ->
                submitAsync {
                    val targetInput = context["target"] ?: ""
                    val resolved = resolvePlayer(targetInput)
                    if (resolved == null) {
                        sender.sendComponent("<red>未找到玩家: $targetInput")
                        return@submitAsync
                    }
                    val (targetUuid, targetName) = resolved

                    val typeStr = argument.trim()
                    val type = if (typeStr.isBlank()) null
                    else runCatching { PunishType.valueOf(typeStr.uppercase()) }.getOrNull()

                    if (typeStr.isNotBlank() && type == null) {
                        sender.sendComponent("<red>未知处罚类型: $typeStr <gray>(可用: ${PunishType.entries.joinToString(", ") { it.name }},留空则解除全部)")
                        return@submitAsync
                    }

                    PunishManager.deactivateAllForPlayer(targetUuid, type)
                    val typeName = type?.displayName ?: "全部"
                    sender.sendComponent("<green>已解除 <yellow>$targetName <green>的${typeName}处罚")
                    if (type == null) {
                        getOnlinePlayerByName(targetName)?.sendComponent("<green>管理员已解除你的全部处罚")
                    }
                }
            }
        }
    }
}
