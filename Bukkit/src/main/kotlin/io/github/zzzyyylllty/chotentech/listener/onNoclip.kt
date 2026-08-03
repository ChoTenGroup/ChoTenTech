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
import io.github.zzzyyylllty.chotentech.event.NoclipEnterEvent
import io.github.zzzyyylllty.chotentech.function.noclip.ConditionResult
import io.github.zzzyyylllty.chotentech.function.noclip.NoclipConditionChecker
import io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerToggleSneakEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.warning
import kotlin.collections.get
import kotlin.collections.set
import io.github.zzzyyylllty.chotentech.function.timer.getNoclippingTimeMultiplier
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

    // 如果玩家没有下蹲或没有低头，不处理（离开区域由计时器检测取消）
    if (!event.isSneaking || player.location.pitch <= 85f) {
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
    var foundAnyNoclipPoint = false
    if (data.noclipPoint == null) {
        val (check, foundAny) = checkPlayerRegion(player, data, levelsList)
        foundAnyNoclipPoint = foundAny
        if (check != null) {
            playerDataMap[uuid] = check
            data = playerDataMap[uuid] ?: return
            player.playSound(player, Sound.ENTITY_ENDER_EYE_LAUNCH, 1F, 0.5F)
            player.sendActionBar("<green>保持在切出点内".toComponent())
        }
    }

    if (playerDataMap[uuid]?.noclipPoint == null && !foundAnyNoclipPoint) {
        data.setCooldownSec(10.0)
        player.sendComponent("<#FF6666>此处不是切出点。")
        player.sendActionBar("<red>此处不是切出点".toComponent())
    }
}


fun checkPlayerRegion(
    player: Player,
    list: PlayerData,
    level: LinkedHashMap<String, Level>,
): Pair<PlayerData?, Boolean> {
    val world = BukkitAdapter.adapt(player.world)
    val loc: Location = player.location
    val container = WorldGuard.getInstance().platform.regionContainer
    val regions = container.get(world) ?: return Pair(null, false)
    val playerInRegions: ApplicableRegionSet =
        regions.getApplicableRegions(BlockVector3.at(loc.x.toInt(), loc.y.toInt(), loc.z.toInt()))

    var foundAnyPoint = false

    for (playerLevel in playerInRegions) {
        val levelObj = level[playerLevel.id] ?: continue
        val levels = levelObj.noclips
        val enabledNames = RandomNoclipSelector.getEnabledNoclips(player.uniqueId.toString(), levelObj)

        for (noclipPoint in levels) {
            if (noclipPoint.autoNoclip) continue
            if (noclipPoint.elevatorOnly) continue
            if (noclipPoint.name != null && noclipPoint.name !in enabledNames) continue

            val isInNoclipPoint = checkPlayerIsInNoclipPoint(player, noclipPoint)
            if (!isInNoclipPoint) continue

            foundAnyPoint = true

            val condResult = NoclipConditionChecker.checkConditions(player, noclipPoint)
            when (condResult) {
                is ConditionResult.Allowed -> {
                    return Pair(list.copy(
                        noclipPoint = noclipPoint,
                        noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * getNoclippingTimeMultiplier(player) * 1000).roundToLong(),
                        noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).roundToLong(),
                        noclipping = true
                    ), true).also {
                        NoclipEnterEvent(player, playerLevel.id, noclipPoint.name).call()
                    }
                }
                is ConditionResult.NeedsSubmission -> {
                    NoclipConditionChecker.trySubmitItems(player, noclipPoint)
                    NoclipConditionChecker.trySubmitCar(player, noclipPoint)
                    val finalCheck = NoclipConditionChecker.checkConditions(player, noclipPoint)
                    when (finalCheck) {
                        is ConditionResult.Allowed -> {
                            return Pair(list.copy(
                                noclipPoint = noclipPoint,
                                noclipTimeEnd = System.currentTimeMillis() + (noclipPoint.noclippingTime * getNoclippingTimeMultiplier(player) * 1000).roundToLong(),
                                noclipCooldownEnd = System.currentTimeMillis() + (noclipPoint.cooldown * 1000).roundToLong(),
                                noclipping = true
                            ), true).also {
                                NoclipEnterEvent(player, playerLevel.id, noclipPoint.name).call()
                            }
                        }
                        is ConditionResult.Blocked -> {
                            player.sendActionBar((finalCheck.message ?: "需要满足条件").toComponent())
                        }
                        is ConditionResult.NeedsSubmission -> {
                            player.sendActionBar((finalCheck.message ?: "需要满足条件").toComponent())
                        }
                    }
                }
                is ConditionResult.Blocked -> {
                    player.sendActionBar((condResult.message ?: "需要满足条件").toComponent())
                }
            }
        }
    }
    return Pair(null, foundAnyPoint)
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

/**
 * 检查切出点是否为电梯切出点且电源已激活。
 * 如果不是电梯切出点 → 返回 true（允许正常切出）。
 * 如果是电梯切出点但电源未激活 → 返回 false（阻止切出）。
 */
