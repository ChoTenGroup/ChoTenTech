package io.github.zzzyyylllty.chotentech.listener

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.transferLevelTask
import io.github.zzzyyylllty.chotentech.gui.BaseSavePointMenu
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.LifeCycle.LOAD
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import java.util.concurrent.ConcurrentHashMap

@SubscribeEvent
fun onJoin(event: PlayerJoinEvent) {
    val player = event.player
    // Populate playerRegions from current position so updateLevel() can detect the level
    val regionContainer = WorldGuard.getInstance().platform.regionContainer
    val query = regionContainer.createQuery()
    val regions = query.getApplicableRegions(BukkitAdapter.adapt(player.location)).regions.map { it.id }.toSet()
    playerRegions[player.uniqueId.toString()] = regions
    ChoTenDataHelper(player).updateLevel()
}

@SubscribeEvent
fun onPlayerMove(event: PlayerMoveEvent) {
    val from = event.from
    val to = event.to

    if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

    val player = event.player
    val regionContainer = WorldGuard.getInstance().platform.regionContainer
    val query = regionContainer.createQuery()
    val currentRegions = query.getApplicableRegions(BukkitAdapter.adapt(to)).regions.map { it.id }.toSet()

    val previousRegions = playerRegions[player.uniqueId.toString()] ?: emptySet()

    // 进入新区域
    val enteredRegions = currentRegions - previousRegions
    if (enteredRegions.isNotEmpty()) {
        submitAsync {
            // 基地进入标题优先：先提交基地动画并算出它占用多少 tick，
            // 层级进入标题（打字机 HUD/标题/聊天消息）整体后移，避免两者同时播放
            var baseTitleTicks = 0L
            for (region in enteredRegions) {
                try {
                    // 检测基地区域
                    if (!region.startsWith("base-")) continue
                    val base = BaseManager.getBaseByRegion(region) ?: continue
                    BaseManager.discoverBase(player, base.idef)
                    // 每次进入都显示
                    submit {
                        if (!player.isOnline) return@submit
                        playBaseEnterTitle(player, base)
                    }
                    baseTitleTicks = maxOf(baseTitleTicks, baseEnterTitleTicks(base))
                } catch (e: Exception) {
                    // 单个区域的检测失败不影响其他区域
                    e.printStackTrace()
                }
            }

            // 检测层级区域：数据立即更新，只有标题/提示序列延后到基地标题之后
            for (region in enteredRegions) {
                try {
                    val level = levelsList[region] ?: continue
                    transferLevelTask(player, level, baseTitleTicks)
                    ChoTenDataHelper(player).updateLevel()
                } catch (e: Exception) {
                    // 单个区域的检测失败不影响其他区域
                    e.printStackTrace()
                }
            }

            // 检测存档点区域
            for (region in enteredRegions) {
                try {
                    val saveBase = BaseManager.baseMap.values.firstOrNull { it.savePointRegion == region } ?: continue
                    val cooldownKey = "${player.uniqueId}:${saveBase.idef}"
                    val lastShown = savePointCooldown[cooldownKey]
                    if (lastShown == null || System.currentTimeMillis() - lastShown > 30000) {
                        savePointCooldown[cooldownKey] = System.currentTimeMillis()
                        submit {
                            BaseSavePointMenu(saveBase).open(player)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 离开旧区域
    val exitedRegions = previousRegions - currentRegions
    if (exitedRegions.isNotEmpty()) {
        submitAsync {
            Thread.sleep(200)
            ChoTenDataHelper(player).updateLevel()
        }
    }

    playerRegions[player.uniqueId.toString()] = currentRegions
}

/** Cooldown map: "uuid:baseIdef" → timestamp, to avoid spamming the save-point UI */
private val savePointCooldown = ConcurrentHashMap<String, Long>()

/**
 * 基地进入标题动画占用的 tick 数，用于把层级进入标题排到它后面。
 * 对应 [playBaseEnterTitle]：逐帧阶段 period=2、共 (8 + 名称长度*2 + 3) 帧；
 * 之后 +4 tick 发送最终标题，最终标题展示 500+2000+500 ms（60 tick）；末尾留 5 tick 余量。
 */
private fun baseEnterTitleTicks(base: Base): Long =
    2L * (8 + base.name.length * 2 + 3) + 4L + 60L + 5L

/**
 * 基地进入标题动画:
 * 1. 名称占位显示为灰色乱码(<obfuscated> 抖动 "1" 串,等效 §k)
 * 2. 逐字替换为绿色基地名称(已解码部分为绿色,未解码部分保持乱码抖动)
 * 3. 全部解码完成后,显示最终蓝色名称 + 介绍副标题
 */
private fun playBaseEnterTitle(player: Player, base: Base) {
    val name = base.name
    val length = name.length

    // 乱码段:全为 "1",由 <obfuscated>(§k) 在客户端抖动成随机字形
    fun glitch(count: Int): String = "1".repeat(count)

    // 阶段 1+2:每 2 tick 刷新一次。前 8 tick 纯乱码抖动,之后逐字解码,
    // 全部解码后(绿色完整名称)再停留 3 帧进入阶段 3
    var tick = 0
    val totalFrames = 8 + length * 2 + 3
    submit(period = 2, delay = 0) {
        if (!player.isOnline) { cancel(); return@submit }
        if (tick >= totalFrames) {
            cancel()
            // 阶段 3:全部解码完成后显示最终蓝色名称 + 介绍副标题
            submit(delay = 4) {
                if (!player.isOnline) return@submit
                player.showTitle(net.kyori.adventure.title.Title.title(
                    "<#84D1CB><b>${base.name}</b></#84D1CB>".toComponent(),
                    base.title.toComponent(),
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(2000),
                        java.time.Duration.ofMillis(500)
                    )
                ))
            }
            return@submit
        }
        // 已解码字符数:前 8 tick 为 0,之后每 2 tick 解码 1 字
        val decoded = (((tick - 8).coerceAtLeast(0)) / 2).coerceAtMost(length)
        val titleStr = if (decoded <= 0) "<#6E7A8C><b><obfuscated>${glitch(length)}</obfuscated></b></#6E7A8C>"
            else "<#8FD4A6><b>${name.take(decoded)}</b><#6E7A8C><obfuscated>${glitch(length - decoded)}</obfuscated></#6E7A8C>"
        player.showTitle(net.kyori.adventure.title.Title.title(
            titleStr.toComponent(),
            "".toComponent(),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(0),
                java.time.Duration.ofMillis(200),
                java.time.Duration.ofMillis(0)
            )
        ))
        tick++
    }
}