# 返回出生点

> **根指令**：`/backspawn`（别名：`/bspawn`）
> **权限**：`chotentech.command.backspawn`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/BackSpawnCommand.kt`

## 功能概述

把玩家传送回其**个人重生点**。重生点数据由 `SavePointManager.getRespawnLocation(player)` 解析，返回 `Location?`；解析成功即调用 `Player#teleportAsync` 异步传送，解析失败则发送提示"你没有设置存档点"。

该命令只有**一个根指令、没有任何子参数**——`mainCommand` 内只有一段 `execute`，不存在 `literal(...)` / `dynamic(...)` 节点。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/backspawn` | 传送到自己的个人重生点 | `chotentech.command.backspawn` |

## 指令详解

### `/backspawn`

- **用途**：将执行者本人传送至其个人重生点（复活点）。
- **参数**：无。该路径下没有任何 `literal` / `dynamic` / `player` 参数节点。
- **权限**：`chotentech.command.backspawn`（`PermissionDefault.TRUE`，默认所有人可用；`permissionMessage` 被显式置空，因此无权限时不会收到自定义提示）。
- **执行者要求**：**仅玩家**。`execute<CommandSender>` 内部第一行即为 `val player = sender as? Player ?: return@execute`——控制台执行时安全转换失败后**静默返回**，既没有任何输出，也不报错。
- **行为说明**：
  1. `sender` 转成 `Player`，失败则直接返回。
  2. 调用 `SavePointManager.getRespawnLocation(player)`：
     - 若玩家数据不存在（`getData()` 为 null），直接返回默认出生点；
     - 否则按 `data.baseSave` → `data.normalSave` 的顺序读取存档点，跳过配置缺失（`SavePointConfig.get` 为 null）或世界不存在（`Bukkit.getWorld(...)` 为 null）的失效记录；
     - 命中存档点时取 `sp.respawnPos?.toLocation() ?: sp.effectiveHoloPos`；
     - 全部落空时回退到默认出生点 `Bukkit.getWorld("Area0")` 内的 `(-6.0, 14.0, 9.0)`。
  3. 若拿到 `Location`，执行 `player.teleportAsync(loc)`（异步传送，不阻塞主线程）。
  4. 若拿到 `null`（即 `SavePointManager` 连兜底出生点都给不出，通常意味着 `Area0` 世界不存在），发送红色提示：`你没有设置存档点`。
- **示例**：
  ```
  /backspawn
  /bspawn
  ```

## 备注 / 注意事项

- **不存在任何子命令**：`/backspawn xxx` 之类的多余参数不会被接受，也没有自动补全项（源码中没有 `suggestion` 块）。
- 提示文案"你没有设置存档点"只在 `getRespawnLocation` 返回 `null` 时出现；玩家**未设置**存档点但 `Area0` 世界存在时，会被静默送到 Area0 的兜底坐标，而不是看到该提示。
- 传送为异步（`teleportAsync`），命令返回时传送可能尚未完成。
- 权限默认 `TRUE`，服务端实际可用性依赖于权限插件是否把该节点显式收回。
- `CommandHeader` 中 `newParser = false`，使用旧解析器。
