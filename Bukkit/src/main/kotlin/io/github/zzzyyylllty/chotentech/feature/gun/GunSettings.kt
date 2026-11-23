package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * `spread` 段：散布（单位：度）。
 */
class GunSpreadSettings(
    var k: Double = 8.0,
    var barrelPerMeter: Double = 0.05,
    var moving: Double = 1.5,
    var air: Double = 2.0,
    var sneaking: Double = 0.75,
    var hurt: Double = 1.3,
    var ads: Double = 0.5,
)

/**
 * `recoil` 段：后坐。
 *
 * @param recoveryDelay **P3 追加**：最后一发之后延迟多少秒才开始回中
 *   （延迟内只做速度衰减，不把视角拉回来）。塔科夫 / COD 手感。
 */
class GunRecoilSettings(
    var speed: Double = 0.35,
    var damping: Double = 0.12,
    var recovery: Double = 0.02,
    var recoveryAds: Double = 0.04,
    var maxAccumulation: Double = 12.0,
    var resetSeconds: Double = 0.35,
    var recoveryDelay: Double = 0.12,
)

/**
 * `ballistics` 段：弹道。
 */
class GunBallisticsSettings(
    var gravity: Double = 0.05,
    var drag: Double = 0.99,
    var subStep: Double = 0.5,
    var maxSubSteps: Int = 8,
    var maxBullets: Int = 400,
    var maxRangeMultiplier: Double = 3.0,
)

/**
 * `penetration` 段：方块穿透。
 */
class GunPenetrationSettings(
    var thin: Double = 0.12,
    var hardnessLimit: Double = 3.0,
    var speedLoss: Double = 0.25,
    var damageLoss: Double = 0.15,
)

/**
 * `heat` 段：过热（P3 补齐四档闭环）。
 *
 * @param severeStopMs 进入严重档（tier 3）时强制停火的毫秒数（`forcedStopUntil` 同样是毫秒时间戳）
 * @param unlockRatio 热量触顶后，必须冷却到 `capacity * unlockRatio` 以下才解锁开火
 * @param rateMult 四档的射速系数（安全 / 轻微 / 过热 / 严重），越热射速越慢
 */
class GunHeatSettings(
    var capacity: Double = 100.0,
    var perShot: Double = 5.0,
    var coolPerTick: Double = 0.06,
    var tierMild: Double = 40.0,
    var tierHot: Double = 60.0,
    var tierSevere: Double = 80.0,
    var severeStopMs: Long = 2000L,
    var unlockRatio: Double = 0.6,
    var rateMult: List<Double> = listOf(1.0, 0.95, 0.85, 0.7),
)

/**
 * `wear` 段（P3 追加）：每次开火造成的耐久磨损。
 *
 * 实际扣减：`per-shot * (1 + 子弹穿甲值 / 100 * 0.5)`。
 */
class GunWearSettings(
    var perShot: Double = 0.06,
)

/**
 * `scope` 段（P3 追加）：瞄准相关开关。
 *
 * P3 只读取 [enabled]（决定 ADS 是否套用 `spread.ads`），
 * 开镜缩放（FOV）由 [io.github.zzzyyylllty.chotentech.feature.gun.GunZoom] 改写 Abilities 包实现；
 * 这里的 [zoom] 是**全局默认倍率**，单把枪可用 `gun.usage.zoom` 覆盖。
 */
class GunScopeSettings(
    var enabled: Boolean = true,
    /** 默认缩放倍数（1.0 = 不放大）。 */
    var zoom: Double = 2.5,
    /** 瞄准时的移速系数（1.0 = 不减速），以 MOVEMENT_SPEED 乘法修饰符实现。 */
    var moveMultiplier: Double = 0.55,
)

/** 热成像扫描与发光时长；旧配置缺键时全部使用这里的默认值。 */
class GunThermalSettings(
    var enabled: Boolean = true,
    var radius: Double = 8.0,
    var intervalTicks: Long = 5L,
    var glowTicks: Long = 40L,
)

