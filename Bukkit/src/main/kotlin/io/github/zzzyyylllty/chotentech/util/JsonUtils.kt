package io.github.zzzyyylllty.chotentech.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.JsonSerializer
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import java.lang.ref.Reference
import java.security.SecureRandom


val jsonUtils: Gson by lazy {
    val builder = GsonBuilder()
        .setVersion(1.0)
        .disableHtmlEscaping()
        .disableInnerClassSerialization()
        // .setPrettyPrinting()
        .excludeFieldsWithModifiers(java.lang.reflect.Modifier.STATIC, java.lang.reflect.Modifier.TRANSIENT, java.lang.reflect.Modifier.VOLATILE)
        .serializeNulls()
        .setLenient()
//        .disableJdkUnsafe()
        .addSerializationExclusionStrategy(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes): Boolean {
                // Skip fields declared in problematic Java packages
                val declaringClass = f.declaringClass
                if (declaringClass != null) {
                    val packageName = declaringClass.packageName
                    // Skip java.lang.ref.* (Reference and its subclasses)
                    if (packageName.startsWith("java.lang.ref")) {
                        return true
                    }
                    // Skip java.security.* (SecureRandom etc.)
                    if (packageName.startsWith("java.security")) {
                        return true
                    }
                }

                // Skip fields whose type is in problematic Java packages
                val fieldType = f.declaredType
                if (fieldType != null) {
                    val typeName = fieldType.typeName
                    // Skip java.lang.ref.* types
                    if (typeName.startsWith("java.lang.ref")) {
                        return true
                    }
                    // Skip java.security.* types
                    if (typeName.startsWith("java.security")) {
                        return true
                    }
                    // Skip java.util.Random (handled by type adapter, but exclude for safety)
                    if (typeName.contains("java.util.Random")) {
                        return true
                    }
                    // Skip java.security.SecureRandom
                    if (typeName.contains("java.security.SecureRandom")) {
                        return true
                    }
                }

                return false
            }
            override fun shouldSkipClass(clazz: Class<*>): Boolean {
                return false
            }
        })
        .registerTypeAdapter(java.util.Random::class.java, object : JsonSerializer<java.util.Random>, JsonDeserializer<java.util.Random> {
            override fun serialize(src: java.util.Random, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): JsonElement {
                return JsonNull.INSTANCE
            }
            override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): java.util.Random? {
                return null
            }
        })
        .registerTypeAdapter(SecureRandom::class.java, object : JsonSerializer<SecureRandom>, JsonDeserializer<SecureRandom> {
            override fun serialize(src: SecureRandom, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): JsonElement {
                return JsonNull.INSTANCE
            }
            override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): SecureRandom? {
                return null
            }
        })
        .registerTypeAdapter(Reference::class.java, object : JsonSerializer<Reference<*>>, JsonDeserializer<Reference<*>> {
            override fun serialize(src: Reference<*>, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): JsonElement {
                return JsonNull.INSTANCE
            }
            override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): Reference<*>? {
                return null
            }
        })
    builder.create()
}