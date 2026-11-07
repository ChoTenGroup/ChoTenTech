# 干涉指令（/chotentech-interfere）

> **根指令**：`/chotentech-interfere`（别名：`/cttinterfere`, `/interfere`）
> **权限**：`chotentech.command.interfere`（默认：OP，`PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/InterfereCommand.kt`

## 功能概述

「干涉（Interfere）」事件的**生命周期管理**指令。干涉实例由 `InterfereManager` 维护（主键 `instanceId`，一个玩家可同时持有多个实例；同一玩家 + 同一配置最多只有一个未结束实例，重复 `start` 会替换旧的未结束实例）。

实例状态：`InterfereStatus.PENDING`（未开始）/ `ACTIVE`（进行中）/ `FINISHED`（已结束）。

指令能力：创建 → 激活 → 设置各阵营结果等级 → 结算（应用声望变更）/ 取消；另有列表、详情、以及一个直接跳过实例流程的「一次性应用声望」入口 `apply`。

结果等级枚举 `ResultLevel`（`data/Interfere.kt`）：`FULL_SUCCESS`(完胜) / `SUCCESS`(成功) / `NEUTRAL`(平手) / `FAIL`(失败) / `FULL_FAIL`(完败)。

该指令同时被挂到主指令树下：`MainCommand` 中有 `@CommandBody val interfere = InterfereCommand`，因此 `/ctt interfere ...` 也可用。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttinterfere` | 无参数：打印指令树（`createModernHelper()`） | `chotentech.command.interfere` |
| `/cttinterfere start <target> <configId>` | 为玩家创建干扰实例（初始 `PENDING`） | `chotentech.command.interfere` |
| `/cttinterfere activate <instanceId>` | 激活实例，状态转为进行中 | `chotentech.command.interfere` |
| `/cttinterfere setlevel <instanceId> <faction> <level>` | 设置某阵营的结果等级 | `chotentech.command.interfere` |
| `/cttinterfere end <instanceId>` | 结束并结算实例，应用声望变更 | `chotentech.command.interfere` |
| `/cttinterfere cancel <instanceId>` | 取消实例，**不应用**声望变更 | `chotentech.command.interfere` |
| `/cttinterfere list` | 列出全部未结束的实例 | `chotentech.command.interfere` |
| `/cttinterfere list <target>` | 只列出该玩家的未结束实例 | `chotentech.command.interfere` |
| `/cttinterfere info <instanceId>` | 查看实例详情 | `chotentech.command.interfere` |
| `/cttinterfere menu` | 打开干涉菜单（`InterfereMenu`） | `chotentech.command.interfere` |
| `/cttinterfere apply <target> <interfereId> <level>` | 不经实例流程，直接按配置给玩家应用一次声望变更 | `chotentech.command.interfere` |

> 所有 `@CommandBody` 均未单独声明 `permission`，统一继承 header 的 `chotentech.command.interfere`（默认 OP）。

## 指令详解

### `/cttinterfere`（无参数）

- **用途**：打印分层指令帮助。
- **参数**：无。
- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()`）。
- **行为说明**：`main = mainCommand { createModernHelper() }`。
- **示例**：`/interfere`

### `/cttinterfere start <target> <configId>`

