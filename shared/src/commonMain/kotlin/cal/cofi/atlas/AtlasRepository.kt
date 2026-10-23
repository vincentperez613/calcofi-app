package cal.cofi.atlas

import calcofilarvas.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Estado de carga del atlas para que la UI pueda reaccionar sin bloquear
 * mientras se procesan los ~2.4 MB de JSON.
 */
sealed interface EstadoCarga {
    data object Cargando : EstadoCarga
    data class Listo(val data: AtlasData) : EstadoCarga
    data class Error(val mensaje: String) : EstadoCarga
}

/**
 * Espejo minimo del JSON solo para leer el flag de verificacion humana.
 * AtlasLoader.parse (Loader.kt) no lo expone porque Species no lo necesita
 * para el dominio; aqui se lee aparte, contra el mismo species.json, para
 * decidir que especies nunca deben llegar al dispositivo.
 *
 * Candado a prueba de fallos: si el campo no viene en el JSON, la especie
 * se descarta. Solo un `true` explicito la deja pasar. Un default en `true`
 * dejaria pasar datos sin revision por un simple error u omision al
 * generar el JSON; fallar cerrado es el unico default seguro para datos
 * que van a un dispositivo del tecnico.
 */
@Serializable
private data class VerificationDto(
    val id: String,
    val verified_by_human: Boolean = false,
)

private val verificationJson = Json { ignoreUnknownKeys = true }

/**
 * Punto unico de acceso al dataset del atlas.
 *
 * La carga corre en Dispatchers.Default y nunca en el hilo principal: parsear
 * ~2.4 MB de JSON ahi congelaria la UI en un telefono de gama baja. El
 * resultado se cachea, asi que llamar a [cargar] mas de una vez no repite el
 * trabajo.
 */
class AtlasRepository(
    private val readFile: suspend (String) -> String =
        { path -> Res.readBytes("files/$path").decodeToString() },
) {
    private val _estado = MutableStateFlow<EstadoCarga>(EstadoCarga.Cargando)
    val estado: StateFlow<EstadoCarga> = _estado.asStateFlow()

    /** Cuantas especies se descartaron por no tener verified_by_human == true. */
    var descartadasPorVerificacion: Int = 0
        private set

    private val mutex = Mutex()
    private var cache: AtlasData? = null

    suspend fun cargar(): EstadoCarga {
        cache?.let { return EstadoCarga.Listo(it) }

        mutex.withLock {
            cache?.let { return EstadoCarga.Listo(it) }

            return withContext(Dispatchers.Default) {
                try {
                    // Las tres rutas son las mismas que usa AtlasLoader.parse
                    // internamente (Loader.kt): se prelee su contenido con
                    // Res.readBytes (suspend) y se le entrega a parse como un
                    // lookup sincrono, que es lo que su firma espera.
                    val speciesRaw = readFile("data/species.json")
                    val coarseRaw = readFile("data/coarse_filter.json")
                    val figuresRaw = readFile("data/figures.json")
                    val contents = mapOf(
                        "data/species.json" to speciesRaw,
                        "data/coarse_filter.json" to coarseRaw,
                        "data/figures.json" to figuresRaw,
                    )

                    val parsed = AtlasLoader.parse { path -> contents.getValue(path) }

                    val verificado = verificationJson
                        .decodeFromString<List<VerificationDto>>(speciesRaw)
                        .associate { it.id to it.verified_by_human }

                    val especiesVerificadas = parsed.species.filter { verificado[it.id] ?: false }
                    descartadasPorVerificacion = parsed.species.size - especiesVerificadas.size

                    val data = AtlasData(especiesVerificadas, parsed.coarse, parsed.figures)
                    cache = data
                    EstadoCarga.Listo(data).also { _estado.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    EstadoCarga.Error(e.message ?: "failed to load the atlas").also { _estado.value = it }
                }
            }
        }
    }
}
