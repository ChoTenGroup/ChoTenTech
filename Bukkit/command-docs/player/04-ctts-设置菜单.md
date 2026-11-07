# 玩家设置菜单（ChoTenTech Settings）

> **根指令**：`/chotentechsettings`（别名：`/ctts`, `/设置`）
> **权限**：`chotentech.command.settings`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/SettingsCommand.kt`

## 功能概述

打开**玩家设置 GUI**（`SettingsMenu`）。该界面以分页列表列出 `SettingRegistry` 中注册的所有设置项（读取自 `ProfileManager.getSharedData(uuid).settings`），点击即可切换开关，例如 PVP 模式、计分板显示、音效等（具体项由 `SettingRegistry` / 配置决定）。

`@CommandHeader` 使用 `newParser = true`，并设置 `permissionMessage = ""`（无权限时不输出自定义提示）。

本文件**只有一个** `@CommandBody`（无参主体），没有任何子指令或参数。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/ctts` | 打开玩家设置菜单 | `chotentech.command.settings` |

> 完整指令名为 `/chotentechsettings`，可用别名 `/ctts`、`/设置`。

## 指令详解

### `/chotentechsettings`

- **用途**：打开玩家设置菜单。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.settings`（默认 OP；无权限提示文本为空）。
- **执行者要求**：仅玩家（`execute<CommandSender>` 内 `val player = if (sender is Player) sender else return@execute`；控制台执行时**静默返回**，不报错、不输出）。
- **行为说明**：
  - 主体为 `mainCommand`，内部先做 `sender is Player` 判断，非玩家直接 `return@execute`。
  - 通过判断后调用 `SettingsMenu().open(player)` 打开界面。
  - `SettingsMenu` 读取 `ProfileManager.getSharedData(uuid).settings`（**跨档案共享**的玩家设置），因此切换设置在切换档案后依然保留。
  - 未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/ctts`
  - `/设置`
  - `/chotentechsettings`

## 备注 / 注意事项

- **子指令核对**：源码仅 1 个 `@CommandBody`（`main` → `mainCommand`），**无 `subCommand`、无 `literal(`、无 `dynamic(`、无 `player(`**，所以不存在 `/ctts xxx` 形式的参数。
- 控制台执行时是**静默失败**（无任何提示），排查"指令没反应"时需注意执行者是否为玩家。
- 本对象同时被挂在主指令下：`command/MainCommand.kt` 中 `@CommandBody val settings = SettingsCommand`，因此 `/ctt settings` 等价可用（额外需要 `/ctt` 的 `chotentech.command.main` 权限）。
- 默认权限为 OP，如希望普通玩家可用，需要给玩家授予 `chotentech.command.settings`（或在权限插件中按默认值放行）。注意 `permissionDefault = OP` 是**声明**；是否真的放行取决于服务端权限实现与权限插件。
