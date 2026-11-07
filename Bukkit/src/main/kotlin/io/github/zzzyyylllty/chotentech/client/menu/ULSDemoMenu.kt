package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSClientManager
import org.bukkit.entity.Player

/**
 * 框架自带的示例菜单。
 *
 * ## 它的存在意义
 *
 * 这不是一个「功能」，而是一条**端到端自检路径**：从服务端构造菜单、
 * 编码、下发，到客户端解码、渲染、点回来、服务端校验白名单并执行，
 * 整条链路都能用它跑一遍。没有这个菜单的话，菜单代码就只能等
 * 第一个真实需求落地时才有机会第一次被执行——那时候一次调试
 * 要同时排查业务逻辑和框架缺陷。
 *
 * ## 它顺便演示了框架的每一项能力
 *
 * - 动态数据（变量 + `menu_data` 增量更新）
 * - 条件显示（`visibleWhen`）与条件禁用（`enabledWhen`）
 * - 布局容器（`grid`）
 * - 进度条、分隔线、tooltip
 * - 动作回传与白名单
 *
 * ## 上线前请删掉或改权限
 *
 * 在正式服务器上，这个菜单只会出现在 `/ulsmenu demo` 命令下，
 * 而那个命令需要 `chotentech.uls.admin` 权限，所以留着也无害。
 */
object ULSDemoMenu {

    const val MENU_ID = "uls_demo"

    /** 演示用的动作 ID 前缀。 */
    private const val ACTION_GREET = "demo.greet"
    private const val ACTION_TOGGLE = "demo.toggle"
    private const val ACTION_REFRESH = "demo.refresh"
    private const val ACTION_CLOSE = "demo.close"

    /** 每个玩家的开关状态，用于演示 `enabledWhen` 随变量变化。 */
    private val toggled = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>()

    /**
     * 登记演示动作。
     *
     * 在 [ULSMenuManager.open] 之前必须调用过，否则按钮会被判为
     * 「未登记的动作」而全部无效——那正好也演示了白名单是在生效的。
     *
     * ## 为什么先注销再登记
     *
     * [ULSMenuActionRegistry.register] 遇到重复 ID 会**抛异常**，
     * 这是刻意设计（两个插件抢同一个 ID 时静默覆盖，表现为
     * 「按钮有时干 A 有时干 B」，开发期就该炸出来）。
     *
     * 但这里四个动作是**一次性整组登记**的，于是任何一个残留
     * 都会让整组失败：注册到第二个时抛出来，第一个已经进去了，
     * 后两个没进去——四个按钮全废，而且命令体里抛异常的表现是
     * 「命令报了个看不懂的错」。
     *
     * 幂等化之后，重复调用（重载、命令重入、测试）都安全。
     */
    fun registerActions() {
        // 先清掉本组自己的残留，保证这次登记一定成功。
        ULSMenuActionRegistry.unregister(ACTION_GREET)
        ULSMenuActionRegistry.unregister(ACTION_TOGGLE)
        ULSMenuActionRegistry.unregister(ACTION_REFRESH)
        ULSMenuActionRegistry.unregister(ACTION_CLOSE)

        ULSMenuActionRegistry.register(
            ACTION_GREET, owner = null,
            description = "示例：回一条问候",
        ) { context ->
            context.player.sendMessage(
                "§7[§bULS§7] §f你好，§b${context.player.name}§f。" +
                        "这是通过增强菜单按钮触发的动作。"
            )
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_TOGGLE, owner = null,
            description = "示例：切换一个开关",
        ) { context ->
            val current = toggled[context.player.uniqueId] ?: false
            toggled[context.player.uniqueId] = !current
            // 改完之后推一次数据，让客户端立刻看到状态变化。
            // 这里演示的就是「结构和数据分开」的用法：
            // 没有重发整个菜单，只发了几个变量。
            val menu = build(context.player)
            ULSMenuManager.updateData(
                context.player, MENU_ID,
                menu.variables + mapOf("toggle" to (!current).toString()),
            )
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_REFRESH, owner = null,
            description = "示例：刷新面板数据",
        ) { context ->
            val menu = build(context.player)
            ULSMenuManager.updateData(context.player, MENU_ID, menu.variables)
            context.player.sendMessage("§7[§bULS§7] §7面板数据已刷新。")
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_CLOSE, owner = null,
            description = "示例：关闭菜单",
        ) { context ->
            ULSMenuManager.close(context.player)
            true
        }
    }

