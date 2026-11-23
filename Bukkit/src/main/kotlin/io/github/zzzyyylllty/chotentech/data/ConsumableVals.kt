package io.github.zzzyyylllty.chotentech.data

/** Sertraline vals 的食用增减量；null 表示未配置，0 仍表示自定义食物。 */
internal data class ConsumableVals(
    val hunger: Double?,
    val saturation: Double?,
    val health: Double?,
    val san: Double?,
    val water: Double?,
    val nutrition: Map<String, Double>,
    val effects: List<Effect>,
    val configured: Boolean,
) {
    data class Effect(val id: String, val ticks: Int, val amplifier: Int)

    companion object {
        fun read(getVal: (String) -> Any?): ConsumableVals {
            val values = listOf(
                "hunger", "saturation", "health", "san", "water", "nutrition", "effects"
            ).associateWith(getVal)
            val nutrition = values["nutrition"] as? Map<*, *>
            val nutrients = listOf("grain", "vitamin", "protein", "fat").mapNotNull { key ->
                number(nutrition?.get(key))?.let { key to it }
            }.toMap()
            val effects = (values["effects"] as? List<*>)?.mapNotNull { entry ->
                val effect = entry as? Map<*, *> ?: return@mapNotNull null
                val id = effect["effect"] as? String ?: return@mapNotNull null
                Effect(id, number(effect["tick"])?.toInt() ?: 40,
                    (number(effect["level"])?.toInt() ?: 1) - 1)
            }.orEmpty()
            return ConsumableVals(
                number(values["hunger"]), number(values["saturation"]),
                number(values["health"]), number(values["san"]), number(values["water"]),
                nutrients, effects, values.values.any { it != null },
            )
        }

        private fun number(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }?.takeIf { it.isFinite() }
    }
}
