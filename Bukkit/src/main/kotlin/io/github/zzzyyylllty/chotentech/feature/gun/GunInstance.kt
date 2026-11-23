package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.getItemTag
import java.util.UUID

/**
 * 枪械物品的一发「运行时状态」，全部落在 NBT 的
 * `sertraline_data.gun.*` 下（P0 已定死的数据契约）。
 *
 * 字段说明：
 * - [durability] 耐久（0 即损坏）
 * - [heat] 热量（0 ~ capacity）
 * - [jam] 故障标记（0=正常，非 0=卡壳，需要拉栓）
 * - [chamber] 膛内待发弹数（0/1）
 * - [mag] 当前弹匣物品的 Sertraline ID（空串=无弹匣）
 * - [magAmmo] 弹匣内子弹 ID 列表，**末位**为下一发（后进先出）
 * - [fireMode] 射击模式索引（SEMI/BURST/AUTO）
 * - [worn] 磨损度（0 ~ 1，P1 只读写不使用）
 *
 * 这是不可变数据类（[magAmmo] 本身可变），[GunInstance.read] 每次返回新实例，
 * 修改后必须走 [GunInstance.write] 才会落到物品 NBT 上。
 */
data class GunState(
    val durability: Double,
    val heat: Double,
    val jam: Int,
    val chamber: Int,
    val mag: String,
    val magAmmo: MutableList<String>,
    val fireMode: Int,
    val worn: Double,
    /** 膛内实际弹种；有膛内弹时必填，不允许从弹匣推断。 */
    val chamberAmmoId: String? = null,
    /** 每把枪独立实例 ID，用于区分同槽、同配置的不同物品。 */
    val instanceId: String = "",
    /**
     * 入水标记：为 true 时这把枪的下一次开火必定卡壳（jam=1），开火后自动清除。
     *
     * 标记跟着**物品**走而不是玩家：完全浸没时主手与副手的枪都会被打上标记，
     * 之后离开水面、换手、交易、放进箱子再拿出来都还在，直到这把枪真的开过一枪。
     */
    val wetJam: Boolean = false,
)

/**
 * 枪械物品 NBT 读写封装。
 *
 * ### NBT 布局（与 P0 一致）
 * ```
 * sertraline_data:
 *   gun:
 *     v: 1                 # 数据版本（P0 写入，本类只保留不改）
 *     durability: 1000.0
 *     heat: 0.0
 *     jam: 0
 *     chamber: 0
 *     mag: "ofn_pistol_mag"
 *     magAmmo: ["5x14_fmj", ...]   # StringList，末位为下一发
 *     fireMode: 0
 *     addons: {...}        # P0 写入，P1 不动
 *     worn: 0.0
 * ```
 *
 * ### 读写约定
 * - [read] 的语义：**没有 `sertraline_data.gun` 段就返回 null**（说明这根本不是枪械实例）；
 *   段存在但个别字段缺失时，缺失字段按「零值」补齐（`0.0 / 0 / "" / 空列表`），
 *   因此**调用方应先调用 [ensureDefaults] 把缺省字段补齐**（[readEnsured] 把这两步合成一次）。
 * - [write] 一次性写回并 `saveTo(item)`，**`item` 实例被就地修改**；
 *   若调用方拿的是背包镜像（`player.inventory.itemInMainHand`），需要再
 *   `player.inventory.setItemInMainHand(item)` 写回槽位。
 * - 所有方法都不抛异常，失败只打一条 devLog（枪械系统不能因为一个坏物品崩服）。
 */
object GunInstance {

    /** NBT 根路径。 */
    private const val ROOT = "sertraline_data.gun"

    /** 当前数据版本。 */
    const val DATA_VERSION = 1

    // ==================== 缺省值 ====================

    /**
     * 补齐缺省字段（缺什么补什么，已存在的字段一律保留）。
     *
     * `durability` 缺省取枪械配置里的 `attributes.durability`，其余按零值补齐。
     * 不会覆盖玩家已有进度（耐久/热量/弹药）。
     */
    fun ensureDefaults(item: ItemStack, gun: GunData) {
        ensureDefaults(item, gun, gun.attributes)
    }

