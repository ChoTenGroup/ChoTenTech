# 全球市场指令（CTTM）

> **根指令**：`/chotentech-market`（别名：`/cttm`, `/market`, `/globalmarket`）
> **权限**：未声明（继承框架默认）— `@CommandHeader` 未提供 `permission`，也未提供 `permissionDefault`；`reload` 在执行体内另行检查 `chotentech.admin`
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/GlobalMarketCommand.kt`

## 功能概述

全球市场（玩家交易市场）的指令入口。

- `open`：打开市场主界面（`GlobalMarketMenu.openMain`），上架/购买/管理均在 GUI 内完成。
- `claim`：领取自己已售出商品的待领取收益（`GlobalMarketManager.claimProceeds`）。
- `reload`：把市场数据保存到磁盘（`saveToDisk`）。
- `info`：打印市场统计信息（活跃商品数、总价值、待领取收益玩家数）。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotentech-market` | 打印指令用法帮助树（`createModernHelper()`） | 未声明（继承框架默认） |
| `/chotentech-market open` | 打开全球市场主界面 | 未声明（继承框架默认） |
| `/chotentech-market claim` | 领取待领取的市场收益 | 未声明（继承框架默认） |
| `/chotentech-market reload` | 保存市场数据到磁盘 | 运行时检查 `chotentech.admin` |
| `/chotentech-market info` | 查看市场统计信息 | 未声明（继承框架默认） |

> 4 个 `@CommandBody` 子命令（`open` / `claim` / `reload` / `info`）+ 1 个默认主体（`main`）。源码中**没有** `literal(`/`dynamic(`/`player(` 参数声明——全部子命令均无参数。

## 指令详解

### `/chotentech-market`

- **用途**：显示根指令的用法树。
- **参数**：无。
- **权限**：未声明（继承框架默认）
- **执行者要求**：玩家 / 控制台均可（`execute<ProxyCommandSender>`）
- **行为说明**：`createModernHelper()` 按发送者权限过滤后打印子指令结构。
- **示例**：`/cttm`

---

### `/chotentech-market open`

- **用途**：打开全球市场主界面。
- **参数**：无（`subCommand { execute<Player> { ... } }`）
- **权限**：未声明（继承框架默认）
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：调用 `GlobalMarketMenu.openMain(sender)` 渲染主菜单；上架、浏览、购买、取消等具体操作均在 GUI 内完成，指令层无对应子命令。
- **示例**：`/cttm open`

---

### `/chotentech-market claim`

- **用途**：一次性领取自己所有待领取的市场收益。
- **参数**：无
- **权限**：未声明（继承框架默认）
- **执行者要求**：**仅玩家**（`execute<Player>`）
- **行为说明**：
  1. 调用 `GlobalMarketManager.claimProceeds(sender)`，返回 `ClaimResult(success, message)`。
  2. 无待领取记录 → 返回 `没有待领取的收益` 并原样发给玩家。
  3. 有待领取余额 → 逐种货币 `CurrencyManager.addBalance(player, currency, amount.toLong())`（**按 `Long` 截断小数**），清空该玩家的待领取表，随后 `saveToDisk()` 落盘。
  4. 成功后提示 `成功领取: <金额> <货币显示名>, ...`（多货币以 `, ` 连接）。
- **示例**：`/cttm claim`

---

### `/chotentech-market reload`

- **用途**：把全球市场数据保存到磁盘（**注意：不是重载配置**）。
- **参数**：无
- **权限**：**运行时检查** `chotentech.admin`（无权限 → `你没有权限执行此命令`）
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：
  1. 无 `chotentech.admin` → 提示 `你没有权限执行此命令` 并中止。
  2. `GlobalMarketManager.saveToDisk()`。
  3. 提示 `[全球市场] 数据已保存!`。
- **示例**：`/cttm reload`

---

### `/chotentech-market info`

- **用途**：查看市场运行统计。
- **参数**：无
- **权限**：未声明（继承框架默认）— 执行体内无任何权限检查，任何能执行该指令的人均可查看
- **执行者要求**：玩家 / 控制台均可（`execute<CommandSender>`）
- **行为说明**：输出三行统计：
  - `=== 全球市场信息 ===`
  - `活跃商品: <n>` — `getActiveListings().size`
  - `总价值: <v>` — 所有活跃商品 `price` 求和后取 `Long`
  - `待领取收益玩家: <c>` — `getPendingBalancesCount()`（`pendingBalances` 的条目数，即玩家数而非金额）
- **示例**：`/cttm info`

## 备注 / 注意事项

- **权限模型**：`@CommandHeader` **没有** `permission` 参数，也没有 `permissionDefault`（记录为"未声明（继承框架默认）"）。唯一门禁是 `reload` 体内的 `sender.hasPermission("chotentech.admin")`。`open`、`claim`、`info` 均无任何权限检查。
- **`open` / `claim` 仅玩家**：两者使用 `execute<Player>`，控制台执行会因执行者类型不符而无法执行；`reload` / `info` 使用 `execute<CommandSender>`，控制台可用。
- **`reload` 名不副实**：实现只调用 `GlobalMarketManager.saveToDisk()`（保存数据），并不重新读取配置；它与其它模块中"reload = 重载配置"的语义不同。
- **`claim` 的金额精度**：待领取余额内部是 `Double`，领取时 `amount.toLong()` 直接截断小数部分（不是四舍五入），存在不足 1 的小数被丢弃的情况。
- **`claim` 会立即落盘**：领取成功后自动 `saveToDisk()`。
- **`info` 的"待领取收益玩家"**：统计的是 `pendingBalances` 中仍有记录的玩家数量，与金额无关。
- **无列表/下架子指令**：源码中没有 `list`、`cancel`、`sell` 等分支，全部依赖 GUI。
- **`newParser` 未声明**：该 `@CommandHeader` 只有 `name` / `aliases` / `description`，未开启 `newParser`（默认 `false`）。
- **`description = "全球市场系统"`**：会出现在帮助树中。
