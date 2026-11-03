package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.Candidate
import cal.cofi.atlas.Distribucion
import cal.cofi.atlas.FigureRef
import cal.cofi.atlas.Identification
import cal.cofi.atlas.IdentificationEngine
import cal.cofi.atlas.NivelRiesgo
import cal.cofi.atlas.Observation
import cal.cofi.atlas.Species
import cal.cofi.atlas.Stage
import cal.cofi.atlas.TaxonLevel
import cal.cofi.atlas.describirMeses
import cal.cofi.atlas.evaluarRiesgo
import calcofilarvas.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Tercera y ultima pantalla del flujo: el resultado de
 * IdentificationEngine.resolve(stage, obs, tallaMm), ya calculado por
 * PantallaPregunta cuando el interrogatorio termino. Arriba, fijo, cuantas
 * coincidencias hay; abajo, un carrusel vertical con una tarjeta por
 * coincidencia: la lamina recortada del atlas, la evidencia para que un
 * revisor pueda auditar el porque, y el riesgo de error.
 *
 * El riesgo de error por temporada y distribucion (Riesgo.kt) va con el
 * texto del atlas al lado. Acompaña al resultado: no cambia el orden, no
 * quita a nadie y no toca el titular.
 */
@Composable
fun PantallaResultado(
    data: AtlasData,
    stage: Stage,
    tallaMm: Double,
    sesionId: String,
    mesColecta: Int?,
    zonaColecta: String?,
    familyFilter: Set<String>?,
    observaciones: List<Observation>,
    identificacion: Identification,
    onNuevaCaptura: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensiones = LocalDimensiones.current
    val alcance = rememberCoroutineScope()

    // El resultado puede recalcularse una vez, sin el filtro de familia, si
    // el filtro dejo el pool en cero. identificacionActual es lo que se
    // muestra; identificacion (el parametro) sigue siendo la version
    // original con la que entro esta pantalla.
    var identificacionActual by remember(identificacion) { mutableStateOf(identificacion) }
    var filtroDescartado by remember(identificacion) { mutableStateOf(false) }
    var reintentando by remember(identificacion) { mutableStateOf(false) }
    // Lo que el tecnico marco para cada candidato: si su zona de colecta
    // cae en la distribucion de la especie. Sin marca = no se.
    var distribuciones by remember(identificacionActual) { mutableStateOf(mapOf<String, Distribucion>()) }

    // "sin datos" solo puede pasar por un pool vacio, y el pool solo queda
    // vacio si familyFilter excluyo a todo el mundo — con solo Engraulidae
    // cargada, cualquier filtro que aisle otra familia cae aca. Explicar
    // el porque en vez de repetir la etiqueta generica del motor.
    val vaciaPorFiltro = identificacionActual.candidates.isEmpty() &&
        !familyFilter.isNullOrEmpty() && !filtroDescartado

    if (vaciaPorFiltro) {
        ContenedorPantalla(modifier) {
            TituloPantalla("No result with this filter")
            val familiasFiltro = familyFilter.sorted().joinToString(", ")
            val familiasCargadas = remember(data) { data.species.map { it.family }.toSet().sorted() }
            // Los nombres de las cargadas solo si son pocas: con el atlas
            // completo son ~100 y la lista taparia el mensaje.
            val cargadas = if (familiasCargadas.size <= 5) {
                "only ${familiasCargadas.joinToString(", ")}, ${conteo(data.species.size, "species", "species")}"
            } else {
                "${conteo(familiasCargadas.size, "family", "families")}, ${conteo(data.species.size, "species", "species")}"
            }
            Text(
                "The meristic family filter left these families: $familiasFiltro. None of them " +
                    "has species loaded in this version ($cargadas).",
                fontSize = dimensiones.tamanoTextoBase,
            )
            BotonPrincipal(
                texto = "Retry without the family filter",
                habilitado = !reintentando,
                onClick = {
                    reintentando = true
                    alcance.launch {
                        val nuevo = withContext(Dispatchers.Default) {
                            IdentificationEngine(data).resolve(stage, observaciones, null, tallaMm)
                        }
                        // Es el resultado que el tecnico va a ver y a marcar:
                        // tiene que quedar en la sesion, no el vacio de antes.
                        val riesgos = nuevo.candidates.take(3).associate {
                            it.species.species to evaluarRiesgo(mesColecta, it.species.lifeHistory, Distribucion.NO_SE)
                        }
                        Almacen.registrarResultado(sesionId, nuevo, riesgos)
                        identificacionActual = nuevo
                        filtroDescartado = true
                        reintentando = false
                    }
                },
            )
            if (reintentando) {
                Text("Recalculating without the filter...", fontSize = dimensiones.tamanoTextoBase)
            }
            BotonPrincipal(texto = "New specimen", onClick = onNuevaCaptura)
        }
        return
    }

    // Almacen solo guarda los tres primeros del ranking (top1..top3 del CSV).
    val guardadas = remember(identificacionActual) {
        identificacionActual.candidates.take(3).mapTo(mutableSetOf()) { it.species.species }
    }

    ContenedorPantalla(modifier, desplazable = false) {
        // La clave vuelve el carrusel a la primera tarjeta si el resultado
        // se recalcula sin el filtro de familia.
        key(identificacionActual) {
            CarruselCoincidencias(
                data = data,
                stage = stage,
                identificacion = identificacionActual,
                guardadas = guardadas,
                mesColecta = mesColecta,
                zonaColecta = zonaColecta,
                distribuciones = distribuciones,
                onDistribucion = { especie, valor ->
                    distribuciones = distribuciones + (especie.species to valor)
                    Almacen.registrarRiesgo(sesionId, especie.species, evaluarRiesgo(mesColecta, especie.lifeHistory, valor))
                },
                dimensiones = dimensiones,
                modifier = Modifier.weight(1f),
            )
        }
        BotonPrincipal(texto = "New specimen", onClick = onNuevaCaptura)
    }
}