/**
 * `stamina` 段：手部体力。
 */
class GunStaminaSettings(
    var drawCost: Double = 8.0,
    var holdCostPerSecond: Double = 2.0,
    var shotCost: Double = 1.2,
    var adsMultiplier: Double = 1.5,
    /** YAML 键为 `regen-pause-when-armed`：持枪时是否套用「持枪恢复速度」（见 [regenArmedPercentPerSecond]）。 */
    var regPause: Boolean = true,
    /**
     * **最后一次消耗**之后要静默多久才开始回复（秒）。
     *
     * 这是现在的恢复主规则：扣完体力 5 秒内一律不回，避免「扣完下一 tick 就被自然恢复抹平」。
     */
    var regenDelaySeconds: Double = 5.0,
    /** 每秒回复「手部体力上限」的百分比（默认 5%/秒）；再乘属性 `hand_stamina_regen_multiplier`。 */
    var regenPercentPerSecond: Double = 5.0,
    /**
     * **持枪时**每秒回复的百分比（`regen-pause-when-armed: true` 时用这个值）。
     *
     * 默认 0：拿着枪回不动，必须收枪。想要「持枪也能慢慢回」就调大。
     */
    var regenArmedPercentPerSecond: Double = 0.0,
)

/**
 * `broken-melee` 段：枪械损坏后的近战参数。
 *
 * @param damage 固定伤害（不叠加 `attack_damage` / 暴击 / 增伤）
 * @param cooldownTicks 近战冷却（tick）
 * @param range **P3 追加**：可击打的距离（格），取视线锥内最近的目标
 */
class GunBrokenMeleeSettings(
    var damage: Double = 3.0,
    var cooldownTicks: Int = 20,
    var range: Double = 3.0,
)

/**
 * `sounds` 段：音效键。
 */
class GunSoundSettings(
    var fire: String = "item.crossbow.shoot",
    var empty: String = "block.lever.click",
    var hit: String = "entity.arrow.hit",
    var hitBlock: String = "entity.arrow.hit_player",
)

/**
 * `damage` 段（P1 追加）：命中击退。
 */
class GunDamageSettings(
    var knockback: Double = 0.08,
)

/** P5：护甲结算与材质表。 */
class GunArmorSettings(
    var replacesAttributeDefense: Boolean = true,
    var materials: Map<String, ArmorManager.ArmorMaterial> = defaultMaterials(),
) {
    companion object {
        fun defaultMaterials(): Map<String, ArmorManager.ArmorMaterial> = mapOf(
            "DIAMOND" to ArmorManager.ArmorMaterial("DIAMOND", 0.6, "DIAMOND", 5.0),
            "IRON" to ArmorManager.ArmorMaterial("IRON", 1.0, "IRON_INGOT", 0.0),
            "STEEL" to ArmorManager.ArmorMaterial("STEEL", 0.85, "IRON_INGOT", 2.0),
            "LEATHER" to ArmorManager.ArmorMaterial("LEATHER", 1.4, "LEATHER", -5.0),
        )
    }
}

/**
 * `magazine` 段（P2 追加）：弹匣 / 读条动作相关参数。
 *
 * @param pickupLockTicks 快速换弹丢出的弹匣多少 tick 内任何人都不许拾取（默认 100 = 5 秒）
 * @param interruptDistance 读条动作允许的最大位移（格），超过即中断
 */
class GunMagazineSettings(
    var pickupLockTicks: Int = 100,
    var interruptDistance: Double = 2.0,
)

