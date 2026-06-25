package io.github.zzzyyylllty.chotentech.team

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Team(
    var leader: UUID,
    val members: MutableSet<UUID>
) {
    val allMembers: Set<UUID>
        get() = members + leader

    fun isLeader(uuid: UUID) = leader == uuid
    fun isMember(uuid: UUID) = uuid in members
    fun isInTeam(uuid: UUID) = isLeader(uuid) || isMember(uuid)

    /** Online players in the team (includes leader) */
    fun onlinePlayers(): List<Player> = allMembers.mapNotNull { Bukkit.getPlayer(it) }
}

object TeamManager {

    /** player UUID → Team lookup (both leader and members are mapped) */
    private val teams = ConcurrentHashMap<UUID, Team>()

    /** target UUID → set of inviter UUIDs */
    private val invites = ConcurrentHashMap<UUID, MutableSet<Invite>>()

    data class Invite(
        val inviter: UUID,
        val teamLeader: UUID,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > 60_000
    }

    // ── Query ──

    fun getTeam(player: UUID): Team? = teams[player]

    fun isInTeam(player: UUID): Boolean = teams.containsKey(player)

    fun getInvites(target: UUID): List<Invite> =
        invites[target]?.filter { !it.isExpired() }?.toList() ?: emptyList()

    // ── Team operations ──

    fun createTeam(leader: Player): Team {
        val team = Team(leader.uniqueId, mutableSetOf())
        teams[leader.uniqueId] = team
        return team
    }

    /** Leader invites a player. Returns false if leader is not in a team or not the leader. */
    fun invite(inviter: Player, target: Player): Boolean {
        val team = teams[inviter.uniqueId] ?: return false
        if (team.leader != inviter.uniqueId) return false
        if (target.uniqueId in team.allMembers) return false
        invites.computeIfAbsent(target.uniqueId) { mutableSetOf() }
            .add(Invite(inviter.uniqueId, team.leader))
        return true
    }

    /** Player accepts an invite from inviter. */
    fun accept(player: Player, inviterId: UUID): Boolean {
        val inviterInvites = invites[player.uniqueId] ?: return false
        val invite = inviterInvites.find { it.inviter == inviterId && !it.isExpired() } ?: return false
        val team = teams[inviterId] ?: return false

        // Check team max size (based on leader's permissions)
        val leaderPlayer = Bukkit.getPlayer(team.leader)
        val maxSize = if (leaderPlayer != null) getMaxTeamSize(leaderPlayer) else ChoTenTech.config.getInt("team.default-size", 4)
        if (team.allMembers.size >= maxSize) return false

        inviterInvites.remove(invite)
        team.members.add(player.uniqueId)
        teams[player.uniqueId] = team
        return true
    }

    /** Player declines an invite. */
    fun deny(player: Player, inviterId: UUID): Boolean {
        val inviterInvites = invites[player.uniqueId] ?: return false
        return inviterInvites.removeAll { it.inviter == inviterId }
    }

    /** Player leaves their team. If leader tries to leave, disband the team instead. */
    fun leave(player: Player): Boolean {
        val team = teams[player.uniqueId] ?: return false
        if (team.leader == player.uniqueId) {
            return disband(player)
        }
        team.members.remove(player.uniqueId)
        teams.remove(player.uniqueId)
        return true
    }

    /** Leader kicks a member. */
    fun kick(leader: Player, target: Player): Boolean {
        val team = teams[leader.uniqueId] ?: return false
        if (team.leader != leader.uniqueId) return false
        if (target.uniqueId !in team.members) return false
        team.members.remove(target.uniqueId)
        teams.remove(target.uniqueId)
        return true
    }

    /** Leader transfers leadership to a member. */
    fun transfer(leader: Player, target: Player): Boolean {
        val team = teams[leader.uniqueId] ?: return false
        if (team.leader != leader.uniqueId) return false
        if (target.uniqueId !in team.members) return false
        team.members.remove(target.uniqueId)
        team.members.add(leader.uniqueId)
        team.leader = target.uniqueId
        teams[target.uniqueId] = team
        // leader stays in lookup (now as member)
        return true
    }

    /** Leader disbands the team. */
    fun disband(leader: Player): Boolean {
        val team = teams[leader.uniqueId] ?: return false
        if (team.leader != leader.uniqueId) return false
        for (uuid in team.allMembers) {
            teams.remove(uuid)
        }
        return true
    }

    /** Auto-disband when the leader quits. */
    fun handleLeaderQuit(leaderId: UUID) {
        val team = teams[leaderId] ?: return
        if (team.leader != leaderId) return
        val previousMembers = team.allMembers.toList()
        for (uuid in previousMembers) {
            teams.remove(uuid)
        }
        // Notify online members
        for (uuid in previousMembers) {
            val p = Bukkit.getPlayer(uuid) ?: continue
            if (p.isOnline) {
                p.sendMessage("<red>队长已离开服务器，队伍已自动解散".toComponent())
            }
        }
    }

    /** Clean up expired invites periodically */
    fun cleanupExpiredInvites() {
        val now = System.currentTimeMillis()
        invites.values.forEach { set ->
            set.removeAll { now - it.createdAt > 60_000 }
        }
    }

    // ── Config-based max size ──

    /**
     * Get the maximum team size for the given player, based on their permissions.
     * Permission nodes like [chotentech.team.size.6/8/10] grant larger limits.
     */
    fun getMaxTeamSize(player: Player): Int {
        val defaultSize = ChoTenTech.config.getInt("team.default-size", 4)
        val permSection = ChoTenTech.config.getConfigurationSection("team.permission-sizes")
            ?: return defaultSize
        // Iterate from largest to smallest (keys are ordered in config)
        val max = permSection.getKeys(false).mapNotNull { key ->
            val size = permSection.getInt(key, 0)
            if (size > 0 && player.hasPermission(key)) size else null
        }.maxOrNull()
        return max ?: defaultSize
    }
}
