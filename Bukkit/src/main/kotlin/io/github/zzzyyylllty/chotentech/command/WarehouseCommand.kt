package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.gui.WarehouseMenu
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

@CommandHeader(
    name = "warehouse",
    aliases = ["wh"],
    permission = "chotentech.command.warehouse",
    permissionDefault = PermissionDefault.TRUE,
)
object WarehouseCommand {

    @CommandBody
    val warehouse = mainCommand {
        execute<Player> { sender, _, _ ->
            val data = sender.getData()
            if (data == null) {
                sender.sendComponent("<red>数据加载失败！")
                return@execute
            }
            val baseIdef = data.subTag ?: getCurrentBaseIdef(sender)
            if (baseIdef == null) {
                sender.sendComponent("<red>你没有加入任何基地，也无法检测到所在的基地！")
                return@execute
            }
            WarehouseMenu().open(sender, baseIdef)
        }
    }

    /** 通过玩家当前所在的 WorldGuard 区域查找对应的基地 ID */
    fun getCurrentBaseIdef(player: Player): String? {
        val regions = playerRegions[player.uniqueId.toString()] ?: return null
        for (regionId in regions) {
            if (regionId.startsWith("base-")) {
                val base = BaseManager.getBaseByRegion(regionId)
                if (base != null) return base.idef
            }
        }
        return null
    }
}

@CommandHeader(
    name = "whforce",
    aliases = ["whf"],
    permission = "chotentech.command.whforce",
    description = "强制打开指定基地的仓库",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object WhForceCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("base") {
            execute<Player> { sender, context, _ ->
                val baseIdef = context["base"].uppercase()
                if (BaseManager.baseMap[baseIdef] == null) {
                    sender.sendComponent("<red>基地 $baseIdef 不存在！")
                    return@execute
                }
                WarehouseMenu().open(sender, baseIdef)
            }
            suggestion<Player> { _, _ ->
                BaseManager.baseMap.keys.toList()
            }
        }
    }
}
