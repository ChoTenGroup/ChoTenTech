package io.github.zzzyyylllty.quetiapine.event

import org.bukkit.entity.Player
import taboolib.platform.type.BukkitProxyEvent


class QuetiapineGetLevelEvent(
    val levelName: String,
    var level: Long,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineAddExpEvent(
    val levelName: String,
    var exp: Double,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineRemoveExpEvent(
    val levelName: String,
    var exp: Double,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineSetExpEvent(
    val levelName: String,
    var exp: Double,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineAddLevelEvent(
    val levelName: String,
    var addAmount: Long,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineRemoveLevelEvent(
    val levelName: String,
    var removeAmount: Long,
    val player: Player,
) : BukkitProxyEvent()


class QuetiapineSetLevelEvent(
    val levelName: String,
    var level: Long,
    val player: Player,
) : BukkitProxyEvent()

class QuetiapineResetLevelEvent(
    val levelName: String,
    val player: Player,
) : BukkitProxyEvent()

class QuetiapineLevelUpgradeEvent(
    val levelName: String,
    var sourceLevel: Long,
    var targetLevel: Long,
    var finalExp: Double,
    val player: Player,
) : BukkitProxyEvent()

