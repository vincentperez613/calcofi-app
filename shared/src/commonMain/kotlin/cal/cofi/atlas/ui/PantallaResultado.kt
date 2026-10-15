package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
 * PantallaPregunta cuando el interrogatorio termino. Muestra el titular con
 * el nivel taxonomico en palabras (nunca un enum crudo), los tres mejores
 * candidatos con su evidencia para que un revisor pueda auditar el porque,
 * y la lamina recortada del atlas para el mejor candidato.
 *
 * Cada candidato lleva ademas su riesgo de error por temporada y
 * distribucion (Riesgo.kt), con el texto del atlas al lado. Acompaña al
 * resultado: no cambia el orden, no quita a nadie y no toca el titular.
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
    val ancho = LocalAncho.current
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
            TituloPantalla("Sin resultado con este filtro")
            val familiasFiltro = familyFilter.sorted().joinToString(", ")
            val familiasCargadas = remember(data) { data.species.map { it.family }.toSet().sorted() }
            // Los nombres de las cargadas solo si son pocas: con el atlas
            // completo son ~100 y la lista taparia el mensaje.
            val cargadas = if (familiasCargadas.size <= 5) {
                "solo ${familiasCargadas.joinToString(", ")}, ${data.species.size} especies"
            } else {
                "${familiasCargadas.size} familias, ${data.species.size} especies"
            }
            Text(
                "El filtro por conteos dejó estas familias: $familiasFiltro. Ninguna tiene " +
                    "especies cargadas en esta versión ($cargadas).",
                fontSize = dimensiones.tamanoTextoBase,
            )
            BotonPrincipal(
                texto = "Reintentar sin el filtro de familia",
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
                Text("Recalculando sin el filtro...", fontSize = dimensiones.tamanoTextoBase)
            }
            BotonPrincipal(texto = "Nueva captura", onClick = onNuevaCaptura)
        }
        return
    }

    val especiePrincipal = identificacionActual.candidates.firstOrNull()?.species

    val panelFigura: @Composable () -> Unit = {
        if (especiePrincipal != null) {
            PanelFigura(data, especiePrincipal, stage, dimensiones)
        }
    }
    val panelCandidatos: @Composable () -> Unit = {
        PanelCandidatosResultado(
            top3 = identificacionActual.candidates.take(3),
            mesColecta = mesColecta,
            zonaColecta = zonaColecta,
            distribuciones = distribuciones,
            onDistribucion = { especie, valor ->
                distribuciones = distribuciones + (especie.species to valor)
                Almacen.registrarRiesgo(sesionId, especie.species, evaluarRiesgo(mesColecta, especie.lifeHistory, valor))
            },
            dimensiones = dimensiones,
        )
    }

    ContenedorPantalla(modifier) {
        Titular(identificacionActual, dimensiones)

        if (ancho == Ancho.AMPLIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) { panelFigura() }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) { panelCandidatos() }
            }
        } else {
            panelFigura()
            panelCandidatos()
        }

        BotonPrincipal(texto = "Nueva captura", onClick = onNuevaCaptura)
    }
}

// ------------------------------------------------------------------ titular

@Composable
private fun Titular(id: Identification, dimensiones: Dimensiones) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        TituloPantalla(id.label)
        val nota = when (id.level) {
            TaxonLevel.SPECIES -> null
            TaxonLevel.GENUS -> "Con estos datos solo se puede llegar al género."
            TaxonLevel.FAMILY -> "Con estos datos solo se puede llegar a la familia."
            TaxonLevel.UNRESOLVED -> "Ningún candidato se distingue de los demás con lo respondido."
        }
        if (nota != null) {
            Text(nota, fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
    }
}

// --------------------------------------------------------------- candidatos

