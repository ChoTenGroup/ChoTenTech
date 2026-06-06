package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync

@Awake(LifeCycle.ACTIVE)
fun musicTimer() {
    submitAsync(period = 200) {
        for (player in Bukkit.getOnlinePlayers()) {
            ChoTenDataHelper(player).playLevelMusic()
        }
    }
}