/**
 * `nameplate` 段：把耐久写进枪械物品的**显示名称**。
 *
 * @param enabled 关掉后物品名称恢复成 Sertraline 配置的原文（不再附加耐久）
 * @param format MiniMessage 模板，可用占位符
 *   `{name}` 基础名称 / `{cur}` 当前耐久 / `{max}` 最大耐久 / `{percent}` 百分比 /
 *   `{bar}` 进度条 / `{color}` 档位颜色开标签 / `{end}` 对应的闭标签 / `{flag}` 卡壳·损坏标记
 * @param hideWhenFull 满耐久时是否隐藏耐久后缀（默认显示，便于一眼看到磨损）
 * @param colorGood / colorWarn / colorBad 耐久比例 ≥50% / ≥20% / 其余 三档颜色
 * @param barLength `{bar}` 的格数
 */
class GunNameplateSettings(
    var enabled: Boolean = true,
    var format: String = "{name} <#6E7A8C>[</#6E7A8C>{color}{cur}{end}<#6E7A8C>/</#6E7A8C>{max}<#6E7A8C>]</#6E7A8C>{flag}",
    var hideWhenFull: Boolean = false,
    var colorGood: String = "#8FD4A6",
    var colorWarn: String = "#EFDF8F",
    var colorBad: String = "#E08A98",
    var barLength: Int = 10,
)

/**
 * `gun-mechanism.yml` 的强类型快照。
 *
 * 设计要点：
 * - **独立文件**：`config.yml` 是 GBK 编码的历史文件，枪械机制不使用它，
 *   因此这里单独维护 `gun-mechanism.yml`（UTF-8），首次运行由 [load] 释放到数据目录。
 * - **默认值即文档**：所有字段的初始值就是 YAML 里注释的缺省值；
 *   文件缺失 / 键缺失 / 解析失败时一律保留默认值，**不抛异常**（枪械系统不能因为
 *   一条写错的 YAML 整段瘫痪）。
 * - **只读**：运行期只读，不做回写；改配置后 `/ctt reload` 重新 [load] 即可。
 */
object GunSettings {

    @Volatile
    var spread: GunSpreadSettings = GunSpreadSettings()
        private set
    @Volatile
    var recoil: GunRecoilSettings = GunRecoilSettings()
        private set
    @Volatile
    var ballistics: GunBallisticsSettings = GunBallisticsSettings()
        private set
    @Volatile
    var penetration: GunPenetrationSettings = GunPenetrationSettings()
        private set
    @Volatile
    var heat: GunHeatSettings = GunHeatSettings()
        private set
    @Volatile
    var stamina: GunStaminaSettings = GunStaminaSettings()
        private set
    @Volatile
    var brokenMelee: GunBrokenMeleeSettings = GunBrokenMeleeSettings()
        private set
    @Volatile
    var sounds: GunSoundSettings = GunSoundSettings()
        private set
    @Volatile
    var damage: GunDamageSettings = GunDamageSettings()
        private set
    @Volatile
    var magazine: GunMagazineSettings = GunMagazineSettings()
        private set
    /** P3：每次开火的耐久磨损。 */
    @Volatile
    var wear: GunWearSettings = GunWearSettings()
        private set
    /** P3：瞄准开关（只影响 ADS 散布，不做 FOV / 热成像）。 */
    @Volatile
    var scope: GunScopeSettings = GunScopeSettings()
        private set
    /** 把耐久写进枪械物品名称。 */
    @Volatile
    var nameplate: GunNameplateSettings = GunNameplateSettings()
        private set
    @Volatile
    var thermal: GunThermalSettings = GunThermalSettings()
        private set
    @Volatile
    var armor: GunArmorSettings = GunArmorSettings()
        private set

    /** 上次加载成功与否（调试命令 / 日志用）。 */
    @Volatile
    var loaded: Boolean = false
        private set

    /** 配置文件名（与 `src/main/resources/gun-mechanism.yml` 对应）。 */
    const val FILE_NAME = "gun-mechanism.yml"

