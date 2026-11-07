# 通用 API 指令（quetiapineapi）

> **根指令**：`/quetiapineapi`（别名：`/rpgapi`, `/chotentechapi`, `/cttapi`）
> **权限**：`quetiapine.command.api`（默认：**无**，`PermissionDefault.FALSE`，必须显式授权）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/apiSubCommand.kt`

## 功能概述

`quetiapineapi` 是面向运维/开发调试的**通用 API 入口**：解析 MiniMessage 文本，以及以「任意执行者身份」执行 Kether 脚本并回显返回值。

```kotlin
@CommandHeader(
    name = "quetiapineapi",
    aliases = ["rpgapi", "chotentechapi", "cttapi"],
    permission = "quetiapine.command.api",
    permissionMessage = "",
    permissionDefault = PermissionDefault.FALSE,
    newParser = true,
)
```

源码中的安全注释明确说明：

> `eval*` 子指令本质是「以任意 sender 身份执行任意 Kether 脚本」，等价于 RCE 控制台。`PermissionDefault.OP` 意味着所有 OP（含被误提权的管理/半信任账号）默认可用；改为 OP 之外显式授权（默认拒绝），只有真正需要运维调试的账号才应被授予 `quetiapine.command.api`。

因此本根指令的 `permissionDefault` 是 `PermissionDefault.FALSE` —— **默认谁都没有权限**，必须用权限插件显式授予。

另有脚本长度上限：

```kotlin
private const val MAX_SCRIPT_LEN = 8192
```

`eval*` 共用入口 `evalScript(sender, content, scriptTarget)`：脚本长度 > 8192 时直接拒绝并提示 `<#E08A98>脚本过长 (${content.length} > 8192)，已拒绝执行。`，否则调用 `kether.runKether(listOf(content), scriptTarget ?: sender).get()`。

> 子指令字面量取自 `@CommandBody` 的 Kotlin 属性名（本文件未使用 `literal("...")`）：`help` / `minimessage` / `eval` / `evalByPlayer` / `evalSilent` / `evalByPlayerSilent`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/quetiapineapi` | 不带参数：打印指令用法树（`createModernHelper()`） | `quetiapine.command.api` |
| `/quetiapineapi help` | 打印指令用法树 | `quetiapine.command.api` |
| `/quetiapineapi minimessage <content>` | 以 MiniMessage 解析并回显一段文本 | `quetiapine.command.api` |
| `/quetiapineapi eval <script>` | 以执行者身份执行 Kether 脚本，并回显脚本与返回值 | `quetiapine.command.api` |
| `/quetiapineapi evalByPlayer <player> <script>` | 以指定玩家身份执行 Kether 脚本，并回显脚本与返回值 | `quetiapine.command.api` |
| `/quetiapineapi evalSilent <script>` | 以执行者身份执行 Kether 脚本，**不回显** | `quetiapine.command.api` |
| `/quetiapineapi evalByPlayerSilent <player> <script>` | 以指定玩家身份执行 Kether 脚本，**不回显** | `quetiapine.command.api` |

> 所有子指令都**未单独声明** `permission`，统一继承根权限 `quetiapine.command.api`；且由于根权限默认值为 `FALSE`，未显式授权的账号（包括 OP）都无法使用任何一个子指令。

## 指令详解

### `/quetiapineapi` 与 `/quetiapineapi help`
- **用途**：打印本指令的用法树。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | —— | 无参数 | —— | —— |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可（`createModernHelper()` 内部为 `execute<ProxyCommandSender>`）。
- **行为说明**：`main`（`mainCommand`）与 `help`（`subCommand`）均只调用 `createModernHelper()`。
- **示例**：`/quetiapineapi`

---

### `/quetiapineapi minimessage <content>`
- **用途**：把一段 MiniMessage 文本解析后回显，用于验证颜色/格式标签是否正确。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `content` | 待解析的文本（`dynamic`，单个参数） | 必填 | 无 |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可（`execute<CommandSender>`）。
- **行为说明**：`submitAsync` 中直接 `sender.infoS(context["content"])`，由 `infoS` 走的组件链路负责 MiniMessage 解析，因此可用来确认 `<gradient:...>`、`<#RRGGBB>` 等标签在服务端是否生效。
- **示例**：`/quetiapineapi minimessage <gradient:#EFDF8F:#84D1CB>测试文本`

---

