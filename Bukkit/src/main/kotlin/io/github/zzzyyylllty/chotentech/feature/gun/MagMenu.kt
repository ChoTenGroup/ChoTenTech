package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Locale
import kotlin.math.roundToInt

/** 弹匣菜单的交互模式。 */
enum class MagMenuMode {
    /** 普通浏览。 */
    NONE,

    /** 压弹模式：点击自己背包里的子弹 → 单发压入。 */
    INSERT,

    /** 退弹模式：点击自己背包任意处 → 退出一发。 */
    REMOVE,
}

/**
 * 弹匣菜单的 Holder：除了标记身份，还持有**本界面**的压弹 / 退弹模式。
 *
 * 模式挂在 holder 上（而不是挂在玩家身上）的好处：界面一关，holder 就没人引用了，
 * 模式不可能「关掉菜单还残留」。
 */
class MagMenuHolder : CTGunMenuHolder {

    private var backing: Inventory? = null

    var mode: MagMenuMode = MagMenuMode.NONE

    /** 进入模式时的位置（移动超过 `magazine.interrupt-distance` 就退出模式）。 */
    var modeStart: Location? = null

    /** 打开菜单时绑定具体物品内容，防止同槽换成同 ID 弹匣后继续操作。 */
    var boundItem: ItemStack? = null

    fun attach(inventory: Inventory) {
        backing = inventory
    }

    override fun getInventory(): Inventory = backing
        ?: throw IllegalStateException("MagMenuHolder 尚未绑定背包，说明有人在 attach 之前读取了它")
}

/**
 * 弹匣操作菜单（箱子版，27 格 = 3 行，固定布局）。
 *
 * **Paper 1.21.6+ 默认走 [MagDialog] 的原生对话框版**，这里是回退实现
 * （老服务端、或对话框构建抛错时才会被用到）。两版的按钮语义一致，
 * 差别只在压弹 / 退弹：对话框版是按钮，这里是「进入模式 → 点背包子弹」。
 *
 * | 槽 | 内容 |
 * |---|---|
 * | 0 | 预览（口径 / 当前/容量 / 弹种最多 5 条 / 是否允许快速换弹） |
 * | 2 | 检视 |
 * | 4 | 压入子弹（进入压弹模式，**不关菜单**） |
 * | 6 | 弹出子弹（进入退弹模式，**不关菜单**） |
 * | 18 | 状态行 |
 * | 20 | 批量压入 |
 * | 24 | 批量退出（异色） |
 * | 26 | 关闭 |
 */
object MagMenu {

    const val SIZE = 27

    const val SLOT_PREVIEW = 0
    const val SLOT_INSPECT = 2
    const val SLOT_INSERT = 4
    const val SLOT_REMOVE = 6
    const val SLOT_STATUS = 18
    const val SLOT_BULK_INSERT = 20
    const val SLOT_BULK_REMOVE = 24
    const val SLOT_CLOSE = 26

    // ==================== 打开 / 渲染 ====================

