package io.github.zzzyyylllty.chotentech.data

data class HideoutPlayerData(
    var unlocked: Boolean = false,
    var moduleLevels: MutableMap<String, Int> = mutableMapOf(),
    var fuelInventory: MutableMap<Int, String> = linkedMapOf(),
    var fuelAmount: Double = 0.0,
    var craftingQueues: MutableList<CraftingQueueItem> = mutableListOf(),
    // 生产模块累积量: moduleId -> accumulated amount
    var productionAccumulators: MutableMap<String, Double> = mutableMapOf(),
    // 矿场显卡 (序列化物品)
    var minerGpuItems: MutableList<String> = mutableListOf(),
    // 卧室上次点击时间戳
    var bedroomLastUse: Long = 0L,
    // 等待中的升级队列
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
