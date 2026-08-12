package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.profile.MetaEntry
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager.getSharedData
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.common.platform.service.PlatformExecutor

object MetaManager {

    private var expiryTask: PlatformExecutor.PlatformTask? = null

    // ==================== Shared (跨 profile) ====================

    fun setShared(uuid: String, key: String, value: String, expireAt: Long?) {
        cleanupShared(uuid)
        getSharedData(uuid).meta[key] = MetaEntry(value, expireAt)
    }

    fun removeShared(uuid: String, key: String): Boolean {
        cleanupShared(uuid)
        return getSharedData(uuid).meta.remove(key) != null
    }

    fun getShared(uuid: String, key: String): MetaEntry? {
        cleanupShared(uuid)
        val entry = getSharedData(uuid).meta[key] ?: return null
        if (entry.isExpired) {
            getSharedData(uuid).meta.remove(key)
            return null
        }
        return entry
    }

    fun hasShared(uuid: String, key: String): Boolean {
        return getShared(uuid, key) != null
    }

    fun listShared(uuid: String): Map<String, MetaEntry> {
        cleanupShared(uuid)
        return getSharedData(uuid).meta.filterValues { !it.isExpired }
    }

    private fun cleanupShared(uuid: String) {
        val meta = getSharedData(uuid).meta
        meta.entries.removeAll { it.value.isExpired }
    }

    // ==================== Independent (per-profile) ====================

    fun setIndependent(uuid: String, key: String, value: String, expireAt: Long?) {
        val data = playerDataMap[uuid] ?: return
        cleanupIndependent(uuid)
        data.meta[key] = MetaEntry(value, expireAt)
    }

    fun removeIndependent(uuid: String, key: String): Boolean {
        val data = playerDataMap[uuid] ?: return false
        cleanupIndependent(uuid)
        return data.meta.remove(key) != null
    }

    fun getIndependent(uuid: String, key: String): MetaEntry? {
        val data = playerDataMap[uuid] ?: return null
        cleanupIndependent(uuid)
        val entry = data.meta[key] ?: return null
        if (entry.isExpired) {
            data.meta.remove(key)
            return null
        }
        return entry
    }

    fun hasIndependent(uuid: String, key: String): Boolean {
        return getIndependent(uuid, key) != null
    }

    fun listIndependent(uuid: String): Map<String, MetaEntry> {
        val data = playerDataMap[uuid] ?: return emptyMap()
        cleanupIndependent(uuid)
        return data.meta.filterValues { !it.isExpired }
    }

    private fun cleanupIndependent(uuid: String) {
        val data = playerDataMap[uuid] ?: return
        data.meta.entries.removeAll { it.value.isExpired }
    }

    // ==================== Global Cleanup ====================

    /** Remove expired meta entries from all loaded player data */
    fun cleanExpired() {
        // Clean shared data
        for ((uuid, shared) in ProfileManager.sharedDataMap) {
            val removed = shared.meta.entries.removeAll { it.value.isExpired }
            if (removed) {
                ProfileManager.saveSharedData(uuid)
            }
        }
        // Clean independent data
        for ((_, data) in playerDataMap) {
            data.meta.entries.removeAll { it.value.isExpired }
        }
    }

    @Awake(LifeCycle.ACTIVE)
    fun startExpiryCheck() {
        expiryTask = submit(period = 1200L) {   // every 60 seconds
            try {
                cleanExpired()
            } catch (e: Exception) {
                warning("[MetaManager] Expiry check failed: ${e.message}")
            }
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun cancelExpiryCheck() {
        expiryTask?.cancel()
        expiryTask = null
    }
}
