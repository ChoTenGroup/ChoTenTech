# MEG 积分（货币）指令

> **根指令**：`/money`（别名：`/meg`, `/积分`）
> **权限**：`chotentech.command.money`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/MoneyCommand.kt`

## 功能概述

管理员指令，用于增删查改玩家持有的 **MEG 积分**（货币 ID：`money`，显示名：`MEG积分`，取自 `CurrencyManager.displayNames`）。

- 所有操作都通过 `CurrencyManager` 直接读写 `playerDataMap` 中的内存余额（`data.money`）。
- 该指令**只做管理端调账**，不涉及任何 GUI；无查询（balance）子指令，玩家查余额需走其它入口。
- 数量参数统一用 `String.toLongOrNull()` 解析，解析失败或数值越界即报错返回。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/money` | 打印指令用法帮助树（`createModernHelper()` 生成） | `chotentech.command.money` |
| `/money add <user> <amount>` | 为在线玩家增加 MEG 积分 | `chotentech.command.money` |
| `/money give <user> <amount>` | 为在线玩家增加 MEG 积分（与 `add` 完全等价） | `chotentech.command.money` |
| `/money remove <user> <amount>` | 扣除在线玩家的 MEG 积分 | `chotentech.command.money` |
| `/money take <user> <amount>` | 扣除在线玩家的 MEG 积分（与 `remove` 完全等价） | `chotentech.command.money` |
| `/money set <user> <amount>` | 将在线玩家的 MEG 积分设为指定值 | `chotentech.command.money` |

> 5 个 `@CommandBody` 子命令（`add` / `give` / `remove` / `take` / `set`）+ 1 个默认主体（`main`）。源码中 `dynamic(` 出现 5 次、`player(` 出现 5 次，与 5 个子命令一一对应。

## 指令详解

### `/money`

- **用途**：显示该根指令的完整用法树。
- **参数**：无。
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：由 `createModernHelper()` 自动生成，按发送者权限过滤显示的子命令列表，仅展示合法子指令。
- **示例**：`/money`

---

### `/money add <user> <amount>`

- **用途**：为指定在线玩家增加 MEG 积分。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player("user")` 玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加的数量（`dynamic("amount")`，按 `Long` 解析） | 是 | `100`, `1000`, `10000`（`uncheck = true`，仅作提示，可自由输入） |
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`），但 `user` 必须能解析为一个玩家对象
- **行为说明**：
  1. `amount` 解析失败或 `< 1` → 提示 `数量必须为正整数` 并中止。
  2. 通过 `CurrencyManager.addBalance(player, "money", amount)` 增加余额（内部对负数做 `maxOf(amount, 0)` 兜底，但指令层已拦截非正数）。
  3. 成功后向执行者提示 `已给予 <玩家名> <amount> MEG积分`。
  4. 不会向被操作的玩家发送任何通知。
- **示例**：`/money add Steve 1000`

---

### `/money give <user> <amount>`

- **用途**：与 `add` 完全一致的别名式实现（同一份代码），用于习惯 `give` 语法的管理员。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加的数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：校验逻辑与提示文案与 `add` 逐行相同：非正整数拦截；成功提示 `已给予 <玩家名> <amount> MEG积分`。
- **示例**：`/money give Steve 500`

---

### `/money remove <user> <amount>`

- **用途**：从指定在线玩家扣除 MEG 积分。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除的数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. `amount` 解析失败或 `< 1` → 提示 `数量必须为正整数` 并中止。
  2. 调用 `CurrencyManager.removeBalance(player, "money", amount)`：余额不足时返回 `false`，不做任何扣减。
  3. 成功 → `已从 <玩家名> 扣除 <amount> MEG积分`；失败 → `<玩家名> 的MEG积分不足`。
  4. 不会向被操作的玩家发送通知。
- **示例**：`/money remove Steve 200`

---

### `/money take <user> <amount>`

- **用途**：与 `remove` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除的数量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：与 `remove` 逐行相同（余额不足判断、成功/失败文案一致）。
- **示例**：`/money take Steve 200`

---

### `/money set <user> <amount>`

- **用途**：将指定在线玩家的 MEG 积分直接设为某个值（覆盖式写入）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 目标余额（`Long`） | 是 | `0`, `100`, `1000` |
- **权限**：`chotentech.command.money`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 校验为 `amount == null || amount < 0` → 提示 `数量必须为非负整数`（**允许 0**，这是与 add/remove 的关键差异）。
  2. 调用 `CurrencyManager.setBalance(player, "money", amount)`（内部 `maxOf(amount, 0)`）。
  3. 提示 `已将 <玩家名> 的MEG积分设为 <amount>`。
  4. 不校验原余额，也不提示变动差值；不通知被操作玩家。
- **示例**：`/money set Steve 0`

## 备注 / 注意事项

- **权限模型**：`@CommandHeader` 声明 `permission = "chotentech.command.money"` 且 `permissionDefault = PermissionDefault.OP`，因此默认**仅 OP** 可用；`permissionMessage = ""`（拒绝时自定义提示文案为空）。子命令未单独声明权限，全部继承根权限；执行体内也没有额外的 `hasPermission` 检查。
- **`add` ≡ `give`、`remove` ≡ `take`**：四段代码两两完全相同（同一份实现的两个名字），文档、权限、提示文案均无差别。
- **仅内存余额**：`CurrencyManager` 直接改 `playerDataMap[...]` 的字段，本指令不做存档/落盘操作，持久化由其它存盘流程负责。
- **数据缺失时静默无效**：`playerDataMap` 中若没有该玩家的数据（例如数据尚未加载），`addBalance` / `setBalance` 会直接 `return`、`removeBalance` 返回 `false`。此时 `add`/`give`/`set` 仍会打印成功文案，属于源码中的已知行为，排查"调了钱没到账"时需先确认玩家数据已加载。
- **数值范围**：金额为 `Long`，源码未做上下限钳制，理论上存在溢出风险；`amount` 的合法性完全依赖 `toLongOrNull()`。
- **玩家类型参数**：`user` 由 `player("user")` 声明，执行体通过 `context.player("user").castSafely<Player>()!!` 取值（非空断言）。指令层不额外判定"目标不在线"，解析由框架负责。
- **无查询功能**：本文件不提供 `/money balance` 之类的查询子指令，源码中亦无对应 `literal`/`dynamic`。
- **反馈通道**：执行者提示走 `sendComponent`（支持 MiniMessage 风格颜色标签，如 `<#8FD4A6>`）。
