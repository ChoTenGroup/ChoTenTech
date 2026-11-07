# 客户端信息（/ulsclient）

> **根指令**：`/ulsclient`（别名：`/uls`）
> **权限**：`chotentech.uls.client`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/client/ULSClientCommand.kt`

## 功能概述

ULS（阈限天际客户端助手模组）的排障入口：查看当前有多少玩家完成了客户端握手、列出这些玩家的设备/模组信息，或清掉某个玩家的服务端记录以等待重新上报。
面向管理与排障，默认仅 OP；玩家反馈「装了模组但没效果」时应先用它确认服务端是否真的收到了握手。
协议版本、通道名、能力下发等由 `ULSProtocol` / `ULSClientManager` 提供。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/ulsclient` | 概览：握手人数、协议版本、主通道 | `chotentech.uls.client` |
| `/ulsclient list` | 列出全部已连接模组客户端的简要信息 | `chotentech.uls.client` |
| `/ulsclient info <player>` | 查看单个玩家的详细信息与完整 mod 列表 | `chotentech.uls.client` |
| `/ulsclient reload <player>` | 清空该玩家的服务端侧客户端记录 | `chotentech.uls.client` **+ `chotentech.uls.admin`**（运行时校验） |

> `reload` 的额外权限是在 `execute` 内部用 `sender.hasPermission("chotentech.uls.admin")` 运行时检查的（源码注释说明：TabooLib 的 `subCommand { }` 不支持 `permission =` 参数，因此改为运行时自行校验）。该节点**不会**出现在 Tab 补全或框架的权限拦截中。

## 指令详解

### `/ulsclient`

- **用途**：一眼确认「服务端到底收到几个客户端握手」，以及当前协议版本与通道名。
- **参数**：无。
- **权限**：`chotentech.uls.client`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**（`execute<CommandSender>`）。
- **行为说明**：
  - 输出 `[ULS] 当前在线且完成握手的客户端: <count> 人`（`ULSClientManager.count()`，即 `sessions` 表大小）。
  - 输出 `协议版本: <PROTOCOL_VERSION>, 通道: <CHANNEL_MAIN>`；当前源码常量为协议版本 `2`、通道 `chotentech:uls`。
  - `count == 0` → 追加「没有玩家使用客户端模组。这在纯插件环境下是正常的。」（避免误判为故障）。
  - `count > 0` → 追加「使用 `/ulsclient list` 查看详情。」
- **示例**：`/uls` → 显示 `当前在线且完成握手的客户端: 3 人`。

### `/ulsclient list`

- **用途**：列出所有已完成握手的模组客户端。
- **参数**：无。
- **权限**：`chotentech.uls.client`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**。
- **行为说明**：
  - 记录为空 → 输出「当前没有已连接的模组客户端。」
  - 否则输出标题行「已连接的模组客户端 (`数量`)」，然后每个玩家两行：
    - 第一行：`- <name> | <modVersion> | MC <gameVersion>`
    - 第二行：`Java <javaVersion> | <osName>/<osArch> | 语言 <locale> | mod <mods.size>`；若 `modsTruncated` 为 true，追加 `(+<modCount - mods.size>)` 表示被裁剪掉的模组数量。
    - 若 `usernameMismatch` 为 true，再加一行黄色告警：`注意: 客户端自述用户名 '<reportedUsername>' 与登录名不符`。
  - 遍历顺序为 `sessions` 值集合的顺序（按玩家 UUID 插入，不保证字母序）。
- **示例**：`/uls client list` → 逐条打印每个模组玩家的版本与设备信息。

### `/ulsclient info <player>`

- **用途**：查看某名玩家的完整客户端详情，含全部（未裁剪的）mod 列表，用于核对模组版本差异。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 在线玩家名（`dynamic("player")`，源码显式提供 `suggestion`） | 必填 | `Bukkit.getOnlinePlayers()` 的玩家名 |

