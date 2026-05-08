package io.github.zzzyyylllty.chotentech.gui

import io.github.zzzyyylllty.chotentech.data.Base
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.util.minimessage.mmJsonUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

class FactionSelectMenu {

    fun open(player: Player) {
        val data = player.getData() ?: return
        val currentFaction = data.mainTag

        if (currentFaction != null) {
            openBaseList(player, currentFaction)
            return
        }

        player.openMenu<Chest>(mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176>".toComponent())) {
            rows(6)
            hidePlayerInventory()

            map(
                "MMMMM   -",
                "MMMMM   -",
                "MMMMM   -",
                "BBBB-UUUU",
                "BBBB-UUUU",
                "    ---  "
            )

            set('-', XMaterial.BLACK_STAINED_GLASS_PANE) { name = " " }

            // MEG
            set('M', buildFactionIcon(
                display = "<i><#ffdd00><b>探险者总署</b><gray>（M.E.G.）",
                lore = listOf(
                    "我们是一线希望。我们承诺尽可能保护所有人，",
                    "探索从未有人见过的非凡地方，我们会保证你的安全。",
                    "这是他们所承诺的。随着冷战愈演愈烈，",
                    "在战争被波及到的流浪者们纷纷涌向 M.E.G.。",
                    "然而，M.E.G. 必须继续维护其名号的高标准，",
                    "否则，阈限世界将损失一个伟大的组织......",
                    "",
                    "<#ffdd00><i>他们并不完美，但没有人是完美的。他们会找到出路的。",
                    "",
                    "<green>通过点击右边的基地以选择本阵营。"
                )
            )) {
                clicker.closeInventory()
                openBaseList(clicker, "MEG")
            }

            // BNTG
            set('B', buildFactionIcon(
                display = "<i><#33ffaa><b>不结盟贸易集团</b><gray>（B.N.T.G.）",
                lore = listOf(
                    "金钱归我，荣耀归你。",
                    "B.N.T.G.，亦称不结盟贸易集团。",
                    "一个通过电汇、钞票和股票证书联系在一起的组织。",
                    "只要你能带来收益，你就是B.N.T.G.的不错人选。",
                    "我们等待着，渴望一个未来，一个可能属于金钱和荣耀的秩序。",
                    "",
                    "<#33ffaa><i>既来之，则安之。在这里，每一句话都是契约。",
                    "",
                    "<green>通过点击下方的基地以选择本阵营。"
                )
            )) {
                clicker.closeInventory()
                openBaseList(clicker, "BNTG")
            }

            // UEC
            set('U', buildFactionIcon(
                display = "<i><#ff3d00><b>无羁探险者联盟</b><gray>（U.E.C.）",
                lore = listOf(
                    "U.E.C.创立的宗旨是\"人们的安全高于一切\"。至少他们是这么说的。",
                    "无羁探险者联盟通过创立傀儡组织，扶持代理人来攫取利益。",
                    "一个臭名昭著的例子是\"无尽下水道\"组织，许多U.E.C.的居民都定居于此。",
                    "无羁探险者联盟是一个结合了经济联盟、军事联盟和一体化项目的组织。",
                    "",
                    "<#ff3d00><i>不受恐惧之束缚，因为勇敢而团结。",
                    "",
                    "<green>通过点击下方的基地以选择本阵营。"
                )
            )) {
                clicker.closeInventory()
                openBaseList(clicker, "UEC")
            }
        }
    }

