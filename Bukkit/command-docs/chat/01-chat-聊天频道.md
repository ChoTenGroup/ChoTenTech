# 聊天频道（/chat）

> **根指令**：`/chat`（别名：`/ch`, `/channel`）
> **权限**：`chotentech.command.chat`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/chat/ChatCommands.kt`

## 功能概述

面向玩家自己的频道工具：查看服务器已加载的全部聊天频道、把「当前活跃频道」切换成另一个频道，或绕过切换直接以某个频道的格式/范围发一条消息。
本指令只做频道选择与转发，真正的发送校验（使用权限、组队要求、冷却、敏感词过滤、占位符、@ 提及、收件人范围）全部由 `ChatDispatcher.prepare/send` 完成。
玩家的活跃频道会持久化到数据库表 `chat_player_data` 的 `activeChannel` 字段。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chat` | 列出所有频道与当前频道 | `chotentech.command.chat` |
| `/chat list` | 同上，等价于无参执行 | `chotentech.command.chat` |
| `/chat <channel>` | 切换到指定频道（写入活跃频道） | `chotentech.command.chat`（+ 频道自身 `permission`） |
| `/chat <channel> <message>` | 不切换频道，直接以该频道发送一条消息 | `chotentech.command.chat`（+ 频道自身 `permission` 等发送校验） |

> 表中的 `<channel>` 对应源码 `dynamic("channel")`，`<message>` 对应 `dynamic("message")`，两者都不会出现在 Tab 补全里（源码未声明 `suggestion`）。

## 指令详解

### `/chat`（以及 `/chat list`）

- **用途**：展示频道清单与当前频道，帮助玩家知道自己处在哪个频道、还能用哪些频道。
- **参数**：无。
- **权限**：`chotentech.command.chat`（默认所有人）。
- **执行者要求**：**仅玩家**（`execute<Player>`，控制台执行会被 TabooLib 直接拒绝）。
- **行为说明**：
  - 读取 `ChatPlayerDataManager.get(sender)` 的 `activeChannel` 作为当前频道 key；若该 key 在 `ChannelManager` 中已不存在（例如频道 yml 被删/改名），回退显示 `ChannelManager.defaultChannel()`。
  - 输出标题行 `===== 聊天频道 =====`，随后一行 `当前频道: <显示名>`，未找到时显示「无」。
  - 遍历 `ChannelManager.all()`（即 `channels` 目录下加载到的全部频道，加载顺序按文件名排序），每个频道输出一行：`●`（当前频道）/`○`（其他频道）、`displayName`、`(key)`；若该频道配置了 `quick-prefix` 且非空，追加 `前缀: <quickPrefix>`；若该频道配置了 `permission` 且执行者没有该权限，追加红色 `[无权限]` 标记。
  - 最后一行用法提示：`/ch <频道>` 切换 · `/ch <频道> <消息>` 直接发送。
  - 纯只读，不修改任何数据。
- **示例**：`/ch` → 打印频道列表，能一眼看出「组队频道可用/无权限」「当前是 global」。

### `/chat <channel>`

- **用途**：把玩家的活跃频道切换为 `<channel>`。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `channel` | 频道的 **key**（不是显示名），对应频道 yml 里的 `key` 字段 | 必填 | 无源码级补全；取值来自 `channels/*.yml` 的 `key` |

- **权限**：`chotentech.command.chat`；此外若频道配置了 `permission`，玩家必须持有该权限，否则切换失败。
- **执行者要求**：**仅玩家**（需要持久化 `ChatPlayerData`）。
- **行为说明**：
  - `ChannelManager.get(channelKey)` 找不到 → 提示「频道 `<key>` 不存在。使用 `/ch` 查看可用频道。」并中止。
  - 频道 `party = true` 且 `TeamManager.getTeam(sender.uniqueId) == null` → 提示「组队频道需要先加入队伍。」并中止（**切换时就要检查**，不必等到发消息）。
  - 频道 `permission` 非空且玩家没有该权限 → 提示「你没有使用 `<显示名>` 频道的权限。」并中止。
  - 全部通过后写入 `ChatPlayerData.activeChannel = channel.key`，调用 `ChatPlayerDataManager.save(sender.uniqueId)` **异步落库**（首次 insert，之后 update），并提示「已切换到频道 `<显示名>`。」。
  - 注意：频道名（显示名）与 key 不同，切换必须用 key。
- **示例**：`/ch global` → 当前频道变为 global，并收到「已切换到频道 …。」提示。

### `/chat <channel> <message>`

