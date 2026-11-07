# ChoTenTech 玩家数据指令（/chotentech-data）

> **根指令**：`/chotentech-data`（别名：`/cttdata`, `/quetiapinedata`, `/rpgdata`, `/chotentechdata`）
> **权限**：`chotentech.command.data`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/DataCommand.kt`

## 功能概述

`/chotentech-data` 是服务器端（管理员/开发者）对玩家数据做**读取与改写**的唯一入口。它把三类数据面聚合到同一个指令对象中：

1. **原始数据表**（字符串键值对，存储于 `DataUtil` → `QuetiapineDataBridge`）：
   `get` / `remove` / `set` / `clear` / `browse`。
   冷却数据是这张表里以 `cooldown.` 为前缀的键，单独提供 `getCooldown` / `setCooldown` / `removeCooldown` / `clearCooldown` 四个便捷入口。
2. **结构化角色字段**（直接读写 `PlayerData` 对象的强类型字段）：
   `sanity`（理智/压力/黑暗）、`hunger`（饥饿/饱食/消耗）、`nutrition`（营养四分项）、`injury`（内外伤）、`stamina`（耐力/手部耐力）。
3. **关系与杂项**：`reputation`（势力声望）、`affection`（NPC 信任度）、`interfere`（干涉事件结果结算）、`warehouse`（仓库基础格数）、`hud`（临时 HUD 文本）、`editor`（编辑器模式开关）。

> 源码顶部注释说明：本文件原本是两个独立命令对象（`ChoTenDataCommand`，header `chotentech-data`；和 `dataSubCommand.kt` 的 `DataCommand`，header `quetiapinedata`），两者都抢 `cttdata` 这个别名，注册顺序不同会让一半子命令"消失"。现已合并为单一对象，所有子命令与别名都在这里。

## 指令总览

根权限统一为 `chotentech.command.data`（默认 OP）；**所有子命令在源码中都没有单独声明权限**，因此下表"权限"列填写的都是根权限。

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttdata` | 无参数，打印指令用法树（`createModernHelper`） | `chotentech.command.data` |
| `/cttdata help` | 打印指令用法树 | `chotentech.command.data` |
| `/cttdata editor` | 切换自己的编辑器模式开关 | `chotentech.command.data` |
| `/cttdata hud <user> <key> <content> <time>` | 给目标玩家添加一条限时 HUD 文本 | `chotentech.command.data` |
| `/cttdata sanity <user> <type> <operation> <value>` | 修改理智/压力/黑暗类数值 | `chotentech.command.data` |
| `/cttdata warehouse <user> <action> <value>` | 设置/重置仓库基础格数 | `chotentech.command.data` |
| `/cttdata reputation <user> <faction>` | 查询势力声望 | `chotentech.command.data` |
| `/cttdata reputation <user> <faction> <operation> <value>` | 修改势力声望 | `chotentech.command.data` |
| `/cttdata affection <user> <npc>` | 查询 NPC 信任度 | `chotentech.command.data` |
| `/cttdata affection <user> <npc> <operation> <value>` | 修改 NPC 信任度 | `chotentech.command.data` |
| `/cttdata interfere <user> <interfereId> <level>` | 按干涉事件结果等级结算声望变化 | `chotentech.command.data` |
| `/cttdata hunger <user> <type> <operation> <value>` | 修改饥饿/饱食/消耗 | `chotentech.command.data` |
| `/cttdata nutrition <user> <type> <operation> <value>` | 修改谷/维/蛋白/脂肪 | `chotentech.command.data` |
| `/cttdata injury <user> <type> <operation> <value>` | 修改外伤/内伤 | `chotentech.command.data` |
| `/cttdata stamina <user> <type> <operation> <value>` | 修改耐力/手部耐力 | `chotentech.command.data` |
| `/cttdata get <id>` | 查询自己某项原始数据 | `chotentech.command.data` |
| `/cttdata get <id> <player>` | 查询指定玩家某项原始数据 | `chotentech.command.data` |
| `/cttdata set <player> <id> <value>` | 写入指定玩家某项原始数据 | `chotentech.command.data` |
| `/cttdata remove <id>` | 删除自己某项原始数据 | `chotentech.command.data` |
| `/cttdata remove <id> <player>` | 删除指定玩家某项原始数据 | `chotentech.command.data` |
| `/cttdata clear` | 清空自己的全部原始数据 | `chotentech.command.data` |
| `/cttdata clear <player>` | 清空指定玩家的全部原始数据 | `chotentech.command.data` |
| `/cttdata browse` | 浏览自己的全部原始数据 | `chotentech.command.data` |
| `/cttdata browse <player>` | 浏览指定玩家的全部原始数据 | `chotentech.command.data` |
| `/cttdata getCooldown <id>` | 查询自己某项冷却剩余秒数 | `chotentech.command.data` |
| `/cttdata getCooldown <id> <player>` | 查询指定玩家某项冷却剩余秒数 | `chotentech.command.data` |
| `/cttdata setCooldown <player> <id> <value>` | 设置指定玩家某项冷却（秒） | `chotentech.command.data` |
| `/cttdata removeCooldown <id>` | 移除自己某项冷却 | `chotentech.command.data` |
| `/cttdata removeCooldown <id> <player>` | 移除指定玩家某项冷却 | `chotentech.command.data` |
| `/cttdata clearCooldown` | 清空自己全部冷却 | `chotentech.command.data` |
| `/cttdata clearCooldown <player>` | 清空指定玩家全部冷却 | `chotentech.command.data` |

