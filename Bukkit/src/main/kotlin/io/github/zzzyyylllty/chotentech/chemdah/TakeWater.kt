package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import ink.ptms.chemdah.core.quest.selector.InferItem.Companion.toInferItem
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.listener.TakeWaterEvent
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

