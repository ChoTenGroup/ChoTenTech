package io.github.zzzyyylllty.chotentech.data

data class Achievement(
    val id: String,
    val name: String,
    val description: List<String>,
    val icon: String,
    val model: String? = null,
    val doneModel: String? = null,
    val lockedModel: String? = null,
    val category: String,
    val howto: List<String> = emptyList(),
    val criteria: List<Criterion>
)

sealed class Criterion {
    data class PAPI(
        val placeholder: String,
        val operator: Operator,
        val value: String
    ) : Criterion()

    data object Impossible : Criterion()
}

enum class Operator(val symbol: String) {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER(">"),
    LESS("<"),
    GREATER_EQUAL(">="),
    LESS_EQUAL("<=");

    fun compare(actual: Double, target: Double): Boolean = when (this) {
        EQUAL -> actual == target
        NOT_EQUAL -> actual != target
        GREATER -> actual > target
        LESS -> actual < target
        GREATER_EQUAL -> actual >= target
        LESS_EQUAL -> actual <= target
    }

    fun compareString(actual: String, target: String): Boolean = when (this) {
        EQUAL -> actual == target
        NOT_EQUAL -> actual != target
        else -> {
            val a = actual.toDoubleOrNull()
            val t = target.toDoubleOrNull()
            if (a != null && t != null) compare(a, t)
            else actual.compareTo(target).let { cmp ->
                when (this) {
                    GREATER -> cmp > 0
                    LESS -> cmp < 0
                    GREATER_EQUAL -> cmp >= 0
                    LESS_EQUAL -> cmp <= 0
                    else -> false
                }
            }
        }
    }

    companion object {
        fun fromSymbol(symbol: String): Operator = entries.firstOrNull { it.symbol == symbol }
            ?: throw IllegalArgumentException("未知运算符: $symbol")
    }
}

data class AchievementProgress(
    var completed: Boolean = false,
    var completedTime: Long? = null
)
