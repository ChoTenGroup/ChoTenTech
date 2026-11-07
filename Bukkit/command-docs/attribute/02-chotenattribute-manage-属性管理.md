# ChoTenAttribute Manage 属性管理

> **根指令**：`/chotenattribute-manage`（别名：`/attributemanage`, `/atbmanage`）
> **权限**：`chotenattribute.command.manage`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenAttributeManageCommand.kt`

## 功能概述

`/chotenattribute-manage` 是角色属性系统的持久化修饰符（AttributeModifier）管理器，用于为玩家**添加 / 覆盖设置 / 移除 / 列出 / 清空**属性修饰符，以及强制刷新玩家属性计算。

它操作的是 `PlayerAttributeData.modifiers` 这份持久化数据（由 TabooLib PTC `DataMapper` 存取，`AttributeManager.getPlayerPersistentData` / `savePlayerData`），因此对离线玩家同样生效。

本文件同时作为子指令对象被挂载在 `/chotenattribute manage` 下（见 `01-chotenattribute-属性主指令.md`），两条路径的子指令完全一致。`newParser = false`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotenattribute-manage` | 无参数主体，打印指令帮助树 | `chotenattribute.command.manage` |
| `/chotenattribute-manage add <player> <attrId> <type> <value> [duration]` | 为玩家追加一个持久化修饰符（可选限时） | `chotenattribute.command.manage` |
| `/chotenattribute-manage set <player> <attrId> <type> <value>` | 覆盖设置玩家某属性的 `OTHER` 来源修饰符 | `chotenattribute.command.manage` |
| `/chotenattribute-manage remove <player> <uuid>` | 按修饰符 UUID 移除单个修饰符 | `chotenattribute.command.manage` |
| `/chotenattribute-manage update <player>` | 强制刷新玩家属性计算 | `chotenattribute.command.manage` |
| `/chotenattribute-manage list <player>` | 列出玩家全部持久化修饰符 | `chotenattribute.command.manage` |
| `/chotenattribute-manage clear <player>` | 清空玩家全部修饰符 | `chotenattribute.command.manage` |

> 上述子命令均未单独声明 `permission`，统一由根 header 的 `chotenattribute.command.manage` 约束；`execute` 内部没有额外的 `sender.hasPermission(...)` 运行时检查。

## 指令详解

### `/chotenattribute-manage`
- **用途**：打印指令帮助树。
- **参数**：无。
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`mainCommand { createModernHelper() }`，输出各子命令及其参数（参数名来自 `dynamic("...")` 的名字，见下表）。
- **示例**：`/atbmanage`

### `/chotenattribute-manage add <player> <attrId> <type> <value> [duration]`
- **用途**：为指定玩家**追加**一个持久化属性修饰符；可选 `duration` 指定持续时间（秒）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表（`Bukkit.getOnlinePlayers().map { it.name }`） |
  | `attrId` | 属性 ID（`dynamic("attrId")`） | 必填 | 全部已注册属性 ID（`AttributeManager.attributeRegistry.keys`） |
  | `type` | 修饰符计算类型（`dynamic("type")`） | 必填 | `ADD` / `PERCENT` / `MULTIPLY`（`ModifierType` 枚举，补全为 `ModifierType.entries.map { it.name }`） |
  | `value` | 数值（`dynamic("value")`） | 必填 | 无补全；按 `toDoubleOrNull()` 解析，失败报错 |
  | `duration` | 持续时间，单位秒（`dynamic("duration")`） | 可选 | 无补全；按 `toLongOrNull()` 解析，失败视为 `null`（永久） |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. `Bukkit.getPlayer(player)` 取在线玩家；找不到则回复语言键 `Command_Error_Player_Not_Found`（"玩家 {0} 未找到或不在线。"）。
  2. `ModifierType.valueOf(type.uppercase())` 解析类型，失败回复 `Command_Error_Invalid_Type`（提示可选值 `ADD/PERCENT/MULTIPLY`）。
  3. `valueStr.toDoubleOrNull()` 解析数值，失败回复 `Command_Error_Invalid_Value`（"无效的值：{0}"）。
  4. 构造 `AttributeModifier(attributeId = attrId, value = value, type = type, source = ModifierSource.OTHER, expireOn = duration?.let { System.currentTimeMillis() + it * 1000 })`。其余字段用默认值：`uuid = UUID.randomUUID()`、`slot = AttributeSlot.OTHER`、`deathMark = false`。
  5. 调用 `ChoTenAttributeAPI.instance.addModifier(player.uniqueId, modifier)`：追加到 `modifiers` 列表、`savePlayerData` 持久化；若玩家在线则立即 `doUpdate`，若离线则等下次登录时更新。
  6. 回复 `Command_Manage_Add_Success`，参数依次为属性显示名、数值、`type.name`、玩家名。属性显示名由 `AttributeManager.getAttributeDisplayName(sender, attrId)` 取语言键 `Attribute_<id>_Name`；属性未注册时回退为原始 ID。
  7. 若指定了 `duration`，额外回复 `§7持续 §e$duration §7秒,到期自动移除`（纯字符串，不走语言文件）。
  8. 到期清除由 `AttributeManager` 的周期任务兜底（每 20 tick / 1 秒扫描在线玩家，`removeIf { it.isExpired }` 后保存并 `updatePlayer`）；源码注释明确说明不使用一次性延迟任务，避免 tick 边界早于 `expireOn` 导致修饰符永久残留。`doUpdate` 时也会顺带清理过期修饰符。
