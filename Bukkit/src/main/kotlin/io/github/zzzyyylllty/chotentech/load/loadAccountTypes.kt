package io.github.zzzyyylllty.chotentech.load

import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypes
import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypesList
import io.github.zzzyyylllty.chotentech.data.IDType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.configuration.Configuration

fun loadAccountTypes() {
    accountTypes.getKeys(false).forEach { key ->
        val section = accountTypes.getConfigurationSection(key)!!
        accountTypesList[key] = IDType(
            id = section.getString("id") ?: "NORMAL",
            displayName = section.getString("displayName") ?: "?",
            color = section.getString("color") ?: "?",
            icon = section.getString("icon") ?: "?",
            startChar = section.getString("startChar") ?: "?",
            char = section.getString("char") ?: "?",
            endChar = section.getString("endChar") ?: "?",
        )
    }
}