# CTTDROP 投保 / 取回

> **根指令**：`/cttdrop`（无别名）
> **权限**：`chotentech.command.cttdrop`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/CttDropCommand.kt`

## 功能概述

`CttDropCommand` 是「掉落投保 / 投保物品取回」系统的管理入口：`insurance` 为指定玩家打开投保菜单（`InsuranceMenu`，用于把物品纳入投保），`cover` 为指定玩家打开取回菜单（`CoverMenu`，用于取回此前投保后归还的物品）。

指令本身不直接读写物品，只负责解析目标玩家并打开对应 GUI；对于**离线**玩家，`cover` 会退化为「只查询数据库并报告待取回物品数量」，不打开界面、不发放物品。

无参数时只打印两行用法提示（未使用其他指令的 `createModernHelper` 用法树）。根指令注解只声明了 `name`、`permission`、`permissionDefault`，**没有** 别名、`description`、`permissionMessage`、`newParser`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttdrop` | 无参数：打印两行用法提示 | `chotentech.command.cttdrop` |
| `/cttdrop insurance <player>` | 为指定在线玩家打开投保菜单 | `chotentech.command.cttdrop` |
| `/cttdrop cover <player>` | 为指定玩家打开取回菜单；离线时只报告待取回数量 | `chotentech.command.cttdrop` |

## 指令详解

### `/cttdrop`
- **用途**：显示本指令的两条用法。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.cttdrop`
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：固定发送两行文本：
  - `§6/cttdrop insurance <player> §7- 打开投保菜单`
  - `§6/cttdrop cover <player> §7- 取回投保物品`
- **示例**：`/cttdrop` → 打印上述两行。

### `/cttdrop insurance <player>`
- **用途**：为指定在线玩家打开投保菜单。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（必须在线） | 是 | 动态参数，**无自动补全**（未声明 `suggestion`） |

- **权限**：`chotentech.command.cttdrop`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：用 `Bukkit.getPlayerExact(name)` 解析（大小写敏感），为 null → `§c玩家 <name> 不在线！`；否则 `InsuranceMenu().open(target)` 并为操作者发送 `§a已为 §e<name> §a打开投保菜单。`。菜单内的投保/扣费逻辑由 `InsuranceMenu` 与 `InsuranceManager` 处理，本指令不写数据。
- **示例**：`/cttdrop insurance Steve` → Steve 打开投保菜单，控制台/管理员收到确认提示。

### `/cttdrop cover <player>`
- **用途**：为指定玩家打开「取回投保物品」菜单；目标离线时改为报告待取回数量。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（在线则开界面，离线则查库） | 是 | 动态参数，**无自动补全** |

- **权限**：`chotentech.command.cttdrop`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 先 `Bukkit.getPlayerExact(name)`；
  2. **在线**：`CoverMenu().open(target)`，并向操作者发送 `§a已为 §e<name> §a打开取回菜单。`；
  3. **离线**：调用 `DataManager.getDataByName(name)` 读取档案；数据为 null 或 `returnedInsuranceItems` 为空 → `§c玩家 <name> 没有待取回的物品。`；否则只报告 `§e<name> §a有 N 个待取回的物品。`（N = `returnedInsuranceItems.size`），**不会发放物品、也不会清空该列表**。
- **示例**：`/cttdrop cover Steve` → 在线时 Steve 打开取回界面；若 Steve 离线且有 3 件待取回物品，操作者只看到 `Steve 有 3 个待取回的物品。`

## 备注 / 注意事项

- **消息风格**：本文件全部提示使用**旧式 `§` 颜色码 + `sendMessage`**（虽然源码 `import` 了 `mmUtil`，但未实际使用），与 `MainCommand` / `CttAdminCommand` 的 MiniMessage 风格不一致；玩家端若装了 MiniMessage 过滤插件，这里不要写成 `<#RRGGBB>` 形式。
- **无自动补全**：`insurance` / `cover` 的 `player` 都是裸 `dynamic` 参数（没有 `suggestion`），不会像 `CttAdminCommand` 的 `player(...)` 那样补全在线玩家名。
- **必须在线才能真正操作**：两条子指令都通过 `Bukkit.getPlayerExact` 解析目标，`insurance` 对离线玩家直接拒绝；`cover` 对离线玩家只做只读统计——想真正把物品还回去，玩家必须在线并自己打开界面（或由管理员在线时打开）。
- **`cover` 离线分支是纯只读**：不修改 `returnedInsuranceItems`、不投递物品，重复执行会得到相同结果。
- **权限**：仅根权限 `chotentech.command.cttdrop`（默认 OP），源码中无 `sender.hasPermission(...)` 运行时校验；注解未设置 `permissionMessage`，无权限时使用 TabooLib 的默认提示。
- **依赖**：`InsuranceMenu` / `CoverMenu`（GUI）、`InsuranceManager`（投保逻辑）、`PlayerData.returnedInsuranceItems`（待取回物品列表，JSON 档案中的字段，`DataManager.getDataByName` 负责按名查库）。
