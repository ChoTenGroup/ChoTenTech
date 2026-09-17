package io.github.zzzyyylllty.chotentech.event

import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.DelayTo
import taboolib.platform.type.BukkitProxyEvent


class NoclipEnterEvent(
    val player: Player,
    val level: String?,
    val name: String?,
) : BukkitProxyEvent()
