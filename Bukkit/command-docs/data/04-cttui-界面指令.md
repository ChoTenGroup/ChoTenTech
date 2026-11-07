# ChoTenTech 玩家界面指令（/chotentech-ui）

> **根指令**：`/chotentech-ui`（别名：`/cttui`）
> **权限**：`chotentech.command.ui`（默认：所有人，`PermissionDefault.TRUE`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChotenUICommand.kt`

## 功能概述

`/chotentech-ui` 是**玩家侧 GUI 菜单的快捷入口集合**：每个子命令对应一个已实现的菜单界面，既能给自己打开，也能由管理员替其他玩家打开。

源码用一个私有扩展函数 `CommandComponent.playerArg(title, open)` 统一生成"双分支"结构，因此 12 个子命令的形状完全一致：

- `/cttui <子命令>` → 打开**自己**的对应界面（runner 声明为 `execute<Player>`，控制台无法执行）；
- `/cttui <子命令> <target>` → 为**目标玩家**打开对应界面，并在 `sender != target` 时回执"已为 X 打开 Y 界面"。

> `@CommandHeader` 的 `newParser = false`；`permissionDefault = PermissionDefault.TRUE`，即**所有玩家默认可用**（这是本包中少见的非 OP 指令）。

## 指令总览

根权限统一为 `chotentech.command.ui`（默认**所有人**）；源码中**没有为任何子命令单独声明权限**。

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttui` | 无参数，打印指令用法树（`createModernHelper`） | `chotentech.command.ui` |
| `/cttui settings [target]` | 打开**设置**界面（`SettingsMenu`） | `chotentech.command.ui` |
| `/cttui menu [target]` | 打开**主菜单**（`MainMenu`） | `chotentech.command.ui` |
| `/cttui quests [target]` | 打开**任务列表**（`AllQuestMenu`） | `chotentech.command.ui` |
| `/cttui profile [target]` | 打开**角色档案**选择界面（`ProfileSelectionGUI`） | `chotentech.command.ui` |
| `/cttui rank [target]` | 打开**称号**选择界面（`RankSelectionMenu`） | `chotentech.command.ui` |
| `/cttui levels [target]` | 打开**层级**浏览界面（`LevelBrowseMenu`） | `chotentech.command.ui` |
| `/cttui cosmetics [target]` | 打开**装扮**界面（`CosmeticMenu`） | `chotentech.command.ui` |
| `/cttui achievement [target]` | 打开**成就**界面（`AchievementMenu`） | `chotentech.command.ui` |
| `/cttui team [target]` | 打开**队伍**界面（`TeamMenu`） | `chotentech.command.ui` |
| `/cttui friend [target]` | 打开**好友**界面（`FriendMenu`） | `chotentech.command.ui` |
| `/cttui org [target]` | 打开**组织**界面（`OrganizationMenu`） | `chotentech.command.ui` |
| `/cttui subscribe [target]` | 打开**订阅**界面（`SubscriptionMenu`） | `chotentech.command.ui` |

（合计 1 个 `main` 主体 + 12 个子命令；每个子命令含"自己"与"目标玩家"两条路径，共 24 条有效命令路径。）

## 指令详解

> 以下 12 节结构完全对称，差异只在**子命令字面量**、**界面类**与**回执中的中文标题**。公共行为先在此统一说明：

**公共参数表**

| 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
|---|---|---|---|
| `target` | 玩家参数，代为打开界面的目标玩家 | 否（省略即为自己） | 玩家名 |

**公共行为说明**

- 省略 `target`：`execute<Player>` 直接把界面开给发送者自己。
- 给出 `target`：`context.player("target").castSafely<Player>() ?: return@execute` 解析目标玩家，解析失败时静默返回（无提示）；成功则 `open(target)`，且当 `sender != target` 时向发送者回执
  `<#8FD4A6>已为 <#EFDF8F><玩家名> <#8FD4A6>打开<中文标题>界面`。
- **权限**：`chotentech.command.ui`（默认所有人）。
- **执行者要求**：省略 `target` 时**仅玩家**；给出 `target` 时玩家 / 控制台均可。

### `/cttui settings`
- **用途**：打开个人设置界面。
- **参数**：`[target]`（见公共参数表）。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：省略参数仅玩家；带 `target` 玩家/控制台均可。
- **行为说明**：`SettingsMenu().open(目标玩家)`；回执标题为"设置"。
- **示例**：`/cttui settings`、`/cttui settings Steve`

### `/cttui menu`
- **用途**：打开插件主菜单（通常是所有子界面的入口）。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`MainMenu().open(目标玩家)`；回执标题为"主菜单"。
- **示例**：`/cttui menu`、`/cttui menu Steve`

