package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** ADS 热成像：只记录并恢复由本管理器从“不发光”点亮的实体。 */
object ThermalManager {
    private data class Glow(val world: UUID, var restoreAt: Long)

    private val glowing = ConcurrentHashMap<UUID, MutableMap<UUID, Glow>>()

    fun tick(player: Player, state: PlayerGunState, held: org.bukkit.inventory.ItemStack, hasGun: Boolean) {
        if (!hasGun || !state.ads || !AddonManager.hasThermal(held) || !GunSettings.thermal.enabled) {
            clear(player)
            return
        }
        val now = player.world.fullTime
        // 发光恢复的截止时间用**墙钟**：世界 tick 在 `/tick freeze` 下不走，用它会永远不恢复。
        val nowMs = System.currentTimeMillis()
        val owned = glowing[player.uniqueId]
        if (owned != null) expire(player, owned, nowMs)
        val interval = GunSettings.thermal.intervalTicks.coerceAtLeast(1)
        if (now % interval != 0L) return

        val eye = player.eyeLocation
        val direction = eye.direction.clone().normalize()
        val hit = try {
            player.world.rayTraceBlocks(eye, direction, 32.0, FluidCollisionMode.NEVER, true)
        } catch (_: Exception) { null }
        val center = hit?.hitPosition?.toLocation(player.world)
            ?: eye.clone().add(direction.multiply(32.0))
        val radius = GunSettings.thermal.radius.coerceAtLeast(0.0)
        val entities = try {
            player.world.getNearbyEntities(center, radius, radius, radius)
        } catch (_: Exception) { emptyList() }
        val records = glowing.getOrPut(player.uniqueId) { ConcurrentHashMap() }
        for (entity in entities) {
            if (entity !is LivingEntity || entity == player || entity.isDead) continue
            val uuid = entity.uniqueId
            val restoreAt = nowMs + GunSettings.thermal.glowTicks.coerceAtLeast(1) * 50L
            try {
                if (entity.isGlowing) {
                    // 自己或其他热成像玩家点亮的可以续期；外部来源的发光绝不登记。
                    val ownedBySomeone = records.containsKey(uuid) ||
                        glowing.any { (owner, other) -> owner != player.uniqueId && other.containsKey(uuid) }
                    if (ownedBySomeone) records[uuid] = Glow(entity.world.uid, restoreAt)
                } else {
                    entity.isGlowing = true
                    // 校验本次点亮确实生效，避免给未点亮的实体留下之后会误关发光的记录。
                    if (entity.isGlowing) records[uuid] = Glow(entity.world.uid, restoreAt)
                    // 外部插件随后关闭再点亮的情况无法与本系统的发光区分，待实机验证。
                }
            } catch (_: Exception) {
            }
        }
    }

    /** 退出 ADS / 切枪 / 退出 / 死亡时立即恢复；失败记录保留到下次清理重试。 */
    fun clear(player: Player) {
        val records = glowing[player.uniqueId] ?: return
        clearRecords(player.uniqueId, records)
    }

    fun clearAll() {
        // 不在恢复前清空总表：世界未加载或实体找不到时必须留下记录。
        for ((owner, records) in glowing.entries.toList()) {
            clearRecords(owner, records)
        }
    }

    private fun clearRecords(owner: UUID, records: MutableMap<UUID, Glow>) {
        for ((uuid, glow) in records.entries.toList()) {
            val shared = glowing.any { (otherOwner, other) ->
                otherOwner != owner && other.containsKey(uuid)
            }
            // 共享实体只移除当前玩家的所有权，由最后一个拥有者实际恢复。
            if (shared || restore(uuid, glow)) {
                if (records[uuid] === glow) records.remove(uuid)
            }
        }
        if (records.isEmpty()) glowing.remove(owner, records)
    }

    private fun expire(player: Player, records: MutableMap<UUID, Glow>, now: Long) {
        val expired = records.filterValues { now >= it.restoreAt }.keys.toList()
        for (uuid in expired) {
            val glow = records[uuid] ?: continue
            // 另一个热成像玩家仍拥有它时，让那个玩家负责最终恢复。
            val shared = glowing.any { (owner, other) -> owner != player.uniqueId && other.containsKey(uuid) }
            if (shared || restore(uuid, glow)) {
                if (records[uuid] === glow) records.remove(uuid)
            }
        }
    }

    private fun restore(uuid: UUID, glow: Glow): Boolean {
        // 只允许仍持有这份准确记录的所有权链发起恢复，过期/已移除记录不能误关实体。
        if (glowing.values.none { it[uuid] === glow }) return true
        val world = Bukkit.getWorld(glow.world) ?: run {
            devLog("[Gun] 热成像恢复失败，保留记录：世界未加载 (entity=$uuid)")
            return false
        }
        val entity = world.entities.firstOrNull { it.uniqueId == uuid } as? LivingEntity ?: run {
            devLog("[Gun] 热成像恢复失败，保留记录：实体不存在 (entity=$uuid)")
            return false
        }
        return try {
            entity.isGlowing = false
            true
        } catch (e: Exception) {
            devLog("[Gun] 热成像恢复异常，保留记录 (entity=$uuid): ${e.message}")
            false
        }
    }
}
