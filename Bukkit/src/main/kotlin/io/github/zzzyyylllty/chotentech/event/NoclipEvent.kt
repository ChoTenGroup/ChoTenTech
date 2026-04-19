package io.github.zzzyyylllty.chotentech.event

import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.Task
import io.github.zzzyyylllty.chotentech.data.Level
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.platform.type.BukkitProxyEvent
import java.util.concurrent.ConcurrentHashMap

class NoclipEvent(
    val player: Player,
    val level: String?,
    val name: String?,
    val target: String?,
) : BukkitProxyEvent()