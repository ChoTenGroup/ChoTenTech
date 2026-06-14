package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.cancelConsumableTasks
import io.github.zzzyyylllty.chotentech.feature.steampipe.SteamPipeManager
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent

private val DEFAULT_SPAWN by lazy {
    Bukkit.getWorld("Area0")?.let { Location(it, -7.0, 14.0, 6.0) }
}

@SubscribeEvent
fun onPlayerDeath(event: PlayerDeathEvent) {
    val player = event.player

    // 取消 consumable 延时任务
    cancelConsumableTasks(player.uniqueId.toString())

    // 重置状态
    ChoTenDataHelper(player).resetSanity()
    ChoTenDataHelper(player).resetDarkness()
    ChoTenDataHelper(player).resetExternalInjury()
    ChoTenDataHelper(player).resetInternalInjury()

    // 清除原版掉落，使用自定义死亡掉落系统
    event.drops.clear()
    DeathDropManager.handlePlayerDeath(player)

    // 重置蒸汽管道伤害计数（本条命）
    SteamPipeManager.clearPlayerHits(player)

    // Increment total deaths (shared across profiles)
    io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.getSharedData(player.uniqueId.toString()).totalDeaths++
}

@SubscribeEvent
fun onPlayerRespawn(event: PlayerRespawnEvent) {
    val data = playerDataMap[event.player.uniqueId.toString()]
    if (data != null) {
        data.customHunger = 80.0
        data.customSaturation = 20.0
    }
    val point = data?.respawnPoint
    if (point != null) {
        point.toLocation()?.let { event.respawnLocation = it }
    } else {
        DEFAULT_SPAWN?.let { event.respawnLocation = it }
    }
}
