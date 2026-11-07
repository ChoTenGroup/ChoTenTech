# 元数据指令（chotentech-meta）

> **根指令**：`/chotentech-meta`（别名：`/cttmeta`）
> **权限**：`chotentech.admin`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/MetaCommand.kt`

## 功能概述

`chotentech-meta` 用于读写 `MetaManager` 维护的**标记（meta）系统**。meta 分两类，各有自己的 set / unset / list 三件套：

| 类别 | 含义 | 子指令 |
|---|---|---|
| **Shared**（跨 Profile） | 跟随账号本身，跨 Profile 共享的标记 | `sharedset` / `sharedunset` / `sharedlist` |
| **Independent**（当前 Profile） | 只作用于当前 Profile 的标记 | `independentset` / `independentunset` / `independentlist` |

```kotlin
@CommandHeader(
    name = "chotentech-meta",
    aliases = ["cttmeta"],
    permission = "chotentech.admin",
    description = "Meta标记系统",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
```

本指令的 `@CommandHeader` **没有** `permissionMessage`（其它根指令多为 `permissionMessage = ""`），权限节点也不是 `chotentech.command.*` 系列，而是 `chotentech.admin`。

每个标记条目可携带**过期时间**：set 时额外给 `seconds` 即为限时标记，否则为永久标记；list 时会显示「剩余 Ns」或「(永久)」。

> 子指令字面量取自 `@CommandBody` 的 Kotlin 属性名（本文件未使用 `literal("...")`），且写法是**连写的全小写**：`sharedset` / `sharedunset` / `sharedlist` / `independentset` / `independentunset` / `independentlist`（不是 `shared set`）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttmeta` | 不带参数：打印指令用法树（`createModernHelper()`） | `chotentech.admin` |
| `/cttmeta sharedset <key> <value>` | 给自己设置**永久** shared meta | `chotentech.admin` |
| `/cttmeta sharedset <key> <value> <seconds>` | 给自己设置**限时** shared meta | `chotentech.admin` |
| `/cttmeta sharedset <key> <value> <player>` | 为指定玩家设置**永久** shared meta | `chotentech.admin` |
| `/cttmeta sharedset <key> <value> <player> <seconds>` | 为指定玩家设置**限时** shared meta | `chotentech.admin` |
| `/cttmeta sharedunset <key>` | 移除自己的 shared meta | `chotentech.admin` |
| `/cttmeta sharedunset <key> <player>` | 移除指定玩家的 shared meta | `chotentech.admin` |
| `/cttmeta sharedlist` | 列出自己的全部 shared meta | `chotentech.admin` |
| `/cttmeta sharedlist <player>` | 列出指定玩家的全部 shared meta | `chotentech.admin` |
| `/cttmeta independentset <key> <value>` | 给自己设置**永久** independent meta | `chotentech.admin` |
| `/cttmeta independentset <key> <value> <seconds>` | 给自己设置**限时** independent meta | `chotentech.admin` |
| `/cttmeta independentset <key> <value> <player>` | 为指定玩家设置**永久** independent meta | `chotentech.admin` |
| `/cttmeta independentset <key> <value> <player> <seconds>` | 为指定玩家设置**限时** independent meta | `chotentech.admin` |
| `/cttmeta independentunset <key>` | 移除自己的 independent meta | `chotentech.admin` |
| `/cttmeta independentunset <key> <player>` | 移除指定玩家的 independent meta | `chotentech.admin` |
| `/cttmeta independentlist` | 列出自己的全部 independent meta | `chotentech.admin` |
| `/cttmeta independentlist <player>` | 列出指定玩家的全部 independent meta | `chotentech.admin` |

> 共 1 个 `main` + 6 个 `subCommand` 属性（每个属性展开 1~4 条路径）。所有子指令都**未单独声明** `permission`，统一继承根权限 `chotentech.admin`。

## 指令详解

### `/cttmeta`
- **用途**：打印指令用法树。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | —— | 无参数 | —— | —— |
- **权限**：`chotentech.admin`
- **执行者要求**：控制台均可（`mainCommand { createModernHelper() }`）。
- **行为说明**：本根指令**没有** `help` 子指令。
- **示例**：`/cttmeta`

---

### `/cttmeta sharedset <key> <value>` / `… <seconds>` / `… <player>` / `… <player> <seconds>`
- **用途**：设置一条 shared（跨 Profile）meta。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 标记键（`dynamic`） | 必填 | 无 |
  | `value` | 标记值（`dynamic`） | 必填 | 无 |
  | `seconds` | 过期时长（`int`，单位**秒**）；不写则永久 | 可选 | 补全：`60`、`300`、`3600`、`86400` |
  | `player` | 目标玩家（`player`）；不写则作用于执行者自己 | 可选 | 在线玩家名 |
- **权限**：`chotentech.admin`
- **执行者要求**：控制台均可——但**控制台必须使用 `player` 分支**，否则命中「给自己设置」分支时会收到 `<#E08A98>控制台必须指定玩家`。
- **行为说明**：
  - 自己分支：`uuid = (sender as? Player)?.uniqueId?.toString()`；为 null 时输出「控制台必须指定玩家」并中止。
  - 目标玩家分支：`context.player("player").castSafely<Player>()!!`，取其 `uniqueId.toString()`。
  - 无 `seconds` → `MetaManager.setShared(uuid, key, value, null)`，提示 `<#8FD4A6>已设置 shared meta {key} = {value}` / `<#8FD4A6>已为 {name} 设置 shared meta {key} = {value}`。
  - 有 `seconds` → 先校验 `seconds = toLongOrNull()` 且 `>= 1`（否则 `<#E08A98>时长必须为正整数（秒）`），再 `MetaManager.setShared(uuid, key, value, System.currentTimeMillis() + seconds * 1000L)`，提示中追加「（{seconds}秒后过期）」。
  - 源码结构为 `dynamic("key") { dynamic("value") { execute(自己); int("seconds"); player("player") { execute; int("seconds") } } }`。
- **示例**：
  - `/cttmeta sharedset vip true`
  - `/cttmeta sharedset vip true 3600`
  - `/cttmeta sharedset vip true Steve`
  - `/cttmeta sharedset vip true Steve 86400`

---

### `/cttmeta sharedunset <key>` / `/cttmeta sharedunset <key> <player>`
- **用途**：移除一条 shared meta。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `key` | 标记键（`dynamic`） | 必填 | 无 |
  | `player` | 目标玩家（`player`） | 可选 | 在线玩家名 |
- **权限**：`chotentech.admin`
- **执行者要求**：省略 `player` 时**必须是玩家**（控制台会收到「控制台必须指定玩家」）；带 `player` 时控制台亦可。
- **行为说明**：调用 `MetaManager.removeShared(uuid, key)`；返回 `true` → `<#8FD4A6>已移除 shared meta {key}`（指定玩家时为「已移除 {name} 的 shared meta {key}」）；返回 `false` → `<#EFDF8F>shared meta {key} 不存在`（指定玩家时为「{name} 的 shared meta {key} 不存在」）。
- **示例**：
  - `/cttmeta sharedunset vip`
  - `/cttmeta sharedunset vip Steve`

---

### `/cttmeta sharedlist` / `/cttmeta sharedlist <player>`
- **用途**：列出 shared meta 条目。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家（`player`） | 可选 | 在线玩家名 |
- **权限**：`chotentech.admin`
- **执行者要求**：省略 `player` 时**仅玩家**（`execute<Player>`，控制台被框架拒绝）；带 `player` 时控制台亦可。
- **行为说明**：`MetaManager.listShared(uuid)`：
  - 空表 → `<#EFDF8F>暂无 shared meta`（指定玩家时为「{name} 暂无 shared meta」）。
  - 非空 → 先发标题 `<#DFBD7C>===== Shared Meta ({size}) =====`（指定玩家时为 `===== {name} Shared Meta ({size}) =====`），再逐条发 `<#F2F5FA>{key} <#A9B4C3>= <#F2F5FA>{value} <#A9B4C3>(剩余 {remaining}s)` 或 `<#8FD4A6>(永久)`。
  - 剩余时间按 `(entry.expireAt - now) / 1000` 秒向下取整显示。
- **示例**：
  - `/cttmeta sharedlist`
  - `/cttmeta sharedlist Steve`

---

### `/cttmeta independentset <key> <value>` / `… <seconds>` / `… <player>` / `… <player> <seconds>`
- **用途**：设置一条 independent（当前 Profile）meta。
- **参数**：与 `sharedset` 完全一致（`key`、`value`、可选 `seconds`、可选 `player`）。
- **权限**：`chotentech.admin`
- **执行者要求**：同 `sharedset`（控制台必须用 `player` 分支）。
- **行为说明**：与 `sharedset` 同样的结构与校验，只是调用 `MetaManager.setIndependent(...)`，提示文案为 `<#8FD4A6>已设置 independent meta {key} = {value}` / `<#8FD4A6>已为 {name} 设置 independent meta {key} = {value}`，限时分支追加「（{seconds}秒后过期）」。
- **示例**：
  - `/cttmeta independentset tutorial_done true`
  - `/cttmeta independentset tutorial_done true 300 Steve`

---

### `/cttmeta independentunset <key>` / `/cttmeta independentunset <key> <player>`
- **用途**：移除一条 independent meta。
- **参数**：同 `sharedunset`（`key` 必填，`player` 可选）。
- **权限**：`chotentech.admin`
- **执行者要求**：省略 `player` 时必须是玩家；带 `player` 时控制台亦可。
- **行为说明**：`MetaManager.removeIndependent(uuid, key)`；成功 `<#8FD4A6>已移除 independent meta {key}`，失败 `<#EFDF8F>independent meta {key} 不存在`（指定玩家时消息中带玩家名）。
- **示例**：`/cttmeta independentunset tutorial_done`

---

### `/cttmeta independentlist` / `/cttmeta independentlist <player>`
- **用途**：列出 independent meta 条目。
- **参数**：`player` 可选。
- **权限**：`chotentech.admin`
- **执行者要求**：省略 `player` 时仅玩家；带 `player` 时控制台亦可。
- **行为说明**：`MetaManager.listIndependent(uuid)`；空表提示 `暂无 independent meta` / `{name} 暂无 independent meta`；非空时标题为 `<#DFBD7C>===== Independent Meta ({size}) =====`，条目格式与 `sharedlist` 相同。
- **示例**：`/cttmeta independentlist Steve`

## 备注 / 注意事项

1. **权限节点不是 `chotentech.command.*`**：本指令使用 `chotentech.admin`（默认 OP），与 `chotentech.command.data` / `chotentech.command.debug` 等不同；授权时别找错节点。
2. **缺少 `permissionMessage`**：`@CommandHeader` 中没有声明该项（其它根指令通常显式设为 `""`），无权限时会走框架默认提示。
3. **`set` 的第三段有歧义**：`dynamic("value")` 下面同时挂了 `int("seconds")` 与 `player("player")` 两个**同级**分支。也就是说 `/cttmeta sharedset vip true 3600` 与 `/cttmeta sharedset vip true Steve` 在同一位置竞争：数字会被优先当作 `seconds`（按框架的组件匹配顺序），想给玩家设置请写 `<player>`，必要时带上 `seconds` 以消歧。实际行为以游戏内 Tab 补全与实测为准。
4. **`seconds` 必须是正整数**：`toLongOrNull()` 为 null 或 `< 1` 时统一提示「时长必须为正整数（秒）」，因此不能写 `0` 或 `-1` 表示永久 —— 想永久就**省略** `seconds`。
5. **控制台限制**：`sharedset` / `independentset` 的自我分支用 `(sender as? Player)` 判空并提示「控制台必须指定玩家」；`sharedlist` / `independentlist` 的自我分支是 `execute<Player>`（框架直接拒绝控制台，无自定义提示）；`sharedunset` / `independentunset` 的自我分支同 set，会提示「控制台必须指定玩家」。
6. **UUID 语义差异**：`shared*` 走 `ProfileManager.getSharedData(uuid)`（跨 Profile 共享，即使玩家未加载也能设置）；`independent*` 直接读写 `playerDataMap[uuid]?.meta`，**玩家数据未加载时会静默失败**——`setIndependent` 直接 `return`（不报错、指令仍会打印「已设置」），`removeIndependent` 返回 `false`（于是会提示「不存在」）。给离线玩家设置 independent meta 前请确认其数据已加载（可用 `/chotentech-debug loadData` 或先让其上线）。
7. **过期条目会被自动清理**：`MetaManager.listShared` / `listIndependent` 进入时会先 `cleanupShared` / `cleanupIndependent` 删除所有 `isExpired` 的条目，再 `filterValues { !it.isExpired }` 返回，因此 list 里**看不到**已过期的标记；`set*` 与 `remove*` 同样会先清理。所以 list 中显示「剩余 Ns」的条目都仍然有效。
8. **`newParser = true`**：启用了新版解析器。
9. **子指令名是连写小写**：`sharedset`、`independentunset` 等不是 `shared set` 两段；玩家文档/脚本里请按单个 token 书写。
