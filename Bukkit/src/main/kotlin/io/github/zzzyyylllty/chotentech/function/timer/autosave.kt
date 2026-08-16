package io.github.zzzyyylllty.chotentech.function.timer

import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

private var autosaveTask: PlatformExecutor.PlatformTask? = null

/** 玩家档案数据周期保存(每轮间隔 5 分钟):崩溃/强杀时最多丢失一轮进度,而非全部会话 */
private var playerAutosaveTask: PlatformExecutor.PlatformTask? = null

/** 每轮间隔 5 分钟 */
private const val PLAYER_AUTOSAVE_ROUND_MS = 5 * 60 * 1000L

/** 每个 tick 最多保存的玩家数:把全量保存摊开到多个 tick,避免一次性保存造成主线程卡顿 */
private const val PLAYERS_PER_TICK = 2

/** 本轮剩余待保存玩家(每轮开始时对在线玩家做快照,全部保存完后进入间隔等待) */
private var roundPlayers = mutableListOf<org.bukkit.entity.Player>()
private var roundIndex = 0
private var roundActive = false
private var nextRoundAt = 0L

@Awake(LifeCycle.ACTIVE)
fun autosave() {
    autosaveTask = submitAsync (period = 5000) {
        devLog("[CHUNK] === 自动保存 ===")
        loadedChunk.forEach { (pos, province) ->
            DataManager.saveChunkData(pos.world, pos.x, pos.y)
        }
        devLog("[CHUNK] === 自动保存完成 ===")
    }

    // 主线程执行:saveAll 会读取背包/位置并写库,不在异步线程操作;
    // 每 tick 只保存 PLAYERS_PER_TICK 人,把保存负载摊开
    playerAutosaveTask = submit(period = 20) {
        val now = System.currentTimeMillis()
        if (!roundActive) {
            if (now < nextRoundAt) return@submit
            roundPlayers = Bukkit.getOnlinePlayers().toMutableList()
            roundIndex = 0
            if (roundPlayers.isEmpty()) {
                nextRoundAt = now + PLAYER_AUTOSAVE_ROUND_MS
                return@submit
            }
            roundActive = true
        }

        var count = 0
        while (roundIndex < roundPlayers.size && count < PLAYERS_PER_TICK) {
            val player = roundPlayers[roundIndex++]
            if (player.isOnline) {
                // 主线程仅做位置/背包捕获,DB 读写全部在异步线程,不阻塞主线程
                ProfileManager.saveAllAsync(player)
            }
            count++
        }

        if (roundIndex >= roundPlayers.size) {
            roundActive = false
            nextRoundAt = now + PLAYER_AUTOSAVE_ROUND_MS
        }
    }
}

@Awake(LifeCycle.DISABLE)
fun quitSave() {
    autosaveTask?.cancel()
    autosaveTask = null
    playerAutosaveTask?.cancel()
    playerAutosaveTask = null
    roundPlayers.clear()
    roundActive = false
    loadedChunk.forEach { (pos, province) ->
        DataManager.saveChunkData(pos.world, pos.x, pos.y)
    }
}