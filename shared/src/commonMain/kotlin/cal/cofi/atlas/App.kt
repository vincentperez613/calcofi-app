package cal.cofi.atlas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cal.cofi.atlas.ui.Almacen
import cal.cofi.atlas.ui.Ancho
import cal.cofi.atlas.ui.CapturaState
import cal.cofi.atlas.ui.ContenedorPantalla
import cal.cofi.atlas.ui.IconoAnalisis
import cal.cofi.atlas.ui.IconoEspecies
import cal.cofi.atlas.ui.IconoHistorial
import cal.cofi.atlas.ui.LocalAncho
import cal.cofi.atlas.ui.LocalDimensiones
import cal.cofi.atlas.ui.PantallaCaptura
import cal.cofi.atlas.ui.PantallaEspecies
import cal.cofi.atlas.ui.PantallaHistorial
import cal.cofi.atlas.ui.PantallaPregunta
import cal.cofi.atlas.ui.PantallaResultado
import cal.cofi.atlas.ui.ProveedorDeLayout

/** Los pasos DENTRO de la seccion Analisis. App.kt es, por ahora, el unico dueno de en cual estamos. */
private enum class PasoId { CAPTURA, PREGUNTA, RESULTADO }

private data class DatosPregunta(
    val stage: Stage,
    val tallaMm: Double,
    val sesionId: String,
    val mesColecta: Int?,
    val zonaColecta: String?,
)

private data class DatosResultado(
    val stage: Stage,
    val tallaMm: Double,
    val sesionId: String,
    val mesColecta: Int?,
    val zonaColecta: String?,
    val familyFilter: Set<String>?,
    val observaciones: List<Observation>,
    val identificacion: Identification,
)

/** Las secciones de la app, elegidas desde la barra de navegacion en App.kt. */
private enum class Seccion { ANALISIS, ESPECIES, HISTORIAL }

@Composable
@Preview
fun App() {
    MaterialTheme {
        ProveedorDeLayout {
            val repositorio = remember { AtlasRepository() }
            val estadoCarga by repositorio.estado.collectAsState()

            LaunchedEffect(repositorio) { repositorio.cargar() }

            when (val estado = estadoCarga) {
                is EstadoCarga.Cargando -> MensajeCentrado("Loading atlas...")
                is EstadoCarga.Error -> MensajeCentrado("Failed to load the atlas: ${estado.mensaje}")
                is EstadoCarga.Listo -> AppConNavegacion(estado.data)
            }
        }
    }
}

/**
 * Dueno de la seccion activa (Analisis / Historial) y de la barra que la
 * elige: NavigationRail a la izquierda en AMPLIO, NavigationBar abajo en
 * COMPACTO/MEDIO. Las pantallas no conocen esta navegacion.
 *
 * FlujoAnalisis y PantallaHistorial quedan SIEMPRE compuestos como
 * hermanos dentro de un Box, alternando solo su tamano (0.dp vs
 * fillMaxSize) segun la seccion activa. Si en cambio se gatearan con un
 * if/when sobre la llamada al composable, cambiar de seccion destruiria
 * el remember de la que quedo afuera — y perder el progreso a mitad de
 * una identificacion es justo lo que no se puede permitir en campo.
 */
@Composable
private fun AppConNavegacion(data: AtlasData) {
    val ancho = LocalAncho.current
    var seccion by remember { mutableStateOf(Seccion.ANALISIS) }

    // El inset va aca, no en el Row/Column de afuera: NavigationBar y
    // NavigationRail ya insetean sus propios bordes contra las barras del
    // sistema por su cuenta (comportamiento default de Material3). Si el
    // padding de la barra de estado se aplicara tambien afuera, se sumaria
    // al que ya aplica el nav — un hueco doble arriba del rail en AMPLIO.
    val contenido: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Box(if (seccion == Seccion.ANALISIS) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                FlujoAnalisis(data)
            }
            Box(if (seccion == Seccion.ESPECIES) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                PantallaEspecies(data = data, activa = seccion == Seccion.ESPECIES)
            }
            Box(if (seccion == Seccion.HISTORIAL) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                PantallaHistorial(activa = seccion == Seccion.HISTORIAL)
            }
        }
    }

    if (ancho == Ancho.AMPLIO) {
        Row(Modifier.fillMaxSize()) {
            BarraLateral(seccion, onSeccion = { seccion = it })
            Box(Modifier.weight(1f)) { contenido() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { contenido() }
            BarraInferior(seccion, onSeccion = { seccion = it })
        }
    }
}

@Composable
private fun BarraInferior(seccion: Seccion, onSeccion: (Seccion) -> Unit) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        NavigationBarItem(
            selected = seccion == Seccion.ANALISIS,
            onClick = { onSeccion(Seccion.ANALISIS) },
            icon = { IconoAnalisis() },
            label = { Text("Identify") },
        )
        NavigationBarItem(
            selected = seccion == Seccion.ESPECIES,
            onClick = { onSeccion(Seccion.ESPECIES) },
            icon = { IconoEspecies() },
            label = { Text("Species") },
        )
        NavigationBarItem(
            selected = seccion == Seccion.HISTORIAL,
            onClick = { onSeccion(Seccion.HISTORIAL) },
            icon = { IconoHistorial() },
            label = { Text("History") },
        )
    }
}

