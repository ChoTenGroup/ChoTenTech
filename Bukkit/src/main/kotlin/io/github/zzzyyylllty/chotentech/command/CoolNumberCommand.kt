//package io.github.zzzyyylllty.chotentech.command
//
//import io.github.zzzyyylllty.chotentech.data.CoolNumberTier
//import io.github.zzzyyylllty.chotentech.data.DataManager
//import io.github.zzzyyylllty.chotentech.data.IDData
//import io.github.zzzyyylllty.chotentech.data.getData
//import io.github.zzzyyylllty.chotentech.function.CoolNumberManager as Manager
//import io.github.zzzyyylllty.chotentech.gui.CoolNumberMenu
//import io.github.zzzyyylllty.chotentech.util.toComponent
//import org.bukkit.command.CommandSender
//import org.bukkit.entity.Player
//import taboolib.common.platform.command.CommandBody
//import taboolib.common.platform.command.CommandHeader
//import taboolib.common.platform.command.PermissionDefault
//import taboolib.common.platform.command.mainCommand
//import taboolib.common.platform.command.player
//import taboolib.common.platform.command.subCommand
//import taboolib.common.platform.function.submitAsync
//
//@CommandHeader(
//    name = "chotentech-coolnumber",
//    aliases = ["cttcn", "cn", "coolnumber", "coolnum"],
//    permission = "chotentech.command.coolnumber",
//    permissionMessage = "",
//    permissionDefault = PermissionDefault.TRUE
//)
//object CoolNumberCommand {
//
//    @CommandBody
//    val main = mainCommand {
//        execute<CommandSender> { sender, _, _ ->
//            if (sender is Player) CoolNumberMenu().open(sender)
//            else sender.sendMessage("§c请使用此命令作为玩家执行")
//        }
//    }
//
//    @CommandBody
//    val open = subCommand {
//        execute<CommandSender> { sender, _, _ ->
//            if (sender is Player) CoolNumberMenu().open(sender)
//            else sender.sendMessage("§c请使用此命令作为玩家执行")
//        }
//        player("target") {
//            execute<CommandSender> { sender, context, _ ->
//                val target = context.player("target").castSafely<Player>() ?: return@execute
//                CoolNumberMenu().open(target)
//            }
//        }
//    }
//
//    @CommandBody
//    val admin = subCommand {
//        literal("set") {
//            player("target") {
//                dynamic("number") {
//                    dynamic("tier") {
//                        execute<CommandSender> { sender, context, _ ->
//                            val target = context.player("target").castSafely<Player>() ?: return@execute
//                            val number = context["number"]
//                            val tierStr = context["tier"].uppercase()
//
//                            if (number.length < 4 || number.length > 10 || !number.all { it.isDigit() }) {
//                                sender.sendMessage("§c无效号码：需要4-10位数字")
//                                return@execute
//                            }
//                            val tier = CoolNumberTier.entries.firstOrNull { it.tag == tierStr }
//                            if (tier == null) {
//                                sender.sendMessage("§c无效等级：可选 ${CoolNumberTier.entries.joinToString(", ") { it.tag }}")
//                                return@execute
//                            }
//
//                            submitAsync {
//                                val data = target.getData()
//                                if (data == null) {
//                                    sender.sendMessage("§c目标玩家数据不存在")
//                                    return@submitAsync
//                                }
//                                val newCid = IDData(number.toLong(), tier.tag)
//                                DataManager.modifyUserIDData(newCid, data)
//                                sender.sendMessage("§a已将 ${target.name} 的靓号设置为 $number (${tier.displayName})")
//                                target.sendMessage(" §a管理员已将你的靓号设置为 ${tier.color}${number}")
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        literal("recalc") {
//            player("target") {
//                execute<CommandSender> { sender, context, _ ->
//                    val target = context.player("target").castSafely<Player>() ?: return@execute
//                    submitAsync {
//                        val data = target.getData() ?: run {
//                            sender.sendMessage("§c目标玩家数据不存在")
//                            return@submitAsync
//                        }
//                        val score = Manager.calculateScore(data.cid.id.toString())
//                        val tier = Manager.getTierFromScore(score)
//                        val newCid = data.cid.copy(type = tier.tag)
//                        DataManager.modifyUserIDData(newCid, data)
//                        sender.sendMessage("§a已重新计算 ${target.name} 的靓号等级：${data.cid.id} → 分数 $score → ${tier.displayName}")
//                        target.sendMessage(" §a你的靓号等级已根据分数重新计算为 ${tier.color}${tier.displayName}")
//                    }
//                }
//            }
//        }
//
//        literal("info") {
//            player("target") {
//                execute<CommandSender> { sender, context, _ ->
//                    val target = context.player("target").castSafely<Player>() ?: return@execute
//                    val data = target.getData() ?: run {
//                        sender.sendMessage("§c目标玩家数据不存在")
//                        return@execute
//                    }
//                    val num = data.cid.id.toString()
//                    val score = Manager.calculateScore(num)
//                    val tier = CoolNumberTier.fromTag(data.cid.type)
//                    sender.sendMessage("§6=== ${target.name} 的靓号信息 ===")
//                    sender.sendMessage(" §7号码: ${tier.color}${num}")
//                    sender.sendMessage(" §7分数: §e$score")
//                    sender.sendMessage(" §7等级: ${tier.color}${tier.displayName}")
//                }
//            }
//        }
//
//        literal("banned") {
//            dynamic("add/remove/list") {
//                execute<CommandSender> { sender, context, _ ->
//                    sender.sendMessage("§c用法: /cttcn admin banned add|remove|list <number>")
//                }
//                literal("list") {
//                    execute<CommandSender> { sender, _, _ ->
//                        sender.sendMessage("§6禁止的靓号列表:")
//                        Manager.bannedNumbers.forEach { sender.sendMessage(" §7- $it") }
//                    }
//                }
//            }
//        }
//    }
//}
