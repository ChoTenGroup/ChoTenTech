package io.github.zzzyyylllty.chotentech.station

import io.github.zzzyyylllty.chotentech.shop.CostType

/** 单个合成站配置(对应 stations 目录下 yml 的一个顶级节点) */
data class StationConfig(
    val id: String,
    val name: String,
    val queue: Int = 0,
    val extraQueues: List<StationExtraQueue> = emptyList(),
    val recipes: LinkedHashMap<String, StationRecipe> = linkedMapOf(),
)

/** 额外队列解锁条件:满足则额外增加 slots 个队列槽 */
data class StationExtraQueue(
    val key: String,
    val display: String? = null,   // 展示文案(如 "好感达到 30 点"), 为空时回退到原始条件
    val affection: StationAffection? = null,
    val permission: String? = null,
    val slots: Int = 0,
)

/** 好感条件:NPC id 好感度达到 required */
data class StationAffection(
    val id: String,
    val required: Long,
)

/** 配方:产出 + 消耗 + 耗时(秒) */
data class StationRecipe(
    val key: String,
    val output: String,          // e.g. "sertraline:iron_sword"
    val buy: List<StationCost> = emptyList(),
    val time: Int = 0,
    val condition: StationCondition? = null,   // 制作条件(kether), 满足才可制作
)

/** 制作条件:kether 返回 true 表示满足; display 用于展示文案(可为多行) */
data class StationCondition(
    val kether: String? = null,
    val display: String? = null,
)

/** 单个消耗项:货币 或 物品 */
data class StationCost(
    val type: CostType,
    val currency: String? = null,   // CURRENCY 时: money/point/tape/goldentape
    val item: String? = null,       // ITEM 时: "sertraline:iron_ingot" 或材质名 "IRON_INGOT"
    val amount: Long = 1,
)

/** 玩家在某合成站的队列任务(按真实时间推进,重启可保留) */
data class StationQueueItem(
    val recipeId: String,
    val startTime: Long,
    val craftTime: Int,
) {
    val remainingTime: Int
        get() = maxOf(0, craftTime - ((System.currentTimeMillis() - startTime) / 1000).toInt())

    val isComplete: Boolean
        get() = remainingTime <= 0
}
