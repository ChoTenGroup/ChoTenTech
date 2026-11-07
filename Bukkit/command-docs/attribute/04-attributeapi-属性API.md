# AttributeAPI 属性 API 调试

> **根指令**：`/attributeapi`（别名：`/atbapi`）
> **权限**：`chotenattribute.command.api`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenAttributeApiCommand.kt`

## 功能概述

`/attributeapi` 是给脚本作者 / 服主使用的**开发调试入口**，不是给普通玩家的功能指令。它提供三类能力：

1. 打印当前根指令的完整指令帮助树（`main` / `help`）；
2. 把一段文本当作 **MiniMessage** 渲染后发回给自己（`minimessage`），用于即时预览颜色与渐变效果；
3. 执行一段 **Kether** 脚本并回显脚本内容与返回值（`eval` / `evalByPlayer`），或静默执行不回显（`evalSilent` / `evalByPlayerSilent`）。

所有执行体都包在 `submitAsync { ... }` 里异步运行。命令框架为 TabooLib 注解式指令，`newParser = false`，`permissionMessage = ""`（权限不足时不额外提示文本）。

> `@CommandHeader` 的 `description` 字面写的是 `"API Command of DepazItems."`，属于复制粘贴遗留（本指令实际服务于属性系统）。`@CommandBody` 中 `evalByPlayer` / `evalByPlayerSilent` 的属性名是驼峰形式，**实际 literal 是否为 `evalByPlayer` 还是会被框架规范化，源码未显式声明 `aliases`，请以 `/atbapi` 帮助树的实际输出为准**。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/attributeapi` | 无参数主体，打印指令帮助树 | `chotenattribute.command.api` |
| `/attributeapi help` | 打印指令帮助树（与主体相同） | `chotenattribute.command.api` |
| `/attributeapi minimessage <content>` | 把 `content` 按 MiniMessage 渲染后发送给执行者 | `chotenattribute.command.api` |
| `/attributeapi eval <script>` | 以执行者为 sender 执行 Kether 脚本，回显脚本与返回值 | `chotenattribute.command.api` |
| `/attributeapi evalByPlayer <player> <script>` | 以指定玩家为 sender 执行 Kether 脚本，回显脚本与返回值 | `chotenattribute.command.api` |
| `/attributeapi evalSilent <script>` | 以执行者为 sender 执行 Kether 脚本，不回显 | `chotenattribute.command.api` |
| `/attributeapi evalByPlayerSilent <player> <script>` | 以指定玩家为 sender 执行 Kether 脚本，不回显 | `chotenattribute.command.api` |

> 所有子命令均未单独声明 `permission`，统一由根 header 的 `chotenattribute.command.api`（默认 OP）约束；`execute` 内没有额外的 `sender.hasPermission(...)` 检查。

## 指令详解

### `/attributeapi`
- **用途**：打印本根指令的完整指令帮助树。
- **参数**：无。
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`mainCommand { createModernHelper() }`，输出各子命令与参数名（参数名取 `dynamic(...)` 的名字：`content`、`script`、`player`）。
- **示例**：`/atbapi`

### `/attributeapi help`
- **用途**：与无参主体相同，打印指令帮助树。
- **参数**：无。
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`subCommand { createModernHelper() }` —— 直接把帮助渲染函数作为该 literal 的执行体，因此不显示为"需要继续输入子参数"的分支。
- **示例**：`/atbapi help`

### `/attributeapi minimessage <content>`
- **用途**：即时预览 MiniMessage 文本（含渐变、颜色标签）在游戏内的渲染效果。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `content` | 要解析的 MiniMessage 字符串（`dynamic("content")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：在 `submitAsync` 异步任务中读取 `context["content"]`，调用 `sender.sendComponent(content)`。该扩展（`function/player/sendMessage.kt`）内部执行 `sendMessage(message.toComponent())`，把字符串按组件解析（MiniMessage）后发送给执行者本人。由于含空格的内容需要引号包裹（TabooLib `dynamic` 参数规则），多词文本请写成 `"..."` 形式。
- **示例**：`/atbapi minimessage "<gradient:#84D1CB:#EFDF8F>Hello</gradient>"`

### `/attributeapi eval <script>`
- **用途**：以**执行者**为脚本 sender 执行一段 Kether 脚本，并回显脚本内容与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：在 `submitAsync` 中调用 `runKether(listOf(content), sender)`（`function/kether/AttributeEvalKether.kt`，内部为 `KetherShell.eval(script, ScriptOptions(sender = adaptCommandSender(sender)))` 并返回 `CompletableFuture<Any>`），随后输出两行：
  1. `<#EFDF8F>Kether: <#A9B4C3>$content` —— 原样回显脚本；
  2. `<#EFDF8F>Return: <#A9B4C3>${ret.get()}` —— 脚本返回值（`CompletableFuture.get()` 阻塞取值）。
  执行者是控制台时，脚本 sender 亦为控制台适配器。
