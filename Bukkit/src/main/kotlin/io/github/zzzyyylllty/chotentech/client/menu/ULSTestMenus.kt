package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSClientManager
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * 一套**专供测试**的菜单用例，覆盖框架已下发的每一项能力。
 *
 * ## 与 [ULSDemoMenu] 的分工
 *
 * [ULSDemoMenu] 是「一条端到端通路走不走得通」——一个菜单，一次点完。
 * 这里的七个菜单是「每一项能力各自正不正常」——把能力**拆开**逐个隔离，
 * 因为它们的失效方式完全不同：
 *
 * - 文字画错了 → 看一眼就知道；
 * - 条件写反了 → 元素该藏的没藏，但界面看着正常；
 * - 裁剪错了 → 滚出去的内容盖在别的元素上，不报错；
 * - 命中不同源 → 点画出来的按钮没反应，**这才是最难发现的一类**。
 *
 * 一台菜单里全塞进去，出问题时无法判断是哪一项坏的；拆开之后，
 * 「第 3 个菜单坏了」直接缩小到一个渲染分支。
 *
 * ## 只给装了模组的玩家用
 *
 * 这套菜单**没有经典轨可用**：它大量使用 grid / scroll / progress /
 * 多行 tooltip，经典轨的箱子界面表达不出来（会被
 * [ULSMenuClassicRail] 降级成「一堆纸和命名牌」）。
 * 所以 [ULSTestMenus] 的所有入口都经过 [requireMod] 门控，
 * 未装模组的玩家**打不开**，而不是看到一个残缺的箱子界面——
 * 后者会让人以为模组装坏了。
 *
 * ## 怎么用
 *
 * ```
 * /ulsmenu test            列出全部用例
 * /ulsmenu test all        依次打开全部（每个看完手动关掉再开下一个）
 * /ulsmenu test <名称>     打开指定用例
 * ```
 */
object ULSTestMenus {

    // ==================================================================
    // 用例注册表
    // ==================================================================

    /**
     * 一个测试用例。
     *
     * @param name   命令里用的短名（全小写，无空格）。
     * @param title  菜单标题，也是给管理员看的用例名。
     * @param note   这个用例**在测什么**、**坏了会看到什么**。命令列表里会打印，
     *               因为「知道该看哪里」是这类测试能不能发现问题的关键。
     * @param build  构造菜单。取 [Player] 是因为用例需要玩家当前状态
     *               （血量、名字、握手记录）才有测试价值。
     */
    class Case(
        val name: String,
        val title: String,
        val note: String,
        val build: (Player) -> ULSMenuDefinition,
    )

    /** 全部用例，顺序就是 `/ulsmenu test all` 的打开顺序（从简到繁）。 */
    val CASES: List<Case> = listOf(
        Case(
            "text", "① 文本与排版",
            "测 TEXT / SHAPE 两个绘制分支与换行、宽度截断、对齐。" +
                    "坏了会看到：文字重叠、行距不对、超出面板。",
            ::buildTextTest,
        ),
        Case(
            "buttons", "② 按钮与动作",
            "测动作回传、白名单、服务端重新判定权限。" +
                    "坏了会看到：点了没反应，或日志里出现「未登记的动作」。",
            ::buildButtonTest,
        ),
        Case(
            "conditions", "③ 条件显示与禁用",
            "测 visibleWhen / enabledWhen 的六种运算符与 all/any/not 嵌套。" +
                    "坏了最隐蔽——**界面看着完全正常**，只是该藏的元素还在。" +
                    "逐个切换开关，数与每行「应为」的标注对照。",
            ::buildConditionTest,
        ),
        Case(
            "layout", "④ 布局：grid / 嵌套 / 间距",
            "测 grid 列数、cell 宽度分配、gap、嵌套容器 padding。" +
                    "坏了会看到：列数不对、元素挤在一起或撑破容器。",
            ::buildLayoutTest,
        ),
        Case(
            "scroll", "⑤ 滚动与裁剪",
            "测偏移只减一次、scissor 裁剪矩形、命中区域与绘制同源。" +
                    "**这是最容易出沉默故障的一项**：滚出容器的内容如果照旧" +
                    "画出来，会盖在下方元素上；绘制与命中不同源则表现为" +
                    "「点画出来的按钮没反应」。滚到底逐行点，每一行都该有反应。",
            ::buildScrollTest,
        ),
        Case(
            "progress", "⑥ 进度条",
            "测 PROGRESS 的 ratio（静态字面量）与 value（变量绑定）两条取值路径。" +
                    "坏了会看到：进度条不动、满格、或颜色不对。",
            ::buildProgressTest,
        ),
        Case(
            "tooltip", "⑦ tooltip",
            "测多行 tooltip 的换行、长文本折行、空行、以及" +
                    "**容器上的 tooltip 应该被忽略**（容器不该抢悬停）。" +
                    "坏了会看到：鼠标悬停时出现半截 tooltip，或悬停整片区域都在闪。",
            ::buildTooltipTest,
        ),
    )