// ------------------------------------------------------------ coincidencias

/**
 * Las coincidencias son los candidatos que comparten el titular
 * (Identification.tied): una sola, o varias — cientos si casi todo se
 * contesto "no se". Arriba solo se dice cuantas son, nunca "sin resolver":
 * eso no le dice nada al tecnico, las tarjetas si.
 *
 * Carrusel vertical: una tarjeta por coincidencia, con la siguiente
 * asomando abajo para que se note que hay mas. Cada tarjeta scrollea por
 * dentro (el riesgo y el texto del atlas no caben en un telefono); al
 * llegar a su final, el mismo gesto pasa a la siguiente. Las flechas hacen
 * lo mismo sin deslizar — con guantes mojados, o con mouse en escritorio.
 */
@Composable
private fun CarruselCoincidencias(
    data: AtlasData,
    stage: Stage,
    identificacion: Identification,
    guardadas: Set<String>,
    mesColecta: Int?,
    zonaColecta: String?,
    distribuciones: Map<String, Distribucion>,
    onDistribucion: (Species, Distribucion) -> Unit,
    dimensiones: Dimensiones,
    modifier: Modifier = Modifier,
) {
    val tarjetas = identificacion.tied
    if (tarjetas.isEmpty()) {
        TituloPantalla("No matches")
        return
    }
    val estado = rememberPagerState { tarjetas.size }
    val alcance = rememberCoroutineScope()
    val varias = tarjetas.size > 1

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TituloPantalla(conteo(tarjetas.size, "match", "matches"))
                if (varias) {
                    Text("Showing ${estado.currentPage + 1} of ${tarjetas.size}", fontSize = dimensiones.tamanoTextoBase)
                }
            }
            if (varias) {
                BotonFlecha("▲", "Previous match", habilitado = estado.currentPage > 0) {
                    alcance.launch { estado.animateScrollToPage(estado.currentPage - 1) }
                }
                BotonFlecha("▼", "Next match", habilitado = estado.currentPage < tarjetas.size - 1) {
                    alcance.launch { estado.animateScrollToPage(estado.currentPage + 1) }
                }
            }
        }
        // Si todas caen en un mismo genero o familia, eso ya es un resultado.
        val grupo = when (identificacion.level) {
            TaxonLevel.GENUS -> "All in genus ${tarjetas.first().species.genus}."
            TaxonLevel.FAMILY -> "All in family ${tarjetas.first().species.family}."
            else -> null
        }
        grupo?.let { Text(it, fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic) }

        VerticalPager(
            state = estado,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Este hueco es lo que asoma de la tarjeta siguiente.
            contentPadding = PaddingValues(bottom = if (varias) dimensiones.alturaBotonMinima else 0.dp),
            pageSpacing = dimensiones.espaciado / 2,
            key = { tarjetas[it].species.id },
        ) { pagina ->
            val c = tarjetas[pagina]
            TarjetaCandidato(
                data = data,
                stage = stage,
                candidato = c,
                guardada = c.species.species in guardadas,
                mesColecta = mesColecta,
                zonaColecta = zonaColecta,
                distribucion = distribuciones[c.species.species] ?: Distribucion.NO_SE,
                onDistribucion = { onDistribucion(c.species, it) },
                dimensiones = dimensiones,
            )
        }
    }
}

