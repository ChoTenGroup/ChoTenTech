package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.Sound
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import kotlin.time.Clock

@Awake(LifeCycle.ACTIVE)
fun autosave() {
    submitAsync (period = 5000) {
        devLog("[CHUNK] === 自动保存 ===")
        loadedChunk.forEach { (pos, province) ->
            DataManager.saveChunkData(pos.world, pos.x, pos.y)
        }
        devLog("[CHUNK] === 自动保存完成 ===")
    }
}
@Awake(LifeCycle.DISABLE)
fun quitSave() {
    loadedChunk.forEach { (pos, province) ->
        DataManager.saveChunkData(pos.world, pos.x, pos.y)
    }
}