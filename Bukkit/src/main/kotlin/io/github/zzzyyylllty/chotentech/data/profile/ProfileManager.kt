package io.github.zzzyyylllty.chotentech.data.profile

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.CidAllocator
import io.github.zzzyyylllty.chotentech.data.IDData
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import io.github.zzzyyylllty.chotentech.function.savepoint.SaveIdMigrator
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.TTLCache
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Location
import io.github.zzzyyylllty.chotentech.util.jsonUtils
import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.database.Database
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.expansion.Id
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.db
import taboolib.expansion.mapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ProfileInfo(
    val profileId: String,
    val profileName: String,
    val createTime: Long = System.currentTimeMillis(),
    val archivedAt: Long = 0L
)

object ProfileManager {

    /** uuid -> SharedData */
    val sharedDataMap = ConcurrentHashMap<String, SharedData>()

    /** uuid -> list of ProfileInfo (metadata cache) */
    val profileInfoMap = ConcurrentHashMap<String, List<ProfileInfo>>()

    /** 档案数据缓存(key = "uuid:profileId" -> PlayerData):
     *  消除主线程 DB 读取 —— switchProfile/选择流程在多人 + 自动保存写锁时不再阻塞主线程。
     *  固定 TTL:过期条目自动回源,避免离线查询的档案长驻内存;写路径 saveProfileData 同步刷新 */
    private val profileDataCache = TTLCache<String, PlayerData>(PROFILE_DATA_CACHE_TTL)

    /** 档案加载 miss 的负数缓存(固定短 TTL):占位符反复查询不存在档案时不再逐次查库 */
    private val profileMissCache = TTLCache<String, Unit>(MISS_CACHE_TTL)

    /** profile 表 cid -> uuid 索引:CID 占用探测(isCIDTaken)避免每次 findOne 发起 DB 连接 */
    private val profileCidIndex = ConcurrentHashMap<String, String>()

    /** profile 表 cid 占用 miss 的负数缓存(固定短 TTL):配合内存索引避免重复 findOne */
    private val profileCidMissCache = TTLCache<String, Unit>(MISS_CACHE_TTL)

    private const val PROFILE_DATA_CACHE_TTL = 10 * 60 * 1000L
    private const val MISS_CACHE_TTL = 30 * 1000L

    /** Set of uuids that have been migrated from old format */
    private val migrated = ConcurrentHashMap.newKeySet<String>()

    /** Prevent repeated schema migration attempts */
    private var schemaMigrated = false

    // ==================== PtcObject Data Classes & Mappers ====================

    class PlayerProfileRow {
        @Id
        var uuid: String = ""
        @Key
        var profileId: String = ""
        var profileName: String = ""
        var cid: String = ""
        var cidType: String = ""
        var createTime: Long = 0L
        @Length(-1)
        var data: String = ""
        var archived: Int = 0
        var archivedAt: Long = 0L
    }

    class PlayerSharedRow {
        @Id
        var uuid: String = ""
        @Length(-1)
        var data: String = ""
    }

    val profileMapper by mapper<PlayerProfileRow>(db("config.yml", "database", "chotentech.db"))
    val sharedMapper by mapper<PlayerSharedRow>(db("config.yml", "database", "chotentech.db"))

    // ==================== Schema Migration ====================

    /**
     * Auto-add columns that were added in later versions.
     * Uses reflection to access the HikariDataSource from TabooLib's internal Container.
     */
    private fun migrateSchema() {
        try {
            // Force mapper initialization (lazy delegate) so the Container's DataSource exists
            profileMapper.tableName

            val mapperImpl = profileMapper
            val containerField = mapperImpl::class.java.getDeclaredField("container")
            containerField.isAccessible = true
            val persistentContainer = containerField.get(mapperImpl)
            val container = persistentContainer::class.java.getMethod("getContainer").invoke(persistentContainer)
            val dataSource = container::class.java.getMethod("getDataSource").invoke(container) as javax.sql.DataSource

            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    try { stmt.execute("ALTER TABLE `player_profile_row` ADD COLUMN `archived` INT NOT NULL DEFAULT 0") } catch (_: Exception) { }
                    try { stmt.execute("ALTER TABLE `player_profile_row` ADD COLUMN `archivedAt` BIGINT NOT NULL DEFAULT 0") } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            devLog("[ProfileManager] Schema migration failed: ${e.message}")
        }
    }

    private fun ensureSchema() {
        if (schemaMigrated) return
        schemaMigrated = true
        migrateSchema()
    }

    // ==================== Migration ====================

