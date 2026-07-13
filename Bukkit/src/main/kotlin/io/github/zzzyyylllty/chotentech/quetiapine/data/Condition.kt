package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.chotentech.quetiapine.function.kether.evalKether
import io.github.zzzyyylllty.chotentech.quetiapine.util.toBooleanTolerance
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import javax.script.CompiledScript
import javax.script.SimpleBindings

data class Condition(
    val js: CompiledScript? = null,
    val kether: List<String>? = null,
    val mode: ConditionMode = ConditionMode.ALL
){
    fun validate(extraVariables: Map<String, Any?>, player: Player): Boolean {

        val data = defaultData + extraVariables + mapOf("player" to player, "mode" to mode.name)
        val jsEnd = js?.eval(SimpleBindings(data))?.toBooleanTolerance()
        val keEnd = try {
            kether?.evalKether(player, data)?.get()?.toBooleanTolerance()
        } catch (_: Exception) {
            null
        }

        return if (mode == ConditionMode.ALL) {
            if (jsEnd == null && keEnd == null) true
            else if (jsEnd == null || keEnd == null) (jsEnd ?: true) && (keEnd ?: true)
            else jsEnd && keEnd
        } else {
            if (jsEnd == null && keEnd == null) true
            else if (jsEnd == null || keEnd == null) (jsEnd ?: false) || (keEnd ?: false)
            else jsEnd || keEnd
        }

    }
}

enum class ConditionMode{
    ALL,
    ANY
}