package io.github.zzzyyylllty.chotentech.feature.crossbow

import org.bukkit.entity.AbstractArrow
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * 飞行箭矢 / 防御缓存的**清理监听器**（P2 新增）。
 *
 * ### 为什么单独建一个文件，而不是塞进 [CrossbowManager]
 * 1. [CrossbowManager] 现在已经退回成纯「数据 + 计算」层：注册表、防御缓存、穿透公式，
 *    里面**没有任何事件订阅**。把事件塞回去会让这个对象重新变成「管理器 + 监听器」的混合体，
 *    而这次 P2 的目标恰恰是把旧弩体系彻底拆干净。
 * 2. 清理职责只有 3 个事件、20 行代码，独立成文件后 P5 的 `ArmorManager` 接手防御结算时，
 *    可以整个文件删除 / 替换，不用去动穿透公式那张表。
 * 3. 旧的 `CrossbowListener` 里混着 GUI、装填、低头抬头、原版弩发射拦截，那些都随旧体系删掉了；
 *    如果继承它的名字新建一个「同名文件」很容易让人以为旧逻辑还在，所以换名 [FlyingArrowListener]。
 *
 * ### 迁移过来的内容（只搬了清理，不搬业务）
 * | 事件 | 旧行为 | 现在 |
 * |---|---|---|
 * | `EntityDeathEvent` | 箭矢死亡 → `removeFlyingArrow` | 保留 |
 * | `EntityRemoveEvent` | 箭矢被移除（超时/掉出世界）→ `removeFlyingArrow` | 保留 |
 * | `PlayerQuitEvent` | 下线 → `invalidateDefenseCache` | 保留 |
 *
 * 原版弓/弩的箭矢清理是必须的：`flyingArrows` 是一张静态 `ConcurrentHashMap`，
 * 不清理就会随「射出去的箭」无限增长（热重载也不会释放）。
 */
object FlyingArrowListener {

    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity is AbstractArrow) {
            CrossbowManager.removeFlyingArrow(event.entity.uniqueId)
        }
    }

    @SubscribeEvent
    fun onEntityRemove(event: EntityRemoveEvent) {
        if (event.entity is AbstractArrow) {
            CrossbowManager.removeFlyingArrow(event.entity.uniqueId)
        }
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // 防御缓存是按 UUID 存的，玩家下线后这张表不会自己缩，必须主动失效
        CrossbowManager.invalidateDefenseCache(event.player.uniqueId)
    }
}
