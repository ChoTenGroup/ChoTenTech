package io.github.zzzyyylllty.chotentech.punish

data class PunishEntry(
    val id: Long = 0,
    val targetUuid: String,
    val targetName: String,
    val operatorUuid: String?,
    val operatorName: String,
    val type: PunishType,
    val reason: String,
    val durationMs: Long,
    val issueTime: Long,
    val expiryTime: Long,
    val active: Boolean = true,
    val silent: Boolean = false,
    val meta: Map<String, String> = emptyMap()
) {
    val isPermanent: Boolean get() = durationMs == -1L || expiryTime == -1L
    val isExpired: Boolean get() = !isPermanent && System.currentTimeMillis() > expiryTime
    val remainingMs: Long get() = if (isPermanent) -1L else maxOf(0L, expiryTime - System.currentTimeMillis())
}

data class WipeBackupEntry(
    val id: Long = 0,
    val punishmentId: Long,
    val targetUuid: String,
    val targetName: String,
    val scope: String,
    val dataJson: String,
    val backupTime: Long
)

data class WipeSnapshot(
    val inventory: String? = null,
    val enderChest: String? = null,
    val currencies: Map<String, Long> = emptyMap(),
    val playerDataFields: Map<String, String> = emptyMap()
)
