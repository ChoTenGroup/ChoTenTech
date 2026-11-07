package io.github.zzzyyylllty.chotentech.command

import io.github.zzzyyylllty.chotentech.data.BodyPart
import io.github.zzzyyylllty.chotentech.data.BodyParts
import io.github.zzzyyylllty.chotentech.data.PlayerBodyHealth
import io.github.zzzyyylllty.chotentech.data.STATUS_EFFECT_TYPES
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.body.BodyHealthManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.decimal
import taboolib.common.platform.command.int
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.player
import taboolib.common.platform.command.subCommand
import kotlin.math.abs

/**
 * 肢体韧性 / 水分 / 倒地休克 的诊断与调试指令。
 *
 * 所有写操作都作用在**在线玩家当前档案**的 [PlayerBodyHealth] 上，
 * 并在改动后立刻调用 `syncMaxima` / `syncPenalties`，让血上限与移速修饰符同步刷新，
 * 避免「改了数据但看不到效果」或「看到的效果和真实数值不一致」。
 *
 * 调试输出一律走只读快照（[BodyHealthManager.computePenalties]），
 * 不调用会推进计时的 `tickDestructionPenalty`，因此反复查询不会改变游戏状态。
 */
@CommandHeader(
    name = "chotentech-body",
    aliases = ["cttbody", "bodydebug"],
    permission = "chotentech.command.bodydebug",
    description = "肢体韧性调试指令",
    permissionDefault = PermissionDefault.OP,
    newParser = true
)
object ChoTenBodyDebugCommand {

    @CommandBody
    val main = mainCommand { createModernHelper() }

    // ==================== show ====================

