package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.command.createModernHelper
import io.github.zzzyyylllty.chotentech.data.HandStamina
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.feature.gun.item.GunItemProcessors
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.ItemTagType
import taboolib.module.nms.getItemTag
import java.util.Locale

/**
 * 枪械系统 P0 调试命令。
 *
 * 注册方式与本项目其它指令一致：本对象带 [CommandHeader]，由 TabooLib 在
 * `LifeCycle.ENABLE` 阶段自动扫描注册（也可以直接用 `/cttgun ...`）；
 * 同时 `MainCommand` 里用 `@CommandBody val gun = GunDebugCommand` 把它挂到
 * `/ctt gun ...` 下（与 `val debug = ChoTenDebugCommand`、`val attribute = ...` 同一种写法）。
 *
 * 权限节点：`chotentech.command.gun`（OP 默认可用）。
 *
 * | 子命令 | 作用 |
 * |---|---|
 * | `/ctt gun info [id]` | 注册表概览 / 单个 ID 的解析结果 |
 * | `/ctt gun give <id> [n]` | 用 Sertraline 构建物品 |
 * | `/ctt gun fill <ammo> [n]` | 往手持枪械的 `magAmmo` 压弹（调试） |
 * | `/ctt gun state` | 打印手持枪械的 NBT + 运行时状态 |
 * | `/ctt gun reload` | 跳过读条瞬间完成一次标准换弹 |
 * | `/ctt gun menu` | 打开手持物对应的菜单 |
 * | `/ctt gun migrate-crossbow [--apply]` | **P2 新增**：旧弩弹匣 `cmag` → `sertraline_data.mag` 迁移 |
 */
@CommandHeader(
    name = "chotentech-gun",
    aliases = ["cttgun"],
    permission = "chotentech.command.gun",
    description = "枪械系统调试指令",
    permissionMessage = "",
    permissionDefault = PermissionDefault.OP,
    newParser = false,
)
object GunDebugCommand {

    private const val PERMISSION = "chotentech.command.gun"

    @CommandBody
    val main = mainCommand { createModernHelper() }

    // ==================== /ctt gun info ====================

