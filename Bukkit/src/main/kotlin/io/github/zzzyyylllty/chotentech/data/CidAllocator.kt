package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor
import taboolib.expansion.Id
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import taboolib.expansion.db
import taboolib.expansion.mapper
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

@TableName("cid_reservation")
class CidReservationRow {
    @Id
    @UniqueKey
    var cid: String = ""
    var playerName: String = ""
    var uuid: String = ""
    var createTime: Long = 0L
}

@TableName("global_state")
class GlobalStateRow {
    @Id
    var stateKey: String = ""
    var registeredUsers: Long = 0L
    var cidStep: Long = 0L
}

/**
 * CID 分配器（区间硬编码在代码里，调整常量即可，无需改配置）：
 * - 短区间 [CID_MIN, CID_MAX] 按 100 步进窗口搜索，窗口内随机探测
 * - 窗口内占用命中超过 5 次则步进 +100（只改缓存，分配完成后异步落盘），最多提升 3 次
 * - 全部失败或步进越界则回退旧区间 [FALLBACK_MIN, FALLBACK_MAX]
 * - cid_reservation 表作为原子闸门：唯一键插入成功才算抢到 CID（解决并发撞号）
 */
object CidAllocator {

    // ── 区间常量 ──
    const val CID_MIN = 11000L
    const val CID_MAX = 19999L
    const val STEP_SIZE = 100L
    /** 步进硬上限，超过即回退旧区间（留给以后扩大短区间用） */
    const val STEP_HARD_LIMIT = 99999L
    const val FALLBACK_MIN = 100000000L
    const val FALLBACK_MAX = 9999999999L

    private const val STATE_KEY = "cid_alloc"
    private const val MAX_RAISES = 3
    private const val MAX_PROBES_PER_WINDOW = 25
    private const val HIT_THRESHOLD = 5
    private const val FALLBACK_RETRIES = 30
    private const val COUNT_PERIOD_TICKS = 6000L

    private val cidMapper by mapper<CidReservationRow>(db("config.yml", "database", "chotentech.db"))
    private val stateMapper by mapper<GlobalStateRow>(db("config.yml", "database", "chotentech.db"))

    /** 当前步进缓存（窗口起点），只增不减 */
    private val stepCache = AtomicLong(0)
    /** 已成功落盘的步进，避免重复写入 */
    private val lastPersistedStep = AtomicLong(-1)
    @Volatile
    private var fallbackOnly = false
    @Volatile
    private var fallbackWarned = false

    @Volatile
    var registeredUsers: Long = 0L
        private set

    private var countTask: PlatformExecutor.PlatformTask? = null

    // ==================== 生命周期 ====================

