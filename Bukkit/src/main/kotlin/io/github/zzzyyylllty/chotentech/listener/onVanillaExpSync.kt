package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.overrideVanilla
import io.github.zzzyyylllty.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.quetiapine.event.*
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID

/**
 * 当 override-vanilla 启用时，将自定义等级系统的进度同步到原版经验条显示。
 *
 * 设计原则：
 * - 单向同步：自定义 → 原版（仅用于显示）
 * - 原版经验事件不再回写到自定义（避免覆盖和数据不一致）
 */
object VanillaExpSync {

    private val overrideLevel: String?
        get() = overrideVanilla

    /** 正在同步到原版的玩家集合，防止 setLevel/setExp 触发事件导致重入 */
    private val syncingToVanilla = mutableSetOf<UUID>()

    private fun isVanillaOverrideEnabled(): Boolean {
        return overrideLevel != null && overrideLevel != "null"
    }

    private fun getOverrideLevelName(): String {
        return overrideLevel ?: "main"
    }

    /**
     * 将自定义等级进度同步到原版经验条。
     * 必须在主线程执行（Bukkit API 要求）。
     */
    private fun syncCustomToVanilla(player: Player, levelName: String) {
        if (!syncingToVanilla.add(player.uniqueId)) return // 已在同步中，跳过
        try {
            val manager = PlayerDataManager(player)

            val customLevel = manager.getLevel(levelName) ?: return
            player.level = customLevel.toInt().coerceAtLeast(0)

            val currentExp = manager.getExp(levelName)
            val requiredExp = manager.getNextLevelExp(levelName) ?: 1.0

            player.exp = if (requiredExp > 0) {
                (currentExp / requiredExp).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
        } finally {
            syncingToVanilla.remove(player.uniqueId)
        }
    }

    // ========== 加入/切换世界时同步到原版经验条 ==========

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isVanillaOverrideEnabled()) return
        submit { syncCustomToVanilla(event.player, getOverrideLevelName()) }
    }

    @SubscribeEvent
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        if (!isVanillaOverrideEnabled()) return
        submit { syncCustomToVanilla(event.player, getOverrideLevelName()) }
    }

    // ========== 自定义等级事件 → 同步到原版经验条 ==========

    @SubscribeEvent
    fun onQuetiapineExpAdd(event: QuetiapineAddExpEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineRemoveExp(event: QuetiapineRemoveExpEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineSetExp(event: QuetiapineSetExpEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineLevelUpgrade(event: QuetiapineLevelUpgradeEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineSetLevel(event: QuetiapineSetLevelEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineAddLevel(event: QuetiapineAddLevelEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }

    @SubscribeEvent
    fun onQuetiapineRemoveLevel(event: QuetiapineRemoveLevelEvent) {
        if (!isVanillaOverrideEnabled()) return
        if (event.levelName != getOverrideLevelName()) return
        if (event.isCancelled) return
        submit { syncCustomToVanilla(event.player, event.levelName) }
    }
}
