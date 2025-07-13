package com.example.heictojpeg.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemeOption {
    Light,
    Dark
}

fun getColorScheme(themeOption: ThemeOption): ColorScheme {
    return when (themeOption) {
        ThemeOption.Light -> lightColorScheme(
            // Light Theme: White background with coral red buttons and light blue accents
            primary = Color(0xFFc47671), // Coral red for primary buttons
            onPrimary = Color(0xFFFFFFFF), // White text on buttons
            primaryContainer = Color(0xFF9db2c2), // Light glaucous blue for containers
            onPrimaryContainer = Color(0xFF000000), // Black text on containers
            secondary = Color(0xFFc47671), // Coral red for secondary elements
            onSecondary = Color(0xFFFFFFFF), // White text on secondary
            secondaryContainer = Color(0xFF9db2c2), // Light glaucous blue for secondary containers
            onSecondaryContainer = Color(0xFF000000), // Black text on secondary containers
            tertiary = Color(0xFFc47671), // Coral red for tertiary elements
            onTertiary = Color(0xFFFFFFFF), // White text on tertiary
            tertiaryContainer = Color(0xFF9db2c2), // Light glaucous blue for tertiary containers
            onTertiaryContainer = Color(0xFF000000), // Black text on tertiary containers
            error = Color(0xFFD32F2F), // Standard error red
            onError = Color(0xFFFFFFFF), // White text on error
            errorContainer = Color(0xFFFFEBEE), // Light error container
            onErrorContainer = Color(0xFFB71C1C), // Dark red text on error container
            background = Color(0xFFFFFFFF), // White background
            onBackground = Color(0xFF000000), // Black text on background
            surface = Color(0xFFFFFFFF), // White surface
            onSurface = Color(0xFF000000), // Black text on surface
            surfaceVariant = Color(0xFF9db2c2), // Light glaucous blue for surface variants
            onSurfaceVariant = Color(0xFF000000), // Black text on surface variants
            outline = Color(0xFF9db2c2), // Light glaucous blue for outlines
            outlineVariant = Color(0xFFE0E0E0), // Light gray for outline variants
            scrim = Color(0xFF000000), // Black scrim
            inverseSurface = Color(0xFF303030), // Dark surface for inverse
            inverseOnSurface = Color(0xFFFFFFFF), // White text on inverse surface
            inversePrimary = Color(0xFF9db2c2) // Light glaucous blue for inverse primary
        )
        ThemeOption.Dark -> darkColorScheme(
            // Dark Theme: Midnight Eclipse - Black background with carmine buttons and diamine green accents
            primary = Color(0xFFb1010e), // Carmine for primary buttons (swapped)
            onPrimary = Color(0xFFe8c402), // Yellow text on buttons
            primaryContainer = Color(0xFF105717), // Diamine green for containers (swapped)
            onPrimaryContainer = Color(0xFFe8c402), // Yellow text on containers
            secondary = Color(0xFFb1010e), // Carmine for secondary elements (swapped)
            onSecondary = Color(0xFFe8c402), // Yellow text on secondary
            secondaryContainer = Color(0xFF105717), // Diamine green for secondary containers (swapped)
            onSecondaryContainer = Color(0xFFe8c402), // Yellow text on secondary containers
            tertiary = Color(0xFFb1010e), // Carmine for tertiary elements (swapped)
            onTertiary = Color(0xFFe8c402), // Yellow text on tertiary
            tertiaryContainer = Color(0xFF105717), // Diamine green for tertiary containers (swapped)
            onTertiaryContainer = Color(0xFFe8c402), // Yellow text on tertiary containers
            error = Color(0xFFEF5350), // Light red for errors
            onError = Color(0xFFB71C1C), // Dark red text on error
            errorContainer = Color(0xFFD32F2F), // Standard error container
            onErrorContainer = Color(0xFFFFEBEE), // Light text on error container
            background = Color(0xFF17171a), // Black background (#17171a)
            onBackground = Color(0xFFe8c402), // Yellow text on background
            surface = Color(0xFF17171a), // Black surface
            onSurface = Color(0xFFe8c402), // Yellow text on surface
            surfaceVariant = Color(0xFF105717), // Diamine green for surface variants (swapped)
            onSurfaceVariant = Color(0xFFe8c402), // Yellow text on surface variants
            outline = Color(0xFF105717), // Diamine green for outlines (swapped)
            outlineVariant = Color(0xFF424242), // Dark gray for outline variants
            scrim = Color(0xFF000000), // Black scrim
            inverseSurface = Color(0xFFe8c402), // Yellow surface for inverse
            inverseOnSurface = Color(0xFF17171a), // Black text on inverse surface
            inversePrimary = Color(0xFFb1010e) // Carmine for inverse primary (swapped)
        )
    }
}
