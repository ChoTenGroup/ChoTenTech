package io.github.zzzyyylllty.chotentech.listener

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.util.Location
import com.sk89q.worldedit.util.task.Task
import com.sk89q.worldguard.LocalPlayer
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.ApplicableRegionSet
import com.sk89q.worldguard.protection.flags.Flag
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import com.sk89q.worldguard.session.MoveType
import com.sk89q.worldguard.session.Session
import com.sk89q.worldguard.session.SessionManager
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler
import com.sk89q.worldguard.session.handler.Handler
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.function.player.transferLevelTask
import io.github.zzzyyylllty.chotentech.load.getRegion
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import taboolib.common.LifeCycle.LOAD
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync
import java.util.UUID

@SubscribeEvent
fun onJoin(event: PlayerJoinEvent) {
    ChoTenDataHelper(event.player).updateLevel()
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