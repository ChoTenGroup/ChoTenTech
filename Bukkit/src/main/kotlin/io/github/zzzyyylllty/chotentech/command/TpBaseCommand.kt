package io.github.zzzyyylllty.chotentech.command

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.function.submit

@CommandHeader(
    name = "tpbase",
    permission = "chotentech.command.tpbase",
    description = "传送到基地区域中心点",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object TpBaseCommand {

    @CommandBody
    val main = mainCommand {
        dynamic("base") {
            suggestion<Player> { _, _ ->
                BaseManager.baseMap.keys.toList()
            }
            execute<Player> { sender, context, _ ->
                val idef = context["base"].uppercase()
                val base = BaseManager.baseMap[idef]
                if (base == null) {
                    sender.sendComponent("<#E08A98>基地 $idef 不存在！")
                    return@execute
                }
                // 查找基地区域所在世界(WG 区域可能不在配置出生点所在世界)
                var regionWorld: org.bukkit.World? = null
                var region: ProtectedRegion? = null
                for (w in Bukkit.getWorlds()) {
                    val rg = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(w))?.getRegion(base.region)
                    if (rg != null) {
                        regionWorld = w
                        region = rg
                        break
                    }
                }
                if (region != null && regionWorld != null) {
                    // 与 tplevel 一致:传送到基地区域(WG)中心点
                    val reg = WorldEditRegionConverter.convertToRegion(region)
                    submit {
                        sender.teleport(Location(regionWorld, reg.center.x(), reg.center.y(), reg.center.z()))
                        sender.sendComponent("<#8FD4A6>已传送到 <#EFDF8F>${base.name}<#8FD4A6> 的中心点")
                    }
                } else {
                    // 区域不存在时回退到配置的出生点
                    val loc = base.spawn
                    val world = Bukkit.getWorld(loc.world)
                    if (world == null) {
                        sender.sendComponent("<#E08A98>基地 ${base.name} 的世界 ${loc.world} 不存在！")
                        return@execute
                    }
                    submit {
                        sender.teleport(Location(world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
                        sender.sendComponent("<#8FD4A6>已传送到 <#EFDF8F>${base.name}")
                    }
                }
            }
        }
    }
}
