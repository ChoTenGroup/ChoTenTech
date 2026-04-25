package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.util.jsonUtils
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType


class ChunkDataPDCType : PersistentDataType<String, ChunkData> {
    override fun getPrimitiveType(): Class<String> {
        return String::class.java
    }

    override fun getComplexType(): Class<ChunkData> {
        return ChunkData::class.java
    }

    override fun toPrimitive(
        complex: ChunkData,
        context: PersistentDataAdapterContext
    ): String {
        return jsonUtils.toJson(complex, ChunkData::class.java)
    }

    override fun fromPrimitive(
        primitive: String,
        context: PersistentDataAdapterContext
    ): ChunkData {
        return try {
            jsonUtils.fromJson(primitive, ChunkData::class.java)
        } catch (e: Exception) {
            // Return empty ChunkData if deserialization fails
            ChunkData()
        }
    }

    companion object {
        val INSTANCE: ChunkDataPDCType = ChunkDataPDCType()
    }
}