    /** 构建给某个玩家看的演示菜单。 */
    fun build(player: Player): ULSMenuDefinition {
        val record = ULSClientManager.getRecord(player)
        val on = toggled[player.uniqueId] ?: false

        val variables = linkedMapOf(
            "playerName" to player.name,
            "toggle" to on.toString(),
            // 变量既可以是数字也可以是字符串；条件求值时会自动选比较方式
            "ping" to (record?.protocolVersion?.toString() ?: "0"),
            "modVersion" to (record?.modVersion ?: "未安装"),
            "modCount" to (record?.mods?.size?.toString() ?: "0"),
            "health" to player.health.toInt().toString(),
            "maxHealth" to player.maxHealth.toInt().toString(),
        )

        return ULSMenus.build(MENU_ID, "ULS 菜单框架自检") {
            size(260, 240)

            variables.forEach { (k, v) -> variable(k, v) }

            text("这是一条纯文本元素。")
            text("玩家: {playerName}   血量: {health}/{maxHealth}")
            divider(260)

            // 开关状态：用条件控制「文案」和「按钮是否可点」
            text(
                "开关状态: " + if (on) "已开启" else "已关闭",
                width = 200,
            )

            container(ULSMenuElementType.GRID, columns = 2) {
                button("打个招呼", ACTION_GREET, 120)
                button("切换开关", ACTION_TOGGLE, 120)
                button(
                    "刷新数据", ACTION_REFRESH, 120,
                ).visibleWhen(ULSMenuCondition.eq("toggle", "true"))
                button("关闭菜单", ACTION_CLOSE, 120)
            }

            divider(260)
            text("进度条演示（血量）:")
            progress("health", width = 240)

            // ---- 滚动容器自检 ----
            //
            // 这一段是**给滚动逻辑准备的**，不是装饰。滚动这条路径涉及
            // 「偏移在哪一层生效」「裁剪矩形记在哪」「命中区域和绘制坐标
            // 是否同源」三件事，任何一处不一致都会表现成「点画出来的按钮
            // 没反应」这种不会报错、只能靠眼睛发现的故障。
            //
            // 所以这里刻意做成：容器高 60、内容明显超出、里面**每一行都
            // 是按钮**。滚到底之后逐个点，能点动就说明坐标、裁剪、
            // 命中三者一致；点不动或者点错行，就说明又错位了。
            //
            // 高度 60 只够露出一行多，滚动的存在感足够明显。
            divider(260)
            text("滚动容器自检（滚轮滚动，然后点里面的按钮）:")
            scrollArea(width = 240, height = 60) {
                button("滚动行 1", ACTION_GREET, 240)
                button("滚动行 2", ACTION_GREET, 240).newline()
                button("滚动行 3", ACTION_GREET, 240).newline()
                button("滚动行 4", ACTION_GREET, 240).newline()
            }

            // ---- 嵌套滚动容器自检 ----
            //
            // 网格和滚动套在一起，是最容易暴露「偏移被减了两次」的
            // 结构：外层容器滚一格，网格里的元素如果跟着滚两格，
            // 立刻就能看出来。
            divider(260)
            text("嵌套容器自检（grid 套在 scroll 里）:")
            scrollArea(width = 240, height = 60) {
                container(ULSMenuElementType.GRID, columns = 2) {
                    button("网格 A", ACTION_GREET, 110)
                    button("网格 B", ACTION_GREET, 110).newline()
                    button("网格 C", ACTION_GREET, 110)
                    button("网格 D", ACTION_GREET, 110).newline()
                    button("网格 E", ACTION_GREET, 110)
                    button("网格 F", ACTION_GREET, 110).newline()
                }
            }

            divider(260)
            text(
                "这段文字检验 tooltip。",
                width = 240,
            ).tooltip(
                "tooltip 的第一行",
                "第二行可以写说明",
                "客户端不会替服务端解释动作含义",
            )

            text(
                "模组版本: {modVersion}   已加载 mod: {modCount}",
                width = 240,
            )
        }
    }

    /** 玩家退出时清理。 */
    fun clearPlayer(player: Player) {
        toggled.remove(player.uniqueId)
    }
}