    /**
     * 材质表 = 内置 4 种 + `armor.materials.<NAME>` 里新增的键。
     *
     * 旧实现只遍历 [GunArmorSettings.defaultMaterials] 的键，服务器自己加的
     * `armor.materials.KEVLAR` 永远读不到，[ArmorManager.material] 会静默回落 IRON（系数全错）。
     */
    private fun buildArmorMaterials(config: Configuration): Map<String, ArmorManager.ArmorMaterial> {
        val defaults = GunArmorSettings.defaultMaterials()
        // UPPER 键 -> 配置里真实写法的键（YAML 允许小写，热路径按大写查表）。
        val entries = LinkedHashMap<String, String>()
        defaults.keys.forEach { entries[it] = it }
        config.getConfigurationSection("armor.materials")?.getKeys(false)?.forEach {
            entries[it.uppercase()] = it
        }
        return entries.mapValues { (upper, raw) ->
            val fallback = defaults[upper] ?: ArmorManager.ArmorMaterial(upper, 1.0, "IRON_INGOT", 0.0)
            ArmorManager.ArmorMaterial(
                name = upper,
                durabilityMult = config.dbl("armor.materials.$raw.durability-mult", fallback.durabilityMult),
                repairMaterial = config.str("armor.materials.$raw.repair-material", fallback.repairMaterial),
                levelBonus = config.dbl("armor.materials.$raw.level-bonus", fallback.levelBonus),
            )
        }
    }

