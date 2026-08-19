package io.github.zzzyyylllty.chotentech.station

import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.shop.CostType
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.compat.replacePlaceholder

object StationMenu : Listener {

    init {
        try {
            org.bukkit.Bukkit.getPluginManager().registerEvents(this, taboolib.platform.BukkitPlugin.getInstance())
        } catch (_: Exception) {}
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        StationSession.remove(event.player)
    }

    private val layout = listOf(
        "CCC   <> ",
        "IIIIIIIII",
        "IIIIIIIII",
        "IIIIIIIII",
        "IIIIIIIII",
        "NSS--RRQQ",
    )

    private fun iSlotIndices(): List<Int> {
        val slots = mutableListOf<Int>()
        for ((row, line) in layout.withIndex()) {
            for ((col, char) in line.withIndex()) {
                if (char == 'I') slots.add(row * 9 + col)
            }
        }
        return slots
    }

    private fun title(station: StationConfig): String =
        "<white><shift:-16><image:kangelicon:station/station_queuq><shift:-176><aqua><font:minecraft:fusionpixel10>${station.name}"

    fun open(player: Player, stationId: String) {
        val station = StationManager.getStation(stationId) ?: run {
            player.sendComponent("<red>合成站 <yellow>$stationId <white><red>不存在")
            return
        }
        val session = StationSession.get(player)
        session.stationId = stationId
        session.view = StationView.RECIPES
        session.currentPage = 0
        openView(player, station, session)
    }

