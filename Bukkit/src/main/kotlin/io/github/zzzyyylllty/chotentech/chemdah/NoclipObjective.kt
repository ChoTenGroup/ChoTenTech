package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import ink.ptms.chemdah.core.quest.selector.InferItem.Companion.toInferItem
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.function.kether.evalKetherBoolean
import io.github.zzzyyylllty.sertraline.function.kether.evalKetherValue
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.hasItem
import taboolib.platform.util.isNotAir
import taboolib.platform.util.takeItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2


object NoclipObjective : ObjectiveCountableI<NoclipEvent>() {

    override val name = "choten noclip"
    override val event = NoclipEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler {
            it.player
        }
        addFullCondition("source") { profile, task, e ->
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
    SubmitItemObjective.register()
}

