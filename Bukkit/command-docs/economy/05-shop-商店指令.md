# 商店指令（ChoTenTech Shop）

> **根指令**：`/chotentech-shop`（别名：`/cttshop`, `/shop`）
> **权限**：未声明（继承框架默认）— `@CommandHeader` 未提供 `permission`，也未提供 `permissionDefault`；`reload` 与 `unlock` 在执行体内另行检查 `chotentech.admin`
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ShopCommand.kt`

## 功能概述

商店系统入口与运维指令。

- `open`：按商店 ID 打开商店 GUI（`ShopMenu.open`），可对自己或指定在线玩家打开。
- `reload`：重载商店配置并重新加载礼包定义。
- `unlock`：批量或定向解锁商店配方（`unlockId`），写入玩家数据的 `unlockedShopItems` 集合。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-shop` | 打印指令用法帮助树（`createModernHelper()`） | 未声明（继承框架默认） |
| `/chotentech-shop open <shopId>` | 为自己打开指定商店 | 未声明（继承框架默认） |
| `/chotentech-shop open <shopId> <player>` | 为指定在线玩家打开指定商店 | 未声明（继承框架默认） |
| `/chotentech-shop reload` | 重载商店配置与礼包配置 | 运行时检查 `chotentech.admin` |
| `/chotentech-shop unlock <unlockId>` | 为所有已加载数据的玩家解锁该配方 | 运行时检查 `chotentech.admin` |
| `/chotentech-shop unlock <player> <unlockId>` | 为指定在线玩家解锁该配方 | 运行时检查 `chotentech.admin` |

> 3 个 `@CommandBody` 子命令（`open` / `reload` / `unlock`）+ 1 个默认主体（`main`）。源码中 `dynamic(` 出现 5 次（`shopId`、`player`、`unlockId`、`player`、`unlockId`）、`player(` 出现 1 次（`open` 下的 `player("player")`）。

## 指令详解

### `/chotentech-shop`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：未声明（继承框架默认）
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/cttshop`

---

### `/chotentech-shop open <shopId>`

- **用途**：为自己打开指定 ID 的商店界面。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `shopId` | 商店 ID（`dynamic("shopId")`） | 是 | `ShopManager.shops.keys`（已加载的全部商店 ID，`uncheck = true` 允许自由输入） |
- **权限**：未声明（继承框架默认）
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 取 `context["shopId"]` 后调用 `ShopMenu.open(sender, shopId)`。
  2. `ShopMenu.open` 会先 `ShopManager.getShop(shopId)`；ID 不存在时向打开者发送 `商店 <id> 不存在` 并返回。
  3. 存在则设置玩家会话的 `currentShopId`、清空选择，并渲染商店 GUI。
- **示例**：`/cttshop open main`

---

### `/chotentech-shop open <shopId> <player>`

- **用途**：为指定在线玩家打开商店界面（管理员代开）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `shopId` | 商店 ID（`dynamic("shopId")`） | 是 | `ShopManager.shops.keys`（`uncheck = true`） |
  | `player` | 目标玩家（`player("player")` 玩家类型参数） | 是 | 在线玩家名 |
- **权限**：未声明（继承框架默认）
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）——**注意**：该分支没有 `execute<Player>`，因此控制台也能代开
- **行为说明**：
  1. 用 `Bukkit.getPlayerExact(targetName)` 精确匹配在线玩家；找不到 → 提示 `玩家 <name> 不在线` 并中止。
  2. 找到则 `ShopMenu.open(target, shopId)`，界面开在目标玩家身上（不存在的商店 ID 同样会提示给目标玩家）。
  3. 执行者自身不会收到成功提示。
- **示例**：`/cttshop open main Steve`（控制台亦可：`cttshop open main Steve`）

---

### `/chotentech-shop reload`

- **用途**：重载商店配置与礼包配置。
- **参数**：无。
- **权限**：**运行时检查** `chotentech.admin`（无权限 → `§c你没有权限执行此命令`）
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. `ShopManager.reload()` 重载商店定义。
  2. `GiftPackManager.loadAll()` 重新加载礼包定义。
  3. 成功提示 `§a[Shop] 商店配置已重载!`。
- **示例**：`/cttshop reload`

---

### `/chotentech-shop unlock <unlockId>`

- **用途**：为**所有已加载数据的玩家**批量解锁某个商店配方。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `unlockId` | 配方/解锁 ID（`dynamic("unlockId")`） | 是 | 所有商店条目中非空的 `unlock` 字段去重后的集合（该 `suggestion` **未设置** `uncheck`，即候选来自实际配置） |
- **权限**：**运行时检查** `chotentech.admin`（无权限 → `§c你没有权限执行此命令`）
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. 遍历 `playerDataMap`（所有已加载玩家数据），对每个玩家的 `unlockedShopItems` 执行 `add(unlockId)`。
  2. `add` 返回 `true`（即此前未解锁）才计入 `count`。
  3. 提示 `§a[Shop] 已为 <count> 名在线玩家解锁配方: <unlockId>`。
  4. 返回值不落盘（由玩家数据存档流程负责）。
- **示例**：`/cttshop unlock sword_netherite`

---

### `/chotentech-shop unlock <player> <unlockId>`

- **用途**：为指定在线玩家解锁某个商店配方。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`，**字符串**参数，非 `player()` 类型） | 是 | 无内置补全（源码未提供 `suggestion`） |
  | `unlockId` | 配方/解锁 ID（`dynamic("unlockId")`） | 是 | 所有商店条目中非空的 `unlock` 去重集合（未设置 `uncheck`） |