    /**
     * Migrate existing data from [DataManager.table] (user_data) to the new profile system.
     * The old row becomes profile_id="1" in player_profiles, with shared fields extracted.
     */
    fun migrateOldData(uuid: String) {
        if (migrated.contains(uuid)) return
        // Check if old data exists
        val oldData = try {
            io.github.zzzyyylllty.chotentech.data.DataManager.getRawDataByUUID(uuid)
        } catch (_: Exception) { null }

        if (oldData.isNullOrBlank()) {
            migrated.add(uuid)
            return
        }

        // Check if already migrated (has profiles)
        if (getProfileInfos(uuid).isNotEmpty()) {
            migrated.add(uuid)
            devLog("[ProfileManager] Data already migrated for $uuid, skipping")
            return
        }

        try {
            val pData: PlayerData = jsonUtils.fromJson(oldData, PlayerData::class.java)

            // Create profile data without shared fields (BEFORE sharedData so we can use its CID)
            val profileData = pData.copy(
                profileId = "1",
                profileName = "Default",
                tape = 0L,
                ltoken = 0L,
                ptoken = 0L,
                achievements = linkedMapOf(),
                cosmetics = mutableMapOf()
            )
            val profileJson = jsonUtils.toJson(profileData, PlayerData::class.java)

            // Save shared data (with CID from profile)
            val sharedData = SharedData(
                activeProfileId = "1",
                tape = pData.tape,
                ltoken = pData.ltoken,
                ptoken = pData.ptoken,
                achievements = pData.achievements,
                cosmetics = pData.cosmetics,
                cid = profileData.cid     // 迁移 CID 到共享数据
            )

            // Insert into player_profiles
            profileMapper.insert(PlayerProfileRow().also {
                it.uuid = uuid
                it.profileId = "1"
                it.profileName = "Default"
                it.cid = profileData.cid.id.toString()
                it.cidType = profileData.cid.type
                it.createTime = profileData.createTime
                it.data = profileJson
            })

            // Save shared data
            val sharedJson = jsonUtils.toJson(sharedData, SharedData::class.java)
            sharedMapper.insert(PlayerSharedRow().also {
                it.uuid = uuid
                it.data = sharedJson
            })

            migrated.add(uuid)
            refreshProfileInfos(uuid)
            devLog("[ProfileManager] Data migrated for $uuid")
        } catch (e: Exception) {
            devLog("[ProfileManager] Migration failed for $uuid: ${e.message}")
        }
    }

    // ==================== Shared Data ====================

    fun getSharedData(uuid: String): SharedData {
        sharedDataMap[uuid]?.let { return it }

        // Try to load from DB
        val json = sharedMapper.findById(uuid)?.data

        val shared = if (json != null) {
            try {
                jsonUtils.fromJson(json, SharedData::class.java).normalize()
            } catch (_: Exception) {
                SharedData()
            }
        } else {
            SharedData()
        }
        sharedDataMap[uuid] = shared
        return shared
    }

    fun saveSharedData(uuid: String) {
        val shared = sharedDataMap[uuid] ?: return
        val json = jsonUtils.toJson(shared, SharedData::class.java)

        sharedMapper.insertOrUpdate(PlayerSharedRow().also {
            it.uuid = uuid
            it.data = json
        }) { "uuid" eq uuid }
    }

    fun releaseSharedData(uuid: String) {
        sharedDataMap.remove(uuid)
    }

    // ==================== Profile Info Cache ====================

    private fun loadProfileInfos(uuid: String): List<ProfileInfo> {
        ensureSchema()
        return profileMapper.findAll {
            "uuid" eq uuid
            "archived" eq 0
        }.map { row ->
            ProfileInfo(
                profileId = row.profileId,
                profileName = row.profileName,
                createTime = row.createTime
            )
        }
    }

    /** Load archived (soft-deleted) profile metadata */
    private fun loadArchivedProfileInfos(uuid: String): List<ProfileInfo> {
        return profileMapper.findAll {
            "uuid" eq uuid
            "archived" eq 1
        }.map { row ->
            ProfileInfo(
                profileId = row.profileId,
                profileName = row.profileName,
                createTime = row.createTime,
                archivedAt = row.archivedAt
            )
        }
    }

    /** Check if ANY profile row exists in DB for this uuid (bypasses cache) */
    private fun hasAnyProfileInDB(uuid: String): Boolean {
        return profileMapper.count { "uuid" eq uuid } > 0
    }

    fun getProfileInfos(uuid: String): List<ProfileInfo> {
        profileInfoMap[uuid]?.let { return it }
        val infos = loadProfileInfos(uuid)
        // Only cache non-empty results to avoid stale empty-cache race conditions
        if (infos.isNotEmpty()) {
            profileInfoMap[uuid] = infos
        }
        return infos
    }

    fun refreshProfileInfos(uuid: String) {
        profileInfoMap[uuid] = loadProfileInfos(uuid)
    }

    // ==================== Profile CRUD ====================

    /** key = "uuid:profileId" */
    private fun profileCacheKey(uuid: String, profileId: String) = "$uuid:$profileId"

    /** Load a profile's PlayerData from DB (without shared fields) — 缓存优先,避免主线程 DB 读取 */
    fun loadProfileData(uuid: String, profileId: String): PlayerData? {
        ensureSchema()
        val key = profileCacheKey(uuid, profileId)
        profileDataCache.get(key)?.let { return it }
        if (profileMissCache.get(key) != null) return null
        val row = profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq profileId
        }
        if (row == null) {
            profileMissCache.put(key, Unit)
            return null
        }