@Composable
private fun BotonFlecha(simbolo: String, descripcion: String, habilitado: Boolean, onClick: () -> Unit) {
    val dimensiones = LocalDimensiones.current
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier
            .heightIn(min = dimensiones.alturaBotonMinima)
            .widthIn(min = dimensiones.alturaBotonMinima),
    ) {
        Text(
            simbolo,
            fontSize = dimensiones.tamanoTextoBase,
            modifier = Modifier.clearAndSetSemantics { contentDescription = descripcion },
        )
    }
}

/**
 * Todo lo de un candidato en una tarjeta: su figura del atlas para la
 * etapa, la evidencia para auditar el porque y su riesgo de error. La
 * figura va en cada tarjeta y no solo en la del primero: comparar los
 * dibujos de los empatados es lo que le queda al tecnico para desempatar.
 */
@Composable
private fun TarjetaCandidato(
    data: AtlasData,
    stage: Stage,
    candidato: Candidate,
    guardada: Boolean,
    mesColecta: Int?,
    zonaColecta: String?,
    distribucion: Distribucion,
    onDistribucion: (Distribucion) -> Unit,
    dimensiones: Dimensiones,
) {
    val ancho = LocalAncho.current
    val forma = RoundedCornerShape(dimensiones.espaciado)
    val riesgo: @Composable () -> Unit = {
        SeccionRiesgo(
            especie = candidato.species,
            mesColecta = mesColecta,
            zonaColecta = zonaColecta,
            distribucion = distribucion,
            onDistribucion = onDistribucion,
            dimensiones = dimensiones,
        )
        if (!guardada) {
            Text(
                "Not stored in the session record: only the top 3 are.",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(forma)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma)
            .verticalScroll(rememberScrollState())
            .padding(dimensiones.espaciado),
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
    ) {
        EncabezadoCandidato(candidato, dimensiones)
        if (ancho == Ancho.AMPLIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
                Column(modifier = Modifier.weight(1f)) {
                    PanelFigura(data, candidato.species, stage, dimensiones)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) {
                    Evidencia(candidato, dimensiones)
                    riesgo()
                }
            }
        } else {
            PanelFigura(data, candidato.species, stage, dimensiones)
            Evidencia(candidato, dimensiones)
            riesgo()
        }
    }
}

