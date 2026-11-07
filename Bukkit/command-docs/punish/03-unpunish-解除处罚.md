# unpunish：解除处罚

> **根指令**：`/chotentech-unpunish`（别名 `cttunpunish`、`unpunish`）
> **权限**：`chotentech.command.unpunish`（`permissionDefault = PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/punish/UnpunishCommand.kt`（全文 55 行，单个 `@CommandHeader` 对象）

## 功能概述

撤销某名玩家的处罚记录：把数据库中该玩家 `active = true` 的记录改成非激活，并清掉它的内存缓存。可以只撤销**某一种**处罚（如只解禁言），也可以一次撤销**全部**处罚。

被撤销的类型覆盖整个 `PunishType` 枚举：`WARN`（警告）、`MUTE`（禁言）、`KICK`（踢出）、`BAN`（限制登录）、`WIPE`（清除数据）、`AUTH`（身份验证）。
其中真正会让玩家「当场感到变化」的是三类：

- `MUTE` — 解除后聊天不再被 `AsyncChatEvent` 拦截；
- `BAN` / `AUTH` — 解除后 `PlayerLoginEvent` 不再拦人（注意两者是**独立的两条记录**，见「备注」第 2 条）；
- `WARN` — 解除后该条警告不再计入 `getWarnCount`。

指令树只有一个动态参数：

```
mainCommand {
    dynamic("target") {        // 目标玩家
        suggestion { 在线玩家名 }
        execute { … argument 里是「处罚类型」原始文本 … }
    }
}
```

> 源码没有给 `mainCommand` 注册 `execute`，也没有 `createModernHelper()`，因此**无参执行不会打印帮助树**，只会得到框架的缺参提示。

## 指令总览

| 根指令 | 别名 | 说明 | 权限 | 默认 |
|---|---|---|---|---|
| `chotentech-unpunish` | `cttunpunish`、`unpunish` | 解除玩家的处罚（可指定类型，留空 = 解除全部） | `chotentech.command.unpunish` | OP |

子命令/参数结构：

| 完整指令 | 说明 |
|---|---|
| `/chotentech-unpunish <target>` | 解除该玩家的**全部**处罚（并通知在线玩家） |
| `/chotentech-unpunish <target> <处罚类型>` | 只解除该类型（`WARN`/`MUTE`/`KICK`/`BAN`/`WIPE`/`AUTH`，大小写不敏感） |

## 指令详解

### `/chotentech-unpunish <target> [<处罚类型>]`

- **用途**：解除（deactivate）目标玩家已生效的处罚记录。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家：在线玩家名 / 玩家 UUID / 数据库中登记过的玩家名（源码 `dynamic("target")`，走 `resolvePlayer`） | 必填 | 自动补全：当前在线玩家名（`Bukkit.getOnlinePlayers().map { it.name }`） |
  | `处罚类型` | `execute` 的 `argument`（剩余整段文本）`trim()` 后按 `PunishType.valueOf(uppercase)` 解析 | 可选 | 无 Tab 补全（源码 `suggestion` 只补玩家名）。合法值：`WARN`、`MUTE`、`KICK`、`BAN`、`WIPE`、`AUTH`（大小写不敏感）。**留空 = 解除全部** |

- **权限**：`chotentech.command.unpunish`（默认 OP）。`execute` 内**没有**额外的 `sender.hasPermission(...)` 校验；指令本身也不区分「只能解除自己下发的处罚」之类的粒度。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。目标玩家可以离线（`resolvePlayer` 会查数据库）。
- **行为说明**：
  1. 解析 `target`；失败 → `未找到玩家: <输入>` 并结束。
  2. 解析类型：`argument.trim()` 为空 → 类型为 `null`（= 全部）；否则 `runCatching { PunishType.valueOf(它的大写形式) }`。
  3. 类型文本非空但解析失败 → 输出错误并结束：
     `未知处罚类型: <输入> (可用: WARN, MUTE, KICK, BAN, WIPE, AUTH,留空则解除全部)`
  4. 调用 `PunishManager.deactivateAllForPlayer(targetUuid, type)`：
     - 在 `punishment_records` 里筛出该 `target_uuid` 且 `active = true`（指定类型时再加 `type = <类型>`）的行，逐行把 `active` 改成 false 并 update；
     - 然后 `activeCache.remove(uuid)` —— **把这名玩家的整条内存缓存直接丢掉**（不管类型，见「备注」第 1 条）。
  5. 执行者收到 `已解除 <玩家> 的<类型显示名>处罚`；显示名取自 `PunishType.displayName`：`警告` / `禁言` / `踢出` / `限制登录` / `清除数据` / `身份验证`；全部解除时显示 `全部`。
  6. 仅当「解除全部」（类型为 `null`）**且**玩家在线时，额外给玩家发 `管理员已解除你的全部处罚`。
     指定类型解除时**不会**通知玩家。
  7. 全程**不发全服公告**，也不返回被解除的记录条数。
