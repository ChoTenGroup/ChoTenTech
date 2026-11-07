# 好友指令（Friend）

> **根指令**：`/friend`（别名：`/haoyou`, `/fy`）
> **权限**：`chotentech.command.friend`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/FriendCommand.kt`

## 功能概述

玩家好友系统指令，逻辑委派给 `FriendManager`，界面入口为 `FriendMenu` 与 `FriendRequestMenu`。

- **好友关系持久化**：存于 `ProfileManager` 的 SharedData（`friends` 列表，跨 profile 共享，落数据库），双向写入（A 的名单加 B，B 的名单加 A）。
- **好友申请是内存态**：`FriendManager.requests`（`ConcurrentHashMap<UUID, MutableSet<FriendRequest>>`），无过期时间，服务器重启即清空。
- **好友上限**：配置 `friend.max-friends`（默认 `50`），可被权限节点 `chotentech.friend.<n>`（`n` 从 200 递减到 1，取第一个命中的值）覆盖。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/friend` | 打印指令用法帮助树（`createModernHelper()`） | `chotentech.command.friend` |
| `/friend gui` | 打开好友列表界面 | `chotentech.command.friend` |
| `/friend requests` | 打开好友申请处理界面 | `chotentech.command.friend` |
| `/friend add <target>` | 向在线玩家发送好友申请 | `chotentech.command.friend` |
| `/friend accept <target>` | 同意来自 `<target>` 的好友申请 | `chotentech.command.friend` |
| `/friend deny <target>` | 拒绝来自 `<target>` 的好友申请 | `chotentech.command.friend` |
| `/friend remove <name>` | 删除好友（支持离线玩家名） | `chotentech.command.friend` |

> 6 个 `@CommandBody` 子命令 + 1 个默认主体（`main`）。源码中 `player(` 出现 3 次（`add`、`accept`、`deny` 的 `player("target")`），`dynamic(` 出现 1 次（`remove` 的 `dynamic("name")`），无 `literal(`。

## 指令详解

### `/friend`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.friend`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/friend`

---

### `/friend gui`

- **用途**：打开好友列表界面。
- **参数**：无
- **权限**：`chotentech.command.friend`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：`FriendMenu().open(sender)`，每次执行新建菜单实例。
- **示例**：`/friend gui`

---

### `/friend requests`

- **用途**：打开好友申请处理界面（用于查看/处理收到的申请）。
- **参数**：无
- **权限**：`chotentech.command.friend`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：`FriendRequestMenu().open(sender)`。
- **示例**：`/friend requests`

---

### `/friend add <target>`

- **用途**：向在线玩家发送好友申请。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 被申请的玩家（`player("target")` 玩家类型参数） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.friend`
- **执行者要求**：**玩家**（`execute<CommandSender>`，但体内 `sender as? Player ?: return@execute`，控制台执行时静默无反应）
- **行为说明**：
  1. 目标是自己 → `不能添加自己为好友`。
  2. `FriendManager.sendRequest(requester, target)` 在以下任一情况下返回 `false`：目标已是好友、申请者好友数已达上限、对该目标的申请已存在。
  3. 成功 → 申请者收到 `已向 <target> 发送好友申请`；目标收到 `<申请者> 向你发送了好友申请，输入 /friend requests 处理`。
  4. 失败 → `无法发送申请：对方已是好友、申请已存在或你的好友数量已达上限`。
  5. 目标解析失败（`castSafely<Player>()` 为 `null`）时直接静默返回，无提示。
- **示例**：`/friend add Steve`

---

### `/friend accept <target>`

- **用途**：同意来自指定玩家的好友申请。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | **申请人**（向自己发过申请的玩家，`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.friend`
- **执行者要求**：**玩家**（体内 `sender as? Player ?: return@execute`）
- **行为说明**：
  1. 申请人解析失败 → `<#E08A98>该玩家不在线`（申请人必须在线）。
  2. `FriendManager.accept(自己, 申请人uniqueId)`：要求存在来自该申请人的待处理申请，且**双方**好友数量都未达上限；成功后从申请列表移除，并**双向**写入 SharedData 的好友列表并保存。
  3. 成功 → 自己收到 `已同意 <申请人> 的好友申请`；申请人收到 `✦ <自己> 同意了你的好友申请！`。
  4. 失败 → `无法添加该好友（好友数量已达上限或申请不存在）`。
