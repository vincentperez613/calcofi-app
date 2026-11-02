package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cal.cofi.atlas.Ancla
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.Candidate
import cal.cofi.atlas.Distribucion
import cal.cofi.atlas.EfectoEnFamilias
import cal.cofi.atlas.EstadoInterrogatorio
import cal.cofi.atlas.Fase
import cal.cofi.atlas.Identification
import cal.cofi.atlas.IdentificationEngine
import cal.cofi.atlas.Observation
import cal.cofi.atlas.PasoInterrogatorio
import cal.cofi.atlas.Pregunta
import cal.cofi.atlas.Stage
import cal.cofi.atlas.TipoPregunta
import cal.cofi.atlas.efectoEnFamilias
import cal.cofi.atlas.evaluarRiesgo
import cal.cofi.atlas.siguientePaso
import cal.cofi.atlas.valorParaTabla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Segunda pantalla del flujo: el interrogatorio completo, por fases
 * (Forma -> Meristica -> Pigmentacion; ver Interrogatorio.kt). Una pregunta
 * a la vez; cuando ya no queda ninguna util en ninguna fase entrega el
 * resultado via [onTerminar].
 *
 * Absorbe lo que antes era PantallaFiltroGrueso: los conteos con tabla por
 * familia (miomeros, anal, dorsal) son ahora preguntas "ancla" de la fase
 * Meristica, que se usan en los dos niveles — familia y especie — y se
 * piden UNA sola vez. Conservan lo que el filtro grueso hacia bien: la vista
 * previa de cuantas familias quedan, el aviso cuando ninguna familia
 * compatible tiene especies cargadas, y el de "casi siempre es un error de
 * conteo".
 *
 * Una sola pila de "Atras" para todo: deshace la ultima respuesta aunque eso
 * cruce de una fase a otra. [sesionId] ya existe (App.kt la crea al salir de
 * la captura); cada respuesta se guarda en el momento.
 */
