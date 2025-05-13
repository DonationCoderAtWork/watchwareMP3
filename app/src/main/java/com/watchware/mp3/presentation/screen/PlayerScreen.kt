package com.watchware.mp3.presentation.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.watchware.mp3.R
import com.watchware.mp3.presentation.viewmodel.MediaViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import com.watchware.mp3.presentation.screen.MusicVisualizerView as MusicVisualizerViewScreen

@Composable
fun PlayerScreen(
    viewModel: MediaViewModel,
    onBackPressed: () -> Unit
) {
    val currentAudioFile by viewModel.currentAudioFile.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playlistIndex by viewModel.currentPlaylistIndex.collectAsState()
    val playlistItems by viewModel.playlistItems.collectAsState()
    val embeddedArtwork by viewModel.embeddedArtwork.collectAsState()
    val volume by viewModel.mediaVolume.collectAsState()
    
    // Track which view to show (player or visualization)
    var showVisualization by remember { mutableStateOf(false) }
    
    // Track if user is currently interacting with the slider
    var isInteractingWithSlider by remember { mutableStateOf(false) }
    var userSliderPosition by remember { mutableStateOf(0f) }
    
    // Track if user is currently interacting with the volume slider
    var isInteractingWithVolumeSlider by remember { mutableStateOf(false) }
    var userVolumePosition by remember { mutableStateOf(volume) }
    
    // For mute functionality
    var previousVolume by remember { mutableStateOf(0.5f) }
    var isMuted by remember { mutableStateOf(false) }
    
    // For auto-fading controls after inactivity
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var controlsAlpha by remember { mutableStateOf(1f) }
    
    // Background image opacity - increases as controls fade
    var backgroundAlpha by remember { mutableStateOf(0.3f) }
    
    // Function to blend two colors with a specified ratio
    fun blendColors(color1: Color, color2: Color, ratio2: Float): Color {
        val ratio1 = 1.0f - ratio2
        return Color(
            red = color1.red * ratio1 + color2.red * ratio2,
            green = color1.green * ratio1 + color2.green * ratio2,
            blue = color1.blue * ratio1 + color2.blue * ratio2,
            alpha = 1.0f
        )
    }
    
    // Update last interaction time whenever user interacts with any control
    fun updateInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
        controlsAlpha = 1f
        backgroundAlpha = 0.3f
    }
    
    // Function to toggle mute
    fun toggleMute() {
        if (isMuted) {
            // Unmute - restore previous volume
            viewModel.setVolume(previousVolume)
            isMuted = false
        } else {
            // Mute - save current volume and set to 0
            previousVolume = volume
            viewModel.setVolume(0f)
            isMuted = true
        }
        updateInteractionTime()
    }
    
    // Effect to fade out controls after period of inactivity
    LaunchedEffect(Unit) {
        coroutineScope {
            while (true) {
                delay(500) // Check every half second
                val currentTime = System.currentTimeMillis()
                val timeSinceLastInteraction = currentTime - lastInteractionTime
                
                // If more than 5 seconds with no interaction, start fading controls
                if (timeSinceLastInteraction > 5000) {
                    // Target alpha is 0.4 (60% transparent)
                    val targetAlpha = 0.4f
                    
                    // Target background alpha is 0.6 (brighter)
                    val targetBackgroundAlpha = 0.6f
                    
                    // Only update if we're not already at target alpha
                    if (controlsAlpha > targetAlpha) {
                        // Gradually reduce alpha
                        controlsAlpha = (controlsAlpha - 0.05f).coerceAtLeast(targetAlpha)
                    }
                    
                    // Gradually increase background brightness
                    if (backgroundAlpha < targetBackgroundAlpha) {
                        backgroundAlpha = (backgroundAlpha + 0.02f).coerceAtMost(targetBackgroundAlpha)
                    }
                }
            }
        }
    }
    
    // For scrolling title
    val scrollState = rememberScrollState()
    val needsScrolling = currentAudioFile?.name?.length ?: 0 > 20
    
    // Extract colors from album art for UI theming
    var dominantColor by remember { mutableStateOf(Color(0xFF3C3F41)) } // Default dark gray
    var accentColor by remember { mutableStateOf(Color(0xFF6ea9ff)) }    // Default blue accent
    var textColor by remember { mutableStateOf(Color.White) }            // Default white text
    
    // Extract colors from artwork if available
    LaunchedEffect(embeddedArtwork) {
        coroutineScope {
            embeddedArtwork?.let { bitmap ->
                val palette = Palette.from(bitmap).generate()
                
                // Default colors
                val defaultAccentColor = Color(0xFF6ea9ff)
                val defaultDominantColor = Color(0xFF3C3F41)
                
                // Extract vibrant color for accent with reduced intensity (30%)
                palette.vibrantSwatch?.let { vibrantSwatch ->
                    val extractedColor = Color(vibrantSwatch.rgb)
                    // Blend 30% of extracted color with 70% of default
                    accentColor = blendColors(defaultAccentColor, extractedColor, 0.3f)
                    // Determine if we need dark text on this color
                    textColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                }
                
                // Extract dominant color for buttons with reduced intensity (30%)
                palette.dominantSwatch?.let { dominantSwatch ->
                    val extractedColor = Color(dominantSwatch.rgb)
                    // Blend 30% of extracted color with 70% of default
                    dominantColor = blendColors(defaultDominantColor, extractedColor, 0.3f)
                }
            }
        }
    }
    
    // Auto-scroll effect for long titles
    LaunchedEffect(currentAudioFile, needsScrolling) {
        coroutineScope {
            if (needsScrolling) {
                // Reset scroll position when song changes
                scrollState.scrollTo(0)
                
                // Add a small delay before starting to scroll
                delay(1500)
                
                // Begin infinite scrolling animation
                while (true) {
                    // Scroll to the end
                    scrollState.animateScrollTo(
                        value = scrollState.maxValue,
                        animationSpec = tween(
                            durationMillis = 7000,
                            easing = LinearEasing
                        )
                    )
                    
                    // Small pause at the end
                    delay(1000)
                    
                    // Scroll back to the beginning
                    scrollState.animateScrollTo(
                        value = 0,
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = LinearEasing
                        )
                    )
                    
                    // Pause at the beginning before restarting
                    delay(1500)
                }
            }
        }
    }
    
    // Create buttonShape with rounded corners for compact appearance
    val buttonShape = RoundedCornerShape(8.dp)
    
    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show embedded artwork as background if available
            embeddedArtwork?.let { artwork ->
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundAlpha), // Dynamic transparency based on controls
                    contentScale = ContentScale.Crop
                )
            }
            
            // Show either player controls or visualization based on state
            if (showVisualization) {
                MusicVisualizerViewScreen(
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    dominantColor = dominantColor,
                    textColor = textColor,
                    controlsAlpha = controlsAlpha,
                    onToggleView = {
                        showVisualization = false
                        updateInteractionTime()
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Display the audio file name as scrolling text if needed
                    currentAudioFile?.let { audioFile ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(scrollState, enabled = needsScrolling)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = audioFile.name,
                                    style = MaterialTheme.typography.title3,
                                    textAlign = TextAlign.Center,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    modifier = Modifier.alpha(controlsAlpha)
                                )
                                // Add extra space at the end for better scrolling
                                if (needsScrolling) {
                                    Spacer(modifier = Modifier.width(32.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Volume Control Slider - SMALLER HEIGHT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp) // Reduced padding
                                .alpha(controlsAlpha),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Minus button for decreasing volume
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_low),
                                contentDescription = "Decrease Volume",
                                modifier = Modifier
                                    .size(ButtonDefaults.SmallIconSize)
                                    .clickable { 
                                        // Decrease volume by 5% (0.05), but don't go below 0
                                        val newVolume = (volume - 0.05f).coerceAtLeast(0f)
                                        viewModel.setVolume(newVolume)
                                        // If we're at zero, we're muted
                                        if (newVolume <= 0f) {
                                            isMuted = true
                                        } else if (isMuted) {
                                            // If previously muted and now > 0, unmute
                                            isMuted = false
                                        }
                                        updateInteractionTime()
                                    },
                                tint = textColor
                            )
                            
                            // Box to contain both the volume percentage text and slider
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp), // Keep the same height as the slider
                                contentAlignment = Alignment.Center
                            ) {
                                // Volume percentage text in the background
                                Text(
                                    text = "${(if (isInteractingWithVolumeSlider) userVolumePosition else volume) * 100}%".split(".")[0],
                                    style = MaterialTheme.typography.body2.copy(
                                        textAlign = TextAlign.Center
                                    ),
                                    color = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                                
                                // Material3 Slider for volume control with themed colors
                                Slider(
                                    value = if (isInteractingWithVolumeSlider) userVolumePosition else volume,
                                    onValueChange = { 
                                        isInteractingWithVolumeSlider = true
                                        userVolumePosition = it
                                        // Update in real-time for immediate feedback
                                        viewModel.setVolume(it)
                                        // If volume is raised above 0, we're no longer muted
                                        if (it > 0f && isMuted) {
                                            isMuted = false
                                        }
                                        // Update interaction time
                                        updateInteractionTime()
                                    },
                                    onValueChangeFinished = {
                                        isInteractingWithVolumeSlider = false
                                        // If user set volume to zero, we consider it muted
                                        if (userVolumePosition <= 0f) {
                                            isMuted = true
                                        }
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp), // Make the slider smaller
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor.copy(alpha = 0.7f),
                                        inactiveTrackColor = accentColor.copy(alpha = 0.3f)
                                    )
                                )
                            }
                            
                            // Plus button for increasing volume
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_high),
                                contentDescription = "Increase Volume",
                                modifier = Modifier
                                    .size(ButtonDefaults.SmallIconSize)
                                    .clickable { 
                                        // Increase volume by 5% (0.05), but don't exceed 1
                                        val newVolume = (volume + 0.05f).coerceAtMost(1f)
                                        viewModel.setVolume(newVolume)
                                        // If volume is raised above 0, we're no longer muted
                                        if (newVolume > 0f && isMuted) {
                                            isMuted = false
                                        }
                                        updateInteractionTime()
                                    },
                                tint = textColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Rearranged progress controls - Time displays on the sides of the slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp) // Reduced padding
                                .alpha(controlsAlpha),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current position time - show user position while dragging, otherwise show actual position
                            Text(
                                text = if (isInteractingWithSlider) 
                                    viewModel.formatTime((userSliderPosition * duration).toLong())
                                else 
                                    viewModel.formatTime(currentPosition),
                                style = MaterialTheme.typography.body2,
                                color = textColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.width(36.dp)
                            )
                            
                            // Interactive progress bar with themed colors
                            Slider(
                                value = if (isInteractingWithSlider) userSliderPosition else progress,
                                onValueChange = { 
                                    isInteractingWithSlider = true
                                    userSliderPosition = it
                                    // Update interaction time
                                    updateInteractionTime()
                                },
                                onValueChangeFinished = {
                                    viewModel.seekToPosition(userSliderPosition)
                                    isInteractingWithSlider = false
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor.copy(alpha = 0.7f),
                                    inactiveTrackColor = accentColor.copy(alpha = 0.3f)
                                )
                            )
                            
                            // Total duration time
                            Text(
                                text = viewModel.formatTime(duration),
                                style = MaterialTheme.typography.body2,
                                color = textColor,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Playback controls - Now with consistently sized buttons and the shuffle button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(controlsAlpha),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Back button (to file browser) - Same size as other buttons
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(buttonShape)
                                    .background(dominantColor.copy(alpha = 0.7f))
                                    .clickable { 
                                        updateInteractionTime()
                                        onBackPressed() 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_folder),
                                    contentDescription = "Back to folder view",
                                    modifier = Modifier.size(24.dp),
                                    tint = textColor
                                )
                            }
                            
                            // Previous song button - Same size as other navigation buttons
                            val canGoPrevious = playlistItems.isNotEmpty() && playlistIndex > 0
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(buttonShape)
                                    .background(dominantColor.copy(alpha = 0.7f))
                                    .clickable(enabled = canGoPrevious) { 
                                        updateInteractionTime()
                                        if (canGoPrevious) viewModel.playPreviousSong() 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_previous),
                                    contentDescription = "Previous song",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (canGoPrevious) textColor else textColor.copy(alpha = 0.5f)
                                )
                            }
                            
                            // Shuffle button - Replacing visualization button
                            val isShuffleMode = viewModel.isShuffleMode.collectAsState().value
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(buttonShape)
                                    .background(dominantColor.copy(alpha = 0.7f))
                                    .clickable { 
                                        updateInteractionTime()
                                        viewModel.toggleShuffleMode()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shuffle),
                                    contentDescription = if (isShuffleMode) "Disable Shuffle Mode" else "Enable Shuffle Mode",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isShuffleMode) accentColor else textColor
                                )
                            }
                        }
                    } ?: run {
                        // If no audio file is currently selected
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(indicatorColor = accentColor)
                        }
                    }
                }
            }
            
            // Bottom button bar similar to browser view
            // Only show if we have a current audio file and not in visualization mode
            if (currentAudioFile != null && !showVisualization) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(MaterialTheme.colors.surface.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Play/Pause button
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { 
                                updateInteractionTime()
                                viewModel.togglePlayPause() 
                            },
                        tint = MaterialTheme.colors.primary
                    )
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    // Next button
                    val canGoNext = playlistItems.isNotEmpty() && playlistIndex < playlistItems.size - 1
                    Icon(
                        painter = painterResource(id = R.drawable.ic_next),
                        contentDescription = "Next Track",
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(enabled = canGoNext) { 
                                updateInteractionTime()
                                if (canGoNext) viewModel.playNextSong() 
                            },
                        tint = if (canGoNext) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
