package io.github.zzzyyylllty.chotentech.client

/**
 * 客户端模组（ULSHelperMod）与插件之间的通信协议常量。
 *
 * ## 契约声明
 *
 * 本文件是 [cn.gtemc.ulshelper.network.ULSProtocol] 的**逐字镜像**。
 * 任何一侧修改常量都必须同步另一侧，否则握手会静默失败。
 * 修改时请同时改动：
 *
 * - 客户端：`E:\ULSHelperMod\src\main\java\cn\gtemc\ulshelper\network\ULSProtocol.java`
 * - 插件：本文件
 *
 * ## 前提
 *
 * 服务器是**纯插件**（Bukkit/Paper）环境。客户端模组**完全可选**：
 * 未安装模组的玩家走原版路径，插件不得因缺少握手而拒绝服务、
 * 也不得因握手失败而踢出玩家。所有相关功能都必须能安全降级。
 *
 * ## 线格式（重要，改动前务必读完）
 *
 * 客户端使用 Fabric 1.21.4 的 `CustomPayload` 体系，载荷形状是：
 *
 * ```
 * ULSPayloads.C2S = PacketCodec.tuple(string(MAX_ID_LEN), BYTE_ARRAY, ...)
 * ```
 *
 * 也就是说，Bukkit 的 [org.bukkit.plugin.messaging.PluginMessageListener]
 * 收到的字节是：
 *
 * ```
 * VarInt(封包ID长度) + 封包ID(UTF-8) + VarInt(载荷体长度) + 载荷体
 * ```
 *
 * **那个「载荷体长度」前缀是 `PacketCodecs.BYTE_ARRAY` 自己写的**，
 * 不是协议的一部分，但它确实在线上。读封包 ID 之后必须消费掉它，
 * 否则后续所有字段都会错位——而错位的表现是「字段全是乱码」，
 * 不会抛异常，极难排查。发送方向同理，必须手工写出这个长度前缀。
 *
 * 已由 [ULSClientManager] 的 `sendToClient` 与本文件注释固化。
 */
object ULSProtocol {

    // ==================== 通道 ====================

    /** 主握手通道：客户端 → 服务端。 */
    const val CHANNEL_MAIN = "chotentech:uls"

    /** 应答通道：服务端 → 客户端。 */
    const val CHANNEL_SERVER = "chotentech:uls_s2c"

    /** 本插件支持的协议版本。 */
    const val PROTOCOL_VERSION = 2

    /** 可接受的最低协议版本。 */
    const val MIN_SUPPORTED_PROTOCOL_VERSION = 2

    // ==================== 封包 ID ====================

    /** 客户端 → 服务端：握手与环境上报。 */
    const val PKT_HANDSHAKE = "handshake"

    /** 客户端 → 服务端：环境变更后重新上报。 */
    const val PKT_ENV_UPDATE = "env_update"

    /** 客户端 → 服务端：心跳。 */
    const val PKT_PING = "ping"

    /** 服务端 → 客户端：握手应答。 */
    const val PKT_HANDSHAKE_ACK = "handshake_ack"

    /** 服务端 → 客户端：心跳应答。 */
    const val PKT_PONG = "pong"

    /** 服务端 → 客户端：能力下发。 */
    const val PKT_CAPABILITIES = "capabilities"

    /** 服务端 → 客户端：下发一个菜单的描述。 */
    const val PKT_MENU_OPEN = "menu_open"

    /** 服务端 → 客户端：增量更新菜单变量（不重发结构）。 */
    const val PKT_MENU_DATA = "menu_data"

    /** 服务端 → 客户端：关闭当前菜单。 */
    const val PKT_MENU_CLOSE = "menu_close"

    /** 客户端 → 服务端：某个动作被触发。 */
    const val PKT_ACTION = "action"

    /** 客户端 → 服务端：玩家主动关闭增强菜单，载荷为菜单 ID 与当前 nonce。 */
    const val PKT_MENU_DISMISS = "menu_dismiss"

    // ==================== 能力标识 ====================
    //
    // 新增可选 UI 组件类型以 capability 宣告，勿因此抬版本号，
    // 否则老客户端会失去全部增强 UI。只有更改现有封包的非兼容线格式
    // （如版本 2 为菜单包新增 nonce）才必须同步提升协议版本。

