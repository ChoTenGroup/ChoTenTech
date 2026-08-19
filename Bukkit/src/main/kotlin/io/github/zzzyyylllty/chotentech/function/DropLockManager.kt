package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.event.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap

/** Drop-lock state: true = dropping allowed, false = blocked. Resets on each join. */
private val dropLockEnabled = ConcurrentHashMap<String, Boolean>()

@CommandHeader(
    name = "drop",
    aliases = [],
    permission = "chotentech.command.drop",
    description = "切换丢弃锁",
    permissionDefault = PermissionDefault.TRUE,
    newParser = false,
)
object DropCommand {

    @CommandBody
    val main = mainCommand {
        execute<Player> { sender, _, _ ->
            val uuid = sender.uniqueId.toString()
            val current = dropLockEnabled.getOrDefault(uuid, false)
            if (current) {
                dropLockEnabled[uuid] = false
                sender.sendComponent("<red>🔒 丢弃锁已<dark_red>关闭</dark_red>，物品将被阻止丢弃")
            } else {
                dropLockEnabled[uuid] = true
                sender.sendComponent("<green>🔓 丢弃锁已<dark_green>开启</dark_green>，你可以丢弃物品")
            }
        }
    }

    @CommandBody
    val on = subCommand {
        execute<Player> { sender, _, _ ->
            dropLockEnabled[sender.uniqueId.toString()] = true
            sender.sendComponent("<green>🔓 丢弃锁已<dark_green>开启</dark_green>，你可以丢弃物品")
        }
    }

    @CommandBody
    val off = subCommand {
        execute<Player> { sender, _, _ ->
            dropLockEnabled[sender.uniqueId.toString()] = false
            sender.sendComponent("<red>🔒 丢弃锁已<dark_red>关闭</dark_red>，物品将被阻止丢弃")
        }
    }
}

/** Drop-lock always disabled: items can be dropped freely. */
fun isDropLocked(uuid: String): Boolean = false

/** Block item dropping when drop lock is off. Disabled — 取消丢弃锁. */
@SubscribeEvent
fun onPlayerDropItem(event: PlayerDropItemEvent) {
    // 丢弃锁已取消，不再阻止丢弃
}

/** Reset drop lock to off on every join. Disabled — 取消丢弃锁. */
@SubscribeEvent
fun onJoinResetDropLock(event: PlayerJoinEvent) {
}