    /** 按短名找用例。 */
    fun byName(name: String): Case? = CASES.firstOrNull { it.name.equals(name, ignoreCase = true) }

    // ==================================================================
    // 开关状态
    // ==================================================================
    //
    // 条件测试需要**一个服务端可变、客户端可见**的变量。
    // 用不着持久化——测试菜单本来就是用完即走的。

    private val flags = ConcurrentHashMap<UUID, Boolean>()

    private fun flagOf(player: Player): Boolean = flags[player.uniqueId] ?: false

    private fun flip(player: Player): Boolean {
        val next = !flagOf(player)
        flags[player.uniqueId] = next
        return next
    }

    /** 玩家退出时清理。 */
    fun clearPlayer(player: Player) {
        flags.remove(player.uniqueId)
    }

    // ==================================================================
    // 动作
    // ==================================================================

    /**
     * 动作 ID 前缀。
     *
     * 用一个**只在测试里出现**的前缀，是为了让 `unregisterOwner` 之外
     * 还能整组清理：注册表没有「按前缀删」这种操作，所以 [unregisterAll]
     * 把这一组逐个注销。
     */
    private const val PREFIX = "ulstest."

    private const val ACTION_FLIP = "${PREFIX}flip"
    private const val ACTION_REFRESH = "${PREFIX}refresh"
    private const val ACTION_HEAL = "${PREFIX}heal"
    private const val ACTION_DAMAGE = "${PREFIX}damage"
    private const val ACTION_ECHO = "${PREFIX}echo"
    private const val ACTION_FORBIDDEN = "${PREFIX}forbidden"
    private const val ACTION_CLOSE = "${PREFIX}close"

    /** 本组全部动作 ID。 */
    private val ALL_ACTIONS = listOf(
        ACTION_FLIP, ACTION_REFRESH, ACTION_HEAL, ACTION_DAMAGE,
        ACTION_ECHO, ACTION_FORBIDDEN, ACTION_CLOSE,
    )

