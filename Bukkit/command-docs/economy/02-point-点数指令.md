# BNTG 贸易点指令

> **根指令**：`/point`（别名：`/bntg`, `/贸易点`）
> **权限**：`chotentech.command.point`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/PointCommand.kt`

## 功能概述

管理员指令，用于增删改玩家持有的 **BNTG 贸易点**（货币 ID：`point`，显示名：`BNTG贸易点`）。

- 结构与 `MoneyCommand` 完全对称（同样的 5 个子命令 + 同样的校验文案），仅货币 ID 不同。
- 全部操作通过 `CurrencyManager` 读写 `playerDataMap` 中的 `data.point` 字段。
- 数量参数用 `String.toLongOrNull()` 解析为 `Long`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/point` | 打印指令用法帮助树（`createModernHelper()`） | `chotentech.command.point` |
| `/point add <user> <amount>` | 为在线玩家增加贸易点 | `chotentech.command.point` |
| `/point give <user> <amount>` | 为在线玩家增加贸易点（与 `add` 等价） | `chotentech.command.point` |
| `/point remove <user> <amount>` | 扣除在线玩家的贸易点 | `chotentech.command.point` |
| `/point take <user> <amount>` | 扣除在线玩家的贸易点（与 `remove` 等价） | `chotentech.command.point` |
| `/point set <user> <amount>` | 设置在线玩家的贸易点为指定值 | `chotentech.command.point` |

> 5 个 `@CommandBody` 子命令（`add` / `give` / `remove` / `take` / `set`）+ 1 个默认主体（`main`）。源码中 `dynamic(` 与 `player(` 各出现 5 次。

## 指令详解

### `/point`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印可用子指令结构。
- **示例**：`/point`

---

### `/point add <user> <amount>`

- **用途**：为指定在线玩家增加 BNTG 贸易点。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player("user")` 玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加数量（`dynamic("amount")`，`Long`） | 是 | `100`, `1000`, `10000`（`uncheck = true`，可自由输入） |
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. `amount` 为空或 `< 1` → 提示 `数量必须为正整数` 并中止。
  2. `CurrencyManager.addBalance(player, "point", amount)`。
  3. 成功提示 `已给予 <玩家名> <amount> BNTG贸易点`；不通知目标玩家。
- **示例**：`/point add Steve 500`

---

### `/point give <user> <amount>`

- **用途**：与 `add` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：校验、调用与文案与 `add` 逐行相同。
- **示例**：`/point give Steve 500`

---

### `/point remove <user> <amount>`

- **用途**：扣除指定在线玩家的 BNTG 贸易点。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. `amount` 为空或 `< 1` → `数量必须为正整数`。
  2. `CurrencyManager.removeBalance(player, "point", amount)`：余额不足返回 `false` 且不扣减。
  3. 成功 → `已从 <玩家名> 扣除 <amount> BNTG贸易点`；失败 → `<玩家名> 的BNTG贸易点不足`。
- **示例**：`/point remove Steve 100`

---

### `/point take <user> <amount>`

- **用途**：与 `remove` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：与 `remove` 逐行相同。
- **示例**：`/point take Steve 100`

---

### `/point set <user> <amount>`

- **用途**：把指定在线玩家的贸易点直接设为某个值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 目标余额（`Long`） | 是 | `0`, `100`, `1000` |
- **权限**：`chotentech.command.point`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 校验 `amount == null || amount < 0` → `数量必须为非负整数`（**0 合法**）。
  2. `CurrencyManager.setBalance(player, "point", amount)`，内部 `maxOf(amount, 0)`。
  3. 提示 `已将 <玩家名> 的BNTG贸易点设为 <amount>`。
- **示例**：`/point set Steve 0`

## 备注 / 注意事项

- **权限模型**：`permission = "chotentech.command.point"`、`permissionDefault = PermissionDefault.OP` → 默认仅 OP；`permissionMessage = ""`。子命令无独立权限，执行体内无额外 `hasPermission` 检查。
- **与 `/money` 的关系**：本文件是 `MoneyCommand` 的货币 `point` 版本，字段、校验、提示文案结构完全一致，仅把货币 ID 从 `money` 换成 `point`（`data.point`）。
- **`add` ≡ `give`、`remove` ≡ `take`**：四段实现两两完全相同。
- **仅内存余额**：不做落盘，持久化由其它存盘流程负责。
- **数据缺失时静默无效**：`playerDataMap` 无该玩家数据时 `addBalance`/`setBalance` 直接 `return`，`removeBalance` 返回 `false`，但 `add`/`give`/`set` 仍会打印成功文案。
- **数值范围**：`Long`，源码无上下限钳制。
- **目标玩家参数**：`player("user")` 声明，取值用 `context.player("user").castSafely<Player>()!!`。
- **无余额查询子指令**：源码中没有 `balance` / `info` 之类的分支。
