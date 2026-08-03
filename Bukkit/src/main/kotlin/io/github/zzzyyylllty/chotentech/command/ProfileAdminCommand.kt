package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.player
import java.text.SimpleDateFormat
import java.util.Date

@CommandHeader(
    name = "chotentech-profile",
    aliases = ["cttprofile", "cttp"],
    permission = "chotentech.command.profileadmin",
    description = "管理员存档管理指令",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object ProfileAdminCommand {

    @CommandBody
    val main = mainCommand { createModernHelper() }

    /** /cttp list <player> — 列出该玩家的已存档profile */
    @CommandBody
    val list = subCommand {
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                val target = context.player("target").castSafely<Player>()!!
                val uuid = target.uniqueId.toString()
                val archived = ProfileManager.getArchivedProfileInfos(uuid)

                if (archived.isEmpty()) {
                    sender.sendComponent("<yellow>${target.name} 没有已存档的档案")
                    return@execute
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
                sender.sendComponent("<gold>${target.name} 的已存档档案 (${archived.size} 个):")
                for (info in archived) {
                    val time = if (info.archivedAt > 0) dateFormat.format(Date(info.archivedAt)) else "未知"
                    sender.sendComponent(" <gray>#${info.profileId} <white>${info.profileName} <dark_gray>($time)")
                }
            }
        }
    }

    /** /cttp restore <player> <profileId> — 恢复指定存档 */
    @CommandBody
    val restore = subCommand {
        player("target") {
            dynamic("profileId") {
                execute<CommandSender> { sender, context, _ ->
                    val target = context.player("target").castSafely<Player>()!!
                    val uuid = target.uniqueId.toString()
                    val profileId = context["profileId"]

                    if (ProfileManager.restoreProfile(uuid, profileId)) {
                        sender.sendComponent("<green>已恢复 ${target.name} 的档案 #$profileId")
                    } else {
                        sender.sendComponent("<red>恢复失败，请检查档案ID是否正确或名称是否冲突")
                    }
                }
            }
        }
    }
}
