package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 把**耐久**写进枪械物品的显示名称（例如 `OFN-12手枪 [873/1000]`）。
 *
 * ### 为什么要单独一层
 * 耐久每次开火都会变，但物品名称是物品自带的属性 —— 不重新 `setDisplayName` 就永远停在
 * 造出来那一刻的值。所以这里做成「渲染 + 变更检测」：
 * 只有渲染结果真的变了才写物品、并把这一格重新推给客户端（否则每 tick 写一次会闪）。
 *
 * ### 基础名称从哪来
 * 名称由 Sertraline 的 `itemdef.name` 决定，我们不重复实现一遍，而是在**第一次**渲染时
 * 把当时的显示名（MiniMessage 串）存进物品 PDC 的 [BASE_KEY]，之后所有渲染都是
 * `基础名 + 耐久后缀`。这样：
 * - 玩家改 YAML 名称后，`/srebuild` 重建物品 → PDC 丢失 → 下次渲染重新取名字，自动跟上；
 * - 我们自己附加的后缀不会被重复叠加（[stripSuffix] 还会兜一层，处理 PDC 丢失但名称已被改过的旧物品）。
 *
 * ### 何时刷新
 * - [GunStateManager] 的 tick 循环按 HUD 间隔调用 [refreshHeld]（兜底，覆盖所有耐久变化来源）；
 * - 开火扣耐久、菜单排障之后立即调用一次（让磨损/修复当场可见，不用等下一个 HUD 周期）。
 */
object GunNameplate {

    /**
     * PDC 键：Sertraline 原始显示名（MiniMessage 串）。
     *
     * 不需要真实的 Plugin 实例，直接给命名空间即可（避免依赖主类注册时序）。
     */
    private val BASE_KEY = NamespacedKey("chotentech", "gun_base_name")

    /**
     * 我们附加的后缀特征：形如 ` <#RRGGBB>[873/1000]</#RRGGBB>`（含可选的进度条 / 状态标记）。
     *
     * 只认「括号里带 `数字/数字`」的尾巴，避免把玩家名字里本来就有的 `[...]` 误删。
     */
    private val SUFFIX_PATTERN = Regex(
        """\s*(?:<#[0-9A-Fa-f]{6}>)?\[[^\[\]]*\d[^\[\]]*/\s*[^\[\]]*\d[^\[\]]*](?:</#[0-9A-Fa-f]{6}>)?\s*$""",
    )

    /** uuid → (instanceId → 上次渲染出的名称)，用来做变更检测。 */
    private val rendered = ConcurrentHashMap<UUID, MutableMap<String, String>>()

    /** 每个玩家最多记住最近几个枪械实例（防止长期在线时二级 map 无限增长）。 */
    private const val MAX_RENDER_KEYS = 4

    // ==================== 对外 ====================

    /**
     * 刷新玩家**主手**枪械的名称（不是枪械、或功能关闭时什么都不做）。
     *
     * @return true = 名称发生了变化并已写回物品
     */
    fun refreshHeld(player: Player, held: ItemStack, gun: GunData, state: GunState): Boolean {
        if (!GunSettings.nameplate.enabled) return false
        val max = try {
            AddonManager.effectiveAttributes(gun, held).durability
        } catch (_: Exception) {
            gun.attributes.durability
        }
        return render(player, held, state, max)
    }

    /** 已持有物品但不方便传 state 时的便捷入口（内部读一次 NBT）。 */
    fun refreshHeld(player: Player, held: ItemStack, gun: GunData): Boolean {
        val state = GunInstance.read(held) ?: return false
        return refreshHeld(player, held, gun, state)
    }

    /** 改完名称后清掉缓存（切枪 / 下线时调用，避免 UUID 表越积越大）。 */
    fun forget(player: Player) {
        rendered.remove(player.uniqueId)
    }

    /** 全局清空（重载 / 关服）。 */
    fun clear() {
        rendered.clear()
    }

    // ==================== 渲染 ====================

