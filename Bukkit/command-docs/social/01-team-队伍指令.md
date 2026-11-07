# 队伍指令（Team）

> **根指令**：`/team`（别名：`/td`, `/party`, `/dui`）
> **权限**：`chotentech.command.team`（默认：所有人）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/TeamCommand.kt`

## 功能概述

玩家组队系统指令，全部逻辑委派给 `TeamManager`，界面入口为 `TeamMenu`。

- 队伍数据保存在内存（`TeamManager.teams`，`ConcurrentHashMap`），非持久化。
- 队伍规模上限由队长权限动态决定：`getMaxTeamSize(player)` 读取配置 `team.default-size`（默认 `4`）与配置段 `team.permission-sizes`（键为权限节点、值为人数上限，取满足权限的最大值）。
- 邀请有效期为 **60 秒**（`TeamManager.Invite.isExpired()`：`now - createdAt > 60_000`）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/team` | 打印指令用法帮助树（`createModernHelper()`） | `chotentech.command.team` |
| `/team gui` | 打开队伍管理界面 | `chotentech.command.team` |
| `/team info` | 查看自己所在队伍的信息 | `chotentech.command.team` |
| `/team create` | 创建队伍（自己成为队长） | `chotentech.command.team` |
| `/team invite <target>` | 邀请玩家加入自己的队伍（仅队长） | `chotentech.command.team` |
| `/team accept <target>` | 接受某人的邀请加入其队伍 | `chotentech.command.team` |
| `/team deny <target>` | 拒绝某人的邀请 | `chotentech.command.team` |
| `/team leave` | 退出当前队伍（队长不可用） | `chotentech.command.team` |
| `/team kick <target>` | 踢出队员（仅队长） | `chotentech.command.team` |
| `/team transfer <target>` | 移交队长（仅队长，目标须为队员） | `chotentech.command.team` |
| `/team disband` | 解散队伍（仅队长） | `chotentech.command.team` |

> 10 个 `@CommandBody` 子命令 + 1 个默认主体（`main`）。源码中 `player(` 出现 5 次（`invite`、`accept`、`deny`、`kick`、`transfer` 的 `player("target")`），无 `dynamic(`、无 `literal(`。

## 指令详解

### `/team`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：`chotentech.command.team`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/team`

---

### `/team gui`

- **用途**：打开队伍图形界面。
- **参数**：无
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：`TeamMenu().open(sender)`，每次执行都新建一个 `TeamMenu` 实例并打开。
- **示例**：`/team gui`

---

### `/team info`

- **用途**：查看自己所在队伍的信息（队长、在线人数/总人数、成员列表）。
- **参数**：无
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 不在队伍中 → `你不在队伍中` 并中止。
  2. 输出 `===== 队伍信息 =====`。
  3. `队长: <离线名解析，解析不到显示"未知">`。
  4. `人数: <在线成员数>/<总成员数>`；总人数含队长（`team.allMembers` = 成员 + 队长）。
  5. 逐行列出成员：`●` 前缀（在线为绿色 `●`，离线为灰色 `●`），队长额外标 `[队长]`。
- **示例**：`/team info`

---

### `/team create`

- **用途**：创建一个以自己为队长的队伍。
- **参数**：无
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. `TeamManager.isInTeam(sender.uniqueId)` 为真 → `你已经在队伍中了，请先退出或解散当前队伍` 并中止。
  2. `TeamManager.createTeam(sender)`（新队伍的 `members` 为空集合，队长单独存放）。
  3. 提示 `队伍创建成功! 你是队长`，并提示用法 `/team invite <player>`。
- **示例**：`/team create`

---

### `/team invite <target>`

- **用途**：邀请在线玩家加入自己的队伍。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 被邀请的玩家（`player("target")` 玩家类型参数） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：按顺序校验：
  1. 目标解析不到玩家 → `目标玩家不在线`。
  2. 目标是自己 → `不能邀请自己`。
  3. 自己不在队伍 → `你不在队伍中，请先使用 /team create`。
  4. 自己不是队长 → `只有队长可以邀请玩家`。
  5. 目标已在其它队伍 → `该玩家已在其他队伍中`。
  6. 队伍人数已达 `TeamManager.getMaxTeamSize(sender)` → `队伍已满 (最多 N 人)`。
  7. `TeamManager.invite(sender, target)` 成功 → 自己收到 `已邀请 <target> 加入队伍`；目标收到邀请提示及 `/team accept <邀请者>`、`/team deny <邀请者>` 两条用法说明。
  8. 邀请记录仅存于内存，**60 秒后失效**。
- **示例**：`/team invite Steve`

---

### `/team accept <target>`

- **用途**：接受来自指定邀请者的入队邀请。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | **邀请者**（即向自己发出邀请的玩家，`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 目标解析不到玩家 → `目标玩家不在线`（因此邀请者必须在执行时在线）。
  2. 自己已在队伍 → `你已经在队伍中了`。
  3. `TeamManager.accept(sender, target.uniqueId)`：在"发给自己的邀请集合"中查找 `inviter == target` 且未过期的邀请，并再次校验队伍上限（按**队长**的权限上限，若队长离线则用配置 `team.default-size`，默认 4）。
  4. 成功 → 自己收到 `你已加入 <target> 的队伍!`，并通知队伍中其它在线成员 `<自己> 加入了队伍`。
  5. 失败 → `邀请已过期或不存在，请联系 <target> 重新邀请`。
- **示例**：`/team accept Alex`

---

### `/team deny <target>`

- **用途**：拒绝来自指定邀请者的入队邀请。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | **邀请者**（`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 解析不到目标 → `目标玩家不在线`。
  2. `TeamManager.deny(sender, target.uniqueId)` 成功 → 自己收到 `已拒绝 <target> 的邀请`，同时通知邀请者 `<自己> 拒绝了你的邀请`。
  3. 失败 → `没有来自 <target> 的邀请`。
