# 丢弃锁定（/drop）

> **根指令**：`/drop`（别名：无，`aliases = []`）
> **权限**：`chotentech.command.drop`（默认：所有人，`PermissionDefault.TRUE`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/function/DropLockManager.kt`

## 功能概述

历史上这是一个「个人丢弃锁」开关：玩家可以把自己的丢弃锁关掉，让插件阻止自己丢出物品。指令会把开关状态写进 `DropLockManager.kt` 顶部的 `dropLockEnabled`（`ConcurrentHashMap<String, Boolean>`，**内存在线态，不落库**）。

**当前版本该功能已整体停用**（源码注释多处写明「取消丢弃锁」「Disabled — 取消丢弃锁」）：

- `isDropLocked(uuid: String): Boolean = false` —— 硬编码恒为 `false`（函数名语义是「是否被锁定」，恒 false 表示**永不锁定**）；
- `onPlayerDropItem(event: PlayerDropItemEvent)` 事件监听器方法体为**空**，注释「丢弃锁已取消，不再阻止丢弃」；
- `onJoinResetDropLock(event: PlayerJoinEvent)` 事件监听器方法体同样为**空**。

也就是说：**指令仍注册、仍可执行、仍会回显成功消息，但不会产生任何拦截效果**，物品永远可以正常丢弃。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/drop` | 切换丢弃锁状态（开 ↔ 关），并打印当前状态 | `chotentech.command.drop` |
| `/drop on` | 把丢弃锁设为「开启」= 允许丢弃 | `chotentech.command.drop` |
| `/drop off` | 把丢弃锁设为「关闭」= 阻止丢弃 | `chotentech.command.drop` |

> 三个分支的 `@CommandBody` **都没有**单独声明 `permission`，因此全部继承 `@CommandHeader` 的 `chotentech.command.drop`。

## 指令详解

### `/drop`

- **用途**：一键切换自己的丢弃锁状态。
- **参数**：无。
- **权限**：`chotentech.command.drop`（默认所有人）。
- **执行者要求**：**仅玩家**（三个分支都是 `execute<Player>`，控制台执行会被 TabooLib 拒绝）。
- **行为说明**：
  - 读取当前值 `dropLockEnabled.getOrDefault(uuid, false)`（未设置过时按 `false`）。
  - 若当前为 `true`（允许丢弃）→ 置为 `false`，回显「🔒 丢弃锁已**关闭**，物品将被阻止丢弃」。
  - 若当前为 `false` → 置为 `true`，回显「🔓 丢弃锁已**开启**，你可以丢弃物品」。
  - ⚠ 消息文案与实际效果**已脱节**：由于 `isDropLocked` 恒返回 `false` 且 `PlayerDropItemEvent` 监听器为空方法，即便显示「物品将被阻止丢弃」，物品依然可以丢弃。
- **示例**：`/drop`

### `/drop on`

- **用途**：显式把丢弃锁置为「开启」。
- **参数**：无。
- **权限**：`chotentech.command.drop`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`dropLockEnabled[uuid] = true`，回显「🔓 丢弃锁已**开启**，你可以丢弃物品」。幂等，可重复执行。
- **示例**：`/drop on`

### `/drop off`

- **用途**：显式把丢弃锁置为「关闭」。
- **参数**：无。
- **权限**：`chotentech.command.drop`。
- **执行者要求**：**仅玩家**。
- **行为说明**：`dropLockEnabled[uuid] = false`，回显「🔒 丢弃锁已**关闭**，物品将被阻止丢弃」。幂等，可重复执行。
- **示例**：`/drop off`

## 备注 / 注意事项

- **状态只存在内存**：`dropLockEnabled` 是 `ConcurrentHashMap`，服务器重启即全部丢失。源码中原本用于「每次进服重置为关闭」的 `onJoinResetDropLock`（`@SubscribeEvent`）方法体已被清空，因此进服不再重置。
- **功能已停用**：判断入口 `isDropLocked(uuid)` 被硬编码为 `false`，`PlayerDropItemEvent` 监听器为空实现。如需恢复锁定能力，必须同时恢复 `isDropLocked` 与 `onPlayerDropItem` 的实际逻辑。
- **指令仍然注册**：`@CommandHeader(name = "drop", ...)` 未被注释，指令树正常可见，`/drop` 不会报「未知指令」。
- **不会持久化、不会同步**：其他插件/服务端侧不会看到该状态；`/drop` 的开关只影响本对象的 map（且无消费方）。
- **默认权限是 `TRUE`**，即所有玩家默认可执行；因为无实际效果，当前不构成风险。
- 三个分支都是 `execute<Player>`，**控制台不可用**。
