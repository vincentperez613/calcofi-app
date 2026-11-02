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

/**
 * Catalogo de lo que la app tiene cargado: familias con su imagen, buscador y
 * detalle por especie (ver CatalogoEspecies.kt). No identifica nada — es
 * referencia.
 *
 * [activa] sigue el mismo patron que PantallaHistorial; el catalogo es
 * AtlasData, ya en memoria, asi que por ahora no se usa. Se deja por
 * simetria con las demas secciones.
 */
@Composable
fun PantallaEspecies(
    data: AtlasData,
    activa: Boolean,
    modifier: Modifier = Modifier,
    onIdentifyFamily: (String) -> Unit = {},
) {
    CatalogoEspecies(data = data, modifier = modifier, onIdentifyFamily = onIdentifyFamily)
}

// ------------------------------------------------------------------ detalle

@Composable
internal fun ColumnScope.DetalleEspecie(
    data: AtlasData,
    especie: Species,
    dimensiones: Dimensiones,
    textoVolverArriba: String = "Back to catalog",
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
        Text("Family: ${especie.family}", fontSize = dimensiones.tamanoTextoBase)
        Text("Atlas page: ${especie.pdfPage}", fontSize = dimensiones.tamanoTextoBase)

        if (especie.meristics.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
                Text("Meristics", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
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
                Text("Figures", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
                for (figura in figuras) {
                    TarjetaFigura(figura, dimensiones)
                }
            }
        }

        BotonPrincipal(texto = "Back to catalog", onClick = onVolver)
    }
}

/**
 * Temporada de desove, distribucion y habitat: el texto del atlas tal cual
 * (con algun error de OCR), mas los meses leidos de ese texto
 * marcados como lectura automatica sin revisar. Es lo mismo que el
 * resultado usa para el riesgo de error, para que el tecnico pueda
 * consultarlo antes y discrepar con conocimiento.
 */
@Composable
private fun SeccionHistoriaDeVida(lh: LifeHistory?, dimensiones: Dimensiones) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text("Life history", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
        if (lh == null) {
            Text("The atlas gives no life-history information for this species.", fontSize = dimensiones.tamanoTextoBase)
            return@Column
        }
        Text(
            "Verbatim atlas text; OCR errors not corrected.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )
        lh.spawningSeason?.let { Text("Spawning: “$it”", fontSize = dimensiones.tamanoTextoBase) }
        if (lh.spawningMonths.isNotEmpty()) {
            val pico = if (lh.spawningPeak.isNotEmpty()) "; peak: ${describirMeses(lh.spawningPeak)}" else ""
            Text(
                "Months parsed automatically, not reviewed: ${describirMeses(lh.spawningMonths)}$pico.",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }
        lh.range?.let { Text("Range: “$it”", fontSize = dimensiones.tamanoTextoBase) }
        lh.habitat?.let { Text("Habitat: “$it”", fontSize = dimensiones.tamanoTextoBase) }
    }
}

@Composable
private fun ListaDiagnosticos(diagnostic: Map<String, DiagnosticChar>, dimensiones: Dimensiones) {
    if (diagnostic.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text("Diagnostic characters", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
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
