package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.DataManager.getDataByCID
import io.github.zzzyyylllty.chotentech.data.DataManager.releaseData
import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.key.Key as AdventureKey
import net.kyori.adventure.sound.Sound as AdventureSound
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table
import taboolib.module.database.getHost
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

fun emptyData(player: Player): PlayerData {
    return PlayerData(
        playerName = player.name,
        uuid = player.uniqueId.toString(),
        cid = generateID(player)
    )
}

fun generateID(player: Player? = null): IDData {
    var id = ThreadLocalRandom.current().nextLong(
        config.getLong("id.range.min", 100000000),
        config.getLong("id.range.max", 9999999999)
    )

    repeat(20) {
        if (getDataByCID(id.toString()) == null) {
            return IDData(
                id = id,
                type = "NORMAL"
            )
        }
        id = ThreadLocalRandom.current().nextLong(
            config.getLong("id.range.min", 100000000),
            config.getLong("id.range.max", 9999999999)
        )
    }

    player?.kick(Component.text("发生致命错误: 无法为您 (${player.name}) 分配UID，请联系管理员。").color(RED))
    throw IllegalArgumentException("发生致命错误: 无法为玩家 ${player?.name} 分配UID")
}

fun Player.getData(): PlayerData? {
    return DataManager.getData(uniqueId.toString())
}

object DataManager {

    val host = config.getHost("database")

    val dataSource by lazy { host.createDataSource() }

    val table = Table("user_data", host) {
        add { id() }
        add("player_name") {
            type(ColumnTypeSQL.VARCHAR, 128) {
                options(ColumnOptionSQL.KEY)
            }
        }
        add("uuid") {
            type(ColumnTypeSQL.VARCHAR, 64) {
                options(ColumnOptionSQL.UNIQUE_KEY, ColumnOptionSQL.NOTNULL)
            }
        }
        add("cid") {
            type(ColumnTypeSQL.VARCHAR, 16) {
                options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL)
            }
        }
        add("cid_type") {
            type(ColumnTypeSQL.VARCHAR, 64)
        }
        add("data") {
            type(ColumnTypeSQL.LONGTEXT)
        }
    }

