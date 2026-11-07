# CTTAdmin 管理指令

> **根指令**：`/chotentech-admin`（别名：`/cttadmin`）
> **权限**：`chotentech.command.admin`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/CttAdminCommand.kt`

## 功能概述

`CttAdminCommand` 是 ChoTenTech 的运维/管理后台，覆盖六大类操作：存档点（`saves.yml`）的创建与维护、玩家称号的强制设置、倒地救援、档案数据迁移与补偿、世界生成 demo 预览，以及 Token（货币）与订阅的发放/扣除/查询。

它大量直接操作数据层：`SavePointConfig` / `SavePointManager`（存档点与全息图）、`ProfileManager`（档案库）、`CurrencyManager` / `playerDataMap`（货币）、`SubscriptionManager`（订阅）。因此它是**高危指令**，默认仅 OP 可用；无权限时不输出提示（`permissionMessage = ""`），`newParser = false`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttadmin` | 无参数：输出用法树（`createModernHelper`） | `chotentech.command.admin` |
| `/cttadmin feature createspawn <name> <type>` | 以玩家当前位置创建存档点并写入 `saves.yml` | `chotentech.command.admin` |
| `/cttadmin feature fixsavelocation` | 批量检查并抬升存档区域高度，修复被地形掩埋的存档点 | `chotentech.command.admin` |
| `/cttadmin feature tpsave <saveId>` | 传送到指定存档点 | `chotentech.command.admin` |
| `/cttadmin feature movesavetohere <saveId>` | 把指定存档点整体移动到玩家当前位置 | `chotentech.command.admin` |
| `/cttadmin feature migrateids` | 批量迁移所有档案中失效的存档点 ID | `chotentech.command.admin` |
| `/cttadmin feature revive <target>` | 将倒地玩家拉起 | `chotentech.command.admin` |
| `/cttadmin feature rank <type> set <player> <rankId>` | 强制设置玩家某类型称号 | `chotentech.command.admin` |
| `/cttadmin feature rank <type> clear <player>` | 清除玩家某类型称号 | `chotentech.command.admin` |
| `/cttadmin manage setspawn <target> <saveId>` | 把某存档点绑定为目标的基地/普通存档点 | `chotentech.command.admin` |
| `/cttadmin migrate exprefill` | 按已完成任务数给所有档案补偿 main 经验 | `chotentech.command.admin` |
| `/cttadmin debug worldgen <type>` | 在玩家脚下生成世界生成 demo 结构 | `chotentech.command.admin` |
| `/cttadmin token give <target> <type> <amount>` | 给予玩家货币 | `chotentech.command.admin` |
| `/cttadmin token take <target> <type> <amount>` | 扣除玩家货币 | `chotentech.command.admin` |
| `/cttadmin token info <target>` | 查看玩家货币余额 | `chotentech.command.admin` |
| `/cttadmin subscription give <target> <id> <duration>` | 开通/续费订阅 | `chotentech.command.admin` |
| `/cttadmin subscription take <target> <id>` | 移除订阅 | `chotentech.command.admin` |
| `/cttadmin subscription info <target>` | 查看玩家订阅详情 | `chotentech.command.admin` |

## 指令详解

### `/cttadmin`
- **用途**：输出用法树。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：调用 `createModernHelper()`（定义见 `MainCommand.kt`），首行为 `Usage: /chotentech-admin`，随后按权限树形列出 `feature` / `manage` / `migrate` / `debug` / `token` / `subscription` 等节点。纯展示，无副作用。
- **示例**：`/cttadmin` → 打印用法树。

### `/cttadmin feature createspawn <name> <type>`
- **用途**：在玩家当前位置创建一个存档点，并直接写入插件目录的 `saves.yml`。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `name` | 存档点键名；同时被写入 `name` 字段（即显示名） | 是 | 动态参数，无补全 |
  | `type` | 存档点类型 | 是 | `NORMAL`、`BASE`（补全提示为此两项；实际解析为 `typeStr.uppercase()` 后 `SavePointType.valueOf`，因此小写 `normal`/`base` 也可） |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家（`sender as? Player` 为 null 时回 `该指令只能由玩家执行`）