（合计 1 个 `main` 主体 + 21 个子命令分支组。）

## 指令详解

### `/cttdata` 与 `/cttdata help`
- **用途**：打印本指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：两者都调用 `createModernHelper()`（定义于 `MainCommand.kt`），以渐变色树状结构逐条输出子命令；输出前会按发送者的权限过滤子组件（`sender.hasPermission(...)`），并跳过 `hidden` 的字面量节点。
- **示例**：`/cttdata`

### `/cttdata editor`
- **用途**：把自己切换到/退出编辑器模式。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无参数） | 取反当前值 | — | — |
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**（runner 声明为 `execute<Player>`）。
- **行为说明**：读取 `player.getData()?.editorMode ?: false`，取反后写回 `editorMode`，并输出 `<#84D1CB>编辑器模式已设为 <true|false>。`
- **示例**：`/cttdata editor`

### `/cttdata hud <user> <key> <content> <time>`
- **用途**：给目标玩家叠加一条限时 HUD 提示文本。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 玩家参数，目标玩家 | 是 | 玩家名（TabooLib `player()` 解析） |
  | `key` | 自由文本，HUD 键名（同键会被覆盖/替换） | 是 | `key`（源码中的占位提示） |
  | `content` | 自由文本，HUD 显示内容 | 是 | `content`（源码中的占位提示） |
  | `time` | 小数，持续秒数 | 是 | `10.0` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可（无玩家分支限制）。
- **行为说明**：在异步线程执行 `HUDManager(player).addHUD(key, content, time)`；当发送者不是控制台时，回显 `${key},${content},${time}` 供确认。
- **示例**：`/cttdata hud Steve alert 你被通缉了 10.0`

### `/cttdata sanity <user> <type> <operation> <value>`
- **用途**：修改玩家的理智、压力、黑暗等心智类数值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `type` | 变量类型 | 是 | 补全：`STRESS` `SANITY` `MAXSANITY` `DARKNESS` `MAXDARKNESS`；代码另支持 `SANITYREGEN`（补全列表中**未**列出） |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 小数，操作数 | 是 | 无（自由输入） |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 目标玩家必须在 `playerDataMap` 中已有数据，否则输出 `玩家数据为空` 并终止。
  - 读取的字段：`SANITY`→`data.sanity`；`MAXSANITY`→`data.effectiveMaxSanity`；`SANITYREGEN`→`data.sanityRegeneration`；`STRESS`→`data.stress`；`DARKNESS`→`data.darkness`；`MAXDARKNESS`→`data.effectiveMaxDarkness`。其它值输出 `未知的变量类型`。
  - 写入时的钳制规则：
    - `SANITY` → `clamp(final, 0.0, effectiveMaxSanity)`
    - `MAXSANITY` → `max(final, 0.0)`
    - `SANITYREGEN` → `max(final, 0.0)`，并以**固定 UUID** `00000000-0000-0000-0000-000000000001` 先移除旧属性修饰、再以 `attributeId = "sanity_regeneration"`、`ModifierType.ADD`、`ModifierSource.OTHER` 重新添加（值 ≤ 0 时只移除不添加）。
    - `STRESS` → `clamp(final, 0.0, 100.0)`
    - `DARKNESS` → `clamp(final, 0.0, effectiveMaxDarkness)`
    - `MAXDARKNESS` → `max(final, 0.0)`
  - 运算：`+` 加、`-` 减、`=` 直接赋值；其它值输出 `未知的运算类型`。
  - 非控制台发送者会收到回显 `<#E08A98><type> = <#EFDF8F><finalValue>`。
