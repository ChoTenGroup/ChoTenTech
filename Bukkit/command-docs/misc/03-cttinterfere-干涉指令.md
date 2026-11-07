# 干涉指令

> **根指令**：`/chotentech-interfere`（别名：`/cttinterfere`, `/interfere`）
> **权限**：`chotentech.command.interfere`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/InterfereCommand.kt`

## 功能概述

"干涉（Interfere）"系统的管理指令：创建干涉实例、激活、逐阵营设置结果等级、结算、取消、列出、查看详情、打开 GUI，以及直接为玩家应用某结果等级的声望变更。

涉及的数据对象：
- `interfereConfigs`：`Map<String, InterfereConfig>`，键为配置 ID，配置含 `id`、`name`、`scores`（`Map<ResultLevel, InterfereScore>`，而 `InterfereScore.changes` 是 `Map<String, Long>` 即"阵营 → 声望变化"）。
- `ResultLevel` 枚举：`FULL_SUCCESS`（完胜）、`SUCCESS`（成功）、`NEUTRAL`（平手）、`FAIL`（失败）、`FULL_FAIL`（完败），各带 `displayName` 与 `displayColor`。
- `InterfereStatus`：`PENDING`（未开始）/ `ACTIVE`（进行中）/ `FINISHED`（已结束）。
- `InterfereManager` 提供 `startInterfere` / `activateInterfere` / `setFactionLevel` / `endInterfere` / `cancelInterfere` / `getInterfere` / `getActiveInterferes` / `getAllInterferes`。

该文件共定义 **1 个根命令 + 9 个子命令**：`start`、`activate`、`setlevel`、`end`、`cancel`、`list`、`info`、`menu`、`apply`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttinterfere` | 显示帮助菜单（`createModernHelper()`） | `chotentech.command.interfere` |
| `/cttinterfere start <target> <configId>` | 为玩家创建干涉实例（PENDING） | `chotentech.command.interfere` |
| `/cttinterfere activate <instanceId>` | 激活干涉实例 | `chotentech.command.interfere` |
| `/cttinterfere setlevel <instanceId> <faction> <level>` | 设置某阵营的结果等级 | `chotentech.command.interfere` |
| `/cttinterfere end <instanceId>` | 结束干涉并结算声望变更 | `chotentech.command.interfere` |
| `/cttinterfere cancel <instanceId>` | 取消干涉，不应用声望变更 | `chotentech.command.interfere` |
| `/cttinterfere list` | 列出全部未结束的干涉 | `chotentech.command.interfere` |
| `/cttinterfere list <target>` | 列出指定玩家未结束的干涉 | `chotentech.command.interfere` |
| `/cttinterfere info <instanceId>` | 查看干涉实例详情 | `chotentech.command.interfere` |
| `/cttinterfere menu` | 打开干涉 GUI（`InterfereMenu`） | `chotentech.command.interfere` |
| `/cttinterfere apply <target> <interfereId> <level>` | 直接为玩家应用某结果等级的声望变更 | `chotentech.command.interfere` |

事件等级（`level` / 结果等级）可用值固定为源码中的 `LEVEL_SUGGESTIONS`：

| 枚举名 | 中文名 |
|---|---|
| `FULL_SUCCESS` | 完胜 |
| `SUCCESS` | 成功 |
| `NEUTRAL` | 平手 |
| `FAIL` | 失败 |
| `FULL_FAIL` | 完败 |

## 指令详解

### `/cttinterfere`

- **用途**：无参执行时输出该命令的帮助菜单。
- **参数**：无。
- **权限**：`chotentech.command.interfere`（`PermissionDefault.OP`）。
- **执行者要求**：玩家 / 控制台均可（帮助菜单由框架 `createModernHelper()` 生成）。
- **行为说明**：仅调用 `createModernHelper()`。
- **示例**：
  ```
  /cttinterfere
  /interfere
  ```

### `/cttinterfere start <target> <configId>`

