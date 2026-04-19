package io.github.zzzyyylllty.chotentech.chemdah

import ink.ptms.chemdah.core.PlayerProfile
import ink.ptms.chemdah.core.quest.QuestLoader.register
import ink.ptms.chemdah.core.quest.Task
import ink.ptms.chemdah.core.quest.objective.ObjectiveCountableI
import ink.ptms.chemdah.core.quest.selector.InferItem.Companion.toInferItem
import io.github.zzzyyylllty.chotentech.event.SubmitItemEvent
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.function.kether.evalKetherBoolean
import io.github.zzzyyylllty.sertraline.function.kether.evalKetherValue
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.hasItem
import taboolib.platform.util.isNotAir
import taboolib.platform.util.takeItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

@SubscribeEvent(EventPriority.MONITOR, ignoreCancelled = true)
fun test(e: SubmitItemEvent) {
    devLog("SubmitItemEvent Triggered: ${e.quest.id}-${e.task.id} / ${e.player.name}")
    devLog("Cancelled: ${e.isCancelled}")
}



object SubmitItemObjective : ObjectiveCountableI<SubmitItemEvent>() {

    override val name = "choten submit"
    override val event = SubmitItemEvent::class.java
    override val isListener = true
    override val isTickable = false

    init {
        handler {
            it.player
        }
        addFullCondition("item") { profile, task, e ->

            val quest = task.getQuest(profile)

            if (quest?.id == e.quest.id && task.id == e.task.id) {
                if (e.item != null) {

                    val item = task.condition["item"]!!.asList().toInferItem()
                    val amount = task.condition["amount"]?.toInt() ?: 1
                    val consume = task.condition["consume"]?.toBoolean() ?: true
                    val kether = task.condition["kether"]?.toString()
                    val player = e.player
                    // val items = player.inventory
                    // val hasItem = items.hasItem(amount) { item.isItem(it) }
                    val hasItem = item.isItem(e.item)
                    if (hasItem && kether != null) {
                        val bool = kether.evalKetherBoolean(player, linkedMapOf("@QuestContainer" to quest.template))
                        if (!bool) {
                            task.condition["deny-message"]?.toString()?.let { player.sendComponent(it) }
                            return@addFullCondition false
                        }
                    }
                    if (hasItem && consume) {
                        // items.takeItem(amount) { item.isItem(it) }
                        e.item.amount -= amount
                    }
                    hasItem
                    } else {
                        false
                    }

            } else {
                devLog("Quest check NOT passing")
                false
            }
        }
        addFullCondition("position") { profile, task, _ ->
            task.condition["position"]!!.toPosition().inside(profile.player.location)
        }

        addConditionVariable("amount") {
            it.item?.amount ?: 0
        }


    }

    override fun getCount(profile: PlayerProfile, task: Task, event: SubmitItemEvent): Int {
        return event.item?.amount ?: 0
    }
}

@Awake(LifeCycle.ENABLE)
fun registerSubmitItemObjective(){
    SubmitItemObjective.register()
}


/**
 * 移除ConcurrentHashMap中特定数量的符合特定规则的物品
 *
 * @param matcher   规则
 * @param savedItemStack 记录拿取物品的列表
 * @param amount    实例
 * @return boolean
 */
fun ConcurrentHashMap<Int, ItemStack>.takeItem(amount: Int = 1, takeList: MutableList<ItemStack> = mutableListOf(), matcher: (itemStack: ItemStack) -> Boolean): Boolean {
    var takeAmount = amount
    this.forEach { (index, itemStack) ->
        if (itemStack.isNotAir() && matcher(itemStack)) {
            takeAmount -= itemStack.amount
            if (takeAmount < 0) {
                takeList.add(itemStack.clone().apply { this.amount = takeAmount + itemStack.amount })
                itemStack.amount -= takeAmount + itemStack.amount
                return takeList.isNotEmpty()
            } else {
                takeList.add(itemStack.clone())
                this.remove(index)
                if (takeAmount == 0) {
                    return takeList.isNotEmpty()
                }
            }
        }
    }
    return takeList.isNotEmpty()
}

fun ConcurrentHashMap<Int, ItemStack>.hasItem(amount: Int = 1, matcher: (itemStack: ItemStack) -> Boolean): Boolean {
    var checkAmount = amount
    this.forEach { (index, itemStack) ->
        if (itemStack.isNotAir() && matcher(itemStack)) {
            checkAmount -= itemStack.amount
            if (checkAmount <= 0) {
                return true
            }
        }
    }
    return false
}

fun ConcurrentHashMap<Int, ItemStack>.countItem(matcher: (itemStack: ItemStack) -> Boolean): Int {
    var amount = 0
    this.forEach { (index, itemStack) ->
        if (itemStack.isNotAir() && matcher(itemStack)) {
            amount += itemStack.amount
        }
    }
    return amount
}
