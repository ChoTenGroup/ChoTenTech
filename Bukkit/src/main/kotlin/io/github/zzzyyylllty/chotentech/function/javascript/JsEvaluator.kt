package io.github.zzzyyylllty.chotentech.function.javascript

import org.bukkit.entity.Player
import taboolib.common5.Coerce
import java.util.concurrent.CompletableFuture
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings

object JsEvaluator {

    val engine: ScriptEngine by lazy {
        ScriptEngineManager().getEngineByExtension("js")
            ?: ScriptEngineManager().getEngineByName("JavaScript")
            ?: error("No JavaScript engine available (Nashorn/Graal.js)")
    }

    private fun createBindings(player: Player, vars: Map<String, Any?>): SimpleBindings {
        val bindings = SimpleBindings()
        bindings.put("player", player)
        bindings.put("event", null)
        bindings.put("Util", PlayerUtil)
        bindings.put("EventUtil", EventUtil)
        bindings.put("ItemStackUtil", ItemStackUtil)
        bindings.put("ThreadUtil", ThreadUtil)
        vars.forEach { (key, value) -> bindings.put(key, value) }
        return bindings
    }

    fun eval(script: String, player: Player, vars: Map<String, Any?> = emptyMap()): CompletableFuture<Any?> {
        val future = CompletableFuture<Any?>()
        try {
            val bindings = createBindings(player, vars)
            val result = engine.eval(script, bindings)
            future.complete(result)
        } catch (e: ScriptException) {
            future.completeExceptionally(e)
        }
        return future
    }

    fun evalNow(script: String, player: Player, vars: Map<String, Any?> = emptyMap()): Any? {
        val bindings = createBindings(player, vars)
        return engine.eval(script, bindings)
    }

    fun evalBoolean(script: String, player: Player, vars: Map<String, Any?> = emptyMap()): Boolean {
        if (script.isBlank()) return true
        if (script == "true") return true
        if (script == "false") return false
        return try {
            val bindings = createBindings(player, vars)
            val result = engine.eval(script, bindings)
            Coerce.toBoolean(result)
        } catch (_: Exception) {
            false
        }
    }

    fun evalAllBoolean(scripts: List<String>, player: Player, vars: Map<String, Any?> = emptyMap()): Boolean {
        if (scripts.isEmpty()) return true
        return scripts.all { evalBoolean(it, player, vars) }
    }

    fun evalValue(script: String, player: Player, vars: Map<String, Any?> = emptyMap()): Any? {
        if (script.isBlank()) return null
        return try {
            val bindings = createBindings(player, vars)
            engine.eval(script, bindings)
        } catch (_: Exception) {
            null
        }
    }

    fun evalDouble(script: String, player: Player, vars: Map<String, Any?> = emptyMap()): Double {
        val result = evalValue(script, player, vars)
        return when (result) {
            is Number -> result.toDouble()
            is String -> result.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    /** 编译 JS 脚本为 CompiledScript */
    fun compileScript(script: String): CompiledScript? {
        return try {
            if (engine is Compilable) (engine as Compilable).compile(script)
            else null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * String.compileJs() — 编译 JS 脚本字符串为 CompiledScript
 */
fun String.compileJs(): CompiledScript? {
    return JsEvaluator.compileScript(this)
}

/**
 * CompiledScript.eval() — 传入变量(Map)并执行编译后的脚本
 */
fun CompiledScript.eval(vars: Map<String, Any?> = emptyMap(), player: Player? = null): Any? {
    val bindings = SimpleBindings()
    player?.let { bindings.put("player", it) }
    vars.forEach { (k, v) -> bindings.put(k, v) }
    return eval(bindings)
}