- **用途**：按配置为指定玩家创建一个新的干涉实例（初始状态由 `InterfereManager.startInterfere` 决定，通常为 `PENDING`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `configId` | 干涉配置 ID（`dynamic`） | 必填 | `interfereConfigs.keys.toList()` |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`，目标由参数指定）。
- **行为说明**：
  1. 取 `interfereConfigs[configId]`，不存在则提示 `<#E08A98>未找到干扰配置: <configId>`。
  2. 调用 `InterfereManager.startInterfere(target, configId)`，返回 null 则提示 `<#E08A98>无法为 <玩家名> 创建干扰实例`。
  3. 成功时连发三行：
     - `<#8FD4A6>已为 <#EFDF8F><玩家名></#EFDF8F> 创建干扰: <配置名>`
     - `<#A9B4C3>实例ID: <#F2F5FA><instanceId> <#A9B4C3>| 状态: <status>`
     - `<#A9B4C3>使用 /interfere activate <instanceId> 将其激活`
- **示例**：
  ```
  /cttinterfere start Steve raid
  /interfere start Steve raid
  ```

### `/cttinterfere activate <instanceId>`

- **用途**：把已创建（未开始）的干涉实例激活为进行中。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（`dynamic`） | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }`（仅未结束实例） |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. `InterfereManager.getInterfere(instanceId)` 为 null 时提示 `<#E08A98>未找到干涉实例: <instanceId>`。
  2. `InterfereManager.activateInterfere(instanceId)` 返回 `true` 时提示两行：
     - `<#8FD4A6>已激活 <#EFDF8F><玩家名></#EFDF8F> 的干涉: <配置名>`
     - `<#A9B4C3>实例ID: <#F2F5FA><instanceId> <#A9B4C3>| 状态: <#84D1CB>进行中`
  3. 返回 `false` 时提示 `<#E08A98>无法激活 <instanceId>，可能已开始或已结束`。
- **示例**：
  ```
  /cttinterfere activate a1b2c3d4
  ```

### `/cttinterfere setlevel <instanceId> <faction> <level>`

- **用途**：为一个干涉实例中的某个阵营设定结果等级（结算前必须先设置）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（`dynamic`） | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |
  | `faction` | 阵营名（`dynamic`） | 必填 | 优先取该实例所属配置 `interfereConfigs[configId].scores` 里所有 `changes` 的阵营键（`distinct()`）；实例/配置查不到时回退为**全部配置**的阵营键并集 |
  | `level` | 结果等级（`dynamic`，解析时 `uppercase()`） | 必填 | `FULL_SUCCESS`, `SUCCESS`, `NEUTRAL`, `FAIL`, `FULL_FAIL` |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. `runCatching { ResultLevel.valueOf(context["level"].uppercase()) }`，解析失败时提示 `<#E08A98>未知结果等级，可用: ` 并附上 `显示名(枚举名)` 空格拼接的清单（形如 `完胜(FULL_SUCCESS) 成功(SUCCESS) ...`）。
  2. `InterfereManager.getInterfere(instanceId)` 为 null → `<#E08A98>未找到干涉实例: <instanceId>`。
  3. `InterfereManager.setFactionLevel(instanceId, faction, level)` 成功 → `<#8FD4A6>已将 <#EFDF8F><玩家名></#EFDF8F> 的阵营 <#84D1CB><faction></#84D1CB> 结果设为 <等级颜色><等级显示名>`。
  4. 失败 → `<#E08A98>设定失败，干涉实例已结束`。
- **示例**：
  ```
  /cttinterfere setlevel a1b2c3d4 red SUCCESS
  /interfere setlevel a1b2c3d4 blue FULL_FAIL
  ```

### `/cttinterfere end <instanceId>`

