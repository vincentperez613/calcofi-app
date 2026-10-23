package cal.cofi.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.DiagnosticChar
import cal.cofi.atlas.LifeHistory
import cal.cofi.atlas.Range
import cal.cofi.atlas.Species
import cal.cofi.atlas.describirMeses
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/**
 * Catalogo de lo que la app tiene cargado: familias colapsables, buscador,
 * y detalle por especie (rangos, temporada y distribucion, diagnosticos,
 * figuras). No identifica nada — es referencia, para que el tecnico sepa
 * de antemano que puede y que no puede identificar esta version.
 *
 * [activa] sigue el mismo patron que PantallaHistorial: esta pantalla queda
 * permanentemente compuesta bajo la navegacion de secciones, asi que no
 * hay "onEntrar" natural — pero a diferencia de Historial no hace falta
 * releer nada al activarse (el catalogo es AtlasData, ya en memoria), asi
 * que por ahora [activa] no se usa. Se deja el parametro para simetria y
 * por si en el futuro hace falta (p.ej. resetear el buscador al salir).
 */
@Composable
fun PantallaEspecies(data: AtlasData, activa: Boolean, modifier: Modifier = Modifier) {
    val dimensiones = LocalDimensiones.current

    // Precalculado UNA sola vez por AtlasData: normalizar 435 especies en
    // cada tecla se nota en un telefono de gama baja. familiasOrdenadas
    // tambien se arma una vez — agrupar/ordenar 435 especies en cada
    // recomposicion es trabajo de sobra.
    val indice = remember(data) { construirIndice(data) }
    val familiasOrdenadas = remember(data) {
        data.species.groupBy { it.family }
            .toSortedMap()
            .mapValues { (_, especies) -> especies.sortedBy { it.species } }
    }

    var especieSeleccionadaId by rememberSaveable { mutableStateOf<String?>(null) }
    var familiasExpandidas by rememberSaveable(stateSaver = SaverFamiliasExpandidas) {
        mutableStateOf(emptySet())
    }
    var texto by remember { mutableStateOf("") }
    // null = sin busqueda activa (mostrar todo tal cual). Con busqueda
    // activa pero sin coincidencias, es un set vacio, no null — asi
    // "sin resultados" y "sin buscar" no se confunden.
    var coincidencias by remember { mutableStateOf<Set<String>?>(null) }
    var consultaAplicada by remember { mutableStateOf("") }

    // El filtrado nunca corre en la composicion: con 435 especies se nota
    // en gama baja. snapshotFlow observa el texto ya saneado (ver
    // sanitizarBusqueda en el CampoTexto de abajo); el trim() que decide
    // si "solo espacios" cuenta como vacio pasa aca, justo antes de
    // normalizar y comparar — nunca contra el texto tal como se tecleo.
    @OptIn(FlowPreview::class)
    LaunchedEffect(indice) {
        snapshotFlow { texto }
            .debounce(200)
            .collectLatest { t ->
                val consulta = t.trim()
                if (consulta.isEmpty()) {
                    coincidencias = null
                    consultaAplicada = ""
                    return@collectLatest
                }
                val normalizada = normalizarBusqueda(consulta)
                val encontradas = withContext(Dispatchers.Default) {
                    indice.filterTo(mutableSetOf()) { it.normalizado.contains(normalizada) }
                        .mapTo(mutableSetOf()) { it.especie.id }
                }
                coincidencias = encontradas
                consultaAplicada = consulta
            }
    }

    val especieSeleccionada = remember(especieSeleccionadaId, data) {
        especieSeleccionadaId?.let { id -> data.species.firstOrNull { it.id == id } }
    }

    if (especieSeleccionada != null) {
        ContenedorPantalla(modifier) {
            DetalleEspecie(data, especieSeleccionada, dimensiones, onVolver = { especieSeleccionadaId = null })
        }
        return
    }

    val coincidenciasActuales = coincidencias
    val familiasVisibles = remember(coincidenciasActuales, familiasOrdenadas) {
        if (coincidenciasActuales == null) {
            familiasOrdenadas
        } else {
            familiasOrdenadas
                .mapValues { (_, especies) -> especies.filter { it.id in coincidenciasActuales } }
                .filterValues { it.isNotEmpty() }
        }
    }
    val familiasConCoincidencia = if (coincidenciasActuales == null) emptySet() else familiasVisibles.keys

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = dimensiones.anchoMaximoContenido)
                .align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier.padding(dimensiones.espaciado),
                verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
            ) {
                TituloPantalla("Species")
                Text(
                    "${conteo(data.species.size, "species", "species")} in " +
                        conteo(familiasOrdenadas.size, "family", "families"),
                    fontSize = dimensiones.tamanoTextoBase,
                )
                CampoTexto(
                    valor = texto,
                    onValorCambia = { texto = sanitizarBusqueda(it) },
                    etiqueta = "Search species, genus or family",
                )
                if (texto.isNotEmpty()) {
                    BotonSecundario(texto = "Clear search", onClick = { texto = "" })
                }
            }

            if (coincidenciasActuales != null && familiasVisibles.isEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = dimensiones.espaciado),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) {
                    Text("No results for “$consultaAplicada”.", fontSize = dimensiones.tamanoTextoBase)
                    BotonSecundario(texto = "Clear search", onClick = { texto = "" })
                }
            } else {
                LazyColumn(
                    // weight, no fillMaxSize: esto es un hijo mas de la Column
                    // exterior, despues del encabezado — fillMaxSize() le
                    // pediria a Compose el alto COMPLETO sin restar lo que ya
                    // ocupa el encabezado, y desbordaria.
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = dimensiones.espaciado,
                        end = dimensiones.espaciado,
                        bottom = dimensiones.espaciado,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4),
                ) {
                    for ((familia, especies) in familiasVisibles) {
                        val expandida = familia in familiasExpandidas || familia in familiasConCoincidencia
                        item(key = "familia:$familia") {
                            FilaFamilia(
                                familia = familia,
                                total = especies.size,
                                expandida = expandida,
                                dimensiones = dimensiones,
                                onClick = {
                                    familiasExpandidas = if (familia in familiasExpandidas) {
                                        familiasExpandidas - familia
                                    } else {
                                        familiasExpandidas + familia
                                    }
                                },
                            )
                        }
                        if (expandida) {
                            items(especies, key = { "especie:${it.id}" }) { especie ->
                                FilaEspecie(especie, dimensiones, onClick = { especieSeleccionadaId = especie.id })
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SaverFamiliasExpandidas = Saver<Set<String>, String>(
    save = { it.joinToString("") },
    restore = { if (it.isEmpty()) emptySet() else it.split("").toSet() },
)

// ------------------------------------------------------------ busqueda

private data class IndiceEspecie(val especie: Species, val normalizado: String)

private fun construirIndice(data: AtlasData): List<IndiceEspecie> =
    data.species.map { IndiceEspecie(it, normalizarBusqueda("${it.species} ${it.family}")) }

/**
 * Se aplica en cada tecla, antes de que el texto llegue al estado: nunca
 * hace falta un caracter de control (pegado desde otra app) ni mas de 60
 * caracteres en un buscador. El trim() de "solo espacios cuenta como
 * vacio" es aparte — pasa al comparar, no aca, porque si se trimeara aca
 * el tecnico no podria escribir "Engraulis " para seguir con "mordax".
 */
private fun sanitizarBusqueda(entrada: String): String =
    entrada.filterNot(::esCaracterDeControl).take(60)

private fun esCaracterDeControl(c: Char): Boolean {
    val codigo = c.code
    return codigo in 0x00..0x1F || codigo == 0x7F || codigo in 0x80..0x9F
}

// ------------------------------------------------------------------- lista

@Composable
private fun FilaFamilia(
    familia: String,
    total: Int,
    expandida: Boolean,
    dimensiones: Dimensiones,
    onClick: () -> Unit,
) {
    val forma = RoundedCornerShape(dimensiones.espaciado / 2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surfaceVariant, forma)
            .clickable(onClick = onClick)
            .padding(dimensiones.espaciado),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$familia ($total)", fontSize = dimensiones.tamanoTextoBase, fontWeight = FontWeight.Bold)
        Text(if (expandida) "▾" else "▸", fontSize = dimensiones.tamanoTextoTitulo)
    }
}

@Composable
private fun FilaEspecie(especie: Species, dimensiones: Dimensiones, onClick: () -> Unit) {
    Text(
        especie.species,
        fontSize = dimensiones.tamanoTextoBase,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = dimensiones.espaciado, top = dimensiones.espaciado / 3, bottom = dimensiones.espaciado / 3),
    )
}

// ------------------------------------------------------------------ detalle

@Composable
private fun DetalleEspecie(data: AtlasData, especie: Species, dimensiones: Dimensiones, onVolver: () -> Unit) {
    TituloPantalla(especie.species)
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