- **示例**：`/cttdata sanity Steve SANITY - 20`、`/cttdata sanity Steve MAXSANITY = 120`

### `/cttdata warehouse <user> <action> <value>`
- **用途**：覆盖或重置玩家仓库的基础格数。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `action` | 操作 | 是 | `set` `reset`（补全列表即 `set`/`reset`） |
  | `value` | 整数，格数 | 是 | `36` `54` `72` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `set`：`value` 必须是 ≥ 1 的整数（`toIntOrNull` 失败或 < 1 时输出 `<#E08A98>格数必须为大于 0 的整数`），成功后写入 `data.warehouseBaseSlots = slots` 并回显 `<#8FD4A6>已将 <玩家名> 的仓库基础格数设为 <#EFDF8F><slots>`。
  - `reset`：把 `data.warehouseBaseSlots` 置为 `null`（即使用默认值），回显 `<#8FD4A6>已重置 <玩家名> 的仓库基础格数为默认值`。
  - 其它 `action` 输出 `<#E08A98>未知操作，可用: set <数量>, reset`。
  - 目标玩家数据为空时输出 `<#E08A98>玩家数据为空`。
  - **注意**：`int("value")` 是必经参数，即使 `reset` 也必须随便给一个整数。
- **示例**：`/cttdata warehouse Steve set 72`、`/cttdata warehouse Steve reset 0`

### `/cttdata reputation <user> <faction>`
- **用途**：查询玩家在指定势力的声望值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `faction` | 势力 ID（会 `uppercase()` 处理） | 是 | `MEG` `BNTG` `UEC` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：调用 `ReputationManager.getReputation(player, faction)`，输出 `<#DFBD7C><faction> <#A9B4C3>- 信誉: <#EFDF8F><value>`。
- **示例**：`/cttdata reputation Steve meg`

### `/cttdata reputation <user> <faction> <operation> <value>`
- **用途**：增删改玩家在某势力的声望。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `faction` | 势力 ID（`uppercase()`） | 是 | `MEG` `BNTG` `UEC` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 整数（`toLongOrNull`） | 是 | `100` `500` `1000` `-500` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`+` → `addReputation(player, faction, value)`；`-` → `addReputation(player, faction, -value)`；`=` → `setReputation(player, faction, value)`；其它 → `<#E08A98>未知操作，可用: + - =`。数值非整数时输出 `<#E08A98>数值必须为整数`。完成后重新读取并回显 `<#8FD4A6>已将 <玩家名> 的 <faction> 信誉设为 <#EFDF8F><当前值>`。
- **示例**：`/cttdata reputation Steve MEG + 500`

### `/cttdata affection <user> <npc>`
- **用途**：查询玩家对某个 NPC 的信任度。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `npc` | NPC ID（不转大写） | 是 | `NPC_A` `NPC_B` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`ReputationManager.getAffection(player, npc)`，输出 `<#DFBD7C><npc> <#A9B4C3>- 信任度: <#EFDF8F><value>`。
- **示例**：`/cttdata affection Steve NPC_A`

### `/cttdata affection <user> <npc> <operation> <value>`
- **用途**：增删改玩家对某个 NPC 的信任度。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `npc` | NPC ID | 是 | `NPC_A` `NPC_B` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 整数 | 是 | `100` `500` `1000` `-500` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`+`→`addAffection`、`-`→`addAffection(-value)`、`=`→`setAffection`；其它输出 `<#E08A98>未知操作，可用: + - =`。数值非整数输出 `<#E08A98>数值必须为整数`。成功后回显 `<#8FD4A6>已将 <玩家名> 对 <npc> 的信任度设为 <#EFDF8F><当前值>`。
- **示例**：`/cttdata affection Steve NPC_A = 500`

