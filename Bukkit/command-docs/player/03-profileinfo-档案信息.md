# 档案信息（Profile Info）

> **根指令**：`/profileinfo`（别名：`/pi`, `/档案信息`）
> **权限**：`chotentech.command.profileinfo`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ProfileInfoCommand.kt`

## 功能概述

打开**玩家档案信息 GUI**（`ProfileInfoGUI`），用于查看某个玩家的档案详情。管理员可以为**其他玩家**打开该界面；控制台执行时则把界面开给目标玩家本人。

`@CommandHeader` 使用 `newParser = true`，并设置 `permissionMessage = ""`（无权限时不输出自定义提示）。

无参执行时调用公共扩展函数 `createModernHelper()` 打印用法树（含 `open <target>` 一条分支）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/profileinfo` | 输出指令用法树（无参主体） | `chotentech.command.profileinfo` |
| `/profileinfo open <target>` | 打开指定玩家的档案信息界面 | `chotentech.command.profileinfo` |

## 指令详解

### `/profileinfo`（无参主体）

- **用途**：打印用法树。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.profileinfo`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` 内为 `execute<ProxyCommandSender>`）。
- **行为说明**：
  - 输出 `Usage: /profileinfo ...` 形式的树状用法（`createModernHelper()`，定义于 `command/MainCommand.kt`）。
  - 默认按执行者权限过滤可见节点。
- **示例**：
  - `/profileinfo`
  - `/pi`

### `/profileinfo open <target>`

- **用途**：打开指定玩家的档案信息界面。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 玩家（`player("target")`，必须为在线玩家） | 必填 | TabooLib 在线玩家列表 |
- **权限**：`chotentech.command.profileinfo`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - `literal` 字面量为 `open`（`subCommand` 属性名），其下为 `player("target")`。
  - 取 `context.player("target").castSafely<Player>()!!` 作为被查看对象。
  - 决定界面接收者：`viewer = if (sender is Player) sender else target`。即**玩家执行时界面开给执行者自己**（内容是 target 的档案信息），**控制台执行时界面开给 target 本人**。
  - 若 `sender != viewer`（只可能发生在控制台执行的情况），额外向 `sender` 发送 `<#8FD4A6>已为 <target.name> 打开档案信息界面`。
  - 未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/profileinfo open Steve`（OP 玩家执行 → OP 自己看到 Steve 的档案信息界面）
  - `/pi open Steve`
  - 控制台：`profileinfo open Steve`（界面开给 Steve，控制台收到确认提示）

## 备注 / 注意事项

- **子指令/参数节点核对**：源码共 2 个 `@CommandBody`（`main`、`open`）；参数节点仅 `player("target")` 1 个，**没有任何 `dynamic(` / `literal(` 节点**。
- 该指令不会"替别人打开界面"给第三方旁观：玩家执行时界面接收者永远是执行者本人，`target` 只决定界面内容。
- `target` 使用 `player(...)`，只能指定**在线**玩家。
- 本对象同时被挂在主指令下：`command/MainCommand.kt` 中 `@CommandBody val profileinfo = ProfileInfoCommand`，因此 `/ctt profileinfo open <target>` 也可用（额外需要 `/ctt` 的 `chotentech.command.main` 权限）。
- `castSafely<Player>()!!` 为强制解包：若目标在指令解析后、执行前离线，理论上可能抛出空指针异常（非正常路径）。
