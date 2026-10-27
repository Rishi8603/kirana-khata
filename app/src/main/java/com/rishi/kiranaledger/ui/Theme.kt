package com.rishi.kiranaledger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand blue for navigation and neutral actions; red and green stay reserved for money direction. */
val Brand = Color(0xFF1E5BB8)
val BrandSoft = Color(0xFFDCE7FA)
private val BrandDark = Color(0xFF0B2E66)

private val KiranaColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = BrandDark,
    secondaryContainer = BrandSoft,
    onSecondaryContainer = BrandDark,
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF0F5),
    surfaceContainer = Color.White,
)

/** Always light: high contrast in a bright shop. */
@Composable
fun KiranaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KiranaColors, content = content)
}
