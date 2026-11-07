# Quetiapine 等级经验指令（/quetiapinelevel）

> **根指令**：`/quetiapinelevel`（别名：`/rpglevel`, `/chotentechlevel`, `/cttlevel`）
> **权限**：`quetiapine.command.level`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/expSubCommand.kt`

## 功能概述

`/quetiapinelevel` 是等级 / 经验子系统的管理员指令入口，用于直接改写玩家在某个"等级模板"（`levels` 映射中的 key）上的**等级**与**经验**：

- 等级三等一重：`setLevel`（设为指定值）、`addLevel`（增减）、`removeLevel`（扣除）、`resetLevel`（恢复模板起始等级）；
- 经验三等：`setExp`（设为指定值）、`addExp`（增加）、`removeExp`（扣减）。

所有写操作都会经过 `PlayerDataManager` 的对应方法，因而会**触发可取消事件**：

| 子命令 | 触发事件 |
|---|---|
| `setLevel` | `QuetiapineSetLevelEvent` |
| `addLevel` | `QuetiapineAddLevelEvent` |
| `removeLevel` | `QuetiapineRemoveLevelEvent` |
| `resetLevel` | `QuetiapineResetLevelEvent` |
| `setExp` | `QuetiapineSetExpEvent` |
| `addExp` | `QuetiapineAddExpEvent` |
| `removeExp` | `QuetiapineRemoveExpEvent` |

事件被取消（或等级模板不存在）时方法返回 `null`，指令输出 `Operation_Cancelled`。

> **注意**：`@CommandHeader` 的 `newParser = true`，且该对象自身就是一个独立的根指令（不挂在 `/chotentech` 之下）。

## 指令总览

根权限统一为 `quetiapine.command.level`（默认 OP）；源码中**没有任何子命令单独声明权限**。

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttlevel` | 无参数，打印指令用法树（`createModernHelper`） | `quetiapine.command.level` |
| `/cttlevel help` | 打印指令用法树 | `quetiapine.command.level` |
| `/cttlevel setLevel <key> <value> [选项]` | 把自己某等级设为 `value` | `quetiapine.command.level` |
| `/cttlevel setLevel <key> <value> <player> [选项]` | 把指定玩家某等级设为 `value` | `quetiapine.command.level` |
| `/cttlevel addLevel <key> <value> [选项]` | 增加自己某等级 | `quetiapine.command.level` |
| `/cttlevel addLevel <key> <value> <player> [选项]` | 增加指定玩家某等级 | `quetiapine.command.level` |
| `/cttlevel removeLevel <key> <value> [选项]` | 扣除自己某等级 | `quetiapine.command.level` |
| `/cttlevel removeLevel <key> <value> <player> [选项]` | 扣除指定玩家某等级 | `quetiapine.command.level` |
| `/cttlevel resetLevel <key> [选项]` | 重置自己某等级为模板起始值 | `quetiapine.command.level` |
| `/cttlevel resetLevel <key> <player> [选项]` | 重置指定玩家某等级为模板起始值 | `quetiapine.command.level` |
| `/cttlevel setExp <key> <value> [选项]` | 把自己某经验设为 `value` | `quetiapine.command.level` |
| `/cttlevel setExp <key> <value> <player> [选项]` | 把指定玩家某经验设为 `value` | `quetiapine.command.level` |
| `/cttlevel addExp <key> <value> [选项]` | 增加自己某经验 | `quetiapine.command.level` |
| `/cttlevel addExp <key> <value> <player> [选项]` | 增加指定玩家某经验 | `quetiapine.command.level` |
| `/cttlevel removeExp <key> <value> [选项]` | 扣减自己某经验 | `quetiapine.command.level` |
| `/cttlevel removeExp <key> <value> <player> [选项]` | 扣减指定玩家某经验 | `quetiapine.command.level` |

（合计 1 个 `main` 主体 + 8 个子命令：`help` 及 7 个读写子命令，共 15 条有效命令路径。）

### 通用选项（options）

7 个读写子命令（`setLevel` / `addLevel` / `removeLevel` / `resetLevel` / `setExp` / `addExp` / `removeExp`）在**所有分支**上都读取两个 TabooLib 选项：

| 选项写法 | 代码读取方式 | 作用 |
|---|---|---|
| `-silent` 或 `-s` | `context.option("silent", "s") == ""` | 为真时**不通知目标玩家**（跳过 `player.sendStringAsComponent(...)`） |
| `-consolesilent` 或 `-cs` 或 `-csilent` | `context.option("consolesilent", "cs", "csilent") == ""` | 为真时**不向指令发送者回执**（跳过 `sender.infoS(...)`，含 `Operation_Cancelled`） |

两者互相独立，可同时使用；选项缺省（`null`）时两个开关都为 `false`，即都会提示。

## 指令详解

### `/cttlevel` 与 `/cttlevel help`
- **用途**：打印本指令的用法树。
- **参数**：无。
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：调用 `createModernHelper()`，按发送者权限过滤并以树状输出子命令；`key` 提示来自 `dynamic("key").comment`。
- **示例**：`/cttlevel`

