package icu.nd4y.dosette

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.ui.DosetteRoot
import icu.nd4y.dosette.ui.MainViewModel
import icu.nd4y.dosette.ui.onboarding.OnboardingScreen
import icu.nd4y.dosette.ui.theme.DosetteTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val loaded = settings
            val darkTheme =
                when (loaded?.theme ?: ThemeMode.SYSTEM) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            // enableEdgeToEdge() above picked icon colours from the system theme
            // once; a forced light/dark theme needs them re-picked or the
            // status-bar icons vanish against the app background.
            DisposableEffect(darkTheme) {
                val bars =
                    if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            DosetteTheme(darkTheme = darkTheme, dynamicColor = loaded?.dynamicColor ?: true) {
                when {
                    loaded == null -> {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }

                    !loaded.onboardingDone -> {
                        OnboardingScreen()
                    }

                    else -> {
                        DosetteRoot()
                    }
                }
            }
        }
    }
}
