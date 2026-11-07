# 跳过教程（Skip Tutorial）

> **根指令**：`/chotentech-skiptutorial`（别名：`/cttskiptutorial`, `/skiptutorial`）
> **权限**：`chotentech.command.skiptutorial`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/SkipTutorialCommand.kt`

## 功能概述

让处于**教程任务进行中**的玩家跳过教学流程：把共享数据的 `isCompletedTutorial` 置为 `true` 并落库，然后把玩家传送到正常出生点（`Area0` 世界的 `-6.0, 14.0, 5.0`）。

本文件**只有一个** `@CommandBody`（无参主体），没有子指令或参数。`newParser = false`，`permissionDefault = PermissionDefault.TRUE`（默认所有人可用）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/skiptutorial` | 跳过教学流程并传送至出生点 | `chotentech.command.skiptutorial` |

> 完整指令名为 `/chotentech-skiptutorial`，可用别名 `/cttskiptutorial`、`/skiptutorial`。

## 指令详解

### `/chotentech-skiptutorial`

- **用途**：跳过教程任务，标记教程已完成并把玩家送回出生点。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.skiptutorial`（默认：所有人）。未声明 `permissionMessage`，无权限时走框架默认提示。
- **执行者要求**：仅玩家（`execute<Player>`）。
- **行为说明**：
  1. **前置校验**：读取执行者的 Chemdah 档案——`if (sender.isChemdahProfileLoaded) sender.chemdahProfile else null`；随后检查其 `questMap` 中是否存在 `template.id` 等于 `tutorial`（`equals(..., ignoreCase = true)`，即大小写不敏感）的任务。
     - 只有在**教程任务已接取（进行中）**时才允许继续；未接取、已完成、已失败都会被拒绝。
     - 拒绝时输出 `<#E08A98>你还没有接取教程任务,无法跳过` 并结束。
  2. **写入完成标记**：`ProfileManager.getSharedData(uuid).isCompletedTutorial = true`，随后调用 `ProfileManager.saveSharedData(uuid)` 立刻落库（注意：是**共享数据**，对所有档案生效）。
  3. **传送**：取世界 `Area0`（`Bukkit.getWorld("Area0")`）；若不存在则退化为 `Bukkit.getWorlds().first()`。
     - 若最终世界名恰为 `Area0`：传送到 `Location(world, -6.0, 14.0, 5.0)`。
     - 否则传送到该世界的 `spawnLocation`。
  4. 输出 `<#8FD4A6>已跳过教学,传送至出生点`。
  - 指令层未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/skiptutorial`
  - `/cttskiptutorial`
  - `/chotentech-skiptutorial`

## 备注 / 注意事项

- **子指令核对**：源码仅 1 个 `@CommandBody`（`main`），**无 `subCommand`、无 `literal(`、无 `dynamic(`、无 `player(`**。
- 该指令**不可重复使用**：一旦 `isCompletedTutorial` 为 `true`，Chemdah 侧教程任务通常已完成/不再进行中，再次执行会被"你还没有接取教程任务"拦截。
- 判定依赖 Chemdah：若 `isChemdahProfileLoaded` 为 `false`（档案未加载），则 `profile` 为 `null`，`tutorialActive` 为 `false`，同样会被拒绝。
- 传送目标写死在源码中（`Area0` 的 `-6.0, 14.0, 5.0`），非配置项；若服务器主世界名不是 `Area0`，会退化到**第一个世界的出生点**（`getWorlds().first()` 的顺序由服务端决定）。
- `isCompletedTutorial` 写在 **SharedData（跨档案）** 上，因此对玩家所有档案一次性生效；与 `independent meta` 的按档案隔离不同。
- 权限默认 `TRUE`（所有人），源码未声明 `permissionMessage`。
- 本对象**没有**被挂到 `/ctt` 主指令下，只能通过上述三个名字调用。
