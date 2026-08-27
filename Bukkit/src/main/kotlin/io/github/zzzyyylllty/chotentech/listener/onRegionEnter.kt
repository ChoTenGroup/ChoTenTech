package io.github.zzzyyylllty.chotentech.listener

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.transferLevelTask
import io.github.zzzyyylllty.chotentech.gui.BaseSavePointMenu
import io.github.zzzyyylllty.chotentech.load.getRegion
import io.github.zzzyyylllty.chotentech.util.toComponent
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
            for (region in enteredRegions) {
                try {
                    // 层级标题序列结束时刻(tick);基地标题延后到该时刻后发送,保证优先显示
                    var levelTitleEndTicks = 0L
                    val level = levelsList[region]
                    if (level != null) {
                        transferLevelTask(player, level)
                        ChoTenDataHelper(player).updateLevel()
                        val length = if (level.number == null) "Enigmatic Level".length else "Level ${level.number}".length
                        when (ProfileManager.getSharedData(player.uniqueId.toString()).settings.get("levelTitleMode")) {
                            // 动画 + 最终标题:最终标题在 5*(length+4) tick 发送,展示 1s+5s+2s
                            "on" -> levelTitleEndTicks = 5L * (length + 4) + 170
                            // 仅最终标题:立即发送,展示 8s
                            "no_animation" -> levelTitleEndTicks = 170L
                            // 关闭:无层级标题
                            else -> levelTitleEndTicks = 0L
                        }
                    }
                    // 检测基地区域
                    if (region.startsWith("base-")) {
                        val base = BaseManager.getBaseByRegion(region)
                        if (base != null) {
                            BaseManager.discoverBase(player, base.idef)
                            // 每次进入都显示;Minecraft 标题机制最后发送者优先,故延迟到层级标题结束后发送
                            submit(delay = levelTitleEndTicks) {
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
                        }
                    }
                    // 检测存档点区域
                    val saveBase = BaseManager.baseMap.values.firstOrNull { it.savePointRegion == region }
                    if (saveBase != null) {
                        val cooldownKey = "${player.uniqueId}:${saveBase.idef}"
                        val lastShown = savePointCooldown[cooldownKey]
                        if (lastShown == null || System.currentTimeMillis() - lastShown > 30000) {
                            savePointCooldown[cooldownKey] = System.currentTimeMillis()
                            submit {
                                BaseSavePointMenu(saveBase).open(player)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 单个区域的检测失败不影响其他区域(如 updateLevel 异常时基地标题仍要显示)
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