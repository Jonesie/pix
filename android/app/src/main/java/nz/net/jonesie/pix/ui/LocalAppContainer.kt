package nz.net.jonesie.pix.ui

import androidx.compose.runtime.staticCompositionLocalOf
import nz.net.jonesie.pix.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided — wrap the app in CompositionLocalProvider")
}