- **示例**：
  - `/unpunish Steve` → 解除 Steve 的全部处罚（含 BAN 与自动签发的 AUTH），Steve 在线时会听到提示。
  - `/cttunpunish Steve MUTE` / `/unpunish Steve mute` → 只解除禁言。
  - `/unpunish Steve AUTH` → 只解除身份验证（`/ban` 附带的那条）。
  - `/unpunish Steve 禁言` → ❌ 报「未知处罚类型: 禁言」，因为只认枚举英文名。

## 备注 / 注意事项

### 1. ⚠️ 指定类型解除时，内存缓存被整体清空

`deactivateAllForPlayer` 的数据库部分**只**把匹配类型的行置为非激活，但最后一句是 `activeCache.remove(uuid)` —— 无论有没有指定类型，都会把该玩家的**整条缓存**删掉。

受影响的是所有读缓存的运行时判定（`isMuted` / `isBannedOrAuthLocked` / `getWarnCount` / `getActiveByType`）。因此实际效果是：

- **数据库**：只有指定类型被置为非激活，其它类型的记录仍是 `active = true`；
- **本次运行期内**：该玩家的所有处罚（包括没被解除的类型）都不再生效（聊天拦截、登录拦截都不再触发）；
- **重启/重载后**：`startPunishmentTimer()`（`LifeCycle.ACTIVE`）会调用 `reloadActiveCache()` 从库里回填 `active = true` 且未过期的记录 —— 那些「只解除了一种类型」的其它处罚会**重新生效**。

若目的是让玩家彻底解封，请用「留空的全部解除」，或用 `punishment_records` 直接核对再用一次 `/unpunish`。

### 2. ⚠️ 解除 `BAN` 不会自动解除它附带的 `AUTH`

`/ban` 在不带 `-n` 时，`PunishManager.issue()` 会**额外签发一条独立的 AUTH 记录**（`durationMs = -1`，`meta.parentBanId = <BAN 的 ID>`）。这两条是两条不同的记录：

- `/unpunish Steve BAN` 只把 BAN 置为非激活，**AUTH 记录仍是 active**，玩家依然登录不进去（登录拦截 `isBannedOrAuthLocked` 同时看 BAN 和 AUTH）。
- 要真正放开登录，需要：`/unpunish Steve AUTH`（若还要留在本次运行期清理，见第 1 条的缓存行为），或者直接用 `/unpunish Steve` 解除全部。

### 3. 可以解除的类型与实际意义

| 类型 | 解除后的效果 |
|---|---|
| `MUTE` | 聊天不再被拦截（`isMuted` 返回 null） |
| `BAN` | 登录拦截不再命中 BAN（但可能仍被 AUTH 拦住） |
| `AUTH` | 登录拦截不再命中 AUTH |
| `WARN` | 该条警告不再计入 `getWarnCount` |
| `KICK` | 仅把历史记录置为非激活，玩家早已离线，无实际影响 |
| `WIPE` | 仅把记录置为非激活；**不会**把已清除的物品数据还原回来（还原要用 `/chotentech-resumewipe <处罚ID>`，见 `02-处罚指令-授权与清除数据.md`） |

### 4. 数据位置

- 处罚记录：数据库表 `punishment_records`（SQLite/MySQL，见 `PunishManager` 的 `db("config.yml", "database", "chotentech.db")`）
- 运行时判定用的内存缓存：`PunishManager.activeCache`
- 处罚历史 JSON：插件目录 `punishment_history.json` —— **解除处罚不会改写这个文件**，历史里仍然保留当初的处罚条目（`active` 字段不会同步刷新），所以历史文件不能当作「当前是否被处罚」的依据。

### 5. 其它

- 解除操作是「置为非激活」，**不删除**任何行，也不删除 `wipe_backups` 备份。
- 指令没有 `-s` / 时长 / 理由参数；多余文本会被当成「处罚类型」解析，从而触发「未知处罚类型」错误。
- 类型解析只接受 `PunishType` 的英文枚举名（大小写不敏感），不接受中文显示名（`禁言`、`封禁` 等都不行；注意 `BAN` 的显示名是「限制登录」而 `AUTH` 是「身份验证」）。
- 与 Chemdah 等外部插件无关，纯数据库/缓存操作。
- 别名 `unpunish` 较通用，若服务器已有同名指令，请用 `/cttunpunish`。
