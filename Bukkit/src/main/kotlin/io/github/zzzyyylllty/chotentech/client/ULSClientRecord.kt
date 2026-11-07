package io.github.zzzyyylllty.chotentech.client

import java.util.UUID

/**
 * 服务端侧记录的一名客户端模组使用者。
 *
 * ## 关于可信度
 *
 * 这份数据**全部来自客户端自述**，因此除 [name] 与 [address] 外
 * 都不可作为鉴权依据：
 *
 * - [name] 由服务端从 `Player#getName()` 取，可信。
 * - [address] 由服务端从连接取 `Player#getAddress()`，可信。
 * - 其余字段（mod 列表、Java 版本、语言等）仅用于**记录、排障与
 *   下发 UI 能力**，绝不能用于权限判定。客户端可以伪造它们。
 *
 * 特别注意：客户端上报的 `username` 字段（对应 [reportedUsername]）
 * 与真实登录名可能不一致——它只是客户端本地缓存的玩家名，
 * 记录它是为了发现异常，而不是为了信任它。
 *
 * @property uuid 玩家 UUID（服务端侧）
 * @property name 真实玩家名（服务端侧，可信）
 * @property address 连接远端地址（服务端侧，可信）
 * @property protocolVersion 客户端协议版本
 * @property modVersion 客户端模组版本，形如 `ulshelper@1.0.0`
 * @property gameVersion 客户端游戏版本
 * @property javaVersion 客户端 Java 版本
 * @property osName 客户端操作系统名
 * @property osArch 客户端操作系统架构
 * @property reportedUsername 客户端自述的玩家名（不可信）
 * @property locale 客户端语言
 * @property modCount mod 总数（可能大于 [mods] 大小，表示被裁剪过）
 * @property mods 已安装 mod：id → 版本
 * @property firstSeen 首次握手时间戳（毫秒）
 * @property lastSeen 最近一次握手/心跳时间戳（毫秒）
 */
data class ULSClientRecord(
    val uuid: UUID,
    val name: String,
    val address: String,
    val protocolVersion: Int,
    val modVersion: String,
    val gameVersion: String,
    val javaVersion: String,
    val osName: String,
    val osArch: String,
    val reportedUsername: String,
    val locale: String,
    val modCount: Int,
    val mods: Map<String, String>,
    val firstSeen: Long,
    val lastSeen: Long
) {

    /** mod 列表是否因超出上限而被裁剪。 */
    val modsTruncated: Boolean get() = modCount > mods.size

    /** 是否安装了某个 mod。 */
    fun hasMod(modId: String): Boolean = mods.containsKey(modId)

    /** 某个 mod 的版本，未安装返回 null。 */
    fun modVersion(modId: String): String? = mods[modId]

    /**
     * 客户端自述玩家名是否与真实登录名不符。
     * 正常情况下应为 false；true 说明客户端环境异常，值得排查，
     * 但**不构成作弊证据**（例如离线模式下的改名）。
     */
    val usernameMismatch: Boolean
        get() = reportedUsername.isNotBlank() &&
                !reportedUsername.equals("unknown", ignoreCase = true) &&
                !reportedUsername.equals(name, ignoreCase = true)

    /** 一行摘要，用于日志。 */
    fun describe(): String = buildString {
        append("ULSClient{")
        append("uuid=").append(uuid)
        append(", name=").append(name)
        append(", ip=").append(address)
        append(", proto=").append(protocolVersion)
        append(", mod=").append(modVersion)
        append(", mc=").append(gameVersion)
        append(", java=").append(javaVersion)
        append(", os=").append(osName).append('/').append(osArch)
        append(", locale=").append(locale)
        append(", mods=").append(mods.size)
        if (modsTruncated) append('+').append(modCount - mods.size)
        if (usernameMismatch) append(", USERNAME_MISMATCH=").append(reportedUsername)
        append('}')
    }
}
