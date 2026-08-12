package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.OggDuration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap

private var musicTask: PlatformExecutor.PlatformTask? = null

/** 已挂起的精确播放任务（key: player uuid），轮询时会被刷新替换 */
private val pendingMusicTasks = ConcurrentHashMap<String, PlatformExecutor.PlatformTask>()

@Awake(LifeCycle.ACTIVE)
fun musicTimer() {
    // 10 tick 轮询；临近到期时按差额 tick 调度精确播放，避免轮询粒度造成静默间隔
    musicTask = submit(period = 10) {
        for (player in Bukkit.getOnlinePlayers()) {
            scheduleLevelMusic(player)
        }
    }
}

/** 已到期立即播放；未到期则计算差额 tick 调度精确播放（取代旧的挂起任务） */
private fun scheduleLevelMusic(player: Player) {
    val helper = ChoTenDataHelper(player)
    val data = helper.data
    val level = data.activeLevel ?: return
    val music = level.music ?: return
    val mode = ProfileManager.getSharedData(data.uuid).settings.get("levelMusicMode")
    if (mode == "off") return
    // once/count 模式播完后不再调度
    if (!helper.levelMusicCanPlay()) return
    val loopInterval = OggDuration.getDuration(music)
    val remainingMs = loopInterval - (System.currentTimeMillis() - data.lastMusicPlayTime)
    if (remainingMs <= 0) {
        helper.playLevelMusic()
        return
    }
    val remainingTicks = (remainingMs / 50).toInt().coerceAtLeast(1)
    pendingMusicTasks[data.uuid]?.cancel()
    pendingMusicTasks[data.uuid] = submit(delay = remainingTicks.toLong()) {
        pendingMusicTasks.remove(data.uuid)
        if (player.isOnline) helper.playLevelMusic()
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelMusicTimer() {
    musicTask?.cancel()
    pendingMusicTasks.values.forEach { it.cancel() }
    pendingMusicTasks.clear()
}
