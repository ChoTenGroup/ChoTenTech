package io.github.zzzyyylllty.chotentech.data

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class PlayerData (
    var userId: String,
    val playerName: String,
    val playerUUID: String,
) {

}