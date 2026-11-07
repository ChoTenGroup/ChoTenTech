# 仓库管理（/whmanage）

> **根指令**：`/whmanage`（别名：`/whm`）
> **权限**：`chotentech.command.whmanage`（默认：OP，`PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/WhManageCommand.kt`

## 功能概述

管理员对**任意玩家**（在线或离线）的**基地仓库数据**（`PlayerData.warehouseContents`，结构为 `基地ID -> (物品键 -> 数量)` 的有序表）做查看与清理：

- `list`：按基地列出仓库里的物品条目；
- `remove`：按索引删掉某基地仓库里的第 N 个条目；
- `clear`：清空全部仓库，或只清空某个基地的仓库；
- `reset`：把仓库重置回「初始状态」，并对在线玩家**重新发放基地初始物资**（`BaseManager` 的 `auto-grant` 机制）。

与 `/wh`（打开自己的仓库界面）完全不同：本指令只动数据库/内存里的仓库**记录**，不打开任何 GUI。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/whmanage` | 无参数：打印指令树（`createModernHelper()`） | `chotentech.command.whmanage` |
| `/whmanage list <player>` | 列出该玩家所有基地仓库的物品条目 | `chotentech.command.whmanage` |
| `/whmanage remove <player> <base> <index>` | 删除该玩家某基地仓库中第 `index` 个条目（从 1 开始） | `chotentech.command.whmanage` |
| `/whmanage clear <player>` | 清空该玩家**所有**基地仓库内容 | `chotentech.command.whmanage` |
| `/whmanage clear <player> <base>` | 只清空该玩家指定基地的仓库 | `chotentech.command.whmanage` |
| `/whmanage reset <player>` | 重置该玩家所有仓库（并重新发放初始物资） | `chotentech.command.whmanage` |
| `/whmanage reset <player> <base>` | 重置该玩家指定基地的仓库（并重新发放初始物资） | `chotentech.command.whmanage` |

> 4 个 `@CommandBody`（`main` / `list` / `remove` / `clear` / `reset` 共 5 个属性，其中 `main` 无子节点）都**未**单独声明 `permission`，统一继承 header 的 `chotentech.command.whmanage`（默认 OP）。

## 指令详解

### `/whmanage`（无参数）

- **用途**：打印分层指令帮助。
- **参数**：无。
- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` → `execute<ProxyCommandSender>`）。
- **行为说明**：`main = mainCommand { createModernHelper() }`。
- **示例**：`/whm`

### `/whmanage list <player>`

- **用途**：查看某玩家各个基地仓库里存了哪些条目。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名，在线/离线均可 | 必填 | **无**源码级补全（`dynamic` 未声明 `suggestion`） |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - `loadPlayerData`：先 `Bukkit.getPlayerExact(name)` 命中在线玩家则取其内存 `PlayerData`；否则 `DataManager.getRawDataByName(name)` 读库并 `jsonUtils.fromJson` 反序列化。取不到 → 「玩家 `<player>` 不存在或没有数据！」。
  - 输出标题 `===== <player> 仓库一览 =====`，然后对每个 `baseIdef -> contents` 输出一行：`<基地名或ID> (<baseIdef>): <N> 个物品`（基地名取自 `BaseManager.baseMap[baseIdef]?.name`，找不到则回退显示 ID）。
  - 继而对每个条目按索引输出 `  #<i> (ID: <物品ID>)`（`i` 从 **1** 开始，只显示条目 key，**不显示数量**）。
  - `warehouseContents` 为空 → 输出「（空）」。
  - 纯只读，不落库。
- **示例**：`/whm list Steve`

### `/whmanage remove <player> <base> <index>`

- **用途**：按索引精确删除某玩家某基地仓库中的一个条目。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 无补全 |
  | `base` | 基地 ID（`baseIdef`），执行 `uppercase()` 后使用 | 必填 | **无**源码级补全（该 `dynamic("base")` 未声明 `suggestion`） |
  | `index` | 要删除的条目序号，从 **1** 开始 | 必填 | 无补全 |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - `index` 非数字或 `< 1` → 「索引必须大于 0！」。
  - 通过 `modifyWarehouse` 修改：`contents = data.warehouseContents[baseIdef] ?: linkedMapOf()`，用 `contents.entries.toList().getOrNull(index - 1)` 定位条目。
  - 命中 → 从 `data.warehouseContents[baseIdef]` 移除该 key，提示「已移除 `<player>` 的 `<BASE>` 仓库第 `<index>` 个物品」。
  - 未命中 → 「索引 `<index>` 超出范围（共 `<N>` 个物品）！」。
  - 无论是否命中，`modifyWarehouse` 都会在改动后保存（在线玩家 `DataManager.saveData(online)`；离线玩家序列化后 `saveUserDataRow`）。
- **示例**：`/whm remove Steve BASE_A 2` → 删除第 2 个条目（对应 `list` 里的 `#2`）。

### `/whmanage clear <player>`

- **用途**：一次性清空该玩家**所有**基地的仓库内容。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 无补全 |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`data.warehouseContents.clear()`，提示「已清空 `<player>` 的所有仓库内容」，随后保存。
- **示例**：`/whm clear Steve`

