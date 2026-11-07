package io.github.zzzyyylllty.chotentech.client

import io.github.zzzyyylllty.chotentech.logger.infoS
import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 服务端侧的 ULS 客户端握手接收与校验。
 *
 * ## 设计前提（重要）
 *
 * 服务器是**纯插件**环境，客户端模组**完全可选**。因此：
 *
 * - 未安装模组的玩家照常游戏，本管理器对他们完全透明；
 * - 握手失败、协议不匹配、数据损坏一律**只记录日志**，
 *   绝不踢人、绝不阻断原版流程；
 * - 所有状态都是内存态的可选增强，丢失不影响游戏。
 *
 * ## 安全性
 *
 * 客户端上报的一切（mod 列表、用户名、Java 版本……）都是**自述**，
 * 可以伪造。本管理器只把它们当作「记录 / 排障 / 决定下发哪些 UI 能力」
 * 的输入，绝不用作权限判定。
 *
 * 校验重点放在**健壮性**而非信任：
 * - 限制封包大小、字段长度、mod 条数，防止内存放大与 OOM；
 * - 对心跳做限流，防止刷屏；
 * - 任何解析异常都吞掉并记录，不影响服务端主线程。
 *
 * ## 线程
 *
 * 插件消息回调线程取决于服务端实现；这里只解析字节与核对会话，
 * 玩家实体、菜单处理函数和握手元数据写入统一切主线程。
 */
object ULSClientManager : PluginMessageListener {

    /** 在线且已完成握手的客户端：UUID → 记录。 */
    private val sessions = ConcurrentHashMap<UUID, ULSClientRecord>()

    /** 每名玩家最近一次环境上报时间，用于限流（毫秒时间戳）。 */
    private val lastUpdate = ConcurrentHashMap<UUID, Long>()

    /** 握手轮次：旧连接排队中的成功回执不可覆盖较新的失败尝试。 */
    private val handshakeEpoch = ULSHandshakeEpochs()

    /** 是否已在 onEnable 中注册完毕。防止重复注册。 */
    @Volatile
    private var registered = false

    // ==================== 生命周期 ====================

    /**
     * 注册插件通道与监听器。
     *
     * 在 `LifeCycle.ENABLE` 阶段由 TabooLib 自动调用。重复调用安全。
     */
    @Awake(LifeCycle.ENABLE)
    fun register() {
        if (registered) return
        registered = true
        try {
            val plugin = BukkitPlugin.getInstance()
            plugin.server.messenger.registerIncomingPluginChannel(
                plugin, ULSProtocol.CHANNEL_MAIN, this
            )
            plugin.server.messenger.registerOutgoingPluginChannel(
                plugin, ULSProtocol.CHANNEL_SERVER
            )
            infoS("ULS 客户端握手通道已注册: ${ULSProtocol.CHANNEL_MAIN}")
        } catch (t: Throwable) {
            registered = false
            warningS("注册 ULS 客户端通道失败，客户端增强功能将不可用（游戏本身不受影响）: $t")
        }
    }

