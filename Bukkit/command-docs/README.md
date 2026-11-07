# ChoTenTech Bukkit 指令文档

本目录收录 `Bukkit` 模块（`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/`）中**全部指令**的用法说明，
由源码逐行整理而来，按功能分目录组织。

- 指令框架：TabooLib `@CommandHeader` / `@CommandBody` 注解式指令
- 根指令数量：**72**（分布在 60 个含 `@CommandHeader` 的源码文件中）
- 文档语言：简体中文

## 目录结构

| 文件夹 | 内容 | 文档数 |
|---|---|---|
| [`core/`](core/) | 主指令与管理指令：`/ctt`、`/cttadmin`、成就、背包、基地、投保 | 6 |
| [`chat/`](chat/) | 聊天频道、私聊、屏蔽、聊天重载 | 3 |
| [`client/`](client/) | ULS 客户端模组与菜单框架诊断 | 2 |
| [`attribute/`](attribute/) | ChoTenAttribute 属性系统（主指令 / 管理 / 调试 / API） | 4 |
| [`api/`](api/) | 对外 API 指令：物品 API、通用 API | 2 |
| [`data/`](data/) | 玩家数据、等级经验、层级浏览、界面指令 | 4 |
| [`debug/`](debug/) | 调试类指令：`chotentech-debug`、`quetiapinedebug`、`meta`、`chotentech-body` | 4 |
| [`economy/`](economy/) | 货币、点数、声望、磁带、商店、全球市场 | 6 |
| [`social/`](social/) | 队伍、好友 | 2 |
| [`player/`](player/) | 档案、设置、安全容器、教程、提交物品、装饰、反穿墙 | 9 |
| [`teleport/`](teleport/) | 出生点与传送指令 | 4 |
| [`misc/`](misc/) | 游戏时间、PVP、干涉、称号 | 4 |
| [`punish/`](punish/) | 禁言 / 踢出 / 警告 / 封禁 / 清除数据 / 状态属性 | 4 |
| [`system/`](system/) | 枪械、丢弃锁、藏身处、仓库、备份、站点、干涉、酷数 | 9 |
| **合计** | | **62** |

> 一个源码文件可能定义多个根指令（例如 `PunishCommand.kt` 定义 7 个），因此「文档数」少于「根指令数」。
> 另外 `chotentech-meta` 的详细文档位于 [`debug/03`](debug/03-cttmeta-元数据指令.md)，`chotentech-interfere` 的详细文档位于 [`misc/03`](misc/03-cttinterfere-干涉指令.md)（`system/08` 为同一指令树的另一份记录）。

## 指令总表（72 个根指令）

### 核心 / 管理

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotentech` | `ctt` | `chotentech.command.main` | OP | 插件主指令，汇总大量子模块 | [core/01](core/01-ctt-主指令.md) |
| `chotentech-admin` | `cttadmin` | `chotentech.command.admin` | OP | 服务器管理：存档点、货币、订阅、迁移 | [core/02](core/02-cttadmin-管理指令.md) |
| `chotentech-achievement` | `cttach`、`cttachievement` | `chotentech.command.achievement` | OP | 成就系统管理 | [core/03](core/03-cttach-成就系统.md) |
| `chotenbackpack` | `cb`、`cho` | `chotentech.command.backpack` | — | 背包管理 | [core/04](core/04-chotenbackpack-背包管理.md) |
| `chotentech-base` | `cttbase`、`cttb` | `chotentech.command.base` | OP | 基地系统 | [core/05](core/05-cttbase-基地系统.md) |
| `cttdrop` | — | `chotentech.command.cttdrop` | OP | 投保 / 取回投保物品 | [core/06](core/06-cttdrop-投保取回.md) |

### 聊天与客户端

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chat` | `ch`、`channel` | `chotentech.command.chat` | 所有人 | 聊天频道切换与发言 | [chat/01](chat/01-chat-聊天频道.md) |
| `chatreload` | `chreload` | `chotentech.chat.admin` | OP | 重载聊天配置 | [chat/02](chat/02-chatreload-重载聊天配置.md) |
| `msg` | `whisper`、`w`、`pm` | `chotentech.command.msg` | 所有人 | 私聊 | [chat/03](chat/03-私聊与屏蔽.md) |
| `reply` | `r` | `chotentech.command.msg` | 所有人 | 回复私聊 | [chat/03](chat/03-私聊与屏蔽.md) |
| `ignore` | `block` | `chotentech.command.msg` | 所有人 | 屏蔽玩家 | [chat/03](chat/03-私聊与屏蔽.md) |
| `unignore` | `unblock` | `chotentech.command.msg` | 所有人 | 解除屏蔽 | [chat/03](chat/03-私聊与屏蔽.md) |
| `ignorelist` | `listignores` | `chotentech.command.msg` | 所有人 | 查看屏蔽列表 | [chat/03](chat/03-私聊与屏蔽.md) |
| `ulsclient` | `uls` | `chotentech.uls.client` | OP | 查看客户端模组握手信息 | [client/01](client/01-ulsclient-客户端信息.md) |
| `ulsmenu` | `ulsm` | `chotentech.uls.menu` | OP | 菜单框架诊断与演示 | [client/02](client/02-ulsmenu-菜单诊断.md) |