- **示例**：`/friend accept Alex`

---

### `/friend deny <target>`

- **用途**：拒绝来自指定玩家的好友申请。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | **申请人**（`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.friend`
- **执行者要求**：**玩家**（体内 `sender as? Player ?: return@execute`）
- **行为说明**：
  1. 申请人解析失败 → `该玩家不在线`。
  2. `FriendManager.deny(自己, 申请人uniqueId)` 成功 → 自己收到 `已拒绝 <申请人> 的好友申请`；申请人收到 `✦ <自己> 拒绝了你的好友申请`。
  3. 失败 → `该申请不存在`。
- **示例**：`/friend deny Alex`

---

### `/friend remove <name>`

- **用途**：把某个玩家从自己的好友列表中删除。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `name` | 好友的玩家名（`dynamic("name")`，**字符串**参数） | 是 | 无内置补全（源码未提供 `suggestion`）；支持离线玩家名 |
- **权限**：`chotentech.command.friend`
- **执行者要求**：**玩家**（体内 `sender as? Player ?: return@execute`）
- **行为说明**：
  1. `resolvePlayerId(name)`：先 `Bukkit.getPlayer(name)` 取在线玩家 UUID；否则 `Bukkit.getOfflinePlayer(name)`，仅当 `hasPlayedBefore()` 为真才返回其 UUID。
  2. 解析为 `null` → `找不到玩家 <name>`。
  3. `FriendManager.remove(player, targetId)`：若对方不在自己的好友列表则返回 `false`。
  4. 成功 → 自己收到 `已删除好友 <显示名>`（显示名取 `Bukkit.getOfflinePlayer(id).name`，取不到则回退为输入的名字）；若对方在线，对方收到 `✦ <自己> 将你从好友列表中删除了`。
  5. 失败 → `<显示名> 不是你的好友`。
- **示例**：`/friend remove Steve`、`/friend remove Notch`（离线亦可）

## 备注 / 注意事项

- **权限模型**：`permission = "chotentech.command.friend"`、`permissionDefault = PermissionDefault.TRUE` → **所有人默认可执行**；`permissionMessage = ""`。子命令无独立权限，执行体内无额外 `hasPermission` 检查。`newParser = false`。
- **全部子命令仅玩家可用**：`gui` / `requests` 为 `execute<Player>`；`add` / `accept` / `deny` / `remove` 虽声明为 `execute<CommandSender>`，但体内第一行都是 `sender as? Player ?: return@execute`，控制台执行会**静默无输出**。
- **`accept` / `deny` 的 `target` 是申请人**：参数名 `target` 有歧义，实际是"向我发申请的人"，且必须在线才能解析。
- **申请无过期**：与队伍邀请（60 秒）不同，好友申请保存在内存中不过期，直到被接受/拒绝或服务器重启。
- **好友上限双向校验**：`sendRequest` 只校验申请者上限；`accept` 会校验**双方**上限（申请人若不在线则跳过对其上限的检查）。上限来源：配置 `friend.max-friends`（默认 50），并会被 `chotentech.friend.<n>`（`n` 由 200 递减，取第一个命中者）覆盖。
- **好友关系持久化 vs 申请内存态**：好友名单写入 `ProfileManager` 的 SharedData 并落库；申请列表只在内存。
- **`remove` 用 `dynamic("name")` 而非 `player()`**：因此支持离线玩家（依赖 `hasPlayedBefore()`）；从未进服的玩家名会得到"找不到玩家"。注意 `Bukkit.getOfflinePlayer(name)` 对未知名可能返回带新 UUID 的对象，源码已用 `hasPlayedBefore()` 拦截。
- **`remove` 的对方侧清理有保护**：仅当对方已有 SharedData 数据（内存或数据库中存在）时才清理对方名单，避免为从未建档的玩家创建空记录。
- **静默失败点**：`add` 中 `context.player("target").castSafely<Player>()` 为 `null` 时直接 `return@execute`，玩家得不到任何提示（与 `accept`/`deny` 的"该玩家不在线"提示不一致）。
- **无屏蔽/黑名单功能**：源码只有 `gui`、`requests`、`add`、`accept`、`deny`、`remove` 六个分支，没有 `list`、`block` 等子指令。
