# ChoTenTech 调试指令（chotentech-debug）

> **根指令**：`/chotentech-debug`（别名：`/cttdebug`）
> **权限**：`chotentech.command.debug`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenDebugCommand.kt`

## 功能概述

`chotentech-debug` 是本插件的**开发者诊断指令**：把内存里的运行时数据结构直接倾倒到聊天框、按内部 ID（CID）反查玩家数据、以及按矩形区块范围批量清理/记录区块存档数据。

```kotlin
@CommandHeader(
    name = "chotentech-debug",
    aliases = ["cttdebug"],
    permission = "chotentech.command.debug",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
```

> ⚠️ **别名冲突**：`quetiapinedebug`（`debugSubCommand.kt`）**也声明了别名 `cttdebug`**。两个根指令同时注册时，`/cttdebug` 实际指向哪一个取决于注册顺序，因此请优先使用完整名 `/chotentech-debug`。

另外，`MainCommand.kt` 中还有 `@CommandBody val debug = ChoTenDebugCommand`，所以本指令也被挂载为 `/chotentech debug`（即 `/ctt debug`），与 `/chotentech-debug` 等价。

> 子指令字面量取自 `@CommandBody` 的 Kotlin 属性名（本文件未使用 `literal("...")`）：`getDataMap` / `getLevelMap` / `getSDClassMap` / `getIdExists` / `getDataById` / `chunks` / `powers` / `chunkCurrent` / `chunkSave` / `loadData` / `getDataByPlayer` / `changePlayerId` / `randomGenerateID`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-debug` | 不带参数：打印指令用法树（`createModernHelper()`） | `chotentech.command.debug` |
| `/chotentech-debug getDataMap` | 倾倒玩家数据表 `playerDataMap` 的全部条目 | `chotentech.command.debug` |
| `/chotentech-debug getLevelMap` | 倾倒 `levelsList` 的全部条目 | `chotentech.command.debug` |
| `/chotentech-debug getSDClassMap` | 倾倒 `sdClassList` 的全部条目（`String -> SurvivalDifficulty` 生存难度分类表） | `chotentech.command.debug` |
| `/chotentech-debug getIdExists <id>` | 判断某 CID 是否已存在 | `chotentech.command.debug` |
| `/chotentech-debug getDataById <id>` | 按 CID 取玩家数据对象 | `chotentech.command.debug` |
| `/chotentech-debug chunks` | 倾倒已加载区块表 `loadedChunk` | `chotentech.command.debug` |
| `/chotentech-debug powers` | 倾倒 `PowerManager` 运行时表 | `chotentech.command.debug` |
| `/chotentech-debug chunkCurrent` | 查看自己所在区块的存档数据 | `chotentech.command.debug` |
| `/chotentech-debug chunkSave <x1> <z1> <x2> <z2> <mode>` | 对矩形范围内区块执行存档数据操作 | `chotentech.command.debug` |
| `/chotentech-debug loadData` | 为自己重新执行一次数据初始化 | `chotentech.command.debug` |
| `/chotentech-debug getDataByPlayer <user>` | 按玩家查询其玩家数据 | `chotentech.command.debug` |
| `/chotentech-debug changePlayerId <user> <id>` | 改写玩家的 CID | `chotentech.command.debug` |
| `/chotentech-debug changePlayerId <user> <id> <type>` | 改写玩家的 CID 与 CID 类型 | `chotentech.command.debug` |
| `/chotentech-debug randomGenerateID` | 异步生成并打印 10 个随机 ID | `chotentech.command.debug` |

> 本文件共 1 个 `main` + 13 个 `subCommand` 属性；其中 `changePlayerId` 有两条命令路径，故有效路径共 14 条。所有子指令都**没有**单独声明 `permission`，统一继承根权限 `chotentech.command.debug`。

## 指令详解

### `/chotentech-debug`
- **用途**：打印指令用法树。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可（`createModernHelper()` 内部为 `execute<ProxyCommandSender>`）。
- **行为说明**：`main` 是 `mainCommand { createModernHelper() }`，按执行者权限过滤并按树状结构输出子指令。注意本根指令**没有** `help` 子指令（区别于 `quetiapinedebug` / `quetiapinelevel` / `quetiapineapi`，它们都有 `help`）。
- **示例**：`/chotentech-debug` 或 `/ctt debug`

---

### `/chotentech-debug getDataMap`
- **用途**：倾倒已加载玩家数据表 `playerDataMap`。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | —— | 无参数 | —— | —— |
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可（`execute<CommandSender>`）。
- **行为说明**：`sender.sendComponent(playerDataMap.entries.toString())`，即把整张表的 `key=value` 条目一次性输出（数据量大时会很长）。
- **示例**：`/chotentech-debug getDataMap`

---

### `/chotentech-debug getLevelMap`
- **用途**：倾倒等级存档表 `levelsList`（`LinkedHashMap<String, Level>`）。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.sendComponent(levelsList.entries.toString())`。
- **示例**：`/chotentech-debug getLevelMap`

---

### `/chotentech-debug getSDClassMap`
- **用途**：倾倒 `sdClassList`（`LinkedHashMap<String, SurvivalDifficulty>`，生存难度分类表）。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.sendComponent(sdClassList.entries.toString())`。
- **示例**：`/chotentech-debug getSDClassMap`

---

### `/chotentech-debug getIdExists <id>`
- **用途**：判断某个 CID 是否已被占用。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | CID（`dynamic`，以字符串形式传入 `DataManager.isIDExist`） | 必填 | 无 |
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：输出 `"${DataManager.isIDExist(context["id"])}"`，即 `true` / `false` 的纯文本。
- **示例**：`/chotentech-debug getIdExists 10001`

---

### `/chotentech-debug getDataById <id>`
- **用途**：按 CID 反查玩家数据对象。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `id` | CID（`dynamic`） | 必填 | 无 |
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：输出 `"${DataManager.getDataByCID(context["id"])}"`（对象 `toString()`，查不到时显示 null）。
- **示例**：`/chotentech-debug getDataById 10001`

---

### `/chotentech-debug chunks`
- **用途**：倾倒已加载区块表 `loadedChunk`。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.sendMessage(loadedChunk.toString())`（注意用的是 `sendMessage`，不做 MiniMessage 解析）。
- **示例**：`/chotentech-debug chunks`

---

### `/chotentech-debug powers`
- **用途**：倾倒 `PowerManager` 的运行时表。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.sendMessage(PowerManager.getRuntimeMap().toString())`。
- **示例**：`/chotentech-debug powers`

---

### `/chotentech-debug chunkCurrent`
- **用途**：查看执行者**当前所在区块**的存档数据。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：**仅玩家**（源码直接 `(sender as Player).chunk`，控制台执行会抛 `ClassCastException`）。
- **行为说明**：`loadedChunk[ChunkPos(chunk.world.name, chunk.x, chunk.z)].toString()`，用 `sendMessage` 输出；该区块未登记时显示 `null`。
- **示例**：`/chotentech-debug chunkCurrent`

---

### `/chotentech-debug chunkSave <x1> <z1> <x2> <z2> <mode>`
- **用途**：对以两个对角坐标界定的矩形范围内的所有区块，执行一次区块存档数据操作。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `x1` | 整数，第一个角的区块 X | 必填 | 无 |
  | `z1` | 整数，第一个角的区块 Z | 必填 | 无 |
  | `x2` | 整数，第二个角的区块 X | 必填 | 无 |
  | `z2` | 整数，第二个角的区块 Z | 必填 | 无 |
  | `mode` | 操作模式（`dynamic`） | 必填 | 补全：`CLEAR`、`LOG_UNBREAK` |
- **权限**：`chotentech.command.debug`
- **执行者要求**：**仅玩家**（源码 `val player = (sender as? Player?)!!`，控制台会 NPE；且用 `player.world` 构造区块坐标）。
- **行为说明**：
  1. 先把 `x1/x2`、`z1/z2` 各自取 `min`/`max` 排序（`min(...)`/`max(...)`），因此两个角的输入顺序无所谓。
  2. 用 `Location(player.world, x1, 100.0, z1).chunk` 与 `Location(player.world, x2, 100.0, z2).chunk` 取得首尾区块。
  3. 先发 `<#84D1CB>开始加载区块，请稍等。`。
  4. 双重循环遍历 `chunkX in first.x..last.x`、`chunkZ in first.z..last.z`，对每个区块 `player.world.getChunkAt(chunkX, chunkZ)`，并输出 `<#EFDF8F>开始执行 $chunkX $chunkZ`。
  5. 按 `mode` 分支：`"CLEAR"` → `DataManager.clearChunkData(chunk)`；`"LOG_UNBREAK"` → `DataManager.logUnbreakChunkData(chunk)`。**其它任何字符串都不做任何事**（无提示、无报错）。
  6. 每个区块处理完输出 `<#8FD4A6>开始执行 $chunkX $chunkZ 完成。`。
  7. 整体包在 `submitAsync` 中异步执行。
- **示例**：
  - `/chotentech-debug chunkSave -5 -5 5 5 CLEAR`
  - `/chotentech-debug chunkSave 0 0 3 3 LOG_UNBREAK`

---

### `/chotentech-debug loadData`
- **用途**：为执行者重新执行一次数据初始化（`initData`）。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：**仅玩家**（`val player = sender as Player`，控制台会抛 `ClassCastException`）。
- **行为说明**：`(sender as Player)?.let { initData(it) }`（`DataManager.initData`）。执行后**没有任何提示输出**，需用 `getDataMap` / `getDataByPlayer` 验证结果。
- **示例**：`/chotentech-debug loadData`

---

### `/chotentech-debug getDataByPlayer <user>`
- **用途**：按玩家查询其玩家数据。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player`） | 必填 | 在线玩家名 |
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`DataManager.getData(bukkitPlayer?.uniqueId.toString())`，结果以 `sendComponent` 输出。玩家解析失败时 `castSafely<Player>()` 返回 null，`uniqueId` 拼接为字符串 `"null"`，最终显示查询不到的结果。
- **示例**：`/chotentech-debug getDataByPlayer Steve`

---

### `/chotentech-debug changePlayerId <user> <id>` / `/chotentech-debug changePlayerId <user> <id> <type>`
- **用途**：强制改写玩家的内部 ID（CID）；三段式写法可同时指定 CID 类型。
- **参数**：
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `user` | 目标玩家（`player`） | 必填 | 在线玩家名 |
  | `id` | 新 CID（`dynamic`，代码内 `context["id"].toLong()`，**必须为数字**） | 必填 | 无 |
  | `type` | CID 类型（`dynamic`，仅三段式） | 可选 | 无（补全为空） |
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：
  - 若 `DataManager.isIDExist(context["id"])` 为真 → 输出 `userId must be unique` 并放弃。
  - 否则取 `DataManager.getData(player.uniqueId.toString())`；数据为 null → 输出 `<#E08A98>未找到玩家数据`。
  - 两段式（不带 `type`）→ `DataManager.modifyUserID(id, playerData)`。
  - 三段式（带 `type`）→ `DataManager.modifyUserIDData(IDData(id, type), playerData)`。
- **示例**：
  - `/chotentech-debug changePlayerId Steve 10001`
  - `/chotentech-debug changePlayerId Steve 10001 ULTIMA`

---

### `/chotentech-debug randomGenerateID`
- **用途**：生成一批随机 ID 用于分配调试。
- **参数**：无。
- **权限**：`chotentech.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：在 `submitAsync` 中 `repeat(10)` 各调用一次 `generateID()`，并把每个结果 `sendComponent` 出去（因此一次执行会收到 10 行）。
- **示例**：`/chotentech-debug randomGenerateID`

## 备注 / 注意事项

1. **别名冲突（重要）**：本根指令的 `cttdebug` 与 `quetiapinedebug` 的 `cttdebug` **重复**。两者是不同指令、不同权限（`chotentech.command.debug` vs `quetiapine.command.debug`）、执行不同逻辑（本指令是区块/玩家数据/ID 调试，`quetiapinedebug` 是再生模板与等级表倾倒）。`/cttdebug` 的实际归属取决于注册顺序，脚本与文档中请使用 `/chotentech-debug`。
2. **双入口**：`/chotentech-debug` 与 `/ctt debug`（经 `MainCommand` 的 `val debug = ChoTenDebugCommand`）功能完全一致。
3. **`newParser = false`**：未启用新版解析器（`quetiapinedebug` 为 `true`）。
4. **无 `help` 子指令**：只有 `main` 提供用法树；使用 `/chotentech-debug help` 不会命中任何子指令。
5. **控制台风险**：`chunkCurrent`（`sender as Player`）、`loadData`（`sender as Player`）、`chunkSave`（`(sender as? Player?)!!`）在控制台执行会抛 `ClassCastException` / `NullPointerException`，属于源码未做防呆；请在游戏内以玩家身份执行。
6. **`chunkSave` 的 `mode` 是 `dynamic` 而非字面量**：可输入任意字符串，但只有 `CLEAR` 与 `LOG_UNBREAK` 有实现，其它值会被**静默忽略**（仍然会遍历区块并打印开始/完成消息）。
7. **`changePlayerId` 的 `id` 必须为纯数字**：源码 `context["id"].toLong()` 未做 `toLongOrNull` 保护，非数字输入会抛 `NumberFormatException`。
8. **`getDataMap` / `getLevelMap` / `getSDClassMap` / `chunks` / `powers` 输出为原始 `toString()`**：使用 `sendComponent` 的几项会经过组件解析，用 `sendMessage` 的几项（`chunks`、`powers`、`chunkCurrent`）不解析颜色标签。
9. **`getIdExists` / `getDataById` 输出无前缀**：仅输出 `true`/`false` 或对象字符串，便于脚本抓取，但可读性差。
