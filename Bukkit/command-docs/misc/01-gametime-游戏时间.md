# 游戏时间

> **根指令**：`/gametime`（别名：`/gtm`, `/游戏时间`）
> **权限**：`chotentech.command.gametime`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/GameTimeCommand.kt`

## 功能概述

管理玩家的**游戏时间**（以"小时"为单位累积，存放在玩家数据的 `gameTimeHours` 字段中，随服务器真实时间自动增长）。本命令提供四个子命令：`add`（快进）、`remove`（倒退）、`set`（设为指定值）、`get`（查询并按 年/月/日/时 显示）。

时间格式化与换算由 `GameTimeManager` 提供：`formatOneLine(hours)` 输出形如 `2160.01.01 13:00` 的字符串，`getYear` / `getMonth` / `getDay` / `getHour` 分别换算年（基准 2150）、月、日、时。

该文件包含一个根命令 + 4 个 `@CommandBody subCommand`（`add` / `remove` / `set` / `get`），共 5 个命令体。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/gametime` | 输出帮助菜单（`createModernHelper()` 生成的现代帮助界面） | `chotentech.command.gametime` |
| `/gametime add <user> <amount>` | 为玩家快进指定小时数 | `chotentech.command.gametime` |
| `/gametime remove <user> <amount>` | 为玩家倒退指定小时数（下限 0） | `chotentech.command.gametime` |
| `/gametime set <user> <amount>` | 把玩家游戏时间设为指定小时数 | `chotentech.command.gametime` |
| `/gametime get <user>` | 查询玩家游戏时间（格式化文本 + 年月日时） | `chotentech.command.gametime` |

## 指令详解

### `/gametime`

- **用途**：不带参数执行时，由 `mainCommand { createModernHelper() }` 生成并显示该命令的帮助菜单。
- **参数**：无。
- **权限**：`chotentech.command.gametime`（`PermissionDefault.OP`，默认仅 OP）。
- **执行者要求**：玩家 / 控制台均可（帮助菜单由框架生成）。
- **行为说明**：仅调用 `createModernHelper()`，没有自定义 `execute` 逻辑。
- **示例**：
  ```
  /gametime
  /gtm
  ```

### `/gametime add <user> <amount>`

- **用途**：把目标玩家的游戏时间**快进** `amount` 小时。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `amount` | 增加的小时数（`dynamic`，按 `toLongOrNull()` 解析） | 必填 | `1`, `24`, `720`, `8760` |

- **权限**：`chotentech.command.gametime`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`，目标玩家由参数给出）。
- **行为说明**：
  1. 解析 `amount`：为 `null` 或 `< 1` 时提示 `<#E08A98>数量必须为正整数` 并返回。
  2. `player.getData()` 为 null 时提示 `<#E08A98>无法获取 <玩家名> 的数据`。
  3. `data.gameTimeHours += amount`。
  4. 用 `GameTimeManager.formatOneLine` 生成格式化时间，依次发送：`<#8FD4A6>已将 <玩家名> 的游戏时间快进 <amount> 小时`、`<#8FD4A6>当前: <格式化时间>`。
- **示例**：
  ```
  /gametime add Steve 24
  /gtm add Steve 8760
  ```

### `/gametime remove <user> <amount>`

- **用途**：把目标玩家的游戏时间**倒退** `amount` 小时。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `amount` | 减少的小时数（`dynamic`，按 `toLongOrNull()` 解析） | 必填 | `1`, `24`, `720`, `8760` |

- **权限**：`chotentech.command.gametime`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 校验规则与 `add` 相同：`amount == null || amount < 1` → `<#E08A98>数量必须为正整数`。
  2. 数据取不到 → `<#E08A98>无法获取 <玩家名> 的数据`。
  3. `data.gameTimeHours = maxOf(data.gameTimeHours - amount, 0L)`——**结果下限为 0**，不会出现负值。
  4. 提示：`<#8FD4A6>已将 <玩家名> 的游戏时间倒退 <amount> 小时` + `<#8FD4A6>当前: <格式化时间>`。
- **示例**：
  ```
  /gametime remove Steve 24
  /gtm remove Steve 720
  ```

### `/gametime set <user> <amount>`

- **用途**：把目标玩家的游戏时间**直接设**为 `amount` 小时。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `amount` | 目标小时数（`dynamic`，按 `toLongOrNull()` 解析） | 必填 | `0`, `8760`, `17520`, `26280` |

- **权限**：`chotentech.command.gametime`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 校验规则**与 add/remove 不同**：`amount == null || amount < 0` → `<#E08A98>数量必须为非负整数`（允许 `0`）。
  2. 数据取不到 → `<#E08A98>无法获取 <玩家名> 的数据`。
  3. `data.gameTimeHours = amount`。
  4. 提示：`<#8FD4A6>已将 <玩家名> 的游戏时间设为 <amount> 小时` + `<#8FD4A6>当前: <格式化时间>`。
- **示例**：
  ```
  /gametime set Steve 0
  /gtm set Steve 17520
  ```

### `/gametime get <user>`

- **用途**：查询目标玩家的游戏时间，并给出格式化日期与 年/月/日/时 明细。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |

- **权限**：`chotentech.command.gametime`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 数据取不到 → `<#E08A98>无法获取 <玩家名> 的数据`。
  2. 读取 `data.gameTimeHours`，调用 `formatOneLine`、`getYear`、`getMonth`、`getDay`、`getHour`。
  3. 依次发送三行：
     - `<#8FD4A6><玩家名> 的游戏时间:`
     - `<#8FD4A6>  <格式化时间>`
     - `<#8FD4A6>  总小时: <hours> | 年: <y> 月: <m> 日: <d> 时: <h>`
- **示例**：
  ```
  /gametime get Steve
  /gtm get Steve
  ```

## 备注 / 注意事项

- **`add` / `remove` 与 `set` 的校验条件不同**：前两者要求 `amount >= 1`（0 与负数被拒，提示"数量必须为正整数"），`set` 允许 `0`（提示"数量必须为非负整数"）。三条提示文案不同，不要混用。
- `remove` 使用 `maxOf(..., 0L)` 夹紧，减到 0 以下只会在 0 处停止，不报错。
- **`user` 参数是必填的**：本命令没有"默认作用于自己"的写法，必须显式给出玩家名。四个子命令都通过 `context.player("user").castSafely<Player>()!!` 取值并在数据层校验，**玩家是否离线由框架 `player(...)` 参数决定**（源码未做 `Bukkit.getPlayerExact` 复查）。
- **补全使用 `uncheck = true`**：`add`/`remove`/`set` 的 `amount` 补全声明为 `suggestion<CommandSender>(uncheck = true)`，与 `get` 等未标记的补全不同。
- 时间单位换算（`GameTimeManager`）：年份基准 2150，`formatOneLine` 输出 `YYYY.MM.DD HH:00`（分钟固定为 `00`）。
- `permissionMessage` 被显式置空，`newParser = true`（与本项目其他多数命令的 `false` 不同）。
- 别名中包含中文 `游戏时间`，实际可用性取决于客户端/服务端对非 ASCII 命令名的处理。