    /** P4 重载：新枪械实例的耐久上限可包含配件加成。 */
    fun ensureDefaults(item: ItemStack, gun: GunData, effective: GunAttributes) {
        try {
            val tag = item.getItemTag()
            val compound = tag.getDeep(ROOT) as? ItemTag

            fun missing(key: String): Boolean = compound == null || compound[key] == null

            var changed = false
            if (missing("v")) {
                tag.putDeep("$ROOT.v", DATA_VERSION)
                changed = true
            }
            if (missing("durability")) {
                tag.putDeep("$ROOT.durability", effective.durability)
                changed = true
            }
            if (missing("heat")) {
                tag.putDeep("$ROOT.heat", 0.0)
                changed = true
            }
            if (missing("jam")) {
                tag.putDeep("$ROOT.jam", 0)
                changed = true
            }
            if (missing("wetJam")) {
                tag.putDeep("$ROOT.wetJam", 0)
                changed = true
            }
            if (missing("chamber")) {
                tag.putDeep("$ROOT.chamber", 0)
                changed = true
            }
            if (missing("mag")) {
                tag.putDeep("$ROOT.mag", "")
                changed = true
            }
            if (missing("magAmmo")) {
                tag.putDeep("$ROOT.magAmmo", emptyList<String>())
                changed = true
            }
            if (missing("fireMode")) {
                tag.putDeep("$ROOT.fireMode", 0)
                changed = true
            }
            if (missing("worn")) {
                tag.putDeep("$ROOT.worn", 0.0)
                changed = true
            }
            if (missing("instanceId")) {
                tag.putDeep("$ROOT.instanceId", UUID.randomUUID().toString())
                changed = true
            }
            // 兼容旧数据：旧版本可能只保存 chamber=1，没有 chamberAmmoId。
            // 如果弹匣列表里有弹，迁移末位弹药到膛内并从弹匣移除；只有确实没有
            // 可迁移弹药时才清理损坏状态，避免无条件吞掉一发有效弹药。
            val chamberAmmo = compound?.get("chamberAmmoId")?.asString()?.trim().orEmpty()
            if (compound != null && compound.int("chamber", 0) > 0 && chamberAmmo.isEmpty()) {
                val legacyMagAmmo = compound.strList("magAmmo").toMutableList()
                val migrated = legacyMagAmmo.removeLastOrNull()
                if (!migrated.isNullOrBlank()) {
                    tag.putDeep("$ROOT.chamber", 1)
                    tag.putDeep("$ROOT.chamberAmmoId", migrated)
                    tag.putDeep("$ROOT.magAmmo", legacyMagAmmo)
                } else {
                    tag.putDeep("$ROOT.chamber", 0)
                    tag.putDeep("$ROOT.chamberAmmoId", "")
                }
                changed = true
            }

            if (changed) {
                tag.saveTo(item)
            }
        } catch (e: Exception) {
            devLog("[Gun] 补齐枪械 NBT 失败: ${e.message}")
        }
    }

    // ==================== 读 ====================

    /**
     * 读取枪械状态；**物品上没有 `sertraline_data.gun` 段时返回 null**。
     * 段存在但缺字段时返回「零值补齐」的结果（见类文档）。
     */
    fun read(item: ItemStack): GunState? {
        val compound = try {
            item.getItemTag().getDeep(ROOT) as? ItemTag
        } catch (_: Exception) {
            null
        } ?: return null

        val chamber = compound.int("chamber", 0).coerceIn(0, 1)
        return GunState(
            durability = compound.dbl("durability", 0.0),
            heat = compound.dbl("heat", 0.0),
            jam = compound.int("jam", 0),
            chamber = chamber,
            mag = compound.str("mag", ""),
            magAmmo = compound.strList("magAmmo").toMutableList(),
            fireMode = compound.int("fireMode", 0),
            worn = compound.dbl("worn", 0.0),
            chamberAmmoId = if (chamber > 0) {
                compound.str("chamberAmmoId", "").trim().takeIf { it.isNotEmpty() }
            } else null,
            instanceId = compound.str("instanceId", ""),
            wetJam = compound.int("wetJam", 0) == 1,
        )
    }

    /**
     * [ensureDefaults] + [read] 的一次封装（只读一次 tag，射击 / 冷却主路径用这个）。
     *
     * 返回 null 只有一种情况：物品连 `sertraline_data.gun` 段都补不进去（不是枪械）。
     */
    fun readEnsured(item: ItemStack, gun: GunData): GunState? {
        ensureDefaults(item, gun)
        return read(item)
    }

    /** P4：以有效属性初始化，保留旧签名给无配件上下文调用。 */
    fun readEnsured(item: ItemStack, gun: GunData, effective: GunAttributes): GunState? {
        ensureDefaults(item, gun, effective)
        return read(item)
    }

    // ==================== 写 ====================

