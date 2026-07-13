package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

private var musicTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun musicTimer() {
    musicTask = submitAsync(period = 1200) {
        for (player in Bukkit.getOnlinePlayers()) {
            ChoTenDataHelper(player).playLevelMusic()
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelMusicTimer() {
    musicTask?.cancel()
}
