# CTTACH 成就系统

> **根指令**：`/chotentech-achievement`（别名：`/cttach`, `/cttachievement`）
> **权限**：`chotentech.command.achievement`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/AchievementCommand.kt`

## 功能概述

`AchievementCommand` 是成就系统（`AchievementManager` + `AchievementMenu`）的管理入口，提供「强制完成某个成就」「打开成就界面」「列出全部成就定义」「重载成就配置」四组能力。

成就定义在启动/重载时由 `ChoTenTech.loadAchievementFiles()` 加载为 `AchievementManager.achievements`（`LinkedHashMap<String, Achievement>`）与 `AchievementManager.categories`（分类定义）；玩家的完成状态保存在 `PlayerData.achievements` 中（`AchievementProgress(completed, completedTime)`）。无权限时不输出提示（`permissionMessage = ""`），`newParser = false`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttach` | 无参数：输出用法树（`createModernHelper`） | `chotentech.command.achievement` |
| `/cttach complete <player> <achievement>` | 为指定玩家强制完成某个成就 | `chotentech.command.achievement` |
| `/cttach open` | 为自己打开成就界面 | `chotentech.command.achievement` |
| `/cttach open <target>` | 为指定玩家打开成就界面 | `chotentech.command.achievement` |
| `/cttach list` | 以聊天文本列出全部成就 | `chotentech.command.achievement` |
| `/cttach reload` | 重载成就配置 | `chotentech.command.achievement` |

## 指令详解

### `/cttach`
- **用途**：输出用法树。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：调用 `createModernHelper()`（实现位于 `MainCommand.kt`），首行 `Usage: /chotentech-achievement`，随后树形列出 `complete` / `open` / `list` / `reload`。纯展示。
- **示例**：`/cttach` → 打印用法树。

### `/cttach complete <player> <achievement>`
- **用途**：为指定玩家直接完成某个成就。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家（在线） | 是 | `player(...)` 组件，在线玩家名补全 |
  | `achievement` | 成就 ID（`AchievementManager.achievements` 的键） | 是 | 补全为 `AchievementManager.achievements.keys` |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：玩家 / 控制台均可（目标可以是任意在线玩家）
- **行为说明**：
  1. `context.player("player").castSafely<Player>()` 解析失败时**静默返回**（无任何提示）；
  2. 调用 `AchievementManager.complete(target, achievement)`：若玩家数据未加载（`playerDataMap` 无该 UUID）→ 返回 false；成就 ID 不存在 → 返回 false；该玩家已完成 → 返回 false；
  3. 成功：写入 `data.achievements[id] = AchievementProgress(completed = true, completedTime = 当前毫秒)`，执行者收到 `已为 <name> 完成成就: <成就显示名>`，目标收到 `恭喜！你获得了成就: <成就显示名>`；
  4. 失败时按原因二选一提示：成就 ID 不存在 → `成就 <id> 不存在`；否则 → `玩家 <name> 已完成该成就`。
- **示例**：`/cttach complete Steve first_blood` → Steve 的 `first_blood` 被标记为已完成，双方都收到提示。

### `/cttach open`
- **用途**：为自己打开成就界面（`AchievementMenu`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：仅玩家（`execute<Player>`；控制台执行该分支不会运行）
- **行为说明**：`AchievementMenu().open(sender)`。界面标题为 `成就列表 [<已完成数>/<总数>]`（`getCompletedCount` / `getTotalCount`），条目来自 `AchievementManager.getSortedEntries(player)`（按分类名、再按 ID 排序），每条按 `entry.progress?.completed` 与 `completedTime` 渲染完成状态与完成时间。只读界面，不写数据。
- **示例**：`/cttach open` → 打开自己的成就界面。

### `/cttach open <target>`
- **用途**：为指定在线玩家打开成就界面。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | `player(...)` 组件，在线玩家名补全 |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：`context.player("target").castSafely<Player>()` 为 null 时静默返回；否则 `AchievementMenu().open(target)`。**不向操作者发送任何确认消息**。
- **示例**：`/cttach open Steve` → Steve 屏幕上弹出成就界面。

### `/cttach list`
- **用途**：以聊天文本列出所有已加载的成就。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：先发 `===== 成就列表 =====`，随后每个成就一行：`<id> - <成就显示名> (<分类显示名>)`；分类名取 `AchievementManager.categories[ach.category]?.name`，取不到时直接显示原始 `category` 值；最后发 `共 N 个成就`。只读。
- **示例**：`/cttach list` → 逐行输出全部成就与总数。

### `/cttach reload`
- **用途**：重新读取成就配置文件。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.achievement`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：先发 `正在重载成就配置...`，随后调用 `ChoTenTech.reloadAchievements()`（内部即 `loadAchievementFiles()`），再发 `成就配置已重载。当前 N 个成就`（N 为 `AchievementManager.achievements.size`）。只重建成就定义（含分类），**不触及其他子系统、也不重载 `saves.yml`**；全量重载请用 `/ctt reload`（它会顺带调用 `reloadAchievements()`）。
- **示例**：`/cttach reload` → 输出重载前后提示与当前成就数量。

## 备注 / 注意事项

- **静默失败点（重要）**：`complete` 与 `open <target>` 都用 `context.player("x").castSafely<Player>() ?: return@execute`，目标玩家不在线/无法解析时**没有任何提示**，命令看起来「没反应」。
- **`complete` 的误导性提示**：「玩家数据未加载」同样会让 `AchievementManager.complete` 返回 false，而失败分支只区分「成就 ID 不存在」与「已完成」，因此数据未加载的在线玩家会收到 `玩家 <name> 已完成该成就` 这一并不准确的提示。
- **完成状态的存储**：写入内存 `playerDataMap[uuid].achievements` 的 `AchievementProgress`，持久化依赖玩家数据的自动保存/存档流程，指令本身不直接写数据库。
- **权限粒度**：四个子节点共用根权限 `chotentech.command.achievement`（默认 OP），源码中无 `sender.hasPermission(...)` 运行时校验；`permissionDefault = PermissionDefault.OP` 意味着默认只有 OP 能打开 `/cttach open`——若希望普通玩家自助查看成就，需要授予该权限节点。
- **依赖**：`AchievementManager`（成就定义与进度）、`AchievementMenu`（GUI）、`PlayerData.achievements`（进度持久化）。成就定义文件由 `loadAchievementFiles()` 加载，重载入口为 `reloadAchievements()`。