- **权限**：**运行时检查** `chotentech.admin`（无权限 → `§c你没有权限执行此命令`）
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. `Bukkit.getPlayerExact(playerName)`；不在线 → `§c玩家 <name> 不在线`。
  2. 从 `playerDataMap[target.uniqueId.toString()]` 取数据；为 `null` → `§c玩家 <name> 没有数据`。
  3. `data.unlockedShopItems.add(unlockId)` 为 `true` → `§a[Shop] 已为 <name> 解锁配方: <id>`；否则 → `§e[Shop] <name> 已解锁该配方: <id>`。
- **示例**：`/cttshop unlock Steve sword_netherite`

## 备注 / 注意事项

- **权限模型**：`@CommandHeader` **没有** `permission` 参数（因此记录为"未声明（继承框架默认）"，也不存在 `PermissionDefault` 声明）。真正的门禁在 `reload` 与 `unlock` 的执行体内：`sender.hasPermission("chotentech.admin")`，失败即 `§c你没有权限执行此命令`。**`open` 分支完全没有权限检查**，任何能执行该指令的人都可打开商店（包括为他人代开）。
- **`open <shopId>` 仅玩家**：该分支使用 `execute<Player>`；带 `<player>` 的第二层分支使用 `execute<CommandSender>`，所以控制台代开只能走"带目标玩家"的形式。
- **`shopId` 与 `unlockId` 的补全语义不同**：`shopId` 的 `suggestion` 带 `uncheck = true`（允许输入任意值，非法值会在 `ShopMenu.open` 内提示"商店不存在"）；`unlockId` 的两处 `suggestion` **没有** `uncheck`（框架层面按候选值约束输入）。
- **`unlock <player> <unlockId>` 的 `player` 是普通动态参数**（`dynamic("player")`，字符串），通过 `Bukkit.getPlayerExact` 精确匹配、大小写敏感；与 `open` 下 `player("player")` 的玩家类型参数不是同一机制。
- **"所有玩家"实为"所有已加载数据"**：批量解锁遍历的是 `playerDataMap`，代码注释写"Find all players online and unlock"，实际范围是内存中已加载数据的玩家；离线但有数据的玩家也会被解锁，提示文案里的"在线玩家"是措辞。
- **不落盘**：`unlock` 只修改内存中的 `unlockedShopItems`，本指令不调用任何保存方法；`reload` 也只重载配置，不写玩家数据。
- **`reload` 额外动作**：除商店外还会执行 `GiftPackManager.loadAll()`（礼包定义一并重新加载）。
- **`newParser = true`**：该根指令启用了 TabooLib 新版命令解析器。
- **无关闭/列表子指令**：源码中没有 `close`、`list`、`buy` 之类分支，购买流程全部在 GUI 内完成。
