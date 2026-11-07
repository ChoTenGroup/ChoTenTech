# 枪械调试（/cttgun）

> **根指令**：`/chotentech-gun`（别名：`/cttgun`；同时被挂到主指令下，可用 `/ctt gun` 访问）
> **权限**：`chotentech.command.gun`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/feature/gun/GunDebugCommand.kt`

## 功能概述

枪械系统（gun / mag / ammo / addon / armor 五段）的**调试与排障**指令集，面向管理员，不属于玩家日常玩法。

指令注册方式有两条：

1. `GunDebugCommand` 对象自带 `@CommandHeader(name = "chotentech-gun", aliases = ["cttgun"])`，由 TabooLib 在 `LifeCycle.ENABLE` 阶段自动扫描注册，因此可直接 `/cttgun ...`；
2. `MainCommand.kt` 中写了 `@CommandBody(permission = "chotentech.command.gun", description = "枪械系统调试指令") val gun = GunDebugCommand`，所以 `/ctt gun ...` 也能到达同一棵指令树。

指令本身只做「查询 / 发物品 / 写调试 NBT / 迁移旧数据」，**不会重新加载配置**——物品配置改动后请执行 `/ctt reload` 让 `GunRegistry` 重建注册表。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/cttgun` | 无参数：打印指令树（`createModernHelper()`） | `chotentech.command.gun` |
| `/cttgun info` | 枪械注册表概览（总条目数与各段数量） | `chotentech.command.gun` |
| `/cttgun info <sertraline_id>` | 输出该 Sertraline ID 在 gun/mag/ammo/addon/armor 各段的解析结果 | `chotentech.command.gun` |
| `/cttgun give <sertraline_id>` | 用 Sertraline 构建 1 个该 ID 的物品给自己 | `chotentech.command.gun` |
| `/cttgun give <sertraline_id> <amount>` | 构建指定数量的该物品 | `chotentech.command.gun` |
| `/cttgun fill <ammo_id>` | 往手持枪械的 `magAmmo` 写入默认 8 发子弹 | `chotentech.command.gun` |
| `/cttgun fill <ammo_id> <count>` | 写入指定发数 | `chotentech.command.gun` |
| `/cttgun state` | 打印手持枪械的原始 NBT + 运行时状态 | `chotentech.command.gun` |
| `/cttgun reload` | **瞬间**完成一次标准换弹（跳过读条） | `chotentech.command.gun` |
| `/cttgun menu` | 打开手持物对应的菜单（枪 → `GunMenu`，弹匣 → `MagMenu`） | `chotentech.command.gun` |
| `/cttgun migrate`（等价 `migrate-crossbow`、`migratecrossbow`） | 旧弩弹匣 `cmag` → `sertraline_data.mag` 迁移，默认 dry-run | `chotentech.command.gun` |
| `/cttgun migrate <flag>` | 同上；`--apply` / `apply` 时真正写回 NBT | `chotentech.command.gun` |
| `/cttgun processors` | 打印物品段落处理器的注册状态（排障用） | `chotentech.command.gun` |
| `/cttgun armor` | 显示身上已登记的 ChoTenTech 护甲 | `chotentech.command.gun` |
| `/cttgun repairarmor` | 维修主手护甲（需耐久为 0） | `chotentech.command.gun` |

> 所有子指令的 `@CommandBody` 都显式写了 `permission = PERMISSION`（= `chotentech.command.gun`），与 header 权限一致。
> `main` 用的是无参 `@CommandBody`（继承 header 权限）。

## 指令详解

### `/cttgun`（无参数）

- **用途**：打印本指令的完整指令树，供管理员查阅。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：玩家 / 控制台均可（`createModernHelper()` 内部使用 `execute<ProxyCommandSender>`，并会按执行者权限过滤子节点）。
- **行为说明**：`main = mainCommand { createModernHelper() }`，输出 `Usage: /<command.name>` 起头的分层树状帮助。
- **示例**：`/ctt gun` → 聊天栏出现分层帮助。

### `/cttgun info`

- **用途**：查看枪械注册表是否加载成功，以及 gun/mag/ammo/addon/armor 各段条目数。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - 若 `GunRegistry.size() == 0`（五段条目数之和为 0），输出「枪械注册表 | 空」，并提示确认 Sertraline 已加载物品后执行 `/ctt reload` 重建注册表。
  - 否则逐行输出：总条目数、`gun`、`mag`、`ammo`、`addon`、`armor` 各段数量。
- **示例**：`/ctt gun info`

### `/cttgun info <sertraline_id>`

- **用途**：查看某个 Sertraline 物品 ID 在枪械注册表中被解析成了什么。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `sertraline_id` | Sertraline 物品 ID（**不是** Bukkit Material） | 必填 | `GunRegistry.allIds()`：五段中所有 ID 去重后排序 |

