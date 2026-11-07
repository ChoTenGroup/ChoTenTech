# 藏身处（/hideout）

> **根指令**：`/hideout`（别名：无，`aliases = []`）
> **权限**：`chotentech.command.hideout`（默认：OP，`PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/function/hideout/HideoutCommand.kt`

## 功能概述

个人基地（base）的「藏身处」系统入口。藏身处数据挂在每个玩家的 `PlayerData.hideoutData` 上，键为基地 key：`global` 或基地 ID 的**大写形式**；每个 key 对应一份 `HideoutPlayerData`（含 `unlocked` 是否解锁、`fuelAmount` 燃料量、`moduleLevels` 模块等级表）。

指令分两类用途：

- **玩家侧**：`/hideout open` 打开个人基地主菜单、`/hideout unlock` 解锁个人基地；
- **管理侧**：`/hideout manage <玩家> ...` 查看/改写任意玩家的藏身处数据（等级、燃料、重置）。

数据只影响 ChoTenTech 自己的藏身处模块；`manage` 分支在改动后会调用 `DataManager.persistPlayerData(target)` 落库。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/hideout` | 无参数：打印指令树（`createModernHelper()`） | `chotentech.command.hideout` |
| `/hideout open` | 打开自己当前所在基地的藏身处主菜单 | `chotentech.command.hideout` |
| `/hideout open <target>` | 为指定在线玩家打开其藏身处主菜单 | `chotentech.command.hideout` |
| `/hideout unlock` | 解锁自己在当前基地的藏身处 | `chotentech.command.hideout` |
| `/hideout unlock <target>` | 解锁指定在线玩家在当前基地的藏身处 | `chotentech.command.hideout` |
| `/hideout manage <player>` | 查看该玩家所有基地的藏身处数据 | `chotentech.command.hideout` |
| `/hideout manage <player> setlevel <base> <module> <level>` | 设置模块等级 | `chotentech.command.hideout` |
| `/hideout manage <player> setfuel <base> <amount>` | 设置燃料量 | `chotentech.command.hideout` |
| `/hideout manage <player> reset <base>` | 重置某基地藏身处数据（`base` 可为 `all`） | `chotentech.command.hideout` |

> 所有 `@CommandBody` 都**未**单独声明 `permission`，全部继承 header 的 `chotentech.command.hideout`（默认 OP）。

## 指令详解

### `/hideout`（无参数）

- **用途**：打印分层指令帮助。
- **参数**：无。
- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` 使用 `execute<ProxyCommandSender>`）。
- **行为说明**：`main = mainCommand { createModernHelper() }`；帮助树按执行者权限过滤。
- **示例**：`/hideout`

### `/hideout open`

- **用途**：打开自己的藏身处主菜单（`HideoutMainMenu`）。
- **参数**：无。
- **权限**：`chotentech.command.hideout`。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：
  1. 读取 `player.getData()`，为 null 时**静默返回**（无任何提示）；
  2. `HideoutManager.getCurrentBaseKey(player)` 取得当前基地 key，`data.hideoutData.getOrPut(baseKey)` 取（或新建）该基地的藏身处数据；
  3. 若 `unlocked == false`：提示「该基地的个人基地尚未解锁！」「输入 `/hideout unlock` 解锁」；
  4. 否则 `HideoutMainMenu(baseKey).open(player)`。
- **示例**：`/hideout open`

### `/hideout open <target>`

