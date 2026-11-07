# 菜单诊断（/ulsmenu）

> **根指令**：`/ulsmenu`（别名：`/ulsm`）
> **权限**：`chotentech.uls.menu`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/client/menu/ULSMenuCommand.kt`

## 功能概述

ULS 菜单框架（「服务端说、客户端画」）的排障与演示入口。菜单出问题的现象往往只有两种——「界面没出来」或「按钮点了没反应」——但原因可能分布在通道注册、握手、能力下发、动作登记等不同层。
本指令把每一层判断单独暴露出来：`diagnose` 逐层定位单个玩家为什么用不上增强菜单，`demo` 打开一个示例菜单，`actions` 核对已登记的 actionId，`test` 逐个隔离单项能力。
面向管理员与模组调试，默认仅 OP。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/ulsmenu` | 菜单框架概览：服务端能力、已登记动作数、子命令清单 | `chotentech.uls.menu` |
| `/ulsmenu diagnose <player>` | 逐层诊断某玩家能否使用增强菜单 | `chotentech.uls.menu` |
| `/ulsmenu demo` | 为自己打开示例菜单（仅玩家） | `chotentech.uls.menu` |
| `/ulsmenu demo <player>` | 替其他玩家打开示例菜单 | `chotentech.uls.menu` **+ `chotentech.uls.admin`**（运行时校验） |
| `/ulsmenu actions` | 列出所有已登记的菜单动作 | `chotentech.uls.menu` |
| `/ulsmenu test` | 列出全部测试用例（不自动打开） | `chotentech.uls.menu` |
| `/ulsmenu test <name>` | 为自己打开指定测试用例（仅玩家） | `chotentech.uls.menu` |
| `/ulsmenu test <name> <player>` | 替其他玩家打开指定测试用例 | `chotentech.uls.menu` **+ `chotentech.uls.admin`**（运行时校验） |

> `demo <player>` 与 `test <name> <player>` 的 `chotentech.uls.admin` 是在 `execute` 内部用 `sender.hasPermission(...)` 运行时检查的（源码注释：TabooLib 的 `subCommand { }` 不支持 `permission =` 参数）。
> `test all` 是 `<name>` 的一个特殊取值，不是独立子命令。

## 指令详解

### `/ulsmenu`

- **用途**：查看菜单框架的总体状态，快速确认服务端到底声明了哪些能力、登记了多少动作。
- **参数**：无。
- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**（`execute<CommandSender>`）。
- **行为说明**：
  - 输出 `[ULS] 菜单框架状态:` 与「服务端已实现的能力:」段，逐项列出 `SERVER_CAPABILITIES`：`menu`、`ui.text`、`ui.shape`、`ui.button`、`ui.image`、`ui.grid`、`ui.tooltip`、`ui.progress`、`ui.scroll`。
  - 输出「已登记的动作: `<数量>` 个（`/ulsmenu actions` 查看）」，数量取自 `ULSMenuActionRegistry.ids().size`。
  - 输出命令速查：`/ulsmenu diagnose <玩家>` | `/ulsmenu demo <玩家>` | `/ulsmenu test [用例]` | `/ulsmenu actions`。
- **示例**：`/ulsm` → 打印能力清单与动作数量。

### `/ulsmenu diagnose <player>`

- **用途**：逐个判据判断某名玩家为什么用不了增强菜单。**输出顺序刻意与实际判定顺序一致**——第一个 ✘ 就是原因。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 在线玩家名（`dynamic("player")`，源码显式提供 `suggestion`） | 必填 | `Bukkit.getOnlinePlayers()` 的玩家名 |

- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**。
- **行为说明**：
  - `Bukkit.getPlayerExact(context["player"])` 为 null → 输出「找不到在线玩家 `<输入值>`」并返回。
  - 输出标题「诊断 `<玩家名>`:」，然后按顺序检查并**在第一个失败处中止**：
    1. `已连接模组并完成握手`（`ULSClientManager.isUsingMod(player)`）。失败时追加两行提示：服务端从未收到该玩家的握手；若确实装了模组，检查通道是否注册成功（启动日志里应有「ULS 客户端握手通道已注册」）。
    2. `已下发 menu 能力`（`ULSClientManager.clientSupports(player, "menu")`）。失败时追加：服务端没有声明菜单能力，玩家会走经典轨。
  - 前两步都通过后，输出「协议版本: `<protocolVersion>` | 模组版本: `<modVersion>`」。
  - 输出「组件能力:」段，对 `COMPONENT_CAPABILITIES` 逐项打印 ✔/✘：`ui.text`、`ui.shape`、`ui.button`、`ui.image`、`ui.grid`、`ui.tooltip`、`ui.progress`、`ui.scroll`（此项决定菜单能否走增强轨：菜单用到的每一项都必须可用）。
  - 最后输出「当前打开的菜单: `<menuId 或 （无）>`」（`ULSMenuManager.currentMenu(player)`）。
- **示例**：`/ulsmenu diagnose Steve` → 若 1. 显示 ✘，说明握手从未到达，应查通道注册日志而不是查菜单代码。

### `/ulsmenu demo`

- **用途**：为自己打开示例菜单，跑通端到端一条通路。
- **参数**：无。
- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**仅玩家**（`sender as? Player` 为 null 时提示「请由玩家执行，或使用 `/ulsmenu demo <玩家>`。」）。
- **行为说明**（`openDemo`）：
  - 每次都调用 `ULSDemoMenu.registerActions()` 重新登记四个动作（`greet` / `toggle` / `refresh` / `close` 一组；该方法是幂等的：先注销再注册）。源码注释说明这是刻意的——以前「探测 `demo.greet` 是否已登记」的做法会在部分残留时跳过注册，导致 `toggle` 按钮永远点不动。
  - 调用 `ULSDemoMenu.build(player)` 构建菜单，再 `ULSMenuManager.open(player, menu)` 打开，返回实际走的轨道。
  - 输出「已为 `<玩家名>` 打开示例菜单，使用 `<轨道>`。」，轨道文案为「增强轨（客户端原生界面）」或「经典轨（原版箱子界面）」。
  - 若走的是经典轨、但玩家**装了模组**（`isUsingMod` 为 true），追加提示「该玩家装了模组但走了经典轨，用 `/ulsmenu diagnose <玩家名>` 查原因。」
- **示例**：`/ulsmenu demo` → 打开示例菜单，并告知你走的是增强轨还是经典轨。

### `/ulsmenu demo <player>`

- **用途**：替另一名在线玩家打开示例菜单（例如玩家看不到界面时由管理员远程触发）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标在线玩家名 | 必填 | 在线玩家名 |

- **权限**：`chotentech.uls.menu` **且** `chotentech.uls.admin`。
- **执行者要求**：**玩家 / 控制台均可**（但必须持有 admin 权限）。
- **行为说明**：
  - 无 `chotentech.uls.admin` → 输出「你没有权限替其他玩家打开菜单。」。
  - 玩家不在线 → 输出「找不到在线玩家 `<输入值>`」。
  - 通过后与 `/ulsmenu demo` 完全相同的流程（重新登记 action、构建、打开、报告轨道，并对「装了模组却走经典轨」给出 `diagnose` 提示）。
- **示例**：`/ulsmenu demo Steve` → 为 Steve 打开示例菜单，并把轨道信息回报给你。

### `/ulsmenu actions`

- **用途**：列出所有已登记的菜单动作，核对 actionId 有没有写错（「按钮点了没反应」最常见的原因之一就是动作未登记）。
- **参数**：无。
- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**。
- **行为说明**：
  - 无任何动作 → 输出「尚未登记任何菜单动作。」。
  - 否则输出标题「已登记的菜单动作 (`数量`)」，按 id **排序**输出 `- <id> <description>`（`description` 为空则不追加）。