- **权限**：`chotentech.uls.client`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**。
- **行为说明**：
  - 用 `Bukkit.getPlayerExact(context["player"])` 精确匹配在线玩家，再取 `ULSClientManager.getRecord(player)`；任一为 `null`（玩家不在线，或未握手）→ 输出「该玩家未使用客户端模组，或尚未完成握手。」并返回。
  - 否则逐行输出：玩家名、连接地址（`address`）、协议版本（`protocolVersion`）、模组版本（`modVersion`）、游戏版本（`gameVersion`）、Java（`javaVersion`）、系统（`osName/osArch`）、语言（`locale`）、自述用户名（`reportedUsername`，若 `usernameMismatch` 追加黄色「(与登录名不符)」）。
  - 最后输出 mod 列表标题（含条数；若 `modsTruncated` 追加 `+ <modCount - mods.size> 已裁剪`），然后把 mod 条目**按 mod id 字母序排序**、每 4 个一行输出为 `id@版本`。
- **示例**：`/uls info Steve` → 打印 Steve 的协议/模组版本与完整 mod 清单。

### `/ulsclient reload <player>`

- **用途**：清理某名玩家在服务端缓存的 ULS 客户端记录，用于排障（例如客户端上报了错误数据，想让服务端重新收一次）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 在线玩家名（带玩家名补全） | 必填 | 在线玩家名 |

- **权限**：`chotentech.uls.client`（根指令权限）**且**运行时必须有 `chotentech.uls.admin`。
- **执行者要求**：**玩家 / 控制台均可**（但必须持有 admin 权限）。
- **行为说明**：
  - 没有 `chotentech.uls.admin` → 输出「你没有权限执行此操作。」并返回。
  - `Bukkit.getPlayerExact(name)` 为 `null` → 输出「找不到在线玩家 `<name>`」并返回。
  - 调用 `ULSClientManager.clearPlayer(player)`：**先 `ULSMenuManager.invalidateEnhanced(player)`**（防止旧的增强菜单在重握手后复活），再清除 `sessions` / `lastUpdate` / `handshakeEpoch` 三项记录。
  - 通过 `submit { infoS(...) }` 在主线程向**服务端日志**打印「已清理 `<玩家名>` 的 ULS 客户端记录，等待其重新上报。」，并给执行者输出「已清理 `<玩家名>` 的客户端记录。」。
  - **不会让客户端重新握手**：源码与 `clearPlayer` 注释都明确说明，只有客户端重连或主动再次发送握手包，增强能力才会恢复；该指令也不会「让客户端立刻重连」。
- **示例**：`/uls reload Steve` → Steve 的握手记录被清空，在下一次握手前他会被视为「未装模组」（菜单走经典轨）。

## 备注 / 注意事项

- **权限结构特殊**：`reload` 的 `chotentech.uls.admin` 是运行时检查，`permissionDefault` 未声明（TabooLib 默认不受理），因此该节点需由权限插件显式授予。默认 OP 若未通过权限插件拿到该节点，会被拒绝——具体行为取决于服务端权限实现。
- **`info` 与 `reload` 都严格按在线玩家名匹配**（`getPlayerExact`），大小写敏感，不能查离线玩家。
- **`modsTruncated` 的含义**：客户端上报的 mod 列表可能被裁剪，`modCount` 为真实总数；`list` 只显示已上报的 `mods.size` 与差额，`info` 的 mod 列表同样只含已上报部分。
- **`usernameMismatch`**：客户端自述用户名与登录名不一致时会在 `list` 与 `info` 中以黄色告警显示，这类情况通常意味着客户端伪造或代理。
- **记录来源与生命周期**：数据保存在 `ULSClientManager.sessions`（内存），由 `chotentech:uls` 通道上的握手包写入；玩家退出、`clear()`（onDisable/reload）会清除。**服务器重启后所有人需要重新握手**。
- **协议兼容性**：`clientSupports` 只有在 `record.protocolVersion >= MIN_SUPPORTED_PROTOCOL_VERSION`（当前为 `2`）且服务端能力表对应项为 `true` 时才算支持；协议高于 `PROTOCOL_VERSION` 的客户端会被视为不兼容。当前服务端下发的能力为 `menu`、`ui.text`、`ui.shape`、`ui.button`、`ui.image`、`ui.grid`、`ui.tooltip`、`ui.progress`、`ui.scroll`（`ui.checkbox`、`ui.textfield`、`ui.slot`、`ui.chart` 刻意不下发）。
- **与其他指令的关系**：查看菜单层面的问题请用 `/ulsmenu diagnose <玩家>`（本文件只管「握手有没有到、设备信息是什么」这一层）。