    /**
     * 一次性写回全部字段并回读校验。调用方必须检查返回值后再交付子弹或扣除物品。
     */
    fun write(item: ItemStack, state: GunState): Boolean {
        // 膛内状态必须自洽：`chamber` 由 `chamberAmmoId` 单向决定。
        // 旧实现只清「chamber=0 而 id 残留」这一侧，反向（chamber=1 而 id 为空）
        // 会落成「幽灵膛内弹」—— GunShoot 会一直按「膛内弹种缺失」拒绝开火，
        // 而 performReload 又会原样保留这个空 id，玩家只有拉一次栓才能脱困。
        val chamberAmmoId = state.chamberAmmoId?.trim()?.takeIf { it.isNotEmpty() }
        val normalized = state.copy(
            chamber = if (chamberAmmoId != null) 1 else 0,
            chamberAmmoId = chamberAmmoId,
            instanceId = state.instanceId.ifEmpty { UUID.randomUUID().toString() },
        )
        return try {
            val tag = item.getItemTag()
            tag.putDeep("$ROOT.durability", normalized.durability)
            tag.putDeep("$ROOT.heat", normalized.heat)
            tag.putDeep("$ROOT.jam", normalized.jam)
            tag.putDeep("$ROOT.chamber", normalized.chamber)
            tag.putDeep("$ROOT.chamberAmmoId", normalized.chamberAmmoId ?: "")
            tag.putDeep("$ROOT.mag", normalized.mag)
            tag.putDeep("$ROOT.magAmmo", normalized.magAmmo.toList())
            tag.putDeep("$ROOT.fireMode", normalized.fireMode)
            tag.putDeep("$ROOT.worn", normalized.worn)
            tag.putDeep("$ROOT.instanceId", normalized.instanceId)
            tag.putDeep("$ROOT.wetJam", if (normalized.wetJam) 1 else 0)
            tag.saveTo(item)
            val verified = read(item) == normalized
            if (!verified) devLog("[Gun] 枪械 NBT 回读校验失败")
            verified
        } catch (e: Exception) {
            devLog("[Gun] 写回枪械 NBT 失败: ${e.message}")
            false
        }
    }

    /**
     * 从弹匣里「上一发」取弹并写回，返回子弹 ID；空弹匣返回 null。
     *
     * 取的是 `magAmmo` 的**末位**（后进先出），与 HUD 显示的「弹匣数」一致。
     */
    fun consumeMagRound(item: ItemStack): String? {
        val state = read(item) ?: return null
        if (state.magAmmo.isEmpty()) return null
        val ammoId = state.magAmmo.removeAt(state.magAmmo.lastIndex)
        return if (write(item, state)) ammoId else null
    }

    /**
     * 只查看下一发（不消耗），用于膛内有弹时判断弹种。
     */
    fun peekMagRound(item: ItemStack): String? = read(item)?.magAmmo?.lastOrNull()

    /**
     * 批量写入弹匣内容（调试命令 `/ctt gun fill`，后续换弹系统复用）。
     *
     * @return 实际写入的发数；物品不是枪械时返回 -1
     */
    fun setMagAmmo(item: ItemStack, ammoId: String, count: Int): Int {
        val state = read(item) ?: return -1
        val amount = count.coerceIn(0, 256)
        return if (write(item, state.copy(magAmmo = MutableList(amount) { ammoId }))) amount else -1
    }

    /** 直接替换弹匣内容（P2 换弹系统预留）。 */
    fun writeMagAmmo(item: ItemStack, ammoIds: List<String>): Boolean {
        val state = read(item) ?: return false
        return write(item, state.copy(magAmmo = ammoIds.toMutableList()))
    }

    // ==================== ItemTag 读取小工具 ====================

    private fun ItemTag.dbl(key: String, def: Double): Double =
        this[key]?.asString()?.trim()?.toDoubleOrNull() ?: def

    private fun ItemTag.int(key: String, def: Int): Int =
        this[key]?.asString()?.trim()?.toDoubleOrNull()?.toInt() ?: def

    private fun ItemTag.str(key: String, def: String): String =
        this[key]?.asString() ?: def

    /**
     * `magAmmo` 读取：正常是 StringList（taboolib 的 `ItemTagList`），
     * 同时兼容早期「逗号拼接字符串」的写法。
     */
    private fun ItemTag.strList(key: String): List<String> {
        val data = this[key] ?: return emptyList()
        if (data is ItemTagList) {
            return data.mapNotNull { it.asString().trim().takeIf { text -> text.isNotEmpty() } }
        }
        val raw = data.asString().trim()
        if (raw.isEmpty()) return emptyList()
        if (raw == "[]") return emptyList()
        // 旧版把列表拼成「a,b,c」。只在看起来确实是旧格式时才按逗号拆：
        // Sertraline 的物品 ID 本身可以含逗号，否则会把一发子弹切成两发无效 ID。
        if (raw.startsWith("[") || raw.startsWith("{")) return emptyList()
        return raw.split(",").mapNotNull { it.trim().takeIf { text -> text.isNotEmpty() } }
    }
}
