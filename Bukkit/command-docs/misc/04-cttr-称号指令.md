# 称号指令

> **根指令**：`/chotentech-rank`（别名：`/cttr`, `/rank`）
> **权限**：`chotentech.command.rank`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/RankCommand.kt`

## 功能概述

打开**称号选择菜单**（`RankSelectionMenu`）。命令本身不处理任何称号数据的读写，只负责把 GUI 打开给某个玩家；称号的增删改查全部在菜单内完成。

该文件共定义 **1 个根命令 + 1 个子命令（`open`）**，无 `literal` / `dynamic` 参数，只有一个可选的 `player("target")` 参数。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-rank` | 为自己打开称号选择菜单 | `chotentech.command.rank` |
| `/chotentech-rank open` | 为自己打开称号选择菜单（与根命令等效） | `chotentech.command.rank` |
| `/chotentech-rank open <target>` | 为指定玩家打开称号选择菜单 | `chotentech.command.rank` |

## 指令详解

### `/chotentech-rank`

- **用途**：为执行者本人打开称号选择菜单。
- **参数**：无（根命令 `mainCommand` 内只有 `execute`）。
- **权限**：`chotentech.command.rank`（`permissionDefault = PermissionDefault.TRUE`，默认所有人）。
- **执行者要求**：**仅玩家**（`execute<CommandSender>` 内部判断 `sender is Player`）。
- **行为说明**：`sender` 是 `Player` 则执行 `RankSelectionMenu().open(sender)`；否则向控制台发送 `§cPlease use this command as a player.`（注意：**不是**静默返回，控制台会看到这条英文提示）。
- **示例**：
  ```
  /cttr
  /rank
  ```

### `/chotentech-rank open`

- **用途**：与根命令等效，为执行者本人打开称号选择菜单。
- **参数**：无。
- **权限**：`chotentech.command.rank`（默认所有人）。
- **执行者要求**：**仅玩家**（同上，非玩家会收到 `§cPlease use this command as a player.`）。
- **行为说明**：与主命令完全相同的逻辑——`sender is Player` 时打开 `RankSelectionMenu`，否则发送英文提示。
- **示例**：
  ```
  /chotentech-rank open
  /cttr open
  ```

### `/chotentech-rank open <target>`

- **用途**：为**另一位玩家**打开称号选择菜单（该玩家会直接看到 GUI）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（框架内置 `player(...)` 玩家参数） | 选填（省略时作用于执行者本人） | 框架内置的玩家补全 |

- **权限**：`chotentech.command.rank`（默认所有人）。
- **执行者要求**：玩家 / 控制台均可——`target` 是参数而非执行者本人，源码在此分支**不再**判断 `sender is Player`。
- **行为说明**：`context.player("target").castSafely<Player>()` 取目标；为 null 时静默 `return@execute`（无提示）；成功则 `RankSelectionMenu().open(target)`。执行者本身不会收到任何确认消息。
- **示例**：
  ```
  /cttr open Steve
  /rank open Steve
  ```

## 备注 / 注意事项

- **给他人打开菜单没有额外权限门槛**：`open <target>` 只受根命令权限 `chotentech.command.rank` 约束，而该节点默认 `PermissionDefault.TRUE`（所有人）。也就是说，默认配置下**任何玩家都能用 `/cttr open <玩家>` 打开别人的称号菜单**。若需要限制，应在权限插件中把 `chotentech.command.rank` 收回或拆分节点（源码未提供第二个权限节点）。
- **控制台行为不一致**：`/chotentech-rank` 与 `/chotentech-rank open` 在控制台执行会收到 `§cPlease use this command as a player.`；而 `/chotentech-rank open <玩家>` 在控制台执行是**合法**的（可以为在线玩家打开菜单）。
- **无成功反馈**：命令打开 GUI 后不发送任何消息，效果只能从客户端界面观察到。
- **`open <target>` 的失败是静默的**：目标玩家转换失败时不输出任何错误。
- `@CommandHeader` **未声明** `description`、`permissionMessage`、`newParser`（这三项采用框架默认值），仅声明了 `name`、`aliases`、`permission`、`permissionDefault`。
- 源码中**没有**运行时 `sender.hasPermission(...)` 检查；也**没有** `prefix` / `suffix` / `chatcolor` 之类的称号类型参数——该文件里唯一存在的关键字面子命令是 `open`。
- 根命令名字面量 `chotentech-rank`，别名 `cttr` 与 `rank`（后者可能与其它插件的 `/rank` 冲突，注意命令注册优先级）。
