# 磁带指令

> **根指令**：`/tape`（别名：`/磁带`）
> **权限**：`chotentech.command.tape`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/TapeCommand.kt`

## 功能概述

管理员指令，用于增删改玩家持有的 **磁带**（货币 ID：`tape`，显示名：`磁带`）。

- 结构与 `/money`、`/point` 完全对称（5 个子命令 + 相同的校验与提示文案模板），仅货币 ID 不同。
- 通过 `CurrencyManager` 读写 `playerDataMap[...]?.tape`。
- 磁带在其它系统中被消费，例如订阅（`SubscriptionManager` 扣 `tape`）与商城价格（`CurrencyUtil.remove(player, "tape", ...)`）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/tape` | 打印指令用法帮助树（`createModernHelper()`） | `chotentech.command.tape` |
| `/tape add <user> <amount>` | 为在线玩家增加磁带 | `chotentech.command.tape` |
| `/tape give <user> <amount>` | 为在线玩家增加磁带（与 `add` 等价） | `chotentech.command.tape` |
| `/tape remove <user> <amount>` | 扣除在线玩家的磁带 | `chotentech.command.tape` |
| `/tape take <user> <amount>` | 扣除在线玩家的磁带（与 `remove` 等价） | `chotentech.command.tape` |
| `/tape set <user> <amount>` | 设置在线玩家的磁带数量 | `chotentech.command.tape` |

> 5 个 `@CommandBody` 子命令（`add` / `give` / `remove` / `take` / `set`）+ 1 个默认主体（`main`）。源码中 `dynamic(` 与 `player(` 各出现 5 次。

## 指令详解

### `/tape`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/tape`

---

### `/tape add <user> <amount>`

- **用途**：为指定在线玩家增加磁带。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player("user")` 玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加数量（`dynamic("amount")`，`Long`） | 是 | `1`, `5`, `10`（`uncheck = true`，可自由输入） |
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. `amount` 为空或 `< 1` → 提示 `数量必须为正整数` 并中止。
  2. `CurrencyManager.addBalance(player, "tape", amount)`。
  3. 成功提示 `已给予 <玩家名> <amount> 磁带`；不通知目标玩家。
- **示例**：`/tape add Steve 10`

---

### `/tape give <user> <amount>`

- **用途**：与 `add` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加数量（`Long`） | 是 | `1`, `5`, `10` |
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：校验、调用与文案与 `add` 逐行相同。
- **示例**：`/tape give Steve 10`

---

### `/tape remove <user> <amount>`

- **用途**：扣除指定在线玩家的磁带。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除数量（`Long`） | 是 | `1`, `5`, `10` |
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. `amount` 为空或 `< 1` → `数量必须为正整数`。
  2. `CurrencyManager.removeBalance(player, "tape", amount)`：余额不足返回 `false`，不做扣减。
  3. 成功 → `已从 <玩家名> 扣除 <amount> 磁带`；失败 → `<玩家名> 的磁带不足`。
- **示例**：`/tape remove Steve 5`

---

### `/tape take <user> <amount>`

- **用途**：与 `remove` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除数量（`Long`） | 是 | `1`, `5`, `10` |
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：与 `remove` 逐行相同。
- **示例**：`/tape take Steve 5`

---

### `/tape set <user> <amount>`

- **用途**：把指定在线玩家的磁带数量直接设为某个值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 目标数量（`Long`） | 是 | `0`, `1`, `5` |
- **权限**：`chotentech.command.tape`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 校验 `amount == null || amount < 0` → `数量必须为非负整数`（**0 合法**）。
  2. `CurrencyManager.setBalance(player, "tape", amount)`，内部 `maxOf(amount, 0)`。
  3. 提示 `已将 <玩家名> 的磁带设为 <amount>`。
- **示例**：`/tape set Steve 0`

## 备注 / 注意事项

- **权限模型**：`permission = "chotentech.command.tape"`、`permissionDefault = PermissionDefault.OP` → 默认仅 OP；`permissionMessage = ""`。子命令无独立权限，执行体内无额外 `hasPermission` 检查。
- **补全数值按磁带量级设计**：`add`/`give`/`remove`/`take` 的提示值为 `1` / `5` / `10`，`set` 为 `0` / `1` / `5`（与 `/money`、`/point` 的百/千级不同）。补全均为 `uncheck = true`，仅作提示。
- **`add` ≡ `give`、`remove` ≡ `take`**：四段实现两两完全相同。
- **仅内存余额**：不做落盘，持久化由其它存盘流程负责。
- **数据缺失时静默无效**：`playerDataMap` 无该玩家数据时 `addBalance`/`setBalance` 直接 `return`、`removeBalance` 返回 `false`，但 add/give/set 仍打印成功文案。
- **数值范围**：`Long`，源码无上下限钳制。
- **目标玩家参数**：`player("user")` 声明，取值 `context.player("user").castSafely<Player>()!!`。
- **无查询子指令**：源码中没有查看磁带的子命令。
