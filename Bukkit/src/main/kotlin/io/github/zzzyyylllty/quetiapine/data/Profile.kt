package io.github.zzzyyylllty.quetiapine.data

import io.github.zzzyyylllty.quetiapine.function.kether.getBukkitPlayer
import taboolib.module.kether.ScriptFrame


fun ScriptFrame.getProfile(): PlayerDataManager {
    val player = this.getBukkitPlayer()
    return PlayerDataManager(player)
}