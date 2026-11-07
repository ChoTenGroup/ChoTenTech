# ChotenBackpack 背包管理（/chotenbackpack）

> **根指令**：`/chotenbackpack`（别名：`/cb`, `/cho`）
> **权限**：`chotentech.command.backpack`（默认：OP —— 注解未显式声明 `permissionDefault`，取 TabooLib 默认值 `PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/BackpackCommand.kt`

## 功能概述

`BackpackCommand` 是 ChoTenBackpack（物理背包物品）系统的运维指令，用于**解封因疑似刷物被封禁的背包**，以及**查看手持背包物品的档案信息**。

背包数据以物理物品上的 ItemTag UUID 为主键保存在数据库中（`PlayerBackpack`，`@Id backpackId`，通过 TabooLib PTC mapper 映射到 `config.yml` 中 `database` 设置指向的 `backpack.db`）。当系统判定玩家疑似刷物时会调用「标记嫌疑并禁用背包」（写入 `suspectedPlayers`、置 `disabled = true`），本指令的 `unlock` 就是把 `disabled` 改回 `false`。注意本文件所有消息都是**旧式 `§` 颜色码 + `sendMessage`**，与本插件其他指令的 MiniMessage 风格不同。

`BackpackCommand` **没有定义无参数主体**（没有 `mainCommand`），因此单独执行 `/chotenbackpack`、`/cb`、`/cho` 不会有任何帮助输出，必须带上 `backpack` 这一层字面量。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotenbackpack backpack unlock <uuid>` | 按背包 UUID 解封 | `chotentech.command.backpack` |
| `/chotenbackpack backpack unlock` | 从主手物品读取 UUID 并解封 | `chotentech.command.backpack` |
| `/chotenbackpack backpack info` | 查看主手背包物品的数据信息 | `chotentech.command.backpack` |

## 指令详解

### `/chotenbackpack backpack unlock <uuid>`
- **用途**：按 UUID 解封指定背包（把 `disabled` 置回 `false`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `uuid` | 背包物品 UUID，对应 `PlayerBackpack.backpackId` | 是 | 动态参数，**无自动补全** |

- **权限**：`chotentech.command.backpack`
- **执行者要求**：玩家 / 控制台均可（该分支不做 `sender is Player` 判断，控制台可用）
- **行为说明**：
  1. `BackpackManager.mapper.findOneByKey(PlayerBackpack(uuid, "", "", 0))` 以 `backpackId` 查库；查不到 → `§c未找到 UUID 为 <uuid> 的背包`；
  2. 该背包当前未禁用 → `§e该背包未被禁用`（不产生任何写入）；
  3. 否则 `BackpackManager.updateBackpack(bp.copy(disabled = false))` 覆盖整行（其余列保持原值），并回 `§a已解锁背包 <uuid>`。
- **示例**：`/cb backpack unlock 3f9a1c2e-…` → 该背包解封，玩家可再次打开。

### `/chotenbackpack backpack unlock`
- **用途**：从主手所持背包物品上读取 UUID 并解封。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数（从 `sender.inventory.itemInMainHand` 的 ItemTag 读取） | — | — |

- **权限**：`chotentech.command.backpack`
- **执行者要求**：仅玩家（控制台执行时回 `§c请手持背包物品或指定 UUID`）
- **行为说明**：
  1. 主手为 `Material.AIR` → `§c请手持背包物品`；
  2. 读取 ItemTag `BackpackConfig.uuidTag`（`"<tag>-uuid"`，默认即 `cho-backpack-uuid`）字符串；缺失 → `§c手中物品不是背包物品`；
  3. 查库无记录 → `§c未找到背包数据`；
  4. 未禁用 → `§e该背包未被禁用`；
  5. 成功 → `updateBackpack(bp.copy(disabled = false))` + `§a已解锁背包 <uuid>`。
- **示例**：`/cb backpack unlock`（手持被禁用的背包）→ `§a已解锁背包 <uuid>`。

### `/chotenbackpack backpack info`
- **用途**：查看主手背包物品的完整数据（类型、格数、封禁状态、打开记录、嫌疑名单）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数（读取主手物品） | — | — |

- **权限**：`chotentech.command.backpack`
- **执行者要求**：仅玩家（控制台执行时回 `§c请手持背包物品`）
- **行为说明**：主手为空时**静默返回**（无提示）；物品不是背包物品 → `§c手中物品不是背包物品`；库中无记录 → `§c该背包尚无数据`。查到后依次输出：
  - `§6=== 背包信息 ===`
  - `§7UUID: §f<backpackId>`
  - `§7类型: §f<type>`
  - `§7格子: §f<slotCount>`
  - `§7状态: §c已禁用` 或 `§a正常`
  - `§7打开记录: §f<openedBy>`（JSON 数组原文）
  - 仅当 `suspectedPlayers != "[]"` 时追加 `§c嫌疑玩家: §f<suspectedPlayers>`（JSON 数组原文）
- **示例**：`/cb backpack info`（手持背包）→ 打印上述 6~7 行信息。

## 备注 / 注意事项

- **只解封、不解封以外的操作**：本指令**没有** `lock`/禁用、`give` 发背包、`clear` 清空等子指令；解封后 `suspectedPlayers`（嫌疑名单）仍保留原文，需要人工判断是否处理。
- **数据层**：`BackpackManager.mapper` 映射 `PlayerBackpack`，数据库文件来自 `config.yml` 的 `database` 配置项（`db("config.yml", "database", "backpack.db")`）。`unlock` 走的是 `updateByKey` 语义的整行覆盖（`bp.copy(disabled = false)`），内容 `content`、`slotCount`、`type`、`openedBy`、`suspectedPlayers` 均保持不变。
- **UUID 键**：物理背包的唯一标识存放在物品 ItemTag 的 `BackpackConfig.uuidTag`（= `"$tag-uuid"`，`tag` 默认 `cho-backpack`，可被配置改写）。注意 `BackpackConfig.load()` 会在 `/ctt reload` 时刷新，改了 `tag` 之后旧物品的 UUID 标签将读不到。
- **无自动补全**：`unlock <uuid>` 的 `uuid` 是纯 `dynamic` 参数，没有 `suggestion`，只能手工输入或从数据库/日志中复制。
- **无帮助输出**：未定义无参主体，`/cb` 单独执行无任何反应；用法树的入口是其他指令的 `createModernHelper`，本指令未接入该 helper。
- **权限**：仅声明根权限 `chotentech.command.backpack`（默认 OP），源码中无 `sender.hasPermission(...)` 的运行时校验。
