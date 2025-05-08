package com.watchware.mp3.presentation.screen.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchware.mp3.R
import com.watchware.mp3.data.model.MediaItem
import com.watchware.mp3.util.MediaMetadataUtil

/**
 * Cache for track counts to avoid repeated calculations
 */
private val trackCountCache = mutableMapOf<String, Int>()

/**
 * Recursively counts the total number of audio tracks in a folder and its subfolders
 * with caching for better performance
 */
fun countTracks(folder: MediaItem.Folder): Int {
    // Check cache first
    trackCountCache[folder.path]?.let { return it }
    
    var count = 0
    
    // Count direct audio files
    count += folder.children.count { it is MediaItem.AudioFile }
    
    // Recursively count audio files in subfolders
    folder.children.filterIsInstance<MediaItem.Folder>().forEach { subFolder ->
        count += countTracks(subFolder)
    }
    
    // Cache the result
    trackCountCache[folder.path] = count
    
    return count
}

@Composable
fun MediaItemRow(
    mediaItem: MediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Calculate these values directly in the Composable function
    val (trackCount, directorySize) = if (mediaItem is MediaItem.Folder) {
        remember(mediaItem.path) { 
            Pair(
                countTracks(mediaItem),
                MediaMetadataUtil.calculateDirectorySize(mediaItem.path)
            )
        }
    } else {
        Pair(null, null)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Different handling for folder vs audio file
        when (mediaItem) {
            is MediaItem.Folder -> {
                FolderIcon()
            }
            is MediaItem.AudioFile -> {
                // Only load album art once item is visible
                AudioFileIcon(filePath = mediaItem.path)
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Item details - name and subtitle
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Item name with scrolling text for names longer than 19 characters
            val scrollState = rememberScrollState()
            val textWidth = remember { mutableStateOf<Int>(0) }
            val containerWidth = remember { mutableStateOf<Int>(0) }
            val name = mediaItem.name
            
            // Check if name exceeds 19 characters to activate scrolling
            val shouldScroll = name.length > 19
            
            // Only apply scrolling animation if text is longer than threshold
            if (shouldScroll && textWidth.value > containerWidth.value && containerWidth.value > 0) {
                val infiniteTransition = rememberInfiniteTransition()
                val scrollPosition = infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = textWidth.value.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = textWidth.value * 25, // Speed proportional to text length
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    )
                )
                
                LaunchedEffect(scrollPosition.value) {
                    scrollState.scrollTo(scrollPosition.value.toInt() % (textWidth.value + 100))
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        containerWidth.value = coordinates.size.width
                    }
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.body1,
                    maxLines = 1,
                    overflow = if (shouldScroll) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .let { 
                            if (shouldScroll) it.horizontalScroll(scrollState) else it 
                        }
                        .onGloballyPositioned { coordinates ->
                            textWidth.value = coordinates.size.width
                        }
                )
            }
            
            // Subtitle for folders showing track count and directory size
            if (mediaItem is MediaItem.Folder && trackCount != null && directorySize != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track count first
                    Text(
                        text = "$trackCount",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    
                    // Music icon moved to right of number
                    Icon(
                        painter = painterResource(id = R.drawable.ic_music_note),
                        contentDescription = "Audio files",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Directory size (without dot separator)
                    Text(
                        text = directorySize,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FolderIcon() {
    Icon(
        painter = painterResource(id = R.drawable.ic_folder),
        contentDescription = "Folder",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colors.primary
    )
}

@Composable
fun AudioFileIcon(filePath: String) {
    val context = LocalContext.current
    
    // Memoize album art to prevent unnecessary recompositions
    var albumArt by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember(filePath) { mutableStateOf(false) }
    
    // Load album art from the audio file with a LaunchedEffect that runs only once per file path
    LaunchedEffect(filePath) {
        try {
            // Use our cached implementation from MediaMetadataUtil
            albumArt = MediaMetadataUtil.loadAlbumArt(context, filePath)
        } catch (e: Exception) {
            // Handle any exceptions during loading
            loadError = true
            albumArt = null
        }
    }
    
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (albumArt != null && !loadError) {
            // If we have album art, display it
            Image(
                bitmap = albumArt!!,
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.5f)),
                        RoundedCornerShape(4.dp)
                    )
            )
        } else {
            // Otherwise use default music icon
            Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = "Music",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colors.primary
            )
        }
    }
}
