package io.github.zzzyyylllty.chotentech.friend

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class FriendRequest(
    val requester: UUID
)

object FriendManager {

    /** target UUID → set of pending friend requests (in-memory) */
    private val requests = ConcurrentHashMap<UUID, MutableSet<FriendRequest>>()

    // ── Query ──

    /** 好友关系存放在 SharedData（跨 profile 共享，持久化到数据库） */
    fun getFriends(player: UUID): List<UUID> =
        ProfileManager.getSharedData(player.toString()).friends
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    fun isFriend(a: UUID, b: UUID): Boolean =
        b.toString() in ProfileManager.getSharedData(a.toString()).friends

    fun getRequests(target: UUID): List<FriendRequest> =
        requests[target]?.toList() ?: emptyList()

    fun getOnlineFriends(player: UUID): List<Player> =
        getFriends(player).mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

    // ── Friend operations ──

    /** Max friends based on config and chotentech.friend.<n> permission (highest wins). */
    fun getMaxFriends(player: Player): Int {
        val defaultMax = ChoTenTech.config.getInt("friend.max-friends", 50)
        var max = defaultMax
        for (i in 200 downTo 1) {
            if (player.hasPermission("chotentech.friend.$i")) {
                max = i
                break
            }
        }
        return max
    }

    /** Send a friend request. Returns false if invalid (self/duplicate/already friends/full). */
    fun sendRequest(requester: Player, target: Player): Boolean {
        if (requester.uniqueId == target.uniqueId) return false
        if (isFriend(requester.uniqueId, target.uniqueId)) return false
        if (getFriends(requester.uniqueId).size >= getMaxFriends(requester)) return false
        val existing = requests[target.uniqueId]?.any { it.requester == requester.uniqueId } ?: false
        if (existing) return false
        requests.computeIfAbsent(target.uniqueId) { mutableSetOf() }
            .add(FriendRequest(requester.uniqueId))
        return true
    }

    /** Player accepts a friend request from requesterId. Writes both sides to DB. */
    fun accept(target: Player, requesterId: UUID): Boolean {
        val targetRequests = requests[target.uniqueId] ?: return false
        val request = targetRequests.find { it.requester == requesterId } ?: return false
        if (getFriends(target.uniqueId).size >= getMaxFriends(target)) return false
        val requester = Bukkit.getPlayer(requesterId)
        if (requester != null && getFriends(requesterId).size >= getMaxFriends(requester)) return false

        targetRequests.remove(request)

        val targetShared = ProfileManager.getSharedData(target.uniqueId.toString())
        targetShared.friends.add(requesterId.toString())
        ProfileManager.saveSharedData(target.uniqueId.toString())

        val requesterShared = ProfileManager.getSharedData(requesterId.toString())
        requesterShared.friends.add(target.uniqueId.toString())
        ProfileManager.saveSharedData(requesterId.toString())
        return true
    }

    /** Player declines a friend request. */
    fun deny(target: Player, requesterId: UUID): Boolean {
        val targetRequests = requests[target.uniqueId] ?: return false
        return targetRequests.removeAll { it.requester == requesterId }
    }

    /** Remove a friend. Works from either side; cleans up both directions in DB. */
    fun remove(player: Player, targetId: UUID): Boolean {
        val myShared = ProfileManager.getSharedData(player.uniqueId.toString())
        if (targetId.toString() !in myShared.friends) return false
        myShared.friends.remove(targetId.toString())
        ProfileManager.saveSharedData(player.uniqueId.toString())

        // 对方侧仅在有数据时清理，避免为从未建档的玩家创建空行
        val targetKey = targetId.toString()
        if (ProfileManager.sharedDataMap.containsKey(targetKey) ||
            ProfileManager.sharedMapper.findById(targetKey) != null
        ) {
            ProfileManager.getSharedData(targetKey).friends.remove(player.uniqueId.toString())
            ProfileManager.saveSharedData(targetKey)
        }
        return true
    }

    // ── 一次性迁移：friends.yml → 数据库 ──

    @Awake(LifeCycle.ACTIVE)
    fun start() {
        migrateYmlToDb()
    }

    private fun migrateYmlToDb() {
        val file = File(getDataFolder(), "friends.yml")
        if (!file.exists()) return
        try {
            val config = Configuration.loadFromFile(file) ?: return
            for (key in config.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val list = config.getStringList(key).mapNotNull {
                    runCatching { UUID.fromString(it) }.getOrNull()
                }
                if (list.isEmpty()) continue
                val shared = ProfileManager.getSharedData(key)
                if (shared.friends.addAll(list.map { it.toString() })) {
                    ProfileManager.saveSharedData(key)
                }
            }
            file.delete()
            devLog("<gray>[Friend] 已将 friends.yml 迁移到数据库")
        } catch (e: Exception) {
            devLog("<yellow>[Friend] friends.yml 迁移失败: ${e.message}")
        }
    }

    // ── Notifications ──

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Notify online friends that this player logged in
        for (friendId in getFriends(uuid)) {
            val friend = Bukkit.getPlayer(friendId) ?: continue
            friend.sendMessage("<aqua>✦ 你的好友 <yellow>${player.name} <aqua>上线了".toComponent())
        }

        // Remind about pending friend requests
        val pending = requests[uuid]?.size ?: 0
        if (pending > 0) {
            player.sendMessage("<gold>■ <yellow>你有 <white>$pending <yellow>条好友申请，输入 <white>/friend requests <yellow>查看".toComponent())
        }
    }
}
