# 安全容器（Secure Container）

> **根指令**：`/securecontainer`（别名：`/sc`）
> **权限**：`chotentech.command.securecontainer`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/SecureContainerCommand.kt`

## 功能概述

打开玩家的**安全容器（安全箱）GUI**（`SecureContainerMenu`）——一个与玩家档案数据绑定的私有储物界面，用于在死亡/切出等场景中保住少量物品。

本文件**只有一个** `@CommandBody`（无参主体），没有任何子指令或参数。`@CommandHeader` 未设置 `newParser`（使用默认值），`permissionDefault = PermissionDefault.TRUE`，即**默认所有人可用**。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/securecontainer` | 打开自己的安全容器 | `chotentech.command.securecontainer` |

> 可用别名 `/sc`。

## 指令详解

### `/securecontainer`

- **用途**：打开执行者自己的安全容器界面。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.securecontainer`（默认：所有人）。
- **执行者要求**：仅玩家（`execute<Player>`，控制台无法执行）。
- **行为说明**：
  - `mainCommand` 主体直接调用 `SecureContainerMenu().open(sender)`。
  - `SecureContainerMenu.open()` 先取 `player.getData()`，取不到则直接返回（不打开界面）。
  - 界面标题来自配置 `secure-container.title`（默认 `安全箱`）；可用格数来自配置 `secure-container.slots`（默认 `9`，并被 `coerceIn(1, 21)` 限制在 1～21 之间），内容格位来自固定槽位表 `CONTENT_SLOTS`（21 个位置：19-25、28-34、37-43）。
  - 超出 `slots` 的格位显示为"未解锁格"（灰色玻璃板，Lore：`该格暂不可用`）。
  - 容器内容从玩家数据的 `data.secureContainer` 反序列化（`ItemSerializer.deserializeAny`）。
  - 指令层未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/securecontainer`
  - `/sc`

## 备注 / 注意事项

- **子指令核对**：源码仅 1 个 `@CommandBody`（`main`），**无 `subCommand`、无 `literal(`、无 `dynamic(`、无 `player(`**，故不存在为他人打开安全箱的参数形式。
- 该指令**只能打开自己的**安全容器（`open(sender)`），无法指定目标玩家。
- 权限默认值 `PermissionDefault.TRUE` 表示**不授予权限节点的玩家也默认通过**；源码未声明 `permissionMessage`，无权限时会走框架默认提示。
- 界面容量由配置控制，修改 `secure-container.slots` 后需重载配置/重启才会反映到新打开的界面（`SecureContainerMenu` 的 `slots()` 每次打开时读取 `config`）。
- 本对象**没有**被挂到 `/ctt` 主指令下（`command/MainCommand.kt` 中未注册），只能通过 `/securecontainer` 或 `/sc` 使用。
