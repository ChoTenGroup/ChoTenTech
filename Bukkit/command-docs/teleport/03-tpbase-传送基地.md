# 传送基地

> **根指令**：`/tpbase`（无别名）
> **权限**：`chotentech.command.tpbase`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/TpBaseCommand.kt`

## 功能概述

把玩家传送到指定**基地**的中心点。基地数据来自 `BaseManager.baseMap`。命令优先查找基地所配置的 WorldGuard 区域 `base.region`，找到则传送到该区域的**几何中心**（通过 `WorldEditRegionConverter.convertToRegion(region).center` 计算）；若区域在全部已加载世界中都不存在，则**回退**到基地配置里记录的出生点 `base.spawn`（含 `yaw` / `pitch`）。

只有一个动态参数 `base`，无嵌套分支。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/tpbase <base>` | 传送到指定基地区域中心点（区域缺失时回退到配置出生点） | `chotentech.command.tpbase` |

## 指令详解

### `/tpbase <base>`

- **用途**：把执行者传送到目标基地的中心点。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `base` | 基地 ID（`dynamic`，字符串） | 必填 | `BaseManager.baseMap.keys.toList()`（基地配置中的键） |

- **权限**：`chotentech.command.tpbase`（`PermissionDefault.OP`，默认仅 OP）；`permissionMessage` 显式置空。源码中没有额外的 `sender.hasPermission(...)` 运行时检查。
- **执行者要求**：**仅玩家**（`execute<Player>`，且后续使用 `sender.teleport(...)`）。
- **行为说明**：
  1. 取 `val idef = context["base"].uppercase()`，用**大写化后**的字符串查询 `BaseManager.baseMap[idef]`；查不到则提示 `<#E08A98>基地 <IDEF> 不存在！`（提示里显示的是大写化之后的 ID）。
  2. 遍历 `Bukkit.getWorlds()`，对每个世界调用 `WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(w))?.getRegion(base.region)`，命中第一个非空区域即记录 `regionWorld` 与 `region` 并跳出（注释说明：WG 区域可能不在配置出生点所在世界）。
  3. **区域存在**：`WorldEditRegionConverter.convertToRegion(region)` 求中心点，在 `submit { }` 中执行 `sender.teleport(Location(regionWorld, center.x, center.y, center.z))`，随后提示 `<#8FD4A6>已传送到 <#EFDF8F><基地名><#8FD4A6> 的中心点`（此处使用的是 `base.name`，不是 ID）。
  4. **区域不存在**：走回退分支，读取 `base.spawn`；若 `Bukkit.getWorld(loc.world)` 为 `null` 则提示 `<#E08A98>基地 <base.name> 的世界 <loc.world> 不存在！` 并返回；否则在 `submit { }` 中 `sender.teleport(Location(world, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))`，提示 `<#8FD4A6>已传送到 <#EFDF8F><base.name>`（注意该分支提示**不带**"的中心点"字样）。
  5. 所有传送都在 `submit { }`（主线程下一 tick 调度）里执行。
- **示例**：
  ```
  /tpbase alpha
  /tpbase BASE_01
  ```

## 备注 / 注意事项

- **大小写敏感风险**：查询前对输入执行了 `uppercase()`，但自动补全列表直接来自 `BaseManager.baseMap.keys` 的原始键。若配置中的键不是全大写，补全出来的值可能查不到（需配置约定为大写键）。错误提示里显示的是大写化后的 ID，可能与玩家输入大小写不同。
- **区域中心取整点几何中心**：只使用 `center.x/y/z`，**不带 yaw/pitch**，传送后朝向由客户端/服务端默认行为决定；回退分支才使用配置中的 `yaw`/`pitch`。
- **依赖 WorldGuard + WorldEdit**：源码直接引用 `com.sk89q.worldguard` 与 `WorldEditRegionConverter`，缺少这些插件时该功能不可用。
- **扫描全部世界**：为定位区域会遍历所有已加载世界，属于服务器级开销（每个世界一次 regionContainer 查询）。
- 无别名（`aliases` 未声明），`CommandHeader` 中 `newParser = false`。
