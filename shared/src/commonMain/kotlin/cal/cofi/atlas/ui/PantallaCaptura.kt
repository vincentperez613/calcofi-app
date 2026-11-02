package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.Stage
import cal.cofi.atlas.nombreMes

/**
 * Estado de la pantalla de captura, hoisteado: el dueno vive afuera
 * (por ahora App.kt). PantallaCaptura solo lo lee y pide cambios via
 * [onCambio]; no guarda talla ni etapa por su cuenta.
 */
data class CapturaState(
    val tallaMm: Double? = null,
    val etapa: Stage? = null,
    /** 1-12, o null si no se sabe. Nunca se rellena con el mes actual. */
    val mesColecta: Int? = null,
    val zonaColecta: String = "",
)

/**
 * Primera pantalla del flujo: no identifica nada, solo fija que datos son
 * comparables (talla + etapa de desarrollo). Cada etapa lleva su nombre del
 * atlas (preflexion, flexion, postflexion) junto al dibujo de la notocorda.
 *
 * La talla es BL, como en el atlas: longitud notocordal en preflexion y
 * flexion, longitud estandar en postflexion. No es longitud total.
 *
 * Mes y zona de colecta son opcionales y no cambian la identificacion: solo
 * alimentan el riesgo de error por temporada y distribucion del resultado.
 * El mes NO se rellena con la fecha de hoy — las muestras se identifican
 * meses despues de colectarse, y un mes inventado es peor que ninguno.
 */
