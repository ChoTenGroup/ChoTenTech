# 档案管理（ChoTenTech Profile Admin）

> **根指令**：`/chotentech-profile`（别名：`/cttprofile`, `/cttp`）
> **权限**：`chotentech.command.profileadmin`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ProfileAdminCommand.kt`

## 功能概述

管理员用的**玩家存档（archived profile）管理指令**。玩家的档案被软删除后会进入"已存档"状态（数据库中 `archived = 1`），本指令提供两组操作：

1. `/cttp list <target>` —— 列出指定玩家所有已存档的档案（ID、名称、存档时间）。
2. `/cttp restore <target> <profileId>` —— 把某一已存档档案恢复为可用档案。

底层调用 `ProfileManager.getArchivedProfileInfos(uuid)` 与 `ProfileManager.restoreProfile(uuid, profileId)`。`@CommandHeader` 使用 `newParser = true`（现代解析器）。

无参执行时不报错，而是调用公共扩展函数 `createModernHelper()` 输出该指令的**用法树**（`Usage: /chotentech-profile ...`），并按执行者的权限过滤可见子指令、跳过 `hidden` 的字面量节点。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttp` | 输出指令用法树（无参主体） | `chotentech.command.profileadmin` |
| `/cttp list <target>` | 列出该玩家已存档的档案 | `chotentech.command.profileadmin` |
| `/cttp restore <target> <profileId>` | 恢复指定档案 | `chotentech.command.profileadmin` |

## 指令详解

### `/chotentech-profile`（无参主体）

- **用途**：打印用法树，帮助管理员查看本指令有哪些子指令。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | （无） | — | — | — |
- **权限**：`chotentech.command.profileadmin`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` 内部为 `execute<ProxyCommandSender>`）。
- **行为说明**：
  - 调用 `createModernHelper()`（定义于 `command/MainCommand.kt`）：以 `<gradient:#EFDF8F:#84D1CB>Usage: /<指令名>` 开头，递归列出子节点，`Literal` 显示字面量、`Dynamic` 显示其 `comment`（以 `@` 开头时按语言键取值）。
  - 默认参数 `checkPermissions = true`，会用 `sender.hasPermission(it.permission)` 过滤子节点；因此控制台/无权限者看到的条目会更少。
- **示例**：
  - `/cttp`
  - `/chotentech-profile`

### `/chotentech-profile list <target>`

- **用途**：列出指定玩家所有已存档（软删除）的档案。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 玩家（`player("target")`，必须为在线玩家） | 必填 | TabooLib 在线玩家列表 |
- **权限**：`chotentech.command.profileadmin`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - 通过 `context.player("target").castSafely<Player>()!!` 取目标玩家，读取其 `uniqueId`。
  - 若 `ProfileManager.getArchivedProfileInfos(uuid)` 为空，输出 `<#EFDF8F><玩家名> 没有已存档的档案` 并结束。
  - 否则先输出表头 `<#DFBD7C><玩家名> 的已存档档案 (N 个):`，再为每条档案输出一行：
    ` <#A9B4C3>#<profileId> <#F2F5FA><profileName> <#6E7A8C>(<时间>)`。
  - 时间格式为 `SimpleDateFormat("yyyy-MM-dd HH:mm")`；当 `archivedAt <= 0` 时显示 `未知`。
  - 未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/cttp list Steve`
  - `/cttprofile list Steve`

### `/chotentech-profile restore <target> <profileId>`

- **用途**：把指定玩家的某个已存档档案恢复为可用档案。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 玩家（`player("target")`，必须为在线玩家） | 必填 | TabooLib 在线玩家列表 |
  | `profileId` | 档案 ID 字符串（`dynamic("profileId")`，即 `/cttp list` 中 `#` 后的值） | 必填 | 无自动补全 |
- **权限**：`chotentech.command.profileadmin`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - 调用 `ProfileManager.restoreProfile(uuid, profileId)`；返回 `true` 时输出 `<#8FD4A6>已恢复 <玩家名> 的档案 #<profileId>`。
  - 返回 `false` 时输出 `<#E08A98>恢复失败，请检查档案ID是否正确或名称是否冲突`。
  - 失败的真实条件（见 `ProfileManager.restoreProfile`）：
    1. 数据库里找不到 `uuid + profile_id + archived = 1` 的记录（ID 不存在或该档案并非已存档状态）；
    2. 该档案的**名称**与该玩家现有可用档案的名称冲突。
  - 恢复成功会把记录的 `archived` 置 `0`、`archivedAt` 置 `0`，并 `refreshProfileInfos(uuid)` 刷新缓存。
  - 未做任何运行时 `sender.hasPermission(...)` 检查。
- **示例**：
  - `/cttp restore Steve 3`
  - `/cttp restore Steve main`

## 备注 / 注意事项

- **子指令/参数节点核对**：源码共 3 个 `@CommandBody`（`main`、`list`、`restore`）；参数节点为 `player("target")`（`list` 下 1 个）、`player("target")` + `dynamic("profileId")`（`restore` 下 2 个）。**没有任何 `literal(` 节点**，因此 `list`/`restore` 之外的写法都不存在。
- 未声明 `permissionMessage`，因此无权限时会显示 TabooLib 默认提示。
- `list` / `restore` 的 `target` 使用 `player(...)` 参数，**只能指定在线玩家**；离线玩家无法操作。
- 已存档档案有**数量上限**：`ProfileManager` 在归档时会调用 `purgeOldestArchived`，当已存档数量 > 10 时永久删除最旧的记录。因此 `/cttp list` 最多只会看到 10 条，早期档案已不可恢复。
- 本对象同时被挂在主指令下：`command/MainCommand.kt` 中有 `@CommandBody val profile = ProfileAdminCommand`，因此 `/ctt profile list <target>` 与 `/ctt profile restore <target> <profileId>` 也可用（此时还需通过 `/ctt` 的 `chotentech.command.main` 权限）。被嵌套时子指令权限仍取本对象的 `permission`。
- 注意与 `/profile`（`ProfileCommand`，打开档案选择 GUI）区分：`/cttp`、`/cttprofile` 是管理员文本指令，`/profile` 是玩家 GUI 入口。
