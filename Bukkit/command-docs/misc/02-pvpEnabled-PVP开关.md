# PVP 开关

> **根指令**：`/pvpEnabled`（无别名，`aliases = []`）
> **权限**：`chotentech.command.pvp`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/PvpCommand.kt`

## 功能概述

切换执行者**本人**的 PVP 开关。状态同时写入两处：

- 共享档案 `ProfileManager.getSharedData(uuid).settings.values["pvpEnabled"]`（以字符串 `"true"` / `"false"` 存储），随后 `ProfileManager.saveSharedData(uuid)` 落盘；
- 玩家内存数据 `sender.getData()?.pvpEnabled`（`Boolean`）。

命令包含一个根命令 + 2 个子命令（`on` / `off`），没有参数节点。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/pvpEnabled` | 反转当前 PVP 状态 | `chotentech.command.pvp` |
| `/pvpEnabled on` | 强制开启 PVP | `chotentech.command.pvp` |
| `/pvpEnabled off` | 强制关闭 PVP | `chotentech.command.pvp` |

## 指令详解

### `/pvpEnabled`

- **用途**：把当前 PVP 状态**取反**（开→关、关→开）。
- **参数**：无（根命令 `mainCommand` 内只有 `execute`）。
- **权限**：`chotentech.command.pvp`（`PermissionDefault.TRUE`，默认所有人；`permissionMessage` 显式置空）。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：
  1. 取 `shared = ProfileManager.getSharedData(uuid)`。
  2. `shared.settings.values["pvpEnabled"] = (!shared.settings.getBoolean("pvpEnabled")).toString()`——先读旧值取反，再以字符串写回。
  3. `sender.getData()?.pvpEnabled = shared.settings.getBoolean("pvpEnabled")`——同步内存数据。
  4. 按新值发送提示：
     - 开启：`<#E08A98>⚔ PVP模式已<#B46572>开启</#B46572>，你现在可以与其他开启PVP的玩家战斗`
     - 关闭：`<#8FD4A6>☮ PVP模式已<#6EA982>关闭</#6EA982>，你将无法攻击其他玩家`
  5. `ProfileManager.saveSharedData(uuid)` 保存。
- **示例**：
  ```
  /pvpEnabled
  ```

### `/pvpEnabled on`

- **用途**：无条件把 PVP 设为**开启**。
- **参数**：无。
- **权限**：`chotentech.command.pvp`（默认所有人）。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：写入 `shared.settings.values["pvpEnabled"] = "true"`、`sender.getData()?.pvpEnabled = true`，发送 `<#E08A98>⚔ PVP模式已<#B46572>开启`，然后 `saveSharedData(uuid)`。**不判断**当前是否已开启（重复执行只重复发提示）。
- **示例**：
  ```
  /pvpEnabled on
  ```

### `/pvpEnabled off`

- **用途**：无条件把 PVP 设为**关闭**。
- **参数**：无。
- **权限**：`chotentech.command.pvp`（默认所有人）。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：写入 `shared.settings.values["pvpEnabled"] = "false"`、`sender.getData()?.pvpEnabled = false`，发送 `<#8FD4A6>☮ PVP模式已<#6EA982>关闭`，然后 `saveSharedData(uuid)`。
- **示例**：
  ```
  /pvpEnabled off
  ```

## 备注 / 注意事项

- **只能操作自己**：三个命令体都用 `execute<Player>` 并直接读取 `sender.uniqueId`，**没有 `player(...)` 参数**，无法为他人切换 PVP（也没有管理员代操作入口）。
- **存储类型不一致需注意**：共享档案里 `pvpEnabled` 存的是**字符串**（`.toString()` / `"true"` / `"false"`），读取时用 `getBoolean("pvpEnabled")`；写入与读取必须成对使用同一套语义，手工改配置时不要写成非 `true`/`false` 的值。
- `sender.getData()` 用了安全调用 `?.`：玩家数据未加载时只更新共享档案，**不会**报错，但内存态不会同步。
- `on` / `off` 没有幂等判断，重复执行会重复输出提示并重复写盘。
- 无别名（`aliases` 显式为空数组）；`permissionMessage = ""`；`newParser = false`。
- 权限节点名为 `chotentech.command.pvp`（注意与命令名 `pvpEnabled` 不同）。
