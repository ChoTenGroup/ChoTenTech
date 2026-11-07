# ChoTenAttribute Debug 属性调试

> **根指令**：`/chotenattribute-debug`（别名：`/attributedebug`, `/atbdebug`）
> **权限**：`chotenattribute.command.debug`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenAttributeDebugCommand.kt`

## 功能概述

`/chotenattribute-debug` 用于查看玩家当前**最终属性值**（`ChoTenAttributeAPI.instance.getAllAttributes(target)` 的返回值），并按硬编码的属性分类分组展示，方便排查属性计算问题。

它同时以对象嵌套方式挂在 `/chotenattribute debug` 下（见 `01-chotenattribute-属性主指令.md`），两条路径等价。`@CommandHeader` 中 `newParser = true`（与同族其它指令的 `false` 不同）。

本文件内维护了一张**硬编码的分类表**（`CATEGORY_NAMES` / `CATEGORY_IDS`），分类与属性 ID 的对应关系见下文的"分类与属性 ID 对照"。注意这张表是**只读展示用映射**，实际可用于 `add`/`set` 的属性 ID 以 `AttributeManager.attributeRegistry` 为准。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotenattribute-debug` | 无参数主体，打印指令帮助树 | `chotenattribute.command.debug` |
| `/chotenattribute-debug show` | 以自己为目标，按分类展示全部属性值 | `chotenattribute.command.debug` |
| `/chotenattribute-debug show <category>` | 以自己为目标，展示指定分类（或 `all`）的属性值 | `chotenattribute.command.debug` |
| `/chotenattribute-debug showplayer <player>` | 以指定玩家为目标，按分类展示全部属性值 | `chotenattribute.command.debug` |
| `/chotenattribute-debug categories` | 列出所有属性分类及每类中的属性数量 | `chotenattribute.command.debug` |

> 各 `@CommandBody` 未单独声明 `permission`，统一受根权限 `chotenattribute.command.debug`（默认 OP）约束；`execute` 内无额外 `hasPermission` 检查。

## 指令详解

### `/chotenattribute-debug`
- **用途**：打印指令帮助树。
- **参数**：无。
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`mainCommand { createModernHelper() }`）。
- **行为说明**：调用 `createModernHelper()` 输出树形帮助，补全提示中的 `category` 参数会被列为可选分支之一。
- **示例**：`/atbdebug`

### `/chotenattribute-debug show`
- **用途**：查看**自己**的全部最终属性值，按分类分组输出。
- **参数**：无（不传 `category` 时 `category == null`，等价于 `all`）。
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：**仅玩家**（`execute<Player>` 且使用 `player` 本身作为目标；控制台执行会因 sender 转换失败而无法执行）。
- **行为说明**：
  - 等价于 `showGrouped(player, player, null)`。
  - `category` 为 `null` 或 `"all"` 时：把 `getAllAttributes(target)` 的结果按 `getCategory(key)` 分组，然后**按 `CATEGORY_NAMES` 的声明顺序**逐类输出（顺序：`basic` → `combat` → `defense` → `block` → `ranged` → `magic` → `fire` → `element` → `body` → `hunger` → `regen`，最后不含在该表中的属性归入 `other`）；没有属性的分类跳过。
  - 每个分类先输出表头 `Command_Debug_Show_Category_Header`（"--- {0} ({1}) ---"，`{0}` = 中文分类名，`{1}` = 该分类条数），随后按属性 ID 字典序（`entries.toSortedMap()`）逐条输出 `<#A9B4C3>  {显示名}<#A9B4C3>: <#F2F5FA>{值}</#F2F5FA>`。显示名由 `AttributeManager.getAttributeDisplayName(sender, id)` 取语言键 `Attribute_<id>_Name`，未注册时回退为原始 ID。
  - 若 `getAllAttributes` 返回空（或抛异常被捕获为空 map），输出 `Command_Debug_Show_EmptyAll`（"该玩家无任何属性数据"）。
- **示例**：`/atbdebug show`

