package com.siddharth.stockticker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.siddharth.stockticker.navigation.StockTickerNavGraph
import com.siddharth.stockticker.ui.theme.StockTickerTheme
import androidx.wear.compose.material.MaterialTheme

/**
 * Single activity hosting the whole Compose tree, which is the Wear norm: the
 * navigation host owns the back gesture, so extra activities would only add
 * window transitions the swipe already handles.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw into the full circle; the layouts keep content inside the
        // bezel-safe area themselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            StockTickerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    StockTickerNavGraph()
                }
            }
        }
    }
}