- **用途**：替指定在线玩家打开其藏身处菜单（管理员代操作）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（必须在线） | 必填 | TabooLib `player("target")` 自动补全在线玩家名 |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`，通过 `context.player("target")` 取目标）。
- **行为说明**：`context.player("target").castSafely<Player>()` 为 null → `§c玩家不在线！`；否则以该玩家身份走与上面相同的流程（含解锁校验）。菜单会开在**目标玩家**的客户端上。
- **示例**：`/hideout open Steve`

### `/hideout unlock`

- **用途**：解锁自己在当前基地的藏身处。
- **参数**：无。
- **权限**：`chotentech.command.hideout`。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：调用 `HideoutManager.unlockHideout(sender)`，解锁与扣费/条件校验的具体逻辑在 `HideoutManager` 内，本指令只做转发与提示。
- **示例**：`/hideout unlock`

### `/hideout unlock <target>`

- **用途**：替指定在线玩家解锁其当前基地的藏身处。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（必须在线） | 必填 | 在线玩家名自动补全 |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：目标不在线 → `§c玩家不在线！`；否则调用 `HideoutManager.unlockHideout(target)`。注意解锁的**对象是目标玩家的数据**，产生的费用/后果按 `HideoutManager` 的实现归属目标玩家。
- **示例**：`/hideout unlock Steve`

### `/hideout manage <player>`

- **用途**：查看任意玩家的藏身处数据总览。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名（**在线或离线**均可） | 必填 | 在线玩家名 |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - 目标解析顺序（`resolveTarget`）：先 `Bukkit.getPlayerExact(name)` 命中在线玩家 → 用 `playerDataMap`（键是 UUID）或 `DataManager.getData(uuid)`；再对所有在线玩家做**大小写不敏感**的名字匹配；最后回落到数据库 `DataManager.getDataByName(name)`（支持离线）。
  - 找不到 → `§c未找到该玩家的数据！`。
  - 输出标题 `===== <player> 的藏身处 =====`，逐个基地输出：基地 key、`已解锁`/`未解锁`、燃料量（保留 1 位小数）；若有模块等级，逐个输出 `- <模块名或模块ID> Lv.<等级>`。
  - `hideoutData` 为空 → 输出「无藏身处数据」。
  - 纯只读。
- **示例**：`/hideout manage Steve`

### `/hideout manage <player> setlevel <base> <module> <level>`

- **用途**：直接设置某玩家某基地的模块等级（后台调参用）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 在线玩家名 |
  | `base` | 基地 key；`global` 大小写不敏感，其它值统一转大写 | 必填 | 补全 `global` + `BaseManager.baseMap.keys` |
  | `module` | 模块 ID | 必填 | 补全 `all` + `HideoutConfig.modules.keys` |
  | `level` | 目标等级（整数，>= 0） | 必填 | 无 |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `level` 非法或 < 0 → `§c等级必须 >= 0！`。
  - 生效的 key 集合 = `{base}`，若该玩家的 `subTag`（所属子基地）不为空，**再追加**一个 `normalizeBaseKey(subTag)`；对每个 key 都执行：`hideoutData.getOrPut(key)` 并把 `unlocked = true`（即**顺带解锁**）。
  - `module == "all"`（大小写不敏感）：遍历 `HideoutConfig.modules`，每个模块写入 `level.coerceAtMost(mod.maxLevel)`（自动夹到上限），提示「已将 `<player>` 的 `<key>` 全部 N 个模块等级设为 `<level>`（自动限制最高等级）」。
  - 指定模块：模块不存在 → `§c未知模块: <id>`；`level > mod.maxLevel` → `§c模块最高等级为 <maxLevel>！`（**不夹取，直接拒绝**）；否则写入并提示「已将 `<player>` 的 `<key>.<模块名>` 等级设为 `<level>`」。
  - 任一 key 更新成功即 `DataManager.persistPlayerData(target)` 落库。
- **示例**：`/hideout manage Steve setlevel global all 3`、`/hideout manage Steve setlevel BASE_A generator 2`

### `/hideout manage <player> setfuel <base> <amount>`

- **用途**：直接设置某玩家某基地的藏身处燃料量。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 在线玩家名 |
  | `base` | 基地 key（`global` 不转换，其它转大写） | 必填 | 补全 `global` + `BaseManager.baseMap.keys` |
  | `amount` | 燃料量（浮点数，>= 0） | 必填 | 无 |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `amount` 解析失败或 < 0 → `§c燃料量必须 >= 0！`。
  - 与 `setlevel` 一样，key 集合 = `{base}`（+ `subTag` 对应的 key）；对每个 key：`getOrPut` 并 `unlocked = true`，写入 `fuelAmount = amount`，提示「已将 `<player>` 的 `<key>` 燃料设为 `<amount>`」。
  - 最后 `DataManager.persistPlayerData(target)` 落库。
  - 注意：这里**没有**对燃料上限做任何夹取或校验（不存在「0..上限」的判断），直接写入。
- **示例**：`/hideout manage Steve setfuel global 1000.5`

### `/hideout manage <player> reset <base>`

- **用途**：清除某玩家某基地（或全部基地）的藏身处数据。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 在线玩家名 |
  | `base` | 基地 key；`all` 表示全部 | 必填 | 补全 `global` + `BaseManager.baseMap.keys` + `all` |

- **权限**：`chotentech.command.hideout`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `base` 归一化为 `normalizeBaseKey(base)`；若结果为 `"ALL"` **或**原始输入与 `all` 大小写不敏感相等 → `target.hideoutData.clear()`，提示「已重置 `<player>` 的所有基地藏身处数据」。
  - 否则 `target.hideoutData.remove(baseKey)`，提示「已重置 `<player>` 的 `<baseKey>` 藏身处数据」（无论该 key 是否存在都提示成功）。
  - 最后 `DataManager.persistPlayerData(target)` 落库。
  - **不涉及**基地仓库/初始物资（那是 `/whmanage` 的 `reset`）。
- **示例**：`/hideout manage Steve reset all`、`/hideout manage Steve reset global`

## 备注 / 注意事项

- **基地 key 归一化规则**：`normalizeBaseKey(raw)` = `raw` 忽略大小写等于 `global` 时返回 `global`，否则返回 `raw.uppercase()`。因此补全里的 `global` 必须小写才保持 `global`，写 `GLOBAL` 会被转成 `ALL`… 实际会被转成 `GLOBAL`（注意与 `all` 判断的交互：只有 `all` 才展开为全部）。
- **`setlevel` / `setfuel` 会顺带把基地设为已解锁**（`hd.unlocked = true`），这是隐式副作用。
- **`subTag` 会被同步修改**：当玩家的 `PlayerData.subTag` 非空时，除指定 `base` 外，还会对 `subTag` 对应的藏身处 key 做同样的修改。
- **`setlevel` 的两种模式对超限处理不同**：`module = all` 时夹取到 `maxLevel`；指定模块时超限直接报错拒绝。
- **`open` / `unlock` 的目标玩家必须在线**；`manage` 系列支持离线玩家（回落到数据库），但离线读取依赖 `DataManager.getDataByName`。
- **数据为 null 时静默返回**：`openHideout` 中 `player.getData() == null` 不会给任何提示（与 `unlock`、`manage` 的显式报错不同）。
- **权限粒度为整棵树**：`open` / `unlock` 与 `manage` 共用 `chotentech.command.hideout`，没有把管理分支单独拆权限；想只给玩家开放前台功能，需要额外用权限插件按指令串限制，或改源码加 `@CommandBody(permission = ...)`。
