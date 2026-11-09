package com.example.peluqueria.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PeluqueriaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0F2D5C), onPrimary = Color.White,
            secondary = Color(0xFFC62828), onSecondary = Color.White,
            background = Color(0xFFF4F6F9), surface = Color.White,
            onBackground = Color(0xFF101B2D), onSurface = Color(0xFF101B2D),
            surfaceVariant = Color(0xFFEDF0F5), onSurfaceVariant = Color(0xFF687387),
            outline = Color(0xFFDCE1EA), error = Color(0xFFC62828)
        ), typography = Typography, content = content
    )
}