    /**
     * 登记全部测试动作。幂等。
     *
     * 幂等的理由与 [ULSDemoMenu.registerActions] 相同：这七个是**整组**
     * 登记的，而 [ULSMenuActionRegistry.register] 遇到重复 ID 会抛异常，
     * 于是任何一个残留都会让整组失败——先注销再登记就没这个问题。
     */
    fun registerActions() {
        unregisterAll()

        ULSMenuActionRegistry.register(
            ACTION_FLIP, owner = null,
            description = "测试：翻转条件测试用的开关",
        ) { context ->
            val next = flip(context.player)
            // 只推变量、不重发结构——这正是条件测试要验证的那条路径。
            pushData(context.player)
            context.player.sendMessage(
                "§7[§bULS§7] §f开关已切换为 §b${if (next) "开" else "关"}§f，已下发 menu_data。"
            )
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_REFRESH, owner = null,
            description = "测试：重算并下发全部变量",
        ) { context ->
            pushData(context.player)
            context.player.sendMessage("§7[§bULS§7] §7变量已重新下发。")
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_HEAL, owner = null,
            description = "测试：把血量补满（会改变变量值）",
        ) { context ->
            context.player.health = context.player.maxHealth
            pushData(context.player)
            context.player.sendMessage("§7[§bULS§7] §f血量已补满。")
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_DAMAGE, owner = null,
            description = "测试：扣 2 点血（会改变变量值）",
        ) { context ->
            val player = context.player
            // 不减到死：测试菜单把人弄死会很碍事。
            player.health = maxOf(1.0, player.health - 2.0)
            pushData(player)
            player.sendMessage("§7[§bULS§7] §f已扣血，当前 §b${player.health.toInt()}§f。")
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_ECHO, owner = null,
            description = "测试：把 payload 原样回显（验证 payload 通不通）",
        ) { context ->
            // payload 来自客户端，**完全不可信**。这里只回显给发送者自己，
            // 不解析、不拼命令、不落盘，所以没有风险面。
            val text = context.payload.ifEmpty { "（空）" }
            context.player.sendMessage("§7[§bULS§7] §fpayload = §b$text")
            true
        }

        ULSMenuActionRegistry.register(
            ACTION_FORBIDDEN, owner = null,
            description = "测试：永远拒绝的动作（验证 handler 返回 false 的表现）",
        ) { context ->
            // 演示一个**注册了但拒绝执行**的动作：白名单通过、业务拒绝。
            // 客户端点了以后界面不会有任何变化——这是刻意的，
            // 「服务端说了不」与「服务端没收到」在客户端看起来一样。
            context.player.sendMessage("§7[§bULS§7] §c这个动作被服务端拒绝了（预期行为）。")
            false
        }

        ULSMenuActionRegistry.register(
            ACTION_CLOSE, owner = null,
            description = "测试：关闭菜单",
        ) { context ->
            ULSMenuManager.close(context.player)
            true
        }
    }

    /** 注销本组全部动作。 */
    fun unregisterAll() {
        for (id in ALL_ACTIONS) ULSMenuActionRegistry.unregister(id)
    }

    /**
     * 重算该玩家的变量表并下发。
     *
     * 注意这里**重新构造整个菜单**只为拿变量表——结构一个字节都不会发。
     * 这不是浪费：它保证了「测试时显示的变量值」与「服务端此刻的真实状态」
     * 一定一致，不会出现菜单里写死一份、动作里算另一份。
     */
    private fun pushData(player: Player) {
        val variables = variablesFor(player)
        ULSMenuManager.updateData(player, currentTestMenuId(player) ?: return, variables)
    }

    /**
     * 该玩家当前打开的测试菜单 ID。
     *
     * 只为 [pushData] 服务：测试用例共用一个动作组，动作被触发时
     * 得知道往哪个菜单推数据。
     */
    private fun currentTestMenuId(player: Player): String? {
        val current = ULSMenuManager.currentMenu(player) ?: return null
        return if (current.startsWith(MENU_ID_PREFIX)) current else null
    }

    // ==================================================================
    // 变量
    // ==================================================================

    /** 用例菜单 ID 的统一前缀，也是「这是不是测试菜单」的判据。 */
    const val MENU_ID_PREFIX = "uls_test_"

    fun menuIdOf(name: String): String = MENU_ID_PREFIX + name

