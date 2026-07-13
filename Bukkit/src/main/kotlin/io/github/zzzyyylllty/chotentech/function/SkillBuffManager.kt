package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 副等级增益管理器
 *
 * 根据副等级自动为玩家应用属性修饰符。
 * 使用签名对比避免不必要的数据库写入，仅在等级变化时触发更新。
 */
object SkillBuffManager {

    // 修饰符固定 UUID
    private val ENDURANCE_BUFF_UUID     = UUID.fromString("a0000001-0001-0001-0001-000000000001")
    private val HEALTH_BUFF_UUID        = UUID.fromString("a0000002-0002-0002-0002-000000000002")
    private val STRENGTH_BUFF_UUID      = UUID.fromString("a0000003-0003-0003-0003-000000000003")
    private val AGILITY_BASE_UUID       = UUID.fromString("a0000005-0005-0005-0005-000000000005")
    private val AGILITY_BUFF_UUID       = UUID.fromString("a0000006-0006-0006-0006-000000000006")
    private val MINING_BUFF_UUID        = UUID.fromString("a0000007-0007-0007-0007-000000000007")

    // 所有增益 UUID 集合，用于批量清除
    private val buffUuids = setOf(
        ENDURANCE_BUFF_UUID, HEALTH_BUFF_UUID, STRENGTH_BUFF_UUID,
        AGILITY_BASE_UUID, AGILITY_BUFF_UUID, MINING_BUFF_UUID
    )

    // 技能列表（用于构建签名，按顺序）- 智力通过 HideoutManager 减少合成耗时，不在此处处理
    private val trackedSkills = listOf("endurance", "health", "strength", "agility", "mining")

    // 玩家等级签名缓存：UUID -> 签名字符串
    private val levelSignatures = ConcurrentHashMap<UUID, String>()

    private var task: PlatformExecutor.PlatformTask? = null

    @Awake(LifeCycle.ACTIVE)
    fun startBuffTask() {
        task = submitAsync(period = 20) {
            applyBuffs()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopBuffTask() {
        task?.cancel()
        task = null
        levelSignatures.clear()
        // 移除所有在线玩家的增益修饰符
        for (player in Bukkit.getOnlinePlayers()) {
            removeBuffModifiers(player.uniqueId)
        }
    }

    /**
     * 从玩家数据中移除所有技能增益修饰符
     */
    private fun removeBuffModifiers(uuid: UUID) {
        val data = AttributeManager.getPlayerPersistentData(uuid)
        val mods = data.modifiers.toMutableList()
        val removed = mods.removeAll { it.uuid in buffUuids }
        if (removed) {
            data.modifiers = mods
            AttributeManager.savePlayerData(data)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) AttributeManager.updatePlayer(player, async = true)
        }
    }

    /**
     * 获取玩家等级签名，等级变化时签名不同
     */
    private fun getLevelSignature(player: Player): String? {
        return try {
            val pdm = PlayerDataManager(player)
            buildString {
                for (skill in trackedSkills) {
                    append(pdm.getLevel(skill) ?: 0L).append(',')
                }
            }
        } catch (_: Exception) { null }
    }

    private fun applyBuffs() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

            val uuid = player.uniqueId
            val sig = getLevelSignature(player) ?: continue

            // 签名未变 → 等级未变 → 跳过
            if (sig == levelSignatures[uuid]) continue
            levelSignatures[uuid] = sig

            // 解析等级（智力通过 HideoutManager 减少合成耗时）
            val parts = sig.split(',')
            val enduranceLevel    = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val healthLevel       = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val strengthLevel     = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val agilityLevel      = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            val miningLevel       = parts.getOrNull(4)?.toLongOrNull() ?: 0L

            // 读取玩家属性数据
            val data = AttributeManager.getPlayerPersistentData(uuid)
            val modifiers = data.modifiers.toMutableList()

            // 移除旧的增益
            modifiers.removeAll { it.uuid in buffUuids }

            // ====== 构建增益列表 ======

            // 耐力 → stamina_regen_multiplier  PERCENT  +0.5%/级
            if (enduranceLevel > 0) {
                modifiers.add(AttributeModifier(
                    uuid = ENDURANCE_BUFF_UUID,
                    attributeId = "stamina_regen_multiplier",
                    value = enduranceLevel * 0.005,
                    type = ModifierType.PERCENT,
                    source = ModifierSource.SKILL
                ))
            }

            // 健康 → max_health  ADD  +0.5/级
            if (healthLevel > 0) {
                modifiers.add(AttributeModifier(
                    uuid = HEALTH_BUFF_UUID,
                    attributeId = "max_health",
                    value = healthLevel * 0.5,
                    type = ModifierType.ADD,
                    source = ModifierSource.SKILL
                ))
            }

            // 力量 → attack_damage  PERCENT  +0.5%/级
            if (strengthLevel > 0) {
                modifiers.add(AttributeModifier(
                    uuid = STRENGTH_BUFF_UUID,
                    attributeId = "attack_damage",
                    value = strengthLevel * 0.005,
                    type = ModifierType.PERCENT,
                    source = ModifierSource.SKILL
                ))
            }

            // 敏捷 → movement_speed  PERCENT
            // 全局基础：-10%（不论等级）
            modifiers.add(AttributeModifier(
                uuid = AGILITY_BASE_UUID,
                attributeId = "movement_speed",
                value = -0.1,
                type = ModifierType.PERCENT,
                source = ModifierSource.SKILL
            ))
            // 敏捷增益：+0.2%/级（最高 +20%）
            if (agilityLevel > 0) {
                modifiers.add(AttributeModifier(
                    uuid = AGILITY_BUFF_UUID,
                    attributeId = "movement_speed",
                    value = agilityLevel * 0.002,
                    type = ModifierType.PERCENT,
                    source = ModifierSource.SKILL
                ))
            }

            // 采矿 → mining_efficiency  PERCENT  +1%/级
            if (miningLevel > 0) {
                modifiers.add(AttributeModifier(
                    uuid = MINING_BUFF_UUID,
                    attributeId = "mining_efficiency",
                    value = miningLevel * 0.01,
                    type = ModifierType.PERCENT,
                    source = ModifierSource.SKILL
                ))
            }

            // 理疗 / 开锁：无增益

            data.modifiers = modifiers
            AttributeManager.savePlayerData(data)
            AttributeManager.updatePlayer(player, async = true)
        }
    }
}
