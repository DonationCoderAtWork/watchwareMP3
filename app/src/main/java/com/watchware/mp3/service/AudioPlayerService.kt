package com.watchware.mp3.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.watchware.mp3.data.model.MediaItem
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
        private const val PROGRESS_UPDATE_INTERVAL = 200L
    }

    private var player: ExoPlayer? = null
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Media Session for handling media button events from Bluetooth headsets
    private var mediaSession: MediaSession? = null
    
    // Volume change receiver to detect system volume changes (Bluetooth/OS)
    private val volumeChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                // Update our volume state to match system volume
                refreshVolume()
            }
        }
    }
    
    // Lazy-initialized persistence manager
    private val persistenceManager by lazy { AudioPlayerPersistenceManager(context) }
    
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
    
    // Track if playlist is shuffled
    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()
    
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
        registerVolumeChangeReceiver()
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
     * Register a broadcast receiver to listen for volume changes made outside the app
     */
    private fun registerVolumeChangeReceiver() {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeChangeReceiver, filter)
    }
    
    /**
     * Sets the playlist and optionally starts playing at the given index
     */
    fun setPlaylist(audioFiles: List<MediaItem.AudioFile>, startIndex: Int = 0) {
        _playlistItems.value = audioFiles
        
        // Store original ordered playlist for restoring from shuffle
        _originalOrderedPlaylist = audioFiles.toList()
        
        // Reset shuffle state when setting a new playlist
        _isShuffled.value = false
        
        if (audioFiles.isNotEmpty() && startIndex in audioFiles.indices) {
            _currentIndex.value = startIndex
            playAudio(audioFiles[startIndex])
        }
        
        // Save the playlist whenever it's set
        persistenceManager.savePlaylist(audioFiles, startIndex, _isShuffled.value)
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
            persistenceManager.saveLastPlayedSong(audioFile)
            
            // Also save the playlist position
            _currentIndex.value.let { currentIndex ->
                if (currentIndex != -1) {
                    persistenceManager.savePlaylist(_playlistItems.value, currentIndex, _isShuffled.value)
                }
            }
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
        
        // Set shuffle state to true
        _isShuffled.value = true
        
        // Save the shuffled playlist along with shuffle state
        persistenceManager.savePlaylist(shuffledList, 0, true)
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
        
        // Set shuffle state to false
        _isShuffled.value = false
        
        // Log for debugging
        android.util.Log.d("AudioPlayerService", "Restored alphabetical order: ${sortedList.map { it.name }}")
        
        // Save the sorted playlist with shuffle state
        persistenceManager.savePlaylist(sortedList, _currentIndex.value, false)
    }
    
    /**
     * Restore the last played song from SharedPreferences
     * This doesn't automatically play the song, just sets it as current
     * Returns true if successful, false otherwise
     */
    fun restoreLastPlayedSong(): Boolean {
        val lastPlaybackState = persistenceManager.restoreLastPlayedSong() ?: return false
        
        try {
            // Set the playlist
            _playlistItems.value = lastPlaybackState.playlist
            // Also save the original ordered playlist
            _originalOrderedPlaylist = lastPlaybackState.playlist.toList()
            
            // Set the current index
            _currentIndex.value = lastPlaybackState.currentIndex
            
            // Set the current media item
            _currentMediaItem.value = lastPlaybackState.audioFile
            
            // Set the shuffle state
            _isShuffled.value = lastPlaybackState.isShuffled
            
            // Prepare the player with the current media item
            val uri = Uri.parse(lastPlaybackState.audioFile.path)
            val mediaItem = ExoMediaItem.fromUri(uri)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            
            // Extract artwork if available
            extractArtwork(lastPlaybackState.audioFile.path)
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
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
    
    fun release() {
        stopProgressUpdates()
        // Unregister volume change receiver
        try {
            context.unregisterReceiver(volumeChangeReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }
}