### 属性系统

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotenattribute` | `attribute`、`atb` | `chotenattribute.command.main` | OP | 属性系统主指令（内嵌 debug / manage） | [attribute/01](attribute/01-chotenattribute-属性主指令.md) |
| `chotenattribute-manage` | `attributemanage`、`atbmanage` | `chotenattribute.command.manage` | — | 属性数值管理 | [attribute/02](attribute/02-chotenattribute-manage-属性管理.md) |
| `chotenattribute-debug` | `attributedebug`、`atbdebug` | `chotenattribute.command.debug` | — | 属性调试 | [attribute/03](attribute/03-chotenattribute-debug-属性调试.md) |
| `attributeapi` | `atbapi` | `chotenattribute.command.api` | — | 属性 API 查询 | [attribute/04](attribute/04-attributeapi-属性API.md) |

### API

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotenapi` | `itemapi`、`needyitemapi`、`depazapi` | `chotentech.command.api` | — | 物品相关的对外 API | [api/01](api/01-chotenapi-物品API.md) |
| `quetiapineapi` | `rpgapi`、`chotentechapi`、`cttapi` | `quetiapine.command.api` | — | 通用对外 API | [api/02](api/02-quetiapineapi-通用API.md) |

### 数据 / 等级 / 界面

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotentech-data` | `cttdata`、`quetiapinedata`、`rpgdata`、`chotentechdata` | `chotentech.command.data` | — | 玩家数据读写 | [data/01](data/01-cttdata-玩家数据指令.md) |
| `quetiapinelevel` | `rpglevel`、`chotentechlevel`、`cttlevel` | `quetiapine.command.level` | — | 等级与经验 | [data/02](data/02-cttlevel-等级经验指令.md) |
| `chotentech-levels` | `cttl`、`cttlevels` | `chotentech.command.levels` | OP | 层级（Levels）浏览菜单 | [data/03](data/03-cttl-层级指令.md) |
| `chotentech-ui` | `cttui` | `chotentech.command.ui` | 所有人 | 玩家界面菜单集合 | [data/04](data/04-cttui-界面指令.md) |

### 调试

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotentech-debug` | `cttdebug` | `chotentech.command.debug` | — | 主插件调试 | [debug/01](debug/01-chotentech-debug-调试指令.md) |
| `quetiapinedebug` | `rpgdebug`、`chotentechdebug`、`cttdebug` | `quetiapine.command.debug` | — | 数据栈调试 | [debug/02](debug/02-quetiapinedebug-数据调试.md) |
| `chotentech-meta` | `cttmeta` | `chotentech.admin` | — | 物品元数据操作 | [debug/03](debug/03-cttmeta-元数据指令.md) |
| `chotentech-body` | `cttbody`、`bodydebug` | `chotentech.command.bodydebug` | OP | 肢体韧性/水分/倒地休克调试 | [debug/04](debug/04-cttbody-肢体韧性调试.md) |

> ⚠️ `chotentech-debug` 与 `quetiapinedebug` **都声明了别名 `cttdebug`**，存在别名冲突，实际生效者取决于注册顺序。请使用完整指令名。

### 经济与市场

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `money` | `meg`、`积分` | `chotentech.command.money` | OP | 货币余额查询与转账 | [economy/01](economy/01-money-货币指令.md) |
| `point` | `bntg`、`贸易点` | `chotentech.command.point` | OP | 贸易点 | [economy/02](economy/02-point-点数指令.md) |
| `repumeg` | `rmeg` | `chotentech.command.repumeg` | OP | 声望换算货币 | [economy/03](economy/03-repumeg-声望与货币.md) |
| `tape` | `磁带` | `chotentech.command.tape` | OP | 磁带（付费货币道具） | [economy/04](economy/04-tape-磁带指令.md) |
| `chotentech-shop` | `cttshop`、`shop` | 未声明 | — | 商店 | [economy/05](economy/05-shop-商店指令.md) |
| `chotentech-market` | `cttm`、`market`、`globalmarket` | 未声明 | — | 全球市场 | [economy/06](economy/06-cttm-全球市场.md) |

