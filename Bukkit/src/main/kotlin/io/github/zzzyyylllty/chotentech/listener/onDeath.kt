package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.platform.event.SubscribeEvent

@SubscribeEvent
fun onPlayerDeath(event: PlayerDeathEvent) {
    val player = event.player

    // 重置状态
    ChoTenDataHelper(player).resetSanity()
    ChoTenDataHelper(player).resetDarkness()

    // 清除原版掉落，使用自定义死亡掉落系统
    event.drops.clear()
    DeathDropManager.handlePlayerDeath(player)
}