    /** 菜单系统总开关。未下发此能力时客户端不渲染任何菜单。 */
    const val CAP_MENU = "menu"
    const val CAP_TEXT = "ui.text"
    const val CAP_SHAPE = "ui.shape"
    const val CAP_BUTTON = "ui.button"
    const val CAP_IMAGE = "ui.image"
    const val CAP_GRID = "ui.grid"
    const val CAP_TOOLTIP = "ui.tooltip"
    const val CAP_CHECKBOX = "ui.checkbox"
    const val CAP_TEXTFIELD = "ui.textfield"
    const val CAP_SLOT = "ui.slot"
    const val CAP_SCROLL = "ui.scroll"
    const val CAP_PROGRESS = "ui.progress"
    const val CAP_CHART = "ui.chart"

    /** 资源包驱动的界面皮肤。由客户端内置兜底外观渲染之外的另一条路径。 */
    const val CAP_UI_SKIN = "ui.skin"

    /** 资源包驱动的字体图标。 */
    const val CAP_UI_ICON_FONT = "ui.iconfont"

    /**
     * 菜单结构来自资源包 JSON（assets/uls/menu/<menuId>.json）。
     * 未下发时服务端必须退回二进制的 menu_open 元素树路径。
     */
    const val CAP_MENU_JSON = "ui.menu_json"

    // ==================== 状态码 ====================

    /** 校验通过。 */
    const val STATUS_OK = 0

    /** 协议版本不兼容。 */
    const val STATUS_VERSION_MISMATCH = 1

    /** 服务端侧功能关闭。 */
    const val STATUS_DISABLED = 2

    /** 客户端被拒绝。 */
    const val STATUS_REJECTED = 3

    /** 服务端内部错误。 */
    const val STATUS_INTERNAL_ERROR = 4

    // ==================== 限制 ====================

    /** 单次封包最大字节数。超限直接丢弃，不解析。 */
    const val MAX_PAYLOAD_BYTES = 24 * 1024

    /** mod 列表最大条数。 */
    const val MAX_REPORTED_MODS = 256

    /** 环境重新上报的最小间隔（毫秒）。 */
    const val ENV_UPDATE_MIN_INTERVAL_MS = 30_000L

    /** 心跳间隔（毫秒）。 */
    const val PING_INTERVAL_MS = 10_000L

    /** 字符串字段上限，用于防御性读取。 */
    const val MAX_STRING_LEN = 512

    /** 单个服务器标识串上限。 */
    const val MAX_ID_LEN = 64

    /**
     * 服务端标识，随握手应答下发给客户端，
     * 用于客户端日志与后续版本判定。
     */
    const val SERVER_ID = "ChoTenTech"

    // ==================== 菜单限额 ====================
    //
    // 这些都是**防护性**上限，不是功能规格。客户端解析时用同样的值兜底，
    // 保证一个畸形或恶意的菜单描述不会让客户端 OOM 或卡死。
    // 服务端构造菜单时也应当自行遵守更严格的预算。

    /** 单个菜单允许的最大元素数（含嵌套子元素）。 */
    const val MAX_MENU_ELEMENTS = 512

    /** 元素树的嵌套深度上限。 */
    const val MAX_MENU_DEPTH = 16

    /** 单个元素允许的最大属性数。 */
    const val MAX_ELEMENT_PROPERTIES = 64

    /** 属性名 / 属性值字符串长度上限。 */
    const val MAX_PROPERTY_LEN = 256

    /** 单个菜单允许的最大变量数。 */
    const val MAX_MENU_VARIABLES = 256

    /** 文本类元素的文本长度上限（字符）。 */
    const val MAX_TEXT_LEN = 1024

    /** 变量名长度上限。 */
    const val MAX_VAR_NAME_LEN = 64

    /** 动作 ID 长度上限。 */
    const val MAX_ACTION_ID_LEN = 64

    /** tooltip 最大行数。 */
    const val MAX_TOOLTIP_LINES = 16

    /**
     * 服务端处理动作封包的最小间隔（毫秒）。
     *
     * 客户端也有一份同样的限流，但那只是礼貌，**不能作为安全措施**——
     * 客户端可以被改写。真正必须生效的是服务端这一份。
     */
    const val ACTION_MIN_INTERVAL_MS = 100L

    /** 单个玩家每秒允许的动作数上限（滑动窗口，服务端强制）。 */
    const val ACTION_MAX_PER_SECOND = 10
}