- **用途**：为某个在线玩家创建一个干扰实例（此时还未开始，状态 `PENDING`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（必须在线） | 必填 | TabooLib `player("target")` 自动补全在线玩家名 |
  | `configId` | 干扰配置 ID，对应 `interfereConfigs` 的键 | 必填 | `interfereConfigs.keys.toList()` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`；`context.player("target").castSafely<Player>()!!`）。
- **行为说明**：
  - `interfereConfigs[configId]` 不存在 → 「未找到干扰配置: `<configId>`」；
  - `InterfereManager.startInterfere(target, configId)` 返回 null → 「无法为 `<玩家名>` 创建干扰实例」；
  - 成功则输出三行：已创建干扰（配置显示名）、实例 ID 与状态、提示「使用 `/interfere activate <instanceId>` 将其激活」。
  - 注意：`target` 是 `player(...)` 参数，因此**目标必须在线**才能通过框架解析。
- **示例**：`/interfere start Steve raid_alpha`

### `/cttinterfere activate <instanceId>`

- **用途**：把待开始的实例转为进行中。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 实例不存在 → 「未找到干涉实例: `<instanceId>`」；
  - `InterfereManager.activateInterfere(instanceId)` 返回 true → 输出「已激活 `<玩家名>` 的干涉: `<干扰名>`」「实例ID: … | 状态: 进行中」；
  - 返回 false → 「无法激活 `<instanceId>`，可能已开始或已结束」。
- **示例**：`/interfere activate interference-1`

### `/cttinterfere setlevel <instanceId> <faction> <level>`

- **用途**：为实例中某个阵营设定结果等级（决定结算时的声望变化强度）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |
  | `faction` | 阵营名（`interfereConfigs[configId].scores[x].changes` 的键） | 必填 | 先按该实例自身配置的 `scores` 中出现的阵营名补全；取不到实例/配置时回落到**所有配置中**出现过的阵营名去重 |
  | `level` | 结果等级 | 必填 | `FULL_SUCCESS` `SUCCESS` `NEUTRAL` `FAIL` `FULL_FAIL` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `level` 用 `ResultLevel.valueOf(context["level"].uppercase())` 解析，失败 → 「未知结果等级，可用: 完胜(FULL_SUCCESS) 成功(SUCCESS) 平手(NEUTRAL) 失败(FAIL) 完败(FULL_FAIL)」（`displayName(name)` 形式列全）；
  - 实例不存在 → 「未找到干涉实例: `<instanceId>`」；
  - `InterfereManager.setFactionLevel(instanceId, faction, level)` 成功 → 「已将 `<玩家名>` 的阵营 `<faction>` 结果设为 `<等级显示名>`」；
  - 失败 → 「设定失败，干涉实例已结束」。
- **示例**：`/interfere setlevel interference-1 rebels SUCCESS`

### `/cttinterfere end <instanceId>`

- **用途**：结束实例并**结算**声望变更。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 实例不存在 → 「未找到干涉实例: `<instanceId>`」；
  - 已 `FINISHED` → 「`<instanceId>` 已经结束了」；
  - `InterfereManager.endInterfere(instanceId)` 返回 null → 「结算失败，`<instanceId>` 仍未结束（需玩家在线、已设置结果等级且配置存在）」；
  - 成功则输出：干涉已结束（玩家名 + 干扰名）、「结果:」各阵营 `阵营:等级显示名` 列表、「声望变更:」逐条 `阵营: +N` / `阵营: -N`（正数带 `+`，颜色区分正负）。
- **示例**：`/interfere end interference-1`

### `/cttinterfere cancel <instanceId>`

- **用途**：取消实例，**不应用**声望变更。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID | 必填 | `InterfereManager.getActiveInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 实例不存在 → 「未找到干涉实例: `<instanceId>`」；
  - `InterfereManager.cancelInterfere(instanceId)` 成功 → 「已取消 `<玩家名>` 的干涉 `<干扰名>`，不应用声望变更」；
  - 失败 → 「无法取消 `<instanceId>`，可能已结束」。
- **示例**：`/interfere cancel interference-1`

### `/cttinterfere list`

- **用途**：列出服务器上**全部未结束**的干涉实例。
- **参数**：无。
- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`sendList(sender, null, null)`：取 `InterfereManager.getActiveInterferes()`；为空 → 「当前没有正在进行的干扰」；否则输出标题 `=== 正在进行的干扰 (N) ===`，每条两行：`玩家名 | 干扰名 | 状态 | 结果`（有 `factionLevels` 时列出全部「阵营:等级」，否则按状态显示「未开始」/「进行中」/「无结果」），第二行灰色显示 `instanceId`。
- **示例**：`/interfere list`

### `/cttinterfere list <target>`

- **用途**：只列出某个玩家的未结束实例。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（必须在线） | 必填 | 在线玩家名自动补全 |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`sendList(sender, target.uniqueId.toString(), target.name)`，内部调用 `InterfereManager.getActiveInterferes(filterUuid)`。标题变为 `=== <玩家名> 的正在进行的干扰 (N) ===`；为空时输出「当前没有 `<玩家名>` 的正在进行的干扰」。格式同上。
- **示例**：`/interfere list Steve`

### `/cttinterfere info <instanceId>`

