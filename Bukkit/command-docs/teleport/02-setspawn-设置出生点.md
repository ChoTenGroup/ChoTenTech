# 设置出生点

> **根指令**：`/setspawn`（别名：`/sspawn`）
> **权限**：`chotentech.command.setspawn`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/SetSpawnCommand.kt`

## 功能概述

管理员指令，**强制指定**某位在线玩家的重生点（世界 + 精确坐标）。命令会把该玩家的 `respawnPoint` 覆写为新的 `RespawnPoint`，并**清空**其原有存档点记录（`normalSave` 与 `baseSave` 均置为 `null`），因此该坐标会覆盖所有存档点优先级。

参数是用**五个按顺序串联的动态参数**（`player("target")` → `dynamic("world")` → `dynamic("x")` → `dynamic("y")` → `dynamic("z")`）拼出的唯一一条路径，不存在分支。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/setspawn` | 单独执行时输出用法提示 | `chotentech.command.setspawn` |
| `/setspawn <target> <world> <x> <y> <z>` | 设置指定玩家的重生点 | `chotentech.command.setspawn` |

## 指令详解

### `/setspawn`

- **用途**：不带参数时仅打印用法提示，不做任何数据修改。
- **参数**：无。
- **权限**：`chotentech.command.setspawn`（`PermissionDefault.OP`，默认仅 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`，无 `Player` 转换）。
- **行为说明**：发送用法文本 `<#E08A98>用法: /setspawn <玩家> <世界> <x> <y> <z>`。
- **示例**：
  ```
  /setspawn
  ```

### `/setspawn <target> <world> <x> <y> <z>`

- **用途**：把目标玩家的重生点设为指定世界中的指定坐标。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（框架内置 `player(...)` 玩家参数） | 必填 | 框架内置的玩家补全 |
  | `world` | 世界名（`dynamic`，字符串） | 必填 | 服务器已加载世界名：`Bukkit.getWorlds().map { it.name }` |
  | `x` | X 坐标（`dynamic`，按 `toDoubleOrNull()` 解析） | 必填 | 固定补全 `0` |
  | `y` | Y 坐标（`dynamic`，按 `toDoubleOrNull()` 解析） | 必填 | 固定补全 `0` |
  | `z` | Z 坐标（`dynamic`，按 `toDoubleOrNull()` 解析） | 必填 | 固定补全 `0` |

- **权限**：`chotentech.command.setspawn`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可——所有参数都是显式给出的（`target` 是参数而非"执行者本人"），源码中没有 `sender as? Player` 强转。
- **行为说明**（按源码顺序校验，任一失败即 `return@execute`）：
  1. `Bukkit.getPlayerExact(context["target"])`：查不到则提示 `<#E08A98>玩家 <target> 不在线！`（精确匹配，不支持模糊/离线玩家）。
  2. `Bukkit.getWorld(context["world"])`：查不到则提示 `<#E08A98>世界 <world> 不存在！`。
  3. 对 `x` / `y` / `z` 分别执行 `toDoubleOrNull()`：任一为 `null` 则提示 `<#E08A98>坐标必须为有效数字！`。
  4. 取 `playerDataMap[target.uniqueId.toString()]`：为 `null` 则提示 `<#E08A98>玩家数据未加载！`。
  5. 修改数据：`data.normalSave = null`、`data.baseSave = null`，随后写入 `data.respawnPoint = RespawnPoint(world = world.name, x, y, z, yaw = 0f, pitch = 0f)`。
  6. 向执行者发送 `<#8FD4A6>已将 <玩家> 的重生点设为 <世界> <x> <y> <z>`；同时向目标玩家发送 `<#8FD4A6>管理员已将你的重生点设为 <世界> <x> <y> <z>`。
- **示例**：
  ```
  /setspawn Steve world 0 64 0
  /setspawn Alex Area0 -6.5 14 9.5
  /sspawn Steve world 100 -20 -300
  ```

## 备注 / 注意事项

- **会清空既有存档点**：执行后 `normalSave` 与 `baseSave` 都被置 `null`，玩家此前通过存档点系统记录的重生点会**不可恢复地失效**（源码注释即 `// clear normal save` / `// clear base save`）。
- **朝向被清零**：`yaw = 0f, pitch = 0f` 写死，无法通过该命令设置朝向。
- 坐标必须是合法 `Double`；`~`、`^` 等相对坐标语法**不受支持**（只会被 `toDoubleOrNull()` 判为非法）。
- 目标玩家必须**在线**，且其玩家数据必须**已加载**，否则命令直接失败。
- `permissionDefault = PermissionDefault.OP`，`permissionMessage` 被显式置空（无权限时无自定义提示）。
- `CommandHeader` 中 `newParser = false`。
