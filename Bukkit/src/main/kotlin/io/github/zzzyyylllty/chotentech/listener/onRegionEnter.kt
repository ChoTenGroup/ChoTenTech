package io.github.zzzyyylllty.chotentech.listener

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.transferLevelTask
import io.github.zzzyyylllty.chotentech.gui.BaseSavePointMenu
import io.github.zzzyyylllty.chotentech.load.getRegion
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
                if (levelsList[region] != null) {
                    transferLevelTask(player, levelsList[region]!!)
                    ChoTenDataHelper(player).updateLevel()
                }
                // 检测基地区域
                if (region.startsWith("base-")) {
                    val base = BaseManager.getBaseByRegion(region)
                    if (base != null) {
                        BaseManager.discoverBase(player, base.idef)
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