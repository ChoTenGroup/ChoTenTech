package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import io.github.zzzyyylllty.chotentech.event.QuetiapineSetLevelEvent
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.api.SertralineAPIImpl
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

/**
 * 到达玩家等级 —— 监听 QuetiapineSetLevelEvent
 * 条件：
 *   level-name: 等级系统名称（支持列表，如 [main, mining]）
 *   min-level: 目标等级
 */
object ReachPlayerLevelObjective : ObjectiveCountableI<QuetiapineSetLevelEvent>() {

    override val name = "choten reach player level"
    override val event = QuetiapineSetLevelEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler { it.player }

        addSimpleCondition("level-name") { data, e ->
            data.asList().any { it.equals(e.levelName, true) }
        }


        addSimpleCondition("min-level") { data, e ->
            e.level >= data.toLong()
        }
    }

    override fun getCount(profile: PlayerProfile, task: Task, event: QuetiapineSetLevelEvent): Int {
        return 1
    }
}

@Awake(LifeCycle.ACTIVE)
fun registerReachPlayerLevelObjective() {
    ReachPlayerLevelObjective.register()
}
