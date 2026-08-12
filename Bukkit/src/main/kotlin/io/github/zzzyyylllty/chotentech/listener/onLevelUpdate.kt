package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineAddExpEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineAddLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineRemoveLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.util.devLog
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor

private var levelCheckTask: PlatformExecutor.PlatformTask? = null

@SubscribeEvent
fun onExpAdd(e: QuetiapineAddExpEvent) {
    val player = e.player
    if (!e.isCancelled) submit {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}


@SubscribeEvent
fun onLevelRemove(e: QuetiapineRemoveLevelEvent) {
    val player = e.player
    if (!e.isCancelled) submit {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}


@SubscribeEvent
fun onLevelAdd(e: QuetiapineAddLevelEvent) {
    val player = e.player
    if (!e.isCancelled) submit {
        PlayerDataManager(e.player).checkUpdateLevel(e.levelName)
    }
}

@Awake(LifeCycle.ACTIVE)
fun onNaturalCheckUpdate() {
    levelCheckTask = submit(period = 100) {
        for (player in Bukkit.getOnlinePlayers()) {
            levels.forEach { PlayerDataManager(player).checkUpdateLevel(it.key) }
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelLevelCheckTask() {
    levelCheckTask?.cancel()
}
