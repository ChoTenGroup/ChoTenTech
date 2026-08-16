package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.NutritionManager
import io.github.zzzyyylllty.chotentech.data.cancelConsumableTasks
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.feature.steampipe.SteamPipeManager
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.function.MarshmallowManager
import io.github.zzzyyylllty.chotentech.function.ChocoManager
import io.github.zzzyyylllty.chotentech.function.HealthAnimationManager
import io.github.zzzyyylllty.chotentech.function.timer.resetNoclipExpState
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent

private val DEFAULT_SPAWN by lazy {
    Bukkit.getWorld("Area0")?.let { Location(it, -6.0, 14.0, 9.0) }
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

    // 营养值死亡扣除
    NutritionManager.applyDeathDeduction(player)

    // 清除带 deathMark 的修饰符
    AttributeManager.removeDeathMarkedModifiers(player.uniqueId)

    // 重置棉花糖计数器并清除相关修饰符
    val pd = playerDataMap[player.uniqueId.toString()]
    if (pd != null) {
        pd.marshmallowCounter = 0
        pd.chocoCounter = 0
        pd.totalDeaths++ // per-profile death count
    }
    MarshmallowManager.removeModifiers(player)
    ChocoManager.removeAll(player)

    // 重置蒸汽管道伤害计数（本条命）
    SteamPipeManager.clearPlayerHits(player)

    // Reset rapid noclip counter
    resetNoclipExpState(player.uniqueId.toString())

    // Increment total deaths (shared across profiles)
    io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.getSharedData(player.uniqueId.toString()).totalDeaths++
}

@SubscribeEvent
fun onPlayerRespawn(event: PlayerRespawnEvent) {
    val player = event.player
    val data = playerDataMap[player.uniqueId.toString()]
    if (data != null) {
        data.customHunger = 80.0
        data.customSaturation = 20.0
    }
    HealthAnimationManager.sync(player)

    val respawnLoc = SavePointManager.getRespawnLocation(player)
    if (respawnLoc != null) {
        event.respawnLocation = respawnLoc
    } else {
        DEFAULT_SPAWN?.let { event.respawnLocation = it }
    }

    // Trigger 60s recall timer if respawning at a base save
    if (data != null && data.baseSave != null) {
        SavePointManager.onBaseSpawn(player, data)
    }
}
