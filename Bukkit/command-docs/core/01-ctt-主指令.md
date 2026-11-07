# ChoTenTech 主指令

> **根指令**：`/chotentech`（别名：`/ctt`）
> **权限**：`chotentech.command.main`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/MainCommand.kt`

## 功能概述

`MainCommand` 是 ChoTenTech 的总入口，基于 TabooLib 注解式指令框架（`@CommandHeader` + `@CommandBody`）注册。它自身只提供三条最基础的能力：无参数的用法树、`about` 版本信息、`reload` 全量热重载，其余功能全部以「嵌套指令对象」的形式挂载在 `/ctt` 之下（例如 `/ctt debug`、`/ctt base`、`/ctt attribute`）。

同一个嵌套对象通常还有自己的独立根指令（例如 `BaseCommand` 既是 `/ctt base`，也是 `/cttbase`），因此命令既可从主指令进入，也可从独立根指令进入。

无权限时本指令不输出任何提示（`permissionMessage = ""`），且注解 `newParser = false`（使用 TabooLib 旧版解析器）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/ctt` | 无参数：输出按权限过滤后的完整用法树（`createModernHelper`） | `chotentech.command.main` |
| `/ctt about` | 显示插件版本、运行平台与 Minecraft 版本、作者 | `chotentech.command.main` |
| `/ctt reload` | 全量热重载配置，并重载 `saves.yml` 存档点、刷新存档点全息图 | `chotentech.command.main` |
| `/ctt debug …` | 嵌套 `ChoTenDebugCommand`（独立根指令 `/chotentech-debug`，别名 `/cttdebug`，权限 `chotentech.command.debug`） | `chotentech.command.debug` |
| `/ctt base …` | 嵌套 `BaseCommand`（独立根指令 `/chotentech-base`，别名 `/cttbase`、`/cttb`，权限 `chotentech.command.base`） | `chotentech.command.base` |
| `/ctt interfere …` | 嵌套 `InterfereCommand`（独立根指令 `/chotentech-interfere`，别名 `/cttinterfere`、`/interfere`，权限 `chotentech.command.interfere`） | `chotentech.command.interfere` |
| `/ctt pvp …` | 嵌套 `PvpCommand`（独立根指令 `/pvpEnabled`，无别名，权限 `chotentech.command.pvp`，默认所有人） | `chotentech.command.pvp` |
| `/ctt attribute …` | 嵌套 `ChoTenAttributeMainCommand`（独立根指令 `/chotenattribute`，别名 `/attribute`、`/atb`，权限 `chotenattribute.command.main`） | `chotenattribute.command.main` |
| `/ctt gametime …` | 嵌套 `GameTimeCommand`（独立根指令 `/gametime`，别名 `/gtm`、`/游戏时间`，权限 `chotentech.command.gametime`） | `chotentech.command.gametime` |
| `/ctt profileinfo …` | 嵌套 `ProfileInfoCommand`（独立根指令 `/profileinfo`，别名 `/pi`、`/档案信息`，权限 `chotentech.command.profileinfo`） | `chotentech.command.profileinfo` |
| `/ctt ui …` | 嵌套 `ChoTenUICommand`（独立根指令 `/chotentech-ui`，别名 `/cttui`，权限 `chotentech.command.ui`，默认所有人） | `chotentech.command.ui` |
| `/ctt settings …` | 嵌套 `SettingsCommand`（独立根指令 `/chotentechsettings`，别名 `/ctts`、`/设置`，权限 `chotentech.command.settings`） | `chotentech.command.settings` |
| `/ctt profile …` | 嵌套 `ProfileAdminCommand`（独立根指令 `/chotentech-profile`，别名 `/cttprofile`、`/cttp`，权限 `chotentech.command.profileadmin`） | `chotentech.command.profileadmin` |
| `/ctt gun …` | 嵌套 `GunDebugCommand`（独立根指令 `/chotentech-gun`，别名 `/cttgun`，权限 `chotentech.command.gun`）；该 `@CommandBody` 上额外声明了 `permission = "chotentech.command.gun"` 与 `description = "枪械系统调试指令"` | `chotentech.command.gun` |

> 表中 `…` 的嵌套内容属于各自对象，**本文不展开**，请查阅对应的独立文档（`ChoTenDebugCommand`、`BaseCommand`、`InterfereCommand`、`PvpCommand`、`ChoTenAttributeMainCommand`、`GameTimeCommand`、`ProfileInfoCommand`、`ChoTenUICommand`、`SettingsCommand`、`ProfileAdminCommand`、`GunDebugCommand`）。
>
> 源码中被注释掉、**未注册**的两个嵌套项：`coolnumber`（`CoolNumberCommand`）与 `Data`（`ChoTenDataCommand`），因此 `/ctt coolnumber`、`/ctt Data` 不存在。

## 指令详解

