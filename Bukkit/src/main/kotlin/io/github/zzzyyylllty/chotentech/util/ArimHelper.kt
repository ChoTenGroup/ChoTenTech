package io.github.zzzyyylllty.chotentech.util

import java.util.LinkedHashMap

/**
 * Self-contained math expression evaluator replacing the Arim library dependency.
 * Uses a shunting-yard style two-stack evaluator instead of a script engine,
 * since expressions are pure arithmetic (`%level% * 10 + 50` etc.).
 */
object FixedCalculator {
    private val sanitizeRegex = Regex("[^0-9+\\-*/%.() ]")
    private const val CACHE_MAX_SIZE = 512

    private val resultCache = object : LinkedHashMap<String, Double>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Double>): Boolean =
            size > CACHE_MAX_SIZE
    }

    fun evaluate(expression: String): Double {
        val sanitized = expression.replace(sanitizeRegex, "")
        synchronized(resultCache) {
            resultCache[sanitized]?.let { return it }
        }
        val result = compute(sanitized)
            ?: throw IllegalArgumentException("Cannot evaluate: $expression")
        synchronized(resultCache) { resultCache[sanitized] = result }
        return result
    }

    private fun compute(s: String): Double? {
        val values = ArrayDeque<Double>()
        val ops = ArrayDeque<Char>()
        var i = 0
        var expectOperand = true
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> {
                    ops.addLast(c)
                    expectOperand = true
                    i++
                }
                c == ')' -> {
                    while (ops.isNotEmpty() && ops.last() != '(') {
                        if (!applyOp(values, ops.removeLast())) return null
                    }
                    if (ops.isEmpty()) return null
                    ops.removeLast()
                    expectOperand = false
                    i++
                }
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    values.addLast(s.substring(i, j).toDoubleOrNull() ?: return null)
                    expectOperand = false
                    i = j
                }
                c == '+' || c == '-' -> {
                    if (expectOperand) {
                        // unary: -x -> 0 - x; +x is a no-op
                        if (c == '-') {
                            values.addLast(0.0)
                            ops.addLast('-')
                        }
                    } else {
                        while (ops.isNotEmpty() && ops.last() != '(' && precedence(ops.last()) >= 1) {
                            if (!applyOp(values, ops.removeLast())) return null
                        }
                        ops.addLast(c)
                    }
                    expectOperand = true
                    i++
                }
                c == '*' || c == '/' || c == '%' -> {
                    if (expectOperand) return null
                    while (ops.isNotEmpty() && ops.last() != '(' && precedence(ops.last()) >= 2) {
                        if (!applyOp(values, ops.removeLast())) return null
                    }
                    ops.addLast(c)
                    expectOperand = true
                    i++
                }
                else -> return null
            }
        }
        while (ops.isNotEmpty()) {
            if (ops.last() == '(') return null
            if (!applyOp(values, ops.removeLast())) return null
        }
        return if (values.size == 1) values.last() else null
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/', '%' -> 2
        else -> 0
    }

    private fun applyOp(values: ArrayDeque<Double>, op: Char): Boolean {
        if (values.size < 2) return false
        val b = values.removeLast()
        val a = values.removeLast()
        values.addLast(
            when (op) {
                '+' -> a + b
                '-' -> a - b
                '*' -> a * b
                '/' -> a / b
                '%' -> a % b
                else -> return false
            }
        )
        return true
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
