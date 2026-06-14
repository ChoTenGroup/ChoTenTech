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
    val shockwave: ShockwaveConfig? = null
)

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
