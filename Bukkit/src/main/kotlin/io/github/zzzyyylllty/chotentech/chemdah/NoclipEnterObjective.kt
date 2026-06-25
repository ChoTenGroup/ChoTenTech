package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import io.github.zzzyyylllty.chotentech.event.NoclipEnterEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

object NoclipEnterObjective : ObjectiveCountableI<NoclipEnterEvent>() {

    override val name = "choten noclip enter"
    override val event = NoclipEnterEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler {
            it.player
        }
        addFullCondition("name") { _, task, e ->
            e.name?.let { task.condition["name"]?.toString()?.toRegex()?.matches(it) } ?: true
        }
        addFullCondition("level") { _, task, e ->
            e.level?.let { task.condition["level"]?.toString()?.toRegex()?.matches(it) } ?: true
        }
    }

    override fun getCount(profile: PlayerProfile, task: Task, event: NoclipEnterEvent): Int {
        return 1
    }
}

@Awake(LifeCycle.ENABLE)
fun registerNoclipEnterObjective() {
    NoclipEnterObjective.register()
}
