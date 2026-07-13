package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import io.github.zzzyyylllty.chotentech.listener.TakeWaterEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake


object TakeWaterObjective : ObjectiveCountableI<TakeWaterEvent>() {

    override val name = "take water"
    override val event = TakeWaterEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler {
            it.player
        }

    }

    override fun getCount(profile: PlayerProfile, task: Task, event: TakeWaterEvent): Int {
        return 1
    }
}

@Awake(LifeCycle.ENABLE)
fun registerTakeWater() {
    TakeWaterObjective.register()
}

