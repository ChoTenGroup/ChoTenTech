# ChoTenAttribute 属性系统主指令

> **根指令**：`/chotenattribute`（别名：`/attribute`, `/atb`）
> **权限**：`chotenattribute.command.main`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenAttributeMainCommand.kt`

## 功能概述

`/chotenattribute` 是 ChoTenTech 角色属性系统（ChoTenAttribute）的指令入口，负责展示插件版本 / 运行平台信息、重载配置文件，并把属性管理子指令与属性调试子指令挂载到本根指令之下。

该指令同时被挂载为 ChoTenTech 主指令 `/chotentech` 的 `attribute` 子指令（见 `command/MainCommand.kt` 第 74 行 `val attribute = ChoTenAttributeMainCommand`），因此也可以通过 `/chotentech attribute ...` 访问同样的子指令树。

所有子命令的执行者均为 `CommandSender`（玩家与控制台均可），命令框架为 TabooLib `@CommandHeader` / `@CommandBody` 注解式指令，`newParser = false`。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chotenattribute` | 无参数主体，打印完整指令帮助树 | `chotenattribute.command.main` |
| `/chotenattribute about` | 显示插件名称、版本、运行平台与作者 | `chotenattribute.command.main` |
| `/chotenattribute reload` | 重载插件自定义配置 | `chotenattribute.command.main` |
| `/chotenattribute debug ...` | 属性调试子指令（嵌套对象 `ChoTenAttributeDebugCommand`） | `chotenattribute.command.debug` |
| `/chotenattribute manage ...` | 属性管理子指令（嵌套对象 `ChoTenAttributeManageCommand`） | `chotenattribute.command.manage` |

> 上表中的权限列：`about` / `reload` / 主体帮助由根指令的 `permission = "chotenattribute.command.main"` 约束；`debug` / `manage` 是嵌套的独立 `@CommandHeader` 对象，各自带有自己的根权限（见对应文档）。

## 指令详解

### `/chotenattribute`
- **用途**：不带任何参数时打印本根指令的完整指令帮助树（Usage 树）。
- **参数**：无。
- **权限**：`chotenattribute.command.main`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`@CommandBody val main = mainCommand { createModernHelper() }`。`createModernHelper()` 定义在 `command/MainCommand.kt`，会递归遍历 `context.commandCompound.children`，按树形（`├──` / `└──`）输出所有子命令的 literal 名与 dynamic 参数名，并以 MiniMessage 渐变着色；参数若标注为 `optional`（或父级参数可选）则以 `[<...>]` 形式显示在方括号内。帮助输出会过滤掉 `hidden` 的 literal，以及（默认 `checkPermissions = true` 时）执行者无权限的子命令节点。
- **示例**：`/chotenattribute`

### `/chotenattribute about`
- **用途**：显示 ChoTenAttribute 插件信息。
- **参数**：无。
- **权限**：`chotenattribute.command.main`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：输出三行文本：
  1. `<gradient:#84D1CB:#EFDF8F>ChoTenAttribute</gradient> <#d9d9f2>$pluginVersion` —— 名称与插件版本；
  2. `<gradient:#946ecf:#ad3aad>Running on:</gradient> <#CDAEEF>${runningPlatform.name} - $minecraftVersion` —— 运行平台与 Minecraft 版本；
  3. `<#7d36a1>Plugin by AkaCandyKAngel.` —— 作者署名。
- **示例**：`/atb about`

