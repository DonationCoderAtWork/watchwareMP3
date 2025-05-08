package com.watchware.mp3.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Music visualizer view that shows animated bars representing the audio
 */
@Composable
fun MusicVisualizerView(
    isPlaying: Boolean,
    accentColor: Color,
    dominantColor: Color,
    textColor: Color,
    controlsAlpha: Float,
    onToggleView: () -> Unit
) {
    // Number of bars in the visualizer
    val barCount = 20
    
    // Store height values for each bar
    val barHeights = remember { 
        List(barCount) { 
            Animatable(0f) 
        } 
    }
    
    // Animate the bars based on playback state
    LaunchedEffect(isPlaying) {
        coroutineScope {
            if (isPlaying) {
                while (true) {
                    // Update each bar with a new random height
                    barHeights.forEachIndexed { index, animatable ->
                        launch {
                            // Create a natural looking wave pattern
                            val targetHeight = Random.nextFloat() * 0.7f + 0.3f
                            
                            // Animate to the new height with varying speeds for a more natural look
                            animatable.animateTo(
                                targetValue = targetHeight,
                                animationSpec = tween(
                                    durationMillis = 200 + (index % 3) * 100,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    }
                    
                    // Slight delay between updates
                    delay(200)
                }
            } else {
                // When not playing, animate bars to a low resting state
                barHeights.forEach { animatable ->
                    launch {
                        animatable.animateTo(
                            targetValue = 0.1f,
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(onClick = onToggleView),
        contentAlignment = Alignment.Center
    ) {
        // Draw the visualizer bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until barCount) {
                val barHeight = barHeights[i].value
                val barColor = when {
                    i % 5 == 0 -> accentColor
                    i % 3 == 0 -> accentColor.copy(alpha = 0.8f)
                    else -> dominantColor
                }
                
                // Individual visualizer bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(barHeight)
                        .padding(horizontal = 1.dp)
                        .background(
                            color = barColor,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                )
            }
        }
    }
}
