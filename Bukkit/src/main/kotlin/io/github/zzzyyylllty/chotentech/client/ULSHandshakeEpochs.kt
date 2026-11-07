package io.github.zzzyyylllty.chotentech.client

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 按玩家区分的握手轮次，淘汰先收到但尚在主线程队列中等待的旧封包。 */
internal class ULSHandshakeEpochs {
    private val next = AtomicLong()
    private val current = ConcurrentHashMap<UUID, Long>()

    fun begin(uuid: UUID): Long = current.compute(uuid) { _, _ -> next.incrementAndGet() }!!

    fun snapshot(uuid: UUID): Long? = current[uuid]

    fun matches(uuid: UUID, epoch: Long?): Boolean = current[uuid] == epoch

    fun invalidate(uuid: UUID) { current.remove(uuid) }

    fun clear() { current.clear() }
}
