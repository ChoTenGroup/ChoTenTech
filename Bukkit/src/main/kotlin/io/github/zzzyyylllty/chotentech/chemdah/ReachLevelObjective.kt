package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import io.github.zzzyyylllty.chotentech.event.NoclipEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

/**
 * 到达层级 —— 监听 NoclipEvent，通过 target 判断到达哪个层级
 * 条件：
 *   target: 目标层级 ID（支持列表）
 */
object ReachLevelObjective : ObjectiveCountableI<NoclipEvent>() {

    override val name = "choten reach level"
    override val event = NoclipEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler { it.player }

        addSimpleCondition("target") { data, e ->
            data.asList().any { it.equals(e.target, true) }
        }

        addSimpleCondition("from") { data, e ->
            data.asList().any { it.equals(e.level, true) }
        }

        addSimpleCondition("name") { data, e ->
            data.asList().any { it.equals(e.name, true) }
        }
    }

    override fun getCount(profile: PlayerProfile, task: Task, event: NoclipEvent): Int {
        return 1
    }
}

@Awake(LifeCycle.ENABLE)
fun registerReachLevelObjective() {
    ReachLevelObjective.register()
}
