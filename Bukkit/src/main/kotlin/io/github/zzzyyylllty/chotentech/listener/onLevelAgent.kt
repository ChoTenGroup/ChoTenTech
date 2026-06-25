package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.quetiapine.data.LevelDataManager
import io.github.zzzyyylllty.quetiapine.event.QuetiapineLevelUpgradeEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent(EventPriority.MONITOR)
fun onLevelUpgrade(e: QuetiapineLevelUpgradeEvent) {
    val player = e.player
    val agents = levels[e.levelName]?.agents ?: return
    agents.runAgent("onUpgrade", linkedMapOf("event" to e, "player" to player, "level" to e.targetLevel), player)

    for (level in e.sourceLevel..e.targetLevel) {
        agents.runAgent("onUpgradeSingle", linkedMapOf("event" to e, "player" to player, "level" to level), player)
    }

}
