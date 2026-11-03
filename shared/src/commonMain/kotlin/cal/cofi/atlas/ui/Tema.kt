package cal.cofi.atlas.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Modo claro / oscuro. El oscuro no es un adorno: de noche en cubierta una
 * pantalla blanca encandila y estorba a quien esta clasificando al lado.
 */
@Composable
fun TemaAtlas(oscuro: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) darkColorScheme() else lightColorScheme()) {
        BarrasDeSistema(oscuro)
        // Sin Surface nadie pinta el fondo ni fija el color del texto: en
        // claro parecia funcionar porque la ventana es blanca y el texto sale
        // negro por defecto, pero en oscuro quedaria negro sobre negro.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

/**
 * Iconos de la barra de estado (y de navegacion en Android) claros u oscuros
 * segun [oscuro]. Hace falta porque el modo lo elige la app: el sistema solo
 * sabe el suyo. En escritorio no hace nada.
 */
@Composable
expect fun BarrasDeSistema(oscuro: Boolean)

/**
 * El boton de la esquina superior derecha. Muestra el modo AL QUE se cambia:
 * una luna en claro, un sol en oscuro.
 */
@Composable
fun BotonModo(oscuro: Boolean, onCambio: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val dimensiones = LocalDimensiones.current
    IconToggleButton(
        checked = oscuro,
        onCheckedChange = onCambio,
        modifier = modifier
            .size(dimensiones.alturaBotonMinima)
            .semantics { contentDescription = if (oscuro) "Switch to light mode" else "Switch to dark mode" },
    ) {
        if (oscuro) IconoSol() else IconoLuna()
    }
}

/**
 * Filtro para las laminas del atlas: el papel toma el color [papel] y la
 * tinta el color [tinta]. Las laminas son blanco y negro puro
 * (package_app.py las re-binariza), asi que el canal rojo basta como
 * "cuanto papel hay" en cada pixel, y los grises del escalado quedan en
 * medio, como deben.
 *
 * Con el fondo y el texto del tema, en oscuro la lamina queda invertida
 * (trazo claro sobre fondo oscuro) y del mismo color que la pantalla, no un
 * rectangulo negro encima de ella. En claro el papel deja de ser un blanco
 * puro distinto del fondo.
 */
fun filtroLamina(papel: Color, tinta: Color): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            papel.red - tinta.red, 0f, 0f, 0f, tinta.red * 255f,
            papel.green - tinta.green, 0f, 0f, 0f, tinta.green * 255f,
            papel.blue - tinta.blue, 0f, 0f, 0f, tinta.blue * 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

// ------------------------------------------------------------ preferencia

@Serializable
private data class PreferenciasGuardadas(val modoOscuro: Boolean? = null)

private val jsonPreferencias = Json { ignoreUnknownKeys = true }

/** Lo que el tecnico eligio y tiene que seguir igual la proxima vez que abra la app. */
object Preferencias {
    /** El modo elegido, o null si nunca eligio: en ese caso manda el del sistema. */
    fun modoOscuro(): Boolean? = leer().modoOscuro

    fun guardarModoOscuro(oscuro: Boolean) {
        AlmacenamientoPlataforma.escribirPreferencias(
            jsonPreferencias.encodeToString(PreferenciasGuardadas.serializer(), leer().copy(modoOscuro = oscuro)),
        )
    }

    private fun leer(): PreferenciasGuardadas {
        val texto = AlmacenamientoPlataforma.leerPreferencias()
        if (texto.isNullOrBlank()) return PreferenciasGuardadas()
        // Un archivo ilegible no tumba la app: se vuelve al modo del sistema.
        return try {
            jsonPreferencias.decodeFromString(PreferenciasGuardadas.serializer(), texto)
        } catch (e: Exception) {
            PreferenciasGuardadas()
        }
    }
}
