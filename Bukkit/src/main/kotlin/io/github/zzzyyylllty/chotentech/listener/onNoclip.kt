package io.github.zzzyyylllty.chotentech.listener

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.ApplicableRegionSet
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PowerManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerToggleSneakEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.warning
import kotlin.collections.get
import kotlin.collections.set
import kotlin.math.round
import kotlin.math.roundToLong


@SubscribeEvent
fun onPlayerSneak(event: PlayerToggleSneakEvent) {
    val player = event.player
    val uuid = player.uniqueId.toString()
    var data = playerDataMap[uuid]


// 当玩家在切出点时：noclipcooldown<0,noclippoint!=null,noclipping=true
// 当玩家离开切出点时：noclipcooldown<0,noclippoint==null,noclipping=false
// 当玩家完成切出时：noclipcooldown>0,noclippoint==null,noclipping=false

    // 如果玩家没有下蹲，设置切出点为null，切出中为false
    // 但 autoNoclip 的切出点或电梯触发的切出不因松开潜行而取消
    if (!event.isSneaking || player.location.pitch <= 85f) {
        if (data != null && data.noclipping
            && data.noclipPoint?.autoNoclip != true
            && !PowerManager.elevatorNoclipPlayers.contains(uuid)
        ) {
            playerDataMap[uuid] = data.copy(noclipping = false, noclipPoint = null)
            player.playSound(player, Sound.ENTITY_ENDER_EYE_DEATH, 1F, 0.5F)
            player.sendComponent("<gradient:red:#550000>切出已取消")
            return
        }
        return
    }

    // 如果玩家没有数据，则为玩家重设数据
    if (data == null) {
        warning("无法读取到玩家 $player 的数据！")
        return
    }
    data = playerDataMap[uuid] ?: return
    // 判断冷却
    if ((data.getCooldownLeftLong() ?: 0) > 0L) {
        player.playSound(player, Sound.ENTITY_VILLAGER_NO, 1F, 0.5F)
        player.sendComponent("<gradient:red:#666600>请等待 ${round((data.getCooldownLeftLong() ?: 0)/100.0)/10} 秒冷却...")
        return
    }
    data = playerDataMap[uuid] ?: return
    if (data.noclipPoint == null) {
        player.playSound(player, Sound.ENTITY_ENDER_EYE_LAUNCH, 1F, 0.5F)
        player.sendComponent("<#66CCFF>正在尝试切出。")
        val check = checkPlayerRegion(player, data, levelsList)
        if (check != null) {
            playerDataMap[uuid] = check
            data = playerDataMap[uuid] ?: return
            player.sendComponent("<#66CCFF>正在尝试切出，请注意松开潜行会导致取消。")
        }
    }

    if (playerDataMap[uuid]?.noclipPoint == null) {
        data.setCooldownSec(10.0)
        player.sendComponent("<#FF6666>此处不是切出点。")
    }
}


fun checkPlayerRegion(
    player: Player,
    list: PlayerData,
    level: LinkedHashMap<String, Level>,
): PlayerData? {
    val world = BukkitAdapter.adapt(player.world)
    val loc: Location = player.location
    val container = WorldGuard.getInstance().platform.regionContainer
    val regions = container.get(world) ?: return null
    val playerInRegions: ApplicableRegionSet =
        regions.getApplicableRegions(BlockVector3.at(loc.x.toInt(), loc.y.toInt(), loc.z.toInt()))
    // ^ 获取玩家在的区域

    for (playerLevel in playerInRegions) {
        // 获取每一个玩家所在的区域
        val levels = level[playerLevel.id]?.noclips
        if (levels != null) {
            for (noclipPoint in levels) {
                val isInNoclipPoint = checkPlayerIsInNoclipPoint(player, noclipPoint)
                if (isInNoclipPoint) {
                    // 如果符合条件，则将切出区设置为区域
                    return list.copy(
                        noclipPoint = noclipPoint,
                        noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * 1000).roundToLong(),
                        noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).roundToLong(),
                        noclipping = true
                    )
                }
            }
        }
    }
    return null
}

fun checkPlayerIsInNoclipPoint(
    player: Player,
    noclipPoint: NoclipPoint,
)
        : Boolean {
    val xmin =
        if (noclipPoint.noclipPointLocation1.x < noclipPoint.noclipPointLocation2.x) noclipPoint.noclipPointLocation1.x else noclipPoint.noclipPointLocation2.x
    val ymin =
        if (noclipPoint.noclipPointLocation1.y < noclipPoint.noclipPointLocation2.y) noclipPoint.noclipPointLocation1.y else noclipPoint.noclipPointLocation2.y
    val zmin =
        if (noclipPoint.noclipPointLocation1.z < noclipPoint.noclipPointLocation2.z) noclipPoint.noclipPointLocation1.z else noclipPoint.noclipPointLocation2.z
    val xmax =
        if (noclipPoint.noclipPointLocation1.x >= noclipPoint.noclipPointLocation2.x) noclipPoint.noclipPointLocation1.x else noclipPoint.noclipPointLocation2.x
    val ymax =
        if (noclipPoint.noclipPointLocation1.y >= noclipPoint.noclipPointLocation2.y) noclipPoint.noclipPointLocation1.y else noclipPoint.noclipPointLocation2.y
    val zmax =
        if (noclipPoint.noclipPointLocation1.z >= noclipPoint.noclipPointLocation2.z) noclipPoint.noclipPointLocation1.z else noclipPoint.noclipPointLocation2.z

    return (
            player.location.x in xmin..xmax
                    && player.location.y in ymin..ymax
                    && player.location.z in zmin..zmax
            )
}