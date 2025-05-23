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
)