### 社交

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `team` | `td`、`party`、`dui` | `chotentech.command.team` | 所有人 | 队伍 | [social/01](social/01-team-队伍指令.md) |
| `friend` | `haoyou`、`fy` | `chotentech.command.friend` | 所有人 | 好友 | [social/02](social/02-friend-好友指令.md) |

### 玩家系统

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `profile` | `profiles`、`玩家档案` | `chotentech.command.profile` | — | 玩家档案 | [player/01](player/01-profile-玩家档案.md) |
| `chotentech-profile` | `cttprofile`、`cttp` | `chotentech.command.profileadmin` | — | 档案管理 | [player/02](player/02-cttprofile-档案管理.md) |
| `profileinfo` | `pi`、`档案信息` | `chotentech.command.profileinfo` | — | 档案信息查看 | [player/03](player/03-profileinfo-档案信息.md) |
| `chotentechsettings` | `ctts`、`设置` | `chotentech.command.settings` | — | 个人设置菜单 | [player/04](player/04-ctts-设置菜单.md) |
| `securecontainer` | `sc` | `chotentech.command.securecontainer` | — | 安全容器 | [player/05](player/05-securecontainer-安全容器.md) |
| `chotentech-skiptutorial` | `cttskiptutorial`、`skiptutorial` | `chotentech.command.skiptutorial` | — | 跳过教程 | [player/06](player/06-skiptutorial-跳过教程.md) |
| `submititem` | — | 未声明 | OP | 提交物品 | [player/07](player/07-submititem-提交物品.md) |
| `cosmetics` | `cosmetic`、`decoration`、`装饰` | `chotentech.command.cosmetics` | — | 装饰 / 外观 | [player/09](player/09-cosmetics-装饰指令.md) |
| `noclips` | `noclip` | `chotentech.command.checknoclips` | OP | 反穿墙检测 | [player/10](player/10-noclip-穿墙检测.md) |

### 传送

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `backspawn` | `bspawn` | `chotentech.command.backspawn` | — | 返回出生点 | [teleport/01](teleport/01-backspawn-返回出生点.md) |
| `setspawn` | `sspawn` | `chotentech.command.setspawn` | — | 设置出生点 | [teleport/02](teleport/02-setspawn-设置出生点.md) |
| `tpbase` | — | `chotentech.command.tpbase` | — | 传送至基地 | [teleport/03](teleport/03-tpbase-传送基地.md) |
| `tplevel` | `tpl` | `chotentech.command.tplevel` | — | 传送至层级 | [teleport/04](teleport/04-tplevel-传送层级.md) |

### 其他

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `gametime` | `gtm`、`游戏时间` | `chotentech.command.gametime` | OP | 游戏内时间推进 | [misc/01](misc/01-gametime-游戏时间.md) |
| `pvpEnabled` | — | `chotentech.command.pvp` | 所有人 | PVP 开关 | [misc/02](misc/02-pvpEnabled-PVP开关.md) |
| `chotentech-interfere` | `cttinterfere`、`interfere` | `chotentech.command.interfere` | OP | 干涉（事件干预） | [misc/03](misc/03-cttinterfere-干涉指令.md)、[system/08](system/08-cttinterfere-干涉指令.md) |
| `chotentech-rank` | `cttr`、`rank` | `chotentech.command.rank` | — | 称号 | [misc/04](misc/04-cttr-称号指令.md) |