    @Awake(LifeCycle.ENABLE)
    fun init() {
        try {
            val row = stateMapper.findById(STATE_KEY)
            val dbStep = row?.cidStep ?: 0L
            registeredUsers = row?.registeredUsers ?: 0L
            lastPersistedStep.set(dbStep)
            if (dbStep > STEP_HARD_LIMIT) {
                fallbackOnly = true
                warning("[CidAllocator] CID 步进 $dbStep 已超过上限 $STEP_HARD_LIMIT，新玩家将直接使用旧区间 (${FALLBACK_MIN}-${FALLBACK_MAX})")
            } else if (dbStep > 0 && dbStep < CID_MIN) {
                // 数据库步进不足起始值，从 CID_MIN 开始
                stepCache.set(CID_MIN)
                writeState()
            } else {
                stepCache.set(maxOf(dbStep, 0L))
            }
        } catch (e: Exception) {
            warning("[CidAllocator] 加载全局状态失败: ${e.message}")
        }
        countTask = submitAsync(period = COUNT_PERIOD_TICKS) {
            refreshRegisteredUsers()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        countTask?.cancel()
        countTask = null
        writeState()
    }

    // ==================== 分配 ====================

    /**
     * 分配一个 CID。playerName/uuid 为 null 时仅探测不预留（调试用）。
     * 短区间耗尽会回退旧区间；全部失败抛异常。
     */
    fun allocate(playerName: String?, uuid: String?): Long {
        if (!fallbackOnly) {
            var raises = 0
            while (raises < MAX_RAISES) {
                val windowStart = currentWindowStart() ?: break
                val cid = tryReserveInWindow(windowStart, playerName, uuid)
                if (cid != null) {
                    persistStepAsync()
                    return cid
                }
                raiseStep()
                raises++
            }
            if (!fallbackWarned) {
                fallbackWarned = true
                warning("[CidAllocator] 短区间 $CID_MIN-$CID_MAX 探测失败，回退旧区间 (${FALLBACK_MIN}-${FALLBACK_MAX})")
            }
        }
        val fallback = tryReserveFallback(playerName, uuid)
        if (fallback == null) {
            throw IllegalStateException("无法分配CID: 短区间已耗尽且旧区间 (${FALLBACK_MIN}-${FALLBACK_MAX}) 重试失败")
        }
        persistStepAsync()
        return fallback
    }

    /** 窗口起点：首次使用时把 0 提升到 CID_MIN（只改缓存，落盘由分配完成后的 persistStepAsync 负责） */
    private fun currentWindowStart(): Long? {
        while (true) {
            val cur = stepCache.get()
            if (cur == 0L || cur < CID_MIN) {
                if (stepCache.compareAndSet(cur, CID_MIN)) {
                    return if (CID_MIN > CID_MAX) null else CID_MIN
                }
                continue
            }
            if (cur > STEP_HARD_LIMIT) {
                if (!fallbackOnly) {
                    fallbackOnly = true
                    warning("[CidAllocator] CID 步进已超出上限 $STEP_HARD_LIMIT，回退旧区间 (${FALLBACK_MIN}-${FALLBACK_MAX})")
                }
                return null
            }
            if (cur > CID_MAX) return null
            return cur
        }
    }

    private fun raiseStep() {
        stepCache.addAndGet(STEP_SIZE)
    }

    private fun tryReserveInWindow(windowStart: Long, playerName: String?, uuid: String?): Long? {
        val probed = HashSet<Long>()
        var hits = 0
        repeat(MAX_PROBES_PER_WINDOW) {
            if (probed.size >= STEP_SIZE.toInt()) return null
            val candidate = ThreadLocalRandom.current().nextLong(windowStart, windowStart + STEP_SIZE)
            if (!probed.add(candidate)) return@repeat
            if (isCidTaken(candidate.toString())) {
                if (++hits > HIT_THRESHOLD) return null  // 窗口内已占用超过5次 → 提升步进
                return@repeat
            }
            if (playerName == null || uuid == null) return candidate  // 探测模式（调试）
            if (reserve(candidate, playerName, uuid)) return candidate
            if (++hits > HIT_THRESHOLD) return null  // 并发竞争失败同样视为占用
        }
        return null
    }

    private fun tryReserveFallback(playerName: String?, uuid: String?): Long? {
        val probed = HashSet<Long>()
        repeat(FALLBACK_RETRIES) {
            val candidate = ThreadLocalRandom.current().nextLong(FALLBACK_MIN, FALLBACK_MAX)
            if (!probed.add(candidate)) return@repeat
            if (isCidTaken(candidate.toString())) return@repeat
            if (playerName == null || uuid == null) return candidate
            if (reserve(candidate, playerName, uuid)) return candidate
        }
        return null
    }

    // ==================== 占用检查 / 原子闸门 ====================

    /** 三表检查：旧 user_data + player_profiles + cid_reservation */
    fun isCidTaken(cid: String): Boolean {
        return ProfileManager.isCIDTaken(cid) || cidMapper.findById(cid) != null
    }

    /**
     * 原子预留：唯一键插入成功才算拿到 CID。
     * 本人已持有同一 CID（如靓号升级）视为可用。
     */
    fun reserve(cid: Long, playerName: String, uuid: String): Boolean {
        val key = cid.toString()
        return try {
            cidMapper.insert(CidReservationRow().also {
                it.cid = key
                it.playerName = playerName
                it.uuid = uuid
                it.createTime = System.currentTimeMillis()
            })
            true
        } catch (e: Exception) {
            try {
                // 唯一键冲突：确认归属；锁/连接抖动（非唯一键冲突）也按不可用处理，探测会重试
                cidMapper.findById(key)?.uuid == uuid
            } catch (_: Exception) {
                false
            }
        }
    }

    /** 该 CID 是否由指定玩家持有 */
    fun isReservedBy(cid: Long, uuid: String): Boolean =
        cidMapper.findById(cid.toString())?.uuid == uuid

    /** 释放预留（仅用于付费前失败的回滚；已有数据引用的 CID 永不释放） */
    fun releaseReservation(cid: Long) {
        try {
            cidMapper.deleteById(cid.toString())
        } catch (e: Exception) {
            warning("[CidAllocator] 释放预留 $cid 失败: ${e.message}")
        }
    }

    fun getAllReservedCids(): Set<String> {
        return try {
            cidMapper.rawQuery({ rows("cid") }) { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    // ==================== 持久化 ====================

    /** 步进落盘：只写当前缓存值（单调不回退），未变化则跳过 */
    private fun persistStepAsync() {
        if (stepCache.get() <= lastPersistedStep.get()) return
        writeStateAsync()
    }

    private fun writeStateAsync() {
        submitAsync { writeState() }
    }

    private fun writeState() {
        try {
            stateMapper.insertOrUpdate(GlobalStateRow().also {
                it.stateKey = STATE_KEY
                it.registeredUsers = registeredUsers
                it.cidStep = stepCache.get()
            }) { "state_key" eq STATE_KEY }
            lastPersistedStep.set(stepCache.get())
        } catch (e: Exception) {
            warning("[CidAllocator] 写入全局状态失败: ${e.message}")
        }
    }

    /** 定时统计注册用户数（player_profiles 的 distinct uuid），写库 */
    private fun refreshRegisteredUsers() {
        try {
            val count = ProfileManager.profileMapper.rawQuery({ rows("COUNT(DISTINCT uuid)") }) { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
            registeredUsers = count
            writeStateAsync()
        } catch (e: Exception) {
            warning("[CidAllocator] 统计注册用户失败: ${e.message}")
        }
    }
}
