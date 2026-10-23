package cal.cofi.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cal.cofi.atlas.NivelRiesgo
import cal.cofi.atlas.nombreMes

/**
 * Seccion "Historial" de la barra de navegacion: lista de sesiones guardadas
 * (mas reciente arriba), detalle completo al tocar una, y exportar a CSV.
 *
 * Con la navegacion de dos secciones esta pantalla queda permanentemente
 * compuesta (nunca se vuelve a montar al entrar) para no perder el estado
 * de la seccion Analisis al cambiar de pestana — por eso [activa] existe:
 * sin ella, [Almacen.listar] se leeria una sola vez al arrancar la app y
 * nunca volveria a reflejar sesiones nuevas.
 */
@Composable
fun PantallaHistorial(
    activa: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimensiones = LocalDimensiones.current
    var sesiones by remember { mutableStateOf(emptyList<SesionGuardada>()) }
    var seleccionada by remember { mutableStateOf<SesionGuardada?>(null) }

    LaunchedEffect(activa) {
        if (activa) sesiones = Almacen.listar()
    }

    ContenedorPantalla(modifier) {
        val actual = seleccionada
        if (actual != null) {
            DetalleSesion(actual, dimensiones, onVolver = { seleccionada = null })
        } else {
            ListaSesiones(sesiones, dimensiones, onSeleccion = { seleccionada = it })
        }
    }
}

@Composable
private fun ListaSesiones(
    sesiones: List<SesionGuardada>,
    dimensiones: Dimensiones,
    onSeleccion: (SesionGuardada) -> Unit,
) {
    TituloPantalla("History")

    var mensajeExportacion by remember { mutableStateOf<String?>(null) }
    BotonSecundario(
        texto = "Export to CSV",
        onClick = {
            mensajeExportacion = try {
                Almacen.exportarCsv()
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
        },
    )
    mensajeExportacion?.let { Text(it, fontSize = dimensiones.tamanoTextoBase) }

    if (sesiones.isEmpty()) {
        Text("No saved sessions yet.", fontSize = dimensiones.tamanoTextoBase)
    } else {
        for (sesion in sesiones) {
            TarjetaSesion(sesion, dimensiones, onClick = { onSeleccion(sesion) })
        }
    }
}

@Composable
private fun TarjetaSesion(sesion: SesionGuardada, dimensiones: Dimensiones, onClick: () -> Unit) {
    val forma = RoundedCornerShape(dimensiones.espaciado / 2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surfaceVariant, forma)
            .clickable(onClick = onClick)
            .padding(dimensiones.espaciado),
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4),
    ) {
        Text(
            "${sesion.fechaHoraIso} — ${nombreEtapaGuardada(sesion.etapa)}, BL ${sesion.tallaMm} mm",
            fontSize = dimensiones.tamanoTextoBase,
            fontWeight = FontWeight.Bold,
        )
        val resumen = sesion.resultado?.let { "${it.label} (${it.nivel})" }
            ?: "Unfinished — ${conteo(sesion.observaciones.size, "answer", "answers")}"
        Text(resumen, fontSize = dimensiones.tamanoTextoBase)
    }
}

@Composable
private fun DetalleSesion(sesion: SesionGuardada, dimensiones: Dimensiones, onVolver: () -> Unit) {
    TituloPantalla("Session ${sesion.fechaHoraIso}")
    Text("Stage: ${nombreEtapaGuardada(sesion.etapa)} — BL: ${sesion.tallaMm} mm", fontSize = dimensiones.tamanoTextoBase)
    Text(
        "Collection month: ${sesion.mesColecta?.let { nombreMes(it) } ?: "not recorded"} — " +
            "Locality: ${sesion.zonaColecta ?: "not recorded"}",
        fontSize = dimensiones.tamanoTextoBase,
    )

    Text("Observations", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
    if (sesion.observaciones.isEmpty()) {
        Text("None recorded yet.", fontSize = dimensiones.tamanoTextoBase)
    } else {
        for (obs in sesion.observaciones) {
            val valorTexto = when {
                obs.numeradorMm != null && obs.denominadorMm != null ->
                    "${obs.numeradorMm} / ${obs.denominadorMm} mm = ${obs.valor}%"
                obs.valor != null -> obs.valor.toString()
                obs.categorico != null -> obs.categorico
                else -> "unknown"
            }
            Text("${traducirCaracter(obs.character)}: $valorTexto", fontSize = dimensiones.tamanoTextoBase)
        }
    }

    val resultado = sesion.resultado
    Text("Result", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
    if (resultado == null) {
        Text(
            "No result — the session was abandoned before the identification was finished.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )
    } else {
        Text("${resultado.label} (${resultado.nivel})", fontSize = dimensiones.tamanoTextoBase)
        for (candidato in resultado.top3) {
            Text(
                "${candidato.especie} — ${candidato.scorePorcentaje}%",
                fontSize = dimensiones.tamanoTextoBase,
            )
            // Sesiones de antes del riesgo no lo traen: no se muestra nada.
            candidato.riesgo?.let { riesgo ->
                val nivel = runCatching { NivelRiesgo.valueOf(riesgo) }.getOrNull()
                Text(
                    "Misidentification risk: ${nivel?.let(::nombreNivel) ?: riesgo}",
                    fontSize = dimensiones.tamanoTextoBase,
                    fontStyle = FontStyle.Italic,
                )
                for (razon in candidato.razonesRiesgo) {
                    Text("• $razon", fontSize = dimensiones.tamanoTextoBase)
                }
            }
        }
    }

    BotonPrincipal(texto = "Back to list", onClick = onVolver)
}

private fun nombreNivel(nivel: NivelRiesgo): String = when (nivel) {
    NivelRiesgo.BAJO -> "low"
    NivelRiesgo.MEDIO -> "medium"
    NivelRiesgo.ALTO -> "high"
    NivelRiesgo.NO_EVALUABLE -> "not assessable"
}
