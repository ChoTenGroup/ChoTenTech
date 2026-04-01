package io.github.zzzyyylllty.chotentech.feature

import io.github.zzzyyylllty.sertraline.data.defaultData
import io.github.zzzyyylllty.sertraline.event.SertralineCustomScriptDataLoadEvent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

@Awake(LifeCycle.ACTIVE)
fun registerFeatures() {
    defaultData["Features"] = Features
}


object Features {

    @JvmStatic
    fun launchLightningProjectile(
        player: Player,
        range: Double,
        power: Int,
        damage: Double,
        itemStack: ItemStack = ItemStack(Material.FIRE_CHARGE)
    ) {
        // 在玩家眼睛位置生成物品实体
        val item = player.world.dropItem(player.eyeLocation, itemStack).apply {
            // 设置物品速度
            velocity = player.location.direction.multiply(power)

            // 设置物品属性
            pickupDelay = Int.MAX_VALUE // 禁止拾取
            setCanMobPickup(false) // 禁止生物拾取

            // 可选：设置物品不会消失
            // isInvulnerable = true
            // ticksLived = 1 // 重置存活时间
        }

        var tickCount = 0
        val maxTicks = 200 // 最大存活时间（10秒），防止物品永久存在

        // 异步检测弹射物是否落地
        submit(period = 1, async = true) {
            tickCount++

            // 检查物品是否已移除、落地或超时
            if (item.isDead || !item.isValid || item.isOnGround || tickCount >= maxTicks) {
                val hitLocation = item.location

                // 在主线程中移除物品
                submit {
                    item.remove()
                }

                // 在主线程中查找最近的实体
                submit {
                    val nearestEntity = hitLocation.world?.livingEntities
                        ?.minByOrNull { entity ->
                            // 找到最近的实体
                            entity.location.distance(hitLocation)
                        }

                    // 如果找到实体，在其位置生成闪电并造成伤害
                    nearestEntity?.let { target ->
                        val targetLocation = target.location

                        // 生成闪电效果
                        targetLocation.world?.strikeLightningEffect(targetLocation)

                        // 造成伤害
                        if (!target.isDead) target.damage(damage, player)
                    }
                }

                // 取消调度器
                cancel()
            }
        }
    }
}