    /**
     * `/ctt gun info`          → 注册表总量与各段数量
     * `/ctt gun info <id>`     → 输出该 Sertraline ID 解析后的属性表
     */
    @CommandBody(permission = PERMISSION)
    val info = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent(summary())
        }
        dynamic("sertraline_id") {
            execute<CommandSender> { sender, context, _ ->
                sender.sendComponent(describe(context["sertraline_id"]))
            }
            suggestion<CommandSender> { _, _ -> GunRegistry.allIds() }
        }
    }

    // ==================== /ctt gun give ====================

    /**
     * `/ctt gun processors` → 打印段落处理器的实际注册状态（排障用）。
     *
     * 「物品没有名称/描述/模型」几乎总是这一段出了问题，所以把它做成可直接查看的一行：
     * - 外部登记表里有没有我们那 6 个段落；
     * - Sertraline 的 live 处理器表里有没有同名条目（决定 `processItem` 会不会真的调用）；
     * - 现场 build 一次示例枪，报告名称/描述/模型到底有没有落上去。
     */
    @CommandBody(permission = PERMISSION)
    val processors = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent(processorReport())
        }
    }

    private fun processorReport(): String = buildString {
        appendLine("<#8FD4A6>== 物品段落处理器 ==")
        val plugin = try {
            org.bukkit.Bukkit.getPluginManager().getPlugin("Sertraline")
        } catch (_: Throwable) {
            null
        }
        appendLine(
            "<#A9B4C3>Sertraline <#F2F5FA>${plugin?.description?.version ?: "未安装"}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>API 版本 <#F2F5FA>${runCatching { Sertraline.api().version.toString() }.getOrElse { "不可用" }}"
        )
        val external = try {
            Sertraline.api().registeredItemProcessors()
        } catch (e: Throwable) {
            appendLine("<#E08A98>读取外部登记表失败: ${e::class.simpleName}: ${e.message}")
            emptyList()
        }
        val live = try {
            Sertraline.api().itemManager().listProcessors()
        } catch (_: Throwable) {
            emptyList()
        }
        appendLine("<#A9B4C3>外部登记 <#F2F5FA>$external")
        appendLine("<#A9B4C3>live 处理器 <#F2F5FA>$live")
        val menuKind = if (GunDialog.supported()) "<#8FD4A6>对话框（Dialog）" else "<#EFDF8F>箱子界面（老服务端回退）"
        appendLine("<#A9B4C3>枪械菜单 <#F2F5FA>$menuKind")
        val missing = GunItemProcessors.segmentNames().filter { it !in live }
        if (missing.isEmpty()) {
            appendLine("<#8FD4A6>✓ 6 个段落全部挂在 live 表上")
        } else {
            appendLine("<#E08A98>✗ 未挂上 live 表的段落（名称/描述/模型不会生效）: $missing")
        }
        append("<#A9B4C3>现场构建探测: ")
        append(GunItemProcessors.probe("ofn_pistol"))
    }

    /**
     * `/ctt gun give <sertraline_id> [amount]` → 用 Sertraline 构建物品发给执行者。
     */
    @CommandBody(permission = PERMISSION)
    val give = subCommand {
        dynamic("sertraline_id") {
            execute<CommandSender> { sender, context, _ ->
                giveItem(sender, context["sertraline_id"], 1)
            }
            dynamic("amount") {
                execute<CommandSender> { sender, context, _ ->
                    giveItem(sender, context["sertraline_id"], context["amount"].toIntOrNull() ?: -1)
                }
                suggestion<CommandSender> { _, _ -> listOf("1", "8", "16", "32", "64") }
            }
            suggestion<CommandSender> { _, _ -> GunRegistry.allIds() }
        }
    }

    // ==================== /ctt gun fill ====================

    /**
     * `/ctt gun fill <ammo_id> [count]` → 把 count 发子弹写进手持枪械的 `magAmmo`
     * （默认 8 发）。P2 换弹系统完成前的唯一手测手段。
     */
    @CommandBody(permission = PERMISSION)
    val fill = subCommand {
        dynamic("ammo_id") {
            execute<CommandSender> { sender, context, _ ->
                fillMagazine(sender, context["ammo_id"], 8)
            }
            dynamic("count") {
                execute<CommandSender> { sender, context, _ ->
                    fillMagazine(sender, context["ammo_id"], context["count"].toIntOrNull() ?: -1)
                }
                suggestion<CommandSender> { _, _ -> listOf("1", "8", "16", "30") }
            }
            suggestion<CommandSender> { _, _ -> GunRegistry.allIds() }
        }
    }

    // ==================== /ctt gun state ====================

    /**
     * `/ctt gun state` → 打印手持枪械的原始 NBT、解析后的 [GunState] 与 [PlayerGunState]。
     */
    @CommandBody(permission = PERMISSION)
    val state = subCommand {
        execute<CommandSender> { sender, _, _ ->
            dumpState(sender)
        }
    }

    // ==================== /ctt gun reload ====================

    /**
     * `/ctt gun reload` → 对手持枪械**瞬间完成一次**标准换弹。
     *
     * 这里的「跳过读条」是**调试专用**：正常玩法里 Q 换弹要走 `usage.reload` 的读条，
     * 手测「枪械 ↔ 弹匣」之间那套 NBT 往返时没必要每次都等两秒。
     */
    @CommandBody(permission = PERMISSION)
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            instantReload(sender)
        }
    }

    // ==================== /ctt gun menu ====================

    /**
     * `/ctt gun menu` → 打开当前手持物对应的菜单（枪 → 武器操作，弹匣 → 弹匣操作）。
     *
     * 等价于按 F，但不用真的去按（写文档截图、排查 GUI 时很方便）。
     */
    @CommandBody(permission = PERMISSION)
    val menu = subCommand {
        execute<CommandSender> { sender, _, _ ->
            openMenu(sender)
        }
    }

    // ==================== /ctt gun migrate-crossbow ====================

    /**
     * `/ctt gun migrate-crossbow [--apply]` —— 旧弩弹匣 → 新 `sertraline_data.mag` 的迁移。
     *
     * 命令名：Kotlin 属性名不能带 `-`，所以属性叫 [migrate]，
     * 由 `@CommandBody(aliases = ["migrate-crossbow", "migratecrossbow"])` 提供 kebab-case 写法。
     * 参数 `--apply` 也接受裸写 `apply`（防止个别客户端 / 输入法把 `--` 吃掉）。
     *
     * 默认 **dry-run**：只统计、只打印，**不碰任何物品**；
     * 加 `--apply` 才写回 NBT 并 `updateInventory()`。
     */
    @CommandBody(permission = PERMISSION, aliases = ["migrate-crossbow", "migratecrossbow"])
    val migrate = subCommand {
        execute<CommandSender> { sender, _, _ ->
            migrateLegacyMagazines(sender, apply = false)
        }
        dynamic("flag") {
            execute<CommandSender> { sender, context, _ ->
                migrateLegacyMagazines(sender, apply = isApplyFlag(context["flag"]))
            }
            suggestion<CommandSender> { _, _ -> listOf("--apply") }
        }
    }

    // ==================== /ctt gun armor / repairarmor ====================

    @CommandBody(permission = PERMISSION)
    val armor = subCommand {
        execute<CommandSender> { sender, _, _ -> showArmor(sender) }
    }

    @CommandBody(permission = PERMISSION)
    val repairarmor = subCommand {
        execute<CommandSender> { sender, _, _ -> repairArmor(sender) }
    }

    // ==================== 输出 ====================

    private fun showArmor(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行")
            return
        }
        val pieces = ArmorManager.pieces(player)
        if (pieces.isEmpty()) {
            sender.sendComponent("<#A9B4C3>没有装备已登记的 ChoTenTech 护甲")
            return
        }
        sender.sendComponent(buildString {
            appendLine("<#84D1CB>■ <#EFDF8F>当前护甲")
            for (piece in pieces) {
                val coverage = listOf("HEAD", "BODY", "STOMACH", "LEGS", "ARMS")
                    .filter { ArmorManager.covers(piece, it) }
                appendLine(
                    "  <#EFDF8F>${piece.slot}<#A9B4C3> ${piece.data.id}" +
                        " <#6E7A8C>|</#6E7A8C> 防御 <#F2F5FA>${num(piece.data.defense)}" +
                        " <#A9B4C3>等级 <#F2F5FA>${num(piece.data.defenseLevel)}"
                )
                appendLine(
                    "    <#A9B4C3>耐久 <#F2F5FA>${num(piece.durability)}/${num(piece.maxDurability)}" +
                        " <#A9B4C3>覆盖 <#F2F5FA>${coverage.joinToString(",")}" +
                        " <#A9B4C3>材质 <#F2F5FA>${ArmorManager.material(piece.data.material).name}"
                )
            }
        }.trimEnd())
    }

    private fun repairArmor(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行")
            return
        }
        val held = player.inventory.itemInMainHand
        val data = GunRegistry.armorOf(held) ?: run {
            sender.sendComponent("<#E08A98>主手不是已登记的护甲")
            return
        }
        if (ArmorManager.durability(held, data) > 0.0) {
            sender.sendComponent("<#DFBD7C>只有耐久为 0 的护甲才能维修")
            return
        }
        val repairName = ArmorManager.material(data.material).repairMaterial
        val repairType = try {
            Material.valueOf(repairName.uppercase())
        } catch (_: Exception) {
            null
        } ?: run {
            sender.sendComponent("<#E08A98>无效的维修材料：$repairName")
            return
        }
        if (!player.inventory.containsAtLeast(ItemStack(repairType), 2)) {
            sender.sendComponent("<#E08A98>缺少维修材料：<#EFDF8F>$repairName ×2")
            return
        }
        player.inventory.removeItem(ItemStack(repairType, 2))
        if (!ArmorManager.repair(held)) {
            player.inventory.addItem(ItemStack(repairType, 2))
            sender.sendComponent("<#E08A98>维修失败，材料已返还")
            return
        }
        player.inventory.setItemInMainHand(held)
        sender.sendComponent(
            "<#8FD4A6>护甲已修至 50%：<#EFDF8F>${num(ArmorManager.durability(held, data))}" +
                "<#A9B4C3>/${num(data.durability)}，消耗 $repairName ×2"
        )
    }

    /** 注册表概览：总条目 + 各段数量。 */
    private fun summary(): String {
        if (GunRegistry.size() == 0) {
            return buildString {
                appendLine("<#84D1CB>■ <#EFDF8F>枪械注册表 <#A9B4C3>| <#E08A98>空")
                append("<#A9B4C3>还没有任何 gun / mag / ammo / addon / armor 段被加载：")
                append("确认 Sertraline 已加载物品，然后执行 <#EFDF8F>/ctt reload<#A9B4C3> 重建注册表。")
            }
        }
        return buildString {
            appendLine("<#84D1CB>■ <#EFDF8F>枪械注册表 <#A9B4C3>| 总条目 <#F2F5FA>${GunRegistry.size()}")
            appendLine("  <#A9B4C3>gun   <#F2F5FA>${GunRegistry.gunCount()}")
            appendLine("  <#A9B4C3>mag   <#F2F5FA>${GunRegistry.magCount()}")
            appendLine("  <#A9B4C3>ammo  <#F2F5FA>${GunRegistry.ammoCount()}")
            appendLine("  <#A9B4C3>addon <#F2F5FA>${GunRegistry.addonCount()}")
            append("  <#A9B4C3>armor <#F2F5FA>${GunRegistry.armorCount()}")
        }
    }

    /**
     * 输出某个 Sertraline ID 的解析结果。同一个 ID 可能同时命中多段（理论上），
     * 所以这里逐段追加而不是「命中即返回」。
     */
    private fun describe(id: String): String {
        val lines = mutableListOf<String>()
        GunRegistry.gun(id)?.let { lines += gunLines(it) }
        GunRegistry.mag(id)?.let { lines += magLines(it) }
        GunRegistry.ammo(id)?.let { lines += ammoLines(it) }
        GunRegistry.addon(id)?.let { lines += addonLines(it) }
        GunRegistry.armor(id)?.let { lines += armorLines(it) }

        if (lines.isEmpty()) {
            return "<#E08A98>未找到 <#EFDF8F>$id <#E08A98>的 gun / mag / ammo / addon / armor 配置" +
                "<#A9B4C3>（若刚改过物品配置，先执行 <#EFDF8F>/ctt reload<#A9B4C3> 重建注册表）"
        }
        return lines.joinToString("\n")
    }

    private fun gunLines(gun: GunData): String {
        val a = gun.attributes
        val u = gun.usage
        val ticksPerShot = if (a.fireRate > 0.0) 1200.0 / a.fireRate else -1.0
        return buildString {
            appendLine("<#84D1CB>■ <#EFDF8F>GUN <#A9B4C3>| <#F2F5FA>${gun.id}")
            appendLine(
                "  <#A9B4C3>口径 <#EFDF8F>${gun.caliber}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>类别 <#EFDF8F>${gun.category}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>枪型 <#EFDF8F>${gun.gunType}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>全自动 <#EFDF8F>${gun.auto}"
            )
            appendLine(
                "  <#A9B4C3>射速 <#EFDF8F>${num(a.fireRate)} RPM" +
                    " <#6E7A8C>(≈${if (ticksPerShot < 0.0) "N/A" else num(ticksPerShot)} tick/发)</#6E7A8C>" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>弹速 <#EFDF8F>${num(a.bulletSpeed)}"
            )
            appendLine(
                "  <#A9B4C3>精度 <#EFDF8F>${num(a.accuracy)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>射程 <#EFDF8F>${num(a.range)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>枪管 <#EFDF8F>${num(a.barrelLength)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>机械效率 <#EFDF8F>${num(a.machineEfficiency)}"
            )
            appendLine(
                "  <#A9B4C3>垂直后坐 <#EFDF8F>${num(a.recoilV)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>水平后坐 <#EFDF8F>${num(a.recoilH)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>耐久 <#EFDF8F>${num(a.durability)}"
            )
            appendLine("  <#A9B4C3>后坐曲线 <#F2F5FA>${patternText(gun.recoilPattern)}")
            appendLine("  <#A9B4C3>水平抖动 <#F2F5FA>${patternText(gun.shakePattern)}")
            appendLine(
                "  <#A9B4C3>模式 <#F2F5FA>${listText(gun.modes)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>点射发数 <#EFDF8F>${gun.burst}"
            )
            appendLine("  <#A9B4C3>配件槽 <#F2F5FA>${listText(gun.addonSlots)}")
            appendLine(
                "  <#A9B4C3>过热 容量 <#EFDF8F>${num(gun.heatCapacity)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>每发 <#EFDF8F>${num(gun.heatPerShot)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>每tick冷却 <#EFDF8F>${num(gun.heatCoolPerTick)}" +
                    " <#6E7A8C>|</#6E7A8C> <#A9B4C3>卡壳率 <#EFDF8F>${num(gun.jamChance)}%"
            )
            appendLine(
                "  <#A9B4C3>usage(s) <#F2F5FA>装填 ${num(u.reload)} / 快速装填 ${num(u.reloadFast)}" +
                    " / 卸弹匣 ${num(u.removeMag)} / 拉栓 ${num(u.bolt)}"
            )
            appendLine(
                "  <#A9B4C3>usage(s) <#F2F5FA>查膛 ${num(u.inspectChamber)} / 检视弹匣 ${num(u.inspectMag)}" +
                    " / 压弹 ${num(u.insertAmmo)} / 退弹 ${num(u.removeAmmo)}"
            )
            append(
                "  <#A9B4C3>usage(s) <#F2F5FA>修理 ${num(u.fix)} / 切模式 ${num(u.fireMode)}" +
                    " / 拔枪 ${num(u.draw)}"
            )
        }
    }

    private fun magLines(mag: MagData): String = buildString {
        appendLine("<#84D1CB>■ <#EFDF8F>MAG <#A9B4C3>| <#F2F5FA>${mag.id}")
        appendLine(
            "  <#A9B4C3>口径 <#EFDF8F>${mag.caliber}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>容量 <#EFDF8F>${mag.capacity}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>装填速度 <#EFDF8F>${num(mag.loadingSpeed)}"
        )
        appendLine(
            "  <#A9B4C3>机械效率 <#EFDF8F>${num(mag.machineEfficiency)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>检视精度 <#EFDF8F>${mag.ammoInspectionAccuracy}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>检视耗时 <#EFDF8F>${num(mag.ammoInspectionTime)}s"
        )
        append("  <#A9B4C3>允许快速装填 <#EFDF8F>${mag.allowFastReload}")
    }

    private fun ammoLines(ammo: AmmoData): String = buildString {
        appendLine("<#84D1CB>■ <#EFDF8F>AMMO <#A9B4C3>| <#F2F5FA>${ammo.id}")
        appendLine(
            "  <#A9B4C3>口径 <#EFDF8F>${ammo.caliber}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>伤害 <#EFDF8F>${num(ammo.damage)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>穿甲 <#EFDF8F>${num(ammo.penetration)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>破甲伤害 <#EFDF8F>${num(ammo.armorDamage)}"
        )
        appendLine(
            "  <#A9B4C3>精度修正 <#EFDF8F>${num(ammo.accuracy)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>后坐 <#EFDF8F>${num(ammo.recoil)}%" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>射速系数 <#EFDF8F>${num(ammo.speedMult)}"
        )
        appendLine(
            "  <#A9B4C3>卡壳率 <#EFDF8F>${num(ammo.stuckChance)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>暴击率 <#EFDF8F>${num(ammo.critChance)}"
        )
        append("  <#A9B4C3>弹丸数 <#EFDF8F>${ammo.pellets}")
    }

    private fun addonLines(addon: AddonData): String = buildString {
        appendLine("<#84D1CB>■ <#EFDF8F>ADDON <#A9B4C3>| <#F2F5FA>${addon.id}")
        appendLine(
            "  <#A9B4C3>槽位 <#EFDF8F>${addon.slot}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>适配枪型 <#F2F5FA>${listText(addon.gunType)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>口径 <#EFDF8F>${addon.caliber ?: "不限"}"
        )
        appendLine(
            "  <#A9B4C3>属性加成 <#F2F5FA>${if (addon.attributes.isEmpty()) "无" else addon.attributes.entries.joinToString(", ") { "${it.key}=${num(it.value)}" }}"
        )
        append(
            "  <#A9B4C3>消音 <#EFDF8F>${num(addon.suppressor)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>倍率 <#EFDF8F>${num(addon.zoom)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>热成像 <#EFDF8F>${addon.thermal}"
        )
    }

    private fun armorLines(armor: ArmorData): String = buildString {
        appendLine("<#84D1CB>■ <#EFDF8F>ARMOR <#A9B4C3>| <#F2F5FA>${armor.id}")
        appendLine(
            "  <#A9B4C3>防御 <#EFDF8F>${num(armor.defense)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>防护等级 <#EFDF8F>${num(armor.defenseLevel)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>耐久 <#EFDF8F>${num(armor.durability)}"
        )
        appendLine(
            "  <#A9B4C3>移速修正 <#EFDF8F>${num(armor.speed)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>机械效率 <#EFDF8F>${num(armor.machineEfficiency)}" +
                " <#6E7A8C>|</#6E7A8C> <#A9B4C3>材质 <#EFDF8F>${armor.material}"
        )
        append("  <#A9B4C3>部位 <#F2F5FA>${listText(armor.part)}")
    }

    // ==================== 发物品 ====================

    private fun giveItem(sender: CommandSender, id: String, amount: Int) {
        val player = sender as? Player
        if (player == null) {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行（需要把物品放进你的背包）")
            return
        }
        if (amount <= 0) {
            sender.sendComponent("<#E08A98>数量必须是正整数")
            return
        }
        val count = amount.coerceAtMost(36 * 64)

        val item = try {
            Sertraline.api().buildItem(id, player, amount = count)
        } catch (e: Exception) {
            sender.sendComponent("<#E08A98>构建物品失败: ${e.message}")
            return
        }
        if (item == null) {
            sender.sendComponent(
                "<#E08A98>未能构建物品 <#EFDF8F>$id <#A9B4C3>" +
                    "（Sertraline 里没有这个 ID，或该物品构建失败）"
            )
            return
        }

        // 构建后立刻核对「名称 / 描述」有没有真的落上去：没落上就说明段落处理器没生效，
        // 当场把原因写在聊天栏，而不是等玩家发现物品是「一堆白板」再来问。
        val report = GunItemProcessors.probeBuilt(item)
        if (report != null) {
            sender.sendComponent("<#E08A98>⚠ $id 的段落处理器似乎没生效：$report")
            sender.sendComponent(
                "<#A9B4C3>执行 <#EFDF8F>/ctt gun processors <#A9B4C3>查看注册状态；" +
                    "通常是 Sertraline 版本低于 <#EFDF8F>3.13.0.2 <#A9B4C3>或未重启服务端"
            )
        }

        val leftover = player.inventory.addItem(item)
        for (rest in leftover.values) {
            player.world.dropItemNaturally(player.location, rest)
        }

        val typeName = when {
            GunRegistry.gun(id) != null -> "枪械"
            GunRegistry.mag(id) != null -> "弹匣"
            GunRegistry.ammo(id) != null -> "子弹"
            GunRegistry.addon(id) != null -> "配件"
            GunRegistry.armor(id) != null -> "护甲"
            else -> "未登记枪械段的物品"
        }
        sender.sendComponent(
            "<#8FD4A6>已给予 <#EFDF8F>$count<#A9B4C3> 个 <#EFDF8F>$id " +
                "<#A9B4C3>($typeName)" + if (leftover.isNotEmpty()) " <#A9B4C3>（背包已满，多余掉落在地上）" else ""
        )
    }

    // ==================== 调试：装弹 / 状态 ====================

    /** `/ctt gun fill` 的实现。 */
    private fun fillMagazine(sender: CommandSender, ammoId: String, count: Int) {
        val player = sender as? Player
        if (player == null) {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行（需要手持枪械）")
            return
        }
        if (count <= 0) {
            sender.sendComponent("<#E08A98>发数必须是正整数")
            return
        }
        val item = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(item)
        if (gun == null) {
            sender.sendComponent("<#E08A98>主手不是枪械")
            return
        }
        if (GunRegistry.ammo(ammoId) == null) {
            sender.sendComponent(
                "<#E08A98>没有这个子弹 ID：<#EFDF8F>$ammoId " +
                    "<#A9B4C3>（先用 <#EFDF8F>/ctt gun info <#A9B4C3>确认已加载的 ammo）"
            )
            return
        }
        GunInstance.ensureDefaults(item, gun)
        val written = GunInstance.setMagAmmo(item, ammoId, count)
        if (written < 0) {
            sender.sendComponent("<#E08A98>写入弹匣失败（物品缺少 sertraline_data.gun 段）")
            return
        }
        player.inventory.setItemInMainHand(item)

        val state = GunInstance.read(item)
        val magText = if (state == null || state.mag.isEmpty()) {
            "<#6E7A8C>无弹匣"
        } else {
            "<#F2F5FA>${state.magAmmo.size} <#A9B4C3>发"
        }
        sender.sendComponent(
            "<#8FD4A6>已装填 <#EFDF8F>$written<#A9B4C3> 发 <#EFDF8F>$ammoId " +
                "<#A9B4C3>→ <#EFDF8F>${gun.id}<#A9B4C3>，弹匣 $magText " +
                "<#A9B4C3>；热量 <#F2F5FA>${num(state?.heat ?: 0.0)} " +
                "<#A9B4C3>耐久 <#F2F5FA>${num(state?.durability ?: 0.0)}"
        )
    }

    /**
     * `/ctt gun reload` 的实现：**瞬间**跑完一次标准换弹（调试用，跳过读条）。
     *
     * 先 [GunAction.cancel] 停掉可能正在跑的读条，避免它稍后再结算一次、
     * 把这次瞬间换弹的结果顶掉。
     */
    private fun instantReload(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行（需要手持枪械）")
            return
        }
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held)
        if (gun == null) {
            sender.sendComponent("<#E08A98>主手不是枪械")
            return
        }
        GunAction.cancel(player)
        GunAction.reloadStandard(player, held, instant = true)

        val state = GunInstance.read(held)
        sender.sendComponent(
            "<#8FD4A6>（调试）已瞬间完成一次换弹：<#EFDF8F>${gun.id} " +
                "<#A9B4C3>弹匣 <#F2F5FA>${state?.mag?.ifEmpty { "无" } ?: "无"} " +
                "<#A9B4C3>发数 <#F2F5FA>${state?.magAmmo?.size ?: 0} " +
                "<#A9B4C3>膛内 <#F2F5FA>${state?.chamber ?: 0}"
        )
    }

    /** `/ctt gun menu` 的实现：按手持物打开对应菜单。 */
    private fun openMenu(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行（需要手持枪械或弹匣）")
            return
        }
        val held = player.inventory.itemInMainHand
        when {
            GunRegistry.gunOf(held) != null -> GunMenu.open(player)
            GunRegistry.magOf(held) != null -> MagMenu.open(player)
            else -> sender.sendComponent("<#E08A98>主手既不是枪械也不是弹匣")
        }
    }

    /** `/ctt gun state` 的实现。 */
    private fun dumpState(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendComponent("<#E08A98>该指令只能由玩家执行（需要手持枪械）")
            return
        }
        val item = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(item)
        if (gun == null) {
            sender.sendComponent("<#E08A98>主手不是枪械")
            return
        }
        val rawTag = try {
            item.getItemTag().toJsonSimplified()
        } catch (e: Exception) {
            "读取失败: ${e.message}"
        }

        val gunState = GunInstance.read(item)
        val playerState = GunStateManager.state(player)

        sender.sendComponent(
            buildString {
                appendLine("<#84D1CB>■ <#EFDF8F>GUN STATE <#A9B4C3>| <#F2F5FA>${gun.id}")
                if (gunState == null) {
                    appendLine("<#E08A98>  物品上没有 sertraline_data.gun 段（NBT 未初始化）")
                } else {
                    appendLine(
                        "  <#A9B4C3>耐久 <#F2F5FA>${num(gunState.durability)}/${num(gun.attributes.durability)}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>热量 <#F2F5FA>${num(gunState.heat)}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>档位 <#F2F5FA>${GunSettings.heatTierText(GunSettings.heatTier(gunState.heat))}"
                    )
                    appendLine(
                        "  <#A9B4C3>卡壳 <#F2F5FA>${gunState.jam}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>膛内 <#F2F5FA>${gunState.chamber}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>弹匣 <#F2F5FA>${gunState.mag.ifEmpty { "无" }}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>模式 <#F2F5FA>${gunState.fireMode}"
                    )
                    appendLine(
                        "  <#A9B4C3>弹匣内容(${gunState.magAmmo.size}) <#F2F5FA>" +
                            if (gunState.mag.isEmpty()) {
                                "<#6E7A8C>无弹匣"
                            } else {
                                gunState.magAmmo.joinToString(", ").ifEmpty { "空" }
                            }
                    )
                    appendLine("  <#A9B4C3>磨损 <#F2F5FA>${num(gunState.worn)}")
                }
                appendLine(
                    "  <#A9B4C3>举枪 <#F2F5FA>${playerState.drawing}/${playerState.drawTicks}t" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>已出枪过 <#F2F5FA>${playerState.drawApplied}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>ADS <#F2F5FA>${playerState.ads}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>连发序号 <#F2F5FA>${playerState.shotIndex}"
                )
                appendLine(
                    "  <#A9B4C3>后坐 <#F2F5FA>pitch=${num(playerState.recoilPitch)} " +
                        "yaw=${num(playerState.recoilYaw)} vel=(${num(playerState.recoilVelP)}, " +
                        "${num(playerState.recoilVelY)})"
                )
                appendLine(
                    "  <#A9B4C3>lastShotTick=<#F2F5FA>${playerState.lastShotTick}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>jammedUntil=<#F2F5FA>${playerState.jammedUntil}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>forcedStopUntil=<#F2F5FA>${playerState.forcedStopUntil}"
                )
                appendLine("  <#A9B4C3>飞行中子弹 <#F2F5FA>${GunBallistics.size()}")
                // 命中链路计数：哪一环是 0，就断在哪一环
                appendLine(
                    "  <#A9B4C3>弹道 发射=<#F2F5FA>${GunBallistics.spawned}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>命中实体 <#F2F5FA>${GunBallistics.entityHits}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>命中方块 <#F2F5FA>${GunBallistics.blockHits}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>结算 <#F2F5FA>${GunDamage.settlements}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>生效 <#F2F5FA>${GunDamage.appliedCount}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>伤害为0 <#F2F5FA>${GunDamage.droppedZero}"
                )
                // 移速诊断：ADS 减速修饰符正常只该是 0 或 1 个；数量 > 1 就是历史遗留的叠加
                // （旧版按名字删不掉 → 每次瞄准/每秒刷新都再挂一个，玩家会被叠到走不动）
                val moveAttr = try {
                    player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)
                } catch (_: Throwable) {
                    null
                }
                appendLine(
                    "  <#A9B4C3>移速 <#F2F5FA>base=${GunMenu.num(moveAttr?.baseValue ?: 0.0)}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>当前=${GunMenu.num(moveAttr?.value ?: 0.0)}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>本系统减速修饰符 <#F2F5FA>${GunAim.adsModifierCount(player)}" +
                        " <#6E7A8C>(应 0 或 1)</#6E7A8C>"
                )
                appendLine(
                    "  <#A9B4C3>上次开火 <#F2F5FA>${GunShoot.lastResultText(player)}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>距上次 <#F2F5FA>" +
                        (if (playerState.lastShotMs > 0L) {
                            "${System.currentTimeMillis() - playerState.lastShotMs}ms"
                        } else {
                            "从未开火"
                        }) +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>射速间隔 <#F2F5FA>${GunShoot.shotIntervalTicks(gun, GunSettings.heatTier(gunState?.heat ?: 0.0))}t" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>世界 fullTime <#F2F5FA>${player.world.fullTime}"
                )
                // 输入计数：全是 0 = 监听器没注册 / 事件被别的插件挡在前面；
                // 有增长 = 事件到了，问题在判定里。这是「按键没反应」最快的一条线索。
                appendLine(
                    "  <#A9B4C3>输入事件 <#F2F5FA>交互=${GunListener.interactEvents}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>丢弃=${GunListener.dropEvents}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>换手=${GunListener.swapEvents}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>切栏=${GunListener.heldChangeEvents}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>攻击=${GunListener.attackEvents}"
                )
                appendLine(
                    "  <#A9B4C3>连发序号 <#F2F5FA>${playerState.shotIndex}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>本发曲线 <#F2F5FA>垂直×" +
                        "${GunMenu.num(GunShoot.samplePattern(gun.recoilPattern, playerState.shotIndex + 1))}" +
                        " <#6E7A8C>|</#6E7A8C> <#F2F5FA>水平×" +
                        "${GunMenu.num(if (gun.shakePattern.isEmpty()) 0.0 else GunShoot.samplePattern(gun.shakePattern, playerState.shotIndex))}" +
                        " <#6E7A8C>|</#6E7A8C> <#A9B4C3>重置窗口 <#F2F5FA>${GunShoot.effectiveResetMs(gun)}ms"
                )
                append("  <#A9B4C3>后坐曲线 <#F2F5FA>${patternText(gun.recoilPattern)} " +
                    "<#6E7A8C>(键=第几发)</#6E7A8C>").append("\n")
                append("  <#A9B4C3>水平抖动 <#F2F5FA>${patternText(gun.shakePattern)} " +
                    "<#6E7A8C>(键=第几发，正=右 负=左)</#6E7A8C>").append("\n")
                // 手部体力：上限来自属性 hand_stamina_max，回复速率 = 上限 × 5%/s × 属性倍率，
                // 且最后一次消耗后要静默 5 秒（gun-mechanism.yml: stamina.regen-*）
                val data = player.getData()
                if (data != null) {
                    val stamMax = HandStamina.max(data)
                    val wait = HandStamina.secondsUntilRegen(player)
                    val perSec = HandStamina.regenPerSecond(player, data)
                    appendLine(
                        "  <#A9B4C3>手部体力 <#F2F5FA>${GunMenu.num(data.handStamina)}/${GunMenu.num(stamMax)}" +
                            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>回复 <#F2F5FA>" +
                            (if (wait > 0.0) "等待 ${GunMenu.num(wait)}s" else "进行中") +
                            " <#6E7A8C>(+${GunMenu.num(perSec)}/s，倍率×" +
                            "${GunMenu.num(HandStamina.regenMultiplier(player))})"
                    )
                }
                append("  <#A9B4C3>NBT <#6E7A8C>").append(rawTag)
            }
        )
    }

    // ==================== 旧弩弹匣迁移（实现） ====================

    /**
     * 一个「可以写回去」的物品槽位。
     *
     * [writeBack] 是写回动作：`--apply` 时必须显式写回容器，
     * 只改 ItemStack 对象的 NBT 在部分容器（盔甲 / 末影箱）上不保证落盘。
     */
    private class MigrateTarget(
        val where: String,
        val item: ItemStack,
        val writeBack: (ItemStack) -> Unit,
    )

    /**
     * 扫描所有在线玩家的 主背包 36 格 + 四格盔甲 + 副手 + 末影箱，
     * 把每个旧弹匣的 `cmag` 迁移到 `sertraline_data.mag.ammo`。
     */
    private fun migrateLegacyMagazines(sender: CommandSender, apply: Boolean) {
        val players = try {
            Bukkit.getOnlinePlayers().toList()
        } catch (e: Exception) {
            sender.sendComponent("<#E08A98>读取在线玩家失败: ${e.message}")
            return
        }
        if (players.isEmpty()) {
            sender.sendComponent("<#E08A98>当前没有在线玩家可以扫描")
            return
        }

        var scanned = 0          // 扫到的旧弹匣数量
        var migratable = 0       // 可迁移（ID 在 GunRegistry.ammo 里存在）的子弹数
        var discarded = 0        // 丢弃（未注册为 ammo）的子弹数
        var written = 0          // 实际写回的物品数
        var failed = 0           // 写回失败的物品数
        val details = mutableListOf<String>()

        for (player in players) {
            var dirty = false
            for (target in collectMigrateTargets(player)) {
                val ids = readLegacyCmag(target.item) ?: continue
                scanned++

                // 逐个校验：GunRegistry.ammo(id) 不存在 = 这发子弹在新系统里没有对应弹种，丢弃
                val kept = ArrayList<String>(ids.size)
                for (id in ids) {
                    if (GunRegistry.ammo(id) != null) {
                        kept += id
                    } else {
                        discarded++
                        if (details.size < 5) {
                            details += "<#6E7A8C>·</#6E7A8C> <#A9B4C3>${player.name} " +
                                "${target.where} <#EFDF8F>$id"
                        }
                    }
                }

                // ★ 顺序转换：旧体系「头部 = 下一发」，新体系「末位 = 下一发」，所以整表反转。
                //   依据见 [readLegacyCmag] 的注释（旧 getFirstArrow / consumeFirstArrow 从头部取，
                //   新的 MagazineCore.pop 从 lastIndex 取）。
                val migrated = kept.asReversed()
                migratable += migrated.size

                if (!apply) continue

                if (writeMigratedMagazine(target.item, migrated)) {
                    target.writeBack(target.item)
                    written++
                    dirty = true
                } else {
                    failed++
                }
            }
            if (dirty) {
                try {
                    player.updateInventory()
                } catch (_: Exception) {
                }
            }
        }

        sender.sendComponent(
            buildString {
                appendLine(
                    "<#84D1CB>■ <#EFDF8F>旧弩弹匣迁移 <#A9B4C3>| " +
                        if (apply) "<#8FD4A6>apply（写回）" else "<#EFDF8F>dry-run"
                )
                appendLine(
                    "  扫描到 <#F2F5FA>$scanned<#A9B4C3> 个旧弹匣 / " +
                        "可迁移 <#F2F5FA>$migratable<#A9B4C3> 发 / " +
                        "丢弃 <#F2F5FA>$discarded<#A9B4C3> 发（详情前 5 条）"
                )
                if (details.isEmpty()) {
                    appendLine("  <#6E7A8C>没有丢弃项，全部 ID 都能在 GunRegistry.ammo 里找到")
                } else {
                    for (line in details) appendLine("  $line")
                    if (discarded > details.size) {
                        appendLine("  <#6E7A8C>…… 另有 ${discarded - details.size} 发未列出")
                    }
                }
                if (scanned == 0) {
                    appendLine("  <#A9B4C3>在线玩家身上没有带 <#EFDF8F>cmag<#A9B4C3> 的旧弹匣")
                }
                if (apply) {
                    appendLine(
                        "  <#8FD4A6>已写回 <#F2F5FA>$written<#A9B4C3> 个物品" +
                            "（已删除 <#EFDF8F>cmag<#A9B4C3>，已写入 <#EFDF8F>mag.ammo<#A9B4C3> + " +
                            "<#EFDF8F>mag.v=1<#A9B4C3>）"
                    )
                    if (failed > 0) appendLine("  <#E08A98>写回失败 <#F2F5FA>$failed<#A9B4C3> 个物品（NBT 读写异常）")
                } else {
                    append("  <#EFDF8F>dry-run，加 <#EFDF8F>--apply <#A9B4C3>生效")
                }
            }
        )
    }

    /**
     * 收集一个玩家身上全部候选槽位。
     *
     * 覆盖范围（按需求）：主背包 36 格（0..35）+ 盔甲四格（36..39）+ 副手（40）+ 末影箱。
     * `PlayerInventory` 的槽位约定：36 靴 / 37 裤 / 38 胸 / 39 头 / 40 副手，
     * 直接用 `getItem` / `setItem` 读写这四个槽位，避免 `armorContents` 数组顺序（反向）踩坑。
     */
    private fun collectMigrateTargets(player: Player): List<MigrateTarget> {
        val targets = ArrayList<MigrateTarget>()
        val inv = player.inventory

        for (slot in 0..35) {
            val item = inv.getItem(slot) ?: continue
            if (item.type.isAir || item.amount <= 0) continue
            targets += MigrateTarget("背包#$slot", item) { inv.setItem(slot, it) }
        }

        for (slot in 36..39) {
            val item = inv.getItem(slot) ?: continue
            if (item.type.isAir || item.amount <= 0) continue
            targets += MigrateTarget(armorSlotName(slot), item) { inv.setItem(slot, it) }
        }

        val offHand = inv.itemInOffHand
        if (!offHand.type.isAir && offHand.amount > 0) {
            targets += MigrateTarget("副手", offHand) { inv.setItemInOffHand(it) }
        }

        // 注意：Bukkit 的 `Player.getEnderChest()` 直接返回末影箱的 Inventory 本体，
        // 不是「方块类容器」（没有再套一层 .inventory）。
        val ender = try {
            player.enderChest
        } catch (_: Exception) {
            null
        }
        if (ender != null) {
            for (slot in 0 until ender.size) {
                val item = ender.getItem(slot) ?: continue
                if (item.type.isAir || item.amount <= 0) continue
                targets += MigrateTarget("末影箱#$slot", item) { ender.setItem(slot, it) }
            }
        }

        return targets
    }

    private fun armorSlotName(slot: Int): String = when (slot) {
        36 -> "靴子"
        37 -> "护腿"
        38 -> "胸甲"
        39 -> "头盔"
        else -> "盔甲#$slot"
    }

    /**
     * 读旧弹匣的 `cmag`，返回**按旧顺序**排列的箭矢 ID 列表（**头部 = 下一发**）；
     * 物品上没有 `cmag` 就返回 null（= 不是旧弹匣）。
     *
     * ### 顺序语义的依据（迁移时为什么要反转）
     * 被删掉的旧 `CrossbowManager`：
     * - `getMagazineContents()` = `cmag.filled.split(",")`，顺序即填装顺序；
     * - `getFirstArrow()` = `getMagazineContents(...).firstOrNull()` → **头部是下一发**；
     * - `consumeFirstArrow()` = `ids.removeAt(0)` → 从**头部**消耗；
     * - 装填（`loadArrowFromInventory` / 箭矢夹 GUI）= `contents + arrowId` → 往**尾部**追加。
     *
     * 新的 `MagazineCore`：
     * - `push()` = `ammo.add(ammoId)`；`pop()` = `ammo.removeAt(ammo.lastIndex)`
     *   → **末位是下一发**（`mag.ammo` 的文档里写死了「末位为下一发」）。
     *
     * 所以旧列表 `[a, b, c]`（下一发 a）必须写成新列表 `[c, b, a]`（末位 a），
     * 也就是 [migrateLegacyMagazines] 里的 `kept.asReversed()` 一次整表反转——
     * **不能**只把 `first` 挪到末尾（那会打乱其余几发的发射顺序）。
     *
     * ### 兼容的形状（三种都吃）
     * 1. **String**：早期直接存 `"a,b,c"`；
     * 2. **复合（Compound）**：`cmag.filled = "a,b,c"`（仓库里上一版 `CrossbowManager` 写的就是这种）；
     * 3. **列表（List）**：`cmag = ["a","b","c"]`。
     *
     * taboolib 的 `ItemTag` 在不同读取路径下把「复合 / 列表」暴露成不同的具体类型
     * （有时是 `ItemTag` / `ItemTagList` 本身，有时是装着它们的 `ItemTagData`，
     * 而 `asCompound()` / `asList()` 是**直接强转**、类型不对就抛异常），
     * 所以先按 [ItemTagType] 判形状，再各自用「本体 or 强转」两条路兜底，
     * 保证三种历史写法在任何一种底层表示下都能读出来。
     */
    private fun readLegacyCmag(item: ItemStack): List<String>? {
        val tag = try {
            item.getItemTag()
        } catch (_: Exception) {
            return null
        }
        val raw = tag["cmag"] ?: return null

        return try {
            when (raw.type) {
                // 1. 复合：cmag.filled（"a,b,c"）
                ItemTagType.COMPOUND -> {
                    val compound = if (raw is ItemTag) raw else raw.asCompound()
                    splitIds(compound["filled"]?.asString())
                }
                // 2. 列表：cmag = [...]
                ItemTagType.LIST -> {
                    val list = if (raw is ItemTagList) raw else raw.asList()
                    list.mapNotNull { data ->
                        val text = data.asString().trim()
                        if (text.isEmpty()) null else text
                    }
                }
                // 3. 字符串：cmag = "a,b,c"
                else -> splitIds(raw.asString())
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 逗号拼接的 ID 串 → 列表（空串 / 空元素一律丢掉）。 */
    private fun splitIds(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(",").mapNotNull { part ->
            val id = part.trim()
            if (id.isEmpty()) null else id
        }
    }

    /**
     * 把迁移结果写回物品 NBT，成功返回 true。
     *
     * 一次 `saveTo` 同时完成三件事（等价于 `MagazineCore.writeAmmo` + 补版本号 + 删旧键）：
     * 1. 删除旧键 `cmag`（`ItemTag` 本身就是 `MutableMap`，直接 `remove` 即可；
     *    旧实现靠「新建 ItemTag 拷贝一遍再写回」，效果相同但要多序列化一次）；
     * 2. `sertraline_data.mag.v = 1`（与 [MagazineCore.DATA_VERSION] 一致）；
     * 3. `sertraline_data.mag.ammo = 迁移后的 ID 列表`（**末位 = 下一发**）。
     *
     * 只 `putDeep` 自己这几个子键，不会覆盖同一物品上的 `sertraline_data.gun` / `.model` 等
     * 其它模块的数据（与 [MagazineCore] 的「合并写入」约定一致）。
     */
    private fun writeMigratedMagazine(item: ItemStack, migrated: List<String>): Boolean {
        return try {
            val tag = item.getItemTag()
            tag.remove("cmag")
            tag.putDeep("sertraline_data.mag.v", MagazineCore.DATA_VERSION)
            tag.putDeep("sertraline_data.mag.ammo", migrated)
            tag.saveTo(item)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** `--apply` / `apply`（大小写不敏感）判定。 */
    private fun isApplyFlag(raw: String?): Boolean {
        val flag = raw?.trim()?.lowercase(Locale.ROOT) ?: return false
        return flag == "--apply" || flag == "apply"
    }

    // ==================== 文本工具 ====================

    /** 无 locale 依赖的两位小数格式化：整数不带小数点。 */
    private fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 100.0) / 100.0
        val integral = rounded.toLong().toDouble()
        return if (rounded == integral) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    private fun listText(values: List<String>): String =
        if (values.isEmpty()) "空" else values.joinToString(", ")

    private fun patternText(pattern: Map<Int, Double>): String =
        if (pattern.isEmpty()) "空" else pattern.entries.joinToString(", ") { "${it.key}→${num(it.value)}" }
}