@Composable
private fun EncabezadoCandidato(c: Candidate, dimensiones: Dimensiones) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(c.species.species, fontSize = dimensiones.tamanoTextoBase * 1.25f, fontWeight = FontWeight.Bold)
            Text(c.species.family, fontSize = dimensiones.tamanoTextoBase)
        }
        Text(
            "${(c.score * 100).toInt()}%",
            fontSize = dimensiones.tamanoTextoTitulo,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * El riesgo de error de un candidato: el nivel, el porque, y el texto
 * original del atlas al lado — para que el tecnico pueda discrepar de la
 * lectura automatica de los meses, que no paso por revision humana.
 */
@Composable
private fun SeccionRiesgo(
    especie: Species,
    mesColecta: Int?,
    zonaColecta: String?,
    distribucion: Distribucion,
    onDistribucion: (Distribucion) -> Unit,
    dimensiones: Dimensiones,
) {
    val lh = especie.lifeHistory
    val evaluacion = evaluarRiesgo(mesColecta, lh, distribucion)

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text(
            textoNivel(evaluacion.nivel),
            fontSize = dimensiones.tamanoTextoBase,
            fontWeight = FontWeight.Bold,
            color = if (evaluacion.nivel == NivelRiesgo.ALTO) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        for (razon in evaluacion.razones) {
            Text("• $razon", fontSize = dimensiones.tamanoTextoBase)
        }

        lh?.spawningSeason?.let {
            Text("Spawning season (atlas): “$it”", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
        if (lh != null && lh.spawningMonths.isNotEmpty()) {
            val pico = if (lh.spawningPeak.isNotEmpty()) "; peak: ${describirMeses(lh.spawningPeak)}" else ""
            Text(
                "Months parsed automatically from that text, not reviewed: ${describirMeses(lh.spawningMonths)}$pico.",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }

        val rango = lh?.range
        if (rango != null) {
            Text("Geographic range (atlas): “$rango”", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
            val zona = zonaColecta?.let { " ($it)" } ?: ""
            Text("Is your collection locality$zona within this range?", fontSize = dimensiones.tamanoTextoBase)
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
                for ((valor, texto) in listOf(
                    Distribucion.SI to "Yes",
                    Distribucion.NO to "No",
                    Distribucion.NO_SE to "Unknown",
                )) {
                    BotonSeleccionable(
                        texto = texto,
                        seleccionado = distribucion == valor,
                        onClick = { onDistribucion(valor) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun textoNivel(nivel: NivelRiesgo): String = when (nivel) {
    NivelRiesgo.BAJO -> "Misidentification risk: low"
    NivelRiesgo.MEDIO -> "Misidentification risk: medium"
    NivelRiesgo.ALTO -> "Misidentification risk: high"
    NivelRiesgo.NO_EVALUABLE -> "Misidentification risk: not assessable"
}

@Composable
private fun Evidencia(c: Candidate, dimensiones: Dimensiones) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        if (c.supporting.isNotEmpty()) {
            Text(
                "Supporting: " + c.supporting.joinToString("; ") { traducirCaracter(it) },
                fontSize = dimensiones.tamanoTextoBase,
            )
        }
        if (c.contradicting.isNotEmpty()) {
            Text(
                "Conflicting: " + c.contradicting.joinToString("; ") { traducirCaracter(it) },
                fontSize = dimensiones.tamanoTextoBase,
            )
        }
        if (c.supporting.isEmpty() && c.contradicting.isEmpty()) {
            Text("No characters evaluated yet.", fontSize = dimensiones.tamanoTextoBase)
        }
    }
}

// -------------------------------------------------------------------- figura

@Composable
private fun PanelFigura(data: AtlasData, especie: Species, stage: Stage, dimensiones: Dimensiones) {
    val figura = remember(especie, stage) { data.figuresFor(especie, stage).firstOrNull() }
    if (figura == null) {
        Text("No figure available for this stage.", fontSize = dimensiones.tamanoTextoBase)
        return
    }
    TarjetaFigura(figura, dimensiones)
}

/**
 * Una figura recortada de una lamina, con su rotulo y advertencias —
 * unidad reutilizable entre PantallaResultado (la mejor figura de cada
 * candidato, una por tarjeta del carrusel) y PantallaEspecies (todas las
 * figuras de una especie, en el catalogo). No dupliques esto: cualquier
 * pantalla que necesite mostrar una FigureRef pasa por aca.
 */
@Composable
fun TarjetaFigura(figura: FigureRef, dimensiones: Dimensiones) {
    var estado by remember(figura.plate) { mutableStateOf<EstadoImagen>(EstadoImagen.Cargando) }

    LaunchedEffect(figura.plate) {
        estado = EstadoImagen.Cargando
        estado = try {
            val enCache = CacheLaminas.obtener(figura.plate)
            val bitmap = enCache ?: withContext(Dispatchers.Default) {
                Res.readBytes("files/figures/${figura.plate}").decodeToImageBitmap()
            }
            if (enCache == null) CacheLaminas.guardar(figura.plate, bitmap)
            EstadoImagen.Listo(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EstadoImagen.Error(e.message ?: "unknown error")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        when (val actual = estado) {
            is EstadoImagen.Cargando -> Text("Loading figure...", fontSize = dimensiones.tamanoTextoBase)
            is EstadoImagen.Error ->
                Text("Could not load figure: ${actual.mensaje}", fontSize = dimensiones.tamanoTextoBase)
            is EstadoImagen.Listo -> RecorteFigura(actual.bitmap, figura, dimensiones)
        }

        // displayLabel(), nunca species.species: el atlas rotula varias
        // larvas tempranas como "Anchoa sp." aunque la ficha sea de una
        // especie puntual. Usar el nombre de la ficha prometeria una
        // precision que el dibujo no respalda.
        Text(figura.displayLabel(), fontSize = dimensiones.tamanoTextoBase, fontWeight = FontWeight.Bold)

        if (figura.stageSource != "caption") {
            Text("Stage inferred from body length, not from the figure caption.", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
        if (figura.uncertain) {
            Text("Identification marked as uncertain in the atlas.", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
    }
}

/**
 * Recorta [box] de [bitmap] al dibujar — nunca se guarda un bitmap recortado
 * aparte, solo se dibuja esa porcion de la lamina completa ya cacheada.
 *
 * [box] viene del atlas y puede estar en las coordenadas de una imagen
 * distinta a la lamina empacada (ya paso: cajas en px de una imagen de
 * 2556px contra laminas de 1200px). Un recorte fuera de rango no debe
 * fallar en silencio ni reventar: si [box] no cae dentro de [bitmap], se
 * ignora y se muestra la lamina completa con una advertencia visible.
 */
@Composable
private fun RecorteFigura(bitmap: ImageBitmap, figura: FigureRef, dimensiones: Dimensiones) {
    val box = figura.box
    val dentroDeLimites = box.size == 4 &&
        box[0] >= 0 && box[1] >= 0 &&
        box[2] > box[0] && box[3] > box[1] &&
        box[2] <= bitmap.width && box[3] <= bitmap.height

    val origen: IntOffset
    val tamano: IntSize
    if (dentroDeLimites) {
        origen = IntOffset(box[0], box[1])
        tamano = IntSize(box[2] - box[0], box[3] - box[1])
    } else {
        origen = IntOffset.Zero
        tamano = IntSize(bitmap.width, bitmap.height)
    }

    // Papel del color del fondo y tinta del color del texto: en modo oscuro
    // la lamina sale invertida y sin un rectangulo blanco encima (ver filtroLamina).
    val papel = MaterialTheme.colorScheme.background
    val tinta = MaterialTheme.colorScheme.onBackground
    val filtro = remember(papel, tinta) { filtroLamina(papel, tinta) }

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tamano.width.toFloat() / tamano.height.toFloat()),
        ) {
            drawImage(
                image = bitmap,
                srcOffset = origen,
                srcSize = tamano,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = filtro,
            )
        }
        if (!dentroDeLimites) {
            Text(
                "DATA WARNING: crop box [${box.joinToString(",")}] falls outside the " +
                    "plate (${bitmap.width}×${bitmap.height} px). Showing the full plate.",
                fontSize = dimensiones.tamanoTextoBase,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private sealed interface EstadoImagen {
    data object Cargando : EstadoImagen
    data class Listo(val bitmap: ImageBitmap) : EstadoImagen
    data class Error(val mensaje: String) : EstadoImagen
}

/**
 * Cache LRU de laminas ya decodificadas, no de recortes: una lamina trae
 * varias larvas recortadas, asi que reusarla entre figuras vale la pena.
 * Con ~508 figuras eventuales, cachear cada lamina que se haya visto
 * reventaria la memoria — maximo 3, y se decodifica bajo demanda, nunca
 * por adelantado.
 */
private object CacheLaminas {
    private const val CAPACIDAD = 3
    private val orden = ArrayDeque<String>()
    private val mapa = mutableMapOf<String, ImageBitmap>()

    fun obtener(plate: String): ImageBitmap? = mapa[plate]?.also {
        orden.remove(plate)
        orden.addLast(plate)
    }

    fun guardar(plate: String, imagen: ImageBitmap) {
        if (!mapa.containsKey(plate) && mapa.size >= CAPACIDAD) {
            orden.removeFirstOrNull()?.let { mapa.remove(it) }
        }
        orden.remove(plate)
        orden.addLast(plate)
        mapa[plate] = imagen
    }
}
