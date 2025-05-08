/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.watchware.mp3.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.watchware.mp3.presentation.screen.MediaBrowserScreen
import com.watchware.mp3.presentation.screen.PlayerScreen
import com.watchware.mp3.presentation.theme.WatchwareMP3Theme
import com.watchware.mp3.presentation.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Handle back button presses
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isPlayerActive.value) {
                    viewModel.closePlayer()
                } else if (!viewModel.navigateBack()) {
                    // If we're at the root already, allow the standard back behavior
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            WatchwareMP3Theme {
                val isPlayerActive by viewModel.isPlayerActive.collectAsState()
                
                if (isPlayerActive) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBackPressed = { viewModel.closePlayer() }
                    )
                } else {
                    MediaBrowserScreen(viewModel)
                }
            }
        }
    }
}