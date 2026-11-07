package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.FluidCollisionMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 枪械的**多状态模型**（参考 WeaponMechanics 的 weapon state 做法）。
 *
 * YAML 里 `model:` 段的每个键就是一个状态，`modelAdapter` 会把它们原样写进
 * `sertraline_data.model`；这里负责在运行时把「当前状态」对应的模型刷到物品上。
 *
 * ### 状态优先级（高 → 低）
 * ```
 * reload / bolt  （读条中：换弹、拉栓、排障、压弹）
 *   > jammed     （卡壳）
 *   > blocked    （前方被方块挡住 → 收枪）
 *   > shoot      （开火后短暂 2 tick 的枪口模型）
 *   > aiming     （瞄准）
 *   > general    （常态）
 * ```
 * 某个状态在 YAML 里没配时，按 [State.fallbacks] 逐级回退，最终回退到 `general`；
 * 全都没配就完全不碰模型（保持物品原样，不写坏数据）。
 *
 * 取值约定与 [io.github.zzzyyylllty.chotentech.feature.gun.item.modelAdapter] 一致：
 * - 纯数字 → `custom_model_data`
 * - `namespace:path` → `item_model` 组件（1.21.4+，旧版本自动跳过）
 */
object GunModel {

    enum class State(val key: String, val fallbacks: List<String>) {
        GENERAL("general", emptyList()),
        AIMING("aiming", listOf("general")),
        SHOOT("shoot", listOf("aiming", "general")),
        BLOCKED("blocked", listOf("general")),
        JAMMED("jammed", listOf("blocked", "general")),
        RELOAD("reload", listOf("general")),
        BOLT("bolt", listOf("reload", "general")),
        DRAW("draw", listOf("general")),
    }

    /** 开火后保持 `shoot` 模型的 tick 数。 */
    private const val SHOOT_LINGER_TICKS = 2L

    /** 枪口前方多长的距离被方块占住就判定为「收枪」。 */
    private const val BLOCK_DISTANCE = 1.2

    /** uuid → 已应用的状态（避免每 tick 重复写 NBT / 组件）。 */
    private val applied = ConcurrentHashMap<UUID, State>()

    /** 清除缓存（切枪 / 退出 / 关服）。 */
    fun forget(player: Player) {
        applied.remove(player.uniqueId)
    }

    fun clear() {
        applied.clear()
    }

    /**
     * 计算并应用当前应有的模型状态。
     *
     * 由 [GunStateManager] 的 1 tick 循环调用，只在状态变化时才真正写物品。
     */
    fun tick(player: Player, gunState: GunState?) {
        if (gunState == null) {
            applied.remove(player.uniqueId)
            return
        }
        val desired = desiredState(player, gunState)
        if (applied[player.uniqueId] == desired) return
        applied[player.uniqueId] = desired
        applyModel(player, desired)
    }

    /**
     * 按当前输入/环境算出目标状态（顺序即优先级）。
     */
    private fun desiredState(player: Player, gunState: GunState): State {
        val action = GunAction.snapshot(player)
        if (action != null) {
            return when (action.type) {
                GunAction.ActionType.BOLT -> State.BOLT
                GunAction.ActionType.RELOAD, GunAction.ActionType.RELOAD_FAST -> State.RELOAD
                GunAction.ActionType.FIX,
                GunAction.ActionType.INSERT_AMMO,
                GunAction.ActionType.REMOVE_AMMO,
                GunAction.ActionType.REMOVE_MAG,
                GunAction.ActionType.INSPECT_MAG,
                GunAction.ActionType.INSPECT_CHAMBER,
                GunAction.ActionType.FIRE_MODE -> State.RELOAD
            }
        }
        if (gunState.jam >= 1) return State.JAMMED
        if (isMuzzleBlocked(player)) return State.BLOCKED
        val runtime = GunStateManager.existing(player)
        // 用墙钟判断「开火后的 2 tick 残留」：世界 tick 在 /tick freeze 下不走，
        // 会让模型永远卡在 shoot 状态。
        if (runtime != null && runtime.lastShotMs > 0L &&
            System.currentTimeMillis() - runtime.lastShotMs <= SHOOT_LINGER_TICKS * 50L
        ) {
            return State.SHOOT
        }
        if (runtime?.ads == true) return State.AIMING
        return State.GENERAL
    }

    /**
     * 枪口前方是否被方块挡住（「前方有方块挡住时收枪」）。
     *
     * 用视线射线前 [BLOCK_DISTANCE] 格检测，命中实体方块即视为挡住。
     */
    fun isMuzzleBlocked(player: Player): Boolean {
        return try {
            val eye = player.eyeLocation
            val direction = eye.direction.clone()
            val hit = player.world.rayTraceBlocks(eye, direction, BLOCK_DISTANCE, FluidCollisionMode.NEVER, true)
            hit?.hitBlock?.type?.isSolid == true
        } catch (_: Exception) {
            false
        }
    }

    /** 把某个状态的模型写到主手物品上（按 fallback 链取值）。 */
    fun applyModel(player: Player, state: State) {
        try {
            val item = player.inventory.itemInMainHand
            if (item.type.isAir) return
            val models = readModels(item) ?: return
            val raw = pickModel(models, state) ?: return
            if (!writeModel(item, raw)) return
            player.inventory.setItemInMainHand(item)
        } catch (_: Exception) {
            // 模型切换失败不影响射击
        }
    }

    /** 读 `sertraline_data.model`（状态名 → 模型路径）。 */
    private fun readModels(item: ItemStack): Map<String, String>? {
        val tag = item.getItemTag()
        val modelTag = tag.getDeep("sertraline_data.model") as? ItemTag ?: return null
        val map = LinkedHashMap<String, String>()
        for (key in modelTag.keys) {
            val value = modelTag[key]?.asString()?.trim().orEmpty()
            if (value.isNotEmpty()) map[key] = value
        }
        return map.takeIf { it.isNotEmpty() }
    }

    private fun pickModel(models: Map<String, String>, state: State): String? {
        models[state.key]?.let { return it }
        for (fallback in state.fallbacks) {
            models[fallback]?.let { return it }
        }
        return null
    }

    /** 写模型：纯数字走 CustomModelData，其余走 item_model 组件。 */
    private fun writeModel(item: ItemStack, raw: String): Boolean {
        val meta = item.itemMeta ?: return false
        val numeric = raw.toIntOrNull()
        if (numeric != null) {
            meta.setCustomModelData(numeric)
        } else {
            val key = NamespacedKey.fromString(raw.lowercase()) ?: return false
            try {
                meta.setItemModel(key)
            } catch (_: Throwable) {
                // 旧版本没有 item_model 组件：保持原样
                return false
            }
        }
        item.itemMeta = meta
        return true
    }

    /** 调试：当前状态名。 */
    fun stateOf(player: Player): String? = applied[player.uniqueId]?.key
}
