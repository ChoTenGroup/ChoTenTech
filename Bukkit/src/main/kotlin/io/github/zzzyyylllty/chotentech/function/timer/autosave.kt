package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.util.devLog
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

private var autosaveTask: PlatformExecutor.PlatformTask? = null

@Awake(LifeCycle.ACTIVE)
fun autosave() {
    autosaveTask = submitAsync (period = 5000) {
        devLog("[CHUNK] === 自动保存 ===")
        loadedChunk.forEach { (pos, province) ->
            DataManager.saveChunkData(pos.world, pos.x, pos.y)
        }
        devLog("[CHUNK] === 自动保存完成 ===")
    }
}

@Awake(LifeCycle.DISABLE)
fun quitSave() {
    autosaveTask?.cancel()
    loadedChunk.forEach { (pos, province) ->
        DataManager.saveChunkData(pos.world, pos.x, pos.y)
    }
}