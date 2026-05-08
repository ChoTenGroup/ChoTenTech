package io.github.zzzyyylllty.chotentech.data

data class RankConfig(
    val id: String,
    val type: String,
    val display: LinkedHashMap<String, String> = linkedMapOf(),
    val name: String = "",
    val desc: String = "",
    val get: String = "",
    val condition: String = "",
    val priority: Int = 0
)