    /**
     * 加载（或重新加载）`gun-mechanism.yml`。
     *
     * 文件不存在时先 [releaseResourceFile] 释放内置默认文件再读取；
     * 读取/解析任一步失败都保留上一份（首次为默认值）配置。
     */
    fun load() {
        val file = try {
            File(getDataFolder(), FILE_NAME)
        } catch (e: Exception) {
            devLog("[Gun] 无法定位数据目录，枪械机制配置使用默认值: ${e.message}")
            return
        }
        if (!file.exists()) {
            try {
                releaseResourceFile(FILE_NAME, false)
            } catch (e: Exception) {
                devLog("[Gun] 释放 $FILE_NAME 失败: ${e.message}")
            }
        }

        val config = try {
            if (!file.exists()) {
                devLog("[Gun] 未找到 $FILE_NAME，枪械机制配置使用默认值")
                return
            }
            Configuration.loadFromFile(file)
        } catch (e: Exception) {
            devLog("[Gun] 读取 $FILE_NAME 失败，沿用上一份配置: ${e.message}")
            return
        }

        spread = GunSpreadSettings(
            k = config.dbl("spread.k", 8.0),
            barrelPerMeter = config.dbl("spread.barrel-per-meter", 0.05),
            moving = config.dbl("spread.moving", 1.5),
            air = config.dbl("spread.air", 2.0),
            sneaking = config.dbl("spread.sneaking", 0.75),
            hurt = config.dbl("spread.hurt", 1.3),
            ads = config.dbl("spread.ads", 0.5),
        )
        recoil = GunRecoilSettings(
            speed = config.dbl("recoil.speed", 0.35),
            damping = config.dbl("recoil.damping", 0.12),
            recovery = config.dbl("recoil.recovery", 0.02),
            recoveryAds = config.dbl("recoil.recovery-ads", 0.04),
            maxAccumulation = config.dbl("recoil.max-accumulation", 12.0),
            resetSeconds = config.dbl("recoil.reset-seconds", 0.35),
            recoveryDelay = config.dbl("recoil.recovery-delay", 0.12).coerceAtLeast(0.0),
        )
        ballistics = GunBallisticsSettings(
            gravity = config.dbl("ballistics.gravity", 0.05),
            drag = config.dbl("ballistics.drag", 0.99),
            subStep = config.dbl("ballistics.sub-step", 0.5).coerceAtLeast(0.05),
            maxSubSteps = config.integer("ballistics.max-sub-steps", 8).coerceAtLeast(1),
            maxBullets = config.integer("ballistics.max-bullets", 400).coerceAtLeast(1),
            maxRangeMultiplier = config.dbl("ballistics.max-range-multiplier", 3.0).coerceAtLeast(0.1),
        )
        penetration = GunPenetrationSettings(
            thin = config.dbl("penetration.thin", 0.12),
            hardnessLimit = config.dbl("penetration.hardness-limit", 3.0),
            speedLoss = config.dbl("penetration.speed-loss", 0.25).coerceIn(0.0, 1.0),
            damageLoss = config.dbl("penetration.damage-loss", 0.15).coerceIn(0.0, 1.0),
        )
        heat = GunHeatSettings(
            capacity = config.dbl("heat.capacity", 100.0).coerceAtLeast(1.0),
            perShot = config.dbl("heat.per-shot", 5.0),
            coolPerTick = config.dbl("heat.cool-per-tick", 0.06).coerceAtLeast(0.0),
            tierMild = config.dbl("heat.tier-mild", 40.0),
            tierHot = config.dbl("heat.tier-hot", 60.0),
            tierSevere = config.dbl("heat.tier-severe", 80.0),
            severeStopMs = config.long("heat.severe-stop-ms", 2000L).coerceAtLeast(0L),
            unlockRatio = config.dbl("heat.unlock-ratio", 0.6).coerceIn(0.0, 1.0),
            rateMult = config.rateMult(),
        )
        stamina = GunStaminaSettings(
            drawCost = config.dbl("stamina.draw-cost", 8.0).coerceAtLeast(0.0),
            holdCostPerSecond = config.dbl("stamina.hold-cost-per-second", 2.0).coerceAtLeast(0.0),
            shotCost = config.dbl("stamina.shot-cost", 1.2).coerceAtLeast(0.0),
            adsMultiplier = config.dbl("stamina.ads-multiplier", 1.5).coerceAtLeast(0.0),
            regPause = config.bool("stamina.regen-pause-when-armed", true),
            regenDelaySeconds = config.dbl("stamina.regen-delay-seconds", 5.0).coerceAtLeast(0.0),
            regenPercentPerSecond = config.dbl("stamina.regen-percent-per-second", 5.0).coerceAtLeast(0.0),
            regenArmedPercentPerSecond =
                config.dbl("stamina.regen-armed-percent-per-second", 0.0).coerceAtLeast(0.0),
        )
        brokenMelee = GunBrokenMeleeSettings(
            damage = config.dbl("broken-melee.damage", 3.0).coerceAtLeast(0.0),
            cooldownTicks = config.integer("broken-melee.cooldown-ticks", 20).coerceAtLeast(0),
            range = config.dbl("broken-melee.range", 3.0).coerceIn(0.5, 8.0),
        )
        sounds = GunSoundSettings(
            fire = config.str("sounds.fire", "item.crossbow.shoot"),
            // 注意：默认值必须是**真实存在**的音效键。
            // 曾用 item.crossbow.dry_fire，该键在 Minecraft 里不存在，会刷
            // 「Unable to play unknown soundEvent」；block.lever.click 是原版空扳机感最接近的。
            empty = config.str("sounds.empty", "block.lever.click"),
            hit = config.str("sounds.hit", "entity.arrow.hit"),
            hitBlock = config.str("sounds.hit-block", "entity.arrow.hit_player"),
        )
        damage = GunDamageSettings(
            knockback = config.dbl("damage.knockback", 0.08).coerceAtLeast(0.0),
        )
        magazine = GunMagazineSettings(
            pickupLockTicks = config.integer("magazine.pickup-lock-ticks", 100).coerceAtLeast(0),
            interruptDistance = config.dbl("magazine.interrupt-distance", 2.0).coerceAtLeast(0.0),
        )
        wear = GunWearSettings(
            perShot = config.dbl("wear.per-shot", 0.06).coerceAtLeast(0.0),
        )
        scope = GunScopeSettings(
            enabled = config.bool("scope.enabled", true),
            zoom = config.dbl("scope.zoom", 2.5).let { if (it.isFinite() && it > 1.0) it else 2.5 },
            moveMultiplier = config.dbl("scope.move-multiplier", 0.55)
                .let { if (it in 0.05..1.0) it else 0.55 },
        )
        nameplate = GunNameplateSettings(
            enabled = config.bool("nameplate.enabled", true),
            format = config.str("nameplate.format", GunNameplateSettings().format)
                .takeIf { it.contains("{name}") && it.contains("{cur}") }
                ?: GunNameplateSettings().format,
            hideWhenFull = config.bool("nameplate.hide-when-full", false),
            colorGood = config.str("nameplate.colors.good", "#8FD4A6"),
            colorWarn = config.str("nameplate.colors.warn", "#EFDF8F"),
            colorBad = config.str("nameplate.colors.bad", "#E08A98"),
            barLength = config.integer("nameplate.bar-length", 10).coerceIn(0, 40),
        )
        thermal = GunThermalSettings(
            enabled = config.bool("thermal.enabled", true),
            radius = config.dbl("thermal.radius", 8.0).coerceAtLeast(0.0),
            intervalTicks = config.long("thermal.interval-ticks", 5L).coerceAtLeast(1L),
            glowTicks = config.long("thermal.glow-ticks", 40L).coerceAtLeast(1L),
        )
        armor = GunArmorSettings(
            replacesAttributeDefense = config.bool("armor.replaces-attribute-defense", true),
            materials = buildArmorMaterials(config),
        )

        // 文档漂移的兜底告警：写在这里的键如果代码不读，启动日志会直接点名。
        config.warnUnknownKeys("armor", setOf("replaces-attribute-defense", "materials"), listOf("materials"))

        loaded = true
        devLog("[Gun] 枪械机制配置已加载: $FILE_NAME")
    }

