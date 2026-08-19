package io.github.zzzyyylllty.chotentech.util

import com.google.gson.reflect.TypeToken
import org.bukkit.inventory.ItemStack
import java.util.Base64

object ItemSerializer {

    private val mapType = object : TypeToken<Map<String, Any>>() {}.type

    fun serialize(item: ItemStack?): String? {
        if (item == null || item.type.isAir) return null
        return try {
            val map = item.serialize()
            jsonUtils.toJson(map)
        } catch (e: Exception) {
            null
        }
    }

    fun deserialize(data: String?): ItemStack? {
        if (data == null) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = jsonUtils.fromJson<Map<String, Any>>(data, mapType) as Map<String, Any>
            ItemStack.deserialize(map)
        } catch (e: Exception) {
            null
        }
    }

    /** 使用 Paper NBT 序列化（兼容 1.21.4 组件系统），不经过 Gson */
    fun serializeNbt(item: ItemStack?): String? {
        if (item == null || item.type.isAir) return null
        return try {
            Base64.getEncoder().encodeToString(item.serializeAsBytes())
        } catch (e: Exception) {
            null
        }
    }

    fun deserializeNbt(data: String?): ItemStack? {
        if (data == null) return null
        return try {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(data))
        } catch (e: Exception) {
            null
        }
    }

    /** 优先按 Paper NBT 反序列化(1.21.4+ 组件系统),失败回退旧 Gson 格式(存量数据) */
    fun deserializeAny(data: String?): ItemStack? {
        if (data == null) return null
        return deserializeNbt(data) ?: deserialize(data)
    }
}
