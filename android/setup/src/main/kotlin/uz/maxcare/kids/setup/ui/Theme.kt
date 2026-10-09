package uz.maxcare.kids.setup.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF1E6FD9)
private val BrandLight = Color(0xFF9CC4FF)

private val LightColors = lightColorScheme(primary = Brand, onPrimary = Color.White)
private val DarkColors = darkColorScheme(primary = BrandLight, onPrimary = Color(0xFF00315F))

@Composable
fun MaxCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