        val data = try {
            jsonUtils.fromJson(row.data, PlayerData::class.java)?.normalize()
        } catch (e: Exception) {
            devLog("[ProfileManager] Failed to deserialize profile $profileId for $uuid: ${e.message}")
            null
        }
        if (data != null) {
            // 存档点 ID 迁移:失效旧 ID(中文)按 name 匹配新英文 ID,join/切档随下次保存落库
            if (SaveIdMigrator.convert(data)) {
                devLog("[ProfileManager] 存档点 ID 迁移 $uuid/$profileId: baseSave=${data.baseSave}, normalSave=${data.normalSave}")
            }
            profileDataCache.put(key, data)
        }
        return data
    }

    /** Save a profile's PlayerData to DB (shared fields are NOT saved here) */
    fun saveProfileData(uuid: String, profileId: String, data: PlayerData) {
        val json = jsonUtils.toJson(data, PlayerData::class.java)
        val info = getProfileInfos(uuid).find { it.profileId == profileId }

        profileMapper.insertOrUpdate(PlayerProfileRow().also {
            it.uuid = uuid
            it.profileId = profileId
            it.profileName = info?.profileName ?: data.profileName
            it.cid = data.cid.id.toString()
            it.cidType = data.cid.type
            it.createTime = data.createTime
            it.data = json
        }) { "uuid" eq uuid and ("profile_id" eq profileId) }
        // 深拷贝入缓存:调用方(常为 playerDataMap 活对象)与缓存解耦,避免共享可变集合引用
        // 被异步读取方并发访问(CME/半更新读取)
        runCatching { jsonUtils.fromJson(json, PlayerData::class.java) }.getOrNull()
            ?.let { profileDataCache.put(profileCacheKey(uuid, profileId), it) }
        profileMissCache.remove(profileCacheKey(uuid, profileId))
        // cid 列已写入,同步内存索引并清除负数缓存,保证 isCIDTaken 不再查库
        if (data.cid.id != 0L) {
            profileCidIndex[data.cid.id.toString()] = uuid
            profileCidMissCache.remove(data.cid.id.toString())
        }
    }

    /** Get the active profile's PlayerData with shared fields merged in */
    fun getActiveProfileData(uuid: String): PlayerData? {
        val shared = getSharedData(uuid)
        val profileId = shared.activeProfileId
        val data = loadProfileData(uuid, profileId) ?: return null
        return mergeSharedFields(data, shared)
    }

    /** Switch the active profile and load its data into playerDataMap.
     *  Inventory capture/restore requires main thread — if called from async,
     *  the entire operation is bridged to main thread synchronously. */
    fun switchProfile(player: Player, profileId: String, skipProfileTeleport: Boolean = false): PlayerData? {
        // Must run on main thread for inventory API. Bridge from async if needed.
        if (!Bukkit.isPrimaryThread()) {
            var result: PlayerData? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { result = switchProfile(player, profileId, skipProfileTeleport) }
                finally { latch.countDown() }
            }
            latch.await()
            return result
        }

        val uuid = player.uniqueId.toString()

        // Accumulate play time for current session before saving
        accumulatePlayTime(player)

        // Save current profile data first (including inventory + location + attributes)
        // Skip if no active profile (player in forced-selection state)
        val currentData = playerDataMap[uuid]
        val currentActiveId = getSharedData(uuid).activeProfileId
        if (currentData != null && currentActiveId.isNotEmpty()) {
            // 将 Chemdah 内存中的任务数据(当前 profile)写回 playerDataMap,此时仍指向旧 profile
            ChemdahAPI.playerProfile[player.name]?.push()
            currentData.profileLocation = RespawnPoint.fromLocation(player.location)
            currentData.lastLogoutTime = System.currentTimeMillis()
            captureInventory(player)
            capturePotionEffects(player)
            // Sync shared fields (tape, ltoken, ptoken, achievements, cosmetics) before saving
            syncToSharedData(uuid, currentData)
            saveSharedData(uuid)
            // Save per-profile attribute modifiers before switching away
            val uuidObj = UUID.fromString(uuid)
            val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
            currentData.attributeModifiers = attrData.modifiers
            saveProfileData(uuid, currentActiveId, stripSharedFields(currentData))
        }

        // Load new profile
        val newData = loadProfileData(uuid, profileId) ?: return null
        val shared = getSharedData(uuid)

        // If shared CID is unset, seed from this profile's CID
        if (shared.cid.id == 0L && newData.cid.id != 0L) {
            shared.cid = newData.cid
            saveSharedData(uuid)
        }

        shared.activeProfileId = profileId
        saveSharedData(uuid)

        // Update per-profile last login time
        newData.lastLoginTime = System.currentTimeMillis()
        saveProfileData(uuid, profileId, stripSharedFields(newData))

        // Restore per-profile attribute modifiers for the incoming profile
        val uuidObj = UUID.fromString(uuid)
        val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
        if (newData.attributeModifiers != null) {
            attrData.modifiers = newData.attributeModifiers!!
        } else {
            // First access / migration: seed from current attributes.db
            newData.attributeModifiers = attrData.modifiers
            saveProfileData(uuid, profileId, stripSharedFields(newData))
        }
        AttributeManager.savePlayerData(attrData)
        AttributeManager.updatePlayer(player, async = true)

        val merged = mergeSharedFields(newData, shared)
        playerDataMap[uuid] = merged

        // 重新加载 Chemdah 任务数据:select(新 profile 数据) -> validation -> 写缓存 -> Selected 事件
        Database.loadProfile(player)

        // Restore inventory for the new profile
        applyInventory(player)

        // Restore potion effects for the new profile
        applyPotionEffects(player)

        // Teleport to the new profile's saved location, if any
        // (新建档案由调用方负责教学传送,此处跳过,避免先误传送到 Area0 fallback)
        if (!skipProfileTeleport) teleportToProfileLocation(player)

        // Re-detect current level based on player position (profile switch doesn't trigger move event)
        val helper = io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper(player)
        helper.updateLevel()

        // Signal LuckPerms context update for profile change
        io.github.zzzyyylllty.chotentech.hook.ProfileContextCalculator.signalUpdate(player)
        return merged
    }

    /** Create a new profile and load it */
    fun createProfile(player: Player, profileName: String): PlayerData? {
        val uuid = player.uniqueId.toString()
        val infos = getProfileInfos(uuid)
        val maxProfiles = getMaxProfiles(player)

        if (infos.size >= maxProfiles) return null
        // 不允许与活跃或已删除的 profile 同名
        if (infos.any { it.profileName == profileName }) return null
        if (loadArchivedProfileInfos(uuid).any { it.profileName == profileName }) return null

        // Find next available profile ID (auto-increment, never reuse)
        val existingIds: Set<Int> = infos.map { it.profileId.toIntOrNull() ?: 0 }.toSet()
        val newId = ((existingIds.maxOrNull() ?: 0) + 1).toString()

        // NOTE: Do NOT save old profile here — switchProfile below handles it
        // with proper inventory capture. Saving here without captureInventory
        // would overwrite the old profile's saved inventory with stale data.

        val now = System.currentTimeMillis()
        val shared = getSharedData(uuid)

        // Use shared CID (all profiles share the same CID)
        val cid = shared.cid

        // Create profile data
        val profileData = PlayerData(
            playerName = player.name,
            uuid = uuid,
            profileId = newId,
            profileName = profileName,
            cid = cid,
            createTime = now
        )

        // Save to DB
        saveProfileData(uuid, newId, profileData)
        refreshProfileInfos(uuid)

        // Switch to it, then teleport the freshly created profile to the tutorial start
        // (跳过 switchProfile 内部的档案位置传送,避免先误传到 Area0 fallback 再二次传送)
        val switched = switchProfile(player, newId, skipProfileTeleport = true)
        teleportToTutorialEntry(player)
        return switched
    }

    /** Soft-delete a profile: mark as archived in DB (cannot delete the last one). Max 10 archived per player. */
    fun deleteProfile(player: Player, profileId: String): Boolean {
        val uuid = player.uniqueId.toString()
        val infos = getProfileInfos(uuid)
        if (infos.size <= 1) return false

        val row = profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq profileId
        } ?: return false

        // Mark as archived in DB
        row.archived = 1
        row.archivedAt = System.currentTimeMillis()
        profileMapper.update(row)

        refreshProfileInfos(uuid)
        profileDataCache.remove(profileCacheKey(uuid, profileId))
        profileMissCache.remove(profileCacheKey(uuid, profileId))

        // Enforce max 10 archived: purge oldest if over limit
        purgeOldestArchived(uuid)

        // If the deleted profile was active, switch to first available
        val shared = getSharedData(uuid)
        if (shared.activeProfileId == profileId) {
            val first = getProfileInfos(uuid).firstOrNull() ?: return false
            shared.activeProfileId = first.profileId
            saveSharedData(uuid)
            switchProfile(player, first.profileId)
        }

        return true
    }

    /** Restore an archived profile back to active */
    fun restoreProfile(uuid: String, profileId: String): Boolean {
        val row = profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq profileId
            "archived" eq 1
        } ?: return false

        // Check name conflict with active profiles
        val activeNames = getProfileInfos(uuid).map { it.profileName }.toSet()
        if (row.profileName in activeNames) return false

        row.archived = 0
        row.archivedAt = 0L
        profileMapper.update(row)
        refreshProfileInfos(uuid)
        return true
    }

    /** Get archived (soft-deleted) profile infos for a player */
    fun getArchivedProfileInfos(uuid: String): List<ProfileInfo> {
        return loadArchivedProfileInfos(uuid)
    }

    /** If archived count > 10, permanently delete the oldest ones */
    private fun purgeOldestArchived(uuid: String) {
        val archived = loadArchivedProfileInfos(uuid).sortedBy { it.archivedAt }
        if (archived.size <= 10) return
        val toDelete = archived.take(archived.size - 10)
        for (info in toDelete) {
            profileMapper.findOne {
                "uuid" eq uuid
                "profile_id" eq info.profileId
            }?.cid?.let { profileCidIndex.remove(it) }
            profileMapper.deleteWhere {
                "uuid" eq uuid
                "profile_id" eq info.profileId
            }
            profileDataCache.remove(profileCacheKey(uuid, info.profileId))
            profileMissCache.remove(profileCacheKey(uuid, info.profileId))
        }
    }

    fun renameProfile(uuid: String, profileId: String, newName: String) {
        profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq profileId
        }?.let { row ->
            row.profileName = newName
            profileMapper.update(row)
        }
        refreshProfileInfos(uuid)
    }

    // ==================== Helpers ====================

    /** Copy shared fields FROM SharedData INTO PlayerData */
    private fun mergeSharedFields(data: PlayerData, shared: SharedData): PlayerData {
        // 如果 SharedData 有 CID 则使用共享的，否则保留 profile 自带的 CID
        val effectiveCid = if (shared.cid.id != 0L) shared.cid else data.cid
        return data.copy(
            cid = effectiveCid,
            tape = shared.tape,
            ltoken = shared.ltoken,
            ptoken = shared.ptoken,
            achievements = shared.achievements,
            cosmetics = shared.cosmetics,
            // Shared settings
            pvpEnabled = shared.settings.getBoolean("pvpEnabled"),
            // Shared stats
            sessionStart = System.currentTimeMillis()
        )
    }

    /** Strip shared fields FROM PlayerData (for saving to profile DB) */
    fun stripSharedFields(data: PlayerData): PlayerData {
        return data.copy(
            tape = 0L,
            ltoken = 0L,
            ptoken = 0L,
            achievements = linkedMapOf(),
            cosmetics = mutableMapOf()
        )
    }

    /** Sync shared fields from PlayerData TO SharedData */
    fun syncToSharedData(uuid: String, data: PlayerData) {
        val shared = getSharedData(uuid)
        shared.tape = data.tape
        shared.ltoken = data.ltoken
        shared.ptoken = data.ptoken
        shared.achievements = data.achievements
        shared.cosmetics = data.cosmetics
        shared.settings.values["pvpEnabled"] = data.pvpEnabled.toString()
        // Shared stats are updated via accumulatePlayTime, not directly from PlayerData
    }

    /**
     * Deduplicate profiles with the same name — keep only one, delete extras.
     * Keep priority: the ACTIVE profile (never delete the row the player is on),
     * then the row with the freshest saved data (player's newest progress),
     * then the lowest profileId.
     * This is a safety net for the race-condition bug that created
     * duplicate "Default" profiles on join.
     */
    private fun deduplicateProfiles(uuid: String) {
        val infos = loadProfileInfos(uuid)
        val activeId = getSharedData(uuid).activeProfileId
        val groups = infos.groupBy { it.profileName }
        for ((_, dups) in groups) {
            if (dups.size <= 1) continue
            // 活跃档案必须保留;否则保留数据最新(lastLoginTime)的行,避免玩家进度被旧行覆盖
            val keep = dups.firstOrNull { it.profileId == activeId }
                ?: dups.maxByOrNull { readLastLoginTime(uuid, it) ?: 0L }
                ?: dups.minBy { it.profileId.toIntOrNull() ?: Int.MAX_VALUE }
            for (dup in dups) {
                if (dup.profileId != keep.profileId) {
                    profileMapper.findOne {
                        "uuid" eq uuid
                        "profile_id" eq dup.profileId
                    }?.cid?.let { profileCidIndex.remove(it) }
                    profileMapper.deleteWhere {
                        "uuid" eq uuid
                        "profile_id" eq dup.profileId
                    }
                    profileDataCache.remove(profileCacheKey(uuid, dup.profileId))
                    profileMissCache.remove(profileCacheKey(uuid, dup.profileId))
                    devLog("[ProfileManager] Removed duplicate profile '${dup.profileName}' (${dup.profileId}) for $uuid, kept ${keep.profileId}")
                }
            }
        }
        refreshProfileInfos(uuid)
    }

    /** Best-effort read of the profile's last login time from its saved data JSON */
    private fun readLastLoginTime(uuid: String, info: ProfileInfo): Long? {
        val row = profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq info.profileId
        } ?: return null
        val parsed = runCatching { jsonUtils.fromJson(row.data, PlayerData::class.java) }.getOrNull() ?: return null
        return parsed.lastLoginTime.takeIf { it > 0 }
    }

    /** Accumulate play time for the current session into both per-profile and shared counters. */
    fun accumulatePlayTime(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - data.sessionStart
        if (elapsed <= 0) return
        data.playTime += elapsed
        data.sessionStart = now
        // Also add to shared total
        getSharedData(uuid).totalPlayTime += elapsed
    }

    fun getMaxProfiles(player: Player): Int {
        val configMax = config.getInt("profile.max-profiles", 3)
        // Permission-based extension: chotentech.profile.5, chotentech.profile.10, etc.
        var max = configMax
        for (i in (configMax + 1)..100) {
            if (player.hasPermission("chotentech.profile.$i")) {
                max = maxOf(max, i)
            }
        }
        // Add profile ending bonuses (tracked in SharedData meta)
        val sharedData = getSharedData(player.uniqueId.toString())
        val endedBonus = sharedData.meta["ended_profile_bonus"]?.value?.toIntOrNull() ?: 0
        max += endedBonus
        return max
    }

    /** Check if CID is unique across ALL data sources */
    fun isCIDTaken(cid: String): Boolean {
        // Check old user_data table
        if (io.github.zzzyyylllty.chotentech.data.DataManager.isIDExist(cid)) return true
        // Check new profile table(内存索引优先)
        if (profileCidIndex.containsKey(cid)) return true
        if (profileCidMissCache.get(cid) != null) return false
        val row = profileMapper.findOne {
            "cid" eq cid
        }
        if (row != null) {
            profileCidIndex[cid] = row.uuid
            return true
        }
        profileCidMissCache.put(cid, Unit)
        return false
    }

    /** profile 表全量已占 CID(供 CidAllocator 启动时加载内存占用集合) */
    fun getAllProfileCids(): Set<String> {
        return try {
            profileMapper.rawQuery({ rows("cid") }) { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun generateProfileID(playerName: String, uuid: String): IDData {
        return IDData(id = CidAllocator.allocate(playerName, uuid), type = "NORMAL")
    }

    // ==================== Inventory ====================

    /** Serialize player's current inventory into their PlayerData.
     *  Must run on main thread (uses Bukkit inventory API). */
    fun captureInventory(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { captureInventory(player) }
                finally { latch.countDown() }
            }
            latch.await()
            return
        }
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val inv = player.inventory
        val map = mutableMapOf<Int, String>()
        for (i in 0..40) {
            val item = inv.getItem(i) ?: continue
            val serialized = ItemSerializer.serializeNbt(item) ?: continue
            map[i] = serialized
        }
        data.inventoryContents = map
    }

    /** Restore inventory from PlayerData onto the player.
     *  Must run on main thread (uses Bukkit inventory API). */
    fun applyInventory(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { applyInventory(player) }
                finally { latch.countDown() }
            }
            latch.await()
            return
        }
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val inv = player.inventory

        // Always clear first to prevent item duplication when switching to an empty-profile
        inv.clear()

        if (data.inventoryContents.isNullOrEmpty()) return

        data.inventoryContents.forEach { (slot, serialized) ->
            val item = ItemSerializer.deserializeNbt(serialized)
            if (item != null && slot in 0..40) {
                inv.setItem(slot, item)
            }
        }
    }

    /** Clear inventory contents from PlayerData (for fresh profiles) */
    private fun clearInventoryData(data: PlayerData): PlayerData {
        return data.copy(inventoryContents = mutableMapOf())
    }

    // ==================== Potion Effects ====================

    /** Capture current Bukkit potion effects into PlayerData and clear them. Must run on main thread. */
    fun capturePotionEffects(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { capturePotionEffects(player) }
                finally { latch.countDown() }
            }
            latch.await()
            return
        }
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        data.potionEffects = player.activePotionEffects.map { io.github.zzzyyylllty.chotentech.data.PotionEffectData.fromBukkit(it) }.toMutableList()
        player.clearActivePotionEffects()
    }

    /** Restore saved potion effects from PlayerData onto the player. Must run on main thread. */
    fun applyPotionEffects(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { applyPotionEffects(player) }
                finally { latch.countDown() }
            }
            latch.await()
            return
        }
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        for (ped in data.potionEffects) {
            val bukkit = ped.toBukkit() ?: continue
            player.addPotionEffect(bukkit, true)
        }
    }

    // ==================== Save All ====================

    /** Save the player's current location into their active profile. */
    fun saveProfileLocation(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        data.profileLocation = RespawnPoint.fromLocation(player.location)
    }

    /** Teleport the player to their profile's saved location, if any. */
    fun teleportToProfileLocation(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            submit { teleportToProfileLocation(player) }
            return
        }
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return

        grantHudAfterTutorialIfMissing(player)

        data.profileLocation?.toLocation()?.let { loc ->
            // 加载区块，防止掉落虚空
            val chunk = loc.chunk
            if (!chunk.isLoaded) chunk.load()
            // 检查 Y 是否有效（低于世界最低高度则用安全坐标）
            val minY = loc.world?.minHeight ?: -64
            val safeLoc = if (loc.y < minY || loc.y > 320) {
                loc.world?.let { w ->
                    val highest = w.getHighestBlockYAt(loc.blockX, loc.blockZ)
                    loc.clone().apply { y = maxOf(highest + 1.0, minY + 5.0) }
                } ?: loc
            } else loc
            player.teleport(safeLoc)
            return
        }

        // Fallback: no saved location — use Area0 spawn or world spawn
        val world = Bukkit.getWorld("Area0") ?: Bukkit.getWorlds().first()
        val fallback = if (world.name == "Area0") Location(world, -6.0, 14.0, 5.0)
            else Location(world, 0.5, 1.0, 0.5)
        data.profileLocation = RespawnPoint.fromLocation(fallback)
        player.teleport(fallback)
    }

    /** 档案加载后检查:延迟 3 秒,若玩家已完成教学任务且缺少 hudshow.health 权限,则授予 hudshow.* 权限 */
    fun grantHudAfterTutorialIfMissing(player: Player) {
        val uuid = player.uniqueId.toString()
        submit(delay = 60) { // 3 秒 = 60 tick
            try {
                if (!player.isOnline) return@submit
                val shared = try { getSharedData(uuid) } catch (_: Exception) { null } ?: return@submit
                if (!shared.isCompletedTutorial) return@submit
                if (player.hasPermission("hudshow.health")) return@submit
                // 通过 lp 命令授予 hudshow 权限
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "lp user ${player.name} permission set hudshow.* true"
                )
                devLog("[档案] 玩家 ${player.name} 已完成教学,已授予 hudshow 权限")
            } catch (t: Throwable) {
                devLog("[档案] 授予 hudshow 权限失败 ${player.name}: ${t.message}")
            }
        }
    }

    /** 教学关卡起点传送位置(world x y z yaw pitch) */
    private val TUTORIAL_LOCATION = "world 30027 20 71 90 0"

    /** 新档案进入时的教学传送:传送到教学关卡起点;已完成教学的玩家提示可用 /skiptutorial 跳过 */
    fun teleportToTutorialEntry(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            submit { teleportToTutorialEntry(player) }
            return
        }
        val parts = TUTORIAL_LOCATION.split(" ")
        val world = Bukkit.getWorld(parts[0])
        if (world == null) {
            teleportToProfileLocation(player)
            return
        }
        val loc = Location(world, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(), parts[4].toFloat(), parts[5].toFloat())
        val chunk = loc.chunk
        if (!chunk.isLoaded) chunk.load()
        player.teleport(loc)
        // 教学传送后立即按新位置检测层级(与 switchProfile 内部逻辑一致)
        io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper(player).updateLevel()
        if (getSharedData(player.uniqueId.toString()).isCompletedTutorial) {
            player.sendComponent("<#EFDF8F>你已完成教学,接取教程任务后可使用 <#84D1CB>/skiptutorial <#EFDF8F>跳过教学直接开始游戏")
        }
    }

    /** Save all profile data and shared data for a player */
    fun saveAll(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val profileId = getSharedData(uuid).activeProfileId

        // Accumulate play time before saving
        accumulatePlayTime(player)

        // Track quit/logout timestamps
        val now = System.currentTimeMillis()
        data.lastLogoutTime = now
        getSharedData(uuid).lastQuitTime = now

        // Save current location
        data.profileLocation = RespawnPoint.fromLocation(player.location)

        // Capture current inventory into PlayerData
        captureInventory(player)

        // Sync shared fields from PlayerData to SharedData
        syncToSharedData(uuid, data)

        // Save shared data
        saveSharedData(uuid)

        // Capture current attribute modifiers into the profile
        if (profileId.isNotEmpty()) {
            val uuidObj = UUID.fromString(uuid)
            val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
            data.attributeModifiers = attrData.modifiers
        }

        // 未选档案(强制选择状态)时只保存共享数据,不写档案行 —— 否则会向 profile 表插入 profileId="" 的脏行
        if (profileId.isNotEmpty()) {
            saveProfileData(uuid, profileId, stripSharedFields(data))
        }
    }

    /**
     * 周期自动保存玩家的档案与共享数据。
     * 主线程完成 Bukkit API 捕获(位置/背包)、共享字段同步与 JSON 序列化快照;DB 写入也在主线程执行,
     * 与 quit/切档的同步写库完全串行 —— 引用校验与写库之间不会穿插其他流程。
     * 序列化必须在主线程:stripped 是浅拷贝,嵌套集合与 playerDataMap 中的活跃对象共享引用,
     * 异步序列化会与主线程的并发修改竞争 —— 轻则 CME 导致本轮保存失败,重则 HashMap 扩容期迭代
     * 产出"缺条目但合法"的 JSON 落库覆盖新数据(静默数据丢失)。每 tick 仅保存 2 人的节奏下,
     * 捕获与写库开销(毫秒级)由主线程承担可接受。
     * 写库前校验 map 引用:退出流程(saveAll)或切档(switchProfile)已写库并替换/移除条目后,
     * 本快照已过期,放弃落库,防止旧数据覆盖新数据。
     */
    fun saveAllAsync(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val shared = getSharedData(uuid)
        val profileId = shared.activeProfileId

        // ---- 主线程:Bukkit API 与共享字段同步 ----
        accumulatePlayTime(player)
        val now = System.currentTimeMillis()
        data.lastLogoutTime = now
        shared.lastQuitTime = now
        data.profileLocation = RespawnPoint.fromLocation(player.location)
        captureInventory(player)
        syncToSharedData(uuid, data)

        // 未选档案(强制选择状态)时只保存共享数据,不写档案行(与 saveAll 一致)
        val stripped = if (profileId.isNotEmpty()) stripSharedFields(data) else null

        // ---- 主线程:序列化快照 ----
        val sharedJson = jsonUtils.toJson(shared, SharedData::class.java)
        var profileJson: String? = null
        var profileName = data.profileName
        var createTime = data.createTime
        val cid = data.cid.id.toString()
        val cidType = data.cid.type
        if (stripped != null) {
            val info = getProfileInfos(uuid).find { it.profileId == profileId }
            profileName = info?.profileName ?: data.profileName
            stripped.attributeModifiers = AttributeManager.getPlayerPersistentData(UUID.fromString(uuid)).modifiers
            profileJson = jsonUtils.toJson(stripped, PlayerData::class.java)
        }

        // ---- 主线程:DB 写入(引用校验与写库原子) ----
        submit {
            if (playerDataMap[uuid] !== data) return@submit
            try {
                sharedMapper.insertOrUpdate(PlayerSharedRow().also {
                    it.uuid = uuid
                    it.data = sharedJson
                }) { "uuid" eq uuid }

                if (stripped != null && profileJson != null) {
                    profileMapper.insertOrUpdate(PlayerProfileRow().also {
                        it.uuid = uuid
                        it.profileId = profileId
                        it.profileName = profileName
                        it.cid = cid
                        it.cidType = cidType
                        it.createTime = createTime
                        it.data = profileJson
                    }) { "uuid" eq uuid and ("profile_id" eq profileId) }
                    // 深拷贝入缓存:与活数据解耦,异步读取方(getDataByCID/占位符)不会并发读共享集合
                    runCatching { jsonUtils.fromJson(profileJson, PlayerData::class.java) }.getOrNull()
                        ?.let { profileDataCache.put(profileCacheKey(uuid, profileId), it) }
                    profileMissCache.remove(profileCacheKey(uuid, profileId))
                }
            } catch (e: Exception) {
                devLog("[ProfileManager] 自动保存失败 $uuid: ${e.message}")
            }
        }
    }

    fun releaseAll(uuid: String) {
        sharedDataMap.remove(uuid)
        profileInfoMap.remove(uuid)
        val prefix = "$uuid:"
        profileDataCache.removeIf { it.startsWith(prefix) }
        profileMissCache.removeIf { it.startsWith(prefix) }
    }

    /** Ensure a player has at least one profile — called on join */
    fun ensureProfile(player: Player): PlayerData {
        val uuid = player.uniqueId.toString()
        val now = System.currentTimeMillis()

        // Ensure schema migration runs BEFORE any mapper access
        ensureSchema()

        // Migrate old data if needed
        migrateOldData(uuid)

        // Update shared join tracking
        val shared = getSharedData(uuid)
        if (shared.joinCount == 0 && shared.firstJoinTime == 0L) {
            shared.firstJoinTime = now
        }
        shared.lastJoinTime = now
        shared.joinCount++

        // 是否为本次进服自动创建的新档案(用于教学传送)
        var freshProfileCreated = false

        // Check if profiles exist — query DB directly to bypass any stale cache
        if (!hasAnyProfileInDB(uuid)) {
            freshProfileCreated = true
            // Generate CID (only for the very first profile)
            val cid = generateProfileID(player.name, uuid)
            // Save CID to shared data so all profiles share it
            shared.cid = cid

            val defaultLoc = if (Bukkit.getWorld("Area0") != null) {
                Location(Bukkit.getWorld("Area0")!!, -6.0, 14.0, 5.0)
            } else {
                Location(Bukkit.getWorlds().first(), 0.5, 1.0, 0.5)
            }
            val profileData = PlayerData(
                playerName = player.name,
                uuid = uuid,
                profileId = "1",
                profileName = "Default",
                cid = cid,
                createTime = now,
                profileLocation = RespawnPoint.fromLocation(defaultLoc)
            )
            saveProfileData(uuid, "1", profileData)
            refreshProfileInfos(uuid)

            shared.activeProfileId = "1"
        } else {
            // Refresh cache from DB in case it was stale
            refreshProfileInfos(uuid)
            // Deduplicate: remove any extra profiles beyond the first with the same name
            deduplicateProfiles(uuid)
        }

        saveSharedData(uuid)

        // Load active profile
        val data = loadProfileData(uuid, shared.activeProfileId)
        if (data == null) {
            // Fallback: load first profile
            val first = getProfileInfos(uuid).firstOrNull()
            if (first != null) {
                shared.activeProfileId = first.profileId
                saveSharedData(uuid)
                val fallbackData = loadProfileData(uuid, first.profileId)
                if (fallbackData != null) {
                    fallbackData.lastLoginTime = now
                    saveProfileData(uuid, first.profileId, stripSharedFields(fallbackData))
                    val merged = mergeSharedFields(fallbackData, shared)
                    playerDataMap[uuid] = merged
                    // Load profile-specific attribute modifiers
                    val uuidObj = UUID.fromString(uuid)
                    val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
                    if (fallbackData.attributeModifiers != null) {
                        attrData.modifiers = fallbackData.attributeModifiers!!
                        AttributeManager.savePlayerData(attrData)
                    } else {
                        fallbackData.attributeModifiers = attrData.modifiers
                        saveProfileData(uuid, first.profileId, stripSharedFields(fallbackData))
                    }
                    AttributeManager.updatePlayer(player, async = true)
                    applyInventory(player)
                    teleportToProfileLocation(player)
                    return merged
                }
            }
            // Should never happen — clear inventory for fresh start
            val freshCid = generateProfileID(player.name, uuid)
            val empty = PlayerData(playerName = player.name, uuid = uuid, cid = freshCid)
            shared.cid = freshCid
            saveSharedData(uuid)
            playerDataMap[uuid] = empty
            if (Bukkit.isPrimaryThread()) player.inventory.clear()
            else submit { player.inventory.clear() }
            return empty
        }

        // If shared CID is unset, seed from this profile's CID
        if (shared.cid.id == 0L && data.cid.id != 0L) {
            shared.cid = data.cid
            saveSharedData(uuid)
        }

        // Update per-profile last login time (BEFORE merge so merged copy carries it)
        data.lastLoginTime = now
        saveProfileData(uuid, shared.activeProfileId, stripSharedFields(data))

        val merged = mergeSharedFields(data, shared)
        playerDataMap[uuid] = merged

        // Load profile-specific attribute modifiers (seed from shared on first access)
        val uuidObj = UUID.fromString(uuid)
        val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
        if (data.attributeModifiers != null) {
            attrData.modifiers = data.attributeModifiers!!
            AttributeManager.savePlayerData(attrData)
        } else {
            data.attributeModifiers = attrData.modifiers
            saveProfileData(uuid, shared.activeProfileId, stripSharedFields(data))
        }
        AttributeManager.updatePlayer(player, async = true)

        // Restore inventory for this profile
        applyInventory(player)
        // 自动创建的 Default 档案传送至教学关卡起点,其余档案传送至档案保存位置
        if (freshProfileCreated) teleportToTutorialEntry(player) else teleportToProfileLocation(player)
        return merged
    }
}