### `/cttdata interfere <user> <interfereId> <level>`
- **用途**：以管理员身份触发某干涉事件的结果等级结算，直接施加声望变化。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `interfereId` | 干涉事件 ID | 是 | 补全来自 `interfereConfigs.keys`（已加载的全部干涉配置 ID） |
  | `level` | 结果等级（`uppercase()` 后 `ResultLevel.valueOf`） | 是 | `FULL_SUCCESS` `SUCCESS` `NEUTRAL` `FAIL` `FULL_FAIL` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 等级无法解析时输出 `<#E08A98>未知结果等级，可用: <displayName>(<name>) ...`（`displayName` 依次为 完胜/成功/平手/失败/完败）。
  2. `interfereConfigs[interfereId]` 不存在时输出 `<#E08A98>未找到干扰事件: <interfereId>`。
  3. 调用 `ReputationManager.applyInterfereReputation(player, interfereId, level)`；返回空 Map 时输出 `<#E08A98>该结果等级未配置声望变化`。
  4. 否则按 `formatReputationReport(player, config.name, level, changes)` 生成多行报告，逐行（跳过空行）发送给指令发送者。
- **示例**：`/cttdata interfere Steve raid_boss FULL_SUCCESS`

### `/cttdata hunger <user> <type> <operation> <value>`
- **用途**：修改自定义饥饿/饱食/消耗数值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `type` | 变量类型 | 是 | `HUNGER` `SATURATION` `CONSUMPTION` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 小数 | 是 | `100.0` `50.0` `10.0` `1.0` `-10.0` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：异步执行；读取 `customHunger` / `customSaturation` / `consumption`，运算后写入并 `max(final, 0.0)` 钳制下限；非控制台发送者收到回显 `<#E08A98><type> = <#EFDF8F><finalValue>`。未知类型提示 `未知的变量类型，可用: HUNGER SATURATION CONSUMPTION`；未知运算提示 `未知的运算类型，可用: + - =`；玩家数据为空提示 `<#E08A98>玩家数据为空`。
- **示例**：`/cttdata hunger Steve HUNGER - 10.0`

### `/cttdata nutrition <user> <type> <operation> <value>`
- **用途**：修改四类营养值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `type` | 变量类型 | 是 | `GRAIN` `VITAMIN` `PROTEIN` `FAT` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 小数 | 是 | `100.0` `50.0` `10.0` `1.0` `-10.0` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：读取/写入 `data.grain` / `data.vitamin` / `data.protein` / `data.fat`，均以 `max(final, 0.0)` 钳制；其余消息与 `hunger` 一致（未知类型提示 `未知的变量类型，可用: GRAIN VITAMIN PROTEIN FAT`）。
- **示例**：`/cttdata nutrition Steve PROTEIN + 50.0`

### `/cttdata injury <user> <type> <operation> <value>`
- **用途**：修改外伤/内伤累计伤害值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `type` | 变量类型 | 是 | `EXTERNAL` `INTERNAL` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 小数 | 是 | `100.0` `50.0` `10.0` `1.0` `-10.0` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`EXTERNAL` → `data.externalInjury`，`INTERNAL` → `data.internalInjury`；写入前 `max(final, 0.0)`；未知类型提示 `未知的变量类型，可用: EXTERNAL INTERNAL`。
- **示例**：`/cttdata injury Steve INTERNAL = 0.0`

### `/cttdata stamina <user> <type> <operation> <value>`
- **用途**：修改耐力与手部耐力。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家 | 是 | 玩家名 |
  | `type` | 变量类型 | 是 | `STAMINA` `HANDSTAMINA` |
  | `operation` | 运算方式 | 是 | `+` `-` `=` |
  | `value` | 小数 | 是 | `100.0` `50.0` `10.0` `1.0` `-10.0` |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`STAMINA` → `data.stamina`，`HANDSTAMINA` → `data.handStamina`；`max(final, 0.0)` 钳制；未知类型提示 `未知的变量类型，可用: STAMINA HANDSTAMINA`。
- **示例**：`/cttdata stamina Steve HANDSTAMINA = 100.0`

### `/cttdata get <id>`
- **用途**：查询**自己**某项原始数据的字符串值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 自由文本，数据键（完整键名，如 `level.combat`、`cooldown.daily`） | 是 | 无（自由输入） |
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**（发送者非玩家时输出 `Player_Only_Command` 语言文本）。
- **行为说明**：异步执行 `DataUtil.getDataRaw(player, key)`，以语言键 `PlayerData_Fetch`（参数：玩家名、键、值，值缺失时显示 `<i>null`）经 `infoS` 输出（不重复发往控制台）。
- **示例**：`/cttdata get level.combat`

