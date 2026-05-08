package io.github.zzzyyylllty.chotentech.function.player

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import io.github.zzzyyylllty.chotentech.data.Level
import org.bukkit.entity.Player

fun Player.getPlayerLevel(): Level? {
    val regions = playerRegions[uniqueId.toString()]
    if (regions != null) {
        for (region in regions) {
            levelsList[region]?.let { return it }
        }
    }
    return null
}