### `/ctt`
- **用途**：显示当前发送者有权使用的全部子指令用法树。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.main`
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：由 `createModernHelper()`（本文件内定义的公共扩展函数，签名 `CommandComponent.createModernHelper(checkPermissions: Boolean = true)`）生成：
  - 首行固定为 `Usage: /<根指令名>`，此处即 `Usage: /chotentech`（使用 `context.command.name`，而非玩家输入的别名）。
  - 递归遍历 `context.commandCompound.children`，`checkPermissions = true` 时用 `sender.hasPermission(component.permission)` 过滤掉无权限节点；同时过滤掉 `hidden = true` 的字面量节点。
  - 字面量节点显示其 `aliases[0]`；`dynamic` 节点显示为 `<注释>`，可选参数显示为 `[<注释>]`；注释若以 `@` 开头则按语言文件键（`asLangText`）解析。
  - 使用 `├──`、`└──`、`│` 构建树形缩进，逐行以 MiniMessage 组件发送。
  - 只做展示，不执行任何业务逻辑、不写数据。
- **示例**：`/ctt` → 输出类似 `Usage: /chotentech` 后接多行树形子指令列表（OP 可见 `about`、`reload`、`debug`、`base`… `gun` 等节点）。

### `/ctt about`
- **用途**：查看插件版本与运行环境信息。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.main`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：发送三行信息：
  1. `ChoTenTech <pluginVersion>`（TabooLib `pluginVersion`）；
  2. `Running on: <runningPlatform.name> - <minecraftVersion>`；
  3. `Plugin by AkaCandyKAngel.`
- **示例**：`/ctt about` → `ChoTenTech 1.x.x` / `Running on: BUKKIT - 1.20.x` / `Plugin by AkaCandyKAngel.`

### `/ctt reload`
- **用途**：热重载 ChoTenTech 的全部配置，并额外重载存档点与全息图。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.main`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. 先发送 `reloading...`；
  2. 调用 `ChoTenTech.reloadCustomConfig()`（默认 `async = true`）。该方法内部会 `submit(async)`，负责 `config.reload()`、`baseConfig`、`currencyConfig`、`filterConfig`、`chatSettings`、`backpackConfig` 等配置文件重载，重新加载等级/力量/账号类型/成长档文件、`BaseManager.loadBases`、藏身处、站点、订阅、称号、惩罚、安全、成就（内部调用 `reloadAchievements()`）、枪械（`GunRegistry` / `GunExampleItems` / `GunSettings`）、商店与礼包、医疗包、消耗品等一大票子系统；
  3. 随后在主线程执行 `SavePointConfig.load()`（重读 `saves.yml`）与 `SavePointManager.createHolograms()`（重建存档点全息图）——源码注释明确「`saves.yml` 热重载（主线程执行，异步 `reloadCustomConfig` 不包含存档点）」；
  4. 成功后发送 `reloading... Complete.`；任何异常被 `try/catch` 捕获，向发送者发送 `Error thrown. check console for info.` 与异常字符串（`$e`）。
  - 副作用：清空并重建脚本缓存（`ketherScriptCache`、`jsScriptCache`）、方块再生模板与 `levels` 表，因此重载后已在场的再生方块状态会丢失（由 `releaseRegenBlocks()` 释放）。
- **示例**：`/ctt reload` → 输出 `reloading...` 与 `reloading... Complete.`，随后存档点全息图重建。

## 备注 / 注意事项

- **权限静默**：`permissionMessage = ""`，无 `chotentech.command.main` 的发送者执行 `/ctt` 系列不会收到任何权限提示。
- **重载顺序**：`reloadCustomConfig()` 是异步提交的，而 `SavePointConfig.load()` 与 `SavePointManager.createHolograms()` 在指令线程立即执行。因此日志中「配置重载完成」与存档点刷新的先后顺序不保证；若存档点内容依赖本次重载出的新 `levels` 配置，请再次执行 `/cttadmin feature fixsavelocation` 或重载一次。
- **用法树过滤**：`createModernHelper` 只按节点的 `permission` 过滤。嵌套对象（如 `gun`）额外声明了权限，故无 `chotentech.command.gun` 时 `/ctt` 的用法树里也看不到 `gun` 节点。
- **`checkPermissions` 参数**：`createModernHelper(checkPermissions = true)` 是公共扩展函数，`CttAdminCommand`、`AchievementCommand`、`BaseCommand` 的 `main` 也复用同一实现。
- **未注册节点**：`coolnumber`、`Data` 两个 `@CommandBody` 在源码中被注释，任何文档/玩家指令表中都不应出现。
- **与其他系统联动**：`reload` 会连带重载基地（`BaseManager.loadBases`）、成就（`reloadAchievements`）、称号（`RankManager.load`）、商店/订阅、枪械等；重载后 `BackpackConfig.load()` 会刷新背包系统的格子行数与标签键（默认 `tag = "cho-backpack"`，UUID 键为 `cho-backpack-uuid`）。