### `/cttdata get <id> <player>`
- **用途**：查询**指定玩家**某项原始数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 数据键 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可（目标玩家解析失败时输出 `Player_Not_Exist`）。
- **行为说明**：同上，只是目标换为参数指定的玩家。
- **示例**：`/cttdata get level.combat Steve`

### `/cttdata set <player> <id> <value>`
- **用途**：写入指定玩家某项原始数据（字符串）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家 | 是 | 玩家名 |
  | `id` | 数据键 | 是 | 无 |
  | `value` | 任意字符串值 | 是 | 无 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：异步调用 `DataUtil.setData(player, key, dvalue)`，输出语言键 `PlayerData_Modify`（玩家名、键、值）。**没有"给自己设置"的简写分支**，`:player` 参数必填。
- **示例**：`/cttdata set Steve level.combat 30`

### `/cttdata remove <id>`
- **用途**：删除**自己**某项原始数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 数据键 | 是 | 无 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`DataUtil.removeData(player, key)`，输出语言键 `PlayerData_Remove`。
- **示例**：`/cttdata remove level.combat`

### `/cttdata remove <id> <player>`
- **用途**：删除指定玩家某项原始数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 数据键 | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家。
- **示例**：`/cttdata remove level.combat Steve`

### `/cttdata clear`
- **用途**：清空**自己**的全部原始数据。
- **参数**：无。
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`DataUtil.resetAllData(player)` → 直接 `clear()` 该玩家的数据映射；输出语言键 `PlayerData_Clear`。**不可撤销**。
- **示例**：`/cttdata clear`

### `/cttdata clear <player>`
- **用途**：清空指定玩家的全部原始数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家；玩家解析失败输出 `Player_Not_Exist`。
- **示例**：`/cttdata clear Steve`

### `/cttdata browse`
- **用途**：分节浏览**自己**的全部原始数据。
- **参数**：无。
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：取 `DataUtil.getAllDataRaw(player)` 全量键值，交给 `browseDataMap` 拼装：标题用语言键 `PlayerData_Browse_Title`，每项用 `PlayerData_Browse_Section`（键, 值）并以 `<br>` 分隔；Map 为空时额外提示 `PlayerData_Browse_Empty`。最终文本把 `cooldown.` / `level.` / `exp.` 三个前缀替换为带下划线与配色的标记，便于阅读。
- **示例**：`/cttdata browse`

### `/cttdata browse <player>`
- **用途**：分节浏览指定玩家的全部原始数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家。
- **示例**：`/cttdata browse Steve`

### `/cttdata getCooldown <id>`
- **用途**：查询**自己**某项冷却的剩余时间。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 冷却 ID（内部键为 `cooldown.<id>`） | 是 | 无 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`DataUtil.getCooldownLeftLong(player, key)` 得到剩余毫秒，再 `/1000` 转为**秒**；输出语言键 `PlayerCooldown_Fetch`，无记录时显示 `<i>null`。
- **示例**：`/cttdata getCooldown daily`

### `/cttdata getCooldown <id> <player>`
- **用途**：查询指定玩家某项冷却剩余时间。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 冷却 ID | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家。
- **示例**：`/cttdata getCooldown daily Steve`

### `/cttdata setCooldown <player> <id> <value>`
- **用途**：设置指定玩家某项冷却的到期时间（从现在起算的秒数）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家 | 是 | 玩家名 |
  | `id` | 冷却 ID | 是 | 无 |
  | `value` | 小数，**秒** | 是 | 无 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`DataUtil.setCooldown(player, key, dvalue.toDouble())` 会把 `cooldown.<id>` 写成 `当前毫秒 + value*1000`；输出语言键 `PlayerCooldown_Modify`。**没有"给自己设置"的简写分支**。
- **示例**：`/cttdata setCooldown Steve daily 3600`

### `/cttdata removeCooldown <id>`
- **用途**：移除**自己**某项冷却。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 冷却 ID | 是 | 无 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`DataUtil.resetCooldown(player, key)` 删除 `cooldown.<id>` 键；输出语言键 `PlayerCooldown_Remove`。
- **示例**：`/cttdata removeCooldown daily`

### `/cttdata removeCooldown <id> <player>`
- **用途**：移除指定玩家某项冷却。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 冷却 ID | 是 | 无 |
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家。
- **示例**：`/cttdata removeCooldown daily Steve`