- **示例**：`/ulsmenu actions` → 确认 `demo.greet` 之类的 id 是否真的存在。

### `/ulsmenu test`

- **用途**：列出全部测试用例，**不自动打开任何菜单**（打开哪个用例本身就是判断前提，替玩家选会让结果无法归因）。
- **参数**：无。
- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**玩家 / 控制台均可**（控制台执行时输出「测试用例（请用 `/ulsmenu test <名称> <玩家>` 打开）:」；玩家执行时输出「测试用例（用 `/ulsmenu test <名称>` 打开）:」）。
- **行为说明**：
  - 逐条列出 `ULSTestMenus.CASES`，每个用例两行：`- <name> <title>` 与缩进的 `note`。
  - 末尾追加：「另有 `all` 表示依次说明全部用例的打开顺序。」
  - 用例清单（源码顺序，从简到繁）：`text`（① 文本与排版）、`buttons`（② 按钮与动作）、`conditions`（③ 条件显示与禁用）、`layout`（④ 布局：grid / 嵌套 / 间距）、`scroll`（⑤ 滚动与裁剪）、`progress`（⑥ 进度条）、`tooltip`（⑦ tooltip）。
- **示例**：`/ulsmenu test` → 打印七个用例名与其检查要点。

### `/ulsmenu test <name>`

- **用途**：为自己打开一个测试用例（隔离单项能力）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `name` | 用例短名，大小写不敏感 | 必填 | `text`、`buttons`、`conditions`、`layout`、`scroll`、`progress`、`tooltip`（`ULSTestMenus.namesForCommand()`）+ `all` |

- **权限**：`chotentech.uls.menu`（默认 OP）。
- **执行者要求**：**仅玩家**（控制台执行时提示「请由玩家执行，或使用 `/ulsmenu test <名称> <玩家>`。」）。
- **行为说明**（`openTestCase`）：
  - `name` 等于 `all`（忽略大小写）→ 只列出全部用例清单，并追加「按上面的顺序逐个打开即可（一次只开一个，看完关掉再开下一个）。」，**不打开任何菜单**。
  - `ULSTestMenus.byName(name)` 为 null → 输出「没有名为 `<name>` 的测试用例。」并**重新列出用例清单**。
  - 否则调用 `ULSTestMenus.open(player, case)`，其内部**先做模组门控**（`rejectionReason`）再打开：
    - 未装模组（`isUsingMod` 为 false）→ 拒绝：「你没有安装阈限天际客户端助手模组，无法使用增强菜单。这套测试菜单专供模组调试，未装模组时不提供降级界面。」
    - 握手完成但未下发 `menu` 能力 → 「握手已完成，但服务端没有下发 menu 能力。」
    - 菜单所需的某项能力缺失 → 「你的客户端缺少本用例需要的能力: `<缺失能力，用「、」连接>`。（用 `/ulsmenu diagnose <玩家名>` 看逐项清单）」。
    - 这是**硬门控、绝不降级**：测试菜单大量使用 grid / scroll / progress / 多行 tooltip，经典轨的箱子界面表达不出来，残缺界面会让人误以为模组装坏了。
    - 设计意图上刻意**先做门控再报「找不到用例」**，避免没装模组的玩家先去改用例名、白跑一轮。源码中的实际顺序为：本文件中先判断 `name` 是否为 `all`、再判断用例是否存在，随后 `ULSTestMenus.open` 内部**第一步**就是 `rejectionReason` 门控，门控通过后才 `registerActions()` 并打开菜单。
  - 被拒绝时输出「无法打开测试用例「`<title>`」:」+ 拒绝理由；若执行者不是目标玩家本人，再追加「该消息只发给你，玩家自己看不到。」
  - 打开成功时输出「已打开测试用例 `<title>`（菜单 ID `uls_test_<name>`）。」并追加该用例的 `note`。
  - 打开后再查一次 `ULSMenuManager.currentMenu(player)`；若为 null → 追加「菜单没有下发成功，检查启动日志里的编码告警。」（源码注释说明正常情况走不到这里，这是为将来绕过门控的调用留的兜底提示）。
  - 打开前会调用 `ULSTestMenus.registerActions()`（幂等）重新登记动作，避免「按钮点了没反应」与「框架坏了」难以区分。
