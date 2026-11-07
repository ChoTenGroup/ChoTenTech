# 玩家备份（/backup）

> **根指令**：`/backup`（别名：`/玩家备份`, `/bak`）
> **权限**：`chotentech.command.backup`（默认：OP，`PermissionDefault.OP`）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/BackupCommand.kt`

## 功能概述

管理插件自带的**玩家数据备份文件**：

- 列出插件数据目录下 `backups/` 中的备份文件（文件名必须匹配 `backup-yyyy-MM-dd_HH-mm-ss.json.gz`）；
- 查看某个备份的详情与**手工恢复步骤**；
- 从 `restore/` 目录中已解压的 `.json` 备份文件**实际执行还原**（把备份里的 `players` 表逐条写回共享数据 / profile 数据 / 属性数据）。

两个目录（均相对插件数据目录）：

- `backups/`：`Private val backupDir by lazy { File(getDataFolder(), "backups") }` —— **只读**，本指令只列目录、不解压、不写。
- `restore/`：`File(getDataFolder(), "restore")`，类初始化时 `mkdirs()` —— 还原时从这里读被管理员**手工解压**好的 `.json`。

`/backup` 本身**不做备份**（不生成 `.gz`），也不做解压；解压步骤由管理员手工完成，`/backup info` 的输出里会列出这三步。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/backup` | 无参数：列出备份文件（等价于 `/backup list`） | `chotentech.command.backup` |
| `/backup list` | 列出备份文件 | `chotentech.command.backup` |
| `/backup info <filename>` | 查看某备份的文件名、时间、大小与恢复步骤 | `chotentech.command.backup` |
| `/backup restore <filename>` | 从 `restore/<filename>.json` 还原**全部**玩家 | `chotentech.command.backup` |
| `/backup restore <filename> <player>` | 只还原该备份中匹配指定玩家的那一条 | `chotentech.command.backup` |

> 所有 `@CommandBody` 均未单独声明 `permission`，统一继承 header 的 `chotentech.command.backup`（默认 OP）。

## 指令详解

### `/backup`（无参数）与 `/backup list`

- **用途**：列出可用备份，并给出「怎么查看详情」的提示。
- **参数**：无。
- **权限**：`chotentech.command.backup`。
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）。
- **行为说明**：
  - 扫描 `backups/` 目录，用正则 `backup-(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})\.json\.gz` **整名匹配**文件名，捕获组即时分串即「标签（label）」，并用 `yyyy-MM-dd_HH-mm-ss` 解析为 `LocalDateTime`（解析失败则该文件被忽略）。
  - 结果按时间**倒序**排列（最新在前）。
  - 目录不存在或没有匹配文件 → 「暂无备份文件」。
  - 否则输出表头 `玩家数据备份列表 (N 个)`、提示行 `/backup info <文件名> 查看详情与恢复步骤`，然后每个文件一行：`MM-dd HH:mm` 时间、大小（`< 1024` 显示 `B`，`< 1 MiB` 显示 `KB`，否则 `MB` 保留 1 位小数）、label。
  - `main` 与 `list` 是**完全等价**的两条路径，都调用 `listBackups(sender)`。
- **示例**：`/bak`、`/backup list`

### `/backup info <filename>`

- **用途**：查看一个备份文件的元信息，并获得手工恢复的操作步骤。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `filename` | 备份**标签（label）**，即文件名里 `backup-` 与 `.json.gz` 之间的时间戳部分，例如 `2024-05-01_12-30-00`（**不是完整文件名**） | 必填 | 补全为 `listBackupFiles().map { it.label }`，即所有备份的 label |

- **权限**：`chotentech.command.backup`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 用 label **精确匹配**（`entry.label == input`）查找；找不到 → 「未找到备份: `<input>`」。
  - 找到则输出一个方框，内容包括：完整文件名、时间（`yyyy-MM-dd HH:mm:ss`）、大小（`length() / 1024` KB，整数除法），随后是三步恢复指引：
    1. 手动解压 `<文件名>` 得到同名 `.json` 文件；
    2. 将 `.json` 文件放入 `restore` 目录；
    3. 执行 `/backup restore <label>`。
  - 纯只读。
- **示例**：`/backup info 2024-05-01_12-30-00`

### `/backup restore <filename>`

- **用途**：从 `restore/<filename>.json` 还原**文件中全部玩家**的数据。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `filename` | 还原文件名**主体**（不带 `.json`）；只能含字母、数字、点、下划线、连字符 | 必填 | **无**源码级补全（该 `dynamic("filename")` 未声明 `suggestion`） |

- **权限**：`chotentech.command.backup`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**——校验链（全部为安全护栏，任一失败即中止）：
  1. **文件名白名单**：`filename` 为空、包含 `..`、或不匹配 `[A-Za-z0-9._\-]+` → 「非法文件名: 只允许字母、数字、点、下划线、连字符」；
  2. **路径二次校验**：`File(restoreDir, "$filename.json").canonicalPath` 必须以 `restoreDir.canonicalPath + File.separator` 开头，否则「非法文件名: 路径越界」；
  3. **文件存在**：不存在 → 「未找到还原文件: `<filename>.json`」+「请先将解压后的 JSON 文件放入 restore 目录」；
  4. **大小上限 64 MiB**（`64L * 1024 * 1024`）：超过 → 「还原文件过大 (N MB)，超过 64 MB 上限」；
  5. **格式校验**：以 `JsonObject` 解析；`players` 字段缺失 → 「备份文件格式错误: 缺少 players 字段」；`players` 为空 → 「备份文件中没有玩家数据」；
  6. **条目数上限 100000**：超过 → 「备份玩家条目过多 (N)，拒绝还原」（防止主线程长时间阻塞）。