- **权限**：`chotentech.command.gun`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 逐段检查，同一 ID 可能命中多段，因此会**依次追加**各段输出而不是命中即返回：
    - `GUN` 段：口径、类别、枪型、是否全自动、射速（RPM 与换算的 tick/发）、弹速、精度、射程、枪管、机械效率、垂直/水平后坐、耐久、后坐曲线、水平抖动、模式列表、点射发数、配件槽、过热容量/每发/每 tick 冷却、卡壳率、各项 usage 时长（装填/快速装填/卸弹匣/拉栓/查膛/检视弹匣/压弹/退弹/修理/切模式/拔枪）。
    - `MAG` 段：口径、容量、装填速度、机械效率、检视精度、检视耗时、是否允许快速装填。
    - `AMMO` 段：口径、伤害、穿甲、破甲伤害、精度修正、后坐%、射速系数、卡壳率、暴击率、弹丸数。
    - `ADDON` 段：槽位、适配枪型、口径（空则显示「不限」）、属性加成表、消音、倍率、热成像。
    - `ARMOR` 段：防御、防护等级、耐久、移速修正、机械效率、材质、部位列表。
  - 五段都没命中 → 输出「未找到 `<id>` 的 gun / mag / ammo / addon / armor 配置」，并提示若刚改过物品配置先执行 `/ctt reload`。
- **示例**：`/ctt gun info ofn_pistol`

### `/cttgun give <sertraline_id> [amount]`

- **用途**：通过 Sertraline 实际构建一次物品并发给自己（等价于线上发一把枪/一个弹匣/一发子弹）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `sertraline_id` | Sertraline 物品 ID | 必填 | `GunRegistry.allIds()` |
  | `amount` | 数量（整数） | 可选，缺省 1 | 补全 `1` `8` `16` `32` `64` |

- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（`giveItem` 中 `sender as? Player` 为空时报「该指令只能由玩家执行（需要把物品放进你的背包）」）。
- **行为说明**：
  - `amount` 经 `toIntOrNull()` 解析，失败按 `-1` 处理 → 输出「数量必须是正整数」。
  - 实际上限为 `36 * 64`（2304），超过会被裁剪。
  - 调用 `Sertraline.api().buildItem(id, player, amount = count)`；异常 → 「构建物品失败: …」；返回 null → 「未能构建物品 `<id>`（Sertraline 里没有这个 ID，或该物品构建失败）」。
  - 构建后调用 `GunItemProcessors.probeBuilt(item)` 自检名称/描述是否真的落上；若未落上会额外警告「⚠ `<id>` 的段落处理器似乎没生效：…」，并提示执行 `/ctt gun processors` 查看注册状态，通常原因是 Sertraline 版本低于 3.13.0.2 或未重启服务端。
  - 物品优先放入背包；背包满时剩余部分 `dropItemNaturally` 掉落在脚下，并在成功消息后追加「（背包已满，多余掉落在地上）」。
  - 成功消息会标注该 ID 的归属类型：枪械 / 弹匣 / 子弹 / 配件 / 护甲 / 未登记枪械段的物品。
- **示例**：`/ctt gun give ofn_pistol 1`、`/ctt gun give ofn_pistol_mag 4`

### `/cttgun fill <ammo_id> [count]`

- **用途**：直接把子弹写进手持枪械的 `magAmmo`，绕过换弹流程手测弹道/伤害。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `ammo_id` | 子弹 ID，必须存在于 `GunRegistry.ammo` | 必填 | `GunRegistry.allIds()` |
  | `count` | 发数（整数） | 可选，缺省 **8** | 补全 `1` `8` `16` `30` |

- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（需要手持枪械）。
- **行为说明**：
  - `count` 解析失败 → `-1` → 「发数必须是正整数」。
  - 主手物品不是枪械 → 「主手不是枪械」。
  - `ammo_id` 不在 `GunRegistry.ammo` 中 → 「没有这个子弹 ID：…」，并建议先用 `/ctt gun info` 确认已加载的 ammo。
  - 先 `GunInstance.ensureDefaults(item, gun)` 补齐缺失的 NBT 字段，再 `GunInstance.setMagAmmo(item, ammoId, count)`；该函数内部把 count 限制在 `0..256`，返回值小于 0 表示物品缺少 `sertraline_data.gun` 段。
  - 成功后 `setItemInMainHand` 写回主手，并输出实际上填发数、弹匣内容条数、当前热量与耐久。
- **示例**：`/ctt gun fill ammo_9mm`（8 发）、`/ctt gun fill ammo_9mm 30`

### `/cttgun state`