### `/cttlevel setLevel <key> <value>`
- **用途**：把**自己**在指定等级模板上的等级**设为** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key（`levels` 映射的键） | 是 | 补全 = `levels.keys`（无视权限校验，`uncheck = true`） |
  | `value` | 整数（`value.toLong()`） | 是 | 无（自由输入） |
  | `-silent`/`-s` | 选项，静默目标玩家 | 否 | — |
  | `-consolesilent`/`-cs`/`-csilent` | 选项，静默发送者回执 | 否 | — |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**（`sender as? Player? ?: run { severeS(Player_Only_Command) }`）。
- **行为说明**：异步执行 `PlayerDataManager(player).setLevel(key, value)`。等级会被钳制在 `[template.level.min, template.level.getMaxLevel(player)]`（无上限配置时用 `Long.MAX_VALUE`），并写入 `level.<key>`。事件被取消返回 `null` 时提示 `Operation_Cancelled`；成功时向发送者发 `PlayerLevel_Reset_Console`（玩家名, 等级显示名, 结果值），向目标玩家发 `PlayerLevel_Reset_Player`（等级显示名, 结果值）。`levels[key]` 不存在时先输出 `Level_Not_Exist` 并中止。
- **示例**：`/cttlevel setLevel combat 30`、`/cttlevel setLevel combat 30 -s`

### `/cttlevel setLevel <key> <value> <player>`
- **用途**：把**指定玩家**某等级设为 `value`（控制台可用）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 整数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可（目标玩家解析失败时输出 `Player_Not_Exist`）。
- **行为说明**：与上一个分支逻辑完全相同，仅目标玩家来源不同。
- **示例**：`/cttlevel setLevel combat 30 Steve -cs`

### `/cttlevel addLevel <key> <value>`
- **用途**：给**自己**的某等级**加上** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 整数（`toLong()`） | 是 | 无 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `PlayerDataManager(player).addLevel(key, value)`：先触发 `QuetiapineAddLevelEvent`（插件可修改 `addAmount`），再 `limit(min, 当前值 + addAmount, max)` 写入 `level.<key>`。注意本分支**先执行加法再校验 `levels[key]`**（`addLevel` 内部对未知 key 也返回 `null`），因此未知 key 最终仍是先输出 `Level_Not_Exist`。成功用 `PlayerLevel_Modify_Console` / `PlayerLevel_Modify_Player` 文案。
- **示例**：`/cttlevel addLevel combat 5`

### `/cttlevel addLevel <key> <value> <player>`
- **用途**：给指定玩家的某等级加 `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 整数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数。
- **示例**：`/cttlevel addLevel combat 5 Steve`

### `/cttlevel removeLevel <key> <value>`
- **用途**：从**自己**的某等级**扣除** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 整数 | 是 | 无 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `removeLevel(key, value)`：先校验 `levels[key]`（不存在则 `Level_Not_Exist`），再触发 `QuetiapineRemoveLevelEvent`（可改 `removeAmount`），`limit(min, 当前值 - removeAmount, max)` 后写入。结果消息用 `PlayerLevel_Modify_Console` / `PlayerLevel_Modify_Player`。
- **示例**：`/cttlevel removeLevel combat 5`

### `/cttlevel removeLevel <key> <value> <player>`
- **用途**：从指定玩家的某等级扣除 `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 整数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数。
- **示例**：`/cttlevel removeLevel combat 5 Steve`

### `/cttlevel resetLevel <key>`
- **用途**：把**自己**的某等级重置为模板的起始等级。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `resetLevel(key)`：触发 `QuetiapineResetLevelEvent`，写入 `limit(min, template.level.start, max)`。成功用 `PlayerLevel_Reset_Console` / `PlayerLevel_Reset_Player` 文案。
- **示例**：`/cttlevel resetLevel combat`

### `/cttlevel resetLevel <key> <player>`
- **用途**：重置指定玩家的某等级为模板起始等级。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数。
- **示例**：`/cttlevel resetLevel combat Steve`

### `/cttlevel setExp <key> <value>`
- **用途**：把**自己**在某个等级模板上的经验**设为** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数（`toDouble()`） | 是 | 无 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `setExp(key, value)`：触发 `QuetiapineSetExpEvent`（可改 `exp`），写入 `exp.<key>`，**不做上下限钳制**。成功用 `PlayerExp_Modify_Console` / `PlayerExp_Modify_Player` 文案。
- **示例**：`/cttlevel setExp combat 1234.5`

