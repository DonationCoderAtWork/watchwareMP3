package com.watchware.mp3.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.watchware.mp3.data.model.MediaItem
import com.watchware.mp3.util.GsonHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.net.Uri

/**
 * Service for handling audio playback functionality
 */
class AudioPlayerService(private val context: Context) {

    // Add a companion object with constants
    companion object {
        private const val PREFS_NAME = "MediaPlayerPrefs"
        private const val KEY_LAST_PLAYED_PATH = "lastPlayedPath" 
        private const val KEY_LAST_PLAYED_NAME = "lastPlayedName"
        private const val KEY_LAST_PLAYED_MIME_TYPE = "lastPlayedMimeType"
        private const val KEY_LAST_PLAYLIST_DIRECTORY = "lastPlaylistDirectory"
        private const val KEY_LAST_PLAYLIST = "lastPlaylist"
        private const val KEY_LAST_PLAYLIST_INDEX = "lastPlaylistIndex"
        private const val PROGRESS_UPDATE_INTERVAL = 200L
    }

    private var player: ExoPlayer? = null
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Media Session for handling media button events from Bluetooth headsets
    private var mediaSession: MediaSession? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentMediaItem = MutableStateFlow<MediaItem.AudioFile?>(null)
    val currentMediaItem: StateFlow<MediaItem.AudioFile?> = _currentMediaItem.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    // List of audio files in the current directory
    private val _playlistItems = MutableStateFlow<List<MediaItem.AudioFile>>(emptyList())
    val playlistItems: StateFlow<List<MediaItem.AudioFile>> = _playlistItems.asStateFlow()
    
    // Original ordered playlist (for restoring from shuffle)
    private var _originalOrderedPlaylist = listOf<MediaItem.AudioFile>()
    
    // Current index in the playlist
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    // Embedded artwork
    private val _embeddedArtwork = MutableStateFlow<Bitmap?>(null)
    val embeddedArtwork: StateFlow<Bitmap?> = _embeddedArtwork.asStateFlow()
    
    // Volume control
    private val _volume = MutableStateFlow(getSystemVolume())
    val mediaVolume: StateFlow<Float> = _volume.asStateFlow()
    
    private val _maxVolume = MutableStateFlow(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat())
    val maxVolume: StateFlow<Float> = _maxVolume.asStateFlow()
    
