# 仓库指令（/warehouse、/whforce）

> **本文件包含两个根指令**：`warehouse`（别名 `wh`）与 `whforce`（别名 `whf`），二者定义在同一个源码文件里。
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/WarehouseCommand.kt`

---

## 一、`/warehouse`（别名 `/wh`）

> **根指令**：`/warehouse`（别名：`/wh`）
> **权限**：`chotentech.command.warehouse`（默认：所有人，`PermissionDefault.TRUE`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/WarehouseCommand.kt`（`object WarehouseCommand`）

### 功能概述

玩家打开**自己所属基地**的仓库界面（`WarehouseMenu`）。基地 ID（源码称 `baseIdef`）的来源有两级回退：

1. 优先用玩家数据里的 `PlayerData.subTag`（玩家当前所属子基地）；
2. 若 `subTag` 为空，则通过玩家当前所在的 WorldGuard 区域反查（`getCurrentBaseIdef`：遍历 `playerRegions[uuid]`，取第一个以 `base-` 开头的区域，用 `BaseManager.getBaseByRegion(regionId)` 找到基地并返回其 `idef`）。

指令本身不区分仓库内容归属，仓库数据的读写全部由 `WarehouseMenu` 完成。

### 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/warehouse`（`/wh`） | 打开自己所属基地的仓库界面 | `chotentech.command.warehouse` |

> 该对象只有 **1 个** `@CommandBody`（`val warehouse = mainCommand { ... }`），没有任何子指令字面量。

### 指令详解

#### `/warehouse`（即 `/wh`）

- **用途**：打开自己所属基地的仓库 GUI。
- **参数**：无（`mainCommand` 下没有 `dynamic` / `literal` / `player` 分支）。
- **权限**：`chotentech.command.warehouse`（默认所有人）。
- **执行者要求**：**仅玩家**（`execute<Player>`，控制台执行会被 TabooLib 拒绝）。
- **行为说明**（依次校验）：
  1. `sender.getData()` 为 null → 「数据加载失败！」；
  2. `data.subTag` 为空时调用 `getCurrentBaseIdef(sender)`；两者都拿不到 → 「你没有加入任何基地，也无法检测到所在的基地！」；
  3. 通过校验后 `WarehouseMenu().open(sender, baseIdef)`。
- **示例**：`/wh`

### 备注 / 注意事项（`/warehouse`）

- **`baseIdef` 优先取 `subTag`**：玩家属于子基地时，即使站在另一个 `base-*` 区域里，打开的仍是 `subTag` 对应的仓库。
- **区域反查依赖 `playerRegions` 缓存**：`getCurrentBaseIdef` 读的是 `ChoTenTech.playerRegions[uuid]`（由别处的区域监听维护），缓存未建立时会返回 null，从而走「你没有加入任何基地…」分支。
- **只匹配以 `base-` 开头的区域**，并且只取 `BaseManager.getBaseByRegion` 能解析出的第一个。
- 该指令**没有** `newParser` 声明（header 里未写），与同文件的 `whforce` 不同。

---

## 二、`/whforce`（别名 `/whf`）

> **根指令**：`/whforce`（别名：`/whf`）
> **权限**：`chotentech.command.whforce`（默认：OP，`PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/WarehouseCommand.kt`（`object WhForceCommand`）

### 功能概述

管理员用：**跳过所属基地判断**，直接打开**指定基地**的仓库界面。常用于排查玩家仓库显示异常、或替玩家核对某基地仓库内容。

### 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/whforce`（`/whf`） | 无参数：打印指令树（`createModernHelper()`） | `chotentech.command.whforce` |
| `/whforce <base>` | 强制打开指定基地的仓库界面 | `chotentech.command.whforce` |

> 该对象同样只有 **1 个** `@CommandBody`（`val main = mainCommand { dynamic("base") { ... } }`），`dynamic` 嵌在 `mainCommand` 内。

### 指令详解

#### `/whforce <base>`

- **用途**：强制打开指定基地的仓库 GUI。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `base` | 基地 ID（`baseIdef`），会被转成大写后精确匹配 | 必填 | `BaseManager.baseMap.keys.toList()` |

- **权限**：`chotentech.command.whforce`（默认 OP）。
- **执行者要求**：**仅玩家**（`execute<Player>`；`suggestion<Player>` 也按玩家类型声明）。
- **行为说明**：
  - `context["base"].uppercase()` 后查 `BaseManager.baseMap[baseIdef]`；不存在 → 「基地 `<BASE>` 不存在！」（消息里显示的是**大写后**的 ID）。
  - 存在 → `WarehouseMenu().open(sender, baseIdef)`。注意：打开的是**执行者自己**的仓库视图，只是仓库所属基地被强制指定为 `<base>`。
- **示例**：`/whf base_a`

### 备注 / 注意事项（`/whforce`）

- **参数被强制大写**：`global` 之类小写 ID 会被转成 `GLOBAL` 再查表，若 `BaseManager.baseMap` 的键不是大写，匹配会失败。补全列表来自 `baseMap` 的真实键，因此**用补全选中的值最稳妥**（但选中后仍会被 uppercase 一次）。
- **没有无参行为分支**：`mainCommand { dynamic("base") { ... } }` 没有 `execute`，因此 `/whf` 单打时不会执行任何业务逻辑（指令树里也不会走 `createModernHelper()`——`mainCommand` 块内并没有调用它）。
- **不会降低权限**：`whforce` 的默认值 `OP`，普通玩家即使有 `warehouse` 权限也打不开。
- **只影响界面展示目标基地，不改数据**：本指令不改写玩家 `warehouseContents`，具体落库行为取决于 `WarehouseMenu` 内的操作。
- `newParser = false`（header 中显式声明）。

---

## 三、共性说明

| 对比项 | `/warehouse`（`/wh`） | `/whforce`（`/whf`） |
|---|---|---|
| 对象 | `WarehouseCommand` | `WhForceCommand` |
| 权限 | `chotentech.command.warehouse` | `chotentech.command.whforce` |
| 默认 | 所有人（`TRUE`） | OP（`OP`） |
| 基地来源 | `PlayerData.subTag` → 所在 WorldGuard `base-*` 区域 | 指令参数，强制大写 |
| 执行者 | 仅玩家 | 仅玩家 |
| 子指令 | 无 | 无（`dynamic("base")`） |
| 无参行为 | 打开仓库 | 打印指令树（`createModernHelper()`） |

> 两个根指令都在同一文件 `WarehouseCommand.kt` 中，由 TabooLib 在 `LifeCycle.ENABLE` 阶段扫描 `@CommandHeader` 自动注册，互不嵌套。
