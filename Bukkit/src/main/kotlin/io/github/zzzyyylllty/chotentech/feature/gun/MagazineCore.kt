package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.getItemTag
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 弹匣公共层（P2）：把「弹匣物品」的 NBT 读写、容量换算、七档文字、检视文案
 * 全部收在这一个地方，换弹 / 压弹 / 退弹 / 菜单预览都只调这里，避免出现
 * 「枪械菜单里的 6/8 和弹匣菜单里的 7/8 打架」这种两份实现各自算错的问题。
 *
 * ### NBT 契约（与 P0 一致）
 * ```
 * sertraline_data:
 *   mag:
 *     v: 1                     # 数据版本
 *     ammo: ["5x14_fmj", ...]  # StringList，末位为「下一发」（后进先出）
 *     droppedAt: 123456        # 旧兼容字段：被快速换弹丢出的世界 tick
 *     droppedAtMs: 123456789   # 跨世界拾取冷却使用的 Unix 毫秒时间戳
 * ```
 *
 * ### 合并写入
 * [writeAmmo] / [markDropped] **只** `putDeep` 自己那一个子键，绝不整段覆盖
 * `sertraline_data`——同一物品上还有 `sertraline_data.model`、`sertraline_data.gun`
 * 等其它模块的键，整段覆盖会把它们抹掉。
 *
 * ### 容错
 * 所有方法都不抛异常：物品上没有弹匣段、NBT 类型不对、物品是空气……
 * 一律降级成「空弹匣 / 0 / 0L」并打一条 devLog，弹匣系统不能因为一个坏物品崩服。
 */
object MagazineCore {

    /** 弹匣 NBT 根路径。 */
    private const val ROOT = "sertraline_data.mag"

    private const val KEY_AMMO = "$ROOT.ammo"

    private const val KEY_DROPPED_AT = "$ROOT.droppedAt"

    private const val KEY_DROPPED_AT_MS = "$ROOT.droppedAtMs"

    /** 当前数据版本（与枪械的 `sertraline_data.gun.v` 对齐，均为 1）。 */
    const val DATA_VERSION = 1

    /** Stable identifier for one physical magazine, used to bind timed operations. */
    fun instanceId(magItem: ItemStack): String = try {
        val tag = magItem.getItemTag()
        val id = ensureInstanceId(tag)
        tag.saveTo(magItem)
        id
    } catch (_: Exception) {
        "${magItem.type}:${magItem.amount}:${readAmmo(magItem).hashCode()}"
    }

    private fun ensureInstanceId(tag: ItemTag): String {
        val compound = tag.getDeep(ROOT) as? ItemTag
        val existing = compound?.get("instanceId")?.asString()?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val created = java.util.UUID.randomUUID().toString()
        ensureVersion(tag)
        tag.putDeep("$ROOT.instanceId", created)
        return created
    }

    // ==================== 读写 ====================

    /**
     * 读取弹匣内子弹 ID 列表（末位为下一发）。
     *
     * 缺省返回**空可变列表**（调用方可以直接 `add`），同时兼容早期
     * 「逗号拼接字符串」的写法（`"a,b,c"`）。
     */
    fun readAmmo(magItem: ItemStack): MutableList<String> {
        return try {
            val compound = magItem.getItemTag().getDeep(ROOT) as? ItemTag
                ?: return mutableListOf()
            val data = compound["ammo"] ?: return mutableListOf()
            when (data) {
                is ItemTagList -> data
                    .mapNotNull { it.asString().trim().takeIf { text -> text.isNotEmpty() } }
                    .toMutableList()

                else -> parseLegacy(data.asString()).toMutableList()
            }
        } catch (e: Exception) {
            devLog("[Gun] 读取弹匣 ammo 失败: ${e.message}")
            mutableListOf()
        }
    }

    /**
     * 写入弹匣内子弹 ID 列表（**合并写入**，只动 `sertraline_data.mag.ammo`）。
     */
    fun writeAmmo(magItem: ItemStack, ammo: List<String>): Boolean {
        var hadAmmo = false
        var originalAmmo: Any? = null
        var saveAttempted = false
        try {
            val tag = magItem.getItemTag()
            val originalCompound = tag.getDeep(ROOT) as? ItemTag
            hadAmmo = originalCompound?.get("ammo") != null
            originalAmmo = originalCompound?.get("ammo")?.clone()
            val expected = ammo.toList()

            ensureVersion(tag)
            ensureInstanceId(tag)
            tag.putDeep(KEY_AMMO, expected)
            saveAttempted = true
            tag.saveTo(magItem)
            if (readAmmo(magItem) == expected) return true

            if (!restoreAmmoValue(magItem, hadAmmo, originalAmmo)) {
                devLog("[Gun][ERROR] 弹匣 ammo 写入失败且回滚校验失败")
            }
            return false
        } catch (e: Exception) {
            if (saveAttempted) {
                if (!restoreAmmoValue(magItem, hadAmmo, originalAmmo)) {
                    devLog("[Gun][ERROR] 弹匣 ammo 写入异常且回滚失败: ${e.message}")
                } else {
                    devLog("[Gun] 写入弹匣 ammo 失败，已恢复原内容: ${e.message}")
                }
            } else {
                devLog("[Gun] 写入弹匣 ammo 失败: ${e.message}")
            }
            return false
        }
    }