@Composable
fun PantallaCaptura(
    data: AtlasData,
    estado: CapturaState,
    onCambio: (CapturaState) -> Unit,
    onContinuar: () -> Unit,
    familiaFiltro: String? = null,
    modifier: Modifier = Modifier,
) {
    val ancho = LocalAncho.current
    val dimensiones = LocalDimensiones.current

    // Buffer de edicion solo para el texto del campo: Double.toString()
    // agrega ".0" y el "." final se pierde a mitad de tipear un decimal
    // (11. -> 12 en vez de 11.7). La verdad de negocio sigue siendo
    // estado.tallaMm; cada cambio se revalida y se propaga via onCambio.
    var textoTalla by remember { mutableStateOf(estado.tallaMm?.let(::formatoTalla) ?: "") }
    var mensajeFaltantes by remember { mutableStateOf<String?>(null) }

    // La validez se deriva del texto ACTUAL en cada recomposicion, nunca
    // del ultimo Double que se llego a parsear — si el tecnico escribe
    // "12" y despues lo cambia a "dd", estado.tallaMm no puede seguir
    // siendo 12: se resincroniza a null aca mismo, en cada cambio.
    val resultadoTalla = validarTalla(textoTalla)

    // Discreta a proposito: con pocas familias cargadas el numero es
    // sorprendente y hay que decirlo desde el arranque. Los nombres solo se
    // listan si son pocos; con el atlas completo (~100 familias) la lista
    // seria ruido y basta con los numeros.
    val familiasCargadas = remember(data) { data.species.map { it.family }.toSet().sorted() }
    val listaFamilias = if (familiasCargadas.size <= 5) " (${familiasCargadas.joinToString(", ")})" else ""

    ContenedorPantalla(modifier) {
        TituloPantalla("Specimen")
        familiaFiltro?.let { familia ->
            Text(
                "Family filter: $familia",
                fontSize = dimensiones.tamanoTextoBase,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "This version identifies ${conteo(data.species.size, "species", "species")} from " +
                "${conteo(familiasCargadas.size, "family", "families")}$listaFamilias.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )

        CampoNumerico(
            valor = textoTalla,
            onValorCambia = { texto ->
                textoTalla = texto
                val resultado = validarTalla(texto)
                onCambio(estado.copy(tallaMm = (resultado as? ResultadoValidacion.Valido)?.valor))
            },
            etiqueta = "Body length, BL (mm)",
            mensajeError = explicarTalla(resultadoTalla),
        )
        Text(
            "Notochord length (NL) in preflexion and flexion; standard length (SL) in postflexion.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )

        SelectorEtapa(
            etapa = estado.etapa,
            onSeleccion = { onCambio(estado.copy(etapa = it)) },
            ancho = ancho,
            dimensiones = dimensiones,
        )

        SelectorMes(
            mes = estado.mesColecta,
            onSeleccion = { onCambio(estado.copy(mesColecta = it)) },
            ancho = ancho,
            dimensiones = dimensiones,
        )

        CampoTexto(
            valor = estado.zonaColecta,
            onValorCambia = { onCambio(estado.copy(zonaColecta = sanitizarZona(it))) },
            etiqueta = "Collection locality (optional)",
            // Las mismas formas en que el atlas describe la distribucion
            // ("Bahia Magdalena, Baja California Sur", "Gulf of California",
            // "ca. 30° N"): con eso el tecnico decide en el resultado si su
            // zona cae dentro.
            ejemplo = "e.g. Ensenada, B.C.; Gulf of California; CalCOFI line 90, station 30; 31.5°N 116.8°W",
        )

        // Siempre clickeable: al intentar continuar con algo incompleto,
        // nombra que falta en vez de solo deshabilitarse en silencio.
        BotonPrincipal(
            texto = "Continue",
            onClick = {
                val faltantes = buildList {
                    if (resultadoTalla !is ResultadoValidacion.Valido) add("body length")
                    if (estado.etapa == null) add("developmental stage")
                }
                if (faltantes.isEmpty()) {
                    onContinuar()
                } else {
                    mensajeFaltantes = "Missing: ${faltantes.joinToString(" and ")}."
                }
            },
        )
        mensajeFaltantes?.let {
            Text(it, fontSize = dimensiones.tamanoTextoBase, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatoTalla(valor: Double): String =
    if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()

/** Sin caracteres de control (texto pegado de otra app) y con un tope razonable. */
private fun sanitizarZona(entrada: String): String =
    entrada.filterNot { it.code in 0x00..0x1F || it.code == 0x7F }.take(120)

// ------------------------------------------------------- selector de mes

/**
 * Doce botones y "Unknown". Tocar el mes ya elegido lo quita: un mes
 * puesto por error tiene que poder volver a "sin dato" sin adivinar otro.
 */
@Composable
private fun SelectorMes(
    mes: Int?,
    onSeleccion: (Int?) -> Unit,
    ancho: Ancho,
    dimensiones: Dimensiones,
) {
    val porFila = if (ancho == Ancho.COMPACTO) 4 else 6
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        Text("Collection month (optional)", fontSize = dimensiones.tamanoTextoBase)
        for (fila in (1..12).chunked(porFila)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
                for (m in fila) {
                    BotonSeleccionable(
                        texto = nombreMes(m).take(3),
                        seleccionado = mes == m,
                        onClick = { onSeleccion(if (mes == m) null else m) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        BotonSeleccionable(
            texto = "Unknown",
            seleccionado = mes == null,
            onClick = { onSeleccion(null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------- selector de etapa

/** [descripcion] es el criterio que define la etapa; va debajo del nombre. */
private data class OpcionEtapa(
    val etapa: Stage,
    val descripcion: String,
    val dibujo: @Composable (Color, Modifier) -> Unit,
) {
    val texto: String get() = "${nombreEtapa(etapa)}\n$descripcion"
}

private val opcionesEtapa = listOf(
    OpcionEtapa(Stage.PRF, "notochord straight") { color, mod -> DibujoColaRecta(color, mod) },
    OpcionEtapa(Stage.F, "notochord tip flexing") { color, mod -> DibujoColaDoblandose(color, mod) },
    OpcionEtapa(Stage.POF, "flexion complete") { color, mod -> DibujoColaDobladaArriba(color, mod) },
)

@Composable
private fun SelectorEtapa(
    etapa: Stage?,
    onSeleccion: (Stage) -> Unit,
    ancho: Ancho,
    dimensiones: Dimensiones,
) {
    Text("Developmental stage", fontSize = dimensiones.tamanoTextoBase)
    if (ancho == Ancho.COMPACTO) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
            for (opcion in opcionesEtapa) {
                TarjetaIlustrada(
                    texto = opcion.texto,
                    seleccionada = etapa == opcion.etapa,
                    onClick = { onSeleccion(opcion.etapa) },
                    dibujo = opcion.dibujo,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
            for (opcion in opcionesEtapa) {
                TarjetaIlustrada(
                    texto = opcion.texto,
                    seleccionada = etapa == opcion.etapa,
                    onClick = { onSeleccion(opcion.etapa) },
                    dibujo = opcion.dibujo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// --------------------------------------------- placeholders de ilustracion

@Composable
private fun DibujoColaRecta(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grosor = size.minDimension * 0.08f
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.8f),
            end = Offset(size.width * 0.85f, size.height * 0.2f),
            strokeWidth = grosor,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DibujoColaDoblandose(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grosor = size.minDimension * 0.08f
        val camino = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.8f)
            quadraticTo(
                size.width * 0.5f, size.height * 0.9f,
                size.width * 0.85f, size.height * 0.25f,
            )
        }
        drawPath(camino, color = color, style = Stroke(width = grosor, cap = StrokeCap.Round))
    }
}

@Composable
private fun DibujoColaDobladaArriba(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grosor = size.minDimension * 0.08f
        val camino = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.7f)
            lineTo(size.width * 0.65f, size.height * 0.55f)
            quadraticTo(
                size.width * 0.85f, size.height * 0.5f,
                size.width * 0.8f, size.height * 0.15f,
            )
        }
        drawPath(
            camino,
            color = color,
            style = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
