package io.github.zzzyyylllty.chotentech.data.profile

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.IDData
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.RespawnPoint
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Location
import io.github.zzzyyylllty.chotentech.util.jsonUtils
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
    val cid: IDData,
    val createTime: Long = System.currentTimeMillis()
)

object ProfileManager {

    /** uuid -> SharedData */
    val sharedDataMap = ConcurrentHashMap<String, SharedData>()

    /** uuid -> list of ProfileInfo (metadata cache) */
    val profileInfoMap = ConcurrentHashMap<String, List<ProfileInfo>>()

    /** Set of uuids that have been migrated from old format */
    private val migrated = ConcurrentHashMap.newKeySet<String>()

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
    }

    class PlayerSharedRow {
        @Id
        var uuid: String = ""
        @Length(-1)
        var data: String = ""
    }

    val profileMapper by mapper<PlayerProfileRow>(db("config.yml", "database", "chotentech.db"))
    val sharedMapper by mapper<PlayerSharedRow>(db("config.yml", "database", "chotentech.db"))

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

            // Extract shared fields
            val sharedData = SharedData(
                activeProfileId = "1",
                tape = pData.tape,
                goldentape = pData.goldentape,
                achievements = pData.achievements,
                cosmetics = pData.cosmetics
            )

            // Create profile data without shared fields
            val profileData = pData.copy(
                profileId = "1",
                profileName = "Default",
                tape = 0L,
                goldentape = 0L,
                achievements = linkedMapOf(),
                cosmetics = mutableMapOf()
            )
            val profileJson = jsonUtils.toJson(profileData, PlayerData::class.java)

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
                jsonUtils.fromJson(json, SharedData::class.java)
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
        return profileMapper.findAll {
            "uuid" eq uuid
        }.map { row ->
            ProfileInfo(
                profileId = row.profileId,
                profileName = row.profileName,
                cid = IDData(
                    id = row.cid.toLongOrNull() ?: 0L,
                    type = row.cidType
                ),
                createTime = row.createTime
            )
        }
    }

    fun getProfileInfos(uuid: String): List<ProfileInfo> {
        profileInfoMap[uuid]?.let { return it }
        val infos = loadProfileInfos(uuid)
        profileInfoMap[uuid] = infos
        return infos
    }

    fun refreshProfileInfos(uuid: String) {
        profileInfoMap[uuid] = loadProfileInfos(uuid)
    }

    // ==================== Profile CRUD ====================

    /** Load a profile's PlayerData from DB (without shared fields) */
    fun loadProfileData(uuid: String, profileId: String): PlayerData? {
        val json = profileMapper.findOne {
            "uuid" eq uuid
            "profile_id" eq profileId
        }?.data ?: return null

        return try {
            jsonUtils.fromJson(json, PlayerData::class.java)
        } catch (e: Exception) {
            devLog("[ProfileManager] Failed to deserialize profile $profileId for $uuid: ${e.message}")
            null
        }
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
    fun switchProfile(player: Player, profileId: String): PlayerData? {
        // Must run on main thread for inventory API. Bridge from async if needed.
        if (!Bukkit.isPrimaryThread()) {
            var result: PlayerData? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            submit {
                try { result = switchProfile(player, profileId) }
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
            currentData.profileLocation = RespawnPoint.fromLocation(player.location)
            captureInventory(player)
            // Save per-profile attribute modifiers before switching away
            val uuidObj = UUID.fromString(uuid)
            val attrData = AttributeManager.getPlayerPersistentData(uuidObj)
            currentData.attributeModifiers = attrData.modifiers
            saveProfileData(uuid, currentActiveId, stripSharedFields(currentData))
        }

        // Load new profile
        val newData = loadProfileData(uuid, profileId) ?: return null
        val shared = getSharedData(uuid)
        shared.activeProfileId = profileId
        saveSharedData(uuid)

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

        // Restore inventory for the new profile
        applyInventory(player)

        // Teleport to the new profile's saved location, if any
        teleportToProfileLocation(player)

        // Re-detect current level based on player position (profile switch doesn't trigger move event)
        val helper = io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper(player)
        helper.updateLevel()
        return merged
    }

    /** Create a new profile and load it */
    fun createProfile(player: Player, profileName: String): PlayerData? {
        val uuid = player.uniqueId.toString()
        val infos = getProfileInfos(uuid)
        val maxProfiles = getMaxProfiles(player)

        if (infos.size >= maxProfiles) return null
        if (infos.any { it.profileName == profileName }) return null

        // Find next available profile ID
        val existingIds: Set<Int> = infos.map { it.profileId.toIntOrNull() ?: 0 }.toSet()
        val newId = (1..99).first { it !in existingIds }.toString()

        // Generate CID
        val cid = generateProfileID(uuid)

        // Save current profile first
        val currentData = playerDataMap[uuid]
        if (currentData != null) {
            saveProfileData(uuid, getSharedData(uuid).activeProfileId, stripSharedFields(currentData))
        }

        val now = System.currentTimeMillis()

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

        // Switch to it
        return switchProfile(player, newId)
    }

    /** Delete a profile (cannot delete the last one) */
    fun deleteProfile(player: Player, profileId: String): Boolean {
        val uuid = player.uniqueId.toString()
        val infos = getProfileInfos(uuid)
        if (infos.size <= 1) return false

        profileMapper.deleteWhere {
            "uuid" eq uuid
            "profile_id" eq profileId
        }

        refreshProfileInfos(uuid)

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
        return data.copy(
            tape = shared.tape,
            goldentape = shared.goldentape,
            achievements = shared.achievements,
            cosmetics = shared.cosmetics,
            // Shared stats
            sessionStart = System.currentTimeMillis()
        )
    }

    /** Strip shared fields FROM PlayerData (for saving to profile DB) */
    fun stripSharedFields(data: PlayerData): PlayerData {
        return data.copy(
            tape = 0L,
            goldentape = 0L,
            achievements = linkedMapOf(),
            cosmetics = mutableMapOf()
        )
    }

    /** Sync shared fields from PlayerData TO SharedData */
    fun syncToSharedData(uuid: String, data: PlayerData) {
        val shared = getSharedData(uuid)
        shared.tape = data.tape
        shared.goldentape = data.goldentape
        shared.achievements = data.achievements
        shared.cosmetics = data.cosmetics
        // Shared stats are updated via accumulatePlayTime, not directly from PlayerData
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
        return max
    }

    /** Check if CID is unique across ALL data sources */
    fun isCIDTaken(cid: String): Boolean {
        // Check old user_data table
        if (io.github.zzzyyylllty.chotentech.data.DataManager.isIDExist(cid)) return true
        // Check new profile table
        return profileMapper.findOne {
            "cid" eq cid
        } != null
    }

    private fun generateProfileID(uuid: String): IDData {
        var id = (100000000L..9999999999L).random()
        repeat(20) {
            if (!isCIDTaken(id.toString())) {
                return IDData(id = id, type = "NORMAL")
            }
            id = (100000000L..9999999999L).random()
        }
        return IDData(id = id, type = "NORMAL")
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
     *  Only clears inventory if the profile actually has saved inventory data.
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

        // If profile has no inventory data, don't touch the player's items
        // (handles migration and new profiles gracefully)
        if (data.inventoryContents.isNullOrEmpty()) return

        inv.clear()
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
        data.profileLocation?.toLocation()?.let { player.teleport(it); return }
        // Fallback: no saved location — use Area0 spawn or world spawn
        val world = Bukkit.getWorld("Area0") ?: Bukkit.getWorlds().first()
        val fallback = if (world.name == "Area0") Location(world, -6.0, 14.0, 5.0)
            else Location(world, 0.5, 1.0, 0.5)
        data.profileLocation = RespawnPoint.fromLocation(fallback)
        player.teleport(fallback)
    }

    /** Save all profile data and shared data for a player */
    fun saveAll(player: Player) {
        val uuid = player.uniqueId.toString()
        val data = playerDataMap[uuid] ?: return
        val profileId = getSharedData(uuid).activeProfileId

        // Accumulate play time before saving
        accumulatePlayTime(player)

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

        // Save profile data (without shared fields)
        saveProfileData(uuid, profileId, stripSharedFields(data))
    }

    fun releaseAll(uuid: String) {
        sharedDataMap.remove(uuid)
        profileInfoMap.remove(uuid)
    }

    /** Ensure a player has at least one profile — called on join */
    fun ensureProfile(player: Player): PlayerData {
        val uuid = player.uniqueId.toString()

        // Migrate old data if needed
        migrateOldData(uuid)

        // Check if profiles exist
        val infos = getProfileInfos(uuid)
        if (infos.isEmpty()) {
            // Create default profile
            val now = System.currentTimeMillis()
            val cid = generateProfileID(uuid)
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

            val shared = getSharedData(uuid)
            shared.activeProfileId = "1"
            saveSharedData(uuid)
        }

        // Load active profile
        val shared = getSharedData(uuid)
        val data = loadProfileData(uuid, shared.activeProfileId)
        if (data == null) {
            // Fallback: load first profile
            val first = getProfileInfos(uuid).firstOrNull()
            if (first != null) {
                shared.activeProfileId = first.profileId
                saveSharedData(uuid)
                val fallbackData = loadProfileData(uuid, first.profileId)
                if (fallbackData != null) {
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
            val empty = PlayerData(playerName = player.name, uuid = uuid, cid = generateProfileID(uuid))
            playerDataMap[uuid] = empty
            if (Bukkit.isPrimaryThread()) player.inventory.clear()
            else submit { player.inventory.clear() }
            return empty
        }

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
        teleportToProfileLocation(player)
        return merged
    }
}