    // Progress update handler and runnable
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            // Schedule the next update if still playing
            if (_isPlaying.value) {
                progressUpdateHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
            }
        }
    }
    
    init {
        initializePlayer()
        initializeMediaSession()
        // Don't automatically restore last played song, it will be done by the ViewModel
    }
    
    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        
                        // Start or stop progress updates based on playback state
                        if (isPlaying) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateProgress()
                        
                        // Auto-advance to next song when playback ends
                        if (playbackState == Player.STATE_ENDED) {
                            val hasMoreSongs = playNextSong()
                            // If no more songs in playlist, reset to beginning of current song
                            if (!hasMoreSongs) {
                                seekToPosition(0f)
                                pause()
                            }
                        }
                    }
                    
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        // Extract the artwork bitmap from the metadata
                        _embeddedArtwork.value = mediaMetadata.artworkData?.let { artworkData ->
                            BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                        }
                    }
                })
            }
        }
    }
    
    private fun initializeMediaSession() {
        player?.let { exoPlayer ->
            // Create a media session that will handle Bluetooth headset controls
            mediaSession = MediaSession.Builder(context, exoPlayer)
                .setId("WatchwareMP3")
                .build()
        }
    }
    
    private fun updateMediaMetadata(audioFile: MediaItem.AudioFile) {
        // This method is no longer needed as we handle metadata in createMediaItem
        // and we can't update metadata for an existing MediaItem in Media3
    }
    
    /**
     * Create a MediaItem from an AudioFile
     */
    private fun createMediaItem(audioFile: MediaItem.AudioFile): ExoMediaItem {
        // Create a media item using the Uri directly
        return ExoMediaItem.fromUri(Uri.parse(audioFile.path))
    }
    
    private fun startProgressUpdates() {
        // Remove any existing callbacks to avoid duplicates
        stopProgressUpdates()
        // Start periodic updates
        progressUpdateHandler.post(progressUpdateRunnable)
    }
    
    private fun stopProgressUpdates() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }
    
    private fun getSystemVolume(): Float {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return currentVolume.toFloat() / maxVolume.toFloat()
    }
    
    fun setVolume(volumePercent: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeIndex = (volumePercent * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex, 0)
        _volume.value = volumePercent
    }
    
    fun refreshVolume() {
        _volume.value = getSystemVolume()
    }
    
    /**
     * Sets the playlist and optionally starts playing at the given index
     */
    fun setPlaylist(audioFiles: List<MediaItem.AudioFile>, startIndex: Int = 0) {
        _playlistItems.value = audioFiles
        
        // Store original ordered playlist for restoring from shuffle
        _originalOrderedPlaylist = audioFiles.toList()
        
        if (audioFiles.isNotEmpty() && startIndex in audioFiles.indices) {
            _currentIndex.value = startIndex
            playAudio(audioFiles[startIndex])
        }
        
        // Save the playlist whenever it's set
        savePlaylist(audioFiles, startIndex)
    }
    
    /**
     * Save the playlist to SharedPreferences
     */
    private fun savePlaylist(playlist: List<MediaItem.AudioFile>, currentIndex: Int) {
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
     * A public method that can be called from the ViewModel to play an audio file
     * This delegates to the internal playAudio method
     */
    fun playAudioFile(audioFile: MediaItem.AudioFile) {
        playAudio(audioFile)
    }
    
    /**
     * Internal method to play an audio file
     */
    fun playAudio(audioFile: MediaItem.AudioFile) {
        _currentMediaItem.value = audioFile
        
        // Find and update current index in playlist
        val index = _playlistItems.value.indexOf(audioFile)
        if (index != -1) {
            _currentIndex.value = index
        }
        
        player?.let { exoPlayer ->
            // Create a media item
            val mediaItem = createMediaItem(audioFile)
            
            // Set the media item, prepare, and play
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            
            // Extract any embedded artwork
            extractArtwork(audioFile.path)
            
            // Save as the last played song
            saveLastPlayedSong(audioFile)
        }
    }
    
    fun togglePlayPause() {
        player?.let { exoPlayer ->
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
        }
    }
    
    fun pause() {
        player?.pause()
    }
    
    private fun resumePlay() {
        player?.play()
    }
    
    fun playNextSong(): Boolean {
        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < _playlistItems.value.size) {
            playAudio(_playlistItems.value[nextIndex])
            true
        } else {
            false
        }
    }
    
    fun playPreviousSong(): Boolean {
        val previousIndex = _currentIndex.value - 1
        return if (previousIndex >= 0) {
            playAudio(_playlistItems.value[previousIndex])
            true
        } else {
            false
        }
    }
    
    fun seekToPosition(positionFraction: Float) {
        player?.let { exoPlayer ->
            val seekPosition = (exoPlayer.duration * positionFraction).toLong()
            exoPlayer.seekTo(seekPosition)
            updateProgress()
        }
    }
    
    private fun updateProgress() {
        player?.let { exoPlayer ->
            val duration = exoPlayer.duration
            val currentPosition = exoPlayer.currentPosition
            
            _duration.value = duration
            _currentPosition.value = currentPosition
            
            if (duration > 0) {
                _progress.value = currentPosition.toFloat() / duration.toFloat()
            }
        }
    }
    
    /**
     * Format milliseconds to minutes:seconds format
     */
    fun formatTime(millis: Long): String {
        if (millis <= 0) return "0:00"
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
    
    /**
     * Shuffles the current playlist but keeps the currently playing song as the first item
     */
    fun shufflePlaylist() {
        val currentPlaylist = _playlistItems.value
        if (currentPlaylist.size <= 1) return
        
        val currentSong = _currentMediaItem.value ?: return
        
        // Create a mutable list for shuffling
        val shuffledList = currentPlaylist.toMutableList()
        
        // Remove current song from the list that will be shuffled
        shuffledList.remove(currentSong)
        
        // Shuffle the remaining songs
        shuffledList.shuffle()
        
        // Re-add the current song at the beginning
        shuffledList.add(0, currentSong)
        
        // Update the playlist
        _playlistItems.value = shuffledList
        _currentIndex.value = 0
        
        // Save the shuffled playlist
        savePlaylist(shuffledList, 0)
    }
    
    /**
     * Restores the playlist to alphabetical order
     */
    fun restoreAlphabeticalOrder() {
        val currentSong = _currentMediaItem.value ?: return
        
        // Make sure we have the original playlist
        if (_originalOrderedPlaylist.isEmpty()) {
            // If original playlist is empty, there's nothing to restore
            return
        }
        
        // Sort the original playlist alphabetically by name
        val sortedList = _originalOrderedPlaylist.toList().sortedBy { 
            it.name.lowercase().trim() 
        }
        
        // Find the current song in the sorted list by matching paths
        val newIndex = sortedList.indexOfFirst { it.path == currentSong.path }
        
        // Update the playlist with the sorted list
        _playlistItems.value = sortedList
        
        // Set the current index to the position of the current song, or 0 if not found
        _currentIndex.value = if (newIndex >= 0) newIndex else 0
        
        // Log for debugging
        android.util.Log.d("AudioPlayerService", "Restored alphabetical order: ${sortedList.map { it.name }}")
        
        // Save the sorted playlist
        savePlaylist(sortedList, _currentIndex.value)
    }
    
    fun release() {
        stopProgressUpdates()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }
    
    /**
     * Extract artwork from the audio file metadata
     */
    private fun extractArtwork(filePath: String) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            try {
                // Try to get embedded artwork with explicit null checking and casting
                val embeddedPicture: ByteArray? = retriever.getEmbeddedPicture()
                if (embeddedPicture != null) {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(
                            embeddedPicture, 
                            0, 
                            embeddedPicture.size
                        )
                        _embeddedArtwork.value = bitmap
                    } catch (e: Exception) {
                        _embeddedArtwork.value = null
                    }
                } else {
                    _embeddedArtwork.value = null
                }
            } catch (e: Exception) {
                _embeddedArtwork.value = null
            }
            
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
            _embeddedArtwork.value = null
        }
    }
    
    /**
     * Save last played song information to SharedPreferences
     */
    private fun saveLastPlayedSong(audioFile: MediaItem.AudioFile) {
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
        
        // Also save the entire playlist and current index
        _currentIndex.value.let { index ->
            if (index != -1) {
                savePlaylist(_playlistItems.value, index)
            }
        }
    }

    /**
     * Restore the last played song from SharedPreferences
     * This doesn't automatically play the song, just sets it as current
     * Returns true if successful, false otherwise
     */
    fun restoreLastPlayedSong(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPath = prefs.getString(KEY_LAST_PLAYED_PATH, null)
        val lastName = prefs.getString(KEY_LAST_PLAYED_NAME, null)
        val lastMimeType = prefs.getString(KEY_LAST_PLAYED_MIME_TYPE, "audio/mpeg")
        
        // Try to restore the saved playlist first
        val playlistJson = prefs.getString(KEY_LAST_PLAYLIST, null)
        val lastIndex = prefs.getInt(KEY_LAST_PLAYLIST_INDEX, -1)
        
        var success = false
        
        // First attempt: Try to restore from saved playlist JSON
        if (playlistJson != null && lastIndex != -1) {
            try {
                // Use our improved playlist deserialization method
                val playlist = GsonHelper.deserializePlaylist(playlistJson)
                
                if (playlist.isNotEmpty() && lastIndex < playlist.size) {
                    // Set the playlist and current index
                    _playlistItems.value = playlist
                    _currentIndex.value = lastIndex
                    
                    // Get the current item based on saved index
                    val currentAudioFile = playlist[lastIndex]
                    _currentMediaItem.value = currentAudioFile
                    
                    // Prepare the player with the current media item
                    try {
                        val uri = Uri.parse(currentAudioFile.path)
                        val mediaItem = ExoMediaItem.fromUri(uri)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        
                        // Extract artwork if available
                        extractArtwork(currentAudioFile.path)
                        
                        success = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue to fallback method
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to fallback method
            }
        }
        
        // Second attempt (fallback): Try to restore from last played song info
        if (!success && lastPath != null && lastName != null) {
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
                val directory = java.io.File(lastDirectory)
                if (directory.exists() && directory.isDirectory) {
                    // Use content resolver to get all audio files in the same directory
                    try {
                        val contentResolver = context.contentResolver
                        val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        val selection = "${android.provider.MediaStore.Audio.Media.DATA} LIKE ?"
                        val selectionArgs = arrayOf("$lastDirectory/%")
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(
                                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                                android.provider.MediaStore.Audio.Media.DATA,
                                android.provider.MediaStore.Audio.Media.MIME_TYPE
                            ),
                            selection,
                            selectionArgs,
                            "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} ASC"
                        )
                        
                        cursor?.use { c ->
                            val nameColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                            val pathColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                            val mimeTypeColumn = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.MIME_TYPE)
                            
                            val tempList = mutableListOf<MediaItem.AudioFile>()
                            
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
                            
                            audioFiles = tempList
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback to the file-based approach
                        audioFiles = directory.listFiles { file ->
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
                }
            }
            
            // If we couldn't find any files in the directory, at least add the current file to the playlist
            if (audioFiles.isEmpty()) {
                audioFiles = listOf(audioFile)
            }
            
            // Always ensure we have a valid playlist
            _playlistItems.value = audioFiles
            
            // Find the index of the restored song in the playlist
            val index = audioFiles.indexOfFirst { it.path == lastPath }
            
            // If we found the index, update it; otherwise, set it to 0 if we have files
            if (index != -1) {
                _currentIndex.value = index
            } else if (audioFiles.isNotEmpty()) {
                _currentIndex.value = 0
            }
            
            // Only set as current, don't play
            _currentMediaItem.value = audioFile
            
            // Create MediaItem from URI for ExoPlayer, but don't start playback
            try {
                val uri = Uri.parse(lastPath)
                val mediaItem = ExoMediaItem.fromUri(uri)
                player?.setMediaItem(mediaItem)
                player?.prepare()
                
                // Extract artwork if available
                extractArtwork(lastPath)
                
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                // Failed both attempts
            }
        }
        
        // Save the restored playlist again to ensure it's in the correct format for next time
        if (success) {
            _currentIndex.value.let { index ->
                if (index != -1 && _playlistItems.value.isNotEmpty()) {
                    savePlaylist(_playlistItems.value, index)
                }
            }
        }
        
        return success
    }
}
