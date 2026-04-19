package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.objective.Dependency
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import ink.ptms.chemdah.um.event.MobDeathEvent
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake


@Dependency("MythicMobs")
object MMobRangedDeath : ObjectiveCountableI<MobDeathEvent>() {

    override val name = "ranged mythicmobs kill"
    override val event = MobDeathEvent::class.java

    init {
        handler {
            it.killer as? Player
        }
        addSimpleCondition("position") { data, e ->
            data.toPosition().inside(e.killer!!.location)
        }
        addSimpleCondition("name") { data, e ->
            data.asList().any { it.equals(e.mob.id, true) }
        }
        addSimpleCondition("level") { data, e ->
            data.toDouble() == e.mob.level
        }
        addSimpleCondition("min-level") { data, e ->
            data.toDouble() <= e.mob.level
        }
        addSimpleCondition("distance") { data, e ->
            e.killer!!.location.distance(e.mob.entity.location) >= data.toDouble()
        }
        addConditionVariable("name") {
            it.mob.id
        }
    }
}

@Awake(LifeCycle.ENABLE)
fun registerMMobRangedDeath(){
    MMobRangedDeath.register()
}