### `/chotenattribute reload`
- **用途**：重载插件自定义配置。
- **参数**：无。
- **权限**：`chotenattribute.command.main`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：
  - 先通过 `infoS("Reloading...")` 向控制台日志输出 INFO 级别提示（`infoS` 是 `CommandSender?` 扩展，此处以无接收者形式调用，默认输出到控制台）。
  - 调用 `ChoTenTech.reloadCustomConfig()`。该方法签名为 `reloadCustomConfig(async: Boolean = true)`，因此此处**以异步方式**执行重载，指令本身立即返回，重载内容在后台任务中完成。
  - 重载内容包括 `config.reload()`、各类配置文件重载（`baseConfig`、`currencyConfig`、`filterConfig`、`chatSettings`、`backpackConfig` 等）、再生方块 / 等级 / 段位 / 商店 / 枪械 / 医疗包 / 背包等子系统重新加载，并会清空 `ketherScriptCache`、`jsScriptCache` 等缓存。
  - 若抛出异常，捕获后通过 `severeS("<#E08A98>Error thrown. check console for info.</#E08A98>")` 输出 SEVERE 日志，并 `e.printStackTrace()` 打印堆栈。
  - 注意：本指令**不会**向执行者回复成功消息（与 `/chotentech reload` 不同，后者会回复 `reloading...` / `reloading... Complete.`）。
- **示例**：`/chotenattribute reload`

### `/chotenattribute debug ...`
- **用途**：挂载属性调试指令对象 `ChoTenAttributeDebugCommand`（源码 `command/ChoTenAttributeDebugCommand.kt`）。
- **参数**：见 `03-chotenattribute-debug-属性调试.md`。
- **权限**：`chotenattribute.command.debug`（默认 OP）。
- **执行者要求**：取决于具体子命令（`show` 仅玩家；`showplayer`、`categories` 玩家与控制台均可）。
- **行为说明**：`@CommandBody val debug = ChoTenAttributeDebugCommand` —— 以对象嵌套方式挂载，故实际完整路径为 `/chotenattribute debug <子指令>`。
- **示例**：`/atb debug show basic`

### `/chotenattribute manage ...`
- **用途**：挂载属性管理指令对象 `ChoTenAttributeManageCommand`（源码 `command/ChoTenAttributeManageCommand.kt`）。
- **参数**：见 `02-chotenattribute-manage-属性管理.md`。
- **权限**：`chotenattribute.command.manage`（默认 OP）。
- **执行者要求**：玩家 / 控制台均可。
- **行为说明**：`@CommandBody val manage = ChoTenAttributeManageCommand` —— 嵌套挂载，完整路径为 `/chotenattribute manage <子指令>`。
- **示例**：`/atb manage list Steve`

## 备注 / 注意事项

- **帮助渲染函数有两份**：本文件在末尾（第 69–153 行）定义了 `CommandComponent.createAttributeHelper(checkPermissions: Boolean = true)`，功能与 `command/MainCommand.kt` 中的 `createModernHelper` 基本相同（结构、缩进、权限过滤、`hidden` 过滤逻辑一致，仅颜色渐变不同）。但**本文件的指令体实际调用的是 `createModernHelper()`**（来自 `MainCommand.kt`），`createAttributeHelper` 在本文件内没有任何调用点，属于遗留代码。
- **根指令可用性**：`ChoTenAttributeMainCommand` 自身被 `/chotentech` 的 `attribute` 子命令引用（`MainCommand.kt` 第 74 行），因此 `/chotentech attribute debug ...`、`/chotentech attribute manage ...` 同样是有效路径；而独立根指令 `/chotenattribute`（别名 `/attribute`、`/atb`）由 `@CommandHeader` 单独注册。
- **权限三处生效**：根权限 `chotenattribute.command.main` 用于进入根指令，子命令 `debug`、`manage` 分别受 `chotenattribute.command.debug`、`chotenattribute.command.manage` 约束。TabooLib 中 `@CommandBody` 未显式声明 `permission` 时，子命令节点会继承/回落至根 header 的权限判定；帮助输出时按各节点自身的 `permission` 过滤。
- 源码中 `//    @CommandBody val Data = ChoTenDataCommand` 为被注释掉的子指令，当前不生效。
- `permissionMessage = ""`，因此权限不足时不会额外输出自定义提示文本。
