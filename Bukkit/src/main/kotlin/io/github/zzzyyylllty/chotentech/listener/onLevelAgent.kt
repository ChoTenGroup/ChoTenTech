package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineLevelUpgradeEvent
import io.github.zzzyyylllty.chotentech.quetiapine.util.minimessage.mmUtil
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent(EventPriority.MONITOR)
fun onLevelUpgrade(e: QuetiapineLevelUpgradeEvent) {
    val player = e.player
    val levelCfg = levels[e.levelName] ?: return

    // 发送 MiniMessage 格式的升级消息
    val display = levelCfg.displayNames["default"] ?: "<gray>${e.levelName}</gray>"
    val msg = "<gold>等级 $display <yellow><bold>提升!</bold></yellow> <gray>${e.sourceLevel} → ${e.targetLevel}"
    player.sendMessage(mmUtil.deserialize(msg))

    // 运行 JS agents（不再包含 sendMessage，用于其他逻辑）
    levelCfg.agents?.runAgent("onUpgrade", linkedMapOf("event" to e, "player" to player, "level" to e.targetLevel), player)
    for (level in e.sourceLevel..e.targetLevel) {
        levelCfg.agents?.runAgent("onUpgradeSingle", linkedMapOf("event" to e, "player" to player, "level" to level), player)
    }
}
