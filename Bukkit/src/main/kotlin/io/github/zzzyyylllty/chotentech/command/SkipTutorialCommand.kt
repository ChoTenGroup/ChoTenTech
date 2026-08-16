package io.github.zzzyyylllty.chotentech.command

import ink.ptms.chemdah.api.ChemdahAPI.chemdahProfile
import ink.ptms.chemdah.api.ChemdahAPI.isChemdahProfileLoaded
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "chotentech-skiptutorial",
    aliases = ["cttskiptutorial", "skiptutorial"],
    permission = "chotentech.command.skiptutorial",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object SkipTutorialCommand {
    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            // 仅在教程任务已接取(进行中)时可用,未接取/已完成/已失败均拒绝
            val profile = if (sender.isChemdahProfileLoaded) sender.chemdahProfile else null
            val tutorialActive = profile?.questMap?.values?.any {
                it.template.id.equals("tutorial", ignoreCase = true)
            } ?: false
            if (!tutorialActive) {
                sender.sendComponent("<red>你还没有接取教程任务,无法跳过")
                return@execute
            }

            val shared = ProfileManager.getSharedData(sender.uniqueId.toString())
            shared.isCompletedTutorial = true
            ProfileManager.saveSharedData(sender.uniqueId.toString())

            // 传送至正常出生点 Area0
            val world = Bukkit.getWorld("Area0") ?: Bukkit.getWorlds().first()
            val loc = if (world.name == "Area0") Location(world, -6.0, 14.0, 5.0) else world.spawnLocation
            sender.teleport(loc)
            sender.sendComponent("<green>已跳过教学,传送至出生点")
        }
    }
}