### `/quetiapineapi eval <script>`
- **用途**：以**执行者**（控制台则为自己）身份同步执行一段 Kether 脚本，并回显脚本内容与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本（`dynamic`，单个参数） | 必填 | 无 |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可。`evalScript(sender, content, null)` 的第三个参数为 `null`，即脚本以 `sender` 自身为目标执行者。
- **行为说明**：`submitAsync` 中执行脚本；若返回值非 null 或脚本未超长，则依次输出 `<#EFDF8F>Kether: <#A9B4C3>{script}` 与 `<#EFDF8F>Return: <#A9B4C3>{ret}`。脚本超过 8192 字符时由 `evalScript` 拒绝，此时只输出「脚本过长」提示，不再输出 Kether/Return 两行。
- **示例**：`/quetiapineapi eval <script>`（脚本须作为**一个**命令行参数传入；含空格的脚本请先按服务端/框架的引号规则处理，实测确认后再用于生产）

---

### `/quetiapineapi evalByPlayer <player> <script>`
- **用途**：以**指定玩家**身份执行 Kether 脚本，并回显脚本内容与返回值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家（`player`，脚本的执行者） | 必填 | 在线玩家名 |
  | `script` | Kether 脚本（`dynamic`） | 必填 | 无 |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可（`execute<CommandSender>`，仅 `player` 参数需要是真实在线玩家）。
- **行为说明**：`context.player("player").castSafely<Player>() as CommandSender` 作为 `scriptTarget` 传入 `evalScript`，实现「以该玩家身份运行脚本」。回显格式与 `eval` 相同（`Kether:` + `Return:` 两行）。
- **示例**：`/quetiapineapi evalByPlayer Steve <script>`

---

### `/quetiapineapi evalSilent <script>`
- **用途**：以执行者身份执行 Kether 脚本，**不输出任何回显**。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `script` | Kether 脚本（`dynamic`） | 必填 | 无 |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可。
- **行为说明**：仅调用 `evalScript(sender, content, null)`，丢弃返回值；只有「脚本过长」这一种情况会向执行者输出提示。
- **示例**：`/quetiapineapi evalSilent <script>`

---

### `/quetiapineapi evalByPlayerSilent <player> <script>`
- **用途**：以指定玩家身份执行 Kether 脚本，**不输出任何回显**。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家（脚本执行者） | 必填 | 在线玩家名 |
  | `script` | Kether 脚本（`dynamic`） | 必填 | 无 |
- **权限**：`quetiapine.command.api`
- **执行者要求**：控制台均可。
- **行为说明**：与 `evalByPlayer` 相同地构造 `scriptTarget`，但丢弃返回值；同样只有超长时会提示。
- **示例**：`/quetiapineapi evalByPlayerSilent Steve <script>`

## 备注 / 注意事项

1. **默认无权限（安全加固）**：`permissionDefault = PermissionDefault.FALSE`。这是本指令与其它根指令最显著的区别 —— **OP 也默认不可用**，必须通过权限插件授予 `quetiapine.command.api`。源码注释说明了原因：`eval*` 等价于「以任意身份执行任意脚本」的 RCE 入口。
2. **危险面**：`eval` / `evalByPlayer` / `evalSilent` / `evalByPlayerSilent` 都会执行**任意 Kether 脚本**，`evalByPlayer*` 还能指定执行者身份。只应授予可信运维账号。
3. **脚本长度上限 8192**：`MAX_SCRIPT_LEN = 8192`。超长脚本会被拒绝（避免数 MB 拼接字符串拖垮 `submitAsync` 线程池并挤占内存），提示文案中包含实际长度与上限。
4. **脚本是单个 `dynamic` 参数**：源码没有使用 `lastArgs` 之类的方式吞掉剩余参数，因此一段脚本必须作为**一个**命令行 token 传入；含空格的脚本需要依赖服务端/框架的引号处理规则，实际使用前建议先在测试服验证。
5. **异步执行**：五个子指令的 `execute` 体都包在 `submitAsync` 中，Kether 在异步线程池解释执行，返回值通过 `.get()` 阻塞获取。
6. **`informative` 与 `silent` 的差别仅在回显**：`evalSilent` / `evalByPlayerSilent` 一样会真正执行脚本，只是不打印 `Kether:` / `Return:` 两行。
7. **`newParser = true`**：启用新版解析器。
8. **`minimessage` 不做长度限制**：只有 `evalScript`（即 `eval*` 系列）受 8192 限制，`minimessage` 无此检查。
9. **与 `chotenapi` 的区别**：`chotenapi`（别名 `itemapi` 等）是物品相关 API；本指令是通用文本/Kether 执行接口，二者权限节点不同（`chotentech.command.api` vs `quetiapine.command.api`）。