    /**
     * 打开弹匣菜单。
     *
     * 优先用**原生对话框**（Paper 1.21.6+）—— 不开背包、不打断读条、按钮带 tooltip，
     * 与枪械菜单（[GunMenu]）同一套体验；不支持时自动回退到下面的箱子界面。
     *
     * 两条路径的交互模型不同：对话框版把「点背包子弹」的压弹 / 退弹模式改成了
     * **按钮**（压入一发 / 一键压满 / 退出一发 / 一键退空），
     * 因为对话框开着时玩家没法点自己的背包。箱子版保留原模式交互。
     */
    fun open(player: Player) {
        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held)
        if (mag == null) {
            menuActionBar(player, "<#E08A98>主手不是弹匣，无法打开弹匣操作")
            return
        }
        if (MagDialog.open(player)) return
        openChest(player, held)
    }

    /** 箱子版菜单（老服务端的回退路径，也是对话框不可用时的唯一实现）。 */
    private fun openChest(player: Player, held: ItemStack) {
        val holder = MagMenuHolder()
        holder.boundItem = held.clone()
        val inventory = Bukkit.createInventory(
            holder,
            SIZE,
            mmUtil.deserialize("<gold>弹匣操作</gold>"),
        )
        holder.attach(inventory)
        render(player, inventory)
        player.openInventory(inventory)
        GunMenuListener.onMenuOpened(player, inventory)
    }

    fun render(player: Player, inventory: Inventory) {
        fillBackground(inventory)
        val holder = inventory.holder as? MagMenuHolder
        val mode = holder?.mode ?: MagMenuMode.NONE

        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held)
        if (mag == null || (holder?.boundItem?.isSimilar(held) == false)) {
            try {
                player.closeInventory()
            } catch (_: Exception) {
            }
            return
        }
        val ammo = MagazineCore.readAmmo(held)
        val capacity = mag.capacity.coerceAtLeast(0)
        val ratio = MagazineCore.percent(ammo, capacity)
        val matchingAmmo = GunAction.firstMatchingAmmo(player, mag)
        val matchingCount = if (matchingAmmo == null) 0 else GunAction.countAmmo(player, matchingAmmo)
        val notFull = ammo.size < capacity
        val canInsert = matchingAmmo != null && matchingCount > 0 && notFull
        val hasAmmo = ammo.isNotEmpty()

        // ---------- 0 预览 ----------
        inventory.setItem(SLOT_PREVIEW, previewItem(held, mag, ammo, capacity))

        // ---------- 2 检视 ----------
        inventory.setItem(
            SLOT_INSPECT,
            button(
                Material.SPYGLASS,
                "检视弹匣",
                true,
                listOf(
                    "<#A9B4C3>查看装填情况（读数可能有一档误差）",
                    "<#A9B4C3>检视耗时 <#EFDF8F>${num(if (mag.ammoInspectionTime > 0) mag.ammoInspectionTime else 2.0)}s",
                ),
                "",
            ),
        )

        // ---------- 4 压入子弹 ----------
        inventory.setItem(
            SLOT_INSERT,
            if (mode == MagMenuMode.INSERT) {
                menuItem(
                    Material.ARROW,
                    "<#8FD4A6>压弹模式（进行中）",
                    listOf(
                        "<#A9B4C3>点击背包里的子弹即可压入一发",
                        "<#A9B4C3>再次点击本按钮退出模式",
                        "<#6E7A8C>移动超过 ${num(GunSettings.magazine.interruptDistance)} 格会退出模式",
                    ),
                )
            } else {
                button(
                    Material.ARROW,
                    "压入子弹",
                    canInsert,
                    listOf(
                        "<#A9B4C3>进入压弹模式，点击背包里的子弹逐发压入",
                        "<#A9B4C3>匹配子弹：<#EFDF8F>${matchingAmmo ?: "无"} ×$matchingCount",
                    ),
                    when {
                        !notFull -> "弹匣已满"
                        else -> "背包里没有匹配 ${mag.caliber} 的子弹"
                    },
                )
            },
        )

        // ---------- 6 弹出子弹 ----------
        inventory.setItem(
            SLOT_REMOVE,
            if (mode == MagMenuMode.REMOVE) {
                menuItem(
                    Material.HOPPER,
                    "<#8FD4A6>退弹模式（进行中）",
                    listOf(
                        "<#A9B4C3>点击背包任意格即可退出一发到背包",
                        "<#A9B4C3>再次点击本按钮退出模式",
                        "<#6E7A8C>移动超过 ${num(GunSettings.magazine.interruptDistance)} 格会退出模式",
                    ),
                )
            } else {
                button(
                    Material.HOPPER,
                    "弹出子弹",
                    hasAmmo,
                    listOf(
                        "<#A9B4C3>进入退弹模式，点击背包逐发退出",
                        "<#A9B4C3>当前弹匣：<#EFDF8F>${ammo.size}/$capacity",
                    ),
                    "弹匣里没有子弹",
                )
            },
        )

        // ---------- 18 状态行 ----------
        inventory.setItem(
            SLOT_STATUS,
            menuItem(
                Material.PAPER,
                "<gold>状态</gold>",
                buildList {
                    add("<#A9B4C3>装填 <#F2F5FA>${ammo.size}<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity" +
                        "</#F2F5FA> <#6E7A8C>(${(ratio * 100.0).roundToInt()}%)")
                    add("<#A9B4C3>档位 ${MagazineCore.fillText(ratio)}")
                    add("<#A9B4C3>匹配子弹 <#EFDF8F>${matchingAmmo ?: "无"} <#A9B4C3>×<#EFDF8F>$matchingCount")
                    add(
                        "<#A9B4C3>快速换弹 " +
                            if (mag.allowFastReload) "<#8FD4A6>允许" else "<#E08A98>不允许"
                    )
                    val snapshot = GunAction.snapshot(player)
                    if (snapshot != null) {
                        add("<#8FD4A6>进行中：${snapshot.type.label} ${snapshot.percent}%")
                    }
                    add(
                        "<#A9B4C3>模式 " + when (mode) {
                            MagMenuMode.INSERT -> "<#8FD4A6>压弹"
                            MagMenuMode.REMOVE -> "<#8FD4A6>退弹"
                            MagMenuMode.NONE -> "<#6E7A8C>浏览"
                        }
                    )
                },
            ),
        )

        // ---------- 20 批量压入 ----------
        inventory.setItem(
            SLOT_BULK_INSERT,
            button(
                Material.LADDER,
                "批量压入",
                canInsert,
                listOf(
                    "<#A9B4C3>连续压入，直到弹匣满或子弹用尽",
                    "<#A9B4C3>每一步都可被移动 / 关菜单打断",
                ),
                if (!notFull) "弹匣已满" else "背包里没有匹配 ${mag.caliber} 的子弹",
            ),
        )

        // ---------- 24 批量退出（异色） ----------
        inventory.setItem(
            SLOT_BULK_REMOVE,
            if (hasAmmo) {
                menuItem(
                    Material.HOPPER,
                    "<#DFBD7C>批量退出</#DFBD7C>",
                    listOf(
                        "<#A9B4C3>连续退出，直到弹匣空",
                        "<#A9B4C3>每一步都可被移动 / 关菜单打断",
                        "<#6E7A8C>点击执行",
                    ),
                )
            } else {
                button(Material.HOPPER, "批量退出", false, emptyList(), "弹匣里没有子弹")
            },
        )

        // ---------- 26 关闭 ----------
        inventory.setItem(
            SLOT_CLOSE,
            menuItem(Material.BARRIER, "<#E08A98>关闭", listOf("<#A9B4C3>关闭这个界面")),
        )
    }

    // ==================== 点击派发 ====================

    /** 菜单内按钮点击。 */
    fun handleClick(player: Player, holder: MagMenuHolder, slot: Int) {
        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held)
        if (mag == null || holder.boundItem?.isSimilar(held) == false) {
            player.closeInventory()
            return
        }
        when (slot) {
            SLOT_CLOSE -> player.closeInventory()

            SLOT_INSPECT -> closeThen(player) {
                GunAction.inspectMagItem(player, player.inventory.itemInMainHand)
            }

            SLOT_INSERT -> toggleMode(player, holder, MagMenuMode.INSERT)

            SLOT_REMOVE -> toggleMode(player, holder, MagMenuMode.REMOVE)

            SLOT_BULK_INSERT -> {
                exitMode(holder)
                GunAction.bulkInsert(player, player.inventory.itemInMainHand)
                refresh(player, holder)
            }

            SLOT_BULK_REMOVE -> {
                exitMode(holder)
                GunAction.bulkRemove(player, player.inventory.itemInMainHand)
                refresh(player, holder)
            }

            else -> Unit
        }
    }

    /**
     * 背包区域点击：只有压弹 / 退弹模式放行，其余一律取消（取消动作在监听器里）。
     *
     * 压弹模式要求点到**匹配口径的子弹**，退弹模式点背包任意格即可（退出来的那发
     * 会自己找背包空位，不需要指定目标）。
     */
    fun handleInventoryClick(player: Player, holder: MagMenuHolder, event: InventoryClickEvent) {
        if (holder.mode == MagMenuMode.NONE) return
        if (!checkMode(player, holder)) return

        val clicked = event.clickedInventory ?: return
        if (clicked !== player.inventory) return

        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held)
        if (mag == null || holder.boundItem?.isSimilar(held) == false) {
            exitMode(holder)
            return
        }

        when (holder.mode) {
            MagMenuMode.INSERT -> {
                val stack = event.currentItem ?: return
                val ammo = GunRegistry.ammoOf(stack) ?: return
                if (!ammo.caliber.equals(mag.caliber, ignoreCase = true)) return
                GunAction.insertRound(player, held, ammo.id)
            }

            MagMenuMode.REMOVE -> GunAction.removeRound(player, held)

            MagMenuMode.NONE -> Unit
        }
    }

    /**
     * 校验模式是否还该继续：移动超过 `magazine.interrupt-distance` 就退出模式。
     *
     * @return false 表示刚退出了模式
     */
    fun checkMode(player: Player, holder: MagMenuHolder): Boolean {
        if (holder.mode == MagMenuMode.NONE) return true
        val start = holder.modeStart
        if (start == null) {
            holder.modeStart = player.location.clone()
            return true
        }
        val limit = GunSettings.magazine.interruptDistance.coerceAtLeast(0.0)
        val now = player.location
        val tooFar = if (now.world != start.world) {
            true
        } else {
            try {
                // 只算水平位移：原地起跳 / 上下台阶不该算「走开」（与 GunAction.movedTooFar 同源）。
                val dx = now.x - start.x
                val dz = now.z - start.z
                limit > 0.0 && dx * dx + dz * dz > limit * limit
            } catch (_: Exception) {
                false
            }
        }
        if (!tooFar) return true
        exitMode(holder)
        menuActionBar(player, "<#E08A98>移动过远，已退出压弹 / 退弹模式")
        return false
    }

    /** 结束压弹 / 退弹模式。 */
    fun exitMode(holder: MagMenuHolder) {
        holder.mode = MagMenuMode.NONE
        holder.modeStart = null
    }

    // ==================== 内部 ====================

    private fun toggleMode(player: Player, holder: MagMenuHolder, mode: MagMenuMode) {
        val held = player.inventory.itemInMainHand
        val mag = GunRegistry.magOf(held) ?: return
        if (holder.boundItem?.isSimilar(held) == false) return
        if (holder.mode == mode) {
            exitMode(holder)
            val label = if (mode == MagMenuMode.INSERT) "压弹" else "退弹"
            menuActionBar(player, "<#A9B4C3>已退出${label}模式")
            refresh(player, holder)
            return
        }
        val ammo = MagazineCore.readAmmo(held)
        when (mode) {
            MagMenuMode.INSERT -> {
                val matching = GunAction.firstMatchingAmmo(player, mag)
                if (matching == null || GunAction.countAmmo(player, matching) <= 0) {
                    menuActionBar(player, "<#E08A98>背包里没有匹配 ${mag.caliber} 的子弹")
                    return
                }
                if (ammo.size >= mag.capacity.coerceAtLeast(0)) {
                    menuActionBar(player, "<#DFBD7C>弹匣已满")
                    return
                }
                menuActionBar(player, "<#8FD4A6>压弹模式：点击背包里的子弹压入一发")
            }

            MagMenuMode.REMOVE -> {
                if (ammo.isEmpty()) {
                    menuActionBar(player, "<#DFBD7C>弹匣已经空了")
                    return
                }
                menuActionBar(player, "<#8FD4A6>退弹模式：点击背包任意格退出一发")
            }

            MagMenuMode.NONE -> return
        }
        holder.mode = mode
        holder.modeStart = player.location.clone()
        refresh(player, holder)
    }

    private fun refresh(player: Player, holder: MagMenuHolder) {
        try {
            render(player, holder.getInventory())
        } catch (_: Exception) {
        }
    }

    private fun fillBackground(inventory: Inventory) {
        val filler = menuItem(Material.GRAY_STAINED_GLASS_PANE, "<#2A3038> </#2A3038>")
        for (slot in 0 until SIZE) {
            inventory.setItem(slot, filler)
        }
    }

    private fun previewItem(
        held: ItemStack,
        mag: MagData,
        ammo: List<String>,
        capacity: Int,
    ): ItemStack {
        val preview = held.clone()
        val meta = preview.itemMeta ?: return preview
        val existing = meta.lore() ?: emptyList()
        val ratio = MagazineCore.percent(ammo, capacity)
        val lines = mutableListOf<String>()
        lines += "<#6E7A8C>─────────────────────"
        lines += "<#A9B4C3>口径 <#EFDF8F>${mag.caliber}"
        lines += "<#A9B4C3>装填 <#F2F5FA>${ammo.size}<#6E7A8C>/</#6E7A8C><#F2F5FA>$capacity" +
            "</#F2F5FA> <#6E7A8C>(${(ratio * 100.0).roundToInt()}%)"
        lines += "<#A9B4C3>档位 ${MagazineCore.fillText(ratio)}"
        if (ammo.isEmpty()) {
            lines += "<#A9B4C3>弹种 <#6E7A8C>无"
        } else {
            val kinds = ammo.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }
                .take(5)
            for ((index, entry) in kinds.withIndex()) {
                val prefix = if (index == 0) "<#A9B4C3>弹种 " else "<#A9B4C3>     "
                lines += "$prefix<#F2F5FA>${entry.key}</#F2F5FA> <#6E7A8C>×</#6E7A8C><#F2F5FA>${entry.value}</#F2F5FA>"
            }
            if (kinds.size >= 5) {
                lines += "<#6E7A8C>    ……（最多显示 5 条）"
            }
        }
        lines += "<#A9B4C3>快速换弹 " + if (mag.allowFastReload) {
            "<#8FD4A6>允许"
        } else {
            "<#E08A98>不允许"
        }
        lines += "<#6E7A8C>─────────────────────"
        meta.lore(existing + lines.map { mmUtil.deserialize(it) })
        preview.itemMeta = meta
        preview.amount = 1
        return preview
    }

    private fun num(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