### 处罚与状态

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotentech-mute` | `cttmute`、`mute` | `chotentech.command.mute` | — | 禁言 | [punish/01](punish/01-处罚指令-禁言踢出警告封禁.md) |
| `chotentech-kick` | `cttkick`、`kick` | `chotentech.command.kick` | — | 踢出 | [punish/01](punish/01-处罚指令-禁言踢出警告封禁.md) |
| `chotentech-warn` | `cttwarn`、`warn` | `chotentech.command.warn` | — | 警告 | [punish/01](punish/01-处罚指令-禁言踢出警告封禁.md) |
| `chotentech-ban` | `cttban`、`ban` | `chotentech.command.ban` | — | 封禁 | [punish/01](punish/01-处罚指令-禁言踢出警告封禁.md) |
| `chotentech-authpunish` | `cttauth`、`authpunish` | `chotentech.command.authpunish` | — | 授权处罚 | [punish/02](punish/02-处罚指令-授权与清除数据.md) |
| `chotentech-wipe` | `cttwipe`、`wipe` | `chotentech.command.wipe` | — | 清除玩家数据 | [punish/02](punish/02-处罚指令-授权与清除数据.md) |
| `chotentech-resumewipe` | `cttresumewipe`、`resumewipe` | `chotentech.command.resumewipe` | — | 恢复已清除的数据 | [punish/02](punish/02-处罚指令-授权与清除数据.md) |
| `chotentech-unpunish` | `cttunpunish`、`unpunish` | `chotentech.command.unpunish` | — | 解除处罚 | [punish/03](punish/03-unpunish-解除处罚.md) |
| `chotentech-sanity` | `cttsanity`、`sanity` | `chotentech.command.sanity` | — | 理智值 | [punish/04](punish/04-属性状态指令-理智压力黑暗.md) |
| `chotentech-stress` | `cttstress`、`stress` | `chotentech.command.stress` | — | 压力值 | [punish/04](punish/04-属性状态指令-理智压力黑暗.md) |
| `chotentech-darkness` | `cttdarkness`、`darkness` | `chotentech.command.darkness` | — | 黑暗值 | [punish/04](punish/04-属性状态指令-理智压力黑暗.md) |

### 系统功能

| 根指令 | 别名 | 权限 | 默认 | 说明 | 文档 |
|---|---|---|---|---|---|
| `chotentech-gun` | `cttgun`（亦可 `/ctt gun`） | `chotentech.command.gun` | — | 枪械系统调试 | [system/01](system/01-cttgun-枪械调试.md) |
| `drop` | — | `chotentech.command.drop` | 所有人 | 丢弃锁定（**功能已停用，见文档**） | [system/02](system/02-drop-丢弃锁定.md) |
| `hideout` | — | `chotentech.command.hideout` | — | 藏身处 | [system/03](system/03-hideout-藏身处.md) |
| `warehouse` | `wh` | `chotentech.command.warehouse` | — | 仓库 | [system/04](system/04-warehouse-仓库指令.md) |
| `whforce` | `whf` | `chotentech.command.whforce` | — | 强制仓库操作 | [system/04](system/04-warehouse-仓库指令.md) |
| `whmanage` | `whm` | `chotentech.command.whmanage` | — | 仓库管理 | [system/05](system/05-whmanage-仓库管理.md) |
| `backup` | `bak`、`玩家备份` | `chotentech.command.backup` | — | 玩家数据备份 | [system/06](system/06-backup-玩家备份.md) |
| `chotentech-station` | `cttstation`、`station` | 未声明（仅 `reload` 运行期校验 `chotentech.admin`） | — | 站点 | [system/07](system/07-station-站点指令.md) |
| `chotentech-coolnumber` | `cttcn`、`cn`、`coolnumber`、`coolnum` | `chotentech.command.coolnumber` | — | 酷数（**注册代码已注释，当前未启用**） | [system/09](system/09-coolnumber-酷数指令.md) |

## 通用约定

### 参数写法

| 写法 | 含义 |
|---|---|
| `<arg>` | 必填参数 |
| `[arg]` | 可选参数（指令树中显示为 `[<arg>]`） |
| `a\|b` | 二选一，字面量 |
| `/cmd sub` | `sub` 为字面量（`subCommand` / `literal`），必须原样输入 |
| 玩家参数 | Tab 补全为服务器在线玩家名 |

### 权限默认值

| `permissionDefault` | 含义 |
|---|---|
| `TRUE` | 所有玩家默认拥有 |
| `OP` | 仅 OP 默认拥有 |
| 未声明 | 沿用框架默认（通常等价于仅 OP / 需要显式授权） |

> 少数子指令在 `execute` 内部另行做 `sender.hasPermission(...)` 运行期校验（例如 `/ulsclient reload` 需要 `chotentech.uls.admin`、`/ulsmenu demo|test <玩家>` 同样需要 `chotentech.uls.admin`）。这类运行期权限已在各文档的「权限」一栏单列。

### 指令树自省

绝大多数根指令在不带参数执行时会打印自己的指令树（`createModernHelper()`），例如直接输入 `/cttadmin` 即可看到分层的用法列表，并按执行者权限过滤。可把它当作运行时权威帮助。

## 生成方式说明

本套文档由 `Bukkit/src/main/kotlin/` 下的 60 个含 `@CommandHeader` 的 Kotlin 源文件逐个解析生成：
- 根指令名 / 别名 / 权限取自 `@CommandHeader`
- 子指令、字面量、参数与 Tab 补全取自 `subCommand` / `literal` / `dynamic` / `player` / `suggestion`
- 行为说明取自各 `execute` 分支内的实际逻辑与提示消息

若源码变更，请同步更新对应文档。
