# 装饰指令（Cosmetics）

> **根指令**：`/cosmetics`（别名：`/cosmetic`, `/decoration`, `/装饰`）
> **权限**：`chotentech.command.cosmetics`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/CosmeticCommand.kt`

## 功能概述

打开**装饰品菜单**（`CosmeticMenu`）。玩家可在其中浏览并装备/卸下各类装饰（悬浮字、特效等，具体分类由 `CosmeticSlot` 决定）。

本文件有 2 个 `@CommandBody`：无参主体 `main` 与字面量子指令 `menu`，两者行为完全相同（都调用 `CosmeticMenu().open(sender)`）。`@CommandHeader` 使用 `newParser = false`，`permissionDefault = PermissionDefault.TRUE`（默认所有人可用），并设置 `permissionMessage = ""`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cosmetics` | 打开装饰品菜单（默认无参主体） | `chotentech.command.cosmetics` |
| `/cosmetics menu` | 打开装饰品菜单（与无参主体等价） | `chotentech.command.cosmetics` |

> 别名 `/cosmetic`、`/decoration`、`/装饰` 均可替换上面的 `/cosmetics`。

## 指令详解

### `/cosmetics`

- **用途**：打开装饰品菜单。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.cosmetics`（默认所有人；无权限提示文本为空）。
- **执行者要求**：仅玩家（`execute<Player>`）。
- **行为说明**：
  - `mainCommand` 主体调用 `CosmeticMenu().open(sender)`。
  - 装饰品条目**本身还有独立权限** `cosmetic.permission`：菜单展示时会用 `player.hasPermission(it.permission)` 过滤/标记锁定（`openType` 中 `showAll = false` 时过滤无权限项；生成条目时若 `cosmetic.permission != null && !p.hasPermission(...)` 则显示为锁定；点击时再做一次 `clicker.hasPermission(cosmetic.permission)` 校验）。
  - 指令层未做运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/cosmetics`
  - `/装饰`

### `/cosmetics menu`

- **用途**：打开装饰品菜单（与 `/cosmetics` 无参执行一致）。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.cosmetics`（默认所有人）。
- **执行者要求**：仅玩家（`execute<Player>`）。
- **行为说明**：
  - `subCommand` 字面量 `menu`（源码未为该 `subCommand` 单独声明 `aliases`），同样调用 `CosmeticMenu().open(sender)`。
- **示例**：
  - `/cosmetics menu`
  - `/cosmetic menu`

## 备注 / 注意事项

- **子指令核对**：源码共 2 个 `@CommandBody`——`main`（`mainCommand`）与 `menu`（`subCommand` 字面量）；**无 `literal(`、无 `dynamic(`、无 `player(`**，因此不存在带参数的路径。
- 两条路径行为完全相同，属冗余入口。
- 权限分两层：**指令级** `chotentech.command.cosmetics`（默认所有人）+ **条目级** 各装饰品自身的 `permission`（由菜单在渲染/点击时校验，非本指令声明）。给玩家该指令权限并不代表能使用全部装饰品。
- 权限默认 `TRUE`（所有人），`permissionMessage = ""` 表示无权限时不显示自定义提示。
- 执行者类型限定为 `Player`，控制台执行不会有任何界面打开（由框架拦截）。
- 本对象**没有**被挂到 `/ctt` 主指令下，只能通过上述四个名字调用。
