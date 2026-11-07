# MEG 声望指令（RepuMeg）

> **根指令**：`/repumeg`（别名：`/rmeg`）
> **权限**：`chotentech.command.repumeg`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/RepuMegCommand.kt`

## 功能概述

管理员指令，用于增删改玩家在 **MEG 阵营**的声望值。

- 源码内固定两个常量：`FACTION = "MEG"`（阵营 ID 硬编码为 `MEG`，不可通过参数指定其它阵营）与 `REASON = "<#6E7A8C>(杂项扣除)"`（附加在所有提示文案末尾的来源说明）。
- 操作通过 `ReputationManager` 完成：`getReputation` / `setReputation` / `addReputation`，读写 `playerDataMap[...]?.reputation["MEG"]`。
- 每次变动都会额外调用 `ReputationManager.addMiscRecord(player, FACTION, delta)` 写入一条**杂项声望变动记录**（`MiscReputationRecord`），即指令调账在玩家声望流水里以"杂项"来源呈现。
- 与货币指令不同：本指令在增/扣操作后会**同时向目标玩家发送提示**。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/repumeg` | 打印指令用法帮助树（`createModernHelper()`） | `chotentech.command.repumeg` |
| `/repumeg add <user> <amount>` | 增加在线玩家的 MEG 声望 | `chotentech.command.repumeg` |
| `/repumeg give <user> <amount>` | 增加在线玩家的 MEG 声望（与 `add` 等价） | `chotentech.command.repumeg` |
| `/repumeg remove <user> <amount>` | 扣除在线玩家的 MEG 声望 | `chotentech.command.repumeg` |
| `/repumeg take <user> <amount>` | 扣除在线玩家的 MEG 声望（与 `remove` 等价） | `chotentech.command.repumeg` |
| `/repumeg set <user> <amount>` | 将在线玩家的 MEG 声望设为指定值 | `chotentech.command.repumeg` |

> 5 个 `@CommandBody` 子命令（`add` / `give` / `remove` / `take` / `set`）+ 1 个默认主体（`main`）。源码中 `dynamic(` 与 `player(` 各出现 5 次。

## 指令详解

### `/repumeg`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/repumeg`

---

### `/repumeg add <user> <amount>`

- **用途**：为指定在线玩家增加 MEG 阵营声望。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player("user")` 玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加量（`dynamic("amount")`，`Long`） | 是 | `100`, `1000`, `10000`（`uncheck = true`，可自由输入） |
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. `amount` 为空或 `< 1` → 提示 `数量必须为正整数` 并中止。
  2. `ReputationManager.addReputation(player, "MEG", +amount)`。
  3. `ReputationManager.addMiscRecord(player, "MEG", +amount)` 写入一条杂项记录。
  4. 执行者收到 `已给予 <玩家名> <amount> MEG声望 <#6E7A8C>(杂项扣除)`。
  5. 目标玩家收到 `MEG声望 +<amount> <#6E7A8C>(杂项扣除)`（`<span>`：`<#DFBD7C>MEG声望 <#8FD4A6>+N`）。
- **示例**：`/repumeg add Steve 100`

---

### `/repumeg give <user> <amount>`

- **用途**：与 `add` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 增加量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：校验、加声望、写记录、双方提示，与 `add` 逐行相同。
- **示例**：`/repumeg give Steve 100`

---

### `/repumeg remove <user> <amount>`

- **用途**：扣除指定在线玩家的 MEG 阵营声望。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. `amount` 为空或 `< 1` → `数量必须为正整数`。
  2. **前置余额检查**：`ReputationManager.getReputation(player, "MEG") < amount` → 提示 `<玩家名> 的MEG声望不足` 并中止（不允许扣成负数）。
  3. `addReputation(player, "MEG", -amount)`，并 `addMiscRecord(player, "MEG", -amount)`。
  4. 执行者收到 `已从 <玩家名> 扣除 <amount> MEG声望 (杂项扣除)`。
  5. 目标玩家收到 `MEG声望 -<amount> (杂项扣除)`。
- **示例**：`/repumeg remove Steve 50`

---

### `/repumeg take <user> <amount>`

- **用途**：与 `remove` 完全一致的别名式实现。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 扣除量（`Long`） | 是 | `100`, `1000`, `10000` |
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：与 `remove` 逐行相同（含声望不足的提前返回）。
- **示例**：`/repumeg take Steve 50`

---

### `/repumeg set <user> <amount>`

- **用途**：把指定在线玩家的 MEG 声望直接设为指定值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（玩家类型参数） | 是 | 在线玩家名 |
  | `amount` | 目标声望值（`Long`） | 是 | `0`, `100`, `1000` |
- **权限**：`chotentech.command.repumeg`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 校验 `amount == null || amount < 0` → `数量必须为非负整数`（**0 合法**）。
  2. 计算差值 `delta = amount - getReputation(player, "MEG")`。
  3. `setReputation(player, "MEG", amount)` 覆盖写入。
  4. 若 `delta != 0`，调用 `addMiscRecord(player, "MEG", delta)` 写入一条按差值计的杂项记录；`delta == 0` 时不写记录。
  5. 仅向执行者提示 `已将 <玩家名> 的MEG声望设为 <amount>`；**不向目标玩家发送通知**（与 add/remove 不同）。
- **示例**：`/repumeg set Steve 0`

## 备注 / 注意事项

- **权限模型**：`permission = "chotentech.command.repumeg"`、`permissionDefault = PermissionDefault.OP` → 默认仅 OP；`permissionMessage = ""`。子命令无独立权限声明，执行体内无额外 `hasPermission` 检查。
- **阵营硬编码**：`FACTION = "MEG"` 是私有常量，指令层无法指定其它阵营（其它阵营的声望只能通过干涉/剧情等其它系统变更）。
- **`REASON` 文案固定为 `(杂项扣除)`**：即使是 `add`/`give` 增加声望，提示里也会附带"(杂项扣除)"，这是源码写死文案导致的表现差异，不是参数化的原因文本。
- **流水记录**：add/give 记 `+amount`，remove/take 记 `-amount`，set 记 `delta`（为 0 时不记）。记录写入 `data.miscReputationRecords`。
- **`add` ≡ `give`、`remove` ≡ `take`**：四段实现两两完全相同。
- **数据缺失时静默无效**：`ReputationManager` 各方法在 `playerDataMap` 无该玩家数据时直接 `return`（`getReputation` 返回 `0`）。此时 add/give 仍打印成功文案；`remove`/`take` 因余额读到 0 会立刻走"声望不足"分支。set 也不会真正写入但仍打印成功。
- **无上限/下限钳制**：`addReputation` 为 `current + amount` 直接相加；下限由 `remove`/`take` 的余额检查与 `set` 的非负校验间接保证。
- **目标玩家参数**：`player("user")` 声明，取值 `context.player("user").castSafely<Player>()!!`。
- **无查询子指令**：源码中没有查看声望的分支。