- **行为说明**：
  1. 类型解析失败 → `存档点类型必须是 NORMAL 或 BASE`；
  2. `name.trim()` 为空 → `存档点名称不能为空`；已存在同 ID → `存档点 <key> 已存在`；
  3. 自动探测层级 `levelId`：先取 `playerRegions[玩家UUID]` 中的区域，命中 `levelsList[区域]?.id`，或匹配 `levelsList` 中 `region?.id == 区域` 的层级；兜底用玩家数据的 `activeLevelStr`；都取不到 → `无法自动探测当前层级(不在任何层级区域内且无激活层级)`；
  4. 以玩家位置为最低点生成 5×3×5 区域：
     - `pos` = `"<world> <bx-2> <by> <bz-2> <bx+2> <by+2> <bz+2>"`（x/z 各 ±2、y 为 `by..by+2`）；
     - `holoPos` = `"<world> <bx> <by+3> <bz>"`（玩家上方 3 格）；
     - `respawn` = `"<world> <bx> <by> <bz>"`（玩家脚下）；
  5. `BASE` 类型时，若玩家所在区域 ID 以 `base-` 开头且 `BaseManager.getBaseByRegion(region)` 命中，则额外写入 `base = <基地 idef>`；否则不写 `base`；
  6. 写入 `saves.yml` 的键：`<key>.level`、`.pos`、`.holoPos`、`.respawn`、`.name`、`.type`（`NORMAL`/`BASE`），可选 `.base`；写盘失败 → `写入 saves.yml 失败: <原因>`；
  7. 成功后执行 `SavePointConfig.load()` 与 `SavePointManager.createHolograms()`（重新加载并重建全息图），并发送形如 `<基地|普通>存档点 <name> 创建成功: 层级 <levelId> @ <world> bx,by,bz (基地 <idef>)` 的消息。
- **示例**：`/cttadmin feature createspawn base-a1 BASE` → 在脚下生成 5×3×5 存档区域，`saves.yml` 新增 `base-a1` 段（`type: BASE`，若身处 `base-…` 区域则带 `base:`），并立即出现全息图。

### `/cttadmin feature fixsavelocation`
- **用途**：批量诊断并修复「存档区域被地形掩埋」的存档点。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家
- **行为说明**：
  1. 在 `submitAsync` 异步任务里遍历 `SavePointConfig.getAll()`；没有存档点 → `没有存档点可修复`；
  2. 世界不存在则计入失败（`世界 <world> 不存在,跳过`）；
  3. 逐格抬高 `minY`/`maxY`，直到「最下方一层中至少存在一个空气或光源方块」（判定集合 `freeSaveLayerBlocks`：空气类、`LIGHT`、荧石/海晶灯/菌光体、南瓜灯、红石灯、岩浆块、末地烛、各类火把、灯笼、营火、发光地衣/浆果等）。**只要有一个可站立空隙即视为未掩埋**，避免整层被抬高到屋顶；
  4. 抬升上限：最多 256 次且 `maxY < world.maxHeight`；区块异步加载失败按「不可用」处理继续抬升；最终仍找不到 → 计入失败并报告「抬升 N 格后仍未找到可用空间」；
  5. 报告三类结果：`无需修复`、`已修复: 最低点 oldMinY→minY, 最高点 oldMaxY→maxY, 抬升 N 格`、`修复失败…`；
  6. 若有修复项，回到主线程逐条 `writeBackSavePos`（**只写回 `pos`，按 `deltaY` 平移 pos1/pos2**），随后 `SavePointConfig.load()` + `SavePointManager.createHolograms()`，并在报告末尾追加 `(已重载存档点配置并刷新全息图)`；
  7. 最终把整份报告（多行字符串）一次性发给执行者。
