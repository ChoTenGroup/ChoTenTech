package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.loadedChunk
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.sharedNoclipSessions
import io.github.zzzyyylllty.chotentech.data.DataManager.getDataByCID
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.saveAll
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.releaseAll
import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import io.github.zzzyyylllty.chotentech.function.timer.cleanupNoclipState
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import taboolib.expansion.TableName
import java.util.UUID
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.key.Key as AdventureKey
import net.kyori.adventure.sound.Sound as AdventureSound
import net.kyori.adventure.sound.SoundStop as AdventureSoundStop
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
import taboolib.expansion.Id
import taboolib.expansion.Length
import taboolib.expansion.db
import taboolib.expansion.mapper
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToLong

fun emptyData(player: Player, profileId: String = "1", profileName: String = "Default"): PlayerData {
    return PlayerData(
        playerName = player.name,
        uuid = player.uniqueId.toString(),
        profileId = profileId,
        profileName = profileName,
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

@TableName("user_data")
class UserDataRow {
    @Id
    var uuid: String = ""
    var playerName: String = ""
    var cid: String = ""
    var cidType: String = ""
    @Length(-1)
    var data: String = ""
}

object DataManager {

    val userDataMapper by mapper<UserDataRow>(db("config.yml", "database", "chotentech.db"))

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
        return userDataMapper.findById(uuid)?.data
    }

    fun getRawDataByName(name: String): String? {
        return userDataMapper.findOne {
            "player_name" eq name
        }?.data
    }

    private fun PlayerData.fixDefaults(): PlayerData = copy(
        discoveredBases = discoveredBases ?: hashSetOf(),
        exploreredLevels = exploreredLevels ?: hashSetOf(),
        exploreredNoclips = exploreredNoclips ?: linkedMapOf(),
        achievements = achievements ?: linkedMapOf(),
        cosmetics = cosmetics ?: mutableMapOf(),
        warehouseContents = warehouseContents ?: linkedMapOf(),
        warehouseBaseSlots = warehouseBaseSlots,
        secureContainer = secureContainer ?: linkedMapOf(),
        insuredItems = insuredItems ?: linkedMapOf(),
        returnedInsuranceItems = returnedInsuranceItems ?: mutableListOf(),
        deathKeepItems = deathKeepItems ?: mutableListOf(),
        inventoryContents = inventoryContents ?: mutableMapOf(),
        hideoutData = hideoutData ?: linkedMapOf(),
        statusEffects = statusEffects ?: mutableListOf(),
        playTime = playTime ?: 0L,
        activeLevelStr = activeLevelStr,
        quetiapineData = quetiapineData ?: mutableMapOf(),
    )

    fun getData(uuid: String): PlayerData? {
        playerDataMap[uuid]?.let { return it }
        // Try loading from profile system
        return ProfileManager.getActiveProfileData(uuid)
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
        return userDataMapper.findOne {
            "cid" eq cid
        }?.data
    }

    fun saveData(player: Player) {
        saveAll(player)
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

        userDataMapper.insertOrUpdate(UserDataRow().also {
            it.uuid = uuid
            it.cid = newData.cid.id.toString()
            it.cidType = newData.cid.type
        }) { "uuid" eq uuid }
    }

    fun modifyUserID(userID: Long, playerData: PlayerData) {
        val uuid = playerData.uuid
        val newData = playerData.copy(cid = playerData.cid.copy(id = userID))
        playerDataMap[uuid] = newData

        userDataMapper.insertOrUpdate(UserDataRow().also {
            it.uuid = uuid
            it.cid = newData.cid.id.toString()
            it.cidType = newData.cid.type
        }) { "uuid" eq uuid }
    }

    fun isIDExist(cid: String): Boolean {
        return userDataMapper.findOne {
            "cid" eq cid
        } != null
    }

    fun isUUIDExist(uuid: String): Boolean {
        return userDataMapper.findById(uuid) != null
    }

    fun initData(player: Player): PlayerData {
        // Use profile system to ensure player has a profile loaded
        val data = ProfileManager.ensureProfile(player)
        data.lastLoginTime = System.currentTimeMillis()
        playerDataMap[player.uniqueId.toString()] = data
        // Increment join count
        ProfileManager.getSharedData(player.uniqueId.toString()).joinCount++
        saveData(player)
        return data
    }

    fun releaseData(player: Player) {
        saveAll(player)
        playerDataMap.remove(player.uniqueId.toString())
        ProfileManager.releaseAll(player.uniqueId.toString())
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
        val uuid = player.uniqueId.toString()
        cleanupNoclipState(uuid)
        io.github.zzzyyylllty.chotentech.listener.ProfileListener.onPlayerQuit(uuid)
        submitAsync {
            for (session in sharedNoclipSessions.values) {
                session.playersInRange.remove(uuid)
                session.paidBy.remove(uuid)
            }
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
        val current = getSanityRegenModifierValue()
        setSanityRegenModifier(maxOf(current + amount, 0.0))
    }

    fun removeSanityRegeneration(amount: Double) {
        val current = getSanityRegenModifierValue()
        setSanityRegenModifier(maxOf(current - amount, 0.0))
    }

    fun setSanityRegeneration(amount: Double) {
        setSanityRegenModifier(maxOf(amount, 0.0))
    }

    fun resetSanityRegeneration() {
        setSanityRegenModifier(0.0)
    }

    private companion object {
        private val SANITY_REGEN_MODIFIER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    private fun getSanityRegenModifierValue(): Double {
        return AttributeManager.getPlayerPersistentData(player.uniqueId)
            .modifiers.firstOrNull { it.uuid == SANITY_REGEN_MODIFIER_UUID }?.value ?: 0.0
    }

    private fun setSanityRegenModifier(value: Double) {
        val uuid = player.uniqueId
        AttributeManager.removeModifierByUuid(uuid, SANITY_REGEN_MODIFIER_UUID)
        if (value > 0.0) {
            AttributeManager.addModifier(uuid, AttributeModifier(
                attributeId = "sanity_regeneration",
                value = value,
                type = ModifierType.ADD,
                source = ModifierSource.OTHER,
                uuid = SANITY_REGEN_MODIFIER_UUID
            ))
        }
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
        data.noclipCooldownEnd = System.currentTimeMillis() + ((data.noclipPoint?.cooldown ?: 10.0) * 1000).roundToLong()
        data.noclipPoint = null
        data.noclipping = false
        data.noclipTimeEnd = null
    }

    fun updateLevel() {
        val newLevelId = player.getPlayerLevel()?.id
        if (newLevelId == null || newLevelId == data.activeLevelStr) return

        val oldLevelId = data.activeLevelStr
        val isNew = data.exploreredLevels.add(newLevelId)
        data.activeLevelStr = newLevelId

        if (isNew) {
            val level = data.activeLevel
            if (level != null) {
                player.sendMessage("<gold>■ <green>发现新层级: <yellow>${level.prefix}${level.name}".toComponent())
                player.sendMessage("<gray>${level.generateLore()}".toComponent())
            }
        }

        // Reset EACH-mode random noclip selections
        if (oldLevelId != null) {
            io.github.zzzyyylllty.chotentech.function.noclip.RandomNoclipSelector.resetPlayerRandom(player.uniqueId.toString(), oldLevelId)
        }

        resetAndPlayLevelMusic()
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
        data.externalInjury = minOf(data.externalInjury + amount, data.maxExternalInjury)
    }
    fun removeExternalInjury(amount: Double) {
        data.externalInjury = maxOf(data.externalInjury - amount, 0.0)
    }
    fun setExternalInjury(amount: Double) {
        data.externalInjury = amount.coerceIn(0.0, data.maxExternalInjury)
    }
    fun resetExternalInjury() {
        data.externalInjury = data.maxExternalInjury
    }

    // 内伤
    fun addInternalInjury(amount: Double) {
        data.internalInjury = minOf(data.internalInjury + amount, data.maxInternalInjury)
    }
    fun removeInternalInjury(amount: Double) {
        data.internalInjury = maxOf(data.internalInjury - amount, 0.0)
    }
    fun setInternalInjury(amount: Double) {
        data.internalInjury = amount.coerceIn(0.0, data.maxInternalInjury)
    }
    fun resetInternalInjury() {
        data.internalInjury = data.maxInternalInjury
    }

    // === 食物（自定义饥饿值/饱和度） ===

    fun addHunger(amount: Double) {
        data.customHunger = minOf(data.customHunger + amount, data.maxCustomHunger)
    }
    fun removeHunger(amount: Double) {
        data.customHunger = maxOf(data.customHunger - amount, 0.0)
    }
    fun setHunger(amount: Double) {
        data.customHunger = amount.coerceIn(0.0, data.maxCustomHunger)
    }
    fun resetHunger() {
        data.customHunger = minOf(defCustomHunger, data.maxCustomHunger)
    }
    fun fullHunger() {
        data.customHunger = data.maxCustomHunger
    }

    fun addSaturation(amount: Double) {
        data.customSaturation = minOf(data.customSaturation + amount, data.maxCustomSaturation)
    }
    fun removeSaturation(amount: Double) {
        data.customSaturation = maxOf(data.customSaturation - amount, 0.0)
    }
    fun setSaturation(amount: Double) {
        data.customSaturation = amount.coerceIn(0.0, data.maxCustomSaturation)
    }
    fun resetSaturation() {
        data.customSaturation = minOf(defCustomSaturation, data.maxCustomSaturation)
    }
    fun fullSaturation() {
        data.customSaturation = data.maxCustomSaturation
    }

    // === 体力 ===

    fun addStamina(amount: Double) {
        data.stamina = minOf(maxOf(data.stamina + amount, 0.0), data.maxStamina)
    }

    fun setStamina(amount: Double) {
        data.stamina = amount.coerceIn(0.0, data.maxStamina)
    }

    fun resetStamina() {
        data.stamina = minOf(defStamina, data.maxStamina)
    }

    fun fullStamina() {
        data.stamina = data.maxStamina
    }

    // === 手部体力 ===

    fun addHandStamina(amount: Double) {
        data.handStamina = minOf(maxOf(data.handStamina + amount, 0.0), data.maxHandStamina)
    }

    fun setHandStamina(amount: Double) {
        data.handStamina = amount.coerceIn(0.0, data.maxHandStamina)
    }

    fun resetHandStamina() {
        data.handStamina = minOf(defHandStamina, data.maxHandStamina)
    }

    fun fullHandStamina() {
        data.handStamina = data.maxHandStamina
    }

    fun addConsumption(amount: Double) {
        data.consumption = minOf(data.consumption + amount, 100.0)
    }
    fun removeConsumption(amount: Double) {
        data.consumption = maxOf(data.consumption - amount, 0.0)
    }
    fun setConsumption(amount: Double) {
        data.consumption = amount.coerceIn(0.0, 100.0)
    }
    fun resetConsumption() {
        data.consumption = 0.0
    }

    fun resetAndPlayLevelMusic() {
        data.lastMusicPlayTime = 0L
        data.musicReplayDelay = 0L
        player.stopSound(AdventureSoundStop.source(AdventureSound.Source.MUSIC))
        playLevelMusic()
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
        val pitch = 0.9 + ((data.sanity / data.effectiveMaxSanity) * 0.25)
        val soundKey = if (music.contains(':')) music else "kangel:$music"
        player.playSound(AdventureSound.sound(AdventureKey.key(soundKey), AdventureSound.Source.MUSIC, 10000f, pitch.toFloat()))
    }
}