@Composable
private fun PanelCandidatosResultado(
    top3: List<Candidate>,
    mesColecta: Int?,
    zonaColecta: String?,
    distribuciones: Map<String, Distribucion>,
    onDistribucion: (Species, Distribucion) -> Unit,
    dimensiones: Dimensiones,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado)) {
        Text("Candidatos", fontSize = dimensiones.tamanoTextoTitulo, fontWeight = FontWeight.Bold)
        for (c in top3) {
            TarjetaCandidatoResultado(c, dimensiones)
            SeccionRiesgo(
                especie = c.species,
                mesColecta = mesColecta,
                zonaColecta = zonaColecta,
                distribucion = distribuciones[c.species.species] ?: Distribucion.NO_SE,
                onDistribucion = { onDistribucion(c.species, it) },
                dimensiones = dimensiones,
            )
        }
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
            Text("Temporada según el atlas: «$it»", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
        if (lh != null && lh.spawningMonths.isNotEmpty()) {
            val pico = if (lh.spawningPeak.isNotEmpty()) "; pico: ${describirMeses(lh.spawningPeak)}" else ""
            Text(
                "Meses leídos automáticamente de ese texto, sin revisar: ${describirMeses(lh.spawningMonths)}$pico.",
                fontSize = dimensiones.tamanoTextoBase,
                fontStyle = FontStyle.Italic,
            )
        }

        val rango = lh?.range
        if (rango != null) {
            Text("Distribución según el atlas: «$rango»", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
            val zona = zonaColecta?.let { " ($it)" } ?: ""
            Text("¿Tu zona de colecta$zona cae dentro?", fontSize = dimensiones.tamanoTextoBase)
            Row(horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
                for ((valor, texto) in listOf(
                    Distribucion.SI to "Sí",
                    Distribucion.NO to "No",
                    Distribucion.NO_SE to "No sé",
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
    NivelRiesgo.BAJO -> "Riesgo de error: bajo"
    NivelRiesgo.MEDIO -> "Riesgo de error: medio"
    NivelRiesgo.ALTO -> "Riesgo de error: alto"
    NivelRiesgo.NO_EVALUABLE -> "Riesgo de error: no evaluable"
}

@Composable
private fun TarjetaCandidatoResultado(c: Candidate, dimensiones: Dimensiones) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 4)) {
        Text(
            "${c.species.species} — ${(c.score * 100).toInt()}%",
            fontSize = dimensiones.tamanoTextoBase,
            fontWeight = FontWeight.Bold,
        )
        if (c.supporting.isNotEmpty()) {
            Text(
                "A favor: " + c.supporting.joinToString("; ") { traducirCaracter(it) },
                fontSize = dimensiones.tamanoTextoBase,
            )
        }
        if (c.contradicting.isNotEmpty()) {
            Text(
                "En contra: " + c.contradicting.joinToString("; ") { traducirCaracter(it) },
                fontSize = dimensiones.tamanoTextoBase,
            )
        }
        if (c.supporting.isEmpty() && c.contradicting.isEmpty()) {
            Text("Sin evidencia evaluada todavía.", fontSize = dimensiones.tamanoTextoBase)
        }
    }
}

// -------------------------------------------------------------------- figura

@Composable
private fun PanelFigura(data: AtlasData, especie: Species, stage: Stage, dimensiones: Dimensiones) {
    val figura = remember(especie, stage) { data.figuresFor(especie, stage).firstOrNull() }
    if (figura == null) {
        Text("No hay figura disponible para esta etapa.", fontSize = dimensiones.tamanoTextoBase)
        return
    }
    TarjetaFigura(figura, dimensiones)
}

/**
 * Una figura recortada de una lamina, con su rotulo y advertencias —
 * unidad reutilizable entre PantallaResultado (la mejor figura del
 * candidato principal, una por pantalla) y PantallaEspecies (todas las
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
            EstadoImagen.Error(e.message ?: "error desconocido")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        when (val actual = estado) {
            is EstadoImagen.Cargando -> Text("Cargando figura...", fontSize = dimensiones.tamanoTextoBase)
            is EstadoImagen.Error ->
                Text("No se pudo cargar la figura: ${actual.mensaje}", fontSize = dimensiones.tamanoTextoBase)
            is EstadoImagen.Listo -> RecorteFigura(actual.bitmap, figura, dimensiones)
        }

        // displayLabel(), nunca species.species: el atlas rotula varias
        // larvas tempranas como "Anchoa sp." aunque la ficha sea de una
        // especie puntual. Usar el nombre de la ficha prometeria una
        // precision que el dibujo no respalda.
        Text(figura.displayLabel(), fontSize = dimensiones.tamanoTextoBase, fontWeight = FontWeight.Bold)

        if (figura.stageSource != "caption") {
            Text("Etapa inferida de la talla, no del pie de figura.", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
        }
        if (figura.uncertain) {
            Text("Identificación incierta según el atlas.", fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
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
            )
        }
        if (!dentroDeLimites) {
            Text(
                "ADVERTENCIA (datos): el recorte [${box.joinToString(",")}] cae fuera de la " +
                    "lamina (${bitmap.width}×${bitmap.height} px). Mostrando la página completa.",
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
