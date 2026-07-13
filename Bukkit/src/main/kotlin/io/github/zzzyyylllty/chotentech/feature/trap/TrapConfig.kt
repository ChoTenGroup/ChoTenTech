package io.github.zzzyyylllty.chotentech.feature.trap

import io.github.zzzyyylllty.chotentech.feature.projectile.ParticleConfig
import io.github.zzzyyylllty.chotentech.feature.projectile.SoundConfig

sealed class TrapConfig {
    abstract val id: String
    abstract val world: String
    abstract val enabled: Boolean
    abstract val interval: Int
}

data class BlowerConfig(
    override val id: String,
    override val world: String,
    override val enabled: Boolean,
    override val interval: Int,
    val minX: Double, val minY: Double, val minZ: Double,
    val maxX: Double, val maxY: Double, val maxZ: Double,
    val dirX: Double, val dirY: Double, val dirZ: Double,
    val force: Double,
    val particle: ParticleConfig?,
    val sound: SoundConfig?
) : TrapConfig()

data class LaserConfig(
    override val id: String,
    override val world: String,
    override val enabled: Boolean,
    override val interval: Int,
    val minX: Double, val minY: Double, val minZ: Double,
    val maxX: Double, val maxY: Double, val maxZ: Double,
    val damage: Double,
    val particle: ParticleConfig?,
    val sound: SoundConfig?
) : TrapConfig()

data class RocketConfig(
    override val id: String,
    override val world: String,
    override val enabled: Boolean,
    override val interval: Int,
    val launchX: Double, val launchY: Double, val launchZ: Double,
    val dirX: Double, val dirY: Double, val dirZ: Double,
    val speed: Double,
    val maxDistance: Double,
    val explosionPower: Int,
    val damage: Double,
    val particle: ParticleConfig?
) : TrapConfig()
