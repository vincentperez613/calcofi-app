package cal.cofi.atlas.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Pantalla de verificacion manual del sistema de layout: muestra el [Ancho]
 * vigente, los valores de [Dimensiones] actuales y los cuatro componentes
 * base. Redimensionar la ventana (desktopApp) debe moverla entre COMPACTO,
 * MEDIO y AMPLIO en vivo.
 */
@Composable
fun PantallaPrueba() {
    val ancho = LocalAncho.current
    val dimensiones = LocalDimensiones.current
    var talla by remember { mutableStateOf("") }

    ContenedorPantalla {
        TituloPantalla("Ancho: $ancho")

        Text(
            "alturaBotonMinima = ${dimensiones.alturaBotonMinima}\n" +
                "tamanoTextoBase = ${dimensiones.tamanoTextoBase}\n" +
                "tamanoTextoTitulo = ${dimensiones.tamanoTextoTitulo}\n" +
                "espaciado = ${dimensiones.espaciado}\n" +
                "anchoMaximoContenido = ${dimensiones.anchoMaximoContenido}\n" +
                "tamanoIlustracion = ${dimensiones.tamanoIlustracion}",
            fontSize = dimensiones.tamanoTextoBase,
        )

        BotonPrincipal(texto = "Primary button", onClick = {})
        BotonSecundario(texto = "Secondary button", onClick = {})
        CampoNumerico(
            valor = talla,
            onValorCambia = { talla = it },
            etiqueta = "Body length, BL (mm)",
        )
    }
}
