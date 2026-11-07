# ChoTenAPI 物品 / 通用 API 调试

> **根指令**：`/chotenapi`（别名：`/itemapi`, `/needyitemapi`, `/depazapi`）
> **权限**：`chotentech.command.api`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenApiCommand.kt`

## 功能概述

`/chotenapi` 是 ChoTenTech 的**通用 API 调试入口**（文件名与别名带 "item"，但源码实现中并没有任何物品相关的逻辑）。它提供三类能力：

1. 打印根指令的完整指令帮助树（`main` / `help`）；
2. 把一段文本按 **MiniMessage** 渲染后发回给自己（`minimessage`），用于即时预览颜色 / 渐变；
3. 执行一段 **Kether** 脚本并回显脚本内容与返回值（`eval` / `evalByPlayer`），或静默执行（`evalSilent` / `evalByPlayerSilent`）。

所有执行体都包在 `submitAsync { ... }` 里异步运行。命令框架为 TabooLib 注解式指令，`newParser = false`，`permissionMessage = ""`（权限不足时不额外提示文本）。

> `@CommandHeader` 的 `description` 字面写的是 `"API Command of DepazItems."`；别名中的 `itemapi` / `needyitemapi` / `depazapi` 也来自 DepazItems。但**本文件内不存在任何物品 ID、物品注册表或物品枚举的输出逻辑** —— 详见"备注"。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotenapi` | 无参数主体，打印指令帮助树 | `chotentech.command.api` |
| `/chotenapi help` | 打印指令帮助树（与主体相同） | `chotentech.command.api` |
| `/chotenapi minimessage <content>` | 把 `content` 按 MiniMessage 渲染后发送给执行者 | `chotentech.command.api` |
| `/chotenapi eval <script>` | 以执行者为 sender 执行 Kether 脚本，回显脚本与返回值 | `chotentech.command.api` |
| `/chotenapi evalByPlayer <player> <script>` | 以指定玩家为 sender 执行 Kether 脚本，回显脚本与返回值 | `chotentech.command.api` |
| `/chotenapi evalSilent <script>` | 以执行者为 sender 执行 Kether 脚本，不回显 | `chotentech.command.api` |
| `/chotenapi evalByPlayerSilent <player> <script>` | 以指定玩家为 sender 执行 Kether 脚本，不回显 | `chotentech.command.api` |

> 所有子命令均未单独声明 `permission`，统一由根 header 的 `chotentech.command.api`（默认 OP）约束；`execute` 内没有额外的 `sender.hasPermission(...)` 检查。注意根权限属于 `chotentech.*` 命名空间（不是 `chotenattribute.*`）。

## 指令详解

### `/chotenapi`
- **用途**：打印本根指令的完整指令帮助树。
- **参数**：无。
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`mainCommand { createModernHelper() }`。`createModernHelper`（`command/MainCommand.kt` 第 93 行）递归遍历 `context.commandCompound.children`，以树形（`├──` / `└──`）输出 literal 与 dynamic 参数名，用 MiniMessage 渐变着色；可选参数以 `[<...>]` 形式显示；默认 `checkPermissions = true`，会过滤掉执行者无权限的节点与 `hidden` 的 literal。
- **示例**：`/chotenapi`

### `/chotenapi help`
- **用途**：与无参主体相同，打印指令帮助树。
- **参数**：无。
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`subCommand { createModernHelper() }` —— 直接把帮助渲染函数作为该 literal 的执行体。
- **示例**：`/chotenapi help`

### `/chotenapi minimessage <content>`
- **用途**：即时预览 MiniMessage 文本（渐变、颜色标签等）在游戏内的渲染效果。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `content` | 要解析的 MiniMessage 字符串（`dynamic("content")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：在 `submitAsync` 异步任务中读取 `context["content"]`，调用 `sender.sendComponent(content)`（`player/sendMessage.kt` 扩展：`sendMessage(message.toComponent())`，按 MiniMessage 解析为组件后发给执行者本人）。含空格的内容需用引号包裹。
- **示例**：`/chotenapi minimessage "<gradient:#84D1CB:#EFDF8F>Hello</gradient>"`

### `/chotenapi eval <script>`
- **用途**：以**执行者**为脚本 sender 执行一段 Kether 脚本，并回显脚本内容与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：在 `submitAsync` 中调用 `runKether(listOf(content), sender)` —— 实现见 `function/kether/AttributeEvalKether.kt`：`KetherShell.eval(script, ScriptOptions(sender = adaptCommandSender(sender)))`，返回 `CompletableFuture<Any>`。随后向执行者输出两行：
  1. `<#EFDF8F>Kether: <#A9B4C3>$content` —— 原样回显脚本；
  2. `<#EFDF8F>Return: <#A9B4C3>${ret.get()}` —— 返回值（异步线程上 `get()` 阻塞取值）。