- **示例**：
  - `/atbmanage add Steve attack_damage ADD 25` —— 永久 +25 攻击力
  - `/atbmanage add Steve crit_chance PERCENT 15 60` —— 15% 暴击率，持续 60 秒

### `/chotenattribute-manage set <player> <attrId> <type> <value>`
- **用途**：**覆盖设置**玩家某属性的 `OTHER` 来源修饰符（先移除同属性同来源的旧修饰符，再写入新的）。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表 |
  | `attrId` | 属性 ID（`dynamic("attrId")`） | 必填 | 全部已注册属性 ID |
  | `type` | 修饰符计算类型（`dynamic("type")`） | 必填 | `ADD` / `PERCENT` / `MULTIPLY` |
  | `value` | 数值（`dynamic("value")`） | 必填 | 无补全 |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. 使用 `Bukkit.getOfflinePlayer(context["player"])` —— **支持离线玩家**（与 `add` 不同）。
  2. 解析 `type`（大写后 `ModifierType.valueOf`）与 `value`（`toDoubleOrNull`），失败分别回复 `Command_Error_Invalid_Type` / `Command_Error_Invalid_Value`。
  3. 读取 `AttributeManager.getPlayerPersistentData(player.uniqueId)`，`mods.removeIf { it.attributeId == attrId && it.source == ModifierSource.OTHER }`，再追加新的 `AttributeModifier(attributeId, value, type, source = ModifierSource.OTHER)`（`expireOn` 为默认 `null`，即永久）。
  4. `data.modifiers = mods` 后 `AttributeManager.savePlayerData(data)` 持久化。
  5. 若该玩家在线，调用 `AttributeManager.updatePlayer(it)` 立即刷新属性。
  6. 回复 `Command_Manage_Set_Success`（属性显示名、数值、`type.name`、玩家名；名称为 `null` 时用 `"Unknown"`）。
  7. 该指令**不影响**其它来源（`HAND` / `ARMOR` / `SKILL` 等）的修饰符，只替换 `OTHER` 来源。
- **示例**：`/atbmanage set Steve movement_speed ADD 0.05`

### `/chotenattribute-manage remove <player> <uuid>`
- **用途**：按修饰符 UUID 移除玩家的单个修饰符。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表 |
  | `uuid` | 修饰符 UUID（`dynamic("uuid")`，注意参数名为 `uuid`，含义是**修饰符 ID**而非玩家 UUID） | 必填 | 该玩家当前所有修饰符的 UUID 字符串（`AttributeManager.getPlayerPersistentData(...).modifiers.map { it.uuid.toString() }`） |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  1. `Bukkit.getOfflinePlayer(context["player"])` 取玩家（支持离线）。
  2. `UUID.fromString(uuidString)` 解析，失败回复 `§cInvalid UUID.`（纯字符串，不走语言文件）。
  3. 调用 `ChoTenAttributeAPI.instance.removeModifier(player.uniqueId, uuid)`：从 `modifiers` 中移除匹配 UUID（`removeIf { it.uuid == modifierUuid }`），成功则 `savePlayerData`，并对在线玩家调用 `AttributeManager.updatePlayer`。
  4. 无论是否真的移除成功，都会回复 `Command_Manage_Remove_Attempt`（"尝试移除 {1} 的修饰符：{0}"，`{0}` 为 UUID 字符串，`{1}` 为玩家名/`Unknown`），仅表示"尝试"。