- **用途**：结束干涉实例并**结算**声望变更。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（`dynamic`） | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 实例不存在 → `<#E08A98>未找到干涉实例: <instanceId>`。
  2. 状态已是 `InterfereStatus.FINISHED` → `<#E08A98><instanceId> 已经结束了`。
  3. `InterfereManager.endInterfere(instanceId)` 返回 null → `<#E08A98>结算失败，<instanceId> 仍未结束（需玩家在线、已设置结果等级且配置存在）`。这条提示直接说明了结算的三个前置条件。
  4. 成功后重新读取实例（读不到则用结算前的快照 `before`），输出：
     - `<#8FD4A6><#EFDF8F><玩家名></#EFDF8F> 的干涉已结束: <配置名>`
     - `<#A9B4C3>结果: <faction>:<显示名>` 列表（多个阵营以 `<#A9B4C3>, ` 分隔，各带等级颜色）
     - `<#DFBD7C>声望变更:`
     - 每个变更一行：`<#A9B4C3>  <阵营>: <正数绿/负数红><+/-数值>`
- **示例**：
  ```
  /cttinterfere end a1b2c3d4
  ```

### `/cttinterfere cancel <instanceId>`

- **用途**：取消干涉实例，**不应用**任何声望变更。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（`dynamic`） | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 实例不存在 → `<#E08A98>未找到干涉实例: <instanceId>`。
  2. `cancelInterfere` 返回 `true` → `<#E08A98>已取消 <#EFDF8F><玩家名></#EFDF8F> 的干涉 <配置名>，不应用声望变更`。
  3. 返回 `false` → `<#E08A98>无法取消 <instanceId>，可能已结束`。
- **示例**：
  ```
  /cttinterfere cancel a1b2c3d4
  ```

### `/cttinterfere list [target]`

- **用途**：列出**未结束**的干涉实例；给出玩家时只列出该玩家的。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 可选，目标玩家（框架内置 `player(...)` 玩家参数） | 选填 | 框架内置的玩家补全 |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**（由私有方法 `sendList(sender, filterUuid, filterName)` 实现）：
  1. 过滤 UUID 为空时取 `InterfereManager.getActiveInterferes()`，否则取 `getActiveInterferes(filterUuid)`。
  2. 列表为空 → `<#A9B4C3>当前没有<[玩家名 的]>正在进行的干扰`。
  3. 否则先发标题 `<#DFBD7C>=== <[玩家名 的]>正在进行的干扰 (<数量>) ===`。
  4. 每个实例输出两行：
     - `<#EFDF8F><玩家名> <#A9B4C3>| <#8FD4A6><配置名> <#A9B4C3>| <状态颜色><状态> <#A9B4C3>| <阵营结果>`
     - `<#6E7A8C>  <instanceId>`
  5. 阵营结果：若 `factionLevels` 非空则逐项 `<等级颜色><阵营>:<显示名>`；否则按状态显示 `未开始`（PENDING，黄）/ `进行中`（ACTIVE，青）/ `无结果`（其他，灰）。
- **示例**：
  ```
  /cttinterfere list
  /cttinterfere list Steve
  ```

### `/cttinterfere info <instanceId>`

- **用途**：查看单个干涉实例的完整信息（含时间戳）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（`dynamic`） | 必填 | `InterfereManager.getAllInterferes().map { it.instanceId }`（**全部**实例，含已结束） |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 实例不存在 → `<#E08A98>未找到干涉实例: <instanceId>`。
  2. 否则依次输出：标题 `<#DFBD7C>=== 干扰信息: <instanceId> ===`、`玩家`、`配置`（`name`）、`配置ID`（`configId`）、`状态`。
  3. 若 `factionLevels` 非空：先输出 `阵营结果:`，再逐阵营输出 `<阵营>: <等级颜色><显示名>`。
  4. 否则输出 `结果: ` + 按状态的显示值（PENDING→`未开始`、ACTIVE→`进行中`、其他→`无结果`）。
  5. 输出 `开始时间: yyyy-MM-dd HH:mm:ss`（`SimpleDateFormat` 格式化 `startTime`）；`endTime` 非空时追加 `结束时间: ...`。
- **示例**：
  ```
  /cttinterfere info a1b2c3d4
  ```

### `/cttinterfere menu`

