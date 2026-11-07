package io.github.zzzyyylllty.chotentech.util

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 固定 TTL 的线程安全缓存。
 * 条目在 put 后 ttlMillis 毫秒过期;用于负数缓存时,过期保证"不存在"的结果不会永久滞留,
 * 新数据出现后最迟一个 TTL 周期即可见。
 * 过期条目在 get/清扫时移除,全局后台任务每 5 分钟清扫一次,防止无访问条目占内存。
 */
class TTLCache<K, V>(private val ttlMillis: Long) {

    private val map = ConcurrentHashMap<K, Entry<V>>()

    private data class Entry<V>(val timestamp: Long, val value: V)

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        map[key] = Entry(System.currentTimeMillis(), value)
    }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    fun removeIf(predicate: (K) -> Boolean) {
        map.keys.removeIf(predicate)
    }

    val size: Int get() = map.size

    fun sweep() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { (_, entry) -> now - entry.timestamp > ttlMillis }
    }

    companion object {
        private val instances = CopyOnWriteArrayList<TTLCache<*, *>>()
        private const val SWEEP_PERIOD_TICKS = 6000L // 5 分钟

        private var task: PlatformExecutor.PlatformTask? = null

        @Awake(LifeCycle.ENABLE)
        fun startSweeper() {
            // 插件重载（/tql reload 等）会重新走 ENABLE：先清掉上一代
            // 实例的引用，否则旧类加载器创建的 TTLCache 对象会通过这个
            // 静态列表被跨代强引用，导致整个旧插件类加载器无法回收
            // （热重载场景下的类加载器泄漏）。
            instances.clear()
            task = submitAsync(period = SWEEP_PERIOD_TICKS) {
                for (cache in instances) cache.sweep()
            }
        }

        @Awake(LifeCycle.DISABLE)
        fun stopSweeper() {
            task?.cancel()
            task = null
            // 同上：禁用时断开静态持有链。sweep 停了之后残留条目
            // 会随实例本身一起被 GC，无需保留引用。
            instances.clear()
        }
    }

    init {
        instances.add(this)
    }
}
