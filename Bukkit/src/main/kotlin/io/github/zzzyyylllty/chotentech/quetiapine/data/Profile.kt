package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.chotentech.quetiapine.function.kether.getBukkitPlayer
import taboolib.module.kether.ScriptFrame


fun ScriptFrame.getProfile(): PlayerDataManager {
    val player = this.getBukkitPlayer()
    return PlayerDataManager(player)
}