- **示例**：`/cttadmin feature fixsavelocation` → 输出 `■ 存档点位置修复报告:` 及每个存档点的诊断行与统计行 `共 N 个修复, M 个无需修复, K 个失败`。

### `/cttadmin feature tpsave <saveId>`
- **用途**：把执行者传送到指定存档点。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `saveId` | 存档点 ID（`saves.yml` 的键） | 是 | 补全为 `SavePointConfig.getAll()` 的全部 `id` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家
- **行为说明**：ID 不存在 → `存档点 <saveId> 不存在`；世界未加载 → `存档点 <name> 的世界 <world> 不存在`；否则取 `respawnPos`（若有），否则用 `effectiveHoloPos`（配置的 `holoPos`，缺失时由区域中心 +1y 计算），通过 `teleportAsync` 传送，失败时发 `传送失败: <原因>`；成功后发 `已传送到存档点 <name> (<saveId>)`。
- **示例**：`/cttadmin feature tpsave level1-entry` → 立即传送到该存档点的复活坐标。

### `/cttadmin feature movesavetohere <saveId>`
- **用途**：把已有存档点的区域整体挪到执行者当前位置，并同步平移全息图与复活点。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `saveId` | 存档点 ID | 是 | 补全为 `SavePointConfig.getAll()` 的全部 `id` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家
- **行为说明**：
  1. ID 不存在 → `存档点 <saveId> 不存在`；
  2. 以玩家位置（`Math.round` 后取整）为最下层中心重建区域：`pos = "<world> <bx-2> <by> <bz-2> <bx+2> <by+4> <bz+2>"`；
  3. 计算位移：`dx = bx - 旧中心x`、`dy = by + 1 - 旧中心y`、`dz = bz - 旧中心z`（旧中心 y 取 pos1/pos2 的 min/max 平均）；
  4. 若存在 `holoPos` / `respawn` 字符串，用 `shiftCoordString` 平移其前三个坐标并把世界名替换为当前世界（输出只保留 `world x y z` 四段）；
  5. 重新探测层级（同 `createspawn` 逻辑），探测到则更新 `<saveId>.level`，探测不到则保留原值；
  6. 写盘失败 → `写入 saves.yml 失败: <原因>` 并中止；成功后 `SavePointConfig.load()` + `SavePointManager.createHolograms()`；
  7. 发送两行消息：`存档点 <name> 已移动: 区域 <world> (<bx-2>..<bx+2>, <by>..<by+2>, <bz-2>..<bz+2>)` 与 `位移: x <dx>, y <dy>, z <dz>;层级 <levelId|保留原值(未探测到层级区域)>`。
- **示例**：`/cttadmin feature movesavetohere level1-entry` → 存档区域、全息图、复活点一起搬到脚下，报告位移量与层级。

### `/cttadmin feature migrateids`
- **用途**：批量把档案里失效的旧存档点 ID 迁移为新 ID（旧中文 ID 按 `name` 匹配新英文 ID）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家
- **行为说明**：
  1. 异步 `ProfileManager.profileMapper.findAll { }` 读取**全部**档案行（含归档）；读取失败 → `读取档案数据失败`；
  2. 每行 JSON 反序列化为 `PlayerData` 并 `normalize()`，调用 `SaveIdMigrator.convert(data)`：返回 true 表示 `baseSave`/`normalSave` 有变更，则回写该行并计数；
  3. 回主线程拼装报告 `■ 存档点 ID 迁移报告:`：无变更时 `没有需要迁移的档案(所有存档点 ID 均已有效)`，否则列出前 30 条明细（`uuid / profileName : 基地 旧→新, 普通 旧→新`），超出部分提示 `... 其余 N 个省略`；
  4. 更新单行失败只写 `devLog`，不打断整批。
- **示例**：`/cttadmin feature migrateids` → 输出迁移报告与总数。

