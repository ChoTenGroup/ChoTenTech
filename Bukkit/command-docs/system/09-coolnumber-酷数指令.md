# 酷数指令（/chotentech-coolnumber）

> **⚠ 当前状态：未启用。整个源码文件（第 1–142 行）全部被 `//` 注释掉，`@CommandHeader` 也在注释内，因此该根指令当前不会被注册，服务器上不存在 `/chotentech-coolnumber`、`/cttcn`、`/cn`、`/coolnumber`、`/coolnum` 中的任何一个。**
>
> **根指令（仅存在于被注释的代码中）**：`/chotentech-coolnumber`（别名：`/cttcn`, `/cn`, `/coolnumber`, `/coolnum`）
> **权限（仅存在于被注释的代码中）**：`chotentech.command.coolnumber`（默认：所有人，`PermissionDefault.TRUE`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/CoolNumberCommand.kt`

## 功能概述

**酷数（Cool Number，源码中亦写作「靓号」）** 系统的指令入口，用于：

- 玩家打开靓号选择菜单（`CoolNumberMenu`）；
- 管理员为玩家设置靓号号码与等级、重算等级、查看靓号信息、维护禁用号码列表。

本文件**当前整体被注释**（包括 `package` 声明与全部 `import`），因此：

1. `CoolNumberCommand` 这个 Kotlin 对象不存在于编译产物中；
2. `@CommandHeader(name = "chotentech-coolnumber", ...)` 不存在，TabooLib 扫描不到，**根指令不会注册**；
3. `MainCommand.kt` 中本来用于把它挂到 `/ctt` 下的那一行也被注释掉了：
   ```kotlin
   //    @CommandBody
   //    val coolnumber = CoolNumberCommand
   ```
   所以 `/ctt coolnumber ...` 同样不存在。

**但功能主体并没有消失**：数据层与界面层仍在编译：

| 组件 | 文件 | 状态 |
|---|---|---|
| `CoolNumberTier` / `CoolNumberInfo` / `CoolNumberCategory` | `data/CoolNumber.kt` | 已启用 |
| `CoolNumberManager`（评分、号码生成、校验、扣费） | `function/CoolNumberManager.kt` | 已启用 |
| `CoolNumberMenu`（靓号选择界面 + 搜索） | `gui/CoolNumberMenu.kt` | 已启用 |
| `CoolNumberConfirmMenu`（购买/确认界面） | `gui/CoolNumberConfirmMenu.kt` | 已启用 |
| 指令入口 `CoolNumberCommand` | `command/CoolNumberCommand.kt` | **已注释，未启用** |

即：**靓号系统作为 GUI/功能仍在，但缺少指令入口**——目前无法通过指令打开 `CoolNumberMenu`。

## 指令总览（以下均为「若取消注释并重新编译后」的形态，当前不可用）

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-coolnumber` | 无参数：打开靓号菜单（仅玩家） | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber open` | 打开靓号菜单（仅玩家） | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber open <target>` | 为指定玩家打开靓号菜单 | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber admin set <target> <number> <tier>` | 设置玩家靓号号码与等级 | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber admin recalc <target>` | 按号码分数重新计算等级 | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber admin info <target>` | 查看玩家靓号信息（号码/分数/等级） | `chotentech.command.coolnumber` |
| `/chotentech-coolnumber admin banned list` | 列出被禁止的靓号 | `chotentech.command.coolnumber` |

> 所有 `@CommandBody` 都未单独声明 `permission`，统一沿用 header 的 `chotentech.command.coolnumber`（被注释代码中的 `PermissionDefault.TRUE` = 所有人，包括 `admin` 分支——这是一个**设计缺陷**，恢复时建议给 `admin` 单独加权限）。

## 指令详解（以「若启用」的语义描述，均取自被注释的源码；**当前均不可执行**）

### `/chotentech-coolnumber`（无参数）

- **用途**：打开靓号菜单。
- **参数**：无。
- **权限**（若启用）：`chotentech.command.coolnumber`（默认所有人）。
- **执行者要求**：**仅玩家**——分支内判断 `sender is Player`，否则 `§c请使用此命令作为玩家执行`（注意这里用的是运行期判断，而非 `execute<Player>`）。
- **行为说明**（被注释的源码）：`if (sender is Player) CoolNumberMenu().open(sender) else 发送提示`。
- **示例**（若启用）：`/cttcn`

### `/chotentech-coolnumber open`

- **用途**：打开靓号菜单（与无参执行等价）。
- **参数**：无。
- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：**仅玩家**（同样是 `sender is Player` 的运行期判断）。
- **行为说明**：`CoolNumberMenu().open(sender)`。
- **示例**（若启用）：`/cttcn open`

### `/chotentech-coolnumber open <target>`

- **用途**：替指定玩家打开靓号菜单。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家 | 必填 | TabooLib `player("target")` 自动补全在线玩家名 |

- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：`context.player("target").castSafely<Player>() ?: return@execute`（取不到则**静默返回**，无提示），随后 `CoolNumberMenu().open(target)`。
- **示例**（若启用）：`/cttcn open Steve`

### `/chotentech-coolnumber admin set <target> <number> <tier>`

- **用途**：直接设置玩家靓号的号码与等级。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家 | 必填 | 在线玩家名自动补全 |
  | `number` | 靓号号码，**4–10 位纯数字** | 必填 | 无 |
  | `tier` | 等级 tag，需匹配 `CoolNumberTier.entries` 中某项的 `tag`（输入会 `uppercase()`） | 必填 | 无源码级补全；非法时报错列出全部合法 tag |

- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**（被注释的源码）：
  - `number.length < 4 || number.length > 10 || !number.all { it.isDigit() }` → 「§c无效号码：需要4-10位数字」；
  - `CoolNumberTier.entries.firstOrNull { it.tag == tierStr }` 为 null → 「§c无效等级：可选 …」（列出所有 tag）；
  - 通过后 `submitAsync` 异步执行：取 `target.getData()`（null → 「§c目标玩家数据不存在」），构造 `IDData(number.toLong(), tier.tag)`，`DataManager.modifyUserIDData(newCid, data)`；
  - 向执行者回显「§a已将 `<玩家名>` 的靓号设置为 `<number>` (`<等级显示名>`)」，并向目标玩家发送「§a管理员已将你的靓号设置为 `<color><number>`」。
- **示例**（若启用）：`/cttcn admin set Steve 888888 VIP`

### `/chotentech-coolnumber admin recalc <target>`

- **用途**：按号码的评分重新计算该玩家的等级 tag。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家 | 必填 | 在线玩家名自动补全 |

- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**（被注释的源码）：异步取 `target.getData()`（null → 「§c目标玩家数据不存在」），`CoolNumberManager.calculateScore(data.cid.id.toString())` 得分数，`getTierFromScore(score)` 得等级，`data.cid.copy(type = tier.tag)` 写回 `DataManager.modifyUserIDData`；执行者收到「§a已重新计算 `<玩家名>` 的靓号等级：`<号码>` → 分数 `<score>` → `<等级显示名>`」，目标玩家收到对应通知。
- **示例**（若启用）：`/cttcn admin recalc Steve`

### `/chotentech-coolnumber admin info <target>`

- **用途**：查看玩家靓号信息。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家 | 必填 | 在线玩家名自动补全 |

- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**（被注释的源码）：`target.getData()`（null → 「§c目标玩家数据不存在」），输出三行：`§6=== <玩家名> 的靓号信息 ===`、号码（带等级颜色）、分数、等级显示名。当前 `CoolNumberTier.fromTag(data.cid.type)` 取等级、`calculateScore` 取分数。
- **示例**（若启用）：`/cttcn admin info Steve`

### `/chotentech-coolnumber admin banned <add/remove/list>`

- **用途**：维护「禁止使用的靓号」列表。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `add/remove/list` | **这是一个 `dynamic` 参数（属性名就叫 `add/remove/list`），不是三选一的字面量**，源码把整个字符串当参数名 | 必填 | 无源码级补全 |

- **权限**（若启用）：`chotentech.command.coolnumber`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**（被注释的源码）：
  - 该 `dynamic` 的默认 `execute` 永远输出「§c用法: /cttcn admin banned add|remove|list `<number>`」——即 **`add` / `remove` 并未实现**，只有提示；
  - 其下唯一的 `literal("list")` 会输出「§6禁止的靓号列表:」并逐行打印 `CoolNumberManager.bannedNumbers` 中的每一项。
  - 结构上 `literal("list")` 嵌在 `dynamic("add/remove/list")` 之内，因此实际路径是 `admin banned <任意值> list`（两段都要给），只写 `admin banned list` 会命中 `dynamic` 的默认提示分支。
- **示例**（若启用）：`/cttcn admin banned x list`

## 备注 / 注意事项

- **注册状态（核心结论）**：`CoolNumberCommand.kt` 的 **142 行全部以 `//` 开头**，包含 `package`、`import`、`@CommandHeader`、`object CoolNumberCommand` 与所有 `@CommandBody`。因此：
  - 根指令 `/chotentech-coolnumber` 及其 4 个别名（`cttcn`、`cn`、`coolnumber`、`coolnum`）**当前不存在**，输入会得到「未知指令」；
  - `/ctt coolnumber ...` 也不存在，因为 `MainCommand.kt` 第 67–68 行的挂载同样被注释。
