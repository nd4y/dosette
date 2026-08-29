package icu.nd4y.dosette

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.ui.DosetteRoot
import icu.nd4y.dosette.ui.MainViewModel
import icu.nd4y.dosette.ui.theme.DosetteTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme =
                when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            DosetteTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                DosetteRoot()
            }
        }
    }
}