    private fun openView(player: Player, station: StationConfig, session: StationSession) {
        StationSession.put(player, session) // 确保会话已注册(模式切换重开时会话被 onClose 移除过)
        player.openMenu<PageableChest<Any>>(mmJsonUtil.serialize(title(station).toComponent())) {
            rows(6)
            page(session.currentPage)
            onPageChange { p ->
                session.currentPage = page
                // 翻页通过 openInventory(build()) 重建视图, 会触发 onClose 取消刷新任务并移除会话,
                // 这里恢复会话注册并重启实时刷新, 避免翻页后队列倒计时/完成状态不再刷新
                StationSession.put(p, session)
                session.menuOpen = true
                scheduleLiveRefresh(p, station, session)
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            map(*layout.toTypedArray())
            slotsBy('I')

            elements {
                when (session.view) {
                    StationView.RECIPES -> getFilteredRecipes(player, station, session)
                    StationView.QUEUE -> StationManager.getQueue(player, station.id)
                }
            }

            onGenerate { _, item, _, _ ->
                when (session.view) {
                    StationView.RECIPES -> buildRecipeItem(player, item as StationRecipe, station)
                    StationView.QUEUE -> buildQueueItem(player, item as StationQueueItem, station)
                }
            }

            onClick { event, item ->
                event.isCancelled = true
                // 点击被就地刷新为空的槽位时 item 可能为 null, 需安全处理
                if (item == null) return@onClick
                val clicker = event.clicker
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.currentPage = page
                when (session.view) {
                    StationView.RECIPES -> {
                        val recipe = item as? StationRecipe ?: return@onClick
                        if (StationManager.startCrafting(clicker, station, recipe)) {
                            refreshDisplayInPlace(clicker, station, session)
                        }
                    }
                    StationView.QUEUE -> {
                        val entry = item as? StationQueueItem ?: return@onClick
                        if (entry.isComplete) {
                            if (StationManager.claim(clicker, station, entry)) {
                                refreshDisplayInPlace(clicker, station, session)
                            }
                        } else {
                            clicker.sendComponent("<red>该物品仍在制作中, 剩余 ${entry.remainingTime} 秒")
                        }
                    }
                }
            }

            // 左上 3 格关闭
            val closeStack = ItemStack(Material.COMPASS).apply {
                editMeta { m ->
                    m.displayName("<b><aqua>关闭".toComponent())
                    m.lore(listOf("<gray>关闭合成站".toComponent()))
                    m.itemModel = NamespacedKey.fromString("kangelitem:void")
                }
            }
            set('C', closeStack) { clicker.closeInventory() }

            // 信息(45)
            set('N', buildInfoItem(player, station, session)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }

            // 条件筛选(46)
            set(46, buildConditionFilterItem(player, station, session)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.filterReq = cycleFilter(session.filterReq, StationFilterReq.entries.toTypedArray())
                session.currentPage = 0
                page(0) // 同步 PageableChest 内部页码, 避免显示页与实际页脱节
                resetElementsCache()
                refreshDisplayInPlace(clicker, station, session)
            }

            // 物品筛选(47)
            set(47, buildItemFilterItem(player, station, session)) {
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                session.filterItem = cycleFilter(session.filterItem, StationFilterItem.entries.toTypedArray())
                session.currentPage = 0
                page(0) // 同步 PageableChest 内部页码
                resetElementsCache()
                refreshDisplayInPlace(clicker, station, session)
            }

            // 制作列表切换(50,51)
            run {
                val current = session.view == StationView.RECIPES
                val stack = buildToggleItem(current, "制作列表", "查看并选择可制作的配方")
                for (slot in 50..51) {
                    set(slot, stack) {
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        if (current) return@set
                        session.view = StationView.RECIPES
                        session.currentPage = 0
                        clicker.closeInventory()
                        submit(delay = 1) { openView(clicker, station, session) }
                    }
                }
            }

            // 队列切换(52,53)
            run {
                val current = session.view == StationView.QUEUE
                val queue = StationManager.getQueue(player, station.id)
                val maxSlots = StationManager.getMaxQueueSlots(player, station)
                val stack = buildToggleItem(current, "队列", "查看制作队列并领取完成的物品", "${queue.size}/$maxSlots")
                for (slot in 52..53) {
                    set(slot, stack) {
                        clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                        if (current) return@set
                        session.view = StationView.QUEUE
                        session.currentPage = 0
                        clicker.closeInventory()
                        submit(delay = 1) { openView(clicker, station, session) }
                    }
                }
            }

            // 翻页(6,7)
            setPreviousPage(6) { _, hasPrev -> buildPageButton(true, hasPrev) }
            setNextPage(7) { _, hasNext -> buildPageButton(false, hasNext) }

            onClose {
                session.menuOpen = false
                session.refreshTask?.cancel()
                session.refreshTask = null
                // 菜单彻底关闭后清理会话, 避免常驻 Player 引用
                StationSession.remove(player)
            }
        }

        // 打开期间实时刷新:队列视图倒计时 + 完成状态
        session.menuOpen = true
        scheduleLiveRefresh(player, station, session)
    }

    // ── 就地刷新(不重开菜单) ─────────────────────────────────────

    /** 每 20 tick 就地刷新显示区域(队列倒计时 / 完成状态 / 可制作状态)。
     *  翻页会触发 onClose 取消旧任务并移除会话, 故 onPageChange 中会恢复注册并再次调用本方法 */
    private fun scheduleLiveRefresh(player: Player, station: StationConfig, session: StationSession) {
        session.refreshTask?.cancel()
        session.refreshTask = submit(period = 20) {
            val p = player
            if (!p.isOnline) return@submit
            // 直接使用本会话引用, 不依赖 get(p) 的注册状态
            if (!session.menuOpen || session.stationId != station.id) return@submit
            val top = p.openInventory.topInventory
            if (top.size != 54) return@submit
            refreshDisplayInPlace(p, station, session)
        }
    }

    private fun refreshDisplayInPlace(player: Player, station: StationConfig, session: StationSession) {
        val inv = player.openInventory.topInventory
        if (inv.size != 54) return
        val iSlots = iSlotIndices()
        val start = session.currentPage * iSlots.size
        when (session.view) {
            StationView.RECIPES -> {
                val recipes = getFilteredRecipes(player, station, session)
                for ((offset, slot) in iSlots.withIndex()) {
                    val recipe = recipes.getOrNull(start + offset)
                    inv.setItem(slot, if (recipe != null) buildRecipeItem(player, recipe, station) else ItemStack(Material.AIR))
                }
            }
            StationView.QUEUE -> {
                val queue = StationManager.getQueue(player, station.id)
                for ((offset, slot) in iSlots.withIndex()) {
                    val entry = queue.getOrNull(start + offset)
                    inv.setItem(slot, if (entry != null) buildQueueItem(player, entry, station) else ItemStack(Material.AIR))
                }
            }
        }
        refreshDynamicSlots(player, station, session)
    }

    private fun refreshDynamicSlots(player: Player, station: StationConfig, session: StationSession) {
        val inv = player.openInventory.topInventory
        if (inv.size != 54) return
        inv.setItem(45, buildInfoItem(player, station, session))
        inv.setItem(46, buildConditionFilterItem(player, station, session))
        inv.setItem(47, buildItemFilterItem(player, station, session))
        inv.setItem(50, buildToggleItem(session.view == StationView.RECIPES, "制作列表", "查看并选择可制作的配方"))
        inv.setItem(51, buildToggleItem(session.view == StationView.RECIPES, "制作列表", "查看并选择可制作的配方"))
        val queue = StationManager.getQueue(player, station.id)
        val maxSlots = StationManager.getMaxQueueSlots(player, station)
        val queueToggle = buildToggleItem(session.view == StationView.QUEUE, "队列", "查看制作队列并领取完成的物品", "${queue.size}/$maxSlots")
        inv.setItem(52, queueToggle)
        inv.setItem(53, queueToggle)
        // 同步翻页按钮显示(过滤器切换/倒计时刷新不会触发 rebuild)
        inv.setItem(6, buildPageButton(true, session.currentPage > 0))
        val list = currentPageElements(player, station, session)
        inv.setItem(7, buildPageButton(false, hasNextPage(list.size, session.currentPage)))
    }

    // ── 物品构建 ──────────────────────────────────────────────────

    private fun buildPageButton(isPrev: Boolean, enabled: Boolean): ItemStack {
        val (name, model) = when {
            isPrev && enabled -> "<aqua><b>上一页" to "kangelitem:previous"
            isPrev -> "<gray><b>已是第一页" to "kangelitem:previous_no"
            enabled -> "<aqua><b>下一页" to "kangelitem:next"
            else -> "<gray><b>已是最后一页" to "kangelitem:next_no"
        }
        return ItemStack(Material.ARROW).apply {
            editMeta { m ->
                m.displayName(name.toComponent())
                m.itemModel = NamespacedKey.fromString(model)
            }
        }
    }

    private fun buildRecipeItem(player: Player, recipe: StationRecipe, station: StationConfig): ItemStack {
        val item = StationManager.buildOutputItem(player, recipe) ?: ItemStack(Material.BARRIER)
        item.editMeta { m ->
            m.displayName("<gold>制作 ${StationManager.displayName(recipe.output)}".toComponent())
            val queue = StationManager.getQueue(player, station.id)
            val maxSlots = StationManager.getMaxQueueSlots(player, station)
            val craftable = StationManager.canAfford(player, recipe)

            val lore = mutableListOf<Component>()
            lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>".toComponent()
            lore += "<!i><gray>制作时间: <yellow>${recipe.time} 秒".toComponent()
            if (recipe.buy.isNotEmpty()) {
                lore += "<!i> ".toComponent()
                lore += "<!i><gray>材料:".toComponent()
                for (cost in recipe.buy) {
                    when (cost.type) {
                        CostType.CURRENCY -> {
                            val cur = cost.currency ?: "money"
                            val have = CurrencyManager.getBalance(player, cur)
                            val ok = have >= cost.amount
                            val color = if (ok) "<green>" else "<red>"
                            lore += "<!i> ${color}● ${CurrencyManager.displayName(cur)}: <white>$have/$cost.amount".toComponent()
                        }
                        CostType.ITEM -> {
                            val id = cost.item ?: continue
                            val have = StationManager.countItem(player, id)
                            val ok = have >= cost.amount
                            val color = if (ok) "<green>" else "<red>"
                            lore += "<!i> ${color}● ${StationManager.displayName(id)} <white>x$have/$cost.amount".toComponent()
                        }
                    }
                }
            }
            lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>".toComponent()
            lore += "<!i><gray>队列: <white>${queue.size}/$maxSlots".toComponent()
            lore += "<!i> ".toComponent()
            when {
                queue.size >= maxSlots -> lore += "<!i><red>✗ 队列已满, 无法添加".toComponent()
                !craftable -> lore += "<!i><red>✗ 材料不足".toComponent()
                else -> lore += "<!i><green>点击开始制作".toComponent()
            }
            m.lore(lore)
        }
        return item
    }

    private fun buildQueueItem(player: Player, entry: StationQueueItem, station: StationConfig): ItemStack {
        val recipe = station.recipes[entry.recipeId]
        val item = recipe?.let { StationManager.buildOutputItem(player, it) } ?: ItemStack(Material.BARRIER)
        val outputName = recipe?.let { StationManager.displayName(it.output) } ?: entry.recipeId
        item.editMeta { m ->
            if (entry.isComplete) {
                m.displayName("<green><b>✓ $outputName".toComponent())
                m.setEnchantmentGlintOverride(true)
                val lore = mutableListOf<Component>()
                lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>".toComponent()
                lore += "<!i><green>已完成制作".toComponent()
                lore += "<!i><green>点击领取到背包".toComponent()
                m.lore(lore)
            } else {
                m.displayName("<white>$outputName".toComponent())
                val lore = mutableListOf<Component>()
                lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>".toComponent()
                lore += "<!i><gray>制作中...".toComponent()
                lore += "<!i><yellow>剩余 <white>${entry.remainingTime} <yellow>秒".toComponent()
                m.lore(lore)
            }
        }
        return item
    }

    private fun buildInfoItem(player: Player, station: StationConfig, session: StationSession): ItemStack {
        val result = XMaterial.KNOWLEDGE_BOOK.parseItem() ?: ItemStack(Material.KNOWLEDGE_BOOK)
        val meta = result.itemMeta ?: return result
        NamespacedKey.fromString("kangelicon:book")?.let { meta.itemModel = it }
        meta.displayName("<gold><b>信息".toComponent())

        val queue = StationManager.getQueue(player, station.id)
        val maxSlots = StationManager.getMaxQueueSlots(player, station)
        val crafting = queue.count { !it.isComplete }
        val done = queue.size - crafting

        val lore = mutableListOf<String>()
        lore += "<!i><gold>合成站: <white>${station.name}"
        lore += "<!i><gold>队列容量: <white>${queue.size}/$maxSlots"
        lore += "<!i> <gray>制作中 $crafting · 待领取 $done"
        if (station.extraQueues.isNotEmpty()) {
            lore += "<!i> "
            lore += "<!i><gray>额外队列条件:"
            for (eq in station.extraQueues) {
                val desc = StringBuilder()
                if (eq.affection != null) desc.append("好感 ${eq.affection.id} ≥ ${eq.affection.required}")
                if (eq.permission != null) {
                    if (desc.isNotEmpty()) desc.append(" 且 ")
                    desc.append("权限 ${eq.permission}")
                }
                val met = conditionMet(player, eq)
                val color = if (met) "<green>" else "<red>"
                lore += "<!i> ${color}${if (met) "✓" else "✗"} $desc <gray>(+${eq.slots} 槽)"
            }
        }
        lore += "<!i> "
        lore += "<!i><gold>你的货币:"
        lore += "<!i> <aqua>${CurrencyManager.displayName("money")}: <white>${CurrencyManager.getBalance(player, "money")}"
        lore += "<!i> <aqua>${CurrencyManager.displayName("point")}: <white>${CurrencyManager.getBalance(player, "point")}"
        lore += "<!i> <aqua>${CurrencyManager.displayName("tape")}: <white>${CurrencyManager.getBalance(player, "tape")}"
        lore += "<!i> <aqua>${CurrencyManager.displayName("goldentape")}: <white>${CurrencyManager.getBalance(player, "goldentape")}"

        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        result.setItemMeta(meta)
        return result
    }

    private fun buildConditionFilterItem(player: Player, station: StationConfig, session: StationSession): ItemStack {
        val current = session.filterReq
        val result = XMaterial.COMPARATOR.parseItem() ?: ItemStack(Material.COMPARATOR)
        val meta = result.itemMeta ?: return result
        meta.displayName("<white><b>条件筛选".toComponent())

        val recipes = StationManager.getRecipes(station)
        val craftable = recipes.count { StationManager.canAfford(player, it) }
        val lore = mutableListOf<String>()
        StationFilterReq.entries.forEach { f ->
            if (f == current) lore += "<!i><green>▶ <white>${f.displayName}"
            else lore += "<!i><gray>${f.displayName}"
        }
        lore += "<!i> "
        lore += if (craftable > 0) "<!i><green>✓ 有 $craftable 个配方可制作"
        else "<!i><red>✗ 没有配方可制作"

        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        NamespacedKey.fromString(filterReqModel(current))?.let { meta.itemModel = it }
        result.setItemMeta(meta)
        return result
    }

    private fun buildItemFilterItem(player: Player, station: StationConfig, session: StationSession): ItemStack {
        val current = session.filterItem
        val result = XMaterial.HOPPER.parseItem() ?: ItemStack(Material.HOPPER)
        val meta = result.itemMeta ?: return result
        meta.displayName("<white><b>物品筛选".toComponent())

        val recipes = StationManager.getRecipes(station)
        val withMaterials = recipes.count { StationManager.hasMaterials(player, it) }
        val lore = mutableListOf<String>()
        StationFilterItem.entries.forEach { f ->
            if (f == current) lore += "<!i><green>▶ <white>${f.displayName}"
            else lore += "<!i><gray>${f.displayName}"
        }
        lore += "<!i> "
        lore += if (withMaterials > 0) "<!i><green>✓ 有 $withMaterials 个配方材料充足"
        else "<!i><red>✗ 没有配方材料充足"

        meta.lore(lore.map { it.replacePlaceholder(player).toComponent() })
        NamespacedKey.fromString(filterItemModel(current))?.let { meta.itemModel = it }
        result.setItemMeta(meta)
        return result
    }

    private fun buildToggleItem(current: Boolean, name: String, desc: String, extra: String? = null): ItemStack {
        val result = ItemStack(if (current) Material.BOOK else Material.PAPER)
        result.editMeta { m ->
            m.displayName("<white><b>$name".toComponent())
            val lore = mutableListOf<String>()
            lore += "<!i><gray>$desc"
            extra?.let { lore += "<!i><gray>$it" }
            lore += "<!i> "
            lore += if (current) "<!i><green>▶ 当前视图" else "<!i><gray>点击切换"
            m.lore(lore.map { it.toComponent() })
        }
        return result
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private fun getFilteredRecipes(player: Player, station: StationConfig, session: StationSession): List<StationRecipe> {
        return StationManager.getRecipes(station).filter { recipe ->
            when (session.filterReq) {
                StationFilterReq.ALL -> true
                StationFilterReq.MET -> StationManager.canAfford(player, recipe)
                StationFilterReq.NOT_MET -> !StationManager.canAfford(player, recipe)
            }
        }.filter { recipe ->
            when (session.filterItem) {
                StationFilterItem.ALL -> true
                StationFilterItem.HAVE -> StationManager.hasMaterials(player, recipe)
                StationFilterItem.NOT_HAVE -> !StationManager.hasMaterials(player, recipe)
            }
        }
    }

    /** 当前视图下的元素列表(与 PageableChest 的分页基准一致) */
    private fun currentPageElements(player: Player, station: StationConfig, session: StationSession): List<*> {
        return when (session.view) {
            StationView.RECIPES -> getFilteredRecipes(player, station, session)
            StationView.QUEUE -> StationManager.getQueue(player, station.id)
        }
    }

    /** 与 PageableChestImpl.isNext 相同:size / 每页容量 > page + 1 */
    private fun hasNextPage(size: Int, page: Int): Boolean {
        return size / iSlotIndices().size.toDouble() > page + 1
    }

    private fun conditionMet(player: Player, eq: StationExtraQueue): Boolean {
        val affOk = eq.affection == null ||
            ReputationManager.getAffection(player, eq.affection.id) >= eq.affection.required
        val permOk = eq.permission == null || player.hasPermission(eq.permission)
        return affOk && permOk
    }

    private fun <T : Enum<T>> cycleFilter(current: T, values: Array<T>): T {
        val idx = values.indexOf(current)
        return values[(idx + 1) % values.size]
    }

    private fun filterReqModel(v: StationFilterReq): String = "kangelicon:filter_condition_" + when (v) {
        StationFilterReq.ALL -> "all"
        StationFilterReq.MET -> "met"
        StationFilterReq.NOT_MET -> "not_met"
    }

    private fun filterItemModel(v: StationFilterItem): String = "kangelicon:filter_item_" + when (v) {
        StationFilterItem.ALL -> "all"
        StationFilterItem.HAVE -> "have"
        StationFilterItem.NOT_HAVE -> "not_have"
    }
}
