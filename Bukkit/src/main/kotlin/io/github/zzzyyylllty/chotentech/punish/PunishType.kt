package io.github.zzzyyylllty.chotentech.punish

enum class PunishType(val displayName: String, val hasDuration: Boolean) {
    WARN("警告", false),
    MUTE("禁言", true),
    KICK("踢出", false),
    BAN("限制登录", true),
    WIPE("清除数据", false),
    AUTH("身份验证", true)
}
