package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.quetiapine.event.QuetiapineAddExpEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineAddLevelEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineRemoveLevelEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineSetExpEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineSetLevelEvent
import io.github.zzzyyylllty.quetiapine.util.devLog
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync

@SubscribeEvent
fun onExpAdd(e: QuetiapineAddExpEvent) {
    val player = e.player
    if (!e.isCancelled) submitAsync {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}


@SubscribeEvent
fun onExpSet(e: QuetiapineSetExpEvent) {
    val player = e.player
    if (!e.isCancelled) submitAsync {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}


@SubscribeEvent
fun onLevelRemove(e: QuetiapineRemoveLevelEvent) {
    val player = e.player
    if (!e.isCancelled) submitAsync {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}



@SubscribeEvent
fun onLevelSet(e: QuetiapineSetLevelEvent) {
    if (!e.isCancelled) submitAsync {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}


@SubscribeEvent
fun onLevelAdd(e: QuetiapineAddLevelEvent) {
    val player = e.player
    if (!e.isCancelled) submitAsync {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}

@Awake(LifeCycle.ACTIVE)
fun onNaturalCheckUpdate() {
    submitAsync(period = 100) {
        for (player in Bukkit.getOnlinePlayers()) {
            levels.forEach { PlayerDataManager(player).checkUpdateLevel(it.key) }
        }
    }
}
