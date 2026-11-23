package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.isSafeWorld
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

private var baseSanityTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun baseSanityTimer() {
    baseSanityTask = submit(period = 200) {
        for (player in Bukkit.getOnlinePlayers()) {
            val regions = playerRegions[player.uniqueId.toString()]
            val inBase = regions?.any { it.startsWith("base-") } == true || isSafeWorld(player)
            if (!inBase) continue

            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            if (data.sanity < 40.0) data.sanity = 40.0
            data.stress = 0.0
            if (data.darkness > 15.0) data.darkness -= 5.0
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun cancelBaseSanityTimer() {
    baseSanityTask?.cancel()
}
