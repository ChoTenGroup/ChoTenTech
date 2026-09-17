package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.event.QuetiapineLevelUpgradeEvent
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent



@SubscribeEvent(EventPriority.MONITOR)
fun onLevelUpgrade(e: QuetiapineLevelUpgradeEvent) {
    val player = e.player
    val levelCfg = levels[e.levelName] ?: return

    // 发送 MiniMessage 格式的升级消息
    val display = levelCfg.displayNames["default"] ?: "<#A9B4C3>${e.levelName}</#A9B4C3>"
    val msg = "<#DFBD7C>等级 $display <#EFDF8F><bold>提升!</bold></#EFDF8F> <#A9B4C3>${e.sourceLevel} → ${e.targetLevel}"
    player.sendMessage(mmUtil.deserialize(msg))

    // 运行 JS agents（不再包含 sendMessage，用于其他逻辑）
    levelCfg.agents?.runAgent("onUpgrade", linkedMapOf("event" to e, "player" to player, "level" to e.targetLevel), player)
    for (level in e.sourceLevel..e.targetLevel) {
        levelCfg.agents?.runAgent("onUpgradeSingle", linkedMapOf("event" to e, "player" to player, "level" to level), player)
    }
}
