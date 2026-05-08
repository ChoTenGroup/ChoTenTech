package io.github.zzzyyylllty.chotentech.data

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class PlayerData (
    var userId: String,
    val playerName: String,
    val playerUUID: String,
    var noclipCooldown: Double,
    var noclipPoint: @Contextual NoclipPoint?,
    var noclipTimeLeft: Double,
    var noclipping: Boolean,
    var stress: Double = 5.0,
    var sanity: Double = 40.0,
    var maxSanity: Double = 100.0,
    var darkness: Double = 15.0,
    var maxDarkness: Double = 100.0,
)