    private fun render(player: Player, item: ItemStack, state: GunState, maxDurability: Double): Boolean {
        val settings = GunSettings.nameplate
        val current = state.durability.coerceAtLeast(0.0)
        val max = maxDurability.coerceAtLeast(1e-6)
        val ratio = (current / max).coerceIn(0.0, 1.0)

        val meta = try {
            item.itemMeta ?: return false
        } catch (_: Exception) {
            return false
        }

        val base = baseName(item, meta) ?: return false
        // 「满耐久就不显示」= 一点都没掉。用 0.999 会让 999/1000 这种只掉 0.1%
        // 的情况也把后缀吞掉，玩家看起来像「这枪根本没有耐久系统」。
        val suffix = if (settings.hideWhenFull && ratio >= 1.0) "" else buildSuffix(settings, current, max, ratio, state)
        val target = base + suffix

        val key = state.instanceId.ifEmpty { "shared" }
        val previous = rendered.getOrPut(player.uniqueId) { ConcurrentHashMap() }
        // 只保留最近 4 个实例（换枪 / 换实例不会无限累积二级 map）
        if (previous.size > MAX_RENDER_KEYS && !previous.containsKey(key)) {
            previous.keys.firstOrNull { it != key }?.let(previous::remove)
        }
        if (previous[key] == target) return false

        return try {
            meta.displayName(mmUtil.deserialize(target.legacyToMiniMessage()))
            item.itemMeta = meta
            // 必须**无条件**写回主手：`itemInMainHand` 每次调用都会新建一个 CraftItemStack
            // 包装对象（mirror），所以拿 `===` 去比对永远为 false —— 那样服务端名称变了、
            // 客户端却收不到更新包，玩家要重新手持才看得到新耐久。
            // `refreshHeld` 的语义本来就是「刷新主手」，这里不需要再判断。
            if (!item.type.isAir) {
                player.inventory.setItemInMainHand(item)
            }
            previous[key] = target
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 去掉名称末尾由本类附加的耐久后缀（纯文本用；HUD 取名字时用）。
     *
     * 示例：`OFN-12手枪 [873/1000]` → `OFN-12手枪`。
     */
    fun stripDurabilitySuffix(name: String): String = stripSuffix(name)

    /**
     * 取基础名称：优先 PDC 里记着的原名，没有就抓当前**有效名称**并剥掉我们自己加的后缀。
     *
     * 用 `effectiveName()` 而不是 `displayName()`：物品没设自定义名时它直接给出原版译名
     * （`铁锄` / `Iron Hoe`），不会出现「抓不到名字 → 干脆不显示耐久」的情况。
     *
     * 抓到的结果只写进传入的 [meta]（由 [render] 统一 apply 一次），
     * 避免同一个 ItemStack 连续 apply 两次。
     */
    private fun baseName(item: ItemStack, meta: ItemMeta): String? {
        val pdc = meta.persistentDataContainer
        val stored = try {
            pdc.get(BASE_KEY, PersistentDataType.STRING)
        } catch (_: Exception) {
            null
        }
        if (!stored.isNullOrEmpty()) return stored

        val raw = effectiveName(item, meta) ?: return null
        val stripped = stripSuffix(raw)
        try {
            meta.persistentDataContainer.set(BASE_KEY, PersistentDataType.STRING, stripped)
        } catch (_: Exception) {
        }
        return stripped
    }

    /**
     * 有效名称的 MiniMessage 串。
     *
     * 依次尝试：`ItemStack#effectiveName()`（Paper，含原版译名回落）→ `ItemMeta#displayName()`
     * → 材质名。任何一步抛错都往下一级走，保证返回非空。
     */
    private fun effectiveName(item: ItemStack, meta: ItemMeta): String? {
        try {
            mmUtil.serialize(item.effectiveName()).takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Throwable) {
        }
        try {
            meta.displayName()?.let { mmUtil.serialize(it) }?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Throwable) {
        }
        return try {
            mmUtil.serialize(item.displayName()).takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            item.type.name
        }
    }

    /** 去掉名称末尾由本类附加的 `[...]` 后缀（兼容旧的纯文本后缀）。 */
    private fun stripSuffix(name: String): String =
        name.replace(SUFFIX_PATTERN, "").ifEmpty { name }

    private fun buildSuffix(
        settings: GunNameplateSettings,
        current: Double,
        max: Double,
        ratio: Double,
        state: GunState,
    ): String {
        val color = when {
            state.durability <= 0.0 -> settings.colorBad
            ratio >= 0.5 -> settings.colorGood
            ratio >= 0.2 -> settings.colorWarn
            else -> settings.colorBad
        }
        val flag = when {
            state.durability <= 0.0 -> " <#E08A98>损坏"
            state.jam >= 2 -> " <#E08A98>严重故障"
            state.jam == 1 -> " <#EFDF8F>卡壳"
            else -> ""
        }
        return settings.format
            .replace("{name}", "")
            .replace("{cur}", num(current))
            .replace("{max}", num(max))
            .replace("{percent}", String.format(Locale.US, "%d%%", (ratio * 100).toInt()))
            .replace("{bar}", bar(settings.barLength, ratio))
            .replace("{color}", "<$color>")
            .replace("{end}", "</$color>")
            .replace("{flag}", flag)
    }

    /** `{bar}`：`■■■■■□□□□□` 形式的进度条。 */
    private fun bar(length: Int, ratio: Double): String {
        if (length <= 0) return ""
        val filled = (ratio * length).toInt().coerceIn(0, length)
        return "■".repeat(filled) + "□".repeat(length - filled)
    }

    /** 耐久数字：整数不显示小数点（1000 而不是 1000.0）。 */
    private fun num(value: Double): String {
        // 非有限值（NaN / ∞）来自损坏的 NBT：直接显示 `?`，别渲染成 0 或 9223372036854775807
        if (!value.isFinite()) return "?"
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