    private fun restoreAmmoValue(item: ItemStack, hadAmmo: Boolean, originalAmmo: Any?): Boolean = try {
        val tag = item.getItemTag()
        if (hadAmmo && originalAmmo != null) {
            tag.putDeep(KEY_AMMO, originalAmmo)
        } else {
            val compound = tag.getDeep(ROOT) as? ItemTag
            compound?.remove("ammo")
            if (compound != null) tag.putDeep(ROOT, compound)
        }
        tag.saveTo(item)
        val restored = item.getItemTag().getDeep(ROOT) as? ItemTag
        if (hadAmmo) restored?.get("ammo") == originalAmmo else restored?.get("ammo") == null
    } catch (_: Exception) {
        false
    }

    /**
     * 压一发到弹匣**末位**（= 下一发）。
     *
     * @return false 表示弹匣已满（或容量为 0），此时不写 NBT
     */
    fun push(magItem: ItemStack, ammoId: String, capacity: Int): Boolean {
        val cap = if (capacity > 0) capacity else capacity(magItem)
        if (cap <= 0) return false
        if (ammoId.isBlank()) return false
        val ammo = readAmmo(magItem)
        if (ammo.size >= cap) return false
        ammo.add(ammoId)
        return writeAmmo(magItem, ammo)
    }

    /**
     * 从弹匣**末位**退一发并写回。
     *
     * @return 退出的子弹 ID；空弹匣返回 null
     */
    fun pop(magItem: ItemStack): String? {
        val ammo = readAmmo(magItem)
        if (ammo.isEmpty()) return null
        val ammoId = ammo.removeAt(ammo.lastIndex)
        return if (writeAmmo(magItem, ammo)) ammoId else null
    }

    /** 弹匣容量：以 [GunRegistry.magOf] 的静态配置为准，未登记时按 0 处理。 */
    fun capacity(magItem: ItemStack): Int {
        val configured = GunRegistry.magOf(magItem)?.capacity ?: return 0
        return configured.coerceAtLeast(0)
    }

    // ==================== 百分比 / 七档文字 ====================

    /** 装填百分比（0.0 ~ 1.0）；容量 <= 0 视为 0。 */
    fun percent(ammo: List<String>, capacity: Int): Double {
        if (capacity <= 0) return 0.0
        return (ammo.size.toDouble() / capacity).coerceIn(0.0, 1.0)
    }

    /**
     * 七档装填文字（MiniMessage，调用方负责反序列化）。
     *
     * | 区间 | 文案 |
     * |---|---|
     * | = 0 | 空 |
     * | ≤ 20% | 所剩无几 |
     * | ≤ 40% | 较少 |
     * | ≤ 60% | 一半 |
     * | ≤ 80% | 较多 |
     * | < 100% | 几乎满载 |
     * | = 100% | 满 |
     */
    fun fillText(percent: Double): String {
        val p = percent.coerceIn(0.0, 1.0)
        return when {
            p <= 0.0 -> "<#E08A98>空"
            p <= 0.2 -> "<#E08A98>所剩无几"
            p <= 0.4 -> "<#DFBD7C>较少"
            p <= 0.6 -> "<#DFBD7C>一半"
            p <= 0.8 -> "<#EFDF8F>较多"
            p < 1.0 -> "<#8FD4A6>几乎满载"
            else -> "<#84D1CB>满"
        }
    }

    /** 百分比 → 七档序号（0=空 … 6=满），[inspectTextOf] 的抖动基于它。 */
    fun tierIndex(percent: Double): Int {
        val p = percent.coerceIn(0.0, 1.0)
        return when {
            p <= 0.0 -> 0
            p <= 0.2 -> 1
            p <= 0.4 -> 2
            p <= 0.6 -> 3
            p <= 0.8 -> 4
            p < 1.0 -> 5
            else -> 6
        }
    }

    /** 七档序号 → 文案（与 [fillText] 同一套颜色）。 */
    fun tierText(index: Int): String = when (index.coerceIn(0, 6)) {
        0 -> "<#E08A98>空"
        1 -> "<#E08A98>所剩无几"
        2 -> "<#DFBD7C>较少"
        3 -> "<#DFBD7C>一半"
        4 -> "<#EFDF8F>较多"
        5 -> "<#8FD4A6>几乎满载"
        else -> "<#84D1CB>满"
    }

    // ==================== 检视 ====================