- **实际还原流程**（逐条 `players` 的 `uuid -> PlayerData`）：
  - 每条 `jsonUtils.fromJson(element, PlayerData::class.java)`；解析失败 → 输出「跳过 `<uuid>`: 数据解析失败 (…)」并 `skipped++`；
  - 读取 `backupTime`（毫秒）用于显示开始时间，缺失则显示「未知」；
  - 对每条：先 `Bukkit.getPlayerExact(playerData.playerName)`，若在线则用 `kick(Component.text("§c管理员正在恢复你的数据，请重新加入"))` **踢出**（`kicked++`），以确保下次登录重新加载；
  - `ProfileManager.releaseAll(uuid)` + `playerDataMap.remove(uuid)` 清理内存缓存；
  - 写共享数据：`shared.tape` / `ltoken` / `ptoken` / `achievements` / `cosmetics` ← 备份值，然后 `ProfileManager.saveSharedData(uuid)`；
  - 写 profile 数据：`ProfileManager.stripSharedFields(playerData)` 后 `ProfileManager.saveProfileData(uuid, playerData.profileId, stripped)`；
  - 若 `playerData.attributeModifiers != null`，读取 `AttributeManager.getPlayerPersistentData(uuid)` 覆盖 `modifiers` 并 `savePlayerData`；
  - 逐条输出「[i]已恢复 `<玩家名>` (uuid) - profile `<profileId>`」，`restored++`。
  - 收尾输出「还原完成」方框：来源时间、成功数、跳过数（>0 才显示）、踢出人数（>0 才显示），并提示「被踢出的玩家重新加入后自动加载恢复的数据」。
  - 整体 `try/catch`：异常 → 「还原失败: `<message>`」并 `printStackTrace()`。
- **示例**：`/backup restore 2024-05-01_12-30-00`

### `/backup restore <filename> <player>`

- **用途**：只从备份中还原**指定玩家**那一条数据。
- **参数**：

  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | `filename` | 还原文件名主体（规则同上） | 必填 | 无补全 |
  | `player` | 目标玩家名；用于筛选备份条目 | 必填 | TabooLib `player("player")` 自动补全在线玩家名 |

- **权限**：`chotentech.command.backup`。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 走与上面**完全相同**的校验链与还原流程，只是多一层筛选：`targetName != null` 时，用 `Bukkit.getPlayerExact(targetName)?.uniqueId?.toString()` 与条目的 `uuid` 比较，或比较 `playerData.playerName != targetName`；两者都不匹配的条目**静默跳过**（不计入 `skipped`，也不输出）。
  - ⚠ **筛选是不对称的**：目标玩家**必须在线**才能按 UUID 匹配；若目标离线，则只能依靠 `playerData.playerName` 与参数**字符串完全相等**来匹配。因此离线还原时参数必须与备份里记录的玩家名逐字符一致（大小写敏感）。
  - 其余行为（踢人、缓存清理、写共享/profile/属性数据、统计输出）完全一致。
- **示例**：`/backup restore 2024-05-01_12-30-00 Steve`

## 备注 / 注意事项

- **`filename` 的语义有两套**：
  - `info <filename>` 要的是**备份 label**（时间戳部分，如 `2024-05-01_12-30-00`）；
  - `restore <filename>` 要的是 `restore/` 目录里**已解压 JSON 的文件名主体**（不含 `.json`）。虽然 `info` 的第 3 步建议「执行 `/backup restore <label>`」，那是因为手工解压出来的 `.json` 与 `.gz` 同名（只差扩展名）。
- **`restore` 不会自动解压**：必须先手工把 `backups/backup-<label>.json.gz` 解压成 `<name>.json` 并放进 `restore/`，否则会报「未找到还原文件」。
- **路径穿越已做双重防护**：字符白名单（拒绝 `..` 与任何非 `[A-Za-z0-9._-]` 字符）+ `canonicalPath` 前缀校验。
- **资源上限**：单文件 64 MiB、单次最多 100000 个玩家条目，均会硬性拒绝。
- **`restore` 会踢人**：备份里所有被还原且当前在线的玩家都会被踢出（消息「管理员正在恢复你的数据，请重新加入」）；这是为了保证下次登录从数据库重新加载。
- **`restore` 是全服级别的重操作**：逐条 `fromJson` + 多次数据库写入**在主线程同步执行**，玩家量大时会明显卡顿；建议在低峰期/关服窗口使用。
- **`/backup` 不产生备份**：本指令只做浏览与还原，备份文件的生成由其它流程（备份任务）负责；`backups/` 目录不存在时 `list` 直接返回空列表。
- **字号单位**：`info` 里的大小用整数除法显示 KB（小于 1 KB 会显示 `0 KB`），`list` 里则会自适应显示 `B`/`KB`/`MB`。
- **别名含中文**：`/玩家备份` 是合法别名（`aliases = ["玩家备份", "bak"]`），在支持中文输入的客户端可直接使用。
