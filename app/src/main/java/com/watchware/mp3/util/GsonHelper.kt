package com.watchware.mp3.util

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.watchware.mp3.data.model.MediaItem
import java.lang.reflect.Type

/**
 * Helper class for Gson serialization/deserialization of MediaItem sealed class
 */
object GsonHelper {
    
    /**
     * Get a properly configured Gson instance for handling MediaItem serialization
     */
    fun getGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(MediaItem.AudioFile::class.java, MediaItemSerializer())
            .registerTypeAdapter(MediaItem.AudioFile::class.java, MediaItemDeserializer())
            .create()
    }
    
    /**
     * Serialize a list of AudioFile objects to a JSON string
     */
    fun serializePlaylist(playlist: List<MediaItem.AudioFile>): String {
        return getGson().toJson(playlist)
    }
    
    /**
     * Deserialize a JSON string to a list of AudioFile objects
     * Returns an empty list if deserialization fails
     */
    fun deserializePlaylist(json: String?): List<MediaItem.AudioFile> {
        if (json.isNullOrEmpty()) return emptyList()
        
        return try {
            val type = object : TypeToken<List<MediaItem.AudioFile>>() {}.type
            getGson().fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Type adapter for serializing MediaItem instances
     */
    private class MediaItemSerializer : JsonSerializer<MediaItem.AudioFile> {
        override fun serialize(src: MediaItem.AudioFile, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()
            jsonObject.addProperty("name", src.name)
            jsonObject.addProperty("path", src.path)
            jsonObject.addProperty("mimeType", src.mimeType)
            return jsonObject
        }
    }
    
    /**
     * Type adapter for deserializing MediaItem instances
     */
    private class MediaItemDeserializer : JsonDeserializer<MediaItem.AudioFile> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MediaItem.AudioFile {
            val jsonObject = json.asJsonObject
            
            val name = jsonObject.get("name").asString
            val path = jsonObject.get("path").asString
            val mimeType = jsonObject.get("mimeType").asString
            
            return MediaItem.AudioFile(
                name = name,
                path = path,
                mimeType = mimeType
            )
        }
    }
}
