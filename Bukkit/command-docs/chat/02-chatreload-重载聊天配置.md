# 重载聊天配置（/chatreload）

> **根指令**：`/chatreload`（别名：`/chreload`）
> **权限**：`chotentech.chat.admin`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/chat/ChatAdminCommand.kt`

## 功能概述

管理员指令，用于在服务器运行中重载插件配置，聊天系统（频道、敏感词、私聊格式）会随之刷新。
它没有子命令，执行一次即触发一次完整重载。
由管理与排障用途面向：修改 `channels/*.yml`、`filter.yml`、`chat-settings.yml` 之后无需重启服务器。

## 指令总览

| 完整指令 | 说明 | 权限 |
|---|---|---|
| `/chatreload` | 重载插件自定义配置（含聊天相关配置） | `chotentech.chat.admin` |

## 指令详解

### `/chatreload`

- **用途**：重载插件全部自定义配置，其中包含聊天系统依赖的三份配置与频道定义。
- **参数**：无。
- **权限**：`chotentech.chat.admin`（默认 OP；`permissionMessage` 为空，无权限时由框架静默拒绝）。
- **执行者要求**：**玩家 / 控制台均可**（`execute<CommandSender>`，控制台与 RCON 可执行）。
- **行为说明**：
  - 先发出提示「正在重载聊天配置...」。
  - 调用 `ChoTenTech.reloadCustomConfig()`（默认参数 `async = true`，即通过 TabooLib `submit(async)` **异步**执行重载）。
  - 成功时发出「聊天配置重载完成。」；捕获到 `Exception` 时发出「重载失败: `<异常信息>`」。
  - 实际重载的内容（`reloadCustomConfig` 内部，与聊天相关的部分）：
    - `config.reload()`（主配置，含 `debug`、`lang` 等）
    - `baseConfig` / `currencyConfig` / `filterConfig` / **`chatSettings`**（`chat-settings.yml`） / `backpackConfig` 重载
    - `TabooFilter.reload(filterConfig)` —— 敏感词表
    - **`ChannelManager.reload()`** —— 清空并重新从 `channels/` 目录读取全部 `*.yml` 频道定义
  - **副作用范围远大于「聊天」**：同一次重载还会清理方块再生缓存、Kether/JS 脚本缓存、等级与据点缓存，重新加载再生文件、Quetiapine 等级文件、电力文件、SD 职业、账号类型、等级文件、据点（hideout）、站点（stations）等。因此本命令实际上是一次「全量配置重载」，只是在聊天模块中暴露。
- **示例**：`/chatreload` → 聊天相关配置与频道定义重新加载；修改 `channels/global.yml` 后执行即可生效。

## 备注 / 注意事项

- **异步**：`reloadCustomConfig()` 默认 `async = true`，「重载完成」这条提示是在异步任务真正执行成功后发出的；不要假设命令返回即已生效。
- **失败提示会暴露异常文本**：`"重载失败: $e"` 直接把异常 `toString()` 发给执行者，可能包含文件路径等内部信息——只应授予可信管理员。
- **重载期间数据会短暂清空**：`reloadCustomConfig` 会 `clear()` 多个缓存（`ketherScriptCache`、`jsScriptCache`、`blockRegenMap`、`regenTemplates`、`levels` 等），在高负载或玩家正在进行相关操作时执行重载有风险。
- **频道重载会重置默认频道 key**：`ChannelManager.reload()` 后若原来的 `defaultKey` 不再存在且有其他频道，默认频道会被改成「按文件名排序后的第一个」。
- **玩家已选频道不会自动修正**：玩家的 `activeChannel` 存在数据库里；若该 key 在重载后消失，`/chat` 的显示会回退到默认频道，但数据库中的旧值不会被改写。
- **无参数、无子命令**：没有 `/chatreload confirm` 之类的二次确认，也没有「只重载聊天」的细分入口。