    // ==================== 热量档位 ====================

    /**
     * 热量档位（0=冷 1=温 2=热 3=危险）。
     */
    fun heatTier(value: Double): Int = when {
        value >= heat.tierSevere -> 3
        value >= heat.tierHot -> 2
        value >= heat.tierMild -> 1
        else -> 0
    }

    /**
     * 热量档位对散布的系数：**越热越不准**（乘在散布上，>1 表示散布变大）。
     *
     * 设计文档 §5.9：安全 1.00 / 轻微 1.15 / 过热 1.35 / 严重 1.70。
     */
    fun heatSpreadMultiplier(tier: Int): Double = when (tier) {
        1 -> 1.15
        2 -> 1.35
        3 -> 1.70
        else -> 1.0
    }

    /** 热量档位对卡壳率的系数（越热越容易卡壳）。 */
    fun heatJamMultiplier(tier: Int): Double = when (tier) {
        1 -> 1.25
        2 -> 1.6
        3 -> 2.2
        else -> 1.0
    }

    /** 热量档位中文名（HUD / 提示用），与设计文档的四档命名一致。 */
    fun heatTierText(tier: Int): String = when (tier) {
        1 -> "轻微过热"
        2 -> "过热"
        3 -> "严重过热"
        else -> "安全"
    }

    /**
     * 热量档位对**射速**的系数（P3）：越热射速越慢。
     *
     * 缺省 `[1.0, 0.95, 0.85, 0.7]`，数组长度不足时取 1.0（保守：不因为配置写短而卡死射速）。
     */
    fun heatRateMultiplier(tier: Int): Double =
        heat.rateMult.getOrNull(tier.coerceIn(0, 3))?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

    // ==================== Configuration 读取工具 ====================

    private fun Configuration.dbl(path: String, def: Double): Double = try {
        getString(path)?.trim()?.toDoubleOrNull() ?: def
    } catch (_: Exception) {
        def
    }

    private fun Configuration.integer(path: String, def: Int): Int = try {
        getString(path)?.trim()?.toDoubleOrNull()?.toInt() ?: def
    } catch (_: Exception) {
        def
    }

