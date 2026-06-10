package io.github.zzzyyylllty.chotentech.data

import org.bukkit.Location
import org.bukkit.util.Vector

enum class RandomNoclipMode { EACH, RESTART }

data class SharedNoclipSession(
    val paidBy: MutableSet<String> = mutableSetOf(),
    val playersInRange: MutableSet<String> = mutableSetOf(),
    var unlocked: Boolean = false,
)

data class NoclipConditions(
    val item: ItemCondition? = null,
    val car: CarCondition? = null,
    val coop: CoopCondition? = null,
    val signal: SignalCondition? = null,
    val dropbag: DropbagCondition? = null,
    val condition: List<ConditionChainEntry>? = null,
)

data class ItemCondition(
    val shared: Boolean = false,
    val lifetime: Boolean = false,
    val items: List<ConditionItem>,
    val message: String? = null,
)

data class ConditionItem(
    val id: String,
    val amount: Int = 1,
    val consume: Boolean = true,
)

data class CarCondition(
    val shared: Boolean = false,
    val lifetime: Boolean = false,
    val cost: String,
    val amount: Long = 0,
    val maxPlayers: Int = 4,
    val message: String? = null,
)

data class CoopCondition(
    val require: Int = 2,
    val max: Int = 5,
    val message: String? = null,
)

data class SignalCondition(
    val item: Any,  // String or List<String>
    val world: String? = null,
    val location1: Vector? = null,
    val location2: Vector? = null,
    val message: String? = null,
)

data class DropbagCondition(
    val armor: Boolean? = null,
    val inventory: Boolean? = null,
    val hotbar: Boolean = true,
    val helmet: Boolean? = null,
    val chestplate: Boolean? = null,
    val leggings: Boolean? = null,
    val boots: Boolean? = null,
    val message: String? = null,
)

data class ConditionChainEntry(
    val kether: String? = null,
    val message: String? = null,
    val yes: Boolean = false,
)

data class WeightedLocation(
    val location: Location,
    val weightExpr: String = "1",
)
