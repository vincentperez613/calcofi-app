package cal.cofi.atlas

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment

fun main() = application {
    val inicial = remember { ventanaInicial() }
    val estadoVentana = rememberWindowState(
        placement = inicial.placement,
        position = inicial.position,
        size = inicial.size,
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "CalcofiLarvae",
        state = estadoVentana,
    ) {
        App()
    }
}

/** El tamaño que pide la app cuando la pantalla alcanza. */
private val TAMANO_PREFERIDO = DpSize(1100.dp, 900.dp)

private data class VentanaInicial(val placement: WindowPlacement, val position: WindowPosition, val size: DpSize)

/**
 * La ventana inicial, dentro del area util de la pantalla (sin la barra de
 * tareas). Pedir 1100x900 a ciegas no cabe en una laptop comun: 1920x1200
 * al 150% deja 1280x752 utiles, y la ventana quedaba por debajo de la barra
 * de tareas. Si no cabe, abre maximizada (al maximizar, Windows respeta la
 * barra) y con un tamaño restaurado que si cabe, centrado.
 *
 * maximumWindowBounds viene en unidades logicas (ya divididas por la escala
 * de Windows), las mismas que usa Compose para el tamaño de la ventana.
 */
private fun ventanaInicial(): VentanaInicial {
    val util = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val cabe = TAMANO_PREFERIDO.width.value <= util.width && TAMANO_PREFERIDO.height.value <= util.height
    // Restaurada desde maximizada, un poco mas chica que el area util: del
    // mismo tamaño se veria igual que maximizada.
    val margen = if (cabe) 1f else 0.9f
    val ancho = minOf(TAMANO_PREFERIDO.width.value, util.width * margen)
    val alto = minOf(TAMANO_PREFERIDO.height.value, util.height * margen)
    return VentanaInicial(
        placement = if (cabe) WindowPlacement.Floating else WindowPlacement.Maximized,
        position = WindowPosition(
            x = (util.x + (util.width - ancho) / 2).dp,
            y = (util.y + (util.height - alto) / 2).dp,
        ),
        size = DpSize(ancho.dp, alto.dp),
    )
}
