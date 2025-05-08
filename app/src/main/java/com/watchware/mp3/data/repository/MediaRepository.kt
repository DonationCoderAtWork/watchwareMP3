package com.watchware.mp3.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.watchware.mp3.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MediaRepository(private val context: Context) {
    
    private val isScanning = AtomicBoolean(false)
    
    /**
     * Checks if the app has the necessary permissions to scan for media files
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Scans the device for audio files and returns a list of root folders that contain audio files
     */
    suspend fun scanForAudioFiles(): List<MediaItem.Folder> = withContext(Dispatchers.IO) {
        if (isScanning.getAndSet(true)) {
            return@withContext emptyList<MediaItem.Folder>()
        }
        
        try {
            // Set a timeout for scanning to avoid ANR issues
            return@withContext withTimeout(30000) {
                val externalStorageDir = Environment.getExternalStorageDirectory()
                if (!externalStorageDir.exists() || !externalStorageDir.canRead()) {
                    return@withTimeout emptyList<MediaItem.Folder>()
                }
                
                val rootFolders = mutableListOf<MediaItem.Folder>()
                
                // For wear OS devices, we might want to use a more targeted approach
                // since a full scan can be resource-intensive
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                
                // Scan default music directory if it exists and contains files
                if (musicDir.exists() && musicDir.canRead()) {
                    val scannedMusicDirs = scanDirectory(musicDir)
                    scannedMusicDirs.filter { it.children.isNotEmpty() }.forEach {
                        rootFolders.add(it)
                    }
                }
                
                // Scan downloads directory if it exists and contains files
                if (downloadDir.exists() && downloadDir.canRead()) {
                    val scannedDownloadDirs = scanDirectory(downloadDir)
                    scannedDownloadDirs.filter { it.children.isNotEmpty() }.forEach {
                        rootFolders.add(it)
                    }
                }
                
                // If we didn't find any audio files in the targeted directories,
                // fall back to a full scan of external storage
                if (rootFolders.isEmpty()) {
                    val scannedDirectories = scanDirectory(externalStorageDir)
                    scannedDirectories.filter { it.children.isNotEmpty() }.forEach {
                        rootFolders.add(it)
                    }
                }
                
                return@withTimeout rootFolders
            }
        } catch (e: Exception) {
            return@withContext emptyList<MediaItem.Folder>()
        } finally {
            isScanning.set(false)
        }
    }
    
    /**
     * Recursively scans a directory for audio files and returns a folder structure
     */
    private fun scanDirectory(directory: File): List<MediaItem.Folder> {
        val folders = mutableListOf<MediaItem.Folder>()
        
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return folders
        }
        
        val files = directory.listFiles() ?: return folders
        val mediaItems = mutableListOf<MediaItem>()
        
        // First, add audio files in the current directory
        for (file in files) {
            if (file.isFile && MediaItem.isAudioFile(file)) {
                val mimeType = getMimeTypeFromExtension(file.extension)
                mediaItems.add(
                    MediaItem.AudioFile(
                        name = file.name,
                        path = file.absolutePath,
                        mimeType = mimeType
                    )
                )
            }
        }
        
        // Then recursively scan subdirectories
        for (file in files) {
            if (file.isDirectory) {
                val subFolders = scanDirectory(file)
                for (subFolder in subFolders) {
                    if (subFolder.children.isNotEmpty()) {
                        mediaItems.add(subFolder)
                    }
                }
            }
        }
        
        // Only add this directory if it contains audio files (directly or indirectly)
        if (mediaItems.isNotEmpty()) {
            folders.add(
                MediaItem.Folder(
                    name = directory.name,
                    path = directory.absolutePath,
                    children = mediaItems
                )
            )
        }
        
        return folders
    }
    
    /**
     * Gets the MIME type from a file extension
     */
    private fun getMimeTypeFromExtension(extension: String): String {
        return when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/m4a"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "audio/*"
        }
    }
}