    /**
     * 检视一个**真实弹匣物品**，返回一行 MiniMessage 文案。
     *
     * 精度规则（设计文档 §3）：
     * - `mag.ammoInspectionAccuracy == 1` → 精确到发（`7/8`）；
     * - 否则给七档文字 + **±1 档随机抖动**，所以连续两次检视可能不一样。
     *
     * @param player 检视者（保留参数以对齐接口，P2 的文案不区分玩家）
     */
    @Suppress("UNUSED_PARAMETER")
    fun inspectText(player: Player, magItem: ItemStack): String =
        inspectTextOf(GunRegistry.magOf(magItem), readAmmo(magItem))

    /**
     * 检视「一份弹匣数据」（不要求真实物品）。
     *
     * 枪械菜单里的检视弹匣没有真实物品（弹匣内容存在枪的 `magAmmo` 里），
     * 走这个入口可以保证与弹匣菜单的文案完全一致。
     */
    @Suppress("UNUSED_PARAMETER")
    fun inspectTextOf(mag: MagData?, ammo: List<String>): String {
        if (mag == null) return "<#E08A98>无法识别的弹匣"
        val capacity = mag.capacity.coerceAtLeast(0)
        val exact = mag.ammoInspectionAccuracy >= 1
        val ratio = percent(ammo, capacity)

        val countText = if (exact) {
            "<#F2F5FA>${ammo.size}</#F2F5FA><#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity</#F2F5FA>"
        } else {
            val jitter = Random.nextInt(3) - 1
            tierText(tierIndex(ratio) + jitter)
        }
        val hint = if (exact) "" else " <#6E7A8C>(±1 档)</#6E7A8C>"

        val kinds = if (ammo.isEmpty()) {
            "<#6E7A8C>无</#6E7A8C>"
        } else {
            ammo.groupingBy { it }.eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(" <#6E7A8C>·</#6E7A8C> ") { "<#F2F5FA>${it.key}</#F2F5FA>×${it.value}" }
        }

        return "<#A9B4C3>弹匣 <#EFDF8F>${mag.id}</#EFDF8F> " +
            "<gray>|</gray> $countText$hint " +
            "<gray>|</gray> <#A9B4C3>弹种 $kinds"
    }

    // ==================== 丢弃冷却 ====================

    /**
     * 标记「这个弹匣是在当前 tick 被快速换弹丢出来的」，写入
     * `sertraline_data.mag.droppedAt`（合并写入）。
     */
    fun markDropped(item: ItemStack, tick: Long) {
        try {
            val tag = item.getItemTag()
            ensureVersion(tag)
            tag.putDeep(KEY_DROPPED_AT, tick.coerceAtLeast(0L))
            tag.putDeep(KEY_DROPPED_AT_MS, System.currentTimeMillis())
            tag.saveTo(item)
        } catch (e: Exception) {
            devLog("[Gun] 写入弹匣 droppedAt 失败: ${e.message}")
        }
    }

    /** 读取 `droppedAt`（没写过 / 读取失败一律 0，表示「不限制拾取」）。 */
    fun droppedAt(item: ItemStack): Long {
        return try {
            val compound = item.getItemTag().getDeep(ROOT) as? ItemTag ?: return 0L
            compound["droppedAt"]?.asLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /** 读取跨世界可用的毫秒时间戳；旧物品没有该字段时返回 0（已过期）。 */
    fun droppedAtMs(item: ItemStack): Long {
        return try {
            val compound = item.getItemTag().getDeep(ROOT) as? ItemTag ?: return 0L
            compound["droppedAtMs"]?.asLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 拾取冷却判定：`droppedAtMs + lockTicks * 50 > System.currentTimeMillis()`。
     * 保留旧签名与 `droppedAt` 读取兼容；只有旧世界 tick 字段的物品按已过期处理。
     */
    @Suppress("UNUSED_PARAMETER")
    fun isPickupLocked(item: ItemStack, nowTick: Long, lockTicks: Int): Boolean {
        if (lockTicks <= 0) return false
        val droppedAtMs = droppedAtMs(item)
        if (droppedAtMs <= 0L) return false
        return droppedAtMs + lockTicks * 50L > System.currentTimeMillis()
    }

    // ==================== 内部工具 ====================

    /** 补齐 `sertraline_data.mag.v`（幂等，已有则不动）。 */
    private fun ensureVersion(tag: ItemTag) {
        val compound = tag.getDeep(ROOT) as? ItemTag
        if (compound != null && compound["v"] != null) return
        tag.putDeep("$ROOT.v", DATA_VERSION)
    }

    /** 兼容「逗号拼接字符串」的旧写法。 */
    private fun parseLegacy(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty() || text == "[]") return emptyList()
        return text.split(",").mapNotNull { it.trim().takeIf { part -> part.isNotEmpty() } }
    }

    /** 装填百分比 → 0~100 的整数（GUI 显示用）。 */
    fun percentInt(ammo: List<String>, capacity: Int): Int =
        (percent(ammo, capacity) * 100.0).roundToInt()
}