### `/cttadmin feature revive <target>`
- **用途**：把处于倒地（bleedout）状态的玩家拉起。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 补全为**当前在线且处于倒地状态**的玩家名（`BleedoutManager.isDowned`） |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：目标解析失败 → `目标玩家不在线`；`BleedoutManager.reviveDowned(uuid)` 返回 false → `<name> 当前没有处于倒地状态`；成功 → `已将 <name> 拉起`。
- **示例**：`/cttadmin feature revive Steve` → Steve 从倒地状态恢复。

### `/cttadmin feature rank <type> set <player> <rankId>`
- **用途**：直接把某类型称号强制写进玩家档案并立即刷新玩家列表名。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `type` | 称号类型（`RankManager.ranks` 的键） | 是 | 补全为 `RankManager.getTypes()`（即 `RankManager.ranks.keys`） |
  | `player` | 目标玩家（在线） | 是 | 在线玩家名（`player(...)` 组件） |
  | `rankId` | 该类型下的称号 ID | 是 | 补全为 `RankManager.ranks[type]` 的键；`type` 未填时为空列表 |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：未知类型 → `未知称号类型 <type> (可用: …)`；称号不存在于该类型 → `称号 <rankId> 不存在于 <type>`；成功则写 `ProfileManager.getSharedData(uuid).activeRanks[type] = rankId`，`ProfileManager.saveSharedData(uuid)` 持久化，并调用 `playerListName(RankManager.buildPlayerListName(target))` 刷新列表名，最后发 `已设置 <name> 的<类型中文>称号为 <rankId>`。类型中文名映射见 `rankTypeNameMap`：`prefix`→前缀、`suffix`→后缀、`chatsuffix`→聊天后缀、`chatcolor`→聊天颜色、`namecolor`→名字颜色、`joinmessage`→进服消息、`quitmessage`→退服消息；未收录的类型直接显示原文。
- **示例**：`/cttadmin feature rank prefix set Steve vip_prefix` → Steve 获得 `vip_prefix` 前缀，列表名立即刷新。

### `/cttadmin feature rank <type> clear <player>`
- **用途**：清除玩家某类型的称号。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `type` | 称号类型 | 是 | 补全为 `RankManager.getTypes()` |
  | `player` | 目标玩家（在线） | 是 | 在线玩家名 |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：未知类型 → `未知称号类型 <type>`；否则 `activeRanks.remove(type)` → `saveSharedData` → 刷新列表名 → `已清除 <name> 的<类型中文>称号`。
- **示例**：`/cttadmin feature rank prefix clear Steve` → Steve 的前缀被清空。

### `/cttadmin manage setspawn <target> <saveId>`
- **用途**：把某个存档点绑定为目标的「基地存档点」或「普通存档点」。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名 |
  | `saveId` | 存档点 ID | 是 | 补全为 `SavePointConfig.getAll()` 的全部 `id` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：目标解析失败 → `目标玩家不在线`；ID 不存在 → `存档点 <saveId> 不存在`；`playerDataMap` 无该玩家数据 → `目标玩家数据未加载`。按存档点类型自动分流：`sp.isBase == true` 写 `data.baseSave = saveId`，否则写 `data.normalSave = saveId`。操作者收到 `已将 <name> 的<基地|普通>存档点设为 <sp.name> (<saveId>)`，目标玩家收到 `管理员已将你的<基地|普通>存档点设为 <sp.name>`。
- **示例**：`/cttadmin manage setspawn Steve base-a1` → Steve 的 `baseSave` 变为 `base-a1`。

