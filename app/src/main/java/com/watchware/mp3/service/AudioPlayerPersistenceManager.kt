package com.watchware.mp3.service

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.watchware.mp3.data.model.MediaItem
import com.watchware.mp3.util.GsonHelper
import java.io.File

/**
 * Manager for handling persistence of audio player state
 * Implements lazy initialization pattern to avoid increasing startup times
 */
class AudioPlayerPersistenceManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "MediaPlayerPrefs"
        private const val KEY_LAST_PLAYED_PATH = "lastPlayedPath" 
        private const val KEY_LAST_PLAYED_NAME = "lastPlayedName"
        private const val KEY_LAST_PLAYED_MIME_TYPE = "lastPlayedMimeType"
        private const val KEY_LAST_PLAYLIST_DIRECTORY = "lastPlaylistDirectory"
        private const val KEY_LAST_PLAYLIST = "lastPlaylist"
        private const val KEY_LAST_PLAYLIST_INDEX = "lastPlaylistIndex"
    }

    /**
     * Save the playlist to SharedPreferences
     */
    fun savePlaylist(playlist: List<MediaItem.AudioFile>, currentIndex: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        try {
            // Use our improved playlist serialization method
            val playlistJson = GsonHelper.serializePlaylist(playlist)
            
            // Save playlist and current index
            editor.putString(KEY_LAST_PLAYLIST, playlistJson)
            editor.putInt(KEY_LAST_PLAYLIST_INDEX, currentIndex)
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Save last played song information to SharedPreferences
     */
    fun saveLastPlayedSong(audioFile: MediaItem.AudioFile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Get the directory of the current file
        val fileUri = Uri.parse(audioFile.path)
        val fileDirectory = fileUri.path?.substringBeforeLast('/') ?: ""
        
        prefs.edit().apply {
            putString(KEY_LAST_PLAYED_PATH, audioFile.path)
            putString(KEY_LAST_PLAYED_NAME, audioFile.name)
            putString(KEY_LAST_PLAYED_MIME_TYPE, audioFile.mimeType)
            putString(KEY_LAST_PLAYLIST_DIRECTORY, fileDirectory)
            apply()
        }
    }

    /**
     * Restore the last played song from SharedPreferences
     * Returns a data class with the song, playlist, and current index if found, null otherwise
     */
    fun restoreLastPlayedSong(): LastPlaybackState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPath = prefs.getString(KEY_LAST_PLAYED_PATH, null)
        val lastName = prefs.getString(KEY_LAST_PLAYED_NAME, null)
        val lastMimeType = prefs.getString(KEY_LAST_PLAYED_MIME_TYPE, "audio/mpeg")
        
        // Try to restore the saved playlist first
        val playlistJson = prefs.getString(KEY_LAST_PLAYLIST, null)
        val lastIndex = prefs.getInt(KEY_LAST_PLAYLIST_INDEX, -1)
        
        // First attempt: Try to restore from saved playlist JSON
        if (playlistJson != null && lastIndex != -1) {
            try {
                // Use our improved playlist deserialization method
                val playlist = GsonHelper.deserializePlaylist(playlistJson)
                
                if (playlist.isNotEmpty() && lastIndex < playlist.size) {
                    val currentAudioFile = playlist[lastIndex]
                    return LastPlaybackState(
                        audioFile = currentAudioFile,
                        playlist = playlist,
                        currentIndex = lastIndex
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to fallback method
            }
        }
        
        // Second attempt (fallback): Try to restore from last played song info
        if (lastPath != null && lastName != null) {
            val audioFile = MediaItem.AudioFile(
                name = lastName,
                path = lastPath,
                mimeType = lastMimeType ?: "audio/mpeg"
            )
            
            // Initialize an empty list for audio files
            var audioFiles = emptyList<MediaItem.AudioFile>()
            val lastDirectory = prefs.getString(KEY_LAST_PLAYLIST_DIRECTORY, null)
            
            // Find or recreate the playlist based on the directory of the last played file
            if (lastDirectory != null) {
                val directory = File(lastDirectory)
                if (directory.exists() && directory.isDirectory) {
                    // Use content resolver to get all audio files in the same directory
                    try {
                        audioFiles = fetchAudioFilesFromContentResolver(lastDirectory)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback to the file-based approach
                        audioFiles = fetchAudioFilesFromFileSystem(directory)
                    }
                }
            }
            
            // If we couldn't find any files in the directory, at least add the current file to the playlist
            if (audioFiles.isEmpty()) {
                audioFiles = listOf(audioFile)
            }
            
            // Find the index of the restored song in the playlist
            val index = audioFiles.indexOfFirst { it.path == lastPath }
            
            // If we found the index, use it; otherwise, set it to 0 if we have files
            val finalIndex = if (index != -1) index else if (audioFiles.isNotEmpty()) 0 else -1
            
            return LastPlaybackState(
                audioFile = audioFile,
                playlist = audioFiles,
                currentIndex = finalIndex
            )
        }
        
        return null
    }

    /**
     * Get audio files from the MediaStore content resolver
     */
    private fun fetchAudioFilesFromContentResolver(directoryPath: String): List<MediaItem.AudioFile> {
        val contentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$directoryPath/%")
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.MIME_TYPE
            ),
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )
        
        val tempList = mutableListOf<MediaItem.AudioFile>()
        
        cursor?.use { c ->
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            
            while (c.moveToNext()) {
                val name = c.getString(nameColumn)
                val path = c.getString(pathColumn)
                val mimeType = c.getString(mimeTypeColumn)
                
                tempList.add(MediaItem.AudioFile(
                    name = name,
                    path = path,
                    mimeType = mimeType
                ))
            }
        }
        
        return tempList
    }
    
    /**
     * Get audio files from the filesystem directly
     */
    private fun fetchAudioFilesFromFileSystem(directory: File): List<MediaItem.AudioFile> {
        return directory.listFiles { file ->
            file.isFile && (
                file.name.lowercase().endsWith(".mp3") ||
                file.name.lowercase().endsWith(".wav") ||
                file.name.lowercase().endsWith(".ogg") ||
                file.name.lowercase().endsWith(".aac")
            )
        }?.sortedBy { it.name }?.map { file ->
            MediaItem.AudioFile(
                name = file.name,
                path = file.absolutePath,
                mimeType = when {
                    file.name.lowercase().endsWith(".mp3") -> "audio/mpeg"
                    file.name.lowercase().endsWith(".wav") -> "audio/wav"
                    file.name.lowercase().endsWith(".ogg") -> "audio/ogg"
                    file.name.lowercase().endsWith(".aac") -> "audio/aac"
                    else -> "audio/mpeg"
                }
            )
        } ?: emptyList()
    }

    /**
     * Data class to return the complete playback state
     */
    data class LastPlaybackState(
        val audioFile: MediaItem.AudioFile,
        val playlist: List<MediaItem.AudioFile>,
        val currentIndex: Int
    )
}