- **功能未删除**：`data/CoolNumber.kt`、`function/CoolNumberManager.kt`、`gui/CoolNumberMenu.kt`、`gui/CoolNumberConfirmMenu.kt` 都在正常编译，靓号的数据与购买流程仍然存在，只是**没有指令入口**去打开 `CoolNumberMenu`（除非别的代码路径调用）。
- **恢复方式**：取消 `CoolNumberCommand.kt` 全部行首 `//`，并取消 `MainCommand.kt` 中 `val coolnumber = CoolNumberCommand` 两行的注释，然后重新编译；同时建议给 `admin` 分支加独立权限（当前 header 是 `PermissionDefault.TRUE`，会让所有玩家都能执行管理分支）。
- **`banned` 分支不完整**：`add` / `remove` 只有用法提示，没有实现；能用的只有 `list`，而且必须写成 `banned <任意值> list`。
- **`number` 的校验细节**：`number.length` 用的是 Kotlin 字符串属性（长度），要求 4–10 位且全为数字；随后 `number.toLong()`，因此在长度上限 10 位时不会溢出 `Long`。
- **`set` / `recalc` 走异步**（`submitAsync`），数据落库与聊天提示都在异步线程；如果服务端有跨线程访问限制，恢复使用时需要留意。
- **相对路径写法**：源码中大量使用 `IDData(...)`、`DataManager.modifyUserIDData`、`CoolNumberTier`、`Manager`（`function.CoolNumberManager` 的别名 import）等，恢复注释后应能直接编译；若期间 `IDData` / `CoolNumberTier` 的构造签名有变动，需要同步修正。
