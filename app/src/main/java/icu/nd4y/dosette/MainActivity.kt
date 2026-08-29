package icu.nd4y.dosette

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import icu.nd4y.dosette.ui.DosetteRoot
import icu.nd4y.dosette.ui.theme.DosetteTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DosetteTheme {
                DosetteRoot()
            }
        }
    }
}
