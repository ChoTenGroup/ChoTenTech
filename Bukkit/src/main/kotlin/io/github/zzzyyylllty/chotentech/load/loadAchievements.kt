package io.github.zzzyyylllty.chotentech.load

import io.github.zzzyyylllty.chotentech.function.AchievementManager
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import java.io.File

fun loadAchievementFiles() {
    if (!File(getDataFolder(), "achievements").exists()) {
        releaseResourceFolder("achievements")
    }

    val files = mutableListOf<File>()
    collectYamlFiles(File(getDataFolder(), "achievements"), files)

    AchievementManager.load(files)
}

private fun collectYamlFiles(dir: File, result: MutableList<File>) {
    val list = dir.listFiles() ?: return
    for (file in list) {
        if (file.isDirectory) {
            collectYamlFiles(file, result)
        } else if (file.name.endsWith(".yml") || file.name.endsWith(".yaml")) {
            result.add(file)
        }
    }
}
