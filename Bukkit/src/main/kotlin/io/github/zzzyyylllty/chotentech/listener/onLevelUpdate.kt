package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.event.QuetiapineAddExpEvent
import io.github.zzzyyylllty.chotentech.event.QuetiapineAddLevelEvent
import io.github.zzzyyylllty.chotentech.event.QuetiapineRemoveLevelEvent
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor

private var levelCheckTask: PlatformExecutor.PlatformTask? = null

/** 经验增加 → 检查升级(MONITOR:仅监听事件最终态,被取消或安全区拦截则跳过) */


@SubscribeEvent(EventPriority.MONITOR, ignoreCancelled = true)
fun onExpAdd(e: QuetiapineAddExpEvent) {
    submit {
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