### `/cttui quests`
- **用途**：打开全部任务列表。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`AllQuestMenu().open(目标玩家)`；回执标题为"任务列表"。
- **示例**：`/cttui quests`、`/cttui quests Steve`

### `/cttui profile`
- **用途**：打开角色档案**选择**界面（多档案切换入口）。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`ProfileSelectionGUI().open(目标玩家)`；回执标题为"角色档案"。
- **示例**：`/cttui profile`、`/cttui profile Steve`

### `/cttui rank`
- **用途**：打开称号选择界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`RankSelectionMenu().open(目标玩家)`；回执标题为"称号"。
- **示例**：`/cttui rank`、`/cttui rank Steve`

### `/cttui levels`
- **用途**：打开层级（等级）浏览界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`LevelBrowseMenu().open(目标玩家)`；回执标题为"层级"。与 `/chotentech-levels`（别名 `cttl`/`cttlevels`）打开的是**同一个** `LevelBrowseMenu`。
- **示例**：`/cttui levels`、`/cttui levels Steve`

### `/cttui cosmetics`
- **用途**：打开装扮/外观界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`CosmeticMenu().open(目标玩家)`；回执标题为"装扮"。
- **示例**：`/cttui cosmetics`、`/cttui cosmetics Steve`

### `/cttui achievement`
- **用途**：打开成就界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`AchievementMenu().open(目标玩家)`；回执标题为"成就"。
- **示例**：`/cttui achievement`、`/cttui achievement Steve`

### `/cttui team`
- **用途**：打开队伍界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`TeamMenu().open(目标玩家)`；回执标题为"队伍"。
- **示例**：`/cttui team`、`/cttui team Steve`

### `/cttui friend`
- **用途**：打开好友界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`FriendMenu().open(目标玩家)`；回执标题为"好友"。
- **示例**：`/cttui friend`、`/cttui friend Steve`

### `/cttui org`
- **用途**：打开组织界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`OrganizationMenu().open(目标玩家)`；回执标题为"组织"。
- **示例**：`/cttui org`、`/cttui org Steve`

### `/cttui subscribe`
- **用途**：打开订阅界面。
- **参数**：`[target]`。
- **权限**：`chotentech.command.ui`。
- **执行者要求**：同上。
- **行为说明**：`SubscriptionMenu().open(目标玩家)`；回执标题为"订阅"。
- **示例**：`/cttui subscribe`、`/cttui subscribe Steve`

## 备注 / 注意事项

1. **默认对所有人开放**：`permissionDefault = PermissionDefault.TRUE`，普通玩家默认即可使用全部子命令。需要限制时应关闭该默认值或改用带权限的包装指令（源码没有为子命令单独声明权限节点）。
2. **自身分支 `execute<Player>`**：省略 `target` 时 runner 是 `execute<Player>`，控制台执行会被 TabooLib 拒绝（无法打开"自己的界面"）。这也是源码里 `playerArg` 注释所描述的设计。
3. **代开无权限二次校验**：带 `target` 时只校验根权限，**不会**校验发送者是否有权替该玩家操作；任何拥有 `chotentech.command.ui` 的人都能给别人弹菜单。
4. **回执仅在不同人时出现**：`if (sender != target)` 才发"已为 X 打开 Y 界面"，因此自己给自己开（例如控制台给自己？控制台不是玩家）不会刷出多余消息。
5. **目标解析失败静默**：`castSafely<Player>() ?: return@execute`，玩家名写错时命令**没有任何提示**。
6. **`main` 只打印用法树**：`/cttui` 本身不打开任何界面，必须带子命令。
7. **层级界面重复入口**：`levels` 子命令与 `/chotentech-levels`（见 `03-cttl-层级指令.md`）指向同一个 `LevelBrowseMenu`，只是权限与调用方式不同（一个默认所有人、一个是 OP 专用）。
8. **未使用的 import**：源码 `import io.github.zzzyyylllty.chotentech.gui.ProfileInfoGUI` 但文件内没有任何子命令使用它（不存在 `/cttui profileinfo`），属于残留导入；若日后补上该子命令，可复用这个 import。
9. **子命令字面量即属性名**：`settings` / `menu` / `quests` / `profile` / `rank` / `levels` / `cosmetics` / `achievement` / `team` / `friend` / `org` / `subscribe`，均未声明 `aliases`，因此只有原样拼写可用。
10. 该对象也被 `MainCommand` 以 `@CommandBody val ui = ChoTenUICommand` 挂载，因此 `/chotentech ui ...`（`/ctt ui ...`）与 `/cttui ...` 等价。
