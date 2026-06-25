package io.github.zzzyyylllty.chotentech.feature.projectile

/**
 * 音效配置
 */
data class SoundConfig(
    val key: String,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f
)

/**
 * 粒子效果配置
 */
data class ParticleConfig(
    val key: String,
    val count: Int = 10,
    val speed: Double = 0.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0
)

/**
 * 冲击波环形粒子配置
 */
data class ShockwaveConfig(
    val particleKey: String = "glow",
    val count: Int = 36,
    val speed: Double = 1.0,
    val rings: Int = 3
)

/**
 * 投掷物效果定义
 */
data class ProjectileEffect(
    val type: String,
    val range: Double = 0.0,
    val seconds: Int = 0,
    val amount: Int = 0,
    val damage: Map<String, Double> = emptyMap(),
    val power: Int = 0,
    val effectId: String = "",
    val level: Int = 1,
    val sound: SoundConfig? = null,
    val particle: ParticleConfig? = null,
    val check: Boolean = false,
    val shockwave: ShockwaveConfig? = null,
    /** 基础穿透值，随距离衰减后作为无视护甲的伤害 */
    val penetration: Double = 0.0,
    /** 距离衰减配置 */
    val falloff: FalloffConfig? = null
)

/**
 * 距离衰减配置
 */
data class FalloffConfig(
    /** 是否启用衰减 */
    val enabled: Boolean = false,
    /** 衰减起始距离（在此距离内无衰减） */
    val start: Double = 0.0,
    /** 衰减结束距离（达到最小倍率） */
    val end: Double = 30.0,
    /** 最大距离时的最小倍率 */
    val minMult: Double = 0.1,
    /** 衰减公式: linear | exponential */
    val formula: String = "linear"
) {
    /**
     * 根据距离计算衰减倍率
     */
    fun multiplier(distance: Double): Double {
        if (!enabled || distance <= start) return 1.0
        if (distance >= end) return minMult

        val t = (distance - start) / (end - start)
        val raw = when (formula) {
            "exponential" -> {
                val rawExp = kotlin.math.exp(-3.0 * t)
                // Normalize so result is 1 at t=0 and 0 at t=1
                val expAt1 = kotlin.math.exp(-3.0) // ≈ 0.0498
                (rawExp - expAt1) / (1.0 - expAt1)
            }
            else -> 1.0 - t  // linear
        }
        // 将 raw (范围 0~1) 映射到 minMult~1
        return minMult + (1.0 - minMult) * raw
    }
}

/**
 * 引信配置：固定值或随机区间
 */
sealed class FuseConfig {
    data class Fixed(val seconds: Double) : FuseConfig()
    data class Range(val min: Double, val max: Double) : FuseConfig()

    fun rollSeconds(): Double = when (this) {
        is Fixed -> seconds
        is Range -> min + (max - min) * kotlin.random.Random.nextDouble()
    }
}

/**
 * 反弹参数
 */
data class BounceConfig(
    val max: Int = 5,
    val factor: Double = 0.6,
    val upward: Double = 0.2
)

/**
 * 投掷物配置定义
 */
data class ProjectileData(
    val id: String,
    val fuse: FuseConfig,
    val impact: Boolean,
    val bounce: BounceConfig = BounceConfig(),
    val sounds: Map<String, SoundConfig> = emptyMap(),
    val particles: Map<String, ParticleConfig> = emptyMap(),
    val effects: List<ProjectileEffect>
)
