package cal.cofi.atlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.Species
import calcofilarvas.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.decodeToImageBitmap

@Serializable
private data class EntradaImagenEspecie(
    val id: String,
    val file: String,
)

private val jsonIndiceImagenes = Json { ignoreUnknownKeys = true }

@Composable
internal fun CatalogoEspecies(data: AtlasData, modifier: Modifier = Modifier) {
    val dimensiones = LocalDimensiones.current
    val familias = remember(data) {
        data.species.groupBy { it.family }
            .toSortedMap()
            .mapValues { (_, especies) -> especies.sortedBy { it.species } }
    }
    val indiceBusqueda = remember(data) { construirIndiceImagenes(data) }
    var miniaturas by remember(data) { mutableStateOf<Map<String, String>?>(null) }
    var imagenesFullres by remember(data) { mutableStateOf<Map<String, String>?>(null) }
    var errorCarga by remember(data) { mutableStateOf<String?>(null) }
    var especieSeleccionadaId by rememberSaveable { mutableStateOf<String?>(null) }
    var familiasExpandidas by rememberSaveable(stateSaver = SaverFamiliasIndice) {
        mutableStateOf(emptySet())
    }
    var texto by remember { mutableStateOf("") }
    var coincidencias by remember { mutableStateOf<Set<String>?>(null) }
    var consultaAplicada by remember { mutableStateOf("") }
    val especiesConImagen = remember(data, miniaturas) {
        miniaturas?.let { indice -> data.species.count { it.id in indice } }
    }

    LaunchedEffect(data) {
        try {
            miniaturas = cargarIndiceImagenes("thumbnails")
            imagenesFullres = cargarIndiceImagenes("fullres")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorCarga = e.message ?: "error desconocido"
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(indiceBusqueda) {
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
                    indiceBusqueda.filterTo(mutableSetOf()) { it.normalizado.contains(normalizada) }
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
        ContenedorPantalla(modifier, anchoCompleto = true) {
            DetalleEspecie(
                data = data,
                especie = especieSeleccionada,
                dimensiones = dimensiones,
                imagen = imagenesFullres?.get(especieSeleccionada.id),
                carpetaImagen = "fullres",
                textoVolverArriba = "Volver al catálogo",
                onVolver = { especieSeleccionadaId = null },
            )
        }
        return
    }

    val coincidenciasActuales = coincidencias
    val familiasVisibles = remember(coincidenciasActuales, familias) {
        if (coincidenciasActuales == null) {
            familias
        } else {
            familias
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
                verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
            ) {
                TituloPantalla("Especies")
                Text(
                    buildString {
                        append("${data.species.size} especies en ${familias.size} familias")
                        especiesConImagen?.let { append(" · $it con imagen") }
                    },
                    fontSize = dimensiones.tamanoTextoBase,
                )
                when {
                    errorCarga != null -> Text(
                        "No se pudo cargar el índice de imágenes: $errorCarga",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = dimensiones.tamanoTextoBase,
                    )
                    miniaturas == null || imagenesFullres == null -> Text(
                        "Cargando índice de imágenes...",
                        fontSize = dimensiones.tamanoTextoBase,
                    )
                }
                CampoTexto(
                    valor = texto,
                    onValorCambia = { texto = sanitizarBusqueda(it) },
                    etiqueta = "Buscar especie, género o familia",
                )
                if (texto.isNotEmpty()) {
                    BotonSecundario(texto = "Limpiar búsqueda", onClick = { texto = "" })
                }
            }

            if (coincidenciasActuales != null && familiasVisibles.isEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = dimensiones.espaciado),
                    verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
                ) {
                    Text("Sin resultados para «$consultaAplicada».", fontSize = dimensiones.tamanoTextoBase)
                    BotonSecundario(texto = "Limpiar búsqueda", onClick = { texto = "" })
                }
            } else {
                LazyColumn(
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
                            FilaFamiliaIndice(
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
                                FilaEspecieIndice(
                                    especie = especie,
                                    imagen = miniaturas?.get(especie.id),
                                    dimensiones = dimensiones,
                                    onClick = { especieSeleccionadaId = especie.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun cargarIndiceImagenes(carpeta: String): Map<String, String> {
    val contenido = withContext(Dispatchers.Default) {
        Res.readBytes("files/figures/$carpeta/indice.json").decodeToString()
    }
    val entradas = withContext(Dispatchers.Default) {
        jsonIndiceImagenes.decodeFromString<List<EntradaImagenEspecie>>(contenido)
    }
    return entradas.associate { it.id to it.file }
}

private data class EspecieIndexada(val especie: Species, val normalizado: String)

private fun construirIndiceImagenes(data: AtlasData): List<EspecieIndexada> =
    data.species.map { EspecieIndexada(it, normalizarBusqueda("${it.species} ${it.family}")) }

private fun sanitizarBusqueda(entrada: String): String =
    entrada.filterNot { c ->
        val codigo = c.code
        codigo in 0x00..0x1F || codigo == 0x7F || codigo in 0x80..0x9F
    }.take(60)

private val SaverFamiliasIndice = Saver<Set<String>, String>(
    save = { it.joinToString("") },
    restore = { if (it.isEmpty()) emptySet() else it.split("").toSet() },
)

@Composable
private fun FilaFamiliaIndice(
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
private fun FilaEspecieIndice(
    especie: Species,
    imagen: String?,
    dimensiones: Dimensiones,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensiones.espaciado / 3))
            .clickable(onClick = onClick)
            .padding(vertical = dimensiones.espaciado / 4),
        horizontalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imagen != null) {
            ImagenEspecie(
                archivo = imagen,
                descripcion = especie.species,
                carpetaImagen = "thumbnails",
                modifier = Modifier.size(width = 120.dp, height = 64.dp),
                altura = 64.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 64.dp)
                    .clip(RoundedCornerShape(dimensiones.espaciado / 3))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sin imagen", fontSize = dimensiones.tamanoTextoBase * 0.75f)
            }
        }
        Text(especie.species, fontSize = dimensiones.tamanoTextoBase, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ImagenEspecie(
    archivo: String,
    descripcion: String,
    carpetaImagen: String,
    modifier: Modifier = Modifier,
    altura: Dp?,
) {
    val ruta = "$carpetaImagen/$archivo"
    var estado by remember(ruta) { mutableStateOf<EstadoImagenEspecie>(EstadoImagenEspecie.Cargando) }

    LaunchedEffect(ruta) {
        estado = EstadoImagenEspecie.Cargando
        estado = try {
            val enCache = CacheImagenesEspecies.obtener(ruta)
            val imagen = enCache ?: withContext(Dispatchers.Default) {
                Res.readBytes("files/figures/$ruta").decodeToImageBitmap()
            }
            if (enCache == null) CacheImagenesEspecies.guardar(ruta, imagen)
            EstadoImagenEspecie.Lista(imagen)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EstadoImagenEspecie.Error(e.message ?: "error desconocido")
        }
    }

    when (val actual = estado) {
        EstadoImagenEspecie.Cargando -> Box(
            modifier = modifier
                .let { if (altura == null) it.aspectRatio(3f / 2f) else it.height(altura) }
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        is EstadoImagenEspecie.Error -> Box(
            modifier = modifier
                .let { if (altura == null) it.aspectRatio(3f / 2f) else it.height(altura) }
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("Error: ${actual.mensaje}", color = MaterialTheme.colorScheme.onErrorContainer)
        }
        is EstadoImagenEspecie.Lista -> Image(
            bitmap = actual.bitmap,
            contentDescription = descripcion,
            contentScale = ContentScale.Fit,
            modifier = modifier.let {
                if (altura == null) it.aspectRatio(actual.bitmap.width.toFloat() / actual.bitmap.height)
                else it
            },
        )
    }
}

private sealed interface EstadoImagenEspecie {
    data object Cargando : EstadoImagenEspecie
    data class Lista(val bitmap: ImageBitmap) : EstadoImagenEspecie
    data class Error(val mensaje: String) : EstadoImagenEspecie
}

private object CacheImagenesEspecies {
    private const val CAPACIDAD = 12
    private val orden = ArrayDeque<String>()
    private val mapa = mutableMapOf<String, ImageBitmap>()

    fun obtener(archivo: String): ImageBitmap? = mapa[archivo]?.also {
        orden.remove(archivo)
        orden.addLast(archivo)
    }

    fun guardar(archivo: String, imagen: ImageBitmap) {
        if (!mapa.containsKey(archivo) && mapa.size >= CAPACIDAD) {
            orden.removeFirstOrNull()?.let { mapa.remove(it) }
        }
        orden.remove(archivo)
        orden.addLast(archivo)
        mapa[archivo] = imagen
    }
}
