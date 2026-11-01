package cal.cofi.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.DiagnosticChar
import cal.cofi.atlas.LifeHistory
import cal.cofi.atlas.Range
import cal.cofi.atlas.Species
import cal.cofi.atlas.describirMeses
import kotlin.math.floor

/** Catálogo de especies con listado por familias, imágenes y búsqueda. */
@Composable
fun PantallaEspecies(data: AtlasData, modifier: Modifier = Modifier) {
    CatalogoEspecies(data, modifier)
}

// ------------------------------------------------------------------ detalle

@Composable
internal fun ColumnScope.DetalleEspecie(
    data: AtlasData,
    especie: Species,
    dimensiones: Dimensiones,
    textoVolverArriba: String = "Volver al catálogo",
    onVolver: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = dimensiones.anchoMaximoContenido)
            .align(Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onVolver) {
                Text(textoVolverArriba, fontSize = dimensiones.tamanoTextoBase)
            }
        }
        TituloPantalla(especie.species)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = dimensiones.anchoMaximoContenido)
            .align(Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
    ) {
        Text("Familia: ${especie.family}", fontSize = dimensiones.tamanoTextoBase)
        Text("Página del atlas: ${especie.pdfPage}", fontSize = dimensiones.tamanoTextoBase)

        if (especie.meristics.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
                Text("Rangos merísticos", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
                for ((caracter, rango) in especie.meristics.toSortedMap()) {
                    Text("${traducirCaracter(caracter)}: ${formatearRango(rango)}", fontSize = dimensiones.tamanoTextoBase)
                }
            }
        }

        SeccionHistoriaDeVida(especie.lifeHistory, dimensiones)
        ListaDiagnosticos(especie.diagnostic, dimensiones)

        val figuras = remember(especie) { data.figures.filter { it.species == especie.species } }
        if (figuras.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
                Text("Figuras", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
                for (figura in figuras) {
                    TarjetaFigura(figura, dimensiones)
                }
            }
        }

        BotonPrincipal(texto = "Volver al catálogo", onClick = onVolver)
    }
}

/**
 * Temporada de desove, distribucion y habitat: el texto del atlas tal cual
 * (en ingles, con algun error de OCR), mas los meses leidos de ese texto
 * marcados como lectura automatica sin revisar. Es lo mismo que el
 * resultado usa para el riesgo de error, para que el tecnico pueda
 * consultarlo antes y discrepar con conocimiento.
 */
@Composable
private fun SeccionHistoriaDeVida(lh: LifeHistory?, dimensiones: Dimensiones) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text("Temporada y distribución", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
        if (lh == null) {
            Text("El atlas no trae esta información para la especie.", fontSize = dimensiones.tamanoTextoBase)
            return@Column
        }
        Text(
            "Texto del atlas, en inglés y sin corregir.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )
        lh.spawningSeason?.let { Text("Desove: «$it»", fontSize = dimensiones.tamanoTextoBase) }
        if (lh.spawningMonths.isNotEmpty()) {
            val pico = if (lh.spawningPeak.isNotEmpty()) "; pico: ${describirMeses(lh.spawningPeak)}" else ""
            Text(
                "Meses leídos automáticamente, sin revisar: ${describirMeses(lh.spawningMonths)}$pico.",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }
        lh.range?.let { Text("Distribución: «$it»", fontSize = dimensiones.tamanoTextoBase) }
        lh.habitat?.let { Text("Hábitat: «$it»", fontSize = dimensiones.tamanoTextoBase) }
    }
}

@Composable
private fun ListaDiagnosticos(diagnostic: Map<String, DiagnosticChar>, dimensiones: Dimensiones) {
    if (diagnostic.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text("Caracteres diagnósticos", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
        for ((caracter, d) in diagnostic.toSortedMap()) {
            val detalle = when {
                d.value != null -> d.value
                d.range != null -> formatearRango(d.range)
                else -> "—"
            }
            val condicion = d.conditional?.let { " ($it)" } ?: ""
            Text("${traducirCaracter(caracter)}: $detalle$condicion", fontSize = dimensiones.tamanoTextoBase)
        }
    }
}

private fun formatearRango(rango: Range): String = "${formatearNumero(rango.min)}–${formatearNumero(rango.max)}"

private fun formatearNumero(valor: Double): String =
    if (valor == floor(valor)) valor.toLong().toString() else valor.toString()
