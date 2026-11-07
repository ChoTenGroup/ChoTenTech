# 提交物品（Submit Item）

> **根指令**：`/submititem`（未声明别名）
> **权限**：未声明（继承框架默认）；`permissionDefault = PermissionDefault.OP`
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/SubmitItemCommand.kt`

## 功能概述

打开**提交物品 GUI**（`SubmitMenu`），并把一个 **NPC ID** 作为界面上下文传入。该界面用于向某个 NPC/任务提交物品（`SubmitMenu.open(player, npcId)` 是"从指令打开"的分支，等价于任务菜单里带 `quest + task` 的入口，但此处只提供 NPC ID）。

指令支持两种形态：
- `/submititem <id>` —— 执行者为自己打开（执行者必须为玩家）；
- `/submititem <id> <player>` —— 为指定在线玩家打开（玩家 / 控制台均可执行）。

`@CommandHeader` 未设置 `permission`、未设置 `aliases`、未设置 `permissionMessage`、未设置 `newParser`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/submititem <id>` | 为自己打开提交物品界面 | 未声明（继承框架默认）；默认 OP |
| `/submititem <id> <player>` | 为指定在线玩家打开提交物品界面 | 未声明（继承框架默认）；默认 OP |

## 指令详解

### `/submititem <id>`

- **用途**：按 NPC ID 打开提交物品界面，执行者即界面接收者。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 动态参数 `dynamic("id")`，NPC ID 字符串（源码注释：`从 /submititem 指令打开 — 仅 NPCID`） | 必填 | 无自动补全（`suggestion` 未声明） |
- **权限**：`@CommandHeader` 未声明 `permission`，因此权限为"未声明（继承框架默认）"；`permissionDefault = PermissionDefault.OP`（默认 OP 可用）。
- **执行者要求**：仅玩家（该分支为 `execute<Player>`）。
- **行为说明**：
  - 直接把 `context["id"]` 传给 `SubmitMenu().open(sender, context["id"])`。
  - `SubmitMenu.open(player, npcId)` 内部转调 `openInternal(player, quest = null, task = null, npcId = npcId)`：打开 6 行箱子界面（标题 `提交物品`），因为 `quest == null`，界面底部按钮布局使用无任务版本 `"    S    "`。
  - 指令层未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/submititem 12`
  - `/submititem shopkeeper`

### `/submititem <id> <player>`

- **用途**：为指定在线玩家打开提交物品界面。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | 动态参数 `dynamic("id")`，NPC ID 字符串 | 必填 | 无自动补全 |
  | `player` | 动态参数 `dynamic("player")`，目标玩家**名称**（用 `Bukkit.getPlayerExact` 精确匹配） | 必填 | 无自动补全（`suggestion` 未声明） |
- **权限**：未声明（继承框架默认）；`permissionDefault = PermissionDefault.OP`。
- **执行者要求**：玩家 / 控制台均可（该分支为 `execute<CommandSender>`）。
- **行为说明**：
  - `player` 节点是 `dynamic("player")`，**不是** `player(...)` 玩家参数，因此没有在线玩家补全，也不会自动校验在线状态。
  - 通过 `Bukkit.getPlayerExact(context["player"])` 解析目标；为 `null` 时向执行者发送 `§c玩家 <输入的名字> 不在线！`（原版颜色码，经 `sendMessage` 发送，非 MiniMessage 组件）并结束。
  - 解析成功则 `SubmitMenu().open(target, context["id"])`，**界面直接开给目标玩家**，执行者不会收到任何成功提示。
  - 指令层未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/submititem 12 Steve`
  - 控制台：`submititem 12 Steve`

## 备注 / 注意事项

- **子指令/参数节点核对**：源码共 1 个 `@CommandBody`（`main`）；内部节点为 `dynamic("id")` → （其下同级）`dynamic("player")`，共 2 个 `dynamic(`；**无 `subCommand`、无 `literal(`、无 `player(`**。因此不存在 `/submititem list`、`/submititem <id> <player> <extra>` 之类写法。
- `@CommandHeader` **没有 `permission` 参数**：权限节点未声明（继承框架默认），同时也没有 `permissionMessage`；可见性/可用性主要由 `permissionDefault = OP` 与默认配置决定。部署时若需精确管控，建议在权限插件中以 OP 默认值为准或在后续版本为其补充 `permission`。
- `id` 没有任何补全与校验：传入不存在的 NPC ID 不会在指令层报错，行为由 `SubmitMenu` 决定。
- 第二分支的目标玩家必须**在线且名称完全一致**（`getPlayerExact` 大小写敏感匹配），不支持 UUID。
- 源码中 `import org.bukkit.Bukkit`、`import org.bukkit.command.CommandSender`、`import org.bukkit.entity.Player` 均已使用；错误提示使用的是 `sender.sendMessage("§c...")`，其余指令多用 `sendComponent`（MiniMessage），此处风格不一致，属实现细节。
- 本对象**没有**被挂到 `/ctt` 主指令下，只能通过 `/submititem` 调用。
