package cal.cofi.atlas.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * enableEdgeToEdge() (MainActivity) elige los iconos de las barras segun el
 * modo del SISTEMA. Con el modo elegido en la app los dos pueden no
 * coincidir, y los iconos oscuros quedarian invisibles sobre el fondo
 * oscuro: se ajustan aca en cada composicion.
 */
@Composable
actual fun BarrasDeSistema(oscuro: Boolean) {
    val vista = LocalView.current
    if (vista.isInEditMode) return
    SideEffect {
        val ventana = vista.context.actividad()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(ventana, vista).apply {
            isAppearanceLightStatusBars = !oscuro
            isAppearanceLightNavigationBars = !oscuro
        }
    }
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}