### `/cttdata clearCooldown`
- **用途**：清空**自己**的全部冷却记录。
- **参数**：无。
- **权限**：`chotentech.command.data`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`DataUtil.resetAllCooldown(player)` 删除所有以 `cooldown.` 开头的键；输出语言键 `PlayerCooldown_Clear`。
- **示例**：`/cttdata clearCooldown`

### `/cttdata clearCooldown <player>`
- **用途**：清空指定玩家的全部冷却记录。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.data`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：同上，作用于参数玩家。
- **示例**：`/cttdata clearCooldown Steve`

## 备注 / 注意事项

1. **所有子命令都未单独声明权限**：源码中 21 个 `subCommand` 都没有 `permission` 参数，实际拦截依赖根指令权限 `chotentech.command.data`（`permissionDefault = PermissionDefault.OP`，`permissionMessage = ""`）。因此想放行某个子命令，只能整体授权该节点。
2. **两种"执行者要求"来源不同**：
   - `editor` 用 `execute<Player>`，控制台直接无法执行；
   - `get`/`remove`/`clear`/`browse`/`getCooldown`/`removeCooldown`/`clearCooldown` 的**无 player 参数分支**会执行 `sender as? Player? ?: run { severeS(Player_Only_Command) }`，控制台会收到"仅玩家"提示并中止；
   - `set` / `setCooldown` **没有**自我分支，必须显式给出 `<player>`。
3. **`hud`/`sanity`/`hunger`/`nutrition`/`injury`/`stamina` 走异步**（`submitAsync`），先改内存对象（`playerDataMap`/`PlayerData`），落盘由存档流程负责；`warehouse`/`reputation`/`affection`/`interfere` 为同步执行。
4. **`sanity` 的 `SANITYREGEN` 不在补全列表里**：补全只列出 `STRESS SANITY MAXSANITY DARKNESS MAXDARKNESS`，但代码分支里存在 `SANITYREGEN`，需手动输入。它不使用 `PlayerData` 直写，而是通过 `AttributeManager` 的固定 UUID 修饰符实现（覆盖式，不会叠加）。
5. **`warehouse` 的 `reset` 也必须给 `value`**：`int("value")` 位于 `dynamic("action")` 之下且为必经节点，`reset` 时随便给一个整数即可（其值被忽略）。
6. **`player()` 参数强转风险**：`hud`/`sanity`/`warehouse`/`reputation`/`affection`/`interfere` 都用 `context.player("user").castSafely<Player>()!!`，玩家解析不到时是 NPE 而非友好提示；`get`/`set`/`remove`/`clear`/`browse`/冷却系列用 `castSafely<Player>() ?: run { Player_Not_Exist }`，会正常提示。
7. **重复别名历史**：本对象同时提供 `cttdata` / `quetiapinedata` / `rpgdata` / `chotentechdata` 四个别名（合并前后两套别名都在这里），不要在其他命令对象里再次抢占这些名字。
8. **`clear` 与 `clearCooldown` 都是破坏性操作**：前者清空整张原始数据表（含 `level.*`、`exp.*` 与全部 `cooldown.*`），后者只删除 `cooldown.` 前缀键。
9. **"可选值/自动补全"列的两类来源**：由 `suggestion<CommandSender> {...}` 明确给出的（如 `sanity` 的 `type`/`operation`、`warehouse` 的 `action`、`reputation`/`affection` 的 `operation`、`interfere` 的 `interfereId`/`level`）是真正的 Tab 补全；而写在 `int("value", listOf(...))` / `decimal("value", listOf(...))` / `decimal("time", listOf("10.0"))` 第二个参数里的数值，是随参数声明一起给出的候选值提示（源码位置不同，本文档统一放在"可选值/自动补全"列中列出，输入时并非强制取值）。
10. 输出文案全部来自语言文件（`PlayerData_Fetch`、`PlayerData_Modify`、`PlayerData_Remove`、`PlayerData_Clear`、`PlayerCooldown_Fetch`、`PlayerCooldown_Modify`、`PlayerCooldown_Remove`、`PlayerCooldown_Clear`、`PlayerData_Browse_Title`、`PlayerData_Browse_Section`、`PlayerData_Browse_Empty`、`Player_Only_Command`、`Player_Not_Exist`），修改语言文件即可改文案，无需改代码。`sanity` 等结构化字段类的提示则是硬编码的中文文案。
