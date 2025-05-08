package com.watchware.mp3.data.model

import java.io.File

/**
 * Represents an item in the media browser (either a folder or an audio file)
 */
sealed class MediaItem(
    open val name: String,
    open val path: String
) {
    /**
     * Represents a folder that can contain other media items
     */
    data class Folder(
        override val name: String,
        override val path: String,
        val children: List<MediaItem> = emptyList()
    ) : MediaItem(name, path)

    /**
     * Represents an audio file
     */
    data class AudioFile(
        override val name: String,
        override val path: String,
        val mimeType: String
    ) : MediaItem(name, path)
    
    companion object {
        /**
         * Audio file extensions to look for
         */
        val AUDIO_EXTENSIONS = listOf(
            ".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac"
        )
        
        /**
         * Check if a file is an audio file based on its extension
         */
        fun isAudioFile(file: File): Boolean {
            return AUDIO_EXTENSIONS.any { file.name.lowercase().endsWith(it) }
        }
    }
}
