package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake


object NoclipObjective : ObjectiveCountableI<NoclipEvent>() {

    override val name = "choten noclip"
    override val event = NoclipEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler {
            it.player
        }
        addFullCondition("name") { profile, task, e ->
            val target = e.target?.let { task.condition["target"]?.toString()?.toRegex()?.matches(it) } ?: true
            val level = e.level?.let { task.condition["level"]?.toString()?.toRegex()?.matches(it) } ?: true
            val name = e.name?.let { task.condition["name"]?.toString()?.toRegex()?.matches(it) } ?: true
            target && level && name

        }
        addFullCondition("position") { profile, task, _ ->
            task.condition["position"]!!.toPosition().inside(profile.player.location)
        }

    }

    override fun getCount(profile: PlayerProfile, task: Task, event: NoclipEvent): Int {
        return 1
    }
}

@Awake(LifeCycle.ENABLE)
fun registerNoclipObjective(){
    NoclipObjective.register()
}