    /**
     * 全部变量。
     *
     * 刻意**混用**数字型与文本型，因为条件求值会先试数字、失败再按字典序比——
     * 两条路径都要有用例覆盖。`health` 是数字型，`playerName` 是文本型，
     * `toggle` 是 "true"/"false" 文本（字典序比较时 "false" < "true"，
     * 恰好与直觉相反，所以条件测试里只拿它做等值判断）。
     */
    private fun variablesFor(player: Player): Map<String, String> {
        val record = ULSClientManager.getRecord(player)
        val health = player.health.toInt()
        val maxHealth = player.maxHealth.toInt()
        return linkedMapOf(
            "playerName" to player.name,
            "toggle" to flagOf(player).toString(),
            "health" to health.toString(),
            "maxHealth" to maxHealth.toString(),
            // 血量百分比，用来喂进度条。整数百分比，避免小数位在
            // 不同客户端上显示不一致。
            "healthPercent" to if (maxHealth <= 0) "0"
            else ((health * 100) / maxHealth).toString(),
            "foodLevel" to player.foodLevel.toString(),
            "level" to player.level.toString(),
            // 握手信息：这三个能直接证明 menu_data 里的变量确实是
            // 服务端实时算的，而不是菜单打开时快照的。
            "protocolVersion" to (record?.protocolVersion?.toString() ?: "0"),
            "modVersion" to (record?.modVersion ?: "（未上报）"),
            "modCount" to (record?.mods?.size?.toString() ?: "0"),
            // 常量：用来验证「变量不存在」与「变量存在但为空」的区别。
            "emptyValue" to "",
        )
    }

    /** 每个菜单都先把变量灌进去——否则 `{playerName}` 会原样显示。 */
    private fun ULSMenus.ULSMenuRootScope.injectVariables(player: Player) {
        variablesFor(player).forEach { (name, value) -> variable(name, value) }
    }

    // ==================================================================
    // ① 文本与排版
    // ==================================================================

