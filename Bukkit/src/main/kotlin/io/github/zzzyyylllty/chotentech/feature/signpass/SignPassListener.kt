package io.github.zzzyyylllty.chotentech.feature.signpass

import org.bukkit.block.Sign
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import taboolib.common.platform.function.submit
import taboolib.platform.util.nextChat
import taboolib.common.platform.event.SubscribeEvent

object SignPassListener {

    @SubscribeEvent
    fun onAsyncChat(event: AsyncPlayerChatEvent) {
        if (SignPassManager.isWaiting(event.player)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        if (!block.type.name.contains("SIGN")) return

        val sign = block.state as? Sign ?: return
        val lines = sign.lines
        if (lines.none { it.contains("输入密码") }) return

        val locKey = LocationKey(block.world.name, block.x, block.y, block.z)
        val config = SignPassManager.getConfig(locKey) ?: return

        val player = event.player
        if (SignPassManager.isWaiting(player)) {
            player.sendMessage("§c你已有一个待处理的密码输入")
            return
        }

        event.isCancelled = true
        val uid = player.uniqueId
        SignPassManager.setWaiting(player)
        player.sendMessage("§e请输入密码（20秒内有效），输入 cancel 取消")

        player.nextChat { message ->
            if (!SignPassManager.isWaiting(uid)) return@nextChat
            SignPassManager.removeWaiting(uid)

            if (message.equals("cancel", ignoreCase = true) || message.equals("取消", ignoreCase = true)) {
                player.sendMessage("§c已取消")
                return@nextChat
            }

            if (message == config.password) {
                player.sendMessage("§a密码正确！")
                submit {
                    SignPassManager.executeActions(config.actions, player)
                }
            } else {
                player.sendMessage("§c密码错误！")
            }
        }

        // Timeout: 20 seconds
        submit(delay = 400L) {
            if (SignPassManager.isWaiting(uid)) {
                SignPassManager.removeWaiting(uid)
                player.sendMessage("§c输入超时！")
            }
        }
    }
}