@Composable
fun PantallaPregunta(
    data: AtlasData,
    stage: Stage,
    tallaMm: Double,
    sesionId: String,
    mesColecta: Int?,
    familyFilter: Set<String>? = null,
    onAtras: () -> Unit,
    onTerminar: (Identification, List<Observation>, familias: Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ancho = LocalAncho.current
    val dimensiones = LocalDimensiones.current
    val engine = remember(data) { IdentificationEngine(data) }
    val familiasCargadas = remember(data) { data.species.map { it.family }.toSet() }

    var estado by remember(familyFilter) { mutableStateOf(EstadoInterrogatorio(familias = familyFilter)) }
    // Un estado completo por cada respuesta, tomado justo antes de
    // incorporarla: "Atras" restaura el ultimo y el motor recalcula
    // exactamente la misma pregunta. Si no queda nada (primera pregunta),
    // "Atras" vuelve a la captura via onAtras.
    var historial by remember { mutableStateOf<List<EstadoInterrogatorio>>(emptyList()) }
    var pregunta by remember { mutableStateOf<Pregunta?>(null) }
    var plausibles by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var mostrarIndicador by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    // Respuesta a un ancla que todavia no se aplica porque hay que avisar
    // algo antes (ver AvisoFamilias). Se avisa ANTES de aplicar: despues,
    // el resultado ya perdio el conteo crudo para explicar el porque.
    var pendiente by remember { mutableStateOf<RespuestaPendiente?>(null) }

    // siguientePaso() nunca en la composicion: con el atlas completo son
    // ~230 especies x ~30 caracteres por pregunta, en cada respuesta. Si
    // tarda mas de 150ms se avisa en vez de congelar la UI en silencio.
    LaunchedEffect(version) {
        mostrarIndicador = false
        val avisoLento = launch {
            delay(150)
            mostrarIndicador = true
        }
        val actual = estado
        val paso = withContext(Dispatchers.Default) {
            siguientePaso(engine, stage, actual, tallaMm, REGLAS_APP)
        }
        avisoLento.cancel()
        mostrarIndicador = false

        plausibles = paso.plausibles
        when (paso) {
            is PasoInterrogatorio.Preguntar -> pregunta = paso.pregunta
            is PasoInterrogatorio.Terminar -> {
                pregunta = null
                // La distribucion la marca el tecnico en el resultado; aca
                // todavia nadie la marco.
                val riesgos = paso.identificacion.candidates.take(3).associate {
                    it.species.species to evaluarRiesgo(mesColecta, it.species.lifeHistory, Distribucion.NO_SE)
                }
                Almacen.registrarResultado(sesionId, paso.identificacion, riesgos)
                onTerminar(paso.identificacion, actual.observaciones, actual.familias)
            }
        }
    }

    fun aplicar(p: Pregunta, respuestas: List<Observation>, familias: Set<String>?) {
        for (r in respuestas) Almacen.agregarObservacion(sesionId, r)
        historial = historial + estado
        estado = estado.responder(p, respuestas, familias)
        pendiente = null
        version++
    }

    /**
     * Un ancla primero pasa por la tabla por familia; si el resultado
     * merece un aviso, la respuesta queda pendiente hasta que el tecnico
     * decida. Lo que no es ancla (o no se pudo contar) se aplica directo.
     */
    fun responder(p: Pregunta, respuestas: List<Observation>) {
        val ancla = p.ancla
        val valor = valorParaTabla(p, respuestas)
        val efecto = if (ancla != null && valor != null) {
            efectoEnFamilias(data.coarse, ancla.tablaFamilia, valor, estado.familias, familiasCargadas)
        } else {
            null
        }
        when {
            efecto == null -> aplicar(p, respuestas, estado.familias)
            efecto.sinFamiliaCompatible || efecto.sinEspeciesCargadas ->
                pendiente = RespuestaPendiente(p, respuestas, efecto)
            else -> aplicar(p, respuestas, efecto.familias)
        }
    }

    fun retroceder() {
        if (pendiente != null) {
            pendiente = null
            return
        }
        val previo = historial.lastOrNull()
        if (previo != null) {
            estado = previo
            historial = historial.dropLast(1)
            version++
        } else {
            onAtras()
        }
    }

    ContenedorPantalla(modifier) {
        IndicadorFases(pregunta?.fase ?: estado.fase, dimensiones)
        Text("${conteo(plausibles.size, "candidate", "candidates")} remaining", fontSize = dimensiones.tamanoTextoBase)
        BotonSecundario(texto = "Back", onClick = ::retroceder)

        if (mostrarIndicador) {
            IndicadorCalculo(dimensiones)
        }

        val panelPregunta: @Composable () -> Unit = {
            val actual = pregunta
            if (actual != null) {
                // version en la clave: el texto tecleado es de ESTA pregunta;
                // al avanzar o retroceder se empieza en blanco.
                key(version) {
                    PanelPregunta(
                        data = data,
                        pregunta = actual,
                        familiasPrevias = estado.familias,
                        familiasCargadas = familiasCargadas,
                        bloqueado = pendiente != null,
                        dimensiones = dimensiones,
                        onEditar = { pendiente = null },
                        onResponder = { respuestas -> responder(actual, respuestas) },
                    )
                }
                pendiente?.let { p ->
                    AvisoFamilias(
                        pendiente = p,
                        dimensiones = dimensiones,
                        onRevisar = { pendiente = null },
                        onSoloEspecie = { aplicar(p.pregunta, p.respuestas, estado.familias) },
                        onAplicarDeTodosModos = { aplicar(p.pregunta, p.respuestas, p.efecto.familias) },
                    )
                }
            }
        }

        if (ancho == Ancho.AMPLIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) { panelPregunta() }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
                ) { PanelCandidatos(plausibles, dimensiones) }
            }
        } else {
            panelPregunta()
        }
    }
}

private data class RespuestaPendiente(
    val pregunta: Pregunta,
    val respuestas: List<Observation>,
    val efecto: EfectoEnFamilias,
)

// ---------------------------------------------------------------- fases

/** Lo que ve el tecnico: tres fases. Miomeros y aletas son las dos mitades de Meristica. */
private val FASES_VISIBLES = listOf("Body shape", "Meristics", "Pigmentation")

private fun Fase.indiceVisible(): Int = when (this) {
    Fase.FORMA -> 0
    Fase.MIOMEROS, Fase.ALETAS -> 1
    Fase.PIGMENTACION -> 2
}

private fun Fase.detalle(): String? = when (this) {
    Fase.MIOMEROS -> "Myomeres first."
    Fase.ALETAS -> "Now fin rays and skeletal counts, as far as they have formed."
    else -> null
}