### `/whmanage clear <player> <base>`

- **用途**：只清空该玩家某个基地的仓库内容。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 无补全 |
  | `base` | 基地 ID（大写化后使用） | 必填 | `BaseManager.baseMap.keys.toList()` |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`data.warehouseContents.remove(baseIdef)`；返回非 null → 「已清空 `<player>` 的 `<BASE>` 仓库」；该基地本来没有数据 → 「该基地没有仓库数据！」。之后保存。
- **示例**：`/whm clear Steve BASE_A`

### `/whmanage reset <player>`

- **用途**：把该玩家**所有**仓库重置到初始状态，并（在线时）重新发放初始物资。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 无补全 |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - **在线分支**：取 `online.getData()`（null → 「玩家 `<player>` 没有数据！」），调用 `BaseManager.resetAllWarehouses(online, data)`（内部 `warehouseContents.clear()` + `grantedStartItems.clear()`，然后对在线玩家 `grantAutoStartItems` 重新发放所有 `auto-grant` 基地的初始物资），提示「已重置 `<player>` 的所有仓库(已重新发放初始物资)」，最后 `DataManager.saveData(online)`。
  - **离线分支**：从 `DataManager.getRawDataByName` 读原始 JSON → `jsonUtils.fromJson(PlayerData)` 反序列化（失败提示「数据解析失败！」/「数据为空！」）→ 调用 `BaseManager.resetAllWarehouses(null, data)`（`player` 传 null，因此**无法立即发放**）→ 提示「已重置 `<player>` 的所有仓库(auto-grant 基地下次进服自动补发)」→ 序列化后 `saveUserDataRow` 写回。
- **示例**：`/whm reset Steve`

### `/whmanage reset <player> <base>`

- **用途**：只重置该玩家某个基地的仓库，并（在线时）重新发放该基地初始物资。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 玩家名 | 必填 | 无补全 |
  | `base` | 基地 ID（大写化后使用） | 必填 | `BaseManager.baseMap.keys.toList()` |

- **权限**：`chotentech.command.whmanage`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 调用 `BaseManager.resetBaseWarehouse(player, data, baseIdef)`；该方法在 `BaseManager.baseMap` 中找不到该基地时返回 `false`，否则清除该基地的 `warehouseContents[idef]` 与 `grantedStartItems[idef]`，并在玩家在线且基地 `enable = true` 时立即 `grantBaseStartItems`。
  - 返回 `true` → 「已重置 `<player>` 的 `<BASE>` 仓库(已重新发放初始物资)」（离线时为「…(auto-grant 基地下次进服自动补发)」）；返回 `false` → 「基地 `<BASE>` 不存在！」。
  - 在线时随后 `DataManager.saveData(online)`；离线时序列化写回 `UserDataRow`。
- **示例**：`/whm reset Steve BASE_A`

## 备注 / 注意事项

- **`clear` 与 `reset` 的区别**：`clear` 只清内容（`warehouseContents`），不碰 `grantedStartItems`，因此**不会**触发重新发放；`reset` 会同时清 `grantedStartItems` 并触发初始物资重发，等于是「恢复出厂设置」。
- **离线玩家的初始物资延后发放**：离线分支把 `player` 参数传 `null` 给 `BaseManager`，所以只会把数据改好，`auto-grant` 基地的物资要等玩家下次进服时由 `grantAutoStartItems` 补发（提示文案已明确说明）。
- **`remove` 与 `list` 的索引必须一致使用**：`list` 的 `#i` 从 1 开始，`remove` 的 `index` 也是 1-based，且两者都按 `warehouseContents[baseIdef]` 这个 `LinkedHashMap` 的迭代顺序编号——**删除一条后，后续索引会整体前移**。
- **`base` 参数强制大写**：`remove` / `clear <base>` / `reset <base>` 都会 `uppercase()`，因此大小写不同的基地 ID 需要与 `BaseManager.baseMap` 的键一致（`clear`/`reset` 的补全直接给出 `baseMap.keys`，最保险）。
- **补全不一致**：`clear <base>` 与 `reset <base>` 有 `suggestion`（`BaseManager.baseMap.keys`），而 `remove <base>` **没有**；`player` 参数在整棵树里**都没有**玩家名补全。
- **离线保存路径**：离线修改走 `DataManager.saveUserDataRow(UserDataRow)`（含 `uuid` / `playerName` / `cid` / `cidType` / `data`）——若 `data.uuid` 或 `data.cid` 反序列化异常，会直接抛出（源码未对 `cid` 为空做保护）。
- **`modifyWarehouse` 的错误提示发给控制台**：在线玩家数据为 null、离线玩家不存在、离线数据解析失败这三类错误，`modifyWarehouse` 里用的是 `ChoTenTech.consoleSender.sendComponent(...)`，**执行者本人可能看不到提示**（只有控制台会收到）。
- **权限粒度为整棵树**：查看（`list`）与破坏性操作（`remove`/`clear`/`reset`）共用 `chotentech.command.whmanage`，无法只授予只读权限。
