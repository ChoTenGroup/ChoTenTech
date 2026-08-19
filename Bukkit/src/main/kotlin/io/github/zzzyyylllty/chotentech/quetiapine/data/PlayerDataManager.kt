package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineAddExpEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineAddLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineGetLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineLevelUpgradeEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineRemoveExpEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineRemoveLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineResetLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineSetExpEvent
import io.github.zzzyyylllty.chotentech.quetiapine.event.QuetiapineSetLevelEvent
import io.github.zzzyyylllty.chotentech.quetiapine.logger.warningS
import io.github.zzzyyylllty.chotentech.quetiapine.util.devLog
import org.bukkit.entity.Player
import kotlin.math.max

/**
 * PlayerDataManager using per-profile data (via QuetiapineDataBridge) instead of DataContainer.
 */
class PlayerDataManager(val player: Player) {

    private val map: MutableMap<String, String> get() = QuetiapineDataBridge.getDataMap(player)

    companion object {
        private val upgrading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }

    fun getLevelWithoutEvent(levelName: String): Long? {
        return map["level.$levelName"]?.toLong()
    }
    fun getNextLevelExp(levelName: String): Double? {
        return LevelDataManager.getRequiredExp(levelName, player, (getLevel(levelName) ?: 0) + 1)
    }
    fun getLevelExp(levelName: String, level: Long): Double? {
        return LevelDataManager.getRequiredExp(levelName, player, level)
    }
    fun getLevelOrStartWithoutEvent(levelName: String): Long? {
        return map["level.$levelName"]?.toLong() ?: levels[levelName]?.level?.start ?: run {
            warningS("There are No Level levelNamed $levelName")
            null
        }
    }
    fun addLevel(levelName: String, level: Long): Long? {

        val template = levels[levelName]?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        val event = QuetiapineAddLevelEvent(levelName, level, player)
        event.call()
        if (!event.isCancelled) {
            val final = limit(template.level.min, (getLevelOrStart(levelName) ?: 0) + event.addAmount, template.level.getMaxLevel(player) ?: Long.MAX_VALUE)
            map["level.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun removeLevel(levelName: String, level: Long): Long? {
        val template = levels[levelName]?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        val event = QuetiapineRemoveLevelEvent(levelName, level, player)
        event.call()
        if (!event.isCancelled) {
            val final = limit(template.level.min, (getLevelOrStart(levelName) ?: 0) - event.removeAmount, template.level.getMaxLevel(player) ?: Long.MAX_VALUE)
            map["level.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun setLevel(levelName: String, level: Long): Long? {
        val template = levels[levelName]?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        val event = QuetiapineSetLevelEvent(levelName, level, player)
        event.call()
        if (!event.isCancelled) {
            val final = limit(template.level.min, event.level ?: 0L, template.level.getMaxLevel(player) ?: Long.MAX_VALUE)
            map["level.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun resetLevel(levelName: String): Long? {
        val template = levels[levelName]?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        val event = QuetiapineResetLevelEvent(levelName, player)
        event.call()
        if (!event.isCancelled) {
            val final = limit(template.level.min, template.level.start, template.level.getMaxLevel(player) ?: Long.MAX_VALUE)
            map["level.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun getLevel(levelName: String): Long? {
        val event = QuetiapineGetLevelEvent(levelName, getLevelWithoutEvent(levelName) ?: return null, player)
        event.call()
        return event.level
    }
    fun checkUpdateLevel(levelName: String) {
        val lockKey = "${player.uniqueId}:$levelName"
        if (!upgrading.add(lockKey)) {
            devLog("checkUpdateLevel already running for $lockKey, skipping")
            return
        }
        try {
            // 必须在 try 内 return:否则 lockKey 残留导致后续升级检查永久跳过
            if (BaseManager.isInLevelSafeZone(player)) {
                devLog("checkUpdateLevel skipped for ${player.name}: in base or world")
                return
            }
            devLog("Checking update for $levelName")
            var required = getNextLevelExp(levelName) ?: return
            var current = getExp(levelName)
            val sourceLevel = getLevel(levelName)
            var cLevel = sourceLevel ?: return
            devLog("required $required current $current CLevel $cLevel")
            if (required <= current) {
                while (required <= current) {
                    current -= required
                    cLevel++
                    val nextReq = getLevelExp(levelName, cLevel + 1) ?: break
                    required = nextReq
                }
                val event = QuetiapineLevelUpgradeEvent(levelName, sourceLevel, cLevel, current, player)
                event.call()
                setExp(levelName, event.finalExp)
                setLevel(levelName, event.targetLevel)
            }
        } finally {
            upgrading.remove(lockKey)
        }
    }
    fun getLevelDisplay(levelName: String, display: String): String? {
        val level = levels[levelName] ?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        return level.displayNames[display] ?: level.displayNames["default"]
    }
    fun getLevelRaw(levelName: String): String? {
        val level = levels[levelName] ?: run {
            warningS("There are No Level levelNamed $levelName")
            return null
        }
        return level.displayNames["raw"] ?: level.displayNames["default"]
    }
    fun getLevelOrStart(levelName: String): Long? {
        val event = QuetiapineGetLevelEvent(levelName, getLevelOrStartWithoutEvent(levelName) ?: return null, player)
        event.call()
        return event.level
    }
    fun getExp(levelName: String): Double {
        return map["exp.$levelName"]?.toDouble() ?: 0.0
    }
    fun addExp(levelName: String, exp: Double, ignoreSafeZone: Boolean = false): Double? {
        // 基地/world 安全区内禁止加经验:防止泡岩浆等自伤源无限刷耐力/健康经验
        // 管理员命令等显式操作可传 ignoreSafeZone=true 绕过该拦截
        if (exp > 0 && !ignoreSafeZone && BaseManager.isInLevelSafeZone(player)) {
            devLog("addExp skipped for ${player.name}: in base or world")
            return null
        }
        val event = QuetiapineAddExpEvent(levelName, exp, player)
        event.call()
        if (!event.isCancelled) {
            val final = (map["exp.$levelName"]?.toDoubleOrNull() ?: 0.0) + event.exp
            map["exp.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun removeExp(levelName: String, exp: Double): Double? {
        val event = QuetiapineRemoveExpEvent(levelName, exp, player)
        event.call()
        if (!event.isCancelled) {
            val final = max((map["exp.$levelName"]?.toDoubleOrNull() ?: 0.0) - event.exp, 0.0)
            map["exp.$levelName"] = final.toString()
            return final
        }
        return null
    }
    fun setExp(levelName: String, exp: Double): Double? {
        val event = QuetiapineSetExpEvent(levelName, exp, player)
        event.call()
        if (!event.isCancelled) {
            val final = event.exp
            map["exp.$levelName"] = final.toString()
            return final
        }
        return null
    }

    /**
     * 获取玩家在指定等级系统中的总经验值（累计经验）
     * 从1级到当前等级所需的总经验值 + 当前等级的经验值
     */
    fun getTotalExp(levelName: String): Double? {
        val currentLevel = getLevel(levelName) ?: return null
        val currentExp = getExp(levelName)

        var totalExp = 0.0
        for (lvl in 1 until currentLevel) {
            val required = LevelDataManager.getRequiredExp(levelName, player, lvl) ?: return null
            totalExp += required
        }
        totalExp += currentExp

        return totalExp
    }

    /**
     * 根据总经验值设置玩家的等级和经验
     */
    fun setTotalExp(levelName: String, totalExp: Double): Boolean {
        val template = levels[levelName] ?: return false

        var remainingExp = totalExp
        var currentLevel = 1L
        val maxLevel = template.level.getMaxLevel(player) ?: Long.MAX_VALUE

        while (currentLevel <= maxLevel) {
            val required = LevelDataManager.getRequiredExp(levelName, player, currentLevel) ?: break
            if (remainingExp >= required) {
                remainingExp -= required
                currentLevel++
            } else {
                break
            }
        }

        val finalLevel = limit(template.level.min, currentLevel - 1, maxLevel)
        val finalExp = remainingExp

        map["level.$levelName"] = finalLevel.toString()
        map["exp.$levelName"] = finalExp.toString()

        return true
    }
}

object LevelDataManager {

    fun getMinLevel(levelName: String): Long? {
        return levels[levelName]?.level?.min ?: run {
            warningS("There are No Level levelNamed $levelName")
            null
        }
    }
    fun getMaxLevel(levelName: String, player: Player): Long? {
        return levels[levelName]?.level?.getMaxLevel(player) ?: run {
            warningS("There are No Level levelNamed $levelName")
            null
        }
    }
    fun getRequiredExp(levelName: String, player: Player, level: Long): Double? {
        return levels[levelName]?.level?.getRequiredExp(player, level) ?: run {
            warningS("There are No Level levelNamed $levelName")
            null
        }
    }
}

fun limit(min: Long, value: Long, max: Long): Long {
    return if (min > value) min
    else if (max < value) max
    else value
}
