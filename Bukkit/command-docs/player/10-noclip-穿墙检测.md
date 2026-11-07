# 穿墙检测（Noclip 检查）

> **根指令**：`/noclips`（别名：`/noclip`）
> **权限**：`chotentech.command.checknoclips`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/NoclipCommand.kt`

## 功能概述

**诊断/调试用指令**：把执行者当前所在**层级（Level）**的全部切出点（Noclip Point）以聊天文本形式列出，用于排查"某个切出点为什么不可用/为什么被跳过"。

输出内容包括：
- 玩家名与当前层级名，以及该层级的随机切出模式（若启用）；
- 每个切出点的编号（`NOCLIP1`、`NOCLIP2`…）、名称、目标层级（或"没有数据"）、`[自动]` 标记、`[随机禁用]` 标记；
- 该切出点的**条件**标签（物品 / 费用 / 协作 / 信号 / 限制 / 条件）。

本文件只有 1 个 `@CommandBody`（无参主体），没有子指令或参数。`@CommandHeader` 使用 `newParser = false`，`permissionMessage = ""`，`permissionDefault = PermissionDefault.OP`。

> 注意：文件名是 `NoclipCommand.kt`，但 Kotlin 对象名为 `NoclipsCommand`，根指令名为 `noclips`（复数）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/noclips` | 以文本列出当前层级的所有切出点及其状态 | `chotentech.command.checknoclips` |

> 可用别名 `/noclip`。

## 指令详解

### `/noclips`

- **用途**：查看自己当前层级的全部切出点状态（用于核对切出点是否可用、是否被随机禁用、是否缺少探索数据、需要哪些条件）。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.checknoclips`（默认 OP；无权限提示文本为空）。
- **执行者要求**：仅玩家（`execute<CommandSender>` 后立刻 `val player = sender as? Player ?: return@execute`；控制台执行时**静默返回**）。
- **行为说明**：
  1. 取玩家数据：`player.getData()`；为 `null` 直接返回（无任何提示）。
  2. 取当前层级 `data.activeLevel`；`activeLevel?.noclips` 为切出点列表。
  3. **输出头部**：
     - 分隔线 `<#F2F5FA><st>（大量空格）<reset>`（`<st>` 为删除线标签）；
     - `<#8FD4A6><玩家名> - 层级 <activeLevel?.name ?: "???">`；若 `activeLevel.randomNoclip != null` 追加 ` <#6E7A8C>[随机: <模式名>]`；
     - 再一条分隔线。
  4. **无切出点**：若 `noclips` 为 `null` 或空，输出 ` <#E08A98>未找到任何可用切出点。` + 一个空行后结束。
  5. **逐个列出**：`num` 从 `1` 递增，对每个 `noclip`：
     - `randomDisabled = noclip.name != null && noclip.name !in enabledNames`，其中 `enabledNames = RandomNoclipSelector.getEnabledNoclips(uuid, activeLevel)`（随机模式下被抽中的切出点集合；取不到时为 `emptySet()`）。
     - `autoTag = " <#A9B4C3>[自动]"`（当 `noclip.autoNoclip` 为 `true`）。
     - **若 `player.gameMode.isInvulnerable` 为 `true`**（创造/无敌状态，便于管理员核对全部切出点）：
       ` <#6E7A8C>| <#76a76d>NOCLIP<num></#76a76d> <#dad8d8><name></#dad8d8> <#cfae6e>→ <目标层级显示></#cfae6e><autoTag>`，
       若 `randomDisabled` 追加 ` <#e0a1a1>[随机禁用]`。
     - **否则（普通状态）**分三种情况：
       - `randomDisabled` → 灰色弱化行：` <#6E7A8C>| <#858585>NOCLIP<num></#858585> <#747474><name></#747474> <#6E7A8C>[随机禁用]<reset>`；
       - 已探索（`data.exploreredNoclips[data.activeLevelStr]?.contains(noclip.name) == true`）→ ` <#6E7A8C>| <#76a76d>NOCLIP<num></#76a76d> <#dad8d8><name></#dad8d8> <#5FA8A3>→ <目标层级显示></#5FA8A3><autoTag>`；
       - 未探索 → ` <#6E7A8C>| <#76a76d>NOCLIP<num></#76a76d> <#dad8d8><name></#dad8d8> <#6E7A8C>[没有数据]<reset><autoTag>`。
     - 若条件标签非空，紧随其后输出一行 `   <#6E7A8C><条件标签>`。
  6. **尾部**：再输出一条分隔线（`<#F2F5FA><st>…`，无 `<reset>`），最后用 `sendStringAsComponent(str.toString())` 一次性发送整段文本。
- **目标层级显示规则**（`targetLevelDisplay`）：
  | `targetLevel` 值 | 显示 |
  |---|---|
  | `null` | `未知` |
  | 含 `;`（多目标写法） | `多个可能层级` |
  | 其它 | `levelsList[targetLevel]?.name`，取不到则 `未知` |
- **条件标签规则**（`buildConditionTag`，前缀 `条件: `，各项以空格连接）：
  | 判断字段 | 标签 |
  |---|---|
  | `cond.item != null` | `物品` |
  | `cond.car != null` | `费用` |
  | `cond.coop != null` | `协作` |
  | `cond.signal != null` | `信号` |
  | `cond.dropbag != null` | `限制` |
  | `cond.condition != null` | `条件` |

  若 `noclip.conditions` 为 `null` 或以上全为 `null`，则不输出该行。
- **示例**：
  - `/noclips`
  - `/noclip`

## 备注 / 注意事项

- **子指令核对**：源码仅 1 个 `@CommandBody`（`main` → `mainCommand`），**无 `subCommand`、无 `literal(`、无 `dynamic(`、无 `player(`**；也**没有任何 `suggestion`**。
- **看不到别人的数据**：指令不接收目标玩家参数，永远只检查执行者自己。
- **控制台静默失败**：`sender as? Player ?: return@execute`，控制台执行无输出；`player.getData()` 为 `null` 时同样无输出。
- **`isInvulnerable` 分支**：`gameMode.isInvulnerable` 为真时（创造/旁观/无敌标记）会**无条件显示目标层级**（不再判断 `[没有数据]`），仅额外标注 `[随机禁用]`，这是为管理员核对全部切出点设计的"上帝视角"。
- **`[随机禁用]` 只在随机切出模式下有意义**：由 `RandomNoclipSelector.getEnabledNoclips(uuid, level)` 的返回集合决定；若当前层级未启用随机模式（头部无 `[随机: …]`），一般不会出现该标记（除非集合为空且 `noclip.name` 非空）。
- **未探索 ≠ 不可用**：`[没有数据]` 表示该切出点尚未被玩家探索过（`exploreredNoclips` 中无记录），不是配置错误。
- **一个 Noclip 可能只显示部分信息**：`[自动]` 标记（`autoNoclip`）与条件标签可能同时出现，条件标签独占下一行（缩进 3 个空格）。
- 全部输出通过 `io.github.zzzyyylllty.sertraline.logger.sendStringAsComponent` 发送，支持 MiniMessage 颜色标签与 `<st>`（删除线）等标签。
- 权限为 `chotentech.command.checknoclips`（默认 OP），属于调试/管理权限；本对象**没有**被挂到 `/ctt` 主指令下，只能通过 `/noclips` 或 `/noclip` 调用。
