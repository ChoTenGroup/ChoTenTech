# 玩家档案（Profile）

> **根指令**：`/profile`（别名：`/profiles`, `/玩家档案`）
> **权限**：`chotentech.command.profile`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ProfileCommand.kt`

## 功能概述

打开**玩家档案选择菜单**（`ProfileSelectionGUI`）。玩家可以在该界面中查看自己已有的档案列表、切换当前使用的档案、创建新档案等（具体操作由 GUI 逻辑实现，不在本指令文件内）。

本指令只有一条实际行为：调用 `ProfileSelectionGUI().open(sender)`；`main` 与 `menu` 两个入口的代码完全相同，`menu` 只是为方便记忆提供的显式字面量子指令。

`@CommandHeader` 使用了 `newParser = false`（使用旧版解析器），并设置了 `permissionMessage = ""`（无权限时**不输出**自定义提示文本）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/profile` | 打开玩家档案选择菜单（默认无参主体） | `chotentech.command.profile` |
| `/profile menu` | 打开玩家档案选择菜单（与无参主体等价） | `chotentech.command.profile` |

> 别名 `/profiles`、`/玩家档案` 可替换上面两种写法中的 `/profile` 部分。

## 指令详解

### `/profile`

- **用途**：打开玩家档案选择菜单。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.profile`（默认 OP；无权限提示文本为空）。
- **执行者要求**：仅玩家（`execute<Player>`，控制台无法执行）。
- **行为说明**：
  - `mainCommand` 主体，直接以执行者为参数调用 `ProfileSelectionGUI().open(sender)`。
  - 未声明任何运行时 `sender.hasPermission(...)` 检查，权限完全由 `@CommandHeader` 的 `permission` 决定。
- **示例**：
  - `/profile`
  - `/profiles`
  - `/玩家档案`

### `/profile menu`

- **用途**：打开玩家档案选择菜单（与 `/profile` 无参执行完全一致）。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.profile`（默认 OP）。
- **执行者要求**：仅玩家（`execute<Player>`）。
- **行为说明**：
  - `subCommand` 字面量 `menu`（属性名即字面量，源码中未给该 `subCommand` 单独指定 `aliases`）。
  - 同样调用 `ProfileSelectionGUI().open(sender)`。
- **示例**：
  - `/profile menu`
  - `/profiles menu`

## 备注 / 注意事项

- **子指令字面量总数核对**：源码中共 2 个 `@CommandBody`——`main`（`mainCommand`，无参主体）与 `menu`（`subCommand` 字面量）；**没有任何 `literal(` / `dynamic(` / `player(` 参数声明**，因此不存在带参数的路径。
- 两条路径行为完全一致，属冗余入口；若只想暴露一条，可在配置/权限层面控制，但不能在本文件中区分。
- `permissionDefault = PermissionDefault.OP`：默认仅 OP 可用；`permissionMessage = ""` 表示未通过权限检查时不会显示自定义提示语。
- 执行者类型被限定为 `Player`，控制台执行不会打开任何界面（由 TabooLib 框架拦截）。
- `ProfileSelectionGUI.open()` 内部会先校验 `player.isOnline`，并用 `opening` 集合防止同一玩家的界面在异步预加载期间被重复打开（移动事件 / ESC 重开竞态）。
- 该指令在 `command/MainCommand.kt` 中**没有**被挂在 `/ctt` 之下（`/ctt` 下对应的是 `ProfileAdminCommand`，即 `/ctt profile`，与本文件的 `/profile` 是不同对象，注意区分）。
