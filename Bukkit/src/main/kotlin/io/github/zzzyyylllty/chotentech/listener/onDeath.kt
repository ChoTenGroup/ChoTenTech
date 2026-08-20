package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.NutritionManager
import io.github.zzzyyylllty.chotentech.data.cancelConsumableTasks
import io.github.zzzyyylllty.chotentech.feature.crossbow.CrossbowManager
import io.github.zzzyyylllty.chotentech.feature.isFirstTimeLookWindow
import io.github.zzzyyylllty.chotentech.feature.steampipe.SteamPipeManager
import io.github.zzzyyylllty.chotentech.feature.windowDamagedPlayers
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.function.savepoint.SavePointManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.function.MarshmallowManager
import io.github.zzzyyylllty.chotentech.function.ChocoManager
import io.github.zzzyyylllty.chotentech.function.HealthAnimationManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.function.timer.resetNoclipExpState
import io.github.zzzyyylllty.chotentech.quetiapine.util.mmPlainUtil
import io.github.zzzyyylllty.chotentech.quetiapine.util.mmUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

private val DEFAULT_SPAWN by lazy {
    Bukkit.getWorld("Area0")?.let { Location(it, -6.0, 14.0, 9.0) }
}

private val REGEX = Regex.fromLiteral("<insert.+?>")

@SubscribeEvent
fun onPlayerDeath(event: PlayerDeathEvent) {
    val player = event.player

    // 窗口击杀自定义死亡消息
    val displayName = RankManager.buildPlayerListNameExtra(player, "<yellow>", "</yellow>") // 前缀 + 玩家名 + 后缀(称号系统)
    if (windowDamagedPlayers.remove(player.uniqueId.toString())) {
        isFirstTimeLookWindow.remove(player.uniqueId.toString())
        event.deathMessage("<red>$displayName 被窗户吞噬了".toComponent())
    } else {
        val message = event.deathMessage()
        val orgMessage = message?.let { mmUtil.serialize(it) }?.replace(REGEX, "")
        if (orgMessage != null)
                event.deathMessage(mmUtil.deserialize("<red>" + orgMessage.replace(player.name, displayName + "<red>")))

    }

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

    // === 跳过死亡画面:播放僵尸村民治愈音效 + 骷髅emoji标题,1tick后立即操作API重生 ===
    if (!event.isCancelled) {
        submit(delay = 1) {
            if (player.isOnline) {
                player.spigot().respawn()
            }
        }
        submit(delay = 5) {
            if (player.isOnline && player.isDead) {
                player.spigot().respawn()
            }
        }
        player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.0f)
        player.showTitle(net.kyori.adventure.title.Title.title(
            "<red>💀".toComponent(),
            "<gray>你已死亡".toComponent()
        ))
    }
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