- **用途**：一站式排障：一次打印手持枪械的原始 NBT、解析后的 `GunState`、玩家级 `PlayerGunState`，以及弹道/命中/输入等运行时计数器。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（需要手持枪械）。
- **行为说明**：主手不是枪械 → 「主手不是枪械」。输出内容依次为：
  - 物品段：若没有 `sertraline_data.gun` 段则提示「NBT 未初始化」；否则输出耐久（当前/上限）、热量与过热档位、卡壳、膛内发数、弹匣 ID、开火模式、弹匣内容列表、磨损。
  - 玩家状态：举枪（`drawing`/`drawTicks`）、已出枪过、ADS、连发序号、后坐 pitch/yaw/vel、`lastShotTick`、`jammedUntil`、`forcedStopUntil`。
  - 弹道链路计数：飞行中子弹数、发射、命中实体、命中方块、结算、生效、伤害为 0（哪一环为 0 就说明断在哪一环）。
  - 移速诊断：`MOVEMENT_SPEED` 的 base / 当前值，以及本系统 ADS 减速修饰符数量（注释说明**应恒为 0 或 1**，大于 1 表示历史遗留叠加）。
  - 上次开火结果、距上次开火毫秒数、当前射速间隔（tick）、世界 `fullTime`。
  - 输入事件计数：交互、丢弃、换手、切栏、攻击（全为 0 说明监听器未注册或事件被别的插件拦下）。
  - 连发序号、本发按曲线采样的垂直/水平倍率、曲线重置窗口毫秒。
  - 后坐曲线与水平抖动曲线全文（键 = 第几发）。
  - 手部体力：当前/上限、回复状态（等待 N 秒 / 进行中）、每秒回复量与倍率。
  - 最后一行：物品原始 NBT（`getItemTag().toJsonSimplified()`）。
- **示例**：`/ctt gun state`

### `/cttgun reload`

- **用途**：**调试专用**：立刻完成一次标准换弹，免去等待读条，方便反复手测「枪 ↔ 弹匣」的 NBT 往返。
- **参数**：无（**不接受**任何参数）。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（需要手持枪械）。
- **行为说明**：
  - 主手不是枪械 → 「主手不是枪械」。
  - 先 `GunAction.cancel(player)` 停掉可能正在跑的读条（避免它稍后再结算一次顶掉本次结果），再 `GunAction.reloadStandard(player, held, instant = true)`。
  - 完成后输出枪械 ID、弹匣 ID（空则「无」）、弹匣发数、膛内发数。
  - 注意：本指令**不会**重载配置，与 `/ctt reload` 完全不同。
- **示例**：`/ctt gun reload`

### `/cttgun menu`

- **用途**：打开手持物对应的操作菜单，等价于按 F，但无需真的按键。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（需要手持枪械或弹匣）。
- **行为说明**：持有枪械 → `GunMenu.open(player)`；持有弹匣 → `MagMenu.open(player)`；都不是 → 「主手既不是枪械也不是弹匣」。
- **示例**：`/ctt gun menu`

### `/cttgun migrate` / `/cttgun migrate-crossbow` / `/cttgun migratecrossbow`

- **用途**：把旧版弩弹匣物品上的 `cmag` 数据迁移到新体系 `sertraline_data.mag.ammo`。
- **命名说明**：Kotlin 属性名为 `migrate`，字面量因此是 `migrate`；`@CommandBody(permission = PERMISSION, aliases = ["migrate-crossbow", "migratecrossbow"])` 提供 kebab-case 写法（源码注释说明：Kotlin 属性名不能带 `-`）。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `flag` | 是否真正写回；`--apply` 或裸写 `apply`（大小写不敏感）都算真 | 可选，缺省为 dry-run | 补全 `--apply` |

- **权限**：`chotentech.command.gun`。
- **执行者要求**：玩家 / 控制台均可（扫描的是**全部在线玩家**，不是执行者自己）。
- **行为说明**：
  - 扫描范围（每个在线玩家）：主背包 0..35、盔甲槽 36..39（36 靴 / 37 裤 / 38 胸 / 39 头）、副手、末影箱全部格子。
  - 读取旧键 `cmag`，兼容三种历史形状：`cmag = "a,b,c"`（字符串）、`cmag.filled = "a,b,c"`（复合）、`cmag = ["a","b","c"]`（列表）。没有 `cmag` 的物品跳过。
  - 逐个校验 ID：在 `GunRegistry.ammo` 中存在才保留，否则计入「丢弃」，并在输出中列出前 5 条明细。
  - **顺序会整表反转**：旧体系「头部 = 下一发」，新体系「末位 = 下一发」，因此迁移结果为 `kept.asReversed()`（不是只把首发挪到末尾）。
  - dry-run（不加 flag）：只统计与打印，**不碰任何物品**。
  - `--apply`：删除 `cmag`，写入 `sertraline_data.mag.v = 1` 与 `sertraline_data.mag.ammo = 迁移后列表`，写回具体容器槽位，并在有改动时 `updateInventory()`。不会覆盖同物品上的 `sertraline_data.gun` / `.model` 等其它数据。
  - 输出统计：扫描到的旧弹匣数、可迁移发数、丢弃发数、写回成功/失败物品数。
  - 在线玩家为空时输出「当前没有在线玩家可以扫描」。