- **示例**：`/atbmanage remove Steve 3f2b1c8e-...`

### `/chotenattribute-manage update <player>`
- **用途**：强制重新计算并刷新玩家属性。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表 |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`Bukkit.getPlayer(...)` 只接受**在线**玩家，否则回复 `Command_Error_Player_Not_Found`；随后 `ChoTenAttributeAPI.instance.updatePlayerAttributes(player)`（内部转调 `AttributeManager.updatePlayer(player)`，默认 `async = true`，非主线程调用时会切回主线程执行 `doUpdate`）；成功后回复 `Command_Manage_Update_Success`（"成功更新 {0} 的属性。"）。
- **示例**：`/atbmanage update Steve`

### `/chotenattribute-manage list <player>`
- **用途**：列出玩家的全部持久化修饰符。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表 |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`Bukkit.getOfflinePlayer(...)`（支持离线）→ 读取持久化数据 → 输出表头 `Command_Manage_List_Header`（"--- {0} 的属性列表 ---"）；若无修饰符输出 `Command_Manage_List_Empty`（"无修饰符。"）；否则对每个 `AttributeModifier` 输出 `Command_Manage_List_Item`，参数为 属性显示名 / `value` / `type.name` / `uuid`。注意仅列出 `filterIsInstance<AttributeModifier>()` 的条目。
- **示例**：`/atbmanage list Steve`

### `/chotenattribute-manage clear <player>`
- **用途**：清空玩家的全部属性修饰符。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `player` | 目标玩家名（`dynamic("player")`） | 必填 | 在线玩家名列表 |
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`Bukkit.getOfflinePlayer(...)`（支持离线）→ `data.modifiers = emptyList()` → `AttributeManager.savePlayerData(data)`；若玩家在线再 `AttributeManager.updatePlayer(it)`；最后回复 `Command_Manage_Clear_Success`（"成功清除 {0} 的所有修饰符。"）。这是**无差别清空**，所有 `ModifierSource` 的修饰符都会被移除。
- **示例**：`/atbmanage clear Steve`

## 备注 / 注意事项

- **枚举取值（源码 `data/Attribute.kt`）**
  - `ModifierType`（修饰符计算类型）：`ADD`、`PERCENT`、`MULTIPLY`。
  - `ModifierSource`（修饰符来源分类）：`HAND`、`MAINHAND`、`OFFHAND`、`MELEE`、`RANGED`、`ARMOR`、`SKILL`、`POTION`、`HEALING`、`OTHER`。指令写入的修饰符**固定为 `OTHER`**；`set` 只会替换 `OTHER` 来源；`list` 不显示来源；`remove` 按 UUID 精确移除。
  - `AttributeSlot`（槽位，指令不暴露）：`HEAD`、`CHEST`、`LEGS`、`FEET`、`INVENTORY`、`MAINHAND`、`OFFHAND`、`OTHER`（指令写入时取默认 `OTHER`）。
- **`attrId` 不是枚举**：`attrId` 由 `AttributeManager.attributeRegistry` 动态决定，来源包括内置属性（`registerBuiltinAttributes`）、`dataFolder/attributes/*.yml` 配置（`loadAttributeFiles`）以及 `basic-attributes` 配置派生的 `<id>_min` / `<id>_max` / `<id>_regeneration` 等属性。合法 ID 仅允许 `[a-z0-9_]`（`VALID_ID_PATTERN`）。建议以 `/chotenattribute-manage add` 的补全列表或 `/chotenattribute-debug show` 的实际输出为准。
- **在线 / 离线行为差异**：
  - `add`、`update` 使用 `Bukkit.getPlayer(...)`，**仅在线**玩家可用。
  - `set`、`remove`、`list`、`clear` 使用 `Bukkit.getOfflinePlayer(...)`，支持离线玩家（离线时不立即刷新属性，等玩家下次登录 `doUpdate` 时生效）。
- **数值合法性**：`value` 只做 `toDoubleOrNull()` 解析，不做范围钳制（`AttributeType.clamp` 未在此路径调用）。
- **duration 语义**：`add` 的 `duration` 单位为**秒**（`System.currentTimeMillis() + duration * 1000`）；`toLongOrNull()` 失败时静默按 `null`（永久）处理，不报错。
- **语言键位置**：`Bukkit/src/main/resources/lang/zh_CN.yml`（如 `Command_Manage_List_Header`、`Command_Manage_List_Item`、`Command_Debug_*` 等）；`Attribute_<id>_Name` 决定属性显示名。
