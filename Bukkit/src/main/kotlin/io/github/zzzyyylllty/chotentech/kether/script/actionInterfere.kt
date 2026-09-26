package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.ResultLevel
import io.github.zzzyyylllty.chotentech.function.ActiveInterfere
import io.github.zzzyyylllty.chotentech.function.InterfereManager
import io.github.zzzyyylllty.chotentech.function.InterfereStatus
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.library.kether.ArgTypes
import taboolib.module.kether.KetherParser
import taboolib.module.kether.actionTake
import taboolib.module.kether.expects
import taboolib.module.kether.run
import taboolib.module.kether.script
import taboolib.module.kether.scriptParser

/**
 * 干涉（interfere）系统的 Kether 动作。
 *
 * `<干涉>` 支持两种写法，按顺序解析：
 *   1. 配置ID（如 interfere_army）：在「脚本执行者」身上找该配置对应的实例，
 *      优先取未结束的，其次取最新的历史实例 —— 玩家可以同时持有多个干涉，
 *      写配置ID时会自动落到执行者自己的那一条，不需要额外传玩家。
 *   2. 实例ID（如 interfere-1758000000000-1）：全局精确匹配，不依赖执行者。
 *
 * interfere get score <干涉> <阵营>                       # 该阵营当前生效的分数，例：50；未标记或干涉不存在时返回 null
 * interfere get level <干涉> <阵营>                       # 该阵营当前的结果等级，例：success；未标记或干涉不存在时返回 null
 * interfere get ended <干涉>                              # 该干涉是否已完成，返回布尔值
 * interfere set <干涉> <阵营> <等级>                       # 标记结果等级，使用配置中的默认分数
 * interfere set <干涉> <阵营> <等级> extra <数值>          # 标记结果等级，默认分数 + 数值
 * interfere set <干涉> <阵营> <等级> override <数值>       # 标记结果等级，分数覆盖为数值
 *
 * 等级写法大小写不敏感，支持 full_success/success/neutral/fail/full_fail，
 * 也接受中文显示名（完胜/成功/平手/失败/完败）与连字符写法（full-success）。
 * 注意：kether 里裸词是字面量，变量要写 `&变量名`（帧变量）或 chemdah 的 `var 变量名`。
 */
@KetherParser(["interfere"], shared = true)
fun actionInterfere() = scriptParser {
    when (it.expects("get", "set")) {
        "get" -> when (it.expects("score", "level", "ended")) {
            "score" -> {
                val target = it.nextParsedAction()
                val faction = it.nextParsedAction()
                actionTake {
                    val sender = script().sender
                    run(target).thenCompose { t ->
                        run(faction).thenApply { f -> getInterfereScore(sender, t.toString(), f.toString()) }
                    }
                }
            }

            "level" -> {
                val target = it.nextParsedAction()
                val faction = it.nextParsedAction()
                actionTake {
                    val sender = script().sender
                    run(target).thenCompose { t ->
                        run(faction).thenApply { f -> getInterfereLevel(sender, t.toString(), f.toString()) }
                    }
                }
            }

            "ended" -> {
                val target = it.nextParsedAction()
                actionTake {
                    val sender = script().sender
                    run(target).thenApply { t -> isInterfereEnded(sender, t.toString()) }
                }
            }

            else -> error("Unknown interfere get option")
        }

        "set" -> {
            val target = it.nextParsedAction()
            val faction = it.nextParsedAction()
            val level = it.nextParsedAction()
            // 可选后缀：extra <数值> / override <数值>
            val mode = try {
                it.mark()
                it.expects("extra", "override")
            } catch (ex: Throwable) {
                it.reset()
                null
            }
            val amount = if (mode != null) {
                try {
                    it.next(ArgTypes.LONG)
                } catch (ex: Throwable) {
                    it.reset()
                    error("interfere set: '$mode' requires a numeric value")
                }
            } else {
                null
            }
            actionTake {
                val sender = script().sender
                run(target).thenCompose { t ->
                    run(faction).thenCompose { f ->
                        run(level).thenApply { l ->
                            setInterfereResult(sender, t.toString(), f.toString(), l.toString(), mode, amount)
                        }
                    }
                }
            }
        }

        else -> error("Unknown interfere action")
    }
}

/**
 * 解析「干涉」参数：先按实例ID精确匹配，找不到再当成配置ID，
 * 在执行者身上取该配置最新的实例（未结束的优先）。
 */
private fun resolveInterfere(sender: ProxyCommandSender?, token: String): ActiveInterfere? {
    val key = token.trim()
    InterfereManager.getInterfere(key)?.let { return it }
    val player = sender?.castSafely<Player>() ?: return null
    val matched = InterfereManager.getInterferes(player.uniqueId.toString()).filter { it.configId == key }
    return matched.lastOrNull { it.status != InterfereStatus.FINISHED } ?: matched.lastOrNull()
}

/** 该阵营当前生效的分数：覆盖值 > 配置默认值；未标记或解析不到干涉时返回 null */
private fun getInterfereScore(sender: ProxyCommandSender?, token: String, faction: String): Long? {
    val interfere = resolveInterfere(sender, token) ?: return null
    return InterfereManager.getEffectiveFactionScore(interfere.instanceId, faction)
}

/** 该阵营当前的结果等级（小写）；未标记或解析不到干涉时返回 null */
private fun getInterfereLevel(sender: ProxyCommandSender?, token: String, faction: String): String? {
    val interfere = resolveInterfere(sender, token) ?: return null
    return interfere.factionLevels[faction]?.name?.lowercase()
}

/** 仅当解析到实例且状态为已完成时返回 true */
private fun isInterfereEnded(sender: ProxyCommandSender?, token: String): Boolean =
    resolveInterfere(sender, token)?.status == InterfereStatus.FINISHED

private fun setInterfereResult(
    sender: ProxyCommandSender?,
    token: String,
    faction: String,
    rawLevel: String,
    mode: String?,
    amount: Long?
) {
    val interfere = resolveInterfere(sender, token)
        ?: error("Interfere not found: $token (既不是实例ID，也不是当前执行者身上的配置ID)")
    val instanceId = interfere.instanceId
    val level = parseResultLevel(rawLevel)
    val override = when (mode) {
        null -> null
        "extra" -> (InterfereManager.getDefaultFactionScore(instanceId, faction, level) ?: 0L) + (amount ?: 0L)
        "override" -> amount ?: error("interfere set: 'override' requires a numeric value")
        else -> error("Unknown interfere set mode: $mode")
    }
    if (!InterfereManager.setFactionResult(instanceId, faction, level, override)) {
        error("Interfere $instanceId is already finished")
    }
}

private fun parseResultLevel(raw: String): ResultLevel {
    val token = raw.trim()
    ResultLevel.entries.firstOrNull { it.name.equals(token, ignoreCase = true) || it.displayName == token }
        ?.let { return it }
    val normalized = token.uppercase().replace('-', '_').replace(' ', '_')
    return ResultLevel.entries.firstOrNull { it.name == normalized }
        ?: error(
            "Unknown interfere level: $raw (available: ${
                ResultLevel.entries.joinToString("/") { it.name.lowercase() }
            })"
        )
}
