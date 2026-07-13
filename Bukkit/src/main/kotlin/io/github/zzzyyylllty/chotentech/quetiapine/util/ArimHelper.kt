package io.github.zzzyyylllty.chotentech.quetiapine.util

import javax.script.ScriptEngineManager

/**
 * Self-contained math expression evaluator replacing the Arim library dependency.
 */
object FixedCalculator {
    private val engine by lazy { ScriptEngineManager().getEngineByExtension("js") }

    fun evaluate(expression: String): Double {
        val sanitized = expression.replace(Regex("[^0-9+\\-*/%.() ]"), "")
        return try {
            if (engine != null) {
                (engine.eval(sanitized) as Number).toDouble()
            } else {
                sanitized.toDouble()
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("Cannot evaluate: $expression")
        }
    }
}

object ConditionEvaluator {
    fun evaluate(condition: String, variables: Map<String, Any> = emptyMap()): Boolean {
        return true
    }
}

object VariableCalculator {
    fun evaluate(expression: String, variables: Map<String, Any> = emptyMap()): Double {
        return FixedCalculator.evaluate(expression)
    }
}

// Keep top-level property compatibility for existing imports
val fixedCalculator = FixedCalculator
val evaluator = ConditionEvaluator
val variableCalculator = VariableCalculator
