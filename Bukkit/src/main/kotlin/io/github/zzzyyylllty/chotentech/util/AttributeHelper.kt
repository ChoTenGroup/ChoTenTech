package io.github.zzzyyylllty.chotentech.util

import org.bukkit.entity.Player

object AttributeHelper {
    fun updateClassAttribute(p: Player) {
        try {
            val clazz = Class.forName("org.serverct.ersha.AttributePlus")
            val attrManager = clazz.getMethod("getAttributeManager").invoke(null)
            val attrData = attrManager.javaClass.getMethod("getAttributeData", Player::class.java).invoke(attrManager, p)
            val apiAttr = attrData.javaClass.getMethod("takeApiAttribute", String::class.java)
            apiAttr.invoke(attrData, "Quetiapine_ClassBuff")
        } catch (_: Exception) {
            // AttributePlus not installed
        }
    }
}
