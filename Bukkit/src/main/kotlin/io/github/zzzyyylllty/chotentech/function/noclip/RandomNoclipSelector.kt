package io.github.zzzyyylllty.chotentech.function.noclip

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.RandomNoclipMode

object RandomNoclipSelector {

    fun getEnabledNoclips(playerUuid: String, level: Level): Set<String> {
        val allNames = level.noclips.mapNotNull { it.name }.toSet()
        val groups = level.randomGroups

        if (groups == null || groups.isEmpty()) return allNames

        val mode = level.randomNoclip ?: return allNames

        return when (mode) {
            RandomNoclipMode.RESTART -> getRestartEnabled(level.id, allNames, groups)
            RandomNoclipMode.EACH -> getEachEnabled(playerUuid, level.id, allNames, groups)
        }
    }

    private fun getRestartEnabled(levelId: String, allNames: Set<String>, groups: List<List<String>>): Set<String> {
        return ChoTenTech.restartRandomNoclips.computeIfAbsent(levelId) {
            selectFromGroups(allNames, groups)
        }
    }

    private fun getEachEnabled(playerUuid: String, levelId: String, allNames: Set<String>, groups: List<List<String>>): Set<String> {
        val key = "$playerUuid::$levelId"
        return ChoTenTech.playerRandomNoclips.computeIfAbsent(key) {
            selectFromGroups(allNames, groups)
        }
    }

    private fun selectFromGroups(allNames: Set<String>, groups: List<List<String>>): Set<String> {
        val selected = mutableSetOf<String>()
        val groupedNames = mutableSetOf<String>()
        for (group in groups) {
            val available = group.filter { it in allNames }
            if (available.isNotEmpty()) {
                selected.add(available.random())
            }
            groupedNames.addAll(group)
        }
        selected.addAll(allNames - groupedNames)
        return selected
    }

    fun resetPlayerRandom(playerUuid: String, levelId: String) {
        ChoTenTech.playerRandomNoclips.remove("$playerUuid::$levelId")
    }

    fun isNoclipEnabled(playerUuid: String, level: Level, noclipName: String?): Boolean {
        if (noclipName == null) return true
        val enabled = getEnabledNoclips(playerUuid, level)
        return noclipName in enabled
    }
}