### `/chotenattribute-debug show <category>`
- **用途**：查看**自己**指定分类的最终属性值。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `category` | 属性分类 key（`dynamic("category")`） | 可选 | `basic`, `combat`, `defense`, `block`, `ranged`, `magic`, `fire`, `element`, `body`, `hunger`, `regen`, `other`, `all`（补全声明为 `suggestion<CommandSender>(uncheck = true) { allCategoryKeys + listOf("all") }`，`uncheck = true` 表示补全时不做参数合法性校验） |
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：**仅玩家**（`execute<Player>`，目标固定为执行者本人）。
- **行为说明**：
  - 若 `category` 命中 `CATEGORY_NAMES`（上表除 `all` 之外的 12 个 key）：取该分类的 ID 集合，过滤出玩家身上属于该分类的属性，按 ID 字典序输出，表头同样为 `Command_Debug_Show_Category_Header`；若过滤后为空，输出 `Command_Debug_Show_Empty`（"该分类下无属性或分类不存在"）。
  - `category == "all"` 走与无参 `show` 相同的"全部分类"分支。
  - 其它无法识别的值：输出 `Command_Debug_Show_Empty`。
  - 注意 `other` 是合法分类 key，但它对应的 ID 集合**恒为空**（`CATEGORY_NAMES` 里有 `"other" to "其他"`，`CATEGORY_IDS` 初始化时也为它建了空集合，但从未 `add` 过任何 ID），因此 `show other` 只会输出"该分类下无属性或分类不存在"。
- **示例**：`/atbdebug show combat`

### `/chotenattribute-debug showplayer <player>`
- **用途**：查看**指定玩家**的全部最终属性值，按分类分组输出。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家（`player("player")`，TabooLib 玩家参数类型，可按名称解析在线玩家） | 必填 | 由 TabooLib `player(...)` 组件提供（不使用自定义 `suggestion`） |
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - `context.player("player").castSafely<Player>()!!` 取目标玩家，然后调用 `showGrouped(sender, target, null)` —— 即目标为他人、`category` 为 `null`，行为与 `show` 的"全部分类"分支一致（按分类分组、类内按 ID 排序）。
  - 输出与提示发给**执行者**（`sender`）；但 `Command_Debug_Show_EmptyAll` 用的是 `target.asLangText(...)` —— 即空数据提示按**目标玩家**的语言环境取文本，其余消息按执行者的语言环境。
  - 该分支没有 `category` 参数。
- **示例**：`/atbdebug showplayer Steve`

### `/chotenattribute-debug categories`
- **用途**：列出所有属性分类及每类中实际存在的属性数量。
- **参数**：无。
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 先输出表头 `Command_Debug_Categories_Header`（"===== 属性分类 ====="）。
  - 遍历 `allCategoryKeys`（即 `CATEGORY_NAMES.keys`，顺序 `basic, combat, defense, block, ranged, magic, fire, element, body, hunger, regen, other`），对每类统计 `CATEGORY_IDS[catKey]` 中**同时存在于 `AttributeManager.attributeRegistry`** 的 ID 数量，输出 `Command_Debug_Categories_Item`（"{0} — {1} ({2} 个属性)"，参数为 中文分类名 / `§f/<catKey>` / 数量）。
  - 最后额外输出一行特殊项：`"全部"` / `§f/all` / `attributeRegistry.keys.size`。
  - 因此 `basic` 一类的计数会受 `attributeRegistry` 影响（例如注册表里没有的分类 ID 不计入）；`other` 由于 ID 集合为空恒为 0，`all` 反映注册表总数。
- **示例**：`/atbdebug categories`

## 分类与属性 ID 对照（源码 `CATEGORY_NAMES` / `CATEGORY_IDS`）