- **用途**：一次性用某个频道说话，但**不改变**当前活跃频道。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `channel` | 频道 key | 必填 | 同上一节 |
  | `message` | 消息正文（单段动态参数；多词是否可含空格取决于 TabooLib 的 `dynamic` 行为） | 必填 | 无 |

- **权限**：`chotentech.command.chat`；发送时还会校验频道 `permission`、组队要求、频道冷却等（见下）。
- **执行者要求**：**仅玩家**。
- **行为说明**：
  - 该分支**只**做一件事：`ChannelManager.get(channelKey)`，为 null 时提示「频道 `<key>` 不存在。」并返回；否则调用 `ChatDispatcher.send(sender, channel, message)`。
  - 因为绕过了 `switch`，所以本分支**不会**写 `activeChannel`；但成功发送**不会**回退当前频道，玩家频道状态保持不变。
  - 真正的校验发生在 `ChatDispatcher.prepare` 内，顺序为：
    1. 频道 `permission`（提示「你没有使用 `<显示名>` 频道的权限。」）；
    2. `party` 频道必须在队伍中（提示「你不在队伍中，无法使用组队频道。」）；
    3. 频道冷却（`ChatChannel.effectiveCooldown`：先看全局 `chotentech.chat.cooldown.bypass`，再看频道级 `chotentech.chat.cooldown.<key>.bypass`，再看 `cooldown-overrides` 中按配置顺序第一条命中的权限，最后用 `cooldown`；返回 -1 表示无冷却）。冷却中提示剩余秒数（保留一位小数）。
    4. 敏感词过滤 `TabooFilter.filter`，过滤后为空则**静默返回**（不发任何提示）。
    5. 占位符解析 `ChatPlaceholder.resolve`（如 `[item]` → 主手物品名）、`@` 提及解析 `ChatMention.parse`。
  - 发送成功后：给每个收件人发送按查看者渲染的组件（被提及者黄色高亮），控制台输出频道控制台格式行（`formats.console`），并额外提醒被提及的玩家。发送者自己也一定会收到自己的消息。
  - 收件人范围由频道决定（`radius`）：`-1` 全服、`0` 同世界、`>0` 同世界且距离平方 ≤ `radius²`；`party` 频道仅队伍成员。**屏蔽了发送者的玩家不会收到**。
  - 成功发送会刷新该玩家在该频道的冷却时间戳（内存中的 `cooldowns`，按 玩家UUID + 频道key 记录）。
- **示例**：`/ch global 大家好` → 以 global 频道格式向 global 频道收件人广播，当前频道不变。

## 备注 / 注意事项

- **依赖配置**：频道来自插件数据目录下的 `channels/` 文件夹（每个 `*.yml` 一个频道），启动时由 `ChannelManager.init()` 释放默认资源并按文件名排序加载。频道 yml 支持的键（见 `ChatChannel.load`）：
  - `key`（必填，缺失时加载失败并打印 warning）
  - `display-name`（缺省回退为 `key`）
  - `formats.<分组>`（MiniMessage；`default` 为回退格式，`console` 为控制台格式；玩家持有 `chotentech.chat.format.<分组>` 权限则使用该分组）
  - `quick-prefix`（如 `.g`，供聊天输入前缀快速匹配，见 `ChannelManager.matchQuickPrefix`）
  - `radius`（默认 `-1.0`）、`cooldown`（默认 `-1.0`）、`cooldown-overrides.<权限节点>`
  - `party`（默认 `false`）、`permission`（默认空，即不限制）
- **默认频道**：`ChannelManager.defaultKey` 初始为 `global`；重载后若 `global` 不存在且频道列表非空，则自动取第一个加载到的频道 key 作为默认。
- **控制台不能执行**：本文件所有 `execute<Player>` 均为仅玩家，控制台执行 `/chat` 会被框架拒绝且 `permissionMessage` 为空（不会给出提示文本）。
- **数据持久化**：切换频道会写入数据库表 `chat_player_data`（`activeChannel`）；数据库位置取自 `config.yml` 的 `database` 段（`chotentech.db`）。写入是异步的，玩家退出时还会同步保存一次。
- **易错点**：`/chat <channel>` 的 `<channel>` 是 key；用显示名会被判为「频道不存在」。`/chat <channel> <message>` 不会切换频道，容易与 `/chat <channel>` 混淆。
- **静默失败**：消息被敏感词过滤成空白时**没有任何提示**，表现为「命令没反应」，排查时应先检查 `filter.yml` 规则。

---

## 附：同文件涉及的根指令

本文件只定义 `/chat`（含别名 `/ch`、`/channel`）一个根指令；`list` 是其唯一字面量子命令。