@Composable
private fun BarraLateral(seccion: Seccion, onSeccion: (Seccion) -> Unit) {
    NavigationRail {
        NavigationRailItem(
            selected = seccion == Seccion.ANALISIS,
            onClick = { onSeccion(Seccion.ANALISIS) },
            icon = { IconoAnalisis() },
            label = { Text("Identify") },
        )
        NavigationRailItem(
            selected = seccion == Seccion.ESPECIES,
            onClick = { onSeccion(Seccion.ESPECIES) },
            icon = { IconoEspecies() },
            label = { Text("Species") },
        )
        NavigationRailItem(
            selected = seccion == Seccion.HISTORIAL,
            onClick = { onSeccion(Seccion.HISTORIAL) },
            icon = { IconoHistorial() },
            label = { Text("History") },
        )
    }
}

/**
 * Captura y Pregunta quedan montadas permanentemente una vez alcanzadas por
 * primera vez (mismo patron de Box + tamano 0dp que separa las secciones
 * Analisis/Especies/Historial en [AppConNavegacion]): "Atras" solo cambia
 * [pasoVisible], nunca desmonta la pantalla anterior, asi que su estado
 * interno (preguntas ya respondidas, en todas las fases) sigue ahi cuando
 * se vuelve. Pregunta no se monta de entrada porque necesita la sesion,
 * que solo existe despues de la captura.
 *
 * La sesion se crea al salir de la captura, antes de la primera pregunta
 * — sea de la fase que sea —, asi que toda respuesta tiene donde
 * guardarse. Si el tecnico vuelve a la captura y solo cambia mes o zona,
 * se actualiza esa misma sesion y el interrogatorio sigue donde estaba;
 * si cambia etapa o talla, las preguntas ya no aplican: sesion nueva e
 * interrogatorio desde cero (la vieja queda en el historial como sin
 * terminar, con lo que alcanzo a responderse).
 *
 * "Nueva captura" es la unica accion que de verdad reinicia: vacia los
 * datos de los pasos siguientes, lo que los desmonta.
 */
@Composable
private fun FlujoAnalisis(data: AtlasData) {
    var pasoVisible by remember { mutableStateOf(PasoId.CAPTURA) }
    var captura by remember { mutableStateOf(CapturaState()) }
    var datosPregunta by remember { mutableStateOf<DatosPregunta?>(null) }
    var datosResultado by remember { mutableStateOf<DatosResultado?>(null) }

    fun visibilidad(paso: PasoId): Modifier =
        if (pasoVisible == paso) Modifier.fillMaxSize() else Modifier.size(0.dp)

    Box(Modifier.fillMaxSize()) {
        Box(visibilidad(PasoId.CAPTURA)) {
            PantallaCaptura(
                data = data,
                estado = captura,
                onCambio = { captura = it },
                onContinuar = {
                    val etapa = captura.etapa
                    val talla = captura.tallaMm
                    if (etapa != null && talla != null) {
                        val zona = captura.zonaColecta.trim().ifEmpty { null }
                        val previa = datosPregunta
                        datosPregunta = if (previa != null && previa.stage == etapa && previa.tallaMm == talla) {
                            Almacen.actualizarDatosColecta(previa.sesionId, captura.mesColecta, zona)
                            previa.copy(mesColecta = captura.mesColecta, zonaColecta = zona)
                        } else {
                            val id = Almacen.iniciarSesion(etapa, talla, captura.mesColecta, zona)
                            datosResultado = null
                            DatosPregunta(etapa, talla, id, captura.mesColecta, zona)
                        }
                        pasoVisible = PasoId.PREGUNTA
                    }
                },
            )
        }

        val pregunta = datosPregunta
        if (pregunta != null) {
            Box(visibilidad(PasoId.PREGUNTA)) {
                // Una sesion nueva es un interrogatorio nuevo: la clave
                // descarta el estado de la anterior.
                key(pregunta.sesionId) {
                    PantallaPregunta(
                        data = data,
                        stage = pregunta.stage,
                        tallaMm = pregunta.tallaMm,
                        sesionId = pregunta.sesionId,
                        mesColecta = pregunta.mesColecta,
                        onAtras = { pasoVisible = PasoId.CAPTURA },
                        onTerminar = { identificacion, observaciones, familias ->
                            datosResultado = DatosResultado(
                                stage = pregunta.stage,
                                tallaMm = pregunta.tallaMm,
                                sesionId = pregunta.sesionId,
                                mesColecta = pregunta.mesColecta,
                                zonaColecta = pregunta.zonaColecta,
                                familyFilter = familias,
                                observaciones = observaciones,
                                identificacion = identificacion,
                            )
                            pasoVisible = PasoId.RESULTADO
                        },
                    )
                }
            }
        }

        val resultado = datosResultado
        if (resultado != null) {
            Box(visibilidad(PasoId.RESULTADO)) {
                PantallaResultado(
                    data = data,
                    stage = resultado.stage,
                    tallaMm = resultado.tallaMm,
                    sesionId = resultado.sesionId,
                    mesColecta = resultado.mesColecta,
                    zonaColecta = resultado.zonaColecta,
                    familyFilter = resultado.familyFilter,
                    observaciones = resultado.observaciones,
                    identificacion = resultado.identificacion,
                    onNuevaCaptura = {
                        captura = CapturaState()
                        datosPregunta = null
                        datosResultado = null
                        pasoVisible = PasoId.CAPTURA
                    },
                )
            }
        }
    }
}

@Composable
private fun MensajeCentrado(texto: String) {
    val dimensiones = LocalDimensiones.current
    ContenedorPantalla(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        Text(texto, fontSize = dimensiones.tamanoTextoBase)
    }
}
