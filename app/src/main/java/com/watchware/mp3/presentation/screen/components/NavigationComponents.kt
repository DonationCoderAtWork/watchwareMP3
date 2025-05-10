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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchware.mp3.R

@Composable
fun NavigationHeader(
    currentFolder: String,
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colors.surface)
            .padding(start = 42.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_back),
            contentDescription = "Back",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onNavigateBack),
            tint = MaterialTheme.colors.primary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Current folder name with scrolling text for names longer than 19 characters
        val scrollState = rememberScrollState()
        val textWidth = remember { mutableStateOf<Int>(0) }
        val containerWidth = remember { mutableStateOf<Int>(0) }
        
        // The folder name now includes the app name and version
        val displayText = "  $currentFolder  "
        
        // Check if text exceeds 19 characters to activate scrolling
        val shouldScroll = displayText.length > 19
        
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
                .weight(1f)
                .padding(horizontal = 6.dp) // Add padding on both sides
                .onGloballyPositioned { coordinates ->
                    containerWidth.value = coordinates.size.width
                }
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.caption1,
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
    }
}

@Composable
fun AppTitleHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 22.dp), // More padding on sides
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ">  watchwareMP3 1.2",
            style = MaterialTheme.typography.title3,
            maxLines = 1
        )
    }
}
