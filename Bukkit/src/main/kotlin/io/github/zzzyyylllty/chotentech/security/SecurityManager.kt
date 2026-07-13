package io.github.zzzyyylllty.chotentech.security

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.logger.warningS
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.GameMode
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object SecurityManager {

    // ==================== 数据结构 ====================

    data class SecurityGroup(
        val name: String,
        val users: Map<String, List<String>>,
        val safemode: Boolean
    )

    data class CheckPattern(
        val pattern: Pattern,
        val relaxed: Boolean,
        /** 从正则中提取的字面量关键字，用于快速预过滤。
         *  命令不包含任何关键字时跳过此 pattern 的 regex 匹配，大幅减少无谓的 Pattern.matcher() 调用。 */
        val triggerWords: Set<String> = emptySet()
    )

    data class PermissionLiteral(
        val raw: String,
        /** pre-lowercased 版本，避免每次 checkCommand 时重复调 lowercase() */
        val lowered: String,
        val relaxed: Boolean
    )

    data class SecurityCheck(
        val name: String,
        val commandPatterns: List<CheckPattern>,
        val permissionLiterals: List<PermissionLiteral>,
        val miscPatterns: List<CheckPattern>
    )

    data class CheckResult(
        val matched: Boolean,
        val checkName: String,
        val matchContent: String,
        val isRelaxed: Boolean,
        val safemode: Boolean
    )

    // ==================== 状态 ====================

    /** 按配置文件顺序的群组列表 */
    private val groupsOrder = mutableListOf<String>()

    /** groupName -> SecurityGroup */
    private val groups = LinkedHashMap<String, SecurityGroup>()

    /** checkName -> SecurityCheck */
    private val checks = LinkedHashMap<String, SecurityCheck>()

    /** groupName -> resolved checks (含继承解析后的展平结果) */
    private val resolvedGroupChecks = ConcurrentHashMap<String, ResolvedGroup>()

    /** groupName -> raw check name list (缓存，避免每次 resolve 都重读 YAML) */
    private val rawCheckNamesCache = ConcurrentHashMap<String, List<String>>()

    /** playerName -> groupName 缓存 */
    private val playerGroupCache = ConcurrentHashMap<String, String>()

data class ResolvedGroup(
        val safemode: Boolean,
        /** (check, isRelaxed) 列表, isRelaxed 标记该条 check 是否整体宽松 */
        val items: List<ResolvedCheckItem>
    )

    data class ResolvedCheckItem(
        val check: SecurityCheck,
        /** 针对 inheritance 场景: 继承来的 check 是否被视为宽松 */
        val relaxed: Boolean
    ) {
        /** 该 check 内是否有任何非宽松的 command 模式 */
        fun hasStrictCommand(): Boolean = !relaxed && check.commandPatterns.any { !it.relaxed }
        fun hasStrictMisc(): Boolean = !relaxed && check.miscPatterns.any { !it.relaxed }
    }

    // ==================== 加载 ====================

    fun reload() {
        groupsOrder.clear()
        groups.clear()
        checks.clear()
        resolvedGroupChecks.clear()
        rawCheckNamesCache.clear()
        playerGroupCache.clear()

        val file = File(getDataFolder(), "security.yml")
        if (!file.exists()) {
            // 从 JAR 释放默认配置
            try {
                ChoTenTech::class.java.classLoader.getResource("security.yml")?.openStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                devLog("SecurityManager: released default security.yml")
            } catch (e: Exception) {
                warningS("SecurityManager: failed to release security.yml: ${e.message}")
                return
            }
        }

        val config: Configuration
        try {
            config = Configuration.loadFromFile(file)
        } catch (e: Exception) {
            warningS("SecurityManager: failed to load security.yml: ${e.message}")
            return
        }

        // 1. 加载 checks
        val checksSection = config.getConfigurationSection("checks")
        if (checksSection == null) {
            warningS("SecurityManager: security.yml missing 'checks' section")
            return
        }
        loadChecks(checksSection)

        // 2. 加载 groups (按 key 顺序)
        val groupsSection = config.getConfigurationSection("groups")
        if (groupsSection == null) {
            warningS("SecurityManager: security.yml missing 'groups' section")
            return
        }
        loadGroups(groupsSection)

        // 3. 预解析所有群组的继承链
        for (groupName in groupsOrder) {
            resolveGroup(groupName, mutableSetOf())
        }

        devLog("SecurityManager: loaded ${groups.size} groups, ${checks.size} checks")
    }

    private fun loadChecks(section: ConfigurationSection) {
        for (checkName in section.getKeys(false)) {
            val checkSection = section.getConfigurationSection(checkName) ?: continue
            try {
                val commandPatterns = mutableListOf<CheckPattern>()
                val permissionLiterals = mutableListOf<PermissionLiteral>()
                val miscPatterns = mutableListOf<CheckPattern>()

                // commands 段
                val cmdList = checkSection.getStringList("commands")
                for (raw in cmdList) {
                    val (patternStr, relaxed) = parseRelaxed(raw)
                    try {
                        val p = compileCommandPattern(patternStr)
                        val triggerWords = extractTriggerWords(patternStr)
                        commandPatterns.add(CheckPattern(p, relaxed, triggerWords))
                    } catch (e: PatternSyntaxException) {
                        warningS("SecurityManager: check '$checkName' has invalid command pattern '$patternStr': ${e.message}")
                    }
                }

                // permissions 段 — 使用字面量包含匹配而非正则
                val permList = checkSection.getStringList("permissions")
                for (raw in permList) {
                    val (literal, relaxed) = parseRelaxed(raw)
                    permissionLiterals.add(PermissionLiteral(literal, literal.lowercase(), relaxed))
                }

                // misc 段
                val miscList = checkSection.getStringList("misc")
                for (raw in miscList) {
                    val (patternStr, relaxed) = parseRelaxed(raw)
                    try {
                        val p = Pattern.compile(Pattern.quote(patternStr), Pattern.CASE_INSENSITIVE)
                        miscPatterns.add(CheckPattern(p, relaxed, emptySet()))
                    } catch (e: PatternSyntaxException) {
                        warningS("SecurityManager: check '$checkName' has invalid misc pattern '$patternStr': ${e.message}")
                    }
                }

                checks[checkName] = SecurityCheck(
                    name = checkName,
                    commandPatterns = commandPatterns,
                    permissionLiterals = permissionLiterals,
                    miscPatterns = miscPatterns
                )
            } catch (e: Exception) {
                warningS("SecurityManager: failed to load check '$checkName': ${e.message}")
            }
        }
    }

    /**
     * 编译命令模式:
     * - 移除前导 `/`（因为命令已归一化去除了前导 /）
     * - 强制锚定到开头，防止部分匹配绕过（如 `op .+` 误匹配 `stop test`）
     */
    private fun compileCommandPattern(raw: String): Pattern {
        val stripped = if (raw.startsWith('/')) raw.substring(1) else raw
        return Pattern.compile("^$stripped.*", Pattern.CASE_INSENSITIVE)
    }

    /**
     * 从命令模式字符串中提取字面量关键字，用于快速预过滤。
     * 只提取 ≥2 个字母的连续字母序列，跳过正则元字符。
     * 例: `(lp|luckperms) user .+ parent .+ core.+` → {lp, luckperms, user, parent, core}
     *     `op .+` → {op}
     *     `/(cut|set)` → {cut, set}
     */
    private fun extractTriggerWords(patternStr: String): Set<String> {
        val words = mutableSetOf<String>()
        val buf = StringBuilder()
        for (c in patternStr) {
            if (c.isLetter()) {
                buf.append(c.lowercase())
            } else {
                if (buf.length >= 2) words.add(buf.toString())
                buf.clear()
            }
        }
        if (buf.length >= 2) words.add(buf.toString())
        return words
    }

    /**
     * 解析 `_` 前缀: `_xxx` → (xxx, true), `xxx` → (xxx, false)
     */
    private fun parseRelaxed(input: String): Pair<String, Boolean> {
        return if (input.startsWith('_') && input.length > 1) {
            input.substring(1) to true
        } else {
            input to false
        }
    }

    private fun loadGroups(section: ConfigurationSection) {
        for (groupName in section.getKeys(false)) {
            val groupSection = section.getConfigurationSection(groupName) ?: continue
            try {
                val safemode = if (groupSection.contains("safemode")) {
                    groupSection.getBoolean("safemode")
                } else {
                    true // 默认 true
                }

                val users = LinkedHashMap<String, List<String>>()
                val usersSection = groupSection.getConfigurationSection("users")
                if (usersSection != null) {
                    for (playerName in usersSection.getKeys(false)) {
                        val playerSection = usersSection.getConfigurationSection(playerName)
                        val accounts = if (playerSection != null && playerSection.contains("accounts")) {
                            playerSection.getStringList("accounts")
                        } else {
                            emptyList()
                        }
                        users[playerName] = accounts
                    }
                }

                // 缓存原始 check 列表，避免后续 resolve 时重读 YAML
                val rawChecks = groupSection.getStringList("checks")
                rawCheckNamesCache[groupName] = rawChecks

                groupsOrder.add(groupName)
                groups[groupName] = SecurityGroup(
                    name = groupName,
                    users = users,
                    safemode = safemode
                )
            } catch (e: Exception) {
                warningS("SecurityManager: failed to load group '$groupName': ${e.message}")
            }
        }
    }

    /**
     * 递归解析群组的 check 列表（处理 `_inherited` 继承）
     */
    private fun resolveGroup(groupName: String, visiting: MutableSet<String>): ResolvedGroup {
        resolvedGroupChecks[groupName]?.let { return it }

        val group = groups[groupName] ?: return ResolvedGroup(safemode = true, items = emptyList())

        // 防止循环继承
        if (groupName in visiting) {
            warningS("SecurityManager: circular group inheritance detected involving '$groupName'")
            return ResolvedGroup(safemode = group.safemode, items = emptyList())
        }
        visiting.add(groupName)

        // 解析 group 的原始 checks 列表（需从 config 读取原始列表）
        val rawCheckNames = getRawCheckNames(groupName)
        val items = mutableListOf<ResolvedCheckItem>()

        for (raw in rawCheckNames) {
            if (raw.startsWith('_') && raw.length > 1) {
                // 继承另一个群组
                val inheritedGroupName = raw.substring(1)
                val inherited = resolveGroup(inheritedGroupName, visiting)
                // 继承来的 check 标记为 relaxed
                for (item in inherited.items) {
                    items.add(item.copy(relaxed = true))
                }
            } else {
                // 直接引用 check
                val check = checks[raw]
                if (check != null) {
                    items.add(ResolvedCheckItem(check, relaxed = false))
                } else {
                    // 尝试作为正则匹配 check 名称
                    try {
                        val regex = Pattern.compile(raw, Pattern.CASE_INSENSITIVE)
                        var matched = false
                        for ((name, check) in checks) {
                            if (regex.matcher(name).matches()) {
                                items.add(ResolvedCheckItem(check, relaxed = false))
                                matched = true
                            }
                        }
                        if (!matched) {
                            warningS("SecurityManager: group '$groupName' references unknown check '$raw'")
                        }
                    } catch (e: PatternSyntaxException) {
                        warningS("SecurityManager: group '$groupName' references unknown check '$raw'")
                    }
                }
            }
        }

        val resolved = ResolvedGroup(safemode = group.safemode, items = items)
        resolvedGroupChecks[groupName] = resolved
        visiting.remove(groupName)
        return resolved
    }

    /** 从缓存获取群组的原始 check 列表 */
    private fun getRawCheckNames(groupName: String): List<String> {
        return rawCheckNamesCache[groupName] ?: emptyList()
    }

    // ==================== 查询 ====================

    /**
     * 获取玩家所属的第一个群组（从上到下）
     */
    fun getPlayerGroup(player: Player): SecurityGroup? {
        val playerName = player.name
        // 查缓存
        playerGroupCache[playerName]?.let { cachedGroupName ->
            return groups[cachedGroupName]
        }

        // 从上到下遍历
        for (groupName in groupsOrder) {
            val group = groups[groupName] ?: continue
            if (group.users.isEmpty()) continue

            // 检查玩家名是否在群组用户列表中
            for ((entryName, accounts) in group.users) {
                if (playerName.equals(entryName, ignoreCase = true)) {
                    playerGroupCache[playerName] = groupName
                    return group
                }
                // 检查别名
                for (account in accounts) {
                    if (playerName.equals(account, ignoreCase = true)) {
                        playerGroupCache[playerName] = groupName
                        return group
                    }
                }
            }
        }

        // 无匹配
        playerGroupCache[playerName] = "__none__"
        return null
    }

    fun getResolvedGroup(player: Player): ResolvedGroup? {
        val group = getPlayerGroup(player) ?: return null
        return resolvedGroupChecks[group.name]
    }

    /**
     * 检查命令是否违反安全策略
     * @param player 执行者
     * @param command 已归一化的命令（无前导 /，空格已合并）
     * @return 匹配结果，null 表示未触发任何检查
     */
    fun checkCommand(player: Player, command: String): CheckResult? {
        val resolved = getResolvedGroup(player) ?: return null

        // 小写化一次供后续所有关键字匹配复用
        val lowered = command.lowercase()

        for (item in resolved.items) {
            val check = item.check

            // 检查 command 模式（带 trigger word 快速预过滤）
            for (cp in check.commandPatterns) {
                // 快速拒绝：命令不包含任何 trigger word 时跳过 regex 匹配
                if (cp.triggerWords.isNotEmpty() && cp.triggerWords.none { lowered.contains(it) }) {
                    continue
                }
                if (cp.pattern.matcher(command).matches()) {
                    return CheckResult(
                        matched = true,
                        checkName = check.name,
                        matchContent = "command:$command",
                        isRelaxed = item.relaxed || cp.relaxed,
                        safemode = resolved.safemode
                    )
                }
            }

            // 检查 permission 字面量（命令中是否包含该权限字串，如 `*`, `luckperms.autoop`）
            for (pl in check.permissionLiterals) {
                if (lowered.contains(pl.lowered)) {
                    return CheckResult(
                        matched = true,
                        checkName = check.name,
                        matchContent = "permission:${pl.raw}",
                        isRelaxed = item.relaxed || pl.relaxed,
                        safemode = resolved.safemode
                    )
                }
            }
        }

        return null
    }

    /**
     * 检查游戏模式变更
     */
    fun checkGameMode(player: Player, newGameMode: GameMode): CheckResult? {
        val resolved = getResolvedGroup(player) ?: return null
        val gameModeName = newGameMode.name.lowercase()

        for (item in resolved.items) {
            val check = item.check
            for (mp in check.miscPatterns) {
                if (mp.pattern.matcher(gameModeName).matches()) {
                    return CheckResult(
                        matched = true,
                        checkName = check.name,
                        matchContent = "gamemode:$gameModeName",
                        isRelaxed = item.relaxed || mp.relaxed,
                        safemode = resolved.safemode
                    )
                }
            }
        }

        return null
    }

    /**
     * 检查 OP 变更 (由命令处理器调用)
     */
    fun checkOp(player: Player): CheckResult? {
        val resolved = getResolvedGroup(player) ?: return null

        for (item in resolved.items) {
            val check = item.check
            for (mp in check.miscPatterns) {
                if (mp.pattern.matcher("op").matches()) {
                    return CheckResult(
                        matched = true,
                        checkName = check.name,
                        matchContent = "op",
                        isRelaxed = item.relaxed || mp.relaxed,
                        safemode = resolved.safemode
                    )
                }
            }
        }

        return null
    }

    // ==================== 缓存管理 ====================

    fun invalidatePlayerCache(playerName: String) {
        playerGroupCache.remove(playerName)
    }

    fun invalidateAllCache() {
        playerGroupCache.clear()
    }
}
