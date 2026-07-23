package io.github.zzzyyylllty.chotentech.kether.script

import io.github.zzzyyylllty.chotentech.data.getData
import org.bukkit.entity.Player
import taboolib.module.kether.KetherParser
import taboolib.module.kether.combinationParser
import taboolib.module.kether.script

@KetherParser(["balance", "bal"], shared = true)
fun actionBalance() = combinationParser {
    it.group(text(), text(), long().option()).apply(it) { type, option, value ->
        now {
            val player = script().sender?.castSafely<Player>() ?: error("Player required")
            val data = player.getData() ?: error("Player data not found")
            when (option) {
                "set" -> setBalance(data, type, value ?: error("Value required for set"))
                "add" -> addBalance(data, type, value ?: error("Value required for add"))
                "remove" -> removeBalance(data, type, value ?: error("Value required for remove"))
                "get" -> getBalance(data, type)
                "reset" -> resetBalance(data, type)
                else -> error("Unknown balance action: $option")
            }
        }
    }
}

private fun setBalance(data: io.github.zzzyyylllty.chotentech.data.PlayerData, type: String, value: Long) {
    when (type) {
        "money" -> data.money = value
        "point" -> data.point = value
        "tape" -> data.tape = value
        "goldentape" -> data.goldentape = value
        else -> error("Unknown balance type: $type")
    }
}

private fun addBalance(data: io.github.zzzyyylllty.chotentech.data.PlayerData, type: String, amount: Long) {
    when (type) {
        "money" -> data.money += amount
        "point" -> data.point += amount
        "tape" -> data.tape += amount
        "goldentape" -> data.goldentape += amount
        else -> error("Unknown balance type: $type")
    }
}

private fun removeBalance(data: io.github.zzzyyylllty.chotentech.data.PlayerData, type: String, amount: Long) {
    when (type) {
        "money" -> data.money = maxOf(data.money - amount, 0L)
        "point" -> data.point = maxOf(data.point - amount, 0L)
        "tape" -> data.tape = maxOf(data.tape - amount, 0L)
        "goldentape" -> data.goldentape = maxOf(data.goldentape - amount, 0L)
        else -> error("Unknown balance type: $type")
    }
}

private fun getBalance(data: io.github.zzzyyylllty.chotentech.data.PlayerData, type: String): Long {
    return when (type) {
        "money" -> data.money
        "point" -> data.point
        "tape" -> data.tape
        "goldentape" -> data.goldentape
        else -> error("Unknown balance type: $type")
    }
}

private fun resetBalance(data: io.github.zzzyyylllty.chotentech.data.PlayerData, type: String) {
    when (type) {
        "money" -> data.money = 0L
        "point" -> data.point = 0L
        "tape" -> data.tape = 0L
        "goldentape" -> data.goldentape = 0L
        else -> error("Unknown balance type: $type")
    }
}
