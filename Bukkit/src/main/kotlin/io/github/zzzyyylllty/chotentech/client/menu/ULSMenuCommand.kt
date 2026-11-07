package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSClientManager
import io.github.zzzyyylllty.chotentech.client.ULSProtocol
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

/**
 * `/ulsmenu` —— 菜单框架的排障入口。
 *
 * ## 为什么需要它
 *
 * 菜单是「服务端说、客户端画」的结构，出问题时现象往往是
 * 「界面没出来」或「按钮点了没反应」，而这两种现象的原因可能相隔很远
 * （通道没注册 / 握手没过 / 能力没下发 / 动作没登记）。
 * 这个命令把每一层的判断单独暴露出来，避免靠猜。
 *
 * 权限：`chotentech.uls.menu`（默认 OP）
 */
@CommandHeader(
    name = "ulsmenu",
    aliases = ["ulsm"],
    permission = "chotentech.uls.menu",
    description = "菜单框架的诊断与演示",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object ULSMenuCommand {

    /** `demo` 子命令所需的权限，与查看类子命令区分开。 */
    private const val ADMIN_PERMISSION = "chotentech.uls.admin"

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§7[§bULS§7] §f菜单框架状态:")
            sender.sendMessage(" §7服务端已实现的能力:")
            for (capability in SERVER_CAPABILITIES) {
                sender.sendMessage("   §8- §f$capability")
            }
            sender.sendMessage(" §7已登记的动作: §b${ULSMenuActionRegistry.ids().size} §7个" +
                    "（§b/ulsmenu actions§7 查看）")
            sender.sendMessage(" §7命令: §b/ulsmenu diagnose <玩家> §7| §b/ulsmenu demo <玩家> " +
                    "§7| §b/ulsmenu test [用例] §7| §b/ulsmenu actions")
        }
    }

    /**
     * 逐层报告某个玩家能不能用增强菜单。
     *
     * 输出顺序刻意与实际判定顺序一致——这样「第一个不成立的判据」
     * 就是原因，不需要再往下看。
     */
    @CommandBody
    val diagnose = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, context, _ ->
                val player = Bukkit.getPlayerExact(context["player"])
                if (player == null) {
                    sender.sendMessage("§7[§bULS§7] §c找不到在线玩家 ${context["player"]}")
                    return@execute
                }

                sender.sendMessage("§7[§bULS§7] §f诊断 §b${player.name}§f:")

                val usingMod = ULSClientManager.isUsingMod(player)
                sender.sendMessage(step(1, "已连接模组并完成握手", usingMod))
                if (!usingMod) {
                    sender.sendMessage("   §8→ 服务端从未收到该玩家的握手。")
                    sender.sendMessage("   §8→ 若玩家确实装了模组，检查通道是否注册成功" +
                            "（启动日志里应有「ULS 客户端握手通道已注册」）。")
                    return@execute
                }

                val supportsMenu = ULSClientManager.clientSupports(player, ULSProtocol.CAP_MENU)
                sender.sendMessage(step(2, "已下发 ${ULSProtocol.CAP_MENU} 能力", supportsMenu))
                if (!supportsMenu) {
                    sender.sendMessage("   §8→ 服务端没有声明菜单能力，玩家会走经典轨。")
                    return@execute
                }

                val record = ULSClientManager.getRecord(player)
                sender.sendMessage(
                    " §7协议版本: §f${record?.protocolVersion} " +
                            "§7| 模组版本: §f${record?.modVersion}"
                )

                // 逐项列出每个组件能力是否可用——菜单能否用增强轨
                // 取决于「它用到的每一项」都可用，所以这里要把候选列全。
                sender.sendMessage(" §7组件能力:")
                for (capability in COMPONENT_CAPABILITIES) {
                    val ok = ULSClientManager.clientSupports(player, capability)
                    val mark = if (ok) "§a✔" else "§c✘"
                    sender.sendMessage("   $mark §f$capability")
                }

                val current = ULSMenuManager.currentMenu(player)
                sender.sendMessage(" §7当前打开的菜单: §f${current ?: "（无）"}")
            }
        }
    }

    /** 打开示例菜单。 */
    @CommandBody
    val demo = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val player = sender as? Player
            if (player == null) {
                sender.sendMessage("§7[§bULS§7] §c请由玩家执行，或使用 /ulsmenu demo <玩家>。")
                return@execute
            }
            openDemo(sender, player)
        }
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, context, _ ->
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    sender.sendMessage("§7[§bULS§7] §c你没有权限替其他玩家打开菜单。")
                    return@execute
                }
                val player = Bukkit.getPlayerExact(context["player"])
                if (player == null) {
                    sender.sendMessage("§7[§bULS§7] §c找不到在线玩家 ${context["player"]}")
                    return@execute
                }
                openDemo(sender, player)
            }
        }
    }

    /** 列出所有已登记的动作，用于核对 actionId 有没有写错。 */
    @CommandBody
    val actions = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val ids = ULSMenuActionRegistry.ids()
            if (ids.isEmpty()) {
                sender.sendMessage("§7[§bULS§7] §7尚未登记任何菜单动作。")
                return@execute
            }
            sender.sendMessage("§7[§bULS§7] §f已登记的菜单动作 (§b${ids.size}§f):")
            for (id in ids.sorted()) {
                val description = ULSMenuActionRegistry.get(id)?.description.orEmpty()
                sender.sendMessage(" §8- §b$id" + if (description.isNotEmpty()) " §7$description" else "")
            }
        }
    }

    /**
     * 全套能力测试用例。
     *
     * ## 与 `demo` 的区别
     *
     * `demo` 是「一条通路走不走得通」，一个菜单一次点完；
     * `test` 是「每一项能力各自正不正常」，把能力拆开逐个隔离成因。
     *
     * ## 未装模组不允许打开
     *
     * 这是刻意的：测试菜单大量使用 grid / scroll / progress / 多行
     * tooltip，经典轨的箱子界面表达不出来（会被降级成「一堆纸和命名牌」）。
     * 给出一个残缺界面会让人以为模组装坏了，比直接说「你需要装模组」
     * 更难排查。所以这里的拒绝是**硬门控**，不降级。
     */
    @CommandBody
    val test = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val player = sender as? Player
            if (player == null) {
                // 控制台/RCON 没有自己的客户端，只能看列表——但列表也
                // 需要说明用哪个玩家名，所以直接给出用法。
                sender.sendMessage("§7[§bULS§7] §f测试用例（请用 /ulsmenu test <名称> <玩家> 打开）:")
                listTestCases(sender)
                return@execute
            }
            // 无参数：玩家自己执行 → 只列清单。不自动打开第一个，
            // 因为「打开哪个」是判断的前提，替玩家选会让结果无法归因。
            sender.sendMessage("§7[§bULS§7] §f测试用例（用 §b/ulsmenu test <名称>§f 打开）:")
            listTestCases(sender)
        }

        dynamic("name") {
            suggestion<CommandSender> { _, _ -> ULSTestMenus.namesForCommand() + "all" }
            execute<CommandSender> { sender, context, _ ->
                val player = sender as? Player
                if (player == null) {
                    sender.sendMessage("§7[§bULS§7] §c请由玩家执行，或使用 /ulsmenu test <名称> <玩家>。")
                    return@execute
                }
                openTestCase(sender, player, context["name"])
            }

            dynamic("player") {
                suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                execute<CommandSender> { sender, context, _ ->
                    if (!sender.hasPermission(ADMIN_PERMISSION)) {
                        sender.sendMessage("§7[§bULS§7] §c你没有权限替其他玩家打开菜单。")
                        return@execute
                    }
                    val player = Bukkit.getPlayerExact(context["player"])
                    if (player == null) {
                        sender.sendMessage("§7[§bULS§7] §c找不到在线玩家 ${context["player"]}")
                        return@execute
                    }
                    openTestCase(sender, player, context["name"])
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private fun listTestCases(sender: CommandSender) {
        for (case in ULSTestMenus.CASES) {
            sender.sendMessage(" §8- §b${case.name} §7${case.title}")
            sender.sendMessage("   §8${case.note}")
        }
        sender.sendMessage(" §7另有 §ball§7 表示依次说明全部用例的打开顺序。")
    }

    /**
     * 打开一个用例。
     *
     * 无论名称有没有拼对，**先做模组门控**再报「找不到用例」——
     * 否则一个没装模组的玩家会先看到「用例名写错了」，去改名字，
     * 还是打不开，白跑一轮。
     */
    private fun openTestCase(sender: CommandSender, player: Player, name: String) {
        if (name.equals("all", ignoreCase = true)) {
            listTestCases(sender)
            sender.sendMessage(" §7按上面的顺序逐个打开即可（一次只开一个，看完关掉再开下一个）。")
            return
        }

        val case = ULSTestMenus.byName(name)
        if (case == null) {
            sender.sendMessage("§7[§bULS§7] §c没有名为 §f$name §c的测试用例。")
            listTestCases(sender)
            return
        }

        val rejection = ULSTestMenus.open(player, case)
        if (rejection != null) {
            sender.sendMessage("§7[§bULS§7] §c无法打开测试用例「${case.title}」:")
            sender.sendMessage(" §c$rejection")
            if (sender !== player) {
                sender.sendMessage(" §8→ 该消息只发给你，玩家自己看不到。")
            }
            return
        }

        sender.sendMessage("§7[§bULS§7] §f已打开测试用例 §b${case.title}§f（菜单 ID §b${ULSTestMenus.menuIdOf(case.name)}§f）。")
        sender.sendMessage(" §8${case.note}")

        // 走不到经典轨是**预期**的：门控已经挡在前面了。这里再报一次
        // 是为了万一将来有人绕过门控调用，现象能立刻指向原因。
        val rail = ULSMenuManager.currentMenu(player)
        if (rail == null) {
            sender.sendMessage(" §8→ 菜单没有下发成功，检查启动日志里的编码告警。")
        }
    }

    // ------------------------------------------------------------------

    private fun openDemo(sender: CommandSender, player: Player) {
        // 每次都重新登记。
        //
        // [ULSDemoMenu.registerActions] 现在是幂等的（先注销再注册），
        // 所以这里不需要「探测一下有没有登记过」。
        //
        // 以前那个探测本身就有一个缺口：它只查了 `demo.greet` 一个 ID，
        // 而四个动作是整组登记的。只要出现过「greet 还在、toggle 没了」
        // 这种部分残留（unregister 单个、或 unregisterOwner 之后
        // owner 不匹配），探测就会以为已经登记好、跳过注册，
        // 然后 `toggle` 按钮永远点不动。
        ULSDemoMenu.registerActions()

        val menu = ULSDemoMenu.build(player)
        val rail = ULSMenuManager.open(player, menu)

        val railName = when (rail) {
            ULSMenuRail.ENHANCED -> "§a增强轨（客户端原生界面）"
            ULSMenuRail.CLASSIC -> "§e经典轨（原版箱子界面）"
        }
        sender.sendMessage("§7[§bULS§7] §f已为 §b${player.name} §f打开示例菜单，使用 $railName§f。")

        if (rail == ULSMenuRail.CLASSIC && ULSClientManager.isUsingMod(player)) {
            sender.sendMessage(" §8→ 该玩家装了模组但走了经典轨，用 /ulsmenu diagnose ${player.name} 查原因。")
        }
    }

    private fun step(index: Int, label: String, ok: Boolean): String =
        " $index. " + (if (ok) "§a✔ §f" else "§c✘ §f") + label

    /**
     * 服务端当前声明的能力。
     *
     * 与 `ULSClientManager.currentCapabilities()` 是同一份清单，
     * 但那边是私有的，这里为了诊断展示单独列一份。
     * **改一处必须改另一处**——不一致会让诊断结果撒谎，
     * 而一个会撒谎的诊断工具比没有诊断工具更糟。
     */
    private val SERVER_CAPABILITIES = listOf(
        ULSProtocol.CAP_MENU,
        ULSProtocol.CAP_TEXT,
        ULSProtocol.CAP_SHAPE,
        ULSProtocol.CAP_BUTTON,
        ULSProtocol.CAP_IMAGE,
        ULSProtocol.CAP_GRID,
        ULSProtocol.CAP_TOOLTIP,
        ULSProtocol.CAP_PROGRESS,
        // SCROLL 曾经漏在这里。它不是「多列一项」的问题：主命令打印的
        // 是「服务端已实现的能力」，而滚动容器是**真的**下发了的
        // （COMPONENT_CAPABILITIES 里有它、currentCapabilities() 里也有）。
        // 于是 /ulsmenu 的输出与 /ulsmenu diagnose 的逐项清单互相矛盾，
        // 而一个会撒谎的诊断工具比没有诊断工具更糟。
        ULSProtocol.CAP_SCROLL,
    )

    /**
     * 逐项诊断用的组件能力清单。
     *
     * <p>这份清单必须与 {@code ULSClientManager.currentCapabilities()} 保持一致。
     * 曾经这里多列了 CHECKBOX / TEXTFIELD / SLOT / CHART 四项——
     * 那四项是**服务端刻意不下发**的（模组侧还没实现交互），
     * 于是诊断永远打印一排 ✘，看起来像客户端不支持，
     * 实际是服务端根本没打算提供。</p>
     *
     * <p>顺带一提 SCROLL 是**下发**的：模组侧的渲染器确实实现了
     * 滚动容器。所以它在这份清单里，而另外四个不在。</p>
     */
    private val COMPONENT_CAPABILITIES = listOf(
        ULSProtocol.CAP_TEXT,
        ULSProtocol.CAP_SHAPE,
        ULSProtocol.CAP_BUTTON,
        ULSProtocol.CAP_IMAGE,
        ULSProtocol.CAP_GRID,
        ULSProtocol.CAP_TOOLTIP,
        ULSProtocol.CAP_PROGRESS,
        ULSProtocol.CAP_SCROLL,
    )
}
