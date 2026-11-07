package io.github.zzzyyylllty.chotentech.function.body

import io.github.zzzyyylllty.lithiumcarbon.event.ItemSearchStartEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object BodySearchHook {
    @SubscribeEvent(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun search(event: ItemSearchStartEvent) {
        if (event.skipSearch) return
        event.searchTime *= 1.0 + BodyHealthManager.armPenalties(event.player).search
    }
}
