package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync

@Awake(LifeCycle.ACTIVE)
fun baseSanityTimer() {
    submitAsync(period = 200) {
        for (player in Bukkit.getOnlinePlayers()) {
            val regions = playerRegions[player.uniqueId.toString()]
            val inBase = regions?.any { it.startsWith("base-") } == true
            if (!inBase) continue

            val data = playerDataMap[player.uniqueId.toString()] ?: continue
            if (data.sanity < 40.0) data.sanity = 40.0
            data.stress = 0.0
            if (data.darkness > 15.0) data.darkness -= 5.0
        }
    }
}
