# CTTBASE 基地系统

> **根指令**：`/chotentech-base`（别名：`/cttbase`, `/cttb`）
> **权限**：`chotentech.command.base`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/BaseCommand.kt`

## 功能概述

`BaseCommand` 是基地/阵营系统的管理入口，提供四类能力：打开阵营选择界面（`FactionSelectMenu`）、打开基地图鉴（`BaseCodexMenu`）、把玩家直接加入某个基地（`BaseManager.joinBase`）、以及列出基地清单（可按阵营过滤）。

基地定义由 `BaseManager.loadBases(baseConfig)` 从基地配置加载为 `BaseManager.baseMap`（键为基地 `idef`，值含 `name`、`faction`、`region`、`enable`、`spawn` 等字段）；`region` 未配置时默认取 `base-<idef 小写>`，与 WorldGuard 区域及存档点（`saves.yml` 中 `base:` 字段、`base-` 前缀区域）联动。无权限时不输出提示（`permissionMessage = ""`），`newParser = false`。

本指令是 `/ctt base …` 的同一对象（`MainCommand` 中 `@CommandBody val base = BaseCommand`）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttbase` | 无参数：输出用法树（`createModernHelper`） | `chotentech.command.base` |
| `/cttbase open` | 为自己打开阵营选择界面 | `chotentech.command.base` |
| `/cttbase open <target>` | 为指定玩家打开阵营选择界面 | `chotentech.command.base` |
| `/cttbase codex` | 为自己打开基地图鉴 | `chotentech.command.base` |
| `/cttbase codex <target>` | 为指定玩家打开基地图鉴 | `chotentech.command.base` |
| `/cttbase join <idef>` | 把执行者加入指定基地 | `chotentech.command.base` |
| `/cttbase list` | 按阵营分组列出全部基地 | `chotentech.command.base` |
| `/cttbase list <faction>` | 列出指定阵营的基地及其区域 | `chotentech.command.base` |

## 指令详解

### `/cttbase`
- **用途**：输出用法树。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.base`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：调用 `createModernHelper()`（实现位于 `MainCommand.kt`），首行 `Usage: /chotentech-base`，随后树形列出 `open` / `codex` / `join` / `list`。纯展示。
- **示例**：`/cttbase` → 打印用法树。

### `/cttbase open`
- **用途**：为自己打开阵营选择界面。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.base`
- **执行者要求**：仅玩家（`execute<Player>`；控制台执行该分支不会运行）
- **行为说明**：`FactionSelectMenu().open(sender)`，只打开 GUI，不改任何数据（真正的加入动作由玩家在界面内或 `/cttbase join` 触发）。
- **示例**：`/cttbase open` → 弹出阵营/基地选择界面。

### `/cttbase open <target>`
- **用途**：为指定在线玩家打开阵营选择界面。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | `player(...)` 组件，在线玩家名补全 |

- **权限**：`chotentech.command.base`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：`context.player("target").castSafely<Player>()` 为 null 时静默返回；否则 `FactionSelectMenu().open(target)`。不向操作者发送确认消息。
- **示例**：`/cttbase open Steve` → Steve 屏幕上弹出阵营选择界面。

