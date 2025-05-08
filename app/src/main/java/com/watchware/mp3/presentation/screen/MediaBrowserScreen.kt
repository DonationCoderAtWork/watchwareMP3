package com.watchware.mp3.presentation.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.watchware.mp3.R
import com.watchware.mp3.data.model.MediaItem
import com.watchware.mp3.presentation.screen.components.AppTitleHeader
import com.watchware.mp3.presentation.screen.components.CurrentlyPlayingLabelBar
import com.watchware.mp3.presentation.screen.components.MediaItemRow
import com.watchware.mp3.presentation.screen.components.NavigationHeader
import com.watchware.mp3.presentation.screen.components.PlayerButtonsBar
import com.watchware.mp3.presentation.viewmodel.MediaUiState
import com.watchware.mp3.presentation.viewmodel.MediaViewModel

@Composable
fun MediaBrowserScreen(
    viewModel: MediaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    val navigationStack by viewModel.navigationStack.collectAsState()
    
    // Set up permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionsGranted()
        }
    }
    
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
    
    val listState = rememberScalingLazyListState()
    
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = {
            PositionIndicator(
                scalingLazyListState = listState
            )
        }
    ) {
        when {
            !permissionsGranted -> PermissionRequestScreen(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }
            )
            else -> MediaContentScreen(
                uiState = uiState,
                listState = listState,
                onItemClick = { mediaItem ->
                    when (mediaItem) {
                        is MediaItem.Folder -> viewModel.navigateToFolder(mediaItem)
                        is MediaItem.AudioFile -> {
                            viewModel.playAudioFile(mediaItem)
                        }
                    }
                },
                onRefresh = { viewModel.scanMedia() },
                navigationStack = navigationStack,
                onNavigateBack = { viewModel.navigateBack() },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Audio File Access Required",
            style = MaterialTheme.typography.title2,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This app needs permission to access audio files on your device.",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun MediaContentScreen(
    uiState: MediaUiState,
    listState: ScalingLazyListState,
    onItemClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    navigationStack: List<MediaItem.Folder>,
    onNavigateBack: () -> Unit,
    viewModel: MediaViewModel
) {
    val currentAudioFile by viewModel.currentAudioFile.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background logo with 10% opacity (90% transparent)
        Image(
            painter = painterResource(id = R.drawable.watchware_logo_inverted),
            contentDescription = null, // Decorative element doesn't need content description
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f), // 10% opacity (90% transparent)
            contentScale = ContentScale.Fit
        )
        
        when (uiState) {
            is MediaUiState.Loading -> {
                CircularProgressIndicator()
            }
            is MediaUiState.Empty -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No audio files found",
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            modifier = Modifier.size(ButtonDefaults.DefaultIconSize)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
            }
            is MediaUiState.Error -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error loading files",
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = "Retry",
                            modifier = Modifier.size(ButtonDefaults.DefaultIconSize)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
            is MediaUiState.Success -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Show navigation header with back button if we're not at the root
                    if (navigationStack.isNotEmpty()) {
                        NavigationHeader(
                            currentFolder = navigationStack.lastOrNull()?.name ?: "Root",
                            onNavigateBack = onNavigateBack
                        )
                    } else {
                        // Show app title header when at the root
                        AppTitleHeader()
                    }
                    
                    ScalingLazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 16.dp, 
                            end = 16.dp, 
                            top = if (navigationStack.isEmpty()) 16.dp else 8.dp,
                            bottom = 16.dp
                        ),
                        state = listState
                    ) {
                        for (folder in uiState.folders) {
                            // Show folder's subfolders first, sorted alphabetically
                            val sortedSubFolders = folder.children
                                .filterIsInstance<MediaItem.Folder>()
                                .sortedBy { it.name.lowercase() }
                                
                            if (sortedSubFolders.isNotEmpty()) {
                                for (subFolder in sortedSubFolders) {
                                    item {
                                        MediaItemRow(
                                            mediaItem = subFolder,
                                            onClick = { onItemClick(subFolder) }
                                        )
                                    }
                                }
                            }
                            
                            // Then show folder's audio files sorted alphabetically by name
                            val sortedAudioFiles = folder.children
                                .filterIsInstance<MediaItem.AudioFile>()
                                .sortedBy { it.name.lowercase() }
                            
                            if (sortedAudioFiles.isNotEmpty()) {
                                for (audioFile in sortedAudioFiles) {
                                    item {
                                        MediaItemRow(
                                            mediaItem = audioFile,
                                            onClick = { onItemClick(audioFile) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Add currently playing label bar - only show when a song is playing
                    if (currentAudioFile != null) {
                        CurrentlyPlayingLabelBar(
                            currentAudioFile = currentAudioFile!!,
                            onOpenPlayer = { viewModel.openPlayer() }
                        )
                    }
                    
                    // Add folder view player buttons bar
                    PlayerButtonsBar(
                        isPlaying = isPlaying,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.playNextSong() }
                    )
                }
            }
        }
    }
}
