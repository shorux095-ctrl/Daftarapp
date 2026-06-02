package uz.daftar.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Har bir ekran ustida (TopAppBar) ko'rinadigan uy tugmasi uchun CompositionLocal.
 * NavHost'дa qiymat berilsa, ichkari ekranlardа [HomeButton] avtomatik chaqiradi.
 */
val LocalGoHome = staticCompositionLocalOf<() -> Unit> { {} }

/** TopAppBar.actions ichida ishlatish — bosh ekranga qaytaradi */
@Composable
fun HomeButton() {
    val goHome = LocalGoHome.current
    IconButton(onClick = goHome) {
        Icon(Icons.Outlined.Home, contentDescription = "Bosh ekran")
    }
}