    /** 玩家离线时清理记录。 */
    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
        lastUpdate.remove(event.player.uniqueId)
        handshakeEpoch.invalidate(event.player.uniqueId)
        // 菜单状态也必须清理：openMenus / 限流窗口都是按 UUID 存的，
        // 不清理就会随玩家进出一直涨（长开的服务器上是实打实的内存泄漏）。
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.clearPlayer(event.player)
        // 示例菜单自己的状态也一样——它有一张按 UUID 的 toggled 表。
        // 顺带保证「玩家退出再回来，开关回到默认」：跨会话保留一个
        // 演示用的布尔值没有意义，只会让复现问题的人困惑。
        io.github.zzzyyylllty.chotentech.client.menu.ULSDemoMenu.clearPlayer(event.player)
        // 测试菜单同理：它有一张按 UUID 的开关表。
        io.github.zzzyyylllty.chotentech.client.menu.ULSTestMenus.clearPlayer(event.player)
    }

    // ==================== 接收 ====================

    /** 接收者所在线程不受保证：先验证完整线格式，再切主线程操作玩家。 */
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != ULSProtocol.CHANNEL_MAIN) return
        if (message.size > ULSProtocol.MAX_PAYLOAD_BYTES) return

        try {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                val packetId = readString(input, ULSProtocol.MAX_ID_LEN)
                val bodyLength = readVarInt(input)
                val body = readBody(input, bodyLength)
                if (input.available() != 0) throw java.io.IOException("ULS 封包末尾有额外字节")

                if (packetId == ULSProtocol.PKT_MENU_DISMISS) {
                    handleDismiss(player, body)
                    return@use
                }
                if (packetId == ULSProtocol.PKT_ACTION) {
                    handleAction(player, body)
                    return@use
                }
                val uuid = player.uniqueId
                val epoch = if (packetId == ULSProtocol.PKT_HANDSHAKE) {
                    val fresh = handshakeEpoch.begin(uuid)
                    // 收到较新的握手时先废止旧会话；旧握手的排队任务在下面
                    // 核对 epoch 后不能再覆盖这一轮（即便最新握手失败）。
                    sessions.remove(uuid)
                    lastUpdate.remove(uuid)
                    io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.invalidateEnhanced(player)
                    fresh
                } else handshakeEpoch.snapshot(uuid)
                val process = Runnable {
                    if (!player.isOnline || !handshakeEpoch.matches(uuid, epoch)) return@Runnable
                    try {
                        DataInputStream(ByteArrayInputStream(body)).use { packet ->
                            when (packetId) {
                                ULSProtocol.PKT_HANDSHAKE -> handleHandshake(player, packet, isUpdate = false)
                                ULSProtocol.PKT_ENV_UPDATE -> handleHandshake(player, packet, isUpdate = true)
                                ULSProtocol.PKT_PING -> handlePing(player, packet)
                                else -> return@Runnable
                            }
                            if (packet.available() != 0) throw java.io.IOException("ULS $packetId 载荷末尾有额外字节")
                        }
                    } catch (t: Exception) {
                        devLog("ULS $packetId 载荷无效 (${player.name}): ${t.message}")
                    }
                }
                if (Bukkit.isPrimaryThread()) process.run()
                else Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), process)
            }
        } catch (t: Exception) {
            devLog("ULS 封包无效 (${player.name}): ${t.message}")
        }
    }

    // ==================== 处理器 ====================

    private fun handleHandshake(player: Player, input: DataInputStream, isUpdate: Boolean) {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId
        // ---- 限流：环境更新不能刷得太快 ----
        // 注意：限流命中时不能静默丢弃。客户端发完 env_update 就认为
        // 服务端已收到最新环境，如果这里什么都不回，客户端会一直停在
        // 旧的能力表上且毫不知情。所以仍然回一个 ack（带当前真实能力表），
        // 让客户端的状态与服务端保持一致。
        if (isUpdate && !handshakeRecorded(uuid)) {
            // 从未握手却直接发 env_update：不可信，按协议拒绝处理。
            devLog("收到 ${player.name} 未经握手的环境上报，已忽略")
            return
        }
        if (isUpdate) {
            val previous = lastUpdate[uuid]
            if (previous != null && now - previous < ULSProtocol.ENV_UPDATE_MIN_INTERVAL_MS) {
                devLog("${player.name} 的环境上报过于频繁 (间隔 ${now - previous}ms)，仅回执不更新记录")
                // 丢弃载荷但回执，保证客户端不会以为自己成功了。
                drainHandshakePayload(input)
                if (input.available() != 0) throw java.io.IOException("环境上报载荷末尾有额外字节")
                respondHandshake(player, ULSProtocol.STATUS_OK, "环境上报过于频繁，已忽略")
                return
            }
        }
        // ---- 读取（顺序必须与客户端 ClientEnvironment#write 完全一致）----
        val protocolVersion = readVarInt(input)
        val modVersion = readString(input, ULSProtocol.MAX_ID_LEN)
        val gameVersion = readString(input, 32)
        val javaVersion = readString(input, 32)
        val osName = readString(input, 64)
        val osArch = readString(input, 32)
        val reportedUsername = readString(input, 32)
        val locale = readString(input, 16)
        readBoolean(input) // hasULSHelper，为将来其它实现保留

        val modCount = readVarInt(input)
        val modSize = readVarInt(input)
        if (modSize !in 0..ULSProtocol.MAX_REPORTED_MODS) {
            throw java.io.IOException("客户端 mod 列表数非法: $modSize")
        }
        val mods = LinkedHashMap<String, String>(modSize.coerceAtMost(64))
        for (i in 0 until modSize) {
            val id = readString(input, ULSProtocol.MAX_ID_LEN)
            val ver = readString(input, ULSProtocol.MAX_ID_LEN)
            if (id.isNotEmpty()) mods[id] = ver
        }

        if (input.available() != 0) throw java.io.IOException("握手载荷末尾有额外字节")

        // ---- 校验 ----
        val status = validate(player, protocolVersion)
        if (status != ULSProtocol.STATUS_OK) {
            warningS(
                "客户端握手校验未通过: 玩家=${player.name}, 状态=$status, " +
                        "客户端协议=$protocolVersion, 服务端协议=${ULSProtocol.PROTOCOL_VERSION}"
            )
            respondHandshake(player, status, "协议版本不兼容")
            return
        }

        // ---- 记录 ----
        val existing = sessions[uuid]
        val record = ULSClientRecord(
            uuid = uuid,
            name = player.name,                 // 可信：服务端侧
            address = player.address?.address?.hostAddress ?: "unknown", // 可信：服务端侧
            protocolVersion = protocolVersion,
            modVersion = modVersion,
            gameVersion = gameVersion,
            javaVersion = javaVersion,
            osName = osName,
            osArch = osArch,
            reportedUsername = reportedUsername,
            locale = locale,
            modCount = modCount,
            mods = mods,
            firstSeen = existing?.firstSeen ?: now,
            lastSeen = now
        )
        sessions[uuid] = record
        if (isUpdate) lastUpdate[uuid] = now

        if (existing == null) {
            infoS("ULS 客户端已连接并完成握手: ${record.describe()}")
        } else {
            devLog("ULS 客户端环境已更新: ${record.describe()}")
        }
        if (record.usernameMismatch) {
            warningS(
                "玩家 ${record.name} 的客户端自述用户名 '$reportedUsername' 与登录名不一致。" +
                        "这通常只是客户端缓存问题，不代表作弊，仅供排查参考。"
            )
        }

        respondHandshake(player, ULSProtocol.STATUS_OK, "欢迎，${player.name}")
    }

    /** 已完成帧校验后，菜单层再校验会话、动作和限流。 */
    private fun handleAction(player: Player, body: ByteArray) {
        if (!handshakeRecorded(player.uniqueId)) return
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.handleAction(player, body)
    }

    /** 先捕获当前增强菜单，再由菜单层安全切换到主线程移除。 */
    private fun handleDismiss(player: Player, body: ByteArray) {
        if (!handshakeRecorded(player.uniqueId)) return
        val dismiss = io.github.zzzyyylllty.chotentech.client.menu.ULSMenuCodec.decodeDismiss(body) ?: return
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.handleDismiss(player, dismiss)
    }

    /**
     * 按声明长度读取载荷体。
     *
     * 上限用 [ULSProtocol.MAX_PAYLOAD_BYTES]：虽然整个封包已经在入口处
     * 卡过一次大小，但那个校验针对的是**收到的全部字节**，而这里的
     * `bodyLength` 是客户端自己写的数字，可以谎报。不校验就会
     * 用 `ByteArray(huge)` 直接分配。
     */
    private fun readBody(input: DataInputStream, bodyLength: Int): ByteArray {
        if (bodyLength < 0 || bodyLength > ULSProtocol.MAX_PAYLOAD_BYTES) {
            throw java.io.IOException("载荷体长度非法: $bodyLength")
        }
        val body = ByteArray(bodyLength)
        input.readFully(body)
        return body
    }

    private fun handlePing(player: Player, input: DataInputStream) {
        val sentAt = input.readLong()
        if (input.available() != 0) throw java.io.IOException("心跳载荷长度应为 8 字节")
        // computeIfPresent 避免心跳快照覆盖刚刚完成的环境更新记录。
        val uuid = player.uniqueId
        if (sessions.computeIfPresent(uuid) { _, record ->
                record.copy(lastSeen = System.currentTimeMillis())
            } == null) return

        // 原样回传时间戳，客户端据此算 RTT
        try {
            val plugin = BukkitPlugin.getInstance()
            val body = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(body).use { o ->
                o.writeLong(sentAt)
            }
            sendToClient(player, plugin, ULSProtocol.PKT_PONG, body.toByteArray())
        } catch (t: Throwable) {
            devLog("回送 PONG 失败 (${player.name}): ${t.message}")
        }
    }

    private fun respondHandshake(player: Player, status: Int, message: String) {
        try {
            val plugin = BukkitPlugin.getInstance()
            val body = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(body).use { o ->
                writeVarInt(o, status)
                writeVarInt(o, ULSProtocol.PROTOCOL_VERSION)
                writeString(o, "${ULSProtocol.SERVER_ID}@${pluginVersion(plugin)}", ULSProtocol.MAX_ID_LEN)
                writeString(o, message, ULSProtocol.MAX_STRING_LEN)

                // 下发当前服务端支持的菜单与组件能力。
                val capabilities = currentCapabilities()
                writeVarInt(o, capabilities.size)
                capabilities.forEach { (key, enabled) ->
                    writeString(o, key, ULSProtocol.MAX_ID_LEN)
                    o.writeBoolean(enabled)
                }
            }
            sendToClient(player, plugin, ULSProtocol.PKT_HANDSHAKE_ACK, body.toByteArray())
        } catch (t: Throwable) {
            warningS("向 ${player.name} 发送握手应答失败: ${t.message}")
        }
    }

    /**
     * 按客户端 Fabric 载荷的线格式发送封包。
     *
     * <p>客户端的接收载荷 `ULSPayloads.S2C` 用
     * `PacketCodec.tuple(string(MAX_ID_LEN), BYTE_ARRAY, ...)` 解码，
     * 也就是「封包 ID + <b>VarInt 长度前缀</b> + 载荷体」。
     * Bukkit 这一侧必须手工把长度前缀写出来，否则客户端解码会错位。</p>
     *
     * 供菜单层（[io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager]）
     * 复用。**不要把它变成 public**——它绕过插件的通道注册检查，
     * 包外调用容易写出「明明没注册通道却以为发出去了」的代码。
     */
    internal fun sendToClient(
        player: Player,
        packetId: String,
        body: ByteArray,
    ) {
        sendToClient(player, BukkitPlugin.getInstance(), packetId, body)
    }

    private fun sendToClient(player: Player, plugin: org.bukkit.plugin.Plugin, packetId: String, body: ByteArray) {
        val out = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(out).use { o ->
            writeString(o, packetId, ULSProtocol.MAX_ID_LEN)
            writeVarInt(o, body.size)
            o.write(body)
        }
        val payload = out.toByteArray()
        require(payload.size <= ULSProtocol.MAX_PAYLOAD_BYTES) { "下发封包过大: ${payload.size} 字节" }

        // `Player#sendPluginMessage` **不是线程安全的**：它要读玩家的
        // 连接状态，而那个状态在主线程被改。从 Netty 线程直接调用可能
        // 在玩家正好掉线/切服时抛异常或发出半截数据。
        //
        // 调用方也可能来自异步任务；判据是当前线程是否为主线程，
        // 而不是依赖调用方的线程约定。
        if (!Bukkit.getServer().isPrimaryThread) {
            // 排队期间玩家可能已经离线；对一个不在线的玩家发包没有意义，
            // 而且某些实现会抛异常。
            Bukkit.getServer().scheduler.runTask(plugin, Runnable {
                if (player.isOnline) {
                    player.sendPluginMessage(plugin, ULSProtocol.CHANNEL_SERVER, payload)
                }
            })
            return
        }

        if (player.isOnline) player.sendPluginMessage(plugin, ULSProtocol.CHANNEL_SERVER, payload)
    }

    /**
     * 读取插件版本号。
     *
     * `Plugin#getDescription()` 在新版 Bukkit 已废弃，取而代之的是
     * `getPluginMeta()`；但后者在较老的 API 上不存在。
     * 这里用反射探测一次并缓存，避免为了一个版本字符串
     * 把插件绑死在某个具体的 Bukkit 版本上。
     */
    private fun pluginVersion(plugin: org.bukkit.plugin.Plugin): String {
        cachedVersion?.let { return it }
        val version = try {
            val meta = plugin.javaClass.getMethod("getPluginMeta").invoke(plugin)
            meta.javaClass.getMethod("getVersion").invoke(meta) as? String
        } catch (_: Throwable) {
            null
        } ?: plugin.description.version
        cachedVersion = version
        return version
    }

    private var cachedVersion: String? = null

    /**
     * 当前服务端愿意下发给客户端的 UI 能力开关。
     *
     * ## 为什么是一张「全部声明」的表
     *
     * 这里声明的是**服务端这边已经实现**的能力。客户端拿到之后会与
     * 自己支持的取交集——它认识哪些组件由它自己决定（老客户端不认识
     * 新组件，那就少渲染那一块）。
     *
     * 新增可选组件时只需更新能力表和模组的 ULSMenuElementType，
     * 不必抬协议版本；但非兼容线格式变动（菜单 nonce）必须双方同步升版。
     *
     * ## 逐项说明
     *
     * - `CAP_MENU`：菜单系统总开关。客户端没有它就完全不渲染菜单。
     * - 其它：每项对应一类组件。没有对应能力的菜单会整体降级到经典轨，
     *   而不是发一个残缺的增强菜单（缺按钮的菜单比箱子界面更难用，
     *   而且会让玩家以为功能坏了）。
     *
     * 尚未实现的组件（`ui.checkbox` / `ui.textfield` / `ui.slot` /
     * `ui.chart`）**先不下发**——声明了但行为不完整，
     * 比声明得少更糟：客户端会渲染出一个点了没反应的控件。
     *
     * `ui.scroll` 是下发的：模组侧的滚动容器是**实现完整**的
     * （布局阶段就应用滚动偏移，绘制与命中测试用的是同一份坐标）。
     * 曾经它被列在「尚未实现」里，但那个判断已经过时了——
     * 把一块能力留在名单外不是「保守」，而是让模组侧新修好的
     * 滚动逻辑永远不生效。
     */
    private fun currentCapabilities(): Map<String, Boolean> = linkedMapOf(
        ULSProtocol.CAP_MENU to true,
        ULSProtocol.CAP_TEXT to true,
        ULSProtocol.CAP_SHAPE to true,
        ULSProtocol.CAP_BUTTON to true,
        ULSProtocol.CAP_IMAGE to true,
        ULSProtocol.CAP_GRID to true,
        ULSProtocol.CAP_TOOLTIP to true,
        ULSProtocol.CAP_PROGRESS to true,
        ULSProtocol.CAP_SCROLL to true,
        // 以下留待后续阶段，实现后再打开：
        // ULSProtocol.CAP_CHECKBOX to true,
        // ULSProtocol.CAP_TEXTFIELD to true,
        // ULSProtocol.CAP_SLOT to true,
        // ULSProtocol.CAP_CHART to true,
    )

    /** 该玩家是否已经完成过一次握手。 */
    private fun handshakeRecorded(uuid: UUID): Boolean = sessions.containsKey(uuid)

    /**
     * 把剩余的握手载荷读完并丢弃。
     *
     * 限流命中时我们已经决定不更新记录，但仍然要消费掉字节流——
     * 否则 `onPluginMessageReceived` 末尾的「剩余字节」校验会误报，
     * 掩盖真正的协议错位问题。同时也能验证载荷本身是否完整。
     */
    private fun drainHandshakePayload(input: DataInputStream) {
        readVarInt(input)                                  // protocolVersion
        readString(input, ULSProtocol.MAX_ID_LEN)          // modVersion
        readString(input, 32)                              // gameVersion
        readString(input, 32)                              // javaVersion
        readString(input, 64)                              // osName
        readString(input, 32)                              // osArch
        readString(input, 32)                              // reportedUsername
        readString(input, 16)                              // locale
        readBoolean(input)                                 // hasULSHelper
        readVarInt(input)                                  // modCount
        val modSize = readVarInt(input)
        if (modSize !in 0..ULSProtocol.MAX_REPORTED_MODS) throw java.io.IOException("mod 列表数非法: $modSize")
        for (i in 0 until modSize) {
            readString(input, ULSProtocol.MAX_ID_LEN)
            readString(input, ULSProtocol.MAX_ID_LEN)
        }
    }

    // ==================== 校验 ====================
    /**
     * 握手校验。返回 [ULSProtocol] 中的状态码。
     *
     * 注意：**这里永远不返回 REJECTED**。纯插件环境 + 可选客户端的前提是
     * 服务端不能因为客户端模组的版本而拒绝玩家。版本不匹配时返回
     * VERSION_MISMATCH，客户端会自行降级到原版 UI。
     */
    private fun validate(player: Player, clientProtocol: Int): Int {
        if (clientProtocol < ULSProtocol.MIN_SUPPORTED_PROTOCOL_VERSION ||
            clientProtocol > ULSProtocol.PROTOCOL_VERSION
        ) {
            return ULSProtocol.STATUS_VERSION_MISMATCH
        }
        return ULSProtocol.STATUS_OK
    }

    // ==================== 查询 API ====================

    /** 当前握手轮次，仅供菜单协议隔离排队中的旧会话下发。 */
    internal fun handshakeGeneration(player: Player): Long? = handshakeEpoch.snapshot(player.uniqueId)

    /** 该玩家是否使用本模组并已完成握手。 */
    fun isUsingMod(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    /** 该玩家是否使用本模组并已完成握手。 */
    fun isUsingMod(uuid: UUID): Boolean = sessions.containsKey(uuid)

    /** 取客户端记录，未使用模组返回 null。 */
    fun getRecord(player: Player): ULSClientRecord? = sessions[player.uniqueId]

    /** 取客户端记录，未使用模组返回 null。 */
    fun getRecord(uuid: UUID): ULSClientRecord? = sessions[uuid]

    /** 当前在线且已完成握手的客户端数量。 */
    fun count(): Int = sessions.size

    /** 所有已记录的客户端（只读快照）。 */
    fun all(): Collection<ULSClientRecord> = sessions.values.toList()

    /** 玩家是否安装了某个指定 mod。 */
    fun clientHasMod(player: Player, modId: String): Boolean =
        sessions[player.uniqueId]?.hasMod(modId) ?: false

    /** 某一项能力是否已下发给该玩家（即该玩家是否能看到对应的增强 UI）。 */
    fun clientSupports(player: Player, capability: String): Boolean {
        val record = sessions[player.uniqueId] ?: return false
        return record.protocolVersion >= ULSProtocol.MIN_SUPPORTED_PROTOCOL_VERSION &&
                currentCapabilities()[capability] == true
    }

    /** 清理所有状态。用于 onDisable / reload。 */
    fun clear() {
        sessions.clear()
        lastUpdate.clear()
        handshakeEpoch.clear()
    }

    /**
     * 清理单个玩家的记录，等待其重新上报。
     *
     * 注意：单纯清理服务端缓存不会触发客户端重新握手；只有客户端
     * 主动重新连接或再次发送握手才能恢复增强能力。
     */
    fun clearPlayer(player: Player) {
        // 会话重置时旧增强菜单不能在重握手后复活；经典轨仍由打开的箱子事件负责。
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.invalidateEnhanced(player)
        sessions.remove(player.uniqueId)
        lastUpdate.remove(player.uniqueId)
        handshakeEpoch.invalidate(player.uniqueId)
    }

    // ==================== 底层编解码 ====================
    //
    // Minecraft 的 PacketByteBuf 用的是 VarInt 前缀 + UTF-8 的字符串格式，
    // 插件侧没有这个类，所以这里手写一份兼容实现。
    // VarInt：低 7 位一组，最高位表示「还有后续字节」。

    private fun readVarInt(input: DataInputStream): Int {
        var value = 0
        var position = 0
        while (true) {
            val b = input.readByte().toInt()
            value = value or ((b and 0x7F) shl position)
            if (b and 0x80 == 0) return value
            position += 7
            if (position >= 35) throw java.io.IOException("VarInt 过长")
        }
    }

    private fun writeVarInt(out: java.io.DataOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.writeByte(v and 0x7F)
    }

    private fun readString(input: DataInputStream, maxLength: Int): String {
        val length = readVarInt(input)
        if (length < 0 || length > maxLength * 3) {
            throw java.io.IOException("字符串长度非法: $length")
        }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val value = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if (value.length > maxLength) throw java.io.IOException("字符串字符数超限: ${value.length}")
        return value
    }

    private fun writeString(out: java.io.DataOutputStream, value: String, maxLength: Int) {
        val bytes = value.take(maxLength).toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readBoolean(input: DataInputStream): Boolean = input.readBoolean()
}
