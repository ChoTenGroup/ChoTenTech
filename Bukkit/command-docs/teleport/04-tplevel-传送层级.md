# 传送层级

> **根指令**：`/tplevel`（别名：`/tpl`）
> **权限**：`chotentech.command.tplevel`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/TpLevelCommand.kt`

## 功能概述

把玩家传送到某个**层级（Level）**。层级数据来自全局 `levelsList`。命令有两个分支：

1. 只给层级名 —— 传送到该层级 WorldGuard 区域的**几何中心**，并刷新玩家层级数据（`ChoTenDataHelper(sender).updateLevel()`）。
2. 层级名 + 切出点（noclip）名 —— 传送到该切出点两个角坐标（`noclipPointLocation1` / `noclipPointLocation2`）的**中点**。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/tplevel <level>` | 传送到层级区域中心点并刷新层级数据 | `chotentech.command.tplevel` |
| `/tplevel <level> <noclip>` | 传送到该层级的指定切出点 | `chotentech.command.tplevel` |

## 指令详解

### `/tplevel <level>`

- **用途**：传送到指定层级的区域中心。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `level` | 层级 ID（`dynamic`，字符串） | 必填 | `levelsList.keys.toList()` |

- **权限**：`chotentech.command.tplevel`（`PermissionDefault.OP`）；`permissionMessage` 显式置空；源码中无额外 `hasPermission` 检查。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：
  1. 层级解析采用双重查找：`levelsList[context["level"]] ?: levelsList["level-${context["level"]}"]`，即支持传入裸名，也支持传入时自动补上 `level-` 前缀再次查找。
  2. 未找到：发送纯文本 `Level not found.`（无颜色标签）。
  3. 找到：先发送 `Teleporting to Level: <level.name>`，再用 `WorldEditRegionConverter.convertToRegion(level.region)` 求中心点，构造 `Location(level.getWorld(), center.x, center.y, center.z)`。
  4. 在 `submit { }` 中 `sender.teleport(teleportLocation)` 并调用 `ChoTenDataHelper(sender).updateLevel()`（传送后刷新玩家所属层级数据）。
- **示例**：
  ```
  /tplevel level-0
  /tplevel 0
  /tpl B1
  ```

### `/tplevel <level> <noclip>`

- **用途**：传送到指定层级的某个**切出点**（noclip point）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `level` | 层级 ID（`dynamic`，字符串） | 必填 | `levelsList.keys.toList()` |
  | `noclip` | 切出点名称（`dynamic`，字符串） | 必填 | 该层级下 `level.noclips` 中非空 `name` 的列表（`level` 为空时补全为空列表） |

- **权限**：`chotentech.command.tplevel`（默认 OP）。
- **执行者要求**：**仅玩家**（`execute<Player>`）。
- **行为说明**：
  1. 与上一分支相同的双重查找解析 `level`（先原样、再 `level-` 前缀）；为 `null` 时提示 `<#E08A98>层级 <level> 不存在`。
  2. 在 `level.noclips` 中按 `it.name == noclipName` 精确匹配切出点；未命中提示 `<#E08A98>切出点 <noclip> 不存在`。
  3. 计算世界：`point.noclipPointWorld?.let { Bukkit.getWorld(it) } ?: level.getWorld() ?: sender.world`——优先切出点自身声明的世界，其次层级世界，最后回退到执行者当前世界。
  4. 取 `point.noclipPointLocation1` 与 `noclipPointLocation2`，逐轴取平均得到中点：`cx=(v1.x+v2.x)/2.0`、`cy=(v1.y+v2.y)/2.0`、`cz=(v1.z+v2.z)/2.0`。
  5. 在 `submit { }` 中 `sender.teleport(Location(world, cx, cy, cz))`，随后提示 `<#8FD4A6>已传送到 <#EFDF8F><level.name><#8FD4A6> 的切出点 <#EFDF8F><noclip>`。
  6. 该分支**不**调用 `updateLevel()`。
- **示例**：
  ```
  /tplevel B1 exit_north
  /tpl level-B1 exit_north
  ```

## 备注 / 注意事项

- **双重键查找**：`levelsList[x]` 与 `levelsList["level-$x"]` 都会尝试，因此 `level-` 前缀可写可不写；但如果 `levelsList` 中同时存在 `x` 与 `level-x` 两个键，**不带前缀的键优先**。
- **切出点传送不带朝向**：构造的是三参数 `Location(world, x, y, z)`，`yaw`/`pitch` 为默认值。
- **层级分支缺少空值保护**：`/tplevel <level>` 分支直接调用 `level.region` 与 `level.getWorld()`，若 `getWorld()` 返回 null 会在 `Location` 构造处抛异常；该分支没有像 noclip 分支那样显式检查世界。
- **提示语言不一致**：层级分支的两个提示是英文原文（`Level not found.`、`Teleporting to Level: ...`，无颜色标签），noclip 分支是中文带颜色标签的提示。
- **依赖 WorldGuard / WorldEdit**：层级传送依赖 `WorldEditRegionConverter.convertToRegion(level.region)`。
- 所有传送都通过 `submit { }` 调度到主线程执行。
- `CommandHeader` 中 `newParser = false`。