- **用途**：查看实例的完整详情。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `instanceId` | 干涉实例 ID（**含已结束的实例**） | 必填 | `InterfereManager.getAllInterferes().map { it.instanceId }` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：实例不存在 → 「未找到干涉实例: `<instanceId>`」。否则输出：
  - `=== 干扰信息: <instanceId> ===`
  - 玩家、配置名（`c.name`）、配置 ID、状态（`c.status`）；
  - 若 `factionLevels` 非空 → 「阵营结果:」逐条 `阵营: 等级显示名`；否则按状态显示「结果: 未开始 / 进行中 / 无结果」；
  - 开始时间（`yyyy-MM-dd HH:mm:ss`）；若 `endTime != null` 再输出结束时间。
  - 注意补全来自 `getAllInterferes()`（包含已结束实例），与 `activate` / `setlevel` / `end` / `cancel` / `list` 用的 `getActiveInterferes()` 不同。
- **示例**：`/interfere info interference-1`

### `/cttinterfere menu`

- **用途**：打干涉菜单界面（`InterfereMenu`）。
- **参数**：无。
- **权限**：`chotentech.command.interfere`。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：`InterfereMenu().open(sender)`，无额外前置校验。
- **示例**：`/interfere menu`

### `/cttinterfere apply <target> <interfereId> <level>`

- **用途**：**绕过实例流程**，直接按某个干扰配置给玩家应用一次指定结果等级的声望变更（用于补发/修正）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（必须在线） | 必填 | 在线玩家名自动补全 |
  | `interfereId` | 干扰配置 ID（`interfereConfigs` 的键） | 必填 | `interfereConfigs.keys.toList()` |
  | `level` | 结果等级 | 必填 | `FULL_SUCCESS` `SUCCESS` `NEUTRAL` `FAIL` `FULL_FAIL` |

- **权限**：`chotentech.command.interfere`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `level` 解析失败 → 「未知结果等级，可用: …」（与 `setlevel` 同一套提示）；
  - 配置不存在 → 「未找到干扰配置: `<interfereId>`」；
  - `ReputationManager.applyInterfereReputation(target, interfereId, level)` 返回空列表 → 「该结果等级未配置声望变化」；
  - 否则输出「已为 `<玩家名>` 应用干扰 `<配置名>` `<等级显示名>` 声望变更:」并逐条列出 `阵营: +N` / `阵营: -N`。
- **示例**：`/interfere apply Steve raid_alpha SUCCESS`

## 备注 / 注意事项

- **`activate` / `setlevel` / `end` / `cancel` / `list` 的补全只覆盖「未结束」实例**（`getActiveInterferes()`），而 `info` 的补全覆盖全部实例（`getAllInterferes()`）。
- **`end` 的成功条件较严**：源码注释写明需要「玩家在线、已设置结果等级且配置存在」，任一不满足都会返回 null 并提示结算失败。
- **`cancel` 不产生声望变化**，`end` 才会结算；两者对 `FINISHED` 实例分别提示「无法取消…可能已结束」/「已经结束了」。
- **`start` / `list <target>` / `apply` 使用 `player(...)` 参数**，因此目标玩家**必须在线**，否则框架层就会拦下（不会进入业务分支）；而 `list`（无参）与 `info` 不要求目标在线。
- **`setlevel` 的阵营补全依赖实例状态**：只有能取到实例与配置时，才按该配置 `scores` 中出现的阵营去重补全，否则回落到「所有配置中出现过的阵营」。
- **`apply` 与实例体系互不影响**：它不创建/不结束任何实例，只按配置直接改声望，重复执行会**重复叠加**声望变化。
- **等级名大小写不敏感**：`setlevel` / `apply` 都对输入做 `uppercase()` 后 `ResultLevel.valueOf`，因此 `success` 与 `SUCCESS` 等价；但必须是枚举名（如 `FULL_SUCCESS`），不是中文显示名（`完胜` 会解析失败）。
- **权限粒度为整棵树**（默认 OP）：查看类（`list` / `info` / `menu`）与破坏类（`end` / `cancel` / `apply`）共用 `chotentech.command.interfere`。
- `/ctt interfere ...` 是同一指令树的另一入口（`MainCommand` 中 `val interfere = InterfereCommand`），那条路径上未额外声明权限，沿用本 header 的 `chotentech.command.interfere`。
