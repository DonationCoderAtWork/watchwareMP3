package com.watchware.mp3.presentation.screen.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchware.mp3.R
import com.watchware.mp3.data.model.MediaItem

/**
 * Bar displaying the currently playing song with scrolling text effect
 */
@Composable
fun CurrentlyPlayingLabelBar(
    currentAudioFile: MediaItem.AudioFile,
    onOpenPlayer: () -> Unit
) {
    val headerHeight = 32.dp  // 66% of the NavigationHeader height (assuming its ~48dp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(MaterialTheme.colors.surface.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp)
            .clickable(onClick = onOpenPlayer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Add music icon
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = "Now Playing",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colors.primary
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Scrolling text implementation
        val scrollState = rememberScrollState()
        val textWidth = remember { mutableStateOf<Int>(0) }
        val containerWidth = remember { mutableStateOf<Int>(0) }
        
        // Only apply scrolling animation if text is longer than container
        if (textWidth.value > containerWidth.value && containerWidth.value > 0) {
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
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    containerWidth.value = coordinates.size.width
                }
        ) {
            Text(
                text = currentAudioFile.name,
                style = MaterialTheme.typography.caption1.copy(
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .onGloballyPositioned { coordinates ->
                        textWidth.value = coordinates.size.width
                    }
            )
        }
    }
}

/**
 * Bar with playback control buttons
 */
@Composable
fun PlayerButtonsBar(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val headerHeight = 32.dp  // 66% of the NavigationHeader height (assuming its ~48dp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
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
                .clickable(onClick = onPlayPauseClick),
            tint = MaterialTheme.colors.primary
        )
        
        Spacer(modifier = Modifier.width(32.dp))
        
        // Next button
        Icon(
            painter = painterResource(id = R.drawable.ic_next),
            contentDescription = "Next Track",
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onNextClick),
            tint = MaterialTheme.colors.primary
        )
    }
}