| 分类 key | 中文名 | 属性 ID |
|---|---|---|
| `basic` | 基础属性 | `max_health`, `max_absorption`, `movement_speed`, `attack_damage`, `attack_speed`, `armor`, `armor_toughness`, `attack_knockback`, `knockback_resistance`, `luck`, `fall_damage_multiplier`, `burning_time`, `explosion_knockback_resistance`, `mining_efficiency`, `movement_efficiency`, `oxygen_bonus`, `player_block_break_speed`, `player_block_interaction_range`, `player_entity_interaction_range`, `player_submerged_mining_speed`, `player_mining_speed`, `gravity`, `safe_fall_distance`, `scale`, `step_height`, `spawn_reinforcements` |
| `combat` | 战斗 | `crit_chance`, `crit_damage`, `lifesteal`, `dodge_chance`, `defense`, `defense_toughness`, `attack_damage` |
| `defense` | 防御 | `armor`, `armor_toughness`, `all_damage_reduction`, `physical_damage_reduction` |
| `block` | 格挡 | `block_chance`, `block_defense`, `block_defense_percent` |
| `ranged` | 远程 | `ranged_damage`, `ranged_extra_damage`, `ranged_damage_reduction`, `bow_extra_damage`, `crossbow_extra_damage`, `bow_damage_reduction`, `crossbow_damage_reduction` |
| `magic` | 魔法 | `magic_attack_damage`, `magic_damage`, `magic_extra_damage`, `magic_damage_reduction` |
| `fire` | 火焰 | `fire_attack_damage`, `fire_attack_chance`, `fire_extra_damage`, `fire_damage_reduction` |
| `element` | 元素伤害 | `physical_defense`, `physical_extra_damage`, `physical_damage`, `burn_defense`, `burn_extra_damage`, `burn_damage`, `frost_defense`, `frost_extra_damage`, `frost_damage`, `shock_defense`, `shock_extra_damage`, `shock_damage`, `psychic_defense`, `psychic_extra_damage`, `psychic_damage`, `erosion_defense`, `erosion_extra_damage`, `erosion_damage`, `decay_defense`, `decay_extra_damage`, `decay_damage` |
| `body` | 身体状态 | `max_sanity`, `sanity_regeneration`, `max_darkness`, `external_injury`, `internal_injury`, `external_injury_regen`, `internal_injury_regen` |
| `hunger` | 饥饿系统 | `max_custom_hunger`, `max_custom_saturation` |
| `regen` | 恢复 | `stamina_regen_multiplier`, `health_regen_multiplier` |
| `other` | 其他 | （无；空集合。未出现在上表的属性 ID 在分组展示时才归入 `other`） |

## 备注 / 注意事项

- **`getCategory` 的匹配顺序**：`CATEGORY_IDS` 是 `mutableMapOf()` 构建后按 `CATEGORY_NAMES.keys` 初始化，遍历顺序即表顺序；`getCategory` 返回**第一个**包含该 ID 的分类。因此重复出现的 ID 只在**一个**分类下展示：例如 `attack_damage` 同时属于 `basic` 与 `combat`，但只会出现在 `basic`；`armor` / `armor_toughness` 同时属于 `basic` 与 `defense`，只会出现在 `basic`。因此"全部分类"视图的实际条数会少于分类表的名义总数，`categories` 的计数则是按各分类 ID 集合独立统计（可能重复计数）。
- **`CATEGORY_IDS` 是惰性初始化**（`by lazy`），首次使用时构建。
- **分类表与注册表的差异**：分类表是硬编码的；`AttributeManager.registerBuiltinAttributes()` 还注册了一些**不在分类表里**的属性（如 `jump_strength`、`mining_power`、`stamina_max`、`hand_stamina_max`、`hand_stamina_regen_multiplier`、`health_regen`、`weight_limit_multiplier`、`deny_move` 等）。这些属性在"全部分类"视图中会落到末尾的其它分组（`other` 分支只在 `category == null || category == "all"` 的 for 循环里按 `CATEGORY_NAMES` 顺序输出，`other` 也是 `CATEGORY_NAMES` 的 key，所以未被前面分类匹配到的 ID 会在 `---- 其他 (n) ----` 表头下显示）。
- **`getAllAttributes` 的实现**：`ChoTenAttributeAPIImpl.getAllAttributes` = `attributeRegistry.keys.associateWith { getAttributeValue(player, it) }`，即遍历**全部已注册属性**读缓存值，因此输出条数取决于注册表规模，而非玩家身上是否有对应修饰符。
- **私有工具方法**：`getAllAttributes(player: Player)`（第 235 行）与 `getCategory`（第 134 行）为私有辅助方法；前者当前无调用点（`showGrouped` 内直接调用 `ChoTenAttributeAPI.instance.getAllAttributes`），属遗留代码。
- **异常处理**：`showGrouped` 内对 `getAllAttributes` 的调用用 `try/catch` 兜底为空 map，异常被静默吞掉，只会显示"该玩家无任何属性数据"。
- **消息前缀**：属性行为蓝色 `<#A9B4C3>`，数值为白色 `<#F2F5FA>`，均为硬编码 MiniMessage，未走语言文件；而分类表头 / 空提示走语言文件 `Command_Debug_*`。