    private fun Configuration.long(path: String, def: Long): Long = try {
        getString(path)?.trim()?.toDoubleOrNull()?.toLong() ?: def
    } catch (_: Exception) {
        def
    }

    /**
     * 读四档射速系数（`heat.rate-mult`）。
     *
     * 支持两种写法：
     * - YAML 列表 `rate-mult: [1.0, 0.95, 0.85, 0.7]`
     * - 三个标量 `rate-mild` / `rate-hot` / `rate-severe`（列表缺失时的兼容写法）
     *
     * 列表按**固定位置**解析：非法项（空 / 非数字 / <=0）使用该位置的默认值，
     * 不足 4 项也按对应位置默认值补齐，绝不让后面的档位左移错位。
     */
    private fun Configuration.rateMult(): List<Double> {
        val fallback = GunHeatSettings().rateMult
        try {
            val raw = getStringList("heat.rate-mult")
            if (!raw.isNullOrEmpty()) {
                return (0 until 4).map { index ->
                    val value = raw.getOrNull(index)?.trim()?.toDoubleOrNull()
                    if (value == null || !value.isFinite() || value <= 0.0) fallback[index] else value
                }
            }
        } catch (_: Exception) {
        }
        // 退化成三个标量
        val mild = dbl("heat.rate-mild", fallback[1])
        val hot = dbl("heat.rate-hot", fallback[2])
        val severe = dbl("heat.rate-severe", fallback[3])
        return listOf(
            1.0,
            mild.takeIf { it > 0.0 } ?: fallback[1],
            hot.takeIf { it > 0.0 } ?: fallback[2],
            severe.takeIf { it > 0.0 } ?: fallback[3],
        )
    }

    private fun Configuration.bool(path: String, def: Boolean): Boolean = try {
        when (getString(path)?.trim()?.lowercase()) {
            null, "" -> def
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> def
        }
    } catch (_: Exception) {
        def
    }

    private fun Configuration.str(path: String, def: String): String = try {
        val value = getString(path)?.trim()
        if (value.isNullOrEmpty()) def else value
    } catch (_: Exception) {
        def
    }

    /**
     * 一次性把「看起来像本段、但代码从不读取」的键报出来。
     *
     * 这是防文档漂移的最后一道闸：`armor.trauma` / `armor.materials.X.内外伤` 这类
     * 写在文档里却从未实现（或已被删除）的键，以前改完完全没反应、也没有任何提示。
     */
    private fun Configuration.warnUnknownKeys(
        section: String,
        known: Collection<String>,
        nestedAllowed: Collection<String> = emptyList(),
    ) {
        val root = try {
            getConfigurationSection(section) ?: return
        } catch (_: Exception) {
            return
        }
        val unknown = try {
            root.getKeys(false).filter { it !in known }
        } catch (_: Exception) {
            emptyList()
        }
        if (unknown.isNotEmpty()) {
            devLog("[Gun] gun-mechanism.yml 的 `$section` 段里有代码不会读取的键: $unknown（已忽略）")
        }
        for (nested in nestedAllowed) {
            val children = try {
                root.getConfigurationSection(nested)?.getKeys(false) ?: continue
            } catch (_: Exception) {
                continue
            }
            // 材质的子键是「材质名 → 其属性」，只校验每个材质内部的键
            for (material in children) {
                val inner = try {
                    root.getConfigurationSection("$nested.$material")?.getKeys(false) ?: continue
                } catch (_: Exception) {
                    continue
                }
                val bad = inner.filter { it !in ArmorMaterialKeys }
                if (bad.isNotEmpty()) {
                    devLog("[Gun] `$section.$nested.$material` 里有代码不会读取的键: $bad（已忽略）")
                }
            }
        }
    }

    /** `armor.materials.<NAME>` 下真正被读取的三个键。 */
    private val ArmorMaterialKeys = setOf("durability-mult", "repair-material", "level-bonus")
}
