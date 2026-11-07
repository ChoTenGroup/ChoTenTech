package io.github.zzzyyylllty.chotentech.load

import com.sk89q.worldedit.world.World
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe

fun getRegion(
    world: World,
    regionid: String,
): ProtectedRegion? {
    val container = WorldGuard.getInstance().platform.regionContainer
    val regions = container[world]
    if (regions != null) {
        if (regions.getRegion(regionid) != null) {
            info("获取指定的区域 $regionid 于 ${world.name} 成功。正在返回 ${regions.getRegion(regionid)}")
            return regions.getRegion(regionid)
        } else {
            severe("无法获取指定的区域 $regionid 于 $world，将返回 null！")
            return null
        }
    } else {
        severe("$world 不存在任何区域！将返回 null！")
        return null
    }
}