- **用途**：为执行者打开干涉管理 GUI（`InterfereMenu`）。
- **参数**：无。
- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：**仅玩家**（`execute<Player>`，GUI 需要一个在线玩家）。
- **行为说明**：`InterfereMenu().open(sender)`。无任何提示消息。
- **示例**：
  ```
  /cttinterfere menu
  ```

### `/cttinterfere apply <target> <interfereId> <level>`

- **用途**：跳过干涉实例，**直接**把某个配置在某个结果等级下的声望变更应用到玩家身上。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `interfereId` | 干涉配置 ID（`dynamic`） | 必填 | `interfereConfigs.keys.toList()` |
  | `level` | 结果等级（`dynamic`，解析时 `uppercase()`） | 必填 | `FULL_SUCCESS`, `SUCCESS`, `NEUTRAL`, `FAIL`, `FULL_FAIL` |

- **权限**：`chotentech.command.interfere`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**（校验顺序：等级 → 配置 → 变更结果）：
  1. `ResultLevel.valueOf(uppercase)` 失败 → `<#E08A98>未知结果等级，可用: <清单>`。
  2. `interfereConfigs[interfereId]` 为空 → `<#E08A98>未找到干扰配置: <interfereId>`。
  3. `ReputationManager.applyInterfereReputation(target, interfereId, level)` 返回空集合 → `<#E08A98>该结果等级未配置声望变化`。
  4. 成功 → 首行 `<#DFBD7C>已为 <玩家名> 应用干扰 <配置名> <等级颜色><等级显示名> <#DFBD7C>声望变更:`，随后每个变更一行 `<#A9B4C3>  <阵营>: <绿/红><+/-数值>`。
- **示例**：
  ```
  /cttinterfere apply Steve raid SUCCESS
  /interfere apply Steve raid FULL_SUCCESS
  ```

## 备注 / 注意事项

- **`level` 大小写不敏感**：`setlevel` 与 `apply` 都会对输入执行 `uppercase()` 后再 `ResultLevel.valueOf`，因此 `success` 与 `SUCCESS` 等价；但自动补全给出的仍是全大写值。非法值会返回带完整可用清单的错误提示。
- **合法等级仅 5 个**：`FULL_SUCCESS` / `SUCCESS` / `NEUTRAL` / `FAIL` / `FULL_FAIL`（见 `LEVEL_SUGGESTIONS` 常量与 `ResultLevel` 枚举），不存在 `WIN`、`LOSE` 之类别名。
- **`start` 与 `menu` / 结算的在线要求不同**：`menu` 是 `execute<Player>`（仅玩家）；`end` 的结算失败提示明确写出"需玩家在线、已设置结果等级且配置存在"，即目标玩家离线会导致 `endInterfere` 返回 null 而结算失败。
- **玩家参数 vs 执行者**：`start` / `apply` / `list <target>` 的目标是**参数**，因此控制台也能用；`menu` 必须由玩家执行。
- **`info` 与 `activate`/`end`/`cancel`/`setlevel` 的补全范围不同**：`info` 用 `getAllInterferes()`（包含全部实例），其余使用 `getActiveInterferes()`（仅未结束），因此已结束的实例在补全中只会出现在 `info` 里。
- **`setlevel` 的 `faction` 补全依赖实例所属配置**：实例不存在时会退化为全部配置阵营键的并集，因此补全项可能多于该实例实际可用的阵营。
- **文案里"干扰"与"干涉"混用**：提示中既有"创建干扰""干扰信息""正在进行的干扰"，也有"干涉已结束""干涉实例"，属源码现状，检索时两种写法都要查。
- `permissionMessage = ""`（无权限时无自定义提示），`newParser = false`；源码中无额外的 `sender.hasPermission(...)` 运行时检查——权限完全由 `@CommandHeader` 的 `chotentech.command.interfere`（默认 OP）控制。
- 根命令名字面量是 `chotentech-interfere`，而给玩家看的提示文案里写的是 `/interfere activate ...`（短别名），两者都可用。