### `/cttadmin migrate exprefill`
- **用途**：一次性的历史数据补偿——按 Chemdah 已完成任务数给所有档案补 main 经验。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | — | 无参数 | — | — |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：
  1. `submitAsync` 异步执行；`levels["main"]` 不存在 → `未找到 main 等级配置,无法补偿`；
  2. 只遍历 `archived` 为 0（未归档）的档案；读取失败 → `读取档案数据失败`；
  3. 统计 `chemdahProfileData` JSON 中以 `quest.complete.` 开头的键数量作为已完成任务数，每个任务补 **1000** main 经验，写入 `quetiapineData["exp.main"]`（原值 + 补偿，字符串存双精度）；
  4. 在线玩家：直接改内存数据（`playerDataMap`，由自动保存持久化），并立即 `submit { PlayerDataManager(p).checkUpdateLevel("main") }` 让等级追上；
  5. 离线玩家：改数据库档案行并 `profileMapper.update(row)`，登录后由周期 `checkUpdateLevel` 任务追上；
  6. 回主线程发送报告 `■ 任务经验补偿报告: N 个档案, M 个任务, 共补偿 X main经验`，明细最多 30 条，超出显示 `... 其余 N 个省略`；无任务时提示 `没有档案包含已完成任务(quest.complete.*)`。
- **示例**：`/cttadmin migrate exprefill` → 所有人的 main 经验按其完成任务数 ×1000 增加。

### `/cttadmin debug worldgen <type>`
- **用途**：在玩家脚下预览世界生成器的 demo 结构。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `type` | demo 类型（大小写不敏感） | 是 | `corridor`、`aquila`、`gild`、`gothic`、`fabled`、`walls`、`ouroboros`、`room`、`terrain`（补全为 `WorldGenDemo.availableTypes`） |

- **权限**：`chotentech.command.admin`
- **执行者要求**：仅玩家
- **行为说明**：入参 `lowercase()` 后校验是否在 `WorldGenDemo.availableTypes` 中，不在则报 `未知的 demo 类型 <输入> (可用: …)`。以 `loc.blockX, loc.blockY - 1, loc.blockZ` 为基点调用 `WorldGenDemo.generateDemo`（x/z 会按区块对齐，y=0 映射到 baseY，覆盖生成器完整高度），把返回的结果字符串发回玩家。各类型占据的区块尺寸不同（如 `corridor`/`walls`/`terrain` 为 1×1，`gild` 为 8×4，`ouroboros` 为 2×8）。`generateDemo` 内部要求主线程，非主线程会返回 `世界生成 demo 必须在服务器主线程执行`。
- **示例**：`/cttadmin debug worldgen corridor` → 脚下生成一段走廊 demo。

### `/cttadmin token give <target> <type> <amount>`
- **用途**：给玩家发放 Token/货币。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名 |
  | `type` | 货币类型（经 `normalizeTokenType` 归一化） | 是 | 补全提示 `ltoken`、`ptoken`、`token`；实际接受 `l`/`ltoken`/`free`/`lt`→`ltoken`，`p`/`ptoken`/`paid`/`pt`→`ptoken`，`token`/`tokens`→通用 |
  | `amount` | 数量，必须为正整数 | 是 | 补全提示 `100`、`1000`、`10000`、`50000`、`100000` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：目标解析失败 → `目标玩家不在线`；类型非法 → `未知货币 <输入> (可用: ltoken, ptoken, token)`；`amount` 非数字或 ≤0 → `数量必须是正整数`。发放时 `token`（通用）**只加到免费侧 `ltoken`**（源码注释：与扣费顺序相反，发放入免费侧更符合「免费优先」的收益预期），随后 `CurrencyManager.addBalance(target, currency, amount)`。消息：`已给予 <name> <amount> <货币显示名>`，目标收到 `管理员发放了 <amount> <货币显示名>`。货币显示名来自 `CurrencyManager.displayNames`：`ltoken`→免费Token、`ptoken`→付费Token、`money`→MEG积分、`point`→BNTG贸易点、`tape`→磁带。
- **示例**：`/cttadmin token give Steve ltoken 1000` → Steve 免费 Token +1000。