//    val tableChunkData = Table("chunk_data", host) {
//        add { id() }
//        add("position") {
//            type(ColumnTypeSQL.VARCHAR, 64) {
//                options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL)
//            }
//        }
//        add("world") {
//            type(ColumnTypeSQL.VARCHAR, 64) {
//                options(ColumnOptionSQL.KEY, ColumnOptionSQL.NOTNULL)
//            }
//        }
//        add("data") {
//            type(ColumnTypeSQL.LONGTEXT)
//        }
//    }

    init {
        table.createTable(dataSource)
//        tableChunkData.createTable(dataSource)

        devLog("dataSource: ${dataSource}")
    }

    // 待加载区块，改成 ChunkPos，避免持有 Bukkit Chunk 对象
    val prepareLoadChunk = Collections.synchronizedSet(HashSet<ChunkPos>())

    // 正在保存的区块，防止重复提交
    private val savingChunks = Collections.synchronizedSet(HashSet<ChunkPos>())

    @OptIn(ExperimentalAtomicApi::class)
    private val isLoadReleased = AtomicBoolean(true)

    private fun chunkPosOf(chunk: Chunk) = ChunkPos(chunk.world.name, chunk.x, chunk.z)

    fun loadChunkDataQuery(chunk: Chunk) {
        val pos = chunkPosOf(chunk)
        if (loadedChunk[pos] != null) return
        prepareLoadChunk.add(pos)
    }

    /**
     * 定时批量把待加载区块读入内存
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun autoLoadChunk() {
        submitAsync(period = 60) {
            if (!isLoadReleased.compareAndSet(true, false)) {
                return@submitAsync
            }

            val snapshot = synchronized(prepareLoadChunk) {
                val list = prepareLoadChunk.toList()
                prepareLoadChunk.clear()
                list
            }

            if (snapshot.isEmpty()) {
                isLoadReleased.store(true)
                return@submitAsync
            }

            val batchSize = maxOf(1, (snapshot.size + 4) / 5)
            val batches = snapshot.chunked(batchSize)
            val latch = CountDownLatch(batches.size)

            batches.forEachIndexed { index, batch ->
                submitAsync {
                    try {
                        devLog("第 ${index + 1} 批区块加载开始，数量=${batch.size}")
                        batch.forEach { pos ->
                            val chunk = Bukkit.getWorld(pos.world)?.getChunkAt(pos.x, pos.y)
                            if (chunk != null && chunk.isLoaded) {
                                loadChunkData(chunk)
                            }
                        }
                        devLog("第 ${index + 1} 批区块加载完成")
                    } catch (e: Exception) {
                        devLog("第 ${index + 1} 批区块加载失败：${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            submitAsync {
                latch.await()
                isLoadReleased.store(true)
                devLog("所有待加载区块处理完成")
            }
        }
    }

    fun loadChunkData(chunk: Chunk) {
        val pos = chunkPosOf(chunk)
        if (loadedChunk[pos] != null) return

        val long = asLong(chunk.x, chunk.z)
        devLog("[CHUNK] 区块 ${chunk.world.name} - ${chunk.x}, ${chunk.z} 正在读取")
        val data = chunk.persistentDataContainer.get(NamespacedKey.fromString("choten:chunkdata")!!, ChunkDataPDCType())

//        val data = tableChunkData.select(dataSource) {
//            where("position" eq long)
//            where("world" eq chunk.world.name)
//            rows("data")
//            limit(1)
//        }.firstOrNull {
//            getString("data")
//        }

        loadedChunk[pos] = if (data == null) {
            devLog("[CHUNK] 区块 ${chunk.world.name} - ${chunk.x}, ${chunk.z} 不存在数据库，将创建")
            ChunkProvince(chunk.world.name, chunk.x, chunk.z, ChunkData(), true)
        } else {
            ChunkProvince(
                chunk.world.name,
                chunk.x,
                chunk.z,
                data,
                false
            )
        }
    }

    /**
     * 标记区块脏数据，不立即写库
     */
    fun markChunkDirty(world: String, x: Int, z: Int) {
        val pos = ChunkPos(world, x, z)
        loadedChunk[pos]?.dirty = true
    }

    fun saveChunkData(world: String, x: Int, z: Int) {
        Bukkit.getWorld(world)?.getChunkAt(x, z)?.let { saveChunkData(it) }
    }

    /**
     * 保存单个区块，带防重复提交
     */
    fun saveChunkData(chunk: Chunk) {
        val pos = ChunkPos(chunk.world.name, chunk.x, chunk.z)
        val province = loadedChunk[pos] ?: return

        if (!savingChunks.add(pos)) return

        submitAsync {
            try {

//                tableChunkData.insert(dataSource, "position", "world", "data") {
//                    value(
//                        asLong(x, z),
//                        world,
//                        dataJson
//                    )
//                    onDuplicateKeyUpdate {
//                        update("data", dataJson)
//                    }
//                }

                chunk.persistentDataContainer.set(NamespacedKey.fromString("choten:chunkdata")!!, ChunkDataPDCType(), province.data)

                province.dirty = false
            } catch (e: Exception) {
                warning("保存区块失败 ${chunk.world.name}:${chunk.x},${chunk.z} -> ${e.message}")
            } finally {
                savingChunks.remove(pos)
            }
        }
    }

    /**
     * 卸载区块时：只有 dirty 才保存
     */
    fun unloadChunkData(chunk: Chunk) {
        val pos = chunkPosOf(chunk)
        val province = loadedChunk[pos]

        if (province != null && province.dirty) {
            saveChunkData(chunk.world.name, chunk.x, chunk.z)
        }

        devLog("[CHUNK] 区块 ${chunk.world.name} - ${chunk.x}, ${chunk.z} 已卸载")
        loadedChunk.remove(pos)
    }

    fun getChunkData(chunk: Chunk): ChunkProvince? {
        return loadedChunk[chunkPosOf(chunk)]
    }

    fun clearChunkData(chunk: Chunk) {
        val pos = chunkPosOf(chunk)
        devLog("[CHUNK] 区块 ${chunk.world.name} - ${chunk.x}, ${chunk.z} 开始清理")

        loadedChunk[pos] = ChunkProvince(chunk.world.name, chunk.x, chunk.z, ChunkData(), true)
        saveChunkData(chunk.world.name, chunk.x, chunk.z)
    }

    /**
     * 重新设计：主线程采集区块方块，异步保存
     */
    fun logUnbreakChunkData(chunk: Chunk) {
        val pos = chunkPosOf(chunk)

        // 先在主线程采集方块数据
        val data = ChunkData()
        for (x in 0..15) {
            for (y in -64..319) {
                for (z in 0..15) {
                    val block = chunk.getBlock(x, y, z)
                    if (!block.type.isAir) {
                        data.unbreakableBlock.add(BlockPos(block.x, block.y, block.z))
                    }
                }
            }
        }

        devLog("[CHUNK] 区块 ${chunk.world.name} - ${chunk.x}, ${chunk.z} 更改提交，${data.unbreakableBlock.size} 个方块被记录。")
        loadedChunk[pos] = ChunkProvince(chunk.world.name, chunk.x, chunk.z, data, true)
        saveChunkData(chunk.world.name, chunk.x, chunk.z)

    }

    fun getRawDataByUUID(uuid: String): String? {
        return table.select(dataSource) {
            rows("data")
            where("uuid" eq uuid)
            limit(1)
        }.firstOrNull {
            getString("data")
        }
    }

    fun getRawDataByName(name: String): String? {
        return table.select(dataSource) {
            rows("data")
            where("player_name" eq name)
            limit(1)
        }.firstOrNull {
            getString("data")
        }
    }

    private fun PlayerData.fixDefaults(): PlayerData = copy(
        discoveredBases = discoveredBases ?: hashSetOf(),
        exploreredLevels = exploreredLevels ?: hashSetOf(),
        exploreredNoclips = exploreredNoclips ?: linkedMapOf(),
        achievements = achievements ?: linkedMapOf(),
        warehouseContents = warehouseContents ?: linkedMapOf(),
        warehouseBaseSlots = warehouseBaseSlots,
        secureContainer = secureContainer ?: linkedMapOf(),
        insuredItems = insuredItems ?: linkedMapOf(),
        returnedInsuranceItems = returnedInsuranceItems ?: mutableListOf(),
        deathKeepItems = deathKeepItems ?: mutableListOf(),
        hideoutData = hideoutData ?: linkedMapOf(),
        statusEffects = statusEffects ?: mutableListOf(),
    )

    fun getData(uuid: String): PlayerData? {
        playerDataMap[uuid]?.let { return it }
        val dataStr = getRawDataByUUID(uuid)
        val data = dataStr?.let {
            try {
                jsonUtils.fromJson(it, PlayerData::class.java)?.fixDefaults()
            } catch (e: Exception) {
                warning("Failed to deserialize player data for $uuid: ${e.message}")
                null
            }
        }
        return data?.uuid?.let { playerDataMap[it] } ?: data
    }

    fun getDataByName(name: String): PlayerData? {
        val dataStr = getRawDataByName(name)
        val data = dataStr?.let {
            try {
                jsonUtils.fromJson(it, PlayerData::class.java)?.fixDefaults()
            } catch (e: Exception) {
                warning("Failed to deserialize player data for name $name: ${e.message}")
                null
            }
        }
        return data?.uuid?.let { playerDataMap[it] } ?: data
    }

    fun getRawDataByCID(cid: String): String? {
        return table.select(dataSource) {
            rows("data")
            where("cid" eq cid)
            limit(1)
        }.firstOrNull {
            getString("data")
        }
    }

    fun saveData(player: Player) {
        val data = playerDataMap[player.uniqueId.toString()] ?: run {
            warning("玩家 ${player.name} 的数据未找到，跳过保存数据。")
            return
        }

        val dataJson = jsonUtils.toJson(data, PlayerData::class.java)

        table.insert(dataSource, "player_name", "uuid", "cid", "cid_type", "data") {
            value(
                player.name,
                player.uniqueId.toString(),
                data.cid.id,
                data.cid.type,
                dataJson
            )
            onDuplicateKeyUpdate {
                update("cid", data.cid.id)
                update("cid_type", data.cid.type)
                update("data", dataJson)
            }
        }
    }

    fun getDataByCID(cid: String): PlayerData? {
        val dataStr = getRawDataByCID(cid)
        val data = dataStr?.let {
            try {
                jsonUtils.fromJson(it, PlayerData::class.java)?.fixDefaults()
            } catch (e: Exception) {
                warning("Failed to deserialize player data for CID $cid: ${e.message}")
                null
            }
        }
        return data?.uuid?.let { playerDataMap[it] } ?: data
    }

    fun modifyUserIDData(userID: IDData, playerData: PlayerData) {
        val uuid = playerData.uuid
        val newData = playerData.copy(cid = userID)
        playerDataMap[uuid] = newData

        table.update(dataSource) {
            set("cid", newData.cid.id)
            set("cid_type", newData.cid.type)
            where("uuid" eq uuid)
        }
    }

    fun modifyUserID(userID: Long, playerData: PlayerData) {
        val uuid = playerData.uuid
        val newData = playerData.copy(cid = playerData.cid.copy(id = userID))
        playerDataMap[uuid] = newData

        table.update(dataSource) {
            set("cid", newData.cid.id)
            set("cid_type", newData.cid.type)
            where("uuid" eq uuid)
        }
    }

    fun isIDExist(cid: String): Boolean {
        return table.find(dataSource) {
            rows("data")
            where("cid" eq cid)
            limit(1)
        }
    }

    fun isUUIDExist(uuid: String): Boolean {
        return table.find(dataSource) {
            rows("data")
            where("uuid" eq uuid)
            limit(1)
        }
    }

    fun initData(player: Player): PlayerData {
        val pData = getData(player.uniqueId.toString()) ?: emptyData(player)
        val data = pData.copy(lastLoginTime = System.currentTimeMillis())
        playerDataMap[player.uniqueId.toString()] = data
        saveData(player)
        return data
    }

    fun releaseData(player: Player) {
        saveData(player)
        playerDataMap.remove(player.uniqueId.toString())
    }

    /**
     * 定时保存所有 dirty 区块
     */
    fun flushDirtyChunks() {
        submitAsync {
            val dirtyList = loadedChunk.entries
                .filter { it.value.dirty }
                .map { it.key }

            if (dirtyList.isEmpty()) return@submitAsync

            devLog("开始批量保存 dirty 区块，共 ${dirtyList.size} 个")

            dirtyList.forEach { pos ->
                saveChunkData(pos.world, pos.x, pos.y)
            }
        }
    }

    @SubscribeEvent
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 10))
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 10))
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 100, 127))
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 100, 127))

        submitAsync {
            Thread.sleep(2000)
            sendLoadingTips(player, "载入中...")
            sendLoadingTips(player, "正在拉取数据...")
            initData(player)
            sendLoadingTips(player, "完成.")
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1f, 1.7f)
            player.showTitle(
                Title.title(
                    "<yellow>阈限<aqua>天际<dark_blue>服务器".toComponent(),
                    "<green>${player.name}".toComponent()
                )
            )
            Thread.sleep(400)
            player.playSound(player, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1f, 1.7f)
        }
    }

    val title = "<#2CBAA8><b>十年不晚".toComponent()

    fun sendLoadingTips(player: Player, message: String) {
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.MASTER, 1f, 2f)
        player.showTitle(
            Title.title(
                title,
                "<aqua>${message}<#2CBAA8>...".toComponent(),
                Title.Times.times(
                    java.time.Duration.ofMillis(0),
                    java.time.Duration.ofMillis(1000),
                    java.time.Duration.ofMillis(0)
                )
            )
        )
        Thread.sleep(1000)
    }

    @SubscribeEvent
    fun onQuit(e: PlayerQuitEvent) {
        val player = e.player
        submitAsync {
            releaseData(player)
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun saveAllPlayerData() {
        Bukkit.getOnlinePlayers().forEach { player ->
            releaseData(player)
        }
        flushDirtyChunks()
    }
}

class ChoTenDataHelper(val player: Player) {
    val data = playerDataMap[player.uniqueId.toString()] ?: DataManager.initData(player)

    fun addSanity(amount: Double) {
        data.sanity = minOf(maxOf(data.sanity + amount, 0.0), data.effectiveMaxSanity)
    }

    fun removeSanity(amount: Double) {
        data.sanity = maxOf(data.sanity - amount, 0.0)
    }

    fun setSanity(amount: Double) {
        data.sanity = minOf(maxOf(amount, 0.0), data.effectiveMaxSanity)
    }

    fun fullSanity() {
        data.sanity = data.effectiveMaxSanity
    }

    fun resetSanity() {
        data.sanity = minOf(maxOf(config.getDouble("default.sanity", 40.0), 0.0), data.effectiveMaxSanity)
    }

    fun addMaxSanity(amount: Double) {
        data.maxSanity = maxOf(data.maxSanity + amount, 0.0)
    }

    fun removeMaxSanity(amount: Double) {
        data.maxSanity = maxOf(data.maxSanity - amount, 0.0)
    }

    fun setMaxSanity(amount: Double) {
        data.maxSanity = maxOf(amount, 0.0)
    }

    fun resetMaxSanity() {
        data.maxSanity = maxOf(config.getDouble("default.max-sanity", 100.0), 0.0)
    }

    fun addSanityRegeneration(amount: Double) {
        data.sanityRegeneration = maxOf(data.sanityRegeneration + amount, 0.0)
    }

    fun removeSanityRegeneration(amount: Double) {
        data.sanityRegeneration = maxOf(data.sanityRegeneration - amount, 0.0)
    }

    fun setSanityRegeneration(amount: Double) {
        data.sanityRegeneration = maxOf(amount, 0.0)
    }

    fun resetSanityRegeneration() {
        data.sanityRegeneration = maxOf(config.getDouble("default.sanity-regen", 0.0), 0.0)
    }

    fun addStress(amount: Double) {
        data.stress = minOf(maxOf(data.stress + amount, 0.0), 100.0)
    }

    fun removeStress(amount: Double) {
        data.stress = maxOf(data.stress - amount, 0.0)
    }

    fun setStress(amount: Double) {
        data.stress = minOf(maxOf(amount, 0.0), 100.0)
    }

    fun resetStress() {
        data.stress = minOf(maxOf(config.getDouble("default.stress", 0.0), 0.0), 100.0)
    }

    fun addDarkness(amount: Double) {
        data.darkness = minOf(maxOf(data.darkness + amount, 0.0), data.effectiveMaxDarkness)
    }

    fun removeDarkness(amount: Double) {
        data.darkness = maxOf(data.darkness - amount, 0.0)
    }

    fun setDarkness(amount: Double) {
        data.darkness = minOf(maxOf(amount, 0.0), data.effectiveMaxDarkness)
    }

    fun fullDarkness() {
        data.darkness = data.effectiveMaxDarkness
    }

    fun resetDarkness() {
        data.darkness = minOf(maxOf(config.getDouble("default.darkness", 15.0), 0.0), data.effectiveMaxDarkness)
    }

    fun addMaxDarkness(amount: Double) {
        data.maxDarkness = maxOf(data.maxDarkness + amount, 0.0)
    }

    fun removeMaxDarkness(amount: Double) {
        data.maxDarkness = maxOf(data.maxDarkness - amount, 0.0)
    }

    fun setMaxDarkness(amount: Double) {
        data.maxDarkness = maxOf(amount, 0.0)
    }

    fun resetMaxDarkness() {
        data.maxDarkness = maxOf(config.getDouble("default.max-darkness", 100.0), 0.0)
    }

    fun exitNoclip() {
        data.setCooldownSec(data.noclipPoint?.cooldown ?: 10.0)
        data.noclipPoint = null
        data.noclipping = false
    }

    fun updateLevel() {
        val newLevelId = player.getPlayerLevel()?.id
        if (newLevelId == null || newLevelId == data.activeLevelStr) return

        val isNew = data.exploreredLevels.add(newLevelId)
        data.activeLevelStr = newLevelId

        if (isNew) {
            val level = data.activeLevel
            if (level != null) {
                player.sendMessage("<gold>■ <green>发现新层级: <yellow>${level.prefix}${level.name}".toComponent())
                player.sendMessage("<gray>${level.generateLore()}".toComponent())
            }
        }
    }

    fun activeLevel(): Level? {
        return data.activeLevel
    }

    // 状态效果便捷方法 (线程安全, 可异步调用)

    fun addStatusEffect(type: String, level: Int, durationMs: Long) {
        val existing = data.statusEffects.firstOrNull { it.type == type }
        if (existing != null) {
            if (level > existing.level) {
                data.statusEffects.remove(existing)
                data.statusEffects.add(PlayerStatusEffect(type, level, durationMs))
            } else {
                existing.remainingDuration = maxOf(existing.remainingDuration, durationMs)
            }
        } else {
            data.statusEffects.add(PlayerStatusEffect(type, level, durationMs))
        }
    }

    fun removeStatusEffect(type: String) {
        data.statusEffects.removeAll { it.type == type }
    }

    fun hasStatusEffect(type: String): Boolean {
        return data.statusEffects.any { it.type == type }
    }

    fun getStatusEffectLevel(type: String): Int {
        return data.statusEffects.firstOrNull { it.type == type }?.level ?: 0
    }

    fun clearAllStatusEffects() {
        data.statusEffects.clear()
    }

    // 外伤
    fun addExternalInjury(amount: Double) {
        data.externalInjury = minOf(data.externalInjury + amount, data.effectiveMaxExternalInjury)
    }
    fun removeExternalInjury(amount: Double) {
        data.externalInjury = maxOf(data.externalInjury - amount, 0.0)
    }
    fun setExternalInjury(amount: Double) {
        data.externalInjury = amount.coerceIn(0.0, data.effectiveMaxExternalInjury)
    }
    fun resetExternalInjury() {
        data.externalInjury = data.effectiveMaxExternalInjury
    }

    // 内伤
    fun addInternalInjury(amount: Double) {
        data.internalInjury = minOf(data.internalInjury + amount, data.effectiveMaxInternalInjury)
    }
    fun removeInternalInjury(amount: Double) {
        data.internalInjury = maxOf(data.internalInjury - amount, 0.0)
    }
    fun setInternalInjury(amount: Double) {
        data.internalInjury = amount.coerceIn(0.0, data.effectiveMaxInternalInjury)
    }
    fun resetInternalInjury() {
        data.internalInjury = data.effectiveMaxInternalInjury
    }

    fun playLevelMusic() {
        val level = data.activeLevel ?: return
        val music = level.music ?: return
        val now = System.currentTimeMillis()
        if (data.musicReplayDelay == 0L) {
            data.musicReplayDelay = (120000L..300000L).random()
        }
        if (now - data.lastMusicPlayTime < data.musicReplayDelay) return
        data.lastMusicPlayTime = now
        data.musicReplayDelay = (120000L..300000L).random()
        val soundKey = if (music.contains(':')) music else "kangelsound:$music"
        player.playSound(AdventureSound.sound(AdventureKey.key(soundKey), AdventureSound.Source.MUSIC, 1f, 1f))
    }
}