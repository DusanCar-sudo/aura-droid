package dev.aura.auradroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.aura.auradroid.data.settings.AppearanceRepository
import dev.aura.auradroid.data.settings.ThemeMode
import dev.aura.auradroid.ui.navigation.AuraNavHost
import dev.aura.auradroid.ui.splash.BrandSplash
import dev.aura.auradroid.ui.theme.AuraDroidTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps the launch theme for the app theme
        // and hands over from the system splash to BrandSplash below.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by appearance.appearance.collectAsState(initial = dev.aura.auradroid.data.settings.Appearance())

            // System means "whatever the phone is in", resolved here against the
            // actual system setting rather than cached at launch.
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            // Brand splash: once per launch. rememberSaveable so rotating the phone
            // during it does not replay it.
            var showSplash by rememberSaveable { mutableStateOf(true) }

            AuraDroidTheme(darkTheme = darkTheme, dynamicColor = prefs.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AuraNavHost()
                        AnimatedVisibility(
                            visible = showSplash,
                            enter = EnterTransition.None,
                            exit = fadeOut(tween(350)),
                        ) {
                            BrandSplash(onFinished = { showSplash = false })
                        }
                    }
                }
            }
        }
    }
}
