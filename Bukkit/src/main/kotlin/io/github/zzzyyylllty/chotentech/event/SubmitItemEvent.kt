package io.github.zzzyyylllty.chotentech.event

import ink.ptms.chemdah.core.quest.Quest
import ink.ptms.chemdah.core.quest.Task
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.platform.type.BukkitProxyEvent
import java.util.concurrent.ConcurrentHashMap

class SubmitItemEvent(
    val player: Player,
    val quest: Quest,
    val task: Task,
    val item: ItemStack?,
) : BukkitProxyEvent()