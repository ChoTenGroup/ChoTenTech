package io.github.zzzyyylllty.chotentech.data.profile

data class MetaEntry(
    val value: String = "",
    val expireAt: Long? = null   // epoch ms, null = permanent
) {
    val isExpired: Boolean get() = expireAt != null && expireAt <= System.currentTimeMillis()
}