### `/cttbase codex`
- **用途**：为自己打开基地图鉴（`BaseCodexMenu`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.base`
- **执行者要求**：仅玩家（源码判定 `sender is Player`；控制台执行时提示 `§c请指定玩家: /cttbase codex <player>`）
- **行为说明**：`BaseCodexMenu().open(sender)`。只读界面（展示基地条目，图鉴的发现状态由 `BaseManager.discoverBase` 在游戏内触发）。
- **示例**：`/cttbase codex` → 打开基地图鉴。

### `/cttbase codex <target>`
- **用途**：为指定在线玩家打开基地图鉴。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家名 | 是 | `player(...)` 组件，在线玩家名补全 |

- **权限**：`chotentech.command.base`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：用 `Bukkit.getPlayerExact(context["target"])` 查玩家（**区分大小写的精确匹配**），为 null → `§c玩家 <target> 不在线！`；否则 `BaseCodexMenu().open(target)`。
- **示例**：`/cttbase codex Steve` → Steve 打开基地图鉴。

### `/cttbase join <idef>`
- **用途**：把执行者直接加入指定基地。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `idef` | 基地 ID（`BaseManager.baseMap` 的键），入参会被 `uppercase()` | 是 | 补全为 `BaseManager.baseMap.keys` |

- **权限**：`chotentech.command.base`
- **执行者要求**：仅玩家（`execute<Player>`）
- **行为说明**：入参 `uppercase()` 后交给 `BaseManager.joinBase(player, idef)`：
  1. 基地不存在 → `基地 <idef> 不存在！`；
  2. `base.enable == false` → `该基地尚不可用！`；
  3. 玩家数据未加载（`player.getData()` 为 null）→ 返回 false，**静默失败**；
  4. 写入 `data.mainTag = base.faction`、`data.subTag = base.idef`（即直接覆盖当前阵营/基地归属）；
  5. 调用 `grantBaseStartItems` 发放该基地的初始物资（`BaseStartItemsConfig.getStartItems(idef)` 写入玩家仓库 `warehouseContents[idef]`，受 `WarehouseConfig.getMaxSlots` 限制，并记录到 `grantedStartItems`）；
  6. 发送 `你已加入 <基地名>` 与 `阵营: <faction> | 基地: <基地名>`；
  7. 若 `base.spawn` 的世界已加载，`teleportAsync` 到该坐标（含 yaw/pitch）。
- **示例**：`/cttbase join MEG-A1` → 执行者阵营变 `MEG`、基地变 `MEG-A1`，拿到初始物资并被传送到基地出生点。

### `/cttbase list`
- **用途**：按阵营分组列出全部基地及其可用状态。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.base`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：先发 `===== 基地列表 (N) =====`（N = `baseMap.size`），随后按 `base.faction` 分组，每个阵营一行 `<b>FACTION</b> n个基地`，组内每个基地一行 ` <idef> - <name> <可用|不可用>`（`base.enable` 决定）。只读。
- **示例**：`/cttbase list` → 输出全部基地并标注可用性。

### `/cttbase list <faction>`
- **用途**：列出指定阵营的基地（额外显示区域 ID）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `faction` | 阵营名（入参会被 `uppercase()`，查询时忽略大小写） | 是 | 补全提示 `MEG`、`BNTG`、`UEC` |

- **权限**：`chotentech.command.base`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：`BaseManager.getBasesByFaction(faction)`（忽略大小写过滤 **全部基地**，不区分是否启用）；为空 → `未找到阵营 <faction> 的基地`；否则先发 `<faction> 基地列表:`，每个基地两行：` <idef> - <name> <可用|不可用>` 与 `   区域: <base.region>`。
- **示例**：`/cttbase list MEG` → 列出所有 MEG 基地及其 WorldGuard 区域 ID。

## 备注 / 注意事项

- **颜色映射**：`factionColor` 固定映射 `MEG → #cfc26e`、`BNTG → #94dbbe`、`UEC → #cf856e`，其余阵营回退 `white`。新增阵营不会自动获得专属配色（仅影响显示）。
- **`join` 无任何前置校验**：不检查阵营冲突、是否已加入、是否需要审批，直接覆盖 `mainTag`/`subTag`；`idef` 会被 `uppercase()`，因此基地配置里的 `idef` 必须与「大写形式」一致才能被找到（`baseMap` 用配置节键原样作键）。
- **`join` 的失败静默点**：玩家数据未加载时 `getData()` 为 null，`joinBase` 直接返回 false，指令侧不打印任何消息。
- **初始物资副作用**：`joinBase` 会调用 `grantBaseStartItems`，向玩家仓库写入该基地初始物资；已有 `grantedStartItems` 记录或仓库非空时不会重复发放（受 `BaseManager` 内部判断控制），重置请参考 `BaseManager.resetBaseWarehouse`（本指令未暴露）。
- **`codex` 的匹配方式**：走 `Bukkit.getPlayerExact`，大小写敏感；`open` 走 `player(...)` 组件（`context.player`），两者实现不一致，写脚本时不要混用。
- **权限粒度**：四个子节点共用根权限 `chotentech.command.base`（默认 OP），源码中无 `sender.hasPermission(...)` 运行时校验。`list` / `open` 这类只读/自助入口若需开放给普通玩家，需自行授予该权限。
- **与本指令无关但与基地联动**：`/cttadmin feature createspawn … BASE` 会写入 `base:` 字段、`/cttadmin manage setspawn` 会写 `data.baseSave`；两者都依赖 `BaseManager.getBaseByRegion` 与 `base-` 前缀的 WorldGuard 区域。