    private fun openBaseList(player: Player, factionTag: String) {
        val faction = BaseManager.factions.firstOrNull { it.tag == factionTag } ?: return
        val bases = BaseManager.getBasesByFaction(factionTag)

        player.openMenu<PageableChest<Base>>(
            mmJsonUtil.serialize("<white><shift:-16><image:kangelicon:list><shift:-176><aqua><font:minecraft:fusionpixel10>${faction.displayName} 基地选择".toComponent())
        ) {
            rows(6)
            hidePlayerInventory()

            map(
                "CCC    <>",
                "         ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                " @@@@@@@ ",
                "         "
            )

            set('C', buildItem(XMaterial.COMPASS) {
                name = "§b§l菜单"
                lore += "§7点击打开菜单"
            }.apply {
                val m = itemMeta ?: return@apply
                m.itemModel = NamespacedKey.fromString("kangelitem:void")
                setItemMeta(m)
            }) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "invero menu open menu ${clicker.name}")
                clicker.closeInventory()
            }

            slotsBy('@')

            elements { bases }

            onGenerate { p, base, _, _ ->
                buildBaseItem(base, p)
            }

            onClick { event, base ->
                val clicker = event.clicker
                if (!base.enable) {
                    clicker.sendMessage("<red>这个阵营尚不可使用。")
                    clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                    return@onClick
                }
                val data = clicker.getData()
                if (data?.subTag == base.idef) {
                    clicker.playSound(clicker.location, Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 1f)
                    clicker.sendMessage("<red>你已经选择了!")
                    return@onClick
                }
                clicker.sendMessage("<green>已选择这个阵营/基地。")
                clicker.playSound(clicker.location, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f)
                BaseManager.joinBase(clicker, base.idef)
                clicker.closeInventory()
            }

            onPageChange { p ->
                p.playSound(p.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f)
            }

            setPreviousPage(7) { _, page ->
                buildItem(XMaterial.ARROW) {
                    name = "§a上一页"
                }.apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:previous" else "kangelitem:previous_no")
                    setItemMeta(m)
                }
            }

            setNextPage(8) { _, page ->
                buildItem(XMaterial.ARROW) {
                    name = "§a下一页"
                }.apply {
                    val m = itemMeta ?: return@apply
                    m.itemModel = NamespacedKey.fromString(if (page) "kangelitem:next" else "kangelitem:next_no")
                    setItemMeta(m)
                }
            }
        }
    }

    private fun buildFactionIcon(display: String, lore: List<String>): ItemStack {
        val item = XMaterial.STRUCTURE_VOID.parseItem() ?: ItemStack(Material.STRUCTURE_VOID)
        val meta = item.itemMeta ?: return item
        meta.setCustomModelData(10000)
        meta.displayName(display.toComponent())
        meta.lore(lore.map { "<!i><gray>$it" }.toComponent())
        item.setItemMeta(meta)
        return item
    }

    private fun buildBaseItem(base: Base, player: Player): ItemStack {
        val data = player.getData()
        val alreadySelected = data?.subTag == base.idef

        val item = XMaterial.BOOK.parseItem() ?: ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item

        meta.displayName("<gradient:#888888:#eeeeee:#888888><< ${base.name} >>".toComponent())

        val lore = mutableListOf<String>()
        lore += "<!i><gray><st>${" ".repeat(60)}</st>"

        if (!base.enable) {
            lore += "<!i><yellow>施工中 <gray>- 将在 ${base.dev} <gray>版本中发布!"
        } else {
            lore += "<!i><green>可用 <gray>- 已在 ${base.dev} <gray>版本中发布!"
        }

        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"
        lore += "<!i><white><b>| <yellow>描述"
        base.lore.forEach { lore += "<!i> $it" }
        lore += "<!i><white><b>| <yellow>初始资源"
        lore += " <!i>${base.start}"
        lore += "<!i><dark_gray><st>${" ".repeat(60)}</st>"

        if (!base.enable) {
            lore += "<!i><#ffaa00>敬请期待!"
        } else if (alreadySelected) {
            lore += "<!i><#88eeff>选择中"
        } else {
            lore += "<!i><green>点击以选择这个基地。"
        }

        lore += "<!i><gray><st>${" ".repeat(60)}</st>"

        meta.lore(lore.toComponent())
        item.setItemMeta(meta)
        return item
    }
}