### `/cttadmin token take <target> <type> <amount>`
- **用途**：扣除玩家 Token/货币。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名 |
  | `type` | 货币类型 | 是 | 同 `token give`（`ltoken`/`ptoken`/`token` 及其别名） |
  | `amount` | 数量，必须为正整数 | 是 | `100`、`1000`、`10000`、`50000`、`100000` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：`token`（通用）走 `CurrencyManager.removeToken`：**优先扣免费 `ltoken`，不足部分再扣付费 `ptoken`**，合计不足则整体失败且不扣任何余额；`ltoken`/`ptoken` 走 `CurrencyManager.removeBalance(target, type, amount)`（余额不足返回 false）。失败 → `<name> 的 <货币> 不足`；成功 → `已扣除 <name> <amount> <货币>`，目标收到 `管理员扣除了你 <amount> <货币>`。
- **示例**：`/cttadmin token take Steve token 500` → 先扣免费 Token，不够再扣付费 Token。

### `/cttadmin token info <target>`
- **用途**：查看玩家三种 Token/磁带余额。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名（`Bukkit.getOnlinePlayers()`） |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：目标解析失败 → `目标玩家不在线`；否则单行输出 `<name> | 免费Token: <ltoken> | 付费Token: <ptoken> | 磁带: <tape>`（`CurrencyManager.getBalance`，只读）。
- **示例**：`/cttadmin token info Steve` → `Steve | 免费Token: 500 | 付费Token: 1200 | 磁带: 3`

### `/cttadmin subscription give <target> <id> <duration>`
- **用途**：为玩家开通或续费订阅。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名 |
  | `id` | 订阅 ID | 是 | 补全为 `SubscriptionManager.ids()` |
  | `duration` | 时长，形如 `30d` / `12h`（支持 `s`/`m`/`h`/`d`） | 是 | 补全为 `SubscriptionManager.get(id)?.pricing` 的键；id 未填/未知时回退 `30d`、`90d`、`360d` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：目标解析失败 → `目标玩家不在线`；未知订阅 → `未知订阅 <id> (可用: …)`；`ShopManager.parseDuration` 解析（正则 `(\d+)([smhd])`，取字符串中第一个匹配）结果 ≤0 → `时长格式不正确 <输入> (形如 30d / 12h)`；否则 `SubscriptionManager.grant(target, def.id, durationMs)` 累加时长，并回 `已为 <name> 开通/续费 <显示名> (+<格式化时长>)`。
- **示例**：`/cttadmin subscription give Steve vip 30d` → Steve 的 vip 订阅 +30 天。

### `/cttadmin subscription take <target> <id>`
- **用途**：移除玩家的某个订阅。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名 |
  | `id` | 订阅 ID | 是 | 补全为 `SubscriptionManager.ids()` |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：`SubscriptionManager.removeSubscription(target, id)` 失败 → `<name> 没有订阅 <id>`；成功 → `已移除 <name> 的订阅 <id>`。
- **示例**：`/cttadmin subscription take Steve vip` → 移除 Steve 的 vip 订阅。

### `/cttadmin subscription info <target>`
- **用途**：查看玩家所有订阅的状态、到期时间与额度池。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `target` | 目标玩家（在线） | 是 | 在线玩家名（`Bukkit.getOnlinePlayers()`） |

- **权限**：`chotentech.command.admin`
- **执行者要求**：玩家 / 控制台均可
- **行为说明**：无订阅 → `<name> 当前没有任何订阅`；否则先输出 `■ <name> 的订阅 (N)`，每条一行：显示名 | `生效中`/`已过期`（`data.isActive(now)`）| `到期: <formatTime>` | `6h池: <sixHourRemaining>/<def.sixHourBonus>` | `周池: <weekRemaining>/<def.weekBonus>`。只读。
- **示例**：`/cttadmin subscription info Steve` → 列出 vip 等订阅明细。

## 备注 / 注意事项