@Composable
private fun IndicadorFases(fase: Fase, dimensiones: Dimensiones) {
    val actual = fase.indiceVisible()
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
            FASES_VISIBLES.forEachIndexed { i, nombre ->
                if (i > 0) Text("›", fontSize = dimensiones.tamanoTextoBase)
                Text(
                    nombre,
                    fontSize = dimensiones.tamanoTextoBase,
                    fontWeight = if (i == actual) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        i == actual -> MaterialTheme.colorScheme.primary
                        i < actual -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
        fase.detalle()?.let {
            Text(it, fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun IndicadorCalculo(dimensiones: Dimensiones) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
    ) {
        CircularProgressIndicator()
        Text("Calculating...", fontSize = dimensiones.tamanoTextoBase)
    }
}

// ------------------------------------------------------------- preguntas

@Composable
private fun PanelPregunta(
    data: AtlasData,
    pregunta: Pregunta,
    familiasPrevias: Set<String>?,
    familiasCargadas: Set<String>,
    bloqueado: Boolean,
    dimensiones: Dimensiones,
    onEditar: () -> Unit,
    onResponder: (List<Observation>) -> Unit,
) {
    val noSe = { onResponder(pregunta.clavesPedidas.map { Observation(it) }) }

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
        when (val tipo = pregunta.tipo) {
            is TipoPregunta.SiNo -> {
                Text(PROMPTS.getValue(pregunta.clave), fontSize = dimensiones.tamanoTextoBase)
                BotonPrincipal(
                    texto = "Present",
                    onClick = { onResponder(listOf(Observation(pregunta.clave, categorical = tipo.valorSi))) },
                )
                BotonPrincipal(
                    texto = "Absent",
                    onClick = { onResponder(listOf(Observation(pregunta.clave, categorical = tipo.valorNo))) },
                )
                BotonSecundario(texto = "Unknown / not visible", onClick = noSe)
            }
            is TipoPregunta.Opciones -> {
                Text(PROMPTS.getValue(pregunta.clave), fontSize = dimensiones.tamanoTextoBase)
                SelectorOpciones(
                    clave = pregunta.clave,
                    valores = tipo.valores,
                    onElegir = { onResponder(listOf(Observation(pregunta.clave, categorical = it))) },
                )
                BotonSecundario(texto = "Unknown / not visible", onClick = noSe)
            }
            TipoPregunta.Conteo -> PanelConteo(
                data, pregunta, familiasPrevias, familiasCargadas, bloqueado, dimensiones, onEditar, onResponder, noSe,
            )
            is TipoPregunta.ConteoAleta -> PanelAleta(
                data, pregunta, tipo, familiasPrevias, familiasCargadas, bloqueado, dimensiones,
                onEditar, onResponder, noSe,
            )
        }
    }
}

@Composable
private fun PanelConteo(
    data: AtlasData,
    pregunta: Pregunta,
    familiasPrevias: Set<String>?,
    familiasCargadas: Set<String>,
    bloqueado: Boolean,
    dimensiones: Dimensiones,
    onEditar: () -> Unit,
    onResponder: (List<Observation>) -> Unit,
    noSe: () -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    // Se valida el texto ACTUAL en cada recomposicion, no el ultimo valor
    // que llego a parsear bien.
    val resultado = validarConteo(texto)
    val valor = (resultado as? ResultadoValidacion.Valido)?.valor

    Text(PROMPTS.getValue(pregunta.clave), fontSize = dimensiones.tamanoTextoBase)
    CampoNumerico(
        valor = texto,
        onValorCambia = {
            texto = it
            onEditar()
        },
        etiqueta = "Count",
        mensajeError = explicarConteo(resultado),
    )
    pregunta.ancla?.let { ancla ->
        VistaPreviaFamilias(data, ancla, valor, familiasPrevias, familiasCargadas, faltaParte = false, dimensiones)
    }
    BotonPrincipal(
        texto = "Submit",
        habilitado = valor != null && !bloqueado,
        onClick = { valor?.let { onResponder(listOf(Observation(pregunta.clave, value = it))) } },
    )
    BotonSecundario(texto = "Unknown / not visible", onClick = noSe, habilitado = !bloqueado)
}

/**
 * Espinas y radios blandos de una aleta, en una sola pregunta. Cada campo
 * vacio es "no se" para esa parte: lo que si se conto se compara igual. Solo
 * hace falta uno de los dos para responder.
 */
@Composable
private fun PanelAleta(
    data: AtlasData,
    pregunta: Pregunta,
    tipo: TipoPregunta.ConteoAleta,
    familiasPrevias: Set<String>?,
    familiasCargadas: Set<String>,
    bloqueado: Boolean,
    dimensiones: Dimensiones,
    onEditar: () -> Unit,
    onResponder: (List<Observation>) -> Unit,
    noSe: () -> Unit,
) {
    val aleta = tipo.aleta
    var textoEspinas by remember { mutableStateOf("") }
    var textoRadios by remember { mutableStateOf("") }
    val resultadoEspinas = validarConteo(textoEspinas)
    val resultadoRadios = validarConteo(textoRadios)

    fun parte(resultado: ResultadoValidacion): Double? = (resultado as? ResultadoValidacion.Valido)?.valor
    fun aceptable(resultado: ResultadoValidacion) =
        resultado is ResultadoValidacion.Valido || resultado == ResultadoValidacion.Vacio

    val espinas = if (tipo.pedirEspinas) parte(resultadoEspinas) else null
    val radios = parte(resultadoRadios)
    val listo = (radios != null || espinas != null) &&
        aceptable(resultadoRadios) && (!tipo.pedirEspinas || aceptable(resultadoEspinas))

    val respuestas = buildList {
        if (tipo.pedirEspinas) add(Observation(aleta.espinas, value = espinas))
        add(Observation(aleta.radios, value = radios))
    }

    Text(PROMPTS_ALETA.getValue(aleta.id), fontSize = dimensiones.tamanoTextoBase)
    if (tipo.pedirEspinas) {
        Text(AYUDA_ESPINAS_Y_RADIOS, fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        CampoNumerico(
            valor = textoEspinas,
            onValorCambia = {
                textoEspinas = it
                onEditar()
            },
            etiqueta = "Spines",
            mensajeError = explicarConteo(resultadoEspinas),
        )
    }
    CampoNumerico(
        valor = textoRadios,
        onValorCambia = {
            textoRadios = it
            onEditar()
        },
        etiqueta = "Soft rays",
        mensajeError = explicarConteo(resultadoRadios),
    )
    pregunta.ancla?.let { ancla ->
        val total = valorParaTabla(pregunta, respuestas)
        VistaPreviaFamilias(
            data, ancla, total, familiasPrevias, familiasCargadas,
            faltaParte = total == null && (espinas != null || radios != null),
            dimensiones = dimensiones,
        )
    }
    BotonPrincipal(
        texto = "Submit",
        habilitado = listo && !bloqueado,
        onClick = { if (listo) onResponder(respuestas) },
    )
    BotonSecundario(texto = "Unknown / not visible", onClick = noSe, habilitado = !bloqueado)
}

/**
 * Cuantas familias quedarian con el numero que se esta tecleando. Con
 * debounce: sin el, cada digito dispara un recorrido de las ~170 familias
 * mientras el tecnico todavia esta escribiendo. Corre en Dispatchers.Default.
 */
@OptIn(FlowPreview::class)
@Composable
private fun VistaPreviaFamilias(
    data: AtlasData,
    ancla: Ancla,
    valor: Double?,
    familiasPrevias: Set<String>?,
    familiasCargadas: Set<String>,
    faltaParte: Boolean,
    dimensiones: Dimensiones,
) {
    var efecto by remember { mutableStateOf<EfectoEnFamilias?>(null) }
    val valorActual by rememberUpdatedState(valor)

    LaunchedEffect(ancla) {
        snapshotFlow { valorActual }
            .debounce(300)
            .collectLatest { v ->
                efecto = v?.let {
                    withContext(Dispatchers.Default) {
                        efectoEnFamilias(data.coarse, ancla.tablaFamilia, it, familiasPrevias, familiasCargadas)
                    }
                }
            }
    }

    if (faltaParte) {
        Text(
            "Filtering by family needs both counts (spines and soft rays). " +
                "With only one, it is compared against species ranges only.",
            fontSize = dimensiones.tamanoTextoBase,
            fontStyle = FontStyle.Italic,
        )
        return
    }
    val e = efecto ?: return
    if (valor == null) return
    Text(
        "With this count, ${conteo(e.deLaTabla.size, "family", "families")} would remain, " +
            "${e.conEspeciesCargadas.size} of them with species loaded." +
            if (e.noListadasConservadas.isNotEmpty()) {
                " Also still in play: ${conteo(e.noListadasConservadas.size, "loaded family", "loaded families")} " +
                    "not listed in the family table."
            } else {
                ""
            },
        fontSize = dimensiones.tamanoTextoBase,
    )
}

@Composable
private fun AvisoFamilias(
    pendiente: RespuestaPendiente,
    dimensiones: Dimensiones,
    onRevisar: () -> Unit,
    onSoloEspecie: () -> Unit,
    onAplicarDeTodosModos: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
        if (pendiente.efecto.sinFamiliaCompatible) {
            Text(
                "No family is compatible with this count. This is almost always a counting " +
                    "error, not an unknown species.",
                fontSize = dimensiones.tamanoTextoBase,
                color = MaterialTheme.colorScheme.error,
            )
            BotonPrincipal(texto = "Recheck the count", onClick = onRevisar)
            // El conteo crudo nunca se pierde: si el tecnico esta seguro, se
            // guarda y se compara contra las especies, solo que no filtra
            // familias.
            BotonSecundario(texto = "Use it without the family filter", onClick = onSoloEspecie)
        } else {
            // Las familias que quedan SI existen, pero ninguna tiene especies
            // en species.json todavia.
            Text(
                "None of the compatible families has species in this version of the app. " +
                    "You can continue without this filter or recheck the count.",
                fontSize = dimensiones.tamanoTextoBase,
                color = MaterialTheme.colorScheme.error,
            )
            BotonPrincipal(texto = "Skip this filter", onClick = onSoloEspecie)
            BotonSecundario(texto = "Apply anyway", onClick = onAplicarDeTodosModos)
            BotonSecundario(texto = "Recheck the count", onClick = onRevisar)
        }
    }
}

// --------------------------------------------------------------- opciones

/**
 * Dibujo de cada opcion categorica, por (caracter, valor). Vacio hasta que
 * lleguen los datos de forma; mientras, cada tarjeta muestra el espacio
 * reservado para su dibujo ([DibujoPendiente]).
 */
val DIBUJOS_OPCION: Map<Pair<String, String>, @Composable (Color, Modifier) -> Unit> = emptyMap()

@Composable
private fun SelectorOpciones(clave: String, valores: List<String>, onElegir: (String) -> Unit) {
    val ancho = LocalAncho.current
    val dimensiones = LocalDimensiones.current
    val etiquetas = OPCIONES_CATEGORICAS[clave].orEmpty().associate { it.valor to it.texto }
    val porFila = if (ancho == Ancho.COMPACTO) 2 else 3

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
        for (fila in valores.chunked(porFila)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
                for (valor in fila) {
                    TarjetaIlustrada(
                        texto = etiquetas[valor] ?: valor,
                        seleccionada = false,
                        onClick = { onElegir(valor) },
                        dibujo = DIBUJOS_OPCION[clave to valor] ?: { color, mod -> DibujoPendiente(color, mod) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Relleno para que la ultima fila no estire sus tarjetas.
                repeat(porFila - fila.size) {
                    Column(Modifier.weight(1f)) {}
                }
            }
        }
    }
}

/** Marco punteado: el lugar donde va el dibujo de una opcion que todavia no lo tiene. */
@Composable
private fun DibujoPendiente(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grosor = size.minDimension * 0.03f
        drawRoundRect(
            color = color.copy(alpha = 0.4f),
            cornerRadius = CornerRadius(size.minDimension * 0.1f),
            style = Stroke(width = grosor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(grosor * 3, grosor * 3))),
        )
    }
}

// ------------------------------------------------------------- candidatos

/** Con el atlas completo pueden quedar cientos: se listan los primeros y se dice cuantos faltan. */
private const val CANDIDATOS_VISIBLES = 15

@Composable
private fun PanelCandidatos(plausibles: List<Candidate>, dimensiones: Dimensiones) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
    ) {
        Text("Candidates", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
        // plausibles ya viene ordenado por score descendente (IdentificationEngine.rank).
        for (c in plausibles.take(CANDIDATOS_VISIBLES)) {
            Text(
                "${c.species.species} — ${(c.score * 100).toInt()}%",
                fontSize = dimensiones.tamanoTextoBase,
            )
        }
        if (plausibles.size > CANDIDATOS_VISIBLES) {
            Text(
                "and ${plausibles.size - CANDIDATOS_VISIBLES} more",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