- **示例**：`/team deny Alex`

---

### `/team leave`

- **用途**：退出当前队伍。
- **参数**：无
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 不在队伍 → `你不在队伍中`。
  2. 自己是队长 → **拒绝退出**，提示 `你是队长，请使用 /team disband 解散队伍，或使用 /team transfer <player> 移交队长`（注意：`TeamManager.leave` 本身对队长会执行解散，但指令层先拦截了）。
  3. `TeamManager.leave(sender)` 成功 → 自己收到 `你已退出队伍`；若队长在线，队长收到 `<自己> 已退出队伍`。
- **示例**：`/team leave`

---

### `/team kick <target>`

- **用途**：把队员踢出队伍。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 被踢出的队员（`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：按顺序校验：
  1. 解析不到目标 → `目标玩家不在线`。
  2. 目标是自己 → `不能踢出自己，请使用 /team leave`。
  3. 自己不在队伍 → `你不在队伍中`。
  4. 自己不是队长 → `只有队长可以踢人`。
  5. `TeamManager.kick(sender, target)` 成功（要求目标确实在 `members` 中）→ 自己收到 `已踢出 <target>`；目标收到 `你被队长踢出了队伍`；其它在线队员收到 `<target> 被队长踢出了队伍`。
  6. 失败 → `该玩家不在你的队伍中`。
- **示例**：`/team kick Steve`

---

### `/team transfer <target>`

- **用途**：把队长身份移交给某个队员。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 接任队长的队员（`player("target")`） | 是 | 在线玩家名 |
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：按顺序校验：
  1. 解析不到目标 → `目标玩家不在线`。
  2. 目标是自己 → `不能移交给自己`。
  3. 自己不在队伍 → `你不在队伍中`。
  4. 自己不是队长 → `只有队长可以移交队长`。
  5. `TeamManager.transfer(sender, target)` 成功（要求目标在 `members` 中）→ 自己收到 `已将队长移交给 <target>`；目标收到 `你已成为队伍的新队长!`；其它在线队员收到 `队长已变更为 <target>`。移交后原队长变为普通队员。
  6. 失败 → `该玩家不在你的队伍中`。
- **示例**：`/team transfer Steve`

---

### `/team disband`

- **用途**：解散自己领导的队伍。
- **参数**：无
- **权限**：`chotentech.command.team`
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 不在队伍 → `你不在队伍中`。
  2. 不是队长 → `只有队长可以解散队伍`。
  3. `TeamManager.disband(sender)` 成功 → 除自己外所有在线成员收到 `队伍已被队长解散`；自己最后收到 `队伍已解散`。
- **示例**：`/team disband`

## 备注 / 注意事项

- **权限模型**：`permission = "chotentech.command.team"`、`permissionDefault = PermissionDefault.TRUE` → **所有人默认可执行**；`permissionMessage = ""`。子命令无独立权限，执行体内无额外 `hasPermission` 检查。`newParser = false`（未启用新版解析器）。
- **全部子命令仅玩家**：10 个子命令均为 `execute<Player>`，控制台无法执行（`main` 帮助除外，其为 `ProxyCommandSender`）。
- **队伍规模上限受权限影响**：`getMaxTeamSize` 先读配置 `team.default-size`（默认 `4`），再扫描 `team.permission-sizes` 段里玩家拥有权限的键并取最大值。`invite` 用**邀请者（队长）**的上限判断，`accept` 用**当前队长**的上限判断；队长离线时 `accept` 回落到配置默认值。
- **邀请 60 秒过期**：`Invite.isExpired()` 判断 `> 60_000` 毫秒；另有 `cleanupExpiredInvites()` 定期清理。过期后 `accept` 会提示"邀请已过期或不存在"。
- **`accept` 失败文案不精确**：`TeamManager.accept` 在"邀请不存在""邀请已过期""队伍已满"三种情况都返回 `false`，指令层统一提示 `邀请已过期或不存在，请联系 <邀请者> 重新邀请` —— 队伍已满时也会显示这句话。
- **`accept` / `deny` 的 `target` 是邀请者**：参数名 `target` 容易误解，实际语义是"发邀请给我的人"，且必须是**在线**玩家。所以邀请者下线后，被邀请者无法通过指令接受邀请。
- **队长不能 `leave`**：指令层拦截队长（提示用 `disband` 或 `transfer`），尽管 `TeamManager.leave` 对队长会退化为解散。
- **`kick` / `transfer` 目标必须是队员**：队长本人不在 `members` 集合中，因此对自己操作会先被"不能踢出自己 / 不能移交给自己"拦截。
- **队长离线自动解散**：`TeamManager.handleLeaderQuit` 会在队长退出服务器时解散队伍并通知在线成员（由事件触发，非指令行为）。
- **数据非持久化**：`teams` 与 `invites` 均为内存 `ConcurrentHashMap`，服务器重启后队伍与邀请全部丢失。
- **只能邀请在线玩家**：`invite` / `accept` / `deny` / `kick` / `transfer` 都需要目标在线（`as? Player ?: 目标玩家不在线`）。