- **示例**：`/atbapi eval "tell Hello"`

### `/attributeapi evalByPlayer <player> <script>`
- **用途**：以**指定玩家**为脚本 sender 执行 Kether 脚本，并回显脚本与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 脚本运行上下文玩家（`player("player")`，TabooLib 玩家参数类型） | 必填 | 由 TabooLib `player(...)` 组件提供（不使用自定义 `suggestion`） |
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`context.player("player").castSafely<Player>() as CommandSender` 作为 sender（即脚本中的 `sender` 为该玩家本体，而非执行指令的人），调用 `runKether(listOf(content), bukkitPlayer)`，然后同样输出 `Kether: ...` 与 `Return: ...` 两行给**指令执行者**。
- **示例**：`/atbapi evalByPlayer Steve "tell Hello"`

### `/attributeapi evalSilent <script>`
- **用途**：以执行者为 sender 执行 Kether 脚本，**不回显**脚本与返回值（"静默"）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：在 `submitAsync` 中仅执行 `runKether(listOf(content), sender)` 并丢弃返回的 future（不 `get`、不打印任何回执）。脚本自身的副作用（发消息、改属性等）照常发生；指令本身对执行者无任何输出。
- **示例**：`/atbapi evalSilent "tell Hello"`

### `/attributeapi evalByPlayerSilent <player> <script>`
- **用途**：以指定玩家为 sender 执行 Kether 脚本，**不回显**任何内容。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 脚本运行上下文玩家（`player("player")`） | 必填 | 由 TabooLib `player(...)` 组件提供 |
  | `script` | Kether 脚本源码（`dynamic("script")`） | 必填 | 无补全，任意字符串 |
- **权限**：`chotenattribute.command.api`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：与 `evalByPlayer` 相同的 sender 处理（`castSafely<Player>() as CommandSender`），但只调用 `runKether(...)`，不打印 `Kether:` / `Return:` 行。
- **示例**：`/atbapi evalByPlayerSilent Steve "tell Hello"`

## 备注 / 注意事项

- **本文件不打印任何属性 ID 列表**。源码中没有任何 `attributeRegistry`、`ModifierType` 或属性 ID 输出；`content` / `script` 都是自由文本参数。属性 ID 的查询请使用 `/chotenattribute-manage add` 的 `attrId` 补全或 `/chotenattribute-debug show` / `categories`。
- **异步执行**：四个执行分支都在 `submitAsync { }` 中运行，因此 `generateMessage` / Bukkit 主线程 API（如 `tell`、修改方块）在 Kether 脚本中可能触发线程安全问题；`runKether` 本身不做线程切换。
- **`ret.get()` 阻塞**：`eval` / `evalByPlayer` 会在异步线程上阻塞等待 Kether 结果；若脚本永不结束（例如等待条件），该异步任务会长期挂起。
- **参数空格**：`content` 与 `script` 为 `dynamic` 参数，含空格时需引号包裹。
- **与 `/chotenapi` 的关系**：本指令与 `command/ChoTenApiCommand.kt`（`/chotenapi`）的指令体**逐行一致**（同样的 `main`、`help`、`minimessage`、`eval`、`evalByPlayer`、`evalSilent`、`evalByPlayerSilent` 七个 `@CommandBody`，同样的实现与提示颜色），仅根指令名、别名、权限不同。参见 `../api/01-chotenapi-物品API.md`。
- **`description` 字段**：源码写的是 `"API Command of DepazItems."`，与属性系统无关，属遗留文本，不影响功能。