### `/cttlevel setExp <key> <value> <player>`
- **用途**：把指定玩家某经验设为 `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数。
- **示例**：`/cttlevel setExp combat 1234.5 Steve`

### `/cttlevel addExp <key> <value>`
- **用途**：给**自己**的某经验**增加** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数 | 是 | 无 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `addExp(key, value.toDouble(), ignoreSafeZone = true)`。**指令侧强制 `ignoreSafeZone = true`**，因此即使玩家身处基地/`world` 安全区也会正常加经验（绕过正常玩法中"安全区不加经验"的拦截）。写入 `exp.<key> = 旧值 + event.exp`，不钳制。成功用 `PlayerExp_Add_Console` / `PlayerExp_Add_Player` 文案，并回显**输入值**（`value.toDouble()`）而非最终值。
- **示例**：`/cttlevel addExp combat 100.0`

### `/cttlevel addExp <key> <value> <player>`
- **用途**：给指定玩家的某经验增加 `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数（同样 `ignoreSafeZone = true`）。
- **示例**：`/cttlevel addExp combat 100.0 Steve -s`

### `/cttlevel removeExp <key> <value>`
- **用途**：从**自己**的某经验**扣减** `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数 | 是 | 无 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：**仅玩家**。
- **行为说明**：异步调用 `removeExp(key, value)`：触发 `QuetiapineRemoveExpEvent`，写入 `max(旧值 - event.exp, 0.0)`（**下限 0**，不会为负）。成功用 `PlayerExp_Remove_Console` / `PlayerExp_Remove_Player` 文案，回显**输入值**。
- **示例**：`/cttlevel removeExp combat 50.0`

### `/cttlevel removeExp <key> <value> <player>`
- **用途**：从指定玩家的某经验扣减 `value`。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 等级模板 key | 是 | `levels.keys` |
  | `value` | 小数 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
  | 选项 | 同上 | 否 | `-silent`/`-s`、`-consolesilent`/`-cs`/`-csilent` |
- **权限**：`quetiapine.command.level`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于 `player` 参数。
- **示例**：`/cttlevel removeExp combat 50.0 Steve`

## 备注 / 注意事项

1. **根指令名与别名**：`quetiapinelevel` / `rpglevel` / `chotentechlevel` / `cttlevel`。注意它与 `/chotentech-levels`（别名 `cttl`、`cttlevels`，见 `06-cttl-层级指令.md`）是**两个不同的命令**，别名只差一个 `s`，容易混淆。
2. **`key` 必须是已加载的等级模板 key**：所有子命令都先查 `levels[key]`，不存在时输出语言键 `Level_Not_Exist`。补全来源即 `levels.keys.asList()`，且 `uncheck = true`（不做额外校验过滤）。
3. **数值类型**：等级类（`setLevel`/`addLevel`/`removeLevel`）用 `value.toLong()`，经验类（`setExp`/`addExp`/`removeExp`）用 `value.toDouble()`。传非数字会抛 Kotlin 转换异常，命令没有兜底提示。
4. **钳制规则差异**（易踩坑）：
   - `setLevel` / `addLevel` / `removeLevel` / `resetLevel` → 结果被 `limit(template.level.min, ..., template.level.getMaxLevel(player))` 钳制；
   - `setExp` / `addExp` → **不钳制**；
   - `removeExp` → 仅保证下限为 0。
5. **"静默"是控制哪个对象**：`-silent`/`-s` 只静默**目标玩家**；`-consolesilent`/`-cs`/`-csilent` 只静默**指令发送者**（包括吞掉 `Operation_Cancelled`）。因此控制台执行自身分支（无 `player` 参数）时，`sender` 就是控制台，此时"自身分支"根本走不进去（见下条）。
6. **自身分支仅限玩家**：7 个子命令的**无 `<player>` 分支**都用 `sender as? Player? ?: run { severeS(Player_Only_Command); return }`，控制台必须显式提供 `<player>` 才能使用。
7. **`addLevel` 的执行顺序特殊**：它先调用 `PlayerDataManager.addLevel(...)`，再查 `levels[key]` 取显示名（其它子命令都是先查后写）。对未知 key 结果一致（都提示 `Level_Not_Exist`），但若日后事件监听器有副作用需注意时序。
8. **`addExp` 绕过安全区限制**：正常玩法路径中 `addExp` 会在基地/`world` 安全区被拦截，而本指令显式传 `ignoreSafeZone = true`，属于刻意的管理员特权。
9. **`addExp` / `removeExp` 回显的是输入值**，不是结算后的最终值；`setExp`/`setLevel` 等回显的是 `final`（方法返回值）。
10. **事件可取消**：任何 `QuetiapineSetLevelEvent` 等被插件取消后，指令只输出 `Operation_Cancelled`（可被 `-cs` 吞掉），且不会写库。
11. 输出文案全部来自语言文件：`Level_Not_Exist`、`Operation_Cancelled`、`Player_Not_Exist`、`Player_Only_Command`、`PlayerLevel_Reset_Console`、`PlayerLevel_Reset_Player`、`PlayerLevel_Modify_Console`、`PlayerLevel_Modify_Player`、`PlayerExp_Modify_Console`、`PlayerExp_Modify_Player`、`PlayerExp_Add_Console`、`PlayerExp_Add_Player`、`PlayerExp_Remove_Console`、`PlayerExp_Remove_Player`。
