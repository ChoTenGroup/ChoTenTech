package io.github.zzzyyylllty.chotentech.function.hideout

data class HideoutPlayerData(
    var unlocked: Boolean = false,
    var moduleLevels: MutableMap<String, Int> = mutableMapOf(),
    var fuelInventory: MutableMap<Int, String> = linkedMapOf(),
    var fuelAmount: Double = 0.0,
    var craftingQueues: MutableList<CraftingQueueItem> = mutableListOf(),
    var productionAccumulators: MutableMap<String, Double> = mutableMapOf(),
    var minerGpuItems: MutableList<String> = mutableListOf(),
    var bedroomLastUse: Long = 0L,
    var pendingUpgrades: MutableList<PendingUpgrade> = mutableListOf(),
) {
    fun getModuleLevel(moduleId: String): Int = moduleLevels[moduleId] ?: 0

    fun isModuleBuilt(moduleId: String): Boolean = getModuleLevel(moduleId) > 0
}

data class CraftingQueueItem(
    val recipeId: String,
    val startTime: Long,
    val craftTime: Int,
    val playerName: String,
) {
    val remainingTime: Int
        get() = maxOf(0, craftTime - ((System.currentTimeMillis() - startTime) / 1000).toInt())

    val isComplete: Boolean
        get() = remainingTime <= 0
}

data class PendingUpgrade(
    val moduleId: String,
    val targetLevel: Int,
    val startTime: Long,
    val upgradeTime: Int,
) {
    val remainingTime: Int
        get() = maxOf(0, upgradeTime - ((System.currentTimeMillis() - startTime) / 1000).toInt())

    val isComplete: Boolean
        get() = remainingTime <= 0
}
