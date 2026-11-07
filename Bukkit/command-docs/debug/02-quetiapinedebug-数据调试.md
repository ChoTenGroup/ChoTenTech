# Quetiapine 数据调试指令（quetiapinedebug）

> **根指令**：`/quetiapinedebug`（别名：`/rpgdebug`, `/chotentechdebug`, `/cttdebug`）
> **权限**：`quetiapine.command.debug`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/debugSubCommand.kt`

## 功能概述

`quetiapinedebug` 用于把**方块再生 / 等级定义**相关的运行时配置表整体倾倒到聊天框，属于纯只读的配置排查入口。

```kotlin
@CommandHeader(
    name = "quetiapinedebug",
    aliases = ["rpgdebug", "chotentechdebug", "cttdebug"],
    permission = "quetiapine.command.debug",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
```

> ⚠️ **别名冲突**：`chotentech-debug`（`ChoTenDebugCommand.kt`）**也声明了别名 `cttdebug`**，而两者是完全不同的指令（权限也不同：本指令是 `quetiapine.command.debug`，后者是 `chotentech.command.debug`）。`/cttdebug` 落到谁头上取决于注册顺序，请使用完整名 `/quetiapinedebug`。

本指令**只**作为独立根指令注册（不同于 `ChoTenDebugCommand` 还被 `MainCommand` 挂到 `/ctt debug`），源码中并没有把 `DebugCommand` 挂载为 `/chotentech` 的子节点。

> 子指令字面量取自 `@CommandBody` 的 Kotlin 属性名（本文件未使用 `literal("...")`）：`help` / `getBlockRegenMap` / `getRegenTemplatesByBlock` / `getRegenTemplates` / `getLevels`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/quetiapinedebug` | 不带参数：打印指令用法树（`createModernHelper()`） | `quetiapine.command.debug` |
| `/quetiapinedebug help` | 打印指令用法树 | `quetiapine.command.debug` |
| `/quetiapinedebug getBlockRegenMap` | 倾倒方块再生映射 `blockRegenMap` | `quetiapine.command.debug` |
| `/quetiapinedebug getRegenTemplatesByBlock` | 倾倒 `regenTemplatesByBlock`（按方块的再生模板索引） | `quetiapine.command.debug` |
| `/quetiapinedebug getRegenTemplates` | 倾倒 `regenTemplates`（再生模板全集） | `quetiapine.command.debug` |
| `/quetiapinedebug getLevels` | 倾倒等级定义表 `levels` | `quetiapine.command.debug` |

> 本文件共 1 个 `main` + 5 个 `subCommand`（`help` 与 4 个倾倒子指令），全部**未单独声明** `permission`，统一继承根权限 `quetiapine.command.debug`。

## 指令详解

### `/quetiapinedebug` 与 `/quetiapinedebug help`
- **用途**：打印本指令的用法树。
- **参数**：无。
  | 参数 | 类型/含义 | 是否必填 | 可选值/自动补全 |
  |---|---|---|---|
  | —— | 无参数 | —— | —— |
- **权限**：`quetiapine.command.debug`
- **执行者要求**：控制台均可（`createModernHelper()` 内部为 `execute<ProxyCommandSender>`）。
- **行为说明**：`main`（`mainCommand`）与 `help`（`subCommand`）都只调用 `createModernHelper()`，按执行者权限过滤、跳过 `hidden` 字面量后输出树状用法。
- **示例**：`/quetiapinedebug`

---

### `/quetiapinedebug getBlockRegenMap`
- **用途**：倾倒方块再生映射表。
- **参数**：无。
- **权限**：`quetiapine.command.debug`
- **执行者要求**：控制台均可（`execute<CommandSender>`）。
- **行为说明**：`var message = blockRegenMap.toString()`，随后 `sender.infoS(message, false)`（第二个参数 `false` 表示不额外加前缀/不回显给控制台）。
- **示例**：`/quetiapinedebug getBlockRegenMap`

---

### `/quetiapinedebug getRegenTemplatesByBlock`
- **用途**：倾倒「按方块索引的再生模板」表，用于确认某个方块绑定了哪些再生模板。
- **参数**：无。
- **权限**：`quetiapine.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.infoS(regenTemplatesByBlock.toString(), false)`。
- **示例**：`/quetiapinedebug getRegenTemplatesByBlock`

---

### `/quetiapinedebug getRegenTemplates`
- **用途**：倾倒再生模板全集。
- **参数**：无。
- **权限**：`quetiapine.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.infoS(regenTemplates.toString(), false)`。
- **示例**：`/quetiapinedebug getRegenTemplates`

---

### `/quetiapinedebug getLevels`
- **用途**：倾倒等级定义表 `levels`，用于核对层级键是否已加载（与 `/quetiapinelevel` 的 `key` 补全同源）。
- **参数**：无。
- **权限**：`quetiapine.command.debug`
- **执行者要求**：控制台均可。
- **行为说明**：`sender.infoS(levels.toString(), false)`。
- **示例**：`/quetiapinedebug getLevels`

## 备注 / 注意事项

1. **别名冲突（重要）**：`cttdebug` 同时被本指令与 `chotentech-debug` 占用，且前者的 `permission` 是 `quetiapine.command.debug`、后者是 `chotentech.command.debug`；如果你只授予其中一个权限节点，用 `/cttdebug` 可能会命中你没有权限的那个并报无权限。**务必用完整名**。
2. **与 `chotentech-debug` 的分工**：本指令只倾倒**配置/模板类**表（`blockRegenMap`、`regenTemplatesByBlock`、`regenTemplates`、`levels`）；`chotentech-debug` 负责**运行时玩家数据/区块/ID**（`playerDataMap`、`loadedChunk`、CID 等）。不要把两者混为一谈。
3. **`newParser = true`**：启用了新版解析器（`chotentech-debug` 为 `false`）。
4. **本指令只有只读子指令**：没有任何会改动服务器状态的命令，可在生产环境安全用于排查（但输出可能很长，注意刷屏）。
5. **不存在 `getDataMap`**：与本文件同名的 `getDataMap` 只在 `chotentech-debug` 里；在 `/quetiapinedebug` 下输入它不会被识别。
6. **输出走 `infoS(message, false)`**：这是插件自带的日志/消息封装（`io.github.zzzyyylllty.chotentech.logger.infoS`），第二个参数为 `false` 时不会再把同一内容作为控制台日志重复输出。