- **示例**：`/ulsmenu test scroll` → 打开「⑤ 滚动与裁剪」用例，菜单 ID 为 `uls_test_scroll`。

### `/ulsmenu test <name> <player>`

- **用途**：替另一名在线玩家打开指定测试用例。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `name` | 用例短名（同上，含 `all`） | 必填 | 同上 |
  | `player` | 目标在线玩家名 | 必填 | 在线玩家名 |

- **权限**：`chotentech.uls.menu` **且** `chotentech.uls.admin`。
- **执行者要求**：**玩家 / 控制台均可**（但必须持有 admin 权限）。
- **行为说明**：
  - 无 `chotentech.uls.admin` → 「你没有权限替其他玩家打开菜单。」。
  - 玩家不在线 → 「找不到在线玩家 `<输入值>`」。
  - 通过后与 `/ulsmenu test <name>` 相同的 `openTestCase` 流程（含 `all` 只列清单、门控拒绝理由、成功后的菜单 ID 与 note、失败兜底提示）。
  - 门控判定针对**目标玩家**，但提示消息发给**执行者**。
- **示例**：`/ulsmenu test progress Steve` → 为 Steve 打开「⑥ 进度条」用例。

## 备注 / 注意事项

- **两份能力清单必须与 `ULSClientManager.currentCapabilities()` 保持一致**（源码反复强调「一个会撒谎的诊断工具比没有诊断工具更糟」）：
  - `SERVER_CAPABILITIES`（主命令展示）= `menu` + 8 项组件能力；`CAP_SCROLL`（`ui.scroll`）曾被漏掉，现已补齐。
  - `COMPONENT_CAPABILITIES`（`diagnose` 逐项展示）= `ui.text`、`ui.shape`、`ui.button`、`ui.image`、`ui.grid`、`ui.tooltip`、`ui.progress`、`ui.scroll`。**刻意不含** `ui.checkbox`、`ui.textfield`、`ui.slot`、`ui.chart`，因为这四项服务端根本没有下发（模组侧交互未实现），列出来会永远是 ✘、看起来像客户端不支持。**修改任何一处都必须同步另一处。**
- **权限结构特殊**：`chotentech.uls.admin` 是运行时校验，不受根指令的 `PermissionDefault.OP` 影响，需要权限插件显式授予；否则默认 OP 也会被拒绝（取决于服务端权限实现）。
- **玩家名匹配严格**：`diagnose`、`demo <player>`、`test <name> <player>` 都用 `getPlayerExact`，大小写敏感且只支持在线玩家。
- **控制台能做什么**：`/ulsmenu`、`diagnose`、`actions`、`test`（列清单）可以用；`demo`、`test <name>`（自己开）不行，会提示改用带 `<玩家>` 的形式——而带 `<玩家>` 的形式又要求 `chotentech.uls.admin`。
- **`demo` 与 `test` 的区别**：`demo` 是「一条通路走不走得通」，一个菜单一次点完；`test` 是「每一项能力各自正不正常」，把能力拆开逐个隔离成因。
- **轨道（rail）说明**：`ULSMenuManager.chooseRail` 的判定顺序为——未握手 → 经典轨；未下发 `menu` 能力 → 经典轨；菜单所需任一能力缺失 → 经典轨；否则增强轨。
- **相关指令**：确认「握手有没有到达、客户端版本是什么」用 `/ulsclient` 与 `/ulsclient info <玩家>`；本文件关注的是握手之后菜单层面的问题。
