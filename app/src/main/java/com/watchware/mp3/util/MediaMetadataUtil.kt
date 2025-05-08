package com.watchware.mp3.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility class for extracting media metadata including album art
 */
object MediaMetadataUtil {
    // Cache for album art bitmaps to avoid repeated extraction
    private val albumArtCache = LruCache<String, ImageBitmap?>(100) // Cache up to 100 album arts
    
    // Cache for directory sizes to avoid repeated calculation
    private val directorySizeCache = LruCache<String, Long>(200) // Cache up to 200 directory sizes
    
    /**
     * Load album art from an audio file
     * @param context Context to access files
     * @param audioFilePath Path to the audio file
     * @return ImageBitmap containing the album art, or null if no art is found
     */
    suspend fun loadAlbumArt(context: Context, audioFilePath: String): ImageBitmap? {
        // Check cache first
        albumArtCache.get(audioFilePath)?.let { return it }
        
        // If not in cache, extract it
        return withContext(Dispatchers.IO) {
            try {
                val file = File(audioFilePath)
                if (!file.exists()) {
                    // Cache the null result too to avoid checking non-existent files
                    albumArtCache.put(audioFilePath, null)
                    return@withContext null
                }
                
                // Use MediaMetadataRetriever to extract embedded album art
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFilePath)
                    
                    // Attempt to extract the embedded picture
                    val embeddedPicture = retriever.embeddedPicture
                    
                    if (embeddedPicture != null) {
                        // Convert the raw artwork data to a bitmap
                        val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                        val imageBitmap = bitmap?.asImageBitmap()
                        
                        // Cache the result
                        albumArtCache.put(audioFilePath, imageBitmap)
                        return@withContext imageBitmap
                    } else {
                        // Cache the null result too
                        albumArtCache.put(audioFilePath, null)
                        return@withContext null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Cache the null result for faster failure next time
                    albumArtCache.put(audioFilePath, null)
                    return@withContext null
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Cache the null result for faster failure next time
                albumArtCache.put(audioFilePath, null)
                return@withContext null
            }
        }
    }
    
    /**
     * Calculate the size of a directory in megabytes
     * @param directoryPath Path to the directory
     * @return Size in MB as a formatted string
     */
    fun calculateDirectorySize(directoryPath: String): String {
        return try {
            val sizeInBytes = getFolderSize(directoryPath)
            val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
            
            // Format to 1 decimal place if less than 10MB, otherwise no decimal places
            if (sizeInMB < 10) {
                String.format("%.1f MB", sizeInMB)
            } else {
                String.format("%.0f MB", sizeInMB)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "? MB"
        }
    }
    
    /**
     * Get folder size with caching
     */
    fun getFolderSize(directoryPath: String): Long {
        // Check cache first
        directorySizeCache.get(directoryPath)?.let { return it }
        
        // If not in cache, calculate it
        val directory = File(directoryPath)
        val size = calculateFolderSize(directory)
        
        // Cache the result
        directorySizeCache.put(directoryPath, size)
        
        return size
    }
    
    /**
     * Recursively calculate folder size in bytes
     */
    private fun calculateFolderSize(directory: File): Long {
        var size: Long = 0
        
        try {
            // List all files in the directory
            val files = directory.listFiles()
            
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        // Add file size
                        size += file.length()
                    } else if (file.isDirectory) {
                        // Check cache for subdirectory size
                        val subdirPath = file.absolutePath
                        val cachedSize = directorySizeCache.get(subdirPath)
                        
                        if (cachedSize != null) {
                            size += cachedSize
                        } else {
                            // Calculate subdirectory size recursively
                            val subdirSize = calculateFolderSize(file)
                            // Cache the subdirectory size
                            directorySizeCache.put(subdirPath, subdirSize)
                            size += subdirSize
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return size
    }
}
