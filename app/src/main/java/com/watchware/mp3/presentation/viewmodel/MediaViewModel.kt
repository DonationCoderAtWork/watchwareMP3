package com.watchware.mp3.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watchware.mp3.data.model.MediaItem
import com.watchware.mp3.data.repository.MediaRepository
import com.watchware.mp3.service.AudioPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = MediaRepository(application)
    private val audioPlayerService = AudioPlayerService(application)
    
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()
    
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()
    
    private val _navigationStack = MutableStateFlow<List<MediaItem.Folder>>(emptyList())
    val navigationStack: StateFlow<List<MediaItem.Folder>> = _navigationStack.asStateFlow()
    
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()
    
    // Player related states
    private val _isPlayerActive = MutableStateFlow(false)
    val isPlayerActive: StateFlow<Boolean> = _isPlayerActive.asStateFlow()
    
    // Shuffle mode state
    private val _isShuffleMode = MutableStateFlow(false)
    val isShuffleMode: StateFlow<Boolean> = _isShuffleMode.asStateFlow()
    
    val currentAudioFile = audioPlayerService.currentMediaItem
    val isPlaying = audioPlayerService.isPlaying
    val playbackProgress = audioPlayerService.progress
    val currentPosition = audioPlayerService.currentPosition
    val duration = audioPlayerService.duration
    val currentPlaylistIndex = audioPlayerService.currentIndex
    val playlistItems = audioPlayerService.playlistItems
    
    // New properties for artwork and volume
    val embeddedArtwork = audioPlayerService.embeddedArtwork
    val mediaVolume = audioPlayerService.mediaVolume
    
    init {
        _permissionsGranted.value = repository.hasRequiredPermissions()
        if (_permissionsGranted.value) {
            scanMedia()
            
            // Restore the last played song and activate player if successful
            if (audioPlayerService.restoreLastPlayedSong()) {
                _isPlayerActive.value = true
            }
        }
    }
    
    /**
     * Called when permissions are granted
     */
    fun onPermissionsGranted() {
        _permissionsGranted.value = true
        scanMedia()
    }
    
    /**
     * Scans the device for media files
     */
    fun scanMedia() {
        _uiState.value = MediaUiState.Loading
        viewModelScope.launch {
            try {
                val rootFolders = repository.scanForAudioFiles()
                if (rootFolders.isEmpty()) {
                    _uiState.value = MediaUiState.Empty
                } else {
                    // Reset navigation state when scanning
                    _navigationStack.value = emptyList()
                    _currentPath.value = null
                    _uiState.value = MediaUiState.Success(rootFolders)
                }
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error("Failed to scan for media: ${e.message}")
            }
        }
    }
    
    /**
     * Navigates to a specific folder
     */
    fun navigateToFolder(folder: MediaItem.Folder) {
        val currentNavStack = _navigationStack.value.toMutableList()
        currentNavStack.add(folder)
        _navigationStack.value = currentNavStack
        _currentPath.value = folder.path
        _uiState.value = MediaUiState.Success(listOf(folder))
    }
    
    /**
     * Navigates back to the parent folder
     * @return True if navigation was handled, false if we're already at the root
     */
    fun navigateBack(): Boolean {
        val currentNavStack = _navigationStack.value.toMutableList()
        if (currentNavStack.isEmpty()) {
            return false
        }
        
        // Remove the current folder
        currentNavStack.removeAt(currentNavStack.size - 1)
        _navigationStack.value = currentNavStack
        
        if (currentNavStack.isEmpty()) {
            // If we're back at the root, reload all root folders
            _currentPath.value = null
            scanMedia()
        } else {
            // Otherwise, display the parent folder
            val parentFolder = currentNavStack.last()
            _currentPath.value = parentFolder.path
            _uiState.value = MediaUiState.Success(listOf(parentFolder))
        }
        
        return true
    }
    
    /**
     * Plays an audio file
     */
    fun playAudioFile(audioFile: MediaItem.AudioFile) {
        // Find all audio files in the current folder
        val currentFolderAudioFiles = when (val state = _uiState.value) {
            is MediaUiState.Success -> {
                if (state.folders.isNotEmpty()) {
                    // Sort audio files alphabetically by name before setting up the playlist
                    state.folders[0].children.filterIsInstance<MediaItem.AudioFile>()
                        .sortedBy { it.name.lowercase() }
                } else emptyList()
            }
            else -> emptyList()
        }
        
        // Set playlist and start playing the selected file
        val startIndex = currentFolderAudioFiles.indexOf(audioFile).coerceAtLeast(0)
        audioPlayerService.setPlaylist(currentFolderAudioFiles, startIndex)
        
        _isPlayerActive.value = true
    }
    
    /**
     * Skip to next song in playlist
     */
    fun playNextSong() {
        audioPlayerService.playNextSong()
    }
    
    /**
     * Skip to previous song in playlist
     */
    fun playPreviousSong() {
        audioPlayerService.playPreviousSong()
    }
    
    /**
     * Seek to a specific position in the currently playing track
     */
    fun seekToPosition(position: Float) {
        audioPlayerService.seekToPosition(position)
    }
    
    /**
     * Set the system media volume
     */
    fun setVolume(volume: Float) {
        audioPlayerService.setVolume(volume)
    }
    
    /**
     * Update the volume value from the system
     * This is called internally when needed
     */
    private fun refreshVolume() {
        audioPlayerService.refreshVolume()
    }
    
    /**
     * Format time in milliseconds to readable string
     */
    fun formatTime(timeMs: Long): String {
        return audioPlayerService.formatTime(timeMs)
    }
    
    /**
     * Toggles play/pause state
     */
    fun togglePlayPause() {
        audioPlayerService.togglePlayPause()
    }
    
    /**
     * Opens the player screen
     */
    fun openPlayer() {
        _isPlayerActive.value = true
    }
    
    /**
     * Closes the player
     */
    fun closePlayer() {
        _isPlayerActive.value = false
        // Removed audioPlayerService.pause() to allow continuous playback when switching views
    }
    
    /**
     * Toggles shuffle mode for the playlist
     * When enabled, shuffles the playlist
     * When disabled, restores the playlist to alphabetical order
     * @return the new shuffle state
     */
    fun toggleShuffleMode(): Boolean {
        _isShuffleMode.value = !_isShuffleMode.value
        
        if (_isShuffleMode.value) {
            // Shuffle the playlist
            audioPlayerService.shufflePlaylist()
        } else {
            // Restore original order (alphabetical by name)
            audioPlayerService.restoreAlphabeticalOrder()
        }
        
        return _isShuffleMode.value
    }
    
    override fun onCleared() {
        super.onCleared()
        audioPlayerService.release()
    }
}

/**
 * UI state for the media browser
 */
sealed class MediaUiState {
    object Loading : MediaUiState()
    object Empty : MediaUiState()
    data class Success(val folders: List<MediaItem.Folder>) : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}
