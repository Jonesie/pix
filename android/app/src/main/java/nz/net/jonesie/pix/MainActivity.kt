package nz.net.jonesie.pix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import nz.net.jonesie.pix.ui.LocalAppContainer
import nz.net.jonesie.pix.ui.nav.PixNavHost
import nz.net.jonesie.pix.ui.theme.PixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PixApplication).container

        setContent {
            PixTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        PixNavHost()
                    }
                }
            }
        }
    }
}
