# 层级浏览界面指令（/chotentech-levels）

> **根指令**：`/chotentech-levels`（别名：`/cttl`, `/cttlevels`）
> **权限**：`chotentech.command.levels`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/LevelsCommand.kt`
> **对象名**：`LevelsCommand`

## 功能概述

`/chotentech-levels` 是**层级（等级）浏览菜单** `LevelBrowseMenu` 的独立入口，用于给自己或其他玩家打开该 GUI。

它提供两条等价的访问路径：

- `/cttlevels`（`main` 主体，可选 `[target]`）
- `/cttlevels open [target]`（显式 `open` 子命令）

两者的行为完全相同：打开目标玩家的 `LevelBrowseMenu`；控制台在不指定玩家时会收到一条"请指定玩家"的提示。

> `@CommandHeader` 的 `newParser = false`，`permissionDefault = PermissionDefault.OP`。
> 该对象**没有**通过 `MainCommand` 挂载（不在 `/chotentech` 之下），只能用自己的根名或别名调用。

## 指令总览

根权限统一为 `chotentech.command.levels`（默认 OP）；源码中**没有为任何子命令单独声明权限**。

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttlevels` | 为自己打开层级浏览界面；控制台执行时提示需指定玩家 | `chotentech.command.levels` |
| `/cttlevels <target>` | 为指定玩家打开层级浏览界面 | `chotentech.command.levels` |
| `/cttlevels open` | 为自己打开层级浏览界面；控制台执行时提示需指定玩家 | `chotentech.command.levels` |
| `/cttlevels open <target>` | 为指定玩家打开层级浏览界面 | `chotentech.command.levels` |

（合计 1 个 `main` 主体 + 1 个子命令 `open`，共 4 条有效命令路径。）

## 指令详解

### `/cttlevels`
- **用途**：为自己打开层级浏览界面。
- **参数**：无（无参数分支）。
- **权限**：`chotentech.command.levels`。
- **执行者要求**：玩家（发送者为玩家时打开界面）；控制台执行不会报错，但只会收到提示 `§c请指定玩家: /cttlevels <player>`。
- **行为说明**：`main` 同时挂载了 `createModernHelper()`（无参数时打印用法树）和 `execute<CommandSender>`；本分支中 `if (sender is Player) LevelBrowseMenu().open(sender) else sender.sendMessage(...)`。
- **示例**：`/cttlevels`

### `/cttlevels <target>`
- **用途**：为指定玩家打开层级浏览界面。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 玩家参数，要打开界面的目标玩家 | 是 | 玩家名（TabooLib `player()` 解析） |
- **权限**：`chotentech.command.levels`。
- **执行者要求**：玩家 / 控制台均可（runner 为 `execute<CommandSender>`）。
- **行为说明**：`context.player("target").castSafely<Player>() ?: return@execute` 解析目标玩家，解析失败时**静默返回**（无提示）；成功则 `LevelBrowseMenu().open(target)`。**没有**"已为 X 打开界面"的回执（与 `/cttui` 不同）。
- **示例**：`/cttlevels Steve`

### `/cttlevels open`
- **用途**：为自己打开层级浏览界面（`open` 子命令形式）。
- **参数**：无（无参数分支）。
- **权限**：`chotentech.command.levels`。
- **执行者要求**：玩家（控制台只会收到提示 `§c请指定玩家: /cttlevels open <player>`）。
- **行为说明**：与 `/cttlevels` 的无参数分支逻辑相同，仅控制台提示文案多了 `open` 字样。
- **示例**：`/cttlevels open`

### `/cttlevels open <target>`
- **用途**：为指定玩家打开层级浏览界面（`open` 子命令形式）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 玩家参数，目标玩家 | 是 | 玩家名 |
- **权限**：`chotentech.command.levels`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：与 `/cttlevels <target>` 逻辑相同。
- **示例**：`/cttlevels open Steve`

## 备注 / 注意事项

1. **两条路径完全等价**：`main` 的 `[target]` 分支与 `open [target]` 分支实现了同一段代码，`open` 只是给习惯"动词式"写法的使用者提供的别名入口。
2. **`main` 上写了两段执行逻辑**：源码在 `main` 里先调用 `createModernHelper()`（其内部也是注册一个 `execute`，用于打印用法树），随后又写了显式的 `execute<CommandSender> {...}`，显式的那段写在后面。判断：不带参数执行时生效的是**后面注册的**那段（打开界面/提示指定玩家），用法树不会打印。本对象**没有** `help` 子命令，如确需查看用法树可参考同包其它指令的 `help`（`/cttdata help`、`/quetiapinedebug help`）确认 `createModernHelper` 的输出形态。
3. **别名只差一个 s**：本指令是 `cttl` / `cttlevels`；等级经验指令 `/quetiapinelevel` 的别名是 `cttlevel`。`cttlevel` ≠ `cttlevels`，请勿混淆（前者改数据，后者开菜单）。
4. **目标解析失败静默**：`castSafely<Player>() ?: return@execute`，写错玩家名不会有任何反馈。
5. **无回执消息**：本指令不像 `/cttui` 那样在给他人打开界面时回执"已为 X 打开 Y 界面"。
6. **默认 OP**：`permissionDefault = PermissionDefault.OP`；若想让普通玩家自助打开，建议改用 `/cttui levels`（其根权限默认所有人），或另行授予 `chotentech.command.levels`。
7. **界面与 `/cttui levels` 相同**：都调用 `LevelBrowseMenu().open(...)`，属同一个 GUI。
8. `permissionMessage = ""`：权限不足时不输出自定义提示。