    private fun buildTextTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("text"), "① 文本与排版") {
            size(280, 220)
            injectVariables(player)

            text("§7[1] 基本文本：这一行没有任何属性，用默认颜色和宽度。")
            text("§7[2] 显式颜色与换行：下两行是同一条元素的 wrap 结果。")
            text(
                "这一行很长，故意写得超过容器宽度，" +
                        "用来检验客户端有没有把 wrap 属性和 maxLines 处理对——" +
                        "如果它冲出了面板右边框，就是这里坏了。",
                width = 260,
            )
            text("§7[3] 变量替换：{playerName} / {health} / {maxHealth} / {emptyValue}（空）")
            text("§7[4] 不存在的变量：{noSuchVariable}（应该原样显示，不该崩、不该空白）")

            divider(260)

            // 换行演示：三个元素，第二个带 newline，所以它和第三个
            // 各占一行，第一个单独一行。
            text("§7[5] 同一行的三个元素 →")
            text("§a第二行", width = 80).newline()
            text("§a也是第二行", width = 120)
            text("§e第三行（手动换行）", width = 200).newline()

            divider(260)

            text("§7[6] 宽度不足时的表现（30 像素的宽文本）：", width = 260)
            text("会被截断还是换行", width = 30)

            divider(260)
            text("§7[7] 关闭 shadow 的文本（应该更平、更暗）：", width = 260)
                .prop("shadow", false)
            text("§7[8] maxLines=1 的长文本（应该只显示一行并省略）：", width = 260)
            text(
                "这一行很长很长很长很长很长很长很长很长很长很长很长很长" +
                        "很长很长很长很长很长很长很长很长很长很长很长很长",
                width = 260,
            ).prop("maxLines", 1)

            divider(260)
            testFooter(player)
        }

    // ==================================================================
    // ② 按钮与动作
    // ==================================================================

    private fun buildButtonTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("buttons"), "② 按钮与动作") {
            size(280, 200)
            injectVariables(player)

            text("§7每个按钮点击后都应有一条聊天栏反馈。没有反馈 = 动作没到服务端。")
            divider(260)

            text("§7[A] 正常动作（应该回一句问候）:")
            button("打个招呼", "${PREFIX}greet", 200)
            text("§8（这个动作 ID 其实**没有**登记——用来验证白名单拒绝路径）")
            text("§8点它应该没有任何反应，这正是「未登记的动作」的正常表现。", width = 260)

            divider(260)
            text("§7[B] 服务端拒绝的动作（会回一条「被拒绝」，界面无变化）:")
            button("被拒绝的按钮", ACTION_FORBIDDEN, 200)

            divider(260)
            text("§7[C] 改状态的三个动作（同时看⑥里的进度条动不动）:")
            button("补满血量", ACTION_HEAL, 86)
            button("扣 2 点血", ACTION_DAMAGE, 86).newline()
            button("翻转测试开关", ACTION_FLIP, 86)
            button("重新下发变量", ACTION_REFRESH, 86).newline()

            divider(260)
            text("§7[D] payload 回显（客户端目前不发 payload，应为「（空）」）:")
            button("回显 payload", ACTION_ECHO, 200)

            divider(260)
            testFooter(player)
        }

    // ==================================================================
    // ③ 条件显示与禁用
    // ==================================================================

    /**
     * 这是整套测试里**最重要**的一个用例。
     *
     * 条件写错不会报错：可见性判反了，元素照常画出来或照常藏起来，
     * 界面看着都「正常」。所以这里每一条都带**文字标注它应该是什么样子**，
     * 让「看着正常」变成「可以逐行核对」。
     */
    private fun buildConditionTest(player: Player): ULSMenuDefinition {
        val on = flagOf(player)
        return ULSMenus.build(menuIdOf("conditions"), "③ 条件显示与禁用") {
            size(300, 230)
            injectVariables(player)

            text("§7开关当前 = §b$on§7。点下面的按钮翻转它，然后逐行核对。")
            button("翻转开关（下发 menu_data）", ACTION_FLIP, 220)
            divider(280)

            // ---- 等值 ----
            text("§7[==] toggle == \"true\" → 开时可见", width = 280)
                .visibleWhen(ULSMenuCondition.eq("toggle", "true"))
            text("§7[==] toggle == \"false\" → 关时可见", width = 280)
                .visibleWhen(ULSMenuCondition.eq("toggle", "false"))

            // ---- 不等 ----
            text("§7[!=] toggle != \"true\" → 关时可见", width = 280)
                .visibleWhen(ULSMenuCondition.ne("toggle", "true"))

            divider(280)

            // ---- 数值比较（health 是数字型变量，会走数字比较分支）----
            text("§7[>]  health > 15 → 满血时可见（当前 {health}）", width = 280)
                .visibleWhen(ULSMenuCondition.gt("health", "15"))
            text("§7[>=] health >= 20 → 满血时可见", width = 280)
                .visibleWhen(ULSMenuCondition.gte("health", "20"))
            text("§7[<]  health < 20 → 受伤后可见", width = 280)
                .visibleWhen(ULSMenuCondition.lt("health", "20"))
            text("§7[<=] health <= 10 → 重伤时可见", width = 280)
                .visibleWhen(ULSMenuCondition.lte("health", "10"))

            divider(280)

            // ---- 组合 ----
            text("§7[all] 开 且 满血 → 两个都成立才可见", width = 280)
                .visibleWhen(
                    ULSMenuCondition.all(
                        ULSMenuCondition.eq("toggle", "true"),
                        ULSMenuCondition.gte("health", "20"),
                    )
                )
            text("§7[any] 开 或 重伤 → 任一成立即可见", width = 280)
                .visibleWhen(
                    ULSMenuCondition.any(
                        ULSMenuCondition.eq("toggle", "true"),
                        ULSMenuCondition.lte("health", "10"),
                    )
                )
            text("§7[not] 非（关）→ 开时可见", width = 280)
                .visibleWhen(ULSMenuCondition.not(ULSMenuCondition.eq("toggle", "false")))

            divider(280)

            // ---- enabledWhen ----
            //
            // 显示与可交互是两件事：这里按钮**一直显示**，但只有开
            // 才画成亮的。关的时候点它应该没有反应——这一条最容易被
            // 误解成「按钮坏了」，所以文案直接写明。
            text("§7[enabledWhen] 下面这个按钮总在，但只在「开」时可点:", width = 280)
            button("只在开关为「开」时可点", "${PREFIX}greet", 220)
                .enabledWhen(ULSMenuCondition.eq("toggle", "true"))

            // 同时给可见与可交互条件：关的时候连见都见不到。
            text("§7[visible+enabled] 关时连见都见不到:", width = 280)
            button("（只在开时出现）", ACTION_REFRESH, 220)
                .visibleWhen(ULSMenuCondition.eq("toggle", "true"))
                .enabledWhen(ULSMenuCondition.eq("toggle", "true"))

            divider(280)
            text("§8空条件：NOT 无子条件应恒为真（这条永远可见）", width = 280)
                .visibleWhen(ULSMenuCondition.not(ULSMenuCondition.eq("never", "x")))

            divider(280)
            testFooter(player)
        }
    }

    // ==================================================================
    // ④ 布局：grid / 嵌套 / 间距
    // ==================================================================

    private fun buildLayoutTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("layout"), "④ 布局：grid / 嵌套 / 间距") {
            size(300, 230)
            injectVariables(player)

            text("§7[1] grid columns=2，cell 宽度应自动均分:")
            container(ULSMenuElementType.GRID, columns = 2, width = 260) {
                // 不显式给宽度，让客户端按列宽分配——这才是 grid 的正确用法。
                text("cell-1", width = 60)
                text("cell-2", width = 60).newline()
                text("cell-3", width = 60)
                text("cell-4", width = 60).newline()
            }

            divider(280)
            text("§7[2] grid columns=3:")
            container(ULSMenuElementType.GRID, columns = 3, width = 260) {
                button("三列 A", ACTION_REFRESH, 80)
                button("三列 B", ACTION_REFRESH, 80)
                button("三列 C", ACTION_REFRESH, 80).newline()
                button("三列 D", ACTION_REFRESH, 80)
                button("三列 E", ACTION_REFRESH, 80)
                button("三列 F", ACTION_REFRESH, 80).newline()
            }

            divider(280)
            text("§7[3] gap=16 的稀疏排列（应明显比上面松）:")
            container(ULSMenuElementType.GRID, columns = 2, width = 260) {
                text("gap-A", width = 60).prop("gap", 16)
                text("gap-B", width = 60).newline()
                text("gap-C", width = 60).prop("gap", 16)
                text("gap-D", width = 60).newline()
            }

            divider(280)
            text("§7[4] 嵌套：grid 里再套一个 grid（列宽应各自独立分配）:")
            container(ULSMenuElementType.GRID, columns = 2, width = 260) {
                text("外层左", width = 60)
                container(ULSMenuElementType.GRID, columns = 2) {
                    text("内-a", width = 28)
                    text("内-b", width = 28).newline()
                }
                text("外层右", width = 60)
            }

            divider(280)
            testFooter(player)
        }

    // ==================================================================
    // ⑤ 滚动与裁剪
    // ==================================================================

    /**
     * 最容易出沉默故障的一项，所以做得最细：三种结构、
     * 每一行都是可点元素、行号与内容一一对应，滚到底逐行点能点动即可
     * 判定「偏移/裁剪/命中」三者一致。
     */
    private fun buildScrollTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("scroll"), "⑤ 滚动与裁剪") {
            size(300, 200)
            injectVariables(player)

            text("§7滚动区高 48，只露两行多一点。滚到底逐行点，每一行都该回一条消息。")
            text("§7关键判据：滚出去的按钮**不该**盖在下面的文字上。")
            divider(280)

            // 纯滚动：没有嵌套容器，只测「偏移有没有被减对」。
            text("§7[A] 纯滚动区（每一行都是按钮）:")
            scrollArea(width = 280, height = 48) {
                button("滚动行 1", ACTION_ECHO, 280)
                button("滚动行 2", ACTION_ECHO, 280).newline()
                button("滚动行 3", ACTION_ECHO, 280).newline()
                button("滚动行 4", ACTION_ECHO, 280).newline()
                button("滚动行 5", ACTION_ECHO, 280).newline()
                button("滚动行 6", ACTION_ECHO, 280).newline()
            }

            divider(280)

            // 嵌套：grid 套在 scroll 里。偏移若被减两次，网格会以
            // 两倍速度移动并与容器方框脱开——一眼可见。
            text("§7[B] grid 套在 scroll 里（偏移若被减两次就会看出来）:")
            scrollArea(width = 280, height = 48) {
                container(ULSMenuElementType.GRID, columns = 2) {
                    button("网格 A1", ACTION_ECHO, 130)
                    button("网格 A2", ACTION_ECHO, 130).newline()
                    button("网格 A3", ACTION_ECHO, 130)
                    button("网格 A4", ACTION_ECHO, 130).newline()
                    button("网格 A5", ACTION_ECHO, 130)
                    button("网格 A6", ACTION_ECHO, 130).newline()
                }
            }

            divider(280)

            // 嵌套 scroll：内层必须在外层裁剪的基础上**取交集**，
            // 否则内层内容会顶破外层边界。
            text("§7[C] scroll 套 scroll（内层应被外层裁剪二次限制）:")
            scrollArea(width = 280, height = 60) {
                text("外层第一行", width = 260)
                container(ULSMenuElementType.SCROLL, width = 260, height = 34) {
                    text("内层行 1", width = 240).newline()
                    text("内层行 2", width = 240).newline()
                    text("内层行 3", width = 240).newline()
                }
                text("外层第二行（应出现在内层滚动区**下方**，不被内层内容盖住）", width = 260).newline()
            }

            divider(280)
            text("§7这段文字必须完整可见——如果上面有按钮盖在它上面，就是裁剪坏了。", width = 280)

            testFooter(player)
        }

    // ==================================================================
    // ⑥ 进度条
    // ==================================================================

    private fun buildProgressTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("progress"), "⑥ 进度条") {
            size(300, 220)
            injectVariables(player)

            text("§7[A] 静态 ratio（不依赖变量，五个就应该是五分之一递进）:")
            for (ratio in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
                text("ratio = $ratio", width = 80)
                // 必须显式走 element(...)：progressStatic 是伴生对象上的
                // 工厂，不是根作用域的成员——直接写一行等于**丢弃**了
                // 那个 Builder，进度条一个都不会出现，而且不报错。
                // 这正好也是本文件在测的那类问题，写在这里当反面教材。
                element(ULSMenuElement.progressStatic(ratio, 170))
            }

            divider(280)

            text("§7[B] 变量绑定：value=\"healthPercent\"（当前 {healthPercent}%）:")
            progress("healthPercent", width = 260)

            text("§7[C] 变量绑定：value=\"health\"（原始值，最大 20）:")
            progress("health", width = 260)

            divider(280)
            text("§7[D] 越界值——ratio 给 1.5 与 -0.5，应被 clamp 到两端而不是溢出:")
            element(ULSMenuElement.progressStatic(1.5f, 120))
            element(ULSMenuElement.progressStatic(-0.5f, 120))

            divider(280)
            text("§7[E] 不存在的变量名（应画成空轨道，不该崩）:")
            progress("noSuchVariable", width = 260)

            divider(280)
            text("§7点下面的按钮扣血，看 [B] [C] 两条是否立刻变化——不变就是 menu_data 没到:")
            button("扣 2 点血", ACTION_DAMAGE, 90)
            button("补满", ACTION_HEAL, 90).newline()

            divider(280)
            testFooter(player)
        }

    // ==================================================================
    // ⑦ tooltip
    // ==================================================================

    private fun buildTooltipTest(player: Player): ULSMenuDefinition =
        ULSMenus.build(menuIdOf("tooltip"), "⑦ tooltip") {
            size(300, 210)
            injectVariables(player)

            text("§7每组都把鼠标停在元素上，核对 tooltip 的行数与换行位置。")
            divider(280)

            text("§7[1] 单行 tooltip:")
                .tooltip("这是唯一的一行 tooltip")

            text("§7[2] 三行 tooltip（三行应各自成行，不该挤在一起）:")
                .tooltip("第一行", "第二行", "第三行")

            text("§7[3] 长文本 tooltip（超过面板宽度应折行，不该溢出屏幕）:")
                .tooltip(
                    "这一行很长，长到超过了面板宽度，" +
                            "用来检验客户端在 tooltip 里有没有做折行处理，",
                    "第二行仍然应该正常显示。"
                )

            text("§7[4] 含空行的 tooltip（空行应该真的空出一行）:")
                .tooltip("上面一行", "", "下面一行")

            divider(280)

            text("§7[5] 按钮上的 tooltip（悬停应显示，且不影响点击）:")
                .tooltip("这是元素自己的 tooltip")
            button("带我 tooltip 的按钮", ACTION_ECHO, 200)
                .tooltip("按钮 tooltip 第一行", "按钮 tooltip 第二行")

            divider(280)

            text("§7[6] 只有 tooltip、没有动作的文本（悬停显示、点击无反应 = 正确）:")
                .tooltip("悬停能看到我", "点我不会有任何事发生")

            divider(280)
            text("§8[7] 容器上的 tooltip：见下一个 grid。按设计**容器不该抢悬停**，", width = 280)
            text("§8所以悬停 grid 空白处不应弹出任何 tooltip。", width = 280)

            container(ULSMenuElementType.GRID, columns = 2, width = 260) {
                text("grid 内 A", width = 60).tooltip("我在 grid 里")
                text("grid 内 B", width = 60).tooltip("我也在 grid 里").newline()
            }.tooltip("我是容器自己的 tooltip（预期：不该显示）")

            divider(280)
            testFooter(player)
        }

    // ==================================================================
    // 公共尾部
    // ==================================================================

    /**
     * 每个用例末尾都有的收尾区。
     *
     * 故意把「关闭」放在最下面而不是最上面：关掉是唯一一个会让人
     * 失去上下文的操作，放在最上方容易误触。
     */
    private fun ULSMenus.ULSMenuRootScope.testFooter(player: Player) {
        text(
            "§7玩家 §b${player.name}§7 | 协议 v{protocolVersion} | 模组 {modVersion} | 组件 {modCount}",
            width = 280,
        )
        button("重新下发变量", ACTION_REFRESH, 130)
        button("关闭菜单", ACTION_CLOSE, 130).newline()
    }

    // ==================================================================
    // 打开
    // ==================================================================

    /**
     * 这个玩家能不能用测试菜单。
     *
     * 用户要求：**没装模组的玩家不允许开启**。所以这里不做降级，
     * 直接拒绝——返回 null 表示可用，否则返回给玩家看的拒绝理由。
     *
     * 判据比 [ULSMenuManager.chooseRail] 更严：不只看「装了模组」，
     * 还看菜单真正需要的能力。测试菜单大量使用 grid / scroll，
     * 一个「装了模组但能力没下发全」的客户端打开它，会看到残缺界面，
     * 而残缺界面比看不到界面更难判断是哪一层坏的。
     */
    fun rejectionReason(player: Player, case: Case): String? {
        if (!ULSClientManager.isUsingMod(player)) {
            return "你没有安装阈限天际客户端助手模组，无法使用增强菜单。" +
                    "这套测试菜单专供模组调试，未装模组时不提供降级界面。"
        }
        if (!ULSClientManager.clientSupports(player, io.github.zzzyyylllty.chotentech.client.ULSProtocol.CAP_MENU)) {
            return "握手已完成，但服务端没有下发 ${io.github.zzzyyylllty.chotentech.client.ULSProtocol.CAP_MENU} 能力。"
        }
        val menu = case.build(player)
        val missing = menu.requiredCapabilities()
            .filterNot { ULSClientManager.clientSupports(player, it) }
        if (missing.isNotEmpty()) {
            return "你的客户端缺少本用例需要的能力: ${missing.joinToString("、")}。" +
                    "（用 /ulsmenu diagnose ${player.name} 看逐项清单）"
        }
        return null
    }

    /**
     * 打开一个用例。返回拒绝理由，成功返回 null。
     *
     * **每次都重新登记动作**：[registerActions] 是幂等的，而漏登记
     * 会让「按钮点了没反应」与「框架坏了」看起来一模一样。
     */
    fun open(player: Player, case: Case): String? {
        rejectionReason(player, case)?.let { return it }
        registerActions()
        ULSMenuManager.open(player, case.build(player))
        return null
    }

    /** 全部用例的短名，供命令补全与列表展示。 */
    fun namesForCommand(): List<String> = CASES.map { it.name }
}