    @CommandBody
    val show = subCommand {
        execute<CommandSender> { sender, _, _ ->
            self(sender)?.let { dump(sender, it) }
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                resolve(sender, context["target"])?.let { dump(sender, it) }
            }
        }
    }

    // ==================== toughness ====================

    @CommandBody
    val toughness = subCommand {
        literal("set") {
            dynamic("part") {
                decimal("value") {
                    execute<CommandSender> { sender, context, _ ->
                        self(sender)?.let { setToughness(sender, it, context["part"], context["value"].toDouble()) }
                    }
                    player("target") {
                        execute<CommandSender> { sender, context, _ ->
                            resolve(sender, context["target"])?.let {
                                setToughness(sender, it, context["part"], context["value"].toDouble())
                            }
                        }
                    }
                }
            }
        }
        literal("damage") {
            dynamic("part") {
                decimal("value") {
                    execute<CommandSender> { sender, context, _ ->
                        self(sender)?.let { damageToughness(sender, it, context["part"], context["value"].toDouble()) }
                    }
                    player("target") {
                        execute<CommandSender> { sender, context, _ ->
                            resolve(sender, context["target"])?.let {
                                damageToughness(sender, it, context["part"], context["value"].toDouble())
                            }
                        }
                    }
                }
            }
        }
        literal("heal") {
            dynamic("part") {
                decimal("value") {
                    execute<CommandSender> { sender, context, _ ->
                        self(sender)?.let { healToughness(sender, it, context["part"], context["value"].toDouble()) }
                    }
                    player("target") {
                        execute<CommandSender> { sender, context, _ ->
                            resolve(sender, context["target"])?.let {
                                healToughness(sender, it, context["part"], context["value"].toDouble())
                            }
                        }
                    }
                }
            }
        }
        literal("get") {
            dynamic("part") {
                execute<CommandSender> { sender, context, _ ->
                    self(sender)?.let { dumpPart(sender, it, context["part"]) }
                }
                player("target") {
                    execute<CommandSender> { sender, context, _ ->
                        resolve(sender, context["target"])?.let { dumpPart(sender, it, context["part"]) }
                    }
                }
            }
        }
    }

    // ==================== effect ====================

    @CommandBody
    val effect = subCommand {
        literal("add") {
            dynamic("part") {
                dynamic("type") {
                    // 省略等级与时长：1 级、永久
                    execute<CommandSender> { sender, context, _ ->
                        self(sender)?.let { addEffect(sender, it, context["part"], context["type"], 1, null) }
                    }
                    player("target") {
                        execute<CommandSender> { sender, context, _ ->
                            resolve(sender, context["target"])?.let {
                                addEffect(sender, it, context["part"], context["type"], 1, null)
                            }
                        }
                    }
                    int("level") {
                        // 只给等级：仍然永久
                        execute<CommandSender> { sender, context, _ ->
                            self(sender)?.let { addEffect(sender, it, context["part"], context["type"], context["level"].toInt(), null) }
                        }
                        player("target") {
                            execute<CommandSender> { sender, context, _ ->
                                resolve(sender, context["target"])?.let {
                                    addEffect(sender, it, context["part"], context["type"], context["level"].toInt(), null)
                                }
                            }
                        }
                        decimal("seconds") {
                            execute<CommandSender> { sender, context, _ ->
                                self(sender)?.let {
                                    addEffect(sender, it, context["part"], context["type"], context["level"].toInt(), context["seconds"].toDouble())
                                }
                            }
                            player("target") {
                                execute<CommandSender> { sender, context, _ ->
                                    resolve(sender, context["target"])?.let {
                                        addEffect(sender, it, context["part"], context["type"], context["level"].toInt(), context["seconds"].toDouble())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        literal("remove") {
            dynamic("part") {
                dynamic("type") {
                    execute<CommandSender> { sender, context, _ ->
                        self(sender)?.let { removeEffect(sender, it, context["part"], context["type"]) }
                    }
                    player("target") {
                        execute<CommandSender> { sender, context, _ ->
                            resolve(sender, context["target"])?.let { removeEffect(sender, it, context["part"], context["type"]) }
                        }
                    }
                }
            }
        }
        literal("clear") {
            execute<CommandSender> { sender, _, _ ->
                self(sender)?.let { clearEffects(sender, it, null) }
            }
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    resolve(sender, context["target"])?.let { clearEffects(sender, it, null) }
                }
            }
            dynamic("part") {
                execute<CommandSender> { sender, context, _ ->
                    self(sender)?.let { clearEffects(sender, it, context["part"]) }
                }
                player("target") {
                    execute<CommandSender> { sender, context, _ ->
                        resolve(sender, context["target"])?.let { clearEffects(sender, it, context["part"]) }
                    }
                }
            }
        }
    }

    // ==================== water ====================

    @CommandBody
    val water = subCommand {
        execute<CommandSender> { sender, _, _ ->
            self(sender)?.let { dumpWater(sender, it) }
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                resolve(sender, context["target"])?.let { dumpWater(sender, it) }
            }
        }
        literal("set") {
            decimal("value") {
                execute<CommandSender> { sender, context, _ ->
                    self(sender)?.let { setWater(sender, it, context["value"].toDouble(), false) }
                }
                player("target") {
                    execute<CommandSender> { sender, context, _ ->
                        resolve(sender, context["target"])?.let { setWater(sender, it, context["value"].toDouble(), false) }
                    }
                }
            }
        }
        literal("add") {
            decimal("value") {
                execute<CommandSender> { sender, context, _ ->
                    self(sender)?.let { setWater(sender, it, context["value"].toDouble(), true) }
                }
                player("target") {
                    execute<CommandSender> { sender, context, _ ->
                        resolve(sender, context["target"])?.let { setWater(sender, it, context["value"].toDouble(), true) }
                    }
                }
            }
        }
    }

    // ==================== stage ====================

    @CommandBody
    val stage = subCommand {
        literal("down") {
            execute<CommandSender> { sender, _, _ ->
                self(sender)?.let { enterDowned(sender, it) }
            }
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    resolve(sender, context["target"])?.let { enterDowned(sender, it) }
                }
            }
        }
        literal("shock") {
            execute<CommandSender> { sender, _, _ ->
                self(sender)?.let { enterShock(sender, it) }
            }
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    resolve(sender, context["target"])?.let { enterShock(sender, it) }
                }
            }
        }
        literal("revive") {
            execute<CommandSender> { sender, _, _ ->
                self(sender)?.let { revive(sender, it) }
            }
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    resolve(sender, context["target"])?.let { revive(sender, it) }
                }
            }
        }
        literal("kill") {
            execute<CommandSender> { sender, _, _ ->
                self(sender)?.let { kill(sender, it) }
            }
            player("target") {
                execute<CommandSender> { sender, context, _ ->
                    resolve(sender, context["target"])?.let { kill(sender, it) }
                }
            }
        }
    }

    // ==================== reset ====================

    @CommandBody
    val reset = subCommand {
        execute<CommandSender> { sender, _, _ ->
            self(sender)?.let { resetBody(sender, it) }
        }
        player("target") {
            execute<CommandSender> { sender, context, _ ->
                resolve(sender, context["target"])?.let { resetBody(sender, it) }
            }
        }
    }

    // ==================== reference ====================

    /** 打印部位 / 效果 ID 参考表，省得去翻源码或 yml。 */
    @CommandBody
    val reference = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendComponent("<#84D1CB>—— 部位 ——")
            BodyParts.defaults.forEach { (id, max) ->
                sender.sendComponent(
                    "<#A9B4C3>  $id <#F2F5FA>${BodyParts.names[id]}" +
                        "<#6E7A8C> | <#A9B4C3>默认上限 ${num(max)}" +
                        "<#6E7A8C> | <#A9B4C3>扩散 ×${num(BodyParts.multipliers[id] ?: 1.0)}" +
                        "<#6E7A8C> | <#A9B4C3>治疗优先 ${num(BodyParts.priorities[id] ?: 1.0)}"
                )
            }
            sender.sendComponent("<#84D1CB>—— 效果 ——")
            sender.sendComponent("<#A9B4C3>  ${STATUS_EFFECT_TYPES.joinToString("<#6E7A8C>, <#A9B4C3>")}")
            sender.sendComponent(
                "<#6E7A8C>贯穿仅头/身体/胃，骨折仅四肢，其余为头部专属；" +
                    "被拒绝时会打印具体原因。"
            )
        }
    }

    // ==================== 解析与守卫 ====================

    /** 未指定目标时使用执行者自己；控制台必须显式指定。 */
    private fun self(sender: CommandSender): Player? {
        if (sender is Player) return sender
        sender.sendComponent("<#E08A98>控制台执行时必须指定目标玩家")
        return null
    }

    private fun resolve(sender: CommandSender, name: String?): Player? {
        val player = Bukkit.getPlayerExact(name ?: "")
        if (player == null) sender.sendComponent("<#E08A98>玩家 <#EFDF8F>$name <#E08A98>不在线")
        return player
    }

    /** 取到玩家的肢体数据；档案未加载时给出提示而不是静默失败。 */
    private fun bodyOf(sender: CommandSender, player: Player): PlayerBodyHealth? {
        val data = player.getData()
        if (data == null) {
            sender.sendComponent("<#E08A98>玩家 <#EFDF8F>${player.name} <#E08A98>的档案尚未加载")
            return null
        }
        return data.bodyHealth.normalize()
    }

    /**
     * 部位参数解析：支持部位 ID（大小写不敏感）、中文名、以及唯一前缀。
     * 无法确定时打印可用值并返回 null，避免调试指令静默作用到错误部位。
     */
    private fun partOf(sender: CommandSender, body: PlayerBodyHealth, raw: String?): BodyPart? {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) {
            sender.sendComponent("<#E08A98>请指定部位：${BodyParts.defaults.keys.joinToString(" ")}")
            return null
        }
        val id = BodyParts.defaults.keys.firstOrNull { it.equals(input, ignoreCase = true) }
            ?: BodyParts.defaults.keys.firstOrNull { BodyParts.names[it] == input }
            ?: BodyParts.defaults.keys.filter { it.startsWith(input, ignoreCase = true) }.singleOrNull()
        if (id == null) {
            sender.sendComponent("<#E08A98>未知部位 <#EFDF8F>$input<#E08A98>，可用：${BodyParts.defaults.keys.joinToString(" ")}")
            return null
        }
        return body.parts[id]
    }

    /** 每次写操作后同步上限与惩罚，让内存改动立刻反映到真实属性上。 */
    private fun sync(player: Player, body: PlayerBodyHealth) {
        BodyHealthManager.syncMaxima(player, body)
        BodyHealthManager.syncPenalties(player, body)
    }

    // ==================== 韧性操作 ====================

    private fun setToughness(sender: CommandSender, player: Player, partRaw: String?, value: Double) {
        if (!value.isFinite()) {
            sender.sendComponent("<#E08A98>数值必须是有限数"); return
        }
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        val before = target.toughness
        val clamped = value.coerceIn(0.0, target.maxToughness)
        target.toughness = clamped
        sync(player, body)
        sender.sendComponent(
            "<#8FD4A6>${BodyParts.names[target.id]}<#A9B4C3> 韧性 ${num(before)} → <#F2F5FA>${num(clamped)}" +
                "<#6E7A8C>/${num(target.maxToughness)} <#A9B4C3>[${target.state}]" +
                if (clamped != value) " <#EFDF8F>（已夹到 0..${num(target.maxToughness)}）" else ""
        )
    }

    private fun healToughness(sender: CommandSender, player: Player, partRaw: String?, value: Double) {
        if (!value.isFinite() || value <= 0) {
            sender.sendComponent("<#E08A98>恢复量必须为正数"); return
        }
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        if (target.destroyed) {
            // 与常规治疗一致：损毁部位不能被治疗抬起来，调试也不应绕过损毁规则。
            sender.sendComponent(
                "<#E08A98>${BodyParts.names[target.id]}已损毁，常规治疗无法恢复。" +
                    "<#A9B4C3>如需强制复位请用 <#EFDF8F>/cttbody toughness set ${target.id} ${num(target.maxToughness)}"
            )
            return
        }
        val healed = target.heal(value)
        sync(player, body)
        sender.sendComponent(
            "<#8FD4A6>${BodyParts.names[target.id]}<#A9B4C3> 恢复 ${num(healed)} 韧性，" +
                "当前 <#F2F5FA>${num(target.toughness)}<#6E7A8C>/${num(target.maxToughness)} <#A9B4C3>[${target.state}]"
        )
    }

    private fun damageToughness(sender: CommandSender, player: Player, partRaw: String?, value: Double) {
        if (!value.isFinite() || value <= 0) {
            sender.sendComponent("<#E08A98>伤害量必须为正数"); return
        }
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        val before = body.parts.values.associate { it.id to it.toughness }
        // 只扣韧性、不扣血量：专门用来观察「打穿 + 溢出扩散」规则的入口。
        body.damageToughness(target.id, value)
        sync(player, body)
        sender.sendComponent("<#8FD4A6>对 ${BodyParts.names[target.id]} 施加 ${num(value)} 点韧性伤害<#A9B4C3>（不扣血量，含溢出扩散）")
        val changed = before.filter { (id, old) -> abs(body.parts.getValue(id).toughness - old) > 1e-9 }
        if (changed.isEmpty()) {
            sender.sendComponent("<#6E7A8C>  （所有部位均已损毁，本次伤害无处可去）")
            return
        }
        changed.forEach { (id, old) ->
            val part = body.parts.getValue(id)
            sender.sendComponent(
                "<#A9B4C3>  ${BodyParts.names[id]} ${num(old)} → <#F2F5FA>${num(part.toughness)}" +
                    "<#6E7A8C>/${num(part.maxToughness)} <#A9B4C3>[${part.state}]"
            )
        }
    }

    // ==================== 效果操作 ====================

    private fun addEffect(sender: CommandSender, player: Player, partRaw: String?, typeRaw: String?, level: Int, seconds: Double?) {
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        val type = typeRaw?.trim()?.lowercase().orEmpty()
        if (type !in STATUS_EFFECT_TYPES) {
            sender.sendComponent("<#E08A98>未知效果 <#EFDF8F>$type<#E08A98>，可用：${STATUS_EFFECT_TYPES.joinToString(" ")}")
            return
        }
        if (level <= 0) {
            sender.sendComponent("<#E08A98>等级必须 ≥ 1"); return
        }
        if (seconds != null && (!seconds.isFinite() || seconds <= 0)) {
            sender.sendComponent("<#E08A98>持续时间必须为正数（秒）"); return
        }
        val durationMs = seconds?.let { (it * 1000).toLong() } ?: Long.MAX_VALUE
        if (!body.applyEffect(target.id, type, level, durationMs)) {
            // 拒绝本身就是肢体规则在起作用，把原因摆出来才是调试指令的价值所在。
            sender.sendComponent(
                "<#E08A98>无法给 ${BodyParts.names[target.id]} 施加 <#EFDF8F>$type<#E08A98>：" +
                    "<#A9B4C3>${rejectReason(target, type)}"
            )
            return
        }
        sender.sendComponent(
            "<#8FD4A6>${BodyParts.names[target.id]}<#A9B4C3> 获得 <#EFDF8F>$type<#A9B4C3>" +
                (if (level > 1) " Lv.$level" else "") +
                " <#A9B4C3>持续 " + (seconds?.let { "${num(it)}s" } ?: "永久")
        )
    }

    /** 复述【肢体状态效果】一节里各效果的合法部位限制。 */
    private fun rejectReason(part: BodyPart, type: String): String = when {
        type == "fracture" && part.id !in BodyParts.limbs -> "骨折只允许四肢"
        type == "penetration" && part.id !in setOf("HEAD", "BODY", "STOMACH") -> "贯穿只允许头部/身体/胃部"
        type == "minor_bleed" && part.effects.containsKey("major_bleed") -> "该部位已是大出血，不能被较轻的小出血覆盖"
        type in setOf("minor_bleed", "major_bleed", "wound") -> "出血/创口类效果被该部位现有状态拒绝"
        part.id != "HEAD" -> "该效果只允许出现在头部，请改用 HEAD"
        else -> "等级或持续时间非法"
    }

    private fun removeEffect(sender: CommandSender, player: Player, partRaw: String?, typeRaw: String?) {
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        val type = typeRaw?.trim()?.lowercase().orEmpty()
        val removed = body.removeEffect(target.id, type)
        sync(player, body)
        sender.sendComponent(
            if (removed) "<#8FD4A6>已移除 ${BodyParts.names[target.id]} 的 <#EFDF8F>$type"
            else "<#EFDF8F>${BodyParts.names[target.id]} 上没有 <#F2F5FA>$type"
        )
    }

    private fun clearEffects(sender: CommandSender, player: Player, partRaw: String?) {
        val body = bodyOf(sender, player) ?: return
        if (partRaw == null) {
            var count = 0
            body.parts.values.forEach { count += it.effects.size; it.effects.clear() }
            sync(player, body)
            sender.sendComponent("<#8FD4A6>已清空全部部位效果，共 $count 条")
        } else {
            val target = partOf(sender, body, partRaw) ?: return
            val count = target.effects.size
            target.effects.clear()
            sync(player, body)
            sender.sendComponent("<#8FD4A6>已清空 ${BodyParts.names[target.id]} 的 $count 条效果")
        }
    }

    // ==================== 水分 ====================

    /** [additive] 为 true 时按增量处理，等价于 `add`，否则是覆盖。 */
    private fun setWater(sender: CommandSender, player: Player, value: Double, additive: Boolean) {
        if (!value.isFinite()) {
            sender.sendComponent("<#E08A98>数值必须是有限数"); return
        }
        val body = bodyOf(sender, player) ?: return
        val before = body.water
        val requested = if (additive) before + value else value
        body.water = requested.coerceIn(0.0, body.maxWater)
        // 与 ChoTenDataHelper.setWater 一致：水分回正立刻解除脱水并重置计时。
        if (body.water > 0) {
            body.dehydrationTicks = 0
            body.parts.getValue("HEAD").effects.remove("dehydration")
            body.parts.getValue("HEAD").effects.remove("severe_dehydration")
        }
        sync(player, body)
        sender.sendComponent(
            "<#8FD4A6>水分 ${num(before)} → <#F2F5FA>${num(body.water)}" +
                "<#6E7A8C>/${num(body.maxWater)} <#A9B4C3>(${num(percent(body.water, body.maxWater))}%)" +
                if (body.water != requested) " <#EFDF8F>（已夹到 0..${num(body.maxWater)}）" else ""
        )
    }

    private fun dumpWater(sender: CommandSender, player: Player) {
        val body = bodyOf(sender, player) ?: return
        sender.sendComponent(
            "<#84D1CB>水分 <#F2F5FA>${num(body.water)}<#6E7A8C>/<#F2F5FA>${num(body.maxWater)} " +
                "<#A9B4C3>(${num(percent(body.water, body.maxWater))}%)" +
                " <#6E7A8C>| <#A9B4C3>连续脱水 ${num(body.dehydrationTicks / 20.0)}s" +
                " <#6E7A8C>| <#A9B4C3>黑色滤镜 ${num(body.filterValue)}"
        )
    }

    // ==================== 阶段 ====================

    private fun enterDowned(sender: CommandSender, player: Player) {
        if (BleedoutManager.isDowned(player.uniqueId)) {
            sender.sendComponent("<#EFDF8F>${player.name} 已处于倒地/休克阶段"); return
        }
        BleedoutManager.enterBleedout(player)
        val body = player.getData()?.bodyHealth
        val phase = body?.downPhase
        if (phase == null) {
            sender.sendComponent("<#E08A98>未能进入倒地：检查 config.yml 的 bleedout.enabled"); return
        }
        sender.sendComponent("<#8FD4A6>${player.name} <#A9B4C3>进入 $phase，剩余 ${num((body.downTicks) / 2.0)}s")
    }

    private fun enterShock(sender: CommandSender, player: Player) {
        if (!BleedoutManager.debugEnterShock(player)) {
            sender.sendComponent("<#E08A98>未能进入休克：玩家不在线或倒地状态机未建立"); return
        }
        val body = player.getData()?.bodyHealth
        sender.sendComponent("<#8FD4A6>${player.name} <#A9B4C3>已强制进入休克，剩余 ${num((body?.downTicks ?: 0) / 2.0)}s")
    }

    private fun revive(sender: CommandSender, player: Player) {
        if (!BleedoutManager.reviveDowned(player.uniqueId)) {
            sender.sendComponent("<#EFDF8F>${player.name} 当前不在倒地/休克阶段"); return
        }
        sender.sendComponent("<#8FD4A6>${player.name} <#A9B4C3>已被拉起")
    }

    private fun kill(sender: CommandSender, player: Player) {
        BleedoutManager.forceDeath(player)
        sender.sendComponent("<#8FD4A6>${player.name} <#A9B4C3>已被强制致死（走正常死亡流程，伤势会被重置）")
    }

    private fun resetBody(sender: CommandSender, player: Player) {
        BodyHealthManager.reset(player)
        sender.sendComponent("<#8FD4A6>${player.name} <#A9B4C3>的肢体韧性与水分已复位，伤势全部清空")
        dump(sender, player)
    }

    // ==================== 输出 ====================

    private fun dump(sender: CommandSender, player: Player) {
        val body = bodyOf(sender, player) ?: return
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        sender.sendComponent(
            "<#84D1CB>—— ${player.name} 肢体韧性 " +
                "<#F2F5FA>${num(body.toughness)}<#6E7A8C>/<#F2F5FA>${num(body.maxToughness)} " +
                "<#A9B4C3>(${num(body.toughnessPercentage)}%) ——"
        )
        body.parts.values.forEach { part ->
            val color = when (part.state) {
                "DESTROYED" -> "<#E08A98>"
                "DAMAGED" -> "<#EFDF8F>"
                else -> "<#8FD4A6>"
            }
            val effects = part.effects.values.joinToString("<#6E7A8C>, <#A9B4C3>") { effect ->
                effect.type + (if (effect.level > 1) " Lv.${effect.level}" else "") +
                    (if (effect.remainingDuration == Long.MAX_VALUE) "" else "(${num(effect.remainingDuration / 1000.0)}s)")
            }
            sender.sendComponent(
                "$color${BodyParts.names[part.id]} <#6E7A8C>${part.id} " +
                    "<#F2F5FA>${num(part.toughness)}<#6E7A8C>/<#F2F5FA>${num(part.maxToughness)} " +
                    "$color${num(part.percentage * 100)}% $color[${part.state}]" +
                    if (effects.isNotEmpty()) "<#6E7A8C> | <#A9B4C3>$effects" else ""
            )
        }
        val (healthPenalty, speedPenalty) = BodyHealthManager.computePenalties(body)
        val arms = body.armPenalties(
            BodyHealthManager.settings.getDouble("pain.arm-aim-cost", .10),
            BodyHealthManager.settings.getDouble("pain.arm-accuracy-penalty", .10)
        )
        sender.sendComponent(
            "<#84D1CB>—— 结算 ——" +
                " <#6E7A8C>| <#A9B4C3>血量上限 -${num(healthPenalty * 100)}%" +
                " <#6E7A8C>| <#A9B4C3>移速 -${num(speedPenalty * 100)}%" +
                " <#6E7A8C>| <#A9B4C3>禁止奔跑 ${BodyHealthManager.sprintBlocked(body)}" +
                " <#6E7A8C>| <#A9B4C3>当前血上限 ${num(maxHealth)}"
        )
        sender.sendComponent(
            "<#6E7A8C>  | <#A9B4C3>手臂 瞄准 +${num(arms.aim * 100)}% 精准 -${num(arms.accuracy * 100)}%" +
                " 搜索 +${num(arms.search * 100)}% 近战 -${num(arms.melee * 100)}%"
        )
        val head = body.parts.getValue("HEAD")
        val torso = body.parts.getValue("BODY")
        sender.sendComponent(
            "<#6E7A8C>  | <#A9B4C3>头损毁惩罚 ${num(head.destructionPenalty * 100)}%（已走 ${num(head.penaltyTicks / 20.0)}s）" +
                " 身体 ${num(torso.destructionPenalty * 100)}%（${num(torso.penaltyTicks / 20.0)}s）"
        )
        sender.sendComponent(
            "<#6E7A8C>  | <#A9B4C3>水分 ${num(body.water)}<#6E7A8C>/${num(body.maxWater)}" +
                " 黑白 ${num(body.filterValue)}" +
                " 阶段 ${body.downPhase ?: "无"}(${num(body.downTicks / 2.0)}s)" +
                " 在线计时 ${num(body.onlineTicks / 20.0)}s"
        )
    }

    private fun dumpPart(sender: CommandSender, player: Player, partRaw: String?) {
        val body = bodyOf(sender, player) ?: return
        val target = partOf(sender, body, partRaw) ?: return
        sender.sendComponent(
            "<#84D1CB>${BodyParts.names[target.id]} <#6E7A8C>${target.id} " +
                "<#F2F5FA>${num(target.toughness)}<#6E7A8C>/<#F2F5FA>${num(target.maxToughness)} " +
                "<#A9B4C3>[${target.state}] 扩散×${num(BodyParts.multipliers[target.id] ?: 1.0)}"
        )
        if (target.effects.isEmpty()) {
            sender.sendComponent("<#6E7A8C>  无效果")
            return
        }
        target.effects.forEach { (type, effect) ->
            sender.sendComponent(
                "<#A9B4C3>  $type Lv.${effect.level} " +
                    if (effect.remainingDuration == Long.MAX_VALUE) "<#6E7A8C>永久"
                    else "<#6E7A8C>剩余 ${num(effect.remainingDuration / 1000.0)}s"
            )
        }
    }

    private fun percent(value: Double, max: Double): Double = value / max.coerceAtLeast(.001) * 100

    private fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && abs(rounded) < 1e15) rounded.toLong().toString()
        else rounded.toString()
    }
}
