package io.github.zzzyyylllty.chotentech.function.kether.script

import io.github.zzzyyylllty.chotentech.data.DataUtil
import io.github.zzzyyylllty.chotentech.data.QuetiapineDataBridge
import io.github.zzzyyylllty.chotentech.data.getProfile
import io.github.zzzyyylllty.chotentech.function.kether.getBukkitPlayer
import io.github.zzzyyylllty.chotentech.util.serialize.CastHelper
import taboolib.common5.Coerce
import taboolib.library.kether.ArgTypes
import taboolib.library.kether.ParsedAction
import taboolib.module.kether.*
import java.util.concurrent.CompletableFuture

/**
 * Chemdah
 * ink.ptms.chemdah.module.kether.ActionQuetiapine
 *
 * @author sky
 * @since 2021/2/10 6:39 下午
 */
class ActionQuetiapine {

    class QuetiapineDataGet(val key: ParsedAction<*>, val default: ParsedAction<*> = ParsedAction.noop<Any>()) : ScriptAction<Any>() {

        override fun run(frame: ScriptFrame): CompletableFuture<Any> {
            val future = CompletableFuture<Any>()
            frame.newFrame(key).run<Any>().thenApply {
                frame.newFrame(default).run<Any>().thenApply { def ->
                    future.complete(DataUtil.getDataRaw(frame.getBukkitPlayer(), it.toString()) ?: def)
                }
            }
            return future
        }
    }

    class QuetiapineDataSet(
        val key: ParsedAction<*>,
        val value: ParsedAction<*>,
        val symbol: PlayerOperator.Method,
        val default: ParsedAction<*> = ParsedAction.noop<Any>(),
    ) : ScriptAction<Void>() {

        override fun run(frame: ScriptFrame): CompletableFuture<Void> {
            return frame.newFrame(key).run<Any>().thenAccept { key ->
                frame.newFrame(value).run<Any>().thenAccept { value ->
                    frame.newFrame(default).run<Any>().thenAccept { def ->
                        val player = frame.getBukkitPlayer()
                        val dataMap = QuetiapineDataBridge.getDataMap(player)
                        when {
                            value == null -> {
                                dataMap.remove(key.toString())
                            }
                            symbol == PlayerOperator.Method.INCREASE -> {
                                CastHelper.increaseAny(CastHelper.smartCast(dataMap[key.toString()]) ?: def, value)
                                    ?.let { dataMap[key.toString()] = it.toString() }
                            }
                            else -> {
                                dataMap[key.toString()] = value.toString()
                            }
                        }
                    }
                }
            }
        }
    }

    class QuetiapineDataKeys : ScriptAction<List<String>>() {

        override fun run(frame: ScriptFrame): CompletableFuture<List<String>> {
            return CompletableFuture.completedFuture(QuetiapineDataBridge.keys(frame.getBukkitPlayer()).toList())
        }
    }

    class QuetiapineLevelSet(val key: ParsedAction<*>, val type: LevelType, val value: ParsedAction<*>, val symbol: PlayerOperator.Method) : ScriptAction<Void>() {

        override fun run(frame: ScriptFrame): CompletableFuture<Void> {
            return frame.newFrame(key).run<Any>().thenAccept { key ->
                frame.newFrame(value).run<Any>().thenAccept { value ->
                    val levelName = key.toString()
                    val playerQuetiapine = frame.getProfile()
                    val value = value.toString()
                    if (symbol == PlayerOperator.Method.INCREASE) {
                        if (type == LevelType.LEVEL) {
                            playerQuetiapine.addLevel(levelName, value.toLong())
                        } else {
                            playerQuetiapine.addExp(levelName, value.toDouble())
                        }
                    } else {
                        if (type == LevelType.LEVEL) {
                            playerQuetiapine.setLevel(levelName, value.toLong())
                        } else {
                            playerQuetiapine.setExp(levelName, value.toDouble())
                        }
                    }
                }
            }
        }
    }

    class QuetiapineLevelGet(val key: ParsedAction<*>, val type: LevelType) : ScriptAction<Any>() {

        override fun run(frame: ScriptFrame): CompletableFuture<Any> {
            val future = CompletableFuture<Any>()
            frame.newFrame(key).run<Any>().thenApply {
                val levelName = it.toString()
                when (type) {
                    LevelType.LEVEL -> {
                        future.complete(frame.getProfile().getLevel(levelName))
                    }
                    LevelType.EXP -> {
                        future.complete(frame.getProfile().getExp(levelName))
                    }
                    LevelType.NEXT_EXP -> {
                        future.complete(frame.getProfile().getNextLevelExp(levelName))
                    }
                }
            }
            return future
        }
    }

    enum class LevelType {

        LEVEL, EXP, NEXT_EXP
    }

    companion object {

        /**
         * quser data *key
         * quser data *key to *value
         * quser data *key add *value
         * quser data keys
         *
         * quser level *default level
         * quser level *default level to *100
         * quser level *default exp
         * quser level *default exp add *100
         */
        @KetherParser(["quser"], shared = true)
        fun parser() = scriptParser {
            when (it.expects("data", "level")) {
                "data" -> {
                    try {
                        it.mark()
                        it.expect("keys")
                        QuetiapineDataKeys()
                    } catch (ex: Throwable) {
                        it.reset()
                        val key = it.next(ArgTypes.ACTION)
                        try {
                            it.mark()
                            when (it.expects("+", "=", "to", "add", "increase")) {
                                "=", "to" -> QuetiapineDataSet(key, it.next(ArgTypes.ACTION), PlayerOperator.Method.MODIFY)
                                "+", "add", "increase" -> {
                                    val value = it.next(ArgTypes.ACTION)
                                    try {
                                        it.mark()
                                        it.expect("default")
                                        QuetiapineDataSet(key, value, PlayerOperator.Method.INCREASE, it.next(ArgTypes.ACTION))
                                    } catch (ex: Throwable) {
                                        it.reset()
                                        QuetiapineDataSet(key, value, PlayerOperator.Method.INCREASE)
                                    }
                                }

                                else -> error("out of case")
                            }
                        } catch (ex: Throwable) {
                            it.reset()
                            try {
                                it.mark()
                                it.expect("default")
                                QuetiapineDataGet(key, it.next(ArgTypes.ACTION))
                            } catch (ex: Throwable) {
                                it.reset()
                                QuetiapineDataGet(key)
                            }
                        }
                    }
                }

                "level" -> {
                    val key = it.next(ArgTypes.ACTION)
                    val type = when (it.expects("level", "exp", "exp-max")) {
                        "level" -> LevelType.LEVEL
                        "exp" -> LevelType.EXP
                        "next", "next_exp", "required" -> LevelType.NEXT_EXP
                        else -> error("out of case")
                    }
                    try {
                        it.mark()
                        val method = when (it.expects("+", "=", "to", "add", "increase")) {
                            "=", "to" -> PlayerOperator.Method.MODIFY
                            "+", "add", "increase" -> PlayerOperator.Method.INCREASE
                            else -> error("out of case")
                        }
                        QuetiapineLevelSet(key, type, it.next(ArgTypes.ACTION), method)
                    } catch (ex: Throwable) {
                        it.reset()
                        QuetiapineLevelGet(key, type)
                    }
                }

                else -> error("out of case")
            }
        }
    }
}