- **示例**：`/chotenapi eval "tell Hello"`

### `/chotenapi evalByPlayer <player> <script>`
- **用途**：以**指定玩家**为脚本 sender 执行 Kether 脚本，并回显脚本与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 脚本运行上下文玩家（`player("player")`，TabooLib 玩家参数类型） | 必填 | 由 TabooLib `player(...)` 组件提供（不使用自定义 `suggestion`） |
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`context.player("player").castSafely<Player>() as CommandSender` 作为脚本 sender（脚本里的 `sender` 是该玩家，而非执行指令的人），调用 `runKether(listOf(content), bukkitPlayer)`；随后把 `Kether: ...` 与 `Return: ...` 两行发给**指令执行者**。
- **示例**：`/chotenapi evalByPlayer Steve "tell Hello"`

### `/chotenapi evalSilent <script>`
- **用途**：以执行者为 sender 执行 Kether 脚本，**不回显**任何内容。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：在 `submitAsync` 中仅执行 `runKether(listOf(content), sender)`，丢弃返回的 future，不打印任何回执；脚本副作用照常发生。
- **示例**：`/chotenapi evalSilent "tell Hello"`

### `/chotenapi evalByPlayerSilent <player> <script>`
- **用途**：以指定玩家为 sender 执行 Kether 脚本，**不回显**任何内容。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 脚本运行上下文玩家（`player("player")`） | 必填 | 由 TabooLib `player(...)` 组件提供 |
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotentech.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：与 `evalByPlayer` 相同的 sender 处理（`castSafely<Player>() as CommandSender`），但仅调用 `runKether(...)`，不输出 `Kether:` / `Return:` 行。
- **示例**：`/chotenapi evalByPlayerSilent Steve "tell Hello"`

## 备注 / 注意事项

- **"物品 API" 名不副实**：尽管文件名为 `ChoTenApiCommand.kt`、别名为 `itemapi` / `needyitemapi` / `depazapi`、`description = "API Command of DepazItems."`，但源码中**没有**任何物品 ID / 物品注册表 / 物品枚举的输出逻辑。全文件只有上述 7 个 `@CommandBody`，参数只有 `content`、`script`、`player`。如果需要物品 ID 列表，请查阅项目内其它数据源（例如 `resources/` 下的物品定义文件），不要期待本指令。
- **枚举 / ID 清单**：本文件不涉及任何枚举，`ModifierType`、`ModifierSource`、`AttributeSlot` 等均未被引用。
- **与 `/attributeapi` 的关系**：`command/ChoTenAttributeApiCommand.kt`（`/attributeapi`，别名 `/atbapi`）与本文件的实现**逐行一致**（同样的 7 个子命令、同样的提示颜色 `<#EFDF8F>` / `<#A9B4C3>`、同样的 `submitAsync` + `runKether`），仅根指令名、别名、权限（`chotenattribute.command.api`）不同。参见 `../attribute/04-attributeapi-属性API.md`。
- **未挂载到 `/chotentech`**：在 `command/MainCommand.kt` 的 `@CommandBody` 列表中没有引用 `ChoTenApiCommand`，因此本指令只以独立根指令 `/chotenapi`（及四个别名）形式被注解注册。
- **异步执行**：四个执行分支都在 `submitAsync { }` 中运行；Kether 脚本内调用 Bukkit 主线程 API 时需自行注意线程安全，`runKether` 不做线程切换。
- **`ret.get()` 阻塞**：`eval` / `evalByPlayer` 会在异步线程阻塞等待 Kether 结果；脚本不结束时该异步任务会长期挂起。
- **参数空格**：`content` 与 `script` 是 `dynamic` 参数，含空格时必须用引号包裹。
