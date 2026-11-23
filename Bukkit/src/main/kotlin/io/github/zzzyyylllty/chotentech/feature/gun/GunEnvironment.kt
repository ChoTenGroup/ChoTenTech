package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import org.bukkit.Material
import org.bukkit.entity.Player
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 层级环境对枪械属性的修正（`gun-environment.yml`）。
 *
 * 设计取舍：
 * - 只修 [GunAttributes] 里「射击相关」的字段；`durability` 与 `barrelLength` 不参与，
 *   因为 `durability` 会被 [GunInstance.readEnsured] 写进物品 NBT，一旦随层级变化
 *   就会出现「同一把枪换个层级最大耐久变了」的实例数据前后不一致。
 * - 每次调用都直接读配置，不做缓存：`@Config` 注入的 [settings] 在插件重载后是同一
 *   对象的新内容，缓存需要额外的失效通知，得不偿失（读取量只有几项）。
 * - 本对象不做任何「是否在射击」的判断，纯函数式地给出修正值，由调用方决定用在哪。
 */
object GunEnvironment {

    @Config("gun-environment.yml")
    lateinit var settings: Configuration

    /** 允许被环境修正的字段名，与 `GunAttributes` 的属性名一一对应。 */
    private val FIELDS = listOf(
        "machineEfficiency", "accuracy", "bulletSpeed",
        "fireRate", "range", "recoilV", "recoilH",
    )

    /** 某个层级的修正值。[add] 是加法，[multiply] 是乘法因子（1.0 = 不变）。 */
    data class Adjustment(
        val add: Map<String, Double> = emptyMap(),
        val multiply: Map<String, Double> = emptyMap(),
        val waterJam: Boolean = true,
    )

    /** 玩家当前层级 + `default` 合并后的修正。 */
    fun adjustment(player: Player): Adjustment = adjustment(player.getPlayerLevel()?.id)

    /**
     * 按层级 id 取修正：先读 `default` 段，再用层级段覆盖同名项。
     *
     * @param levelId `levels` 目录下 yml 里的层级键（如 `level-4`），null 表示不在任何层级
     */
    fun adjustment(levelId: String?): Adjustment {
        if (!::settings.isInitialized) return Adjustment()
        val sections = buildList {
            if (settings.isConfigurationSection("default")) add("default")
            if (levelId != null && settings.isConfigurationSection(levelId)) add(levelId)
        }
        if (sections.isEmpty()) return Adjustment()

        val add = linkedMapOf<String, Double>()
        val multiply = linkedMapOf<String, Double>()
        var waterJam = true
        for (section in sections) {
            for (key in FIELDS) {
                settings.getDouble("$section.add.$key", Double.NaN).takeIf { !it.isNaN() }?.let { add[key] = it }
                // 乘法因子 <= 0 没有物理意义（会把属性归零或取负），直接忽略。
                settings.getDouble("$section.multiply.$key", Double.NaN).takeIf { it > 0.0 }
                    ?.let { multiply[key] = it }
            }
            if (settings.contains("$section.water-jam")) {
                waterJam = settings.getBoolean("$section.water-jam", waterJam)
            }
        }
        return Adjustment(add, multiply, waterJam)
    }

    /**
     * 把环境修正叠加到已经算好的「基础 + 配件」属性上。
     *
     * 乘法在前、加法在后；夹取范围与 [AddonManager.effectiveAttributes] 保持一致，
     * 避免环境配置把属性推出合法区间（例如人机功效超过 100）。
     */
    fun apply(player: Player, base: GunAttributes): GunAttributes {
        val adj = adjustment(player)
        if (adj.add.isEmpty() && adj.multiply.isEmpty()) return base
        fun value(key: String, current: Double): Double =
            current * (adj.multiply[key] ?: 1.0) + (adj.add[key] ?: 0.0)
        return base.copy(
            machineEfficiency = value("machineEfficiency", base.machineEfficiency).coerceIn(0.0, 100.0),
            accuracy = value("accuracy", base.accuracy).coerceIn(0.0, 100.0),
            bulletSpeed = value("bulletSpeed", base.bulletSpeed).coerceAtLeast(1.0),
            fireRate = value("fireRate", base.fireRate).coerceAtLeast(1.0),
            range = value("range", base.range).coerceAtLeast(1.0),
            recoilV = value("recoilV", base.recoilV).coerceAtLeast(0.0),
            recoilH = value("recoilH", base.recoilH).coerceAtLeast(0.0),
        )
    }

    /**
     * 是否**完全浸没**在水中：判眼睛所在方块。
     *
     * 用眼睛位置而不是 `player.isInWater`（只要身体沾水就为 true，站着浅水也会命中），
     * 这样「水深不过腰」不会触发必卡壳。
     */
    fun isFullySubmerged(player: Player): Boolean = when (player.eyeLocation.block.type) {
        Material.WATER, Material.BUBBLE_COLUMN -> true
        else -> false
    }

    /**
     * 是否该给手上的枪打「入水标记」：完全浸没 + 当前层级允许 `water-jam`。
     *
     * 注意标记是**打给枪**的（[GunState.wetJam]），由 [GunStateManager] 每秒检查一次；
     * 开火时只看标记，不再检查玩家是否还在水里——所以泡过水之后上岸第一枪照样卡壳。
     */
    fun shouldWet(player: Player): Boolean =
        adjustment(player).waterJam && isFullySubmerged(player)
}