- **权限**：所有子节点都只声明了根权限 `chotentech.command.admin`（默认 OP、`permissionMessage = ""`），源码中**没有** `sender.hasPermission(...)` 的运行时细粒度校验；所有子指令对任何持有 `chotentech.command.admin` 的人完全开放。
- **仅玩家的分支**：`createspawn`、`fixsavelocation`、`tpsave`、`movesavetohere`、`migrateids`、`debug worldgen` 通过 `sender as? Player` 判空拒绝控制台，提示统一为 `该指令只能由玩家执行`。`revive`、`rank`、`setspawn`、`token`、`subscription`、`migrate exprefill` 控制台可用。
- **`movesavetohere` 与 `createspawn` 的区域高度不一致**：`createspawn` 写 `by..by+2`（3 格高），`movesavetohere` 写 `by..by+4`（5 格高），但 `movesavetohere` 的源码注释写的是「y 向上 +2（与 createspawn 布局一致）」，且其回执消息里显示的范围也是 `by..by+2`。**以实际写入 `saves.yml` 的 `by..by+4` 为准**，这是源码自身的注释/消息与实现不同步。
- **`fixsavelocation` 只修 `pos`**：`writeBackSavePos` 仅平移 `pos`（pos1/pos2），**不会**同步平移 `holoPos` 与 `respawn`，因此修复后的存档点全息图/复活点可能仍停在掩埋高度，必要时配合 `movesavetohere` 重摆。
- **`fixsavelocation` 的线程约束**：遍历/读方块在 `submitAsync` 中执行，写回 `saves.yml`、`SavePointConfig.load()`、`SavePointManager.createHolograms()` 全部切回主线程；源码注释说明「`SavePointConfig` 是普通 Map（主线程每秒在迭代），与 `createSpawn` 的 `saves.yml` 写入要串行，否则并发写文件会互相覆盖丢键」。执行期间**避免同时**使用 `createspawn` / `movesavetohere`。
- **`createspawn` 的坑**：`name` 同时用作存档点 ID 与显示名，且已存在同 ID 会被拒绝；ID 一旦确定即成为 `saves.yml` 的键（后续 `tpsave`/`setspawn`/`movesavetohere` 都用它）。层级探测完全依赖 `playerRegions` / `levelsList` / `activeLevelStr`，若玩家既不在任何层级区域内、数据也没有激活层级则无法创建。
- **`createspawn` 的基地关联**：只有当玩家所在 WorldGuard 区域 ID 以 `base-` 开头、且能被 `BaseManager.getBaseByRegion` 匹配时才会写 `base:` 字段；否则 `BASE` 类型存档点也不会带基地归属。
- **`rank` 的条件校验**：源码注释明确「管理直接写入，不校验条件（60 秒条件检测仍会撤下不合格称号）」，所以强制设置的称号可能在下一轮条件检测中被收回。
- **`migrateids` / `exprefill` 是异步批处理**：均使用 `submitAsync` 遍历整库，`exprefill` 只处理 `archived = 0` 的档案；大批量数据时建议低峰执行。`exprefill` 依赖 `levels["main"]` 与 Chemdah 的 `chemdahProfileData`（`quest.complete.*` 键），每个已完成任务固定 1000 经验，重复执行会重复补偿（无幂等标记）。
- **`token` 的边界**：`normalizeTokenType` **不接受** `tape`/`money`/`point`，所以「磁带」只能在 `token info` 里查看，无法通过本指令增减；`token give` 的 `token` 只写 `ltoken`，而 `token take` 的 `token` 先扣 `ltoken` 再扣 `ptoken`，两者语义不对称。`target` 必须是**在线**玩家（依赖 `context.player(...).castSafely<Player>()` 且余额来自 `playerDataMap` 内存数据）。
- **依赖的文件/配置**：`saves.yml`（存档点，读写）、档案库（PTC 表，读写）、`subscriptions/` 与 `SubscriptionManager`（订阅定义）、`levelsList`/`levels`（层级、main 等级）、`WorldGenDemo`（世界生成 demo）。全量重载请用 `/ctt reload`。