- **示例**：`/ctt gun migrate`（先看统计）→ `/ctt gun migrate --apply`（真正写回）

### `/cttgun processors`

- **用途**：排障「物品没有名称 / 描述 / 模型」，检查 Sertraline 的物品段落处理器是否真的注册上了。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：输出
  - Sertraline 插件版本与 API 版本；
  - 外部登记表（`Sertraline.api().registeredItemProcessors()`）；
  - live 处理器表（`Sertraline.api().itemManager().listProcessors()`）；
  - 枪械菜单形态：对话框（Dialog）或箱子界面（老服务端回退）；
  - 6 个段落是否全部挂在 live 表上（未挂上会明确列出）；
  - 现场用 `GunItemProcessors.probe("ofn_pistol")` 构建一次示例枪的探测结果。
- **示例**：`/ctt gun processors`

### `/cttgun armor`

- **用途**：列出玩家身上已登记的 ChoTenTech 护甲及其属性。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**（控制台执行报「该指令只能由玩家执行」）。
- **行为说明**：`ArmorManager.pieces(player)` 为空 → 「没有装备已登记的 ChoTenTech 护甲」。否则逐件输出：槽位、护甲 ID、防御、防护等级、当前/最大耐久、覆盖部位（HEAD/BODY/STOMACH/LEGS/ARMS 中实际覆盖者）、材质名。**只读**。
- **示例**：`/ctt gun armor`

### `/cttgun repairarmor`

- **用途**：维修主手那件已登记的护甲。
- **参数**：无。
- **权限**：`chotentech.command.gun`。
- **执行者要求**：**仅玩家**。
- **行为说明**（顺序校验）：
  1. 非玩家 → 「该指令只能由玩家执行」；
  2. 主手不是已登记的护甲 → 「主手不是已登记的护甲」；
  3. 当前耐久 > 0 → 「只有耐久为 0 的护甲才能维修」（即**必须先打坏**）；
  4. 从 `ArmorManager.material(data.material).repairMaterial` 取维修材料，转 `Material` 失败 → 「无效的维修材料：…」；
  5. 背包内该材料不足 2 个 → 「缺少维修材料：`<材料>` ×2」；
  6. 扣除 2 个材料后调用 `ArmorManager.repair(held)`：失败则**返还材料**并提示「维修失败，材料已返还」；
  7. 成功则写回主手，提示「护甲已修至 50%：`<当前>`/`<上限>`，消耗 `<材料>` ×2」。
- **示例**：`/ctt gun repairarmor`

## 备注 / 注意事项

- **不会重载配置**：`/cttgun reload` 是「瞬间换弹」，不是重载。改物品配置后一直要执行 `/ctt reload` 才能让 `GunRegistry` 重建（`info` / `give` / `fill` 的提示消息里反复强调这一点）。
- **`/ctt gun` 与 `/cttgun` 是同一对象**：两者权限、子指令完全一致；`/ctt gun` 那条路径上权限由 `MainCommand` 的 `@CommandBody(permission = "chotentech.command.gun")` 再声明一次。
- **`give` / `fill` / `state` / `reload` / `menu` / `armor` / `repairarmor` 都要求玩家**：源码里是运行期 `sender as? Player` 判断，控制台执行会收到红字提示而不是框架报错。
- **`migrate` 是全局操作**：作用于所有在线玩家；默认 dry-run，务必先不加 `--apply` 看统计。离线玩家不在扫描范围内。
- **`fill` 的 count 上限**：`GunInstance.setMagAmmo` 将发数限制在 `0..256`，命令层不做额外提示，实际写入数会以返回值为准。
- **`give` 的数量上限**：`36 * 64 = 2304`，超出被静默裁剪。
- **自动补全不区分段落**：`sertraline_id` / `ammo_id` 的建议均来自 `GunRegistry.allIds()`（五段合一），所以 `fill` 的补全里也会出现枪械 ID，输入错误时由运行期校验兜底。
- **`repairarmor` 只修到 50%**：成功消息明确写「护甲已修至 50%」，且要求维修前耐久为 0。
- 补全列表来自 `GunRegistry`，注册表为空（未 `/ctt reload`）时补全与校验都会失败。
