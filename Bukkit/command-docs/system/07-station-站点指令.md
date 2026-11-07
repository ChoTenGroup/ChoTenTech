# 站点指令（/chotentech-station）

> **根指令**：`/chotentech-station`（别名：`/cttstation`, `/station`）
> **权限**：**header 未声明权限节点**（`@CommandHeader` 中没有 `permission` / `permissionDefault`，沿用 TabooLib 框架默认）；`reload` 子指令在运行期另行校验 `chotentech.admin`
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/StationCommand.kt`

## 功能概述

合成站（Station）系统的指令入口。合成站配置由 `StationManager.stations`（`linkedMapOf<String, StationConfig>`）承载，通过 `StationManager.loadAll()` 从配置加载；本指令只负责：

- 打开某个合成站的界面（`StationMenu`）；
- 让管理员重载合成站配置。

界面内的合成/队列逻辑在 `StationMenu` / `StationSession` 内，不属于本指令范围。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-station`（`/cttstation`, `/station`） | 无参数：打印指令树（`createModernHelper()`） | 未声明 |
| `/chotentech-station open <stationId>` | 打开指定合成站（给自己） | 未声明 |
| `/chotentech-station open <stationId> <player>` | 为指定在线玩家打开指定合成站 | 未声明 |
| `/chotentech-station reload` | 重载合成站配置 | **运行期校验 `chotentech.admin`** |

> 该 `@CommandHeader` **没有** `permission` 与 `permissionDefault` 字段，只有 `name` / `aliases` / `description = "合成站系统"` / `newParser = true`。

## 指令详解

### `/chotentech-station`（无参数）

- **用途**：打印分层指令帮助。
- **参数**：无。
- **权限**：未声明（继承框架默认）。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` → `execute<ProxyCommandSender>`）。
- **行为说明**：`main = mainCommand { createModernHelper() }`，按执行者权限过滤节点。注意 `newParser = true`（本文件是少数启用新解析器的指令）。
- **示例**：`/station`

### `/chotentech-station open <stationId>`

- **用途**：打开指定合成站的界面。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `stationId` | 合成站 ID，对应 `StationManager.stations` 的键 | 必填 | `StationManager.stations.keys.toList()`（`suggestion<CommandSender>(uncheck = true)`） |

- **权限**：未声明。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：
  - `StationMenu.open(sender, stationId)`；
  - `open` 内部先 `StationManager.getStation(stationId)`，找不到 → 向玩家发「合成站 `<stationId>` 不存在」并返回；
  - 找到则取（或创建）`StationSession`，把 `stationId` 写入会话、`view = RECIPES`、`currentPage = 0`，然后打开可翻页箱子界面。
- **示例**：`/station open workbench`

### `/chotentech-station open <stationId> <player>`

- **用途**：替指定在线玩家打开指定合成站。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `stationId` | 合成站 ID | 必填 | `StationManager.stations.keys` |
  | `player` | 目标玩家（必须在线） | 必填 | TabooLib `player("player")` 自动补全在线玩家名 |

- **权限**：未声明。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - `Bukkit.getPlayerExact(targetName)` 为 null → 「玩家 `<targetName>` 不在线」；
  - 否则 `StationMenu.open(target, stationId)` —— 界面开在**目标玩家**身上；合成站不存在时同样由 `StationMenu.open` 提示目标玩家。
- **示例**：`/station open workbench Steve`

### `/chotentech-station reload`

- **用途**：重载合成站配置（`stations` 目录的 yml）。
- **参数**：无。
- **权限**：**运行期校验 `chotentech.admin`**——`if (!sender.hasPermission("chotentech.admin"))` 则「§c你没有权限执行此命令」并返回。header 未声明权限节点，因此真正的门禁就是这条 `hasPermission`。
- **执行者要求**：玩家 / 控制台均可（`execute<org.bukkit.command.CommandSender>`）。
- **行为说明**：调用 `StationManager.loadAll()` 重新加载全部合成站，成功后输出「§a[Station] 合成站配置已重载! 共 `<N>` 个」（`N = StationManager.stations.size`）。
- **示例**：`/station reload`

## 备注 / 注意事项

- **没有声明权限节点是本指令最需要注意的点**：`@CommandHeader` 里既无 `permission` 也无 `permissionDefault`，TabooLib 因此不会给它绑定根权限；`open`（含替他人打开）的可用性完全取决于框架默认行为与服务器权限插件配置。若要把 `open` 限制给玩家，需要自行在权限插件侧限制或修改源码补 `permission`。
- **唯一的显式权限检查在 `reload` 内部**，用的是 `chotentech.admin`（注意**不是** `chotentech.command.station` 之类的前缀）。
- **`newParser = true`**：本指令启用了 TabooLib 新解析器（同文件 `import taboolib.common.platform.command.*` 通过全限定名调用 `org.bukkit.command.CommandSender` 也是因为新解析器签名）。
- **`open` 的第一个分支是 `execute<Player>`**：控制台执行 `/station open <id>` 会被框架拒绝；只有带 `<player>` 的形式才能由控制台使用。
- **`<stationId>` 的补全对所有人可见**（`uncheck = true` 表示不做权限校验的补全），不会泄露额外业务信息，但会暴露全部站点的 ID。
- **重载只影响合成站配置**：`StationManager.loadAll()` 只重建 `stations`，不重载物品/配方等其它子系统；已经打开界面的玩家所持有的 `StationSession` 不会因为重载而被清理。
- 本指令**不会**出现在 `/ctt` 子指令树下（`MainCommand` 没有把它挂上去），只能通过 `chotentech-station` / `cttstation` / `station` 三个名字进入。
