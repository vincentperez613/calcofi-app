package cal.cofi.atlas.ui

import cal.cofi.atlas.Distribucion
import cal.cofi.atlas.EvaluacionRiesgo
import cal.cofi.atlas.Identification
import cal.cofi.atlas.NivelRiesgo
import cal.cofi.atlas.Observation
import cal.cofi.atlas.Stage
import cal.cofi.atlas.Temporada
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Una observacion cruda, tal como la respondio el tecnico. [valor]/[categorico]
 * ambos null significa "no se / no visible" — una respuesta valida, no un
 * hueco en los datos.
 *
 * [numeradorMm]/[denominadorMm] son de la morfometria que ya no se pregunta
 * (Sn-A/BL, HW/HL...): ninguna observacion nueva los llena, pero NO se
 * quitan. Almacen reescribe el archivo completo en cada operacion; si estos
 * campos desaparecieran de la clase, las medidas ya guardadas en los
 * dispositivos se borrarian en el siguiente guardado. El dato crudo es el
 * activo: con los mm se puede recalcular, con el porcentaje solo no.
 */
@Serializable
data class ObservacionGuardada(
    val character: String,
    val valor: Double? = null,
    val categorico: String? = null,
    val numeradorMm: Double? = null,
    val denominadorMm: Double? = null,
)

/**
 * Un candidato del resultado. Los campos de riesgo (nombres de los enums de
 * Riesgo.kt) llevan default: las sesiones guardadas antes de que existiera
 * el riesgo siguen cargando, simplemente sin el.
 */
@Serializable
data class CandidatoGuardado(
    val especie: String,
    val scorePorcentaje: Int,
    val riesgo: String? = null,
    val temporada: String? = null,
    val distribucion: String? = null,
    val razonesRiesgo: List<String> = emptyList(),
)

@Serializable
data class ResultadoGuardado(
    val label: String,
    val nivel: String,
    val top3: List<CandidatoGuardado>,
)

/**
 * Una sesion de captura completa. [resultado] es null mientras el
 * interrogatorio sigue abierto o si el tecnico abandono antes de terminar
 * — pero [tallaMm], [etapa] y [observaciones] ya estan guardados desde que
 * se contestaron, no solo al final. Una identificacion se puede recalcular
 * en cuanto mejoren las reglas; una medicion que nunca se guardo, no.
 *
 * Todo campo nuevo lleva default: si una sesion vieja no decodifica,
 * [Almacen] empieza una lista nueva y el siguiente guardado borra el
 * historial completo.
 */
@Serializable
data class SesionGuardada(
    val id: String,
    val fechaHoraIso: String,
    val tallaMm: Double,
    val etapa: String,
    val observaciones: List<ObservacionGuardada> = emptyList(),
    val resultado: ResultadoGuardado? = null,
    /** Mes de colecta, 1-12, o null si el tecnico no lo sabe. */
    val mesColecta: Int? = null,
    /** Zona de colecta en texto libre, o null si no se capturo. */
    val zonaColecta: String? = null,
)

/**
 * Lo que cambia por plataforma: donde vive el archivo, como se lee/escribe
 * texto plano, y como sale el CSV del dispositivo. JSON plano, no SQLite —
 * son unas cuantas sesiones por dia, no hace falta una base de datos.
 */
expect object AlmacenamientoPlataforma {
    /** null si el archivo todavia no existe (primera vez que se usa la app). */
    fun leerTexto(): String?

    fun escribirTexto(contenido: String)

    /**
     * El dato crudo no sirve si se queda atrapado en el telefono. En
     * Android abre el share sheet (correo, WhatsApp, Drive, o guardar en
     * Descargas si el tecnico elige eso) via FileProvider, sin pedir
     * permisos de almacenamiento — util en un barco sin señal, que igual
     * puede guardarlo localmente desde ahi. En escritorio abre un dialogo
     * de guardar estandar. Devuelve un mensaje para mostrarle al tecnico.
     */
    fun compartirCsv(contenido: String, nombreArchivo: String): String

    fun ahoraIso(): String
}

private val jsonSesiones = Json { ignoreUnknownKeys = true }

/**
 * Lee el archivo de sesiones. Separado de [Almacen] (que pasa por el
 * archivo real del dispositivo) para poder probar que una sesion guardada
 * con un formato viejo sigue cargando. Lanza si el texto no decodifica.
 */
internal fun decodificarSesiones(texto: String): List<SesionGuardada> =
    jsonSesiones.decodeFromString(texto)

internal fun codificarSesiones(sesiones: List<SesionGuardada>): String =
    jsonSesiones.encodeToString(sesiones)

/**
 * Punto unico de acceso a las sesiones guardadas. Lee-modifica-escribe el
 * archivo completo en cada operacion: con el volumen esperado (unas
 * cuantas sesiones por dia, cada una con un puñado de observaciones) el
 * archivo nunca es lo bastante grande para que eso importe, y así cada
 * respuesta queda en disco de inmediato en vez de acumularse en memoria
 * hasta un guardado final que podria no llegar a pasar.
 */
object Almacen {
    private fun leerTodas(): List<SesionGuardada> {
        val texto = AlmacenamientoPlataforma.leerTexto()
        if (texto.isNullOrBlank()) return emptyList()
        return try {
            decodificarSesiones(texto)
        } catch (e: Exception) {
            // Archivo corrupto o de un formato viejo: no reventar la app por
            // esto. Se pierde la lectura, pero el proximo guardado empieza
            // una lista nueva en vez de tumbar el historial completo.
            emptyList()
        }
    }

    private fun escribirTodas(sesiones: List<SesionGuardada>) {
        AlmacenamientoPlataforma.escribirTexto(codificarSesiones(sesiones))
    }

    private fun actualizar(id: String, cambio: (SesionGuardada) -> SesionGuardada) {
        escribirTodas(leerTodas().map { if (it.id == id) cambio(it) else it })
    }

    /** Mas reciente primero. */
    fun listar(): List<SesionGuardada> = leerTodas().sortedByDescending { it.fechaHoraIso }

    /**
     * Arranca una sesion nueva y la deja en disco de inmediato, con
     * observaciones vacias. Se llama al salir de la captura — antes de la
     * primera pregunta, sea de la fase que sea — para que un abandono a
     * mitad de interrogatorio no pierda ni siquiera eso.
     */
    fun iniciarSesion(stage: Stage, tallaMm: Double, mesColecta: Int?, zonaColecta: String?): String {
        val ahora = AlmacenamientoPlataforma.ahoraIso()
        val id = "$ahora-${(1000..9999).random()}"
        val nueva = SesionGuardada(
            id = id,
            fechaHoraIso = ahora,
            tallaMm = tallaMm,
            etapa = stage.name,
            mesColecta = mesColecta,
            zonaColecta = zonaColecta,
        )
        escribirTodas(leerTodas() + nueva)
        return id
    }

    /** El tecnico volvio a la captura y corrigio mes o zona: misma sesion, no una nueva. */
    fun actualizarDatosColecta(id: String, mesColecta: Int?, zonaColecta: String?) {
        actualizar(id) { it.copy(mesColecta = mesColecta, zonaColecta = zonaColecta) }
    }

    /** Se llama una vez por cada respuesta, no al final: esa es la regla que no se negocia. */
    fun agregarObservacion(id: String, obs: Observation) {
        actualizar(id) {
            it.copy(observaciones = it.observaciones + ObservacionGuardada(obs.character, obs.value, obs.categorical))
        }
    }

    /**
     * [riesgos] por nombre de especie, para los candidatos que se muestran.
     * Los que no esten (o todos, si viene vacio) quedan sin riesgo.
     */
    fun registrarResultado(id: String, identificacion: Identification, riesgos: Map<String, EvaluacionRiesgo>) {
        actualizar(id) { sesion ->
            sesion.copy(
                resultado = ResultadoGuardado(
                    label = identificacion.label,
                    nivel = identificacion.level.name,
                    top3 = identificacion.candidates.take(3).map {
                        CandidatoGuardado(it.species.species, (it.score * 100).toInt())
                            .conRiesgo(riesgos[it.species.species])
                    },
                ),
            )
        }
    }

    /** El tecnico marco si su zona cae en la distribucion de [especie]: se guarda en el momento. */
    fun registrarRiesgo(id: String, especie: String, evaluacion: EvaluacionRiesgo) {
        actualizar(id) { sesion ->
            val resultado = sesion.resultado ?: return@actualizar sesion
            sesion.copy(
                resultado = resultado.copy(
                    top3 = resultado.top3.map { if (it.especie == especie) it.conRiesgo(evaluacion) else it },
                ),
            )
        }
    }

    /** Una fila por sesion; las observaciones van en una columna por caracter distinto. */
    fun exportarCsv(): String {
        val sesiones = listar()
        return AlmacenamientoPlataforma.compartirCsv(construirCsv(sesiones), "calcofilarvas_sessions.csv")
    }
}

private fun CandidatoGuardado.conRiesgo(evaluacion: EvaluacionRiesgo?): CandidatoGuardado =
    if (evaluacion == null) this
    else copy(
        riesgo = evaluacion.nivel.name,
        temporada = evaluacion.temporada.name,
        distribucion = evaluacion.distribucion.name,
        razonesRiesgo = evaluacion.razones,
    )

private const val BOM_UTF8 = "﻿"

/**
 * Las sesiones guardan los enums de Riesgo.kt por nombre ("ALTO", "FUERA");
 * el CSV los saca en ingles, como el resto de la UI. El JSON guardado no
 * cambia. Un nombre que no se reconoce sale tal cual.
 */
private val CSV_NIVEL = mapOf(
    NivelRiesgo.BAJO to "low",
    NivelRiesgo.MEDIO to "medium",
    NivelRiesgo.ALTO to "high",
    NivelRiesgo.NO_EVALUABLE to "not_assessable",
)
private val CSV_TEMPORADA = mapOf(
    Temporada.PICO to "peak",
    Temporada.EN_TEMPORADA to "in_season",
    Temporada.CONTIGUO to "adjacent",
    Temporada.FUERA to "out_of_season",
    Temporada.SIN_DATO to "no_data",
)
private val CSV_DISTRIBUCION = mapOf(
    Distribucion.SI to "yes",
    Distribucion.NO to "no",
    Distribucion.NO_SE to "unknown",
)

private fun aCsv(guardado: String?, valores: Map<out Enum<*>, String>): String =
    guardado?.let { g -> valores.entries.firstOrNull { it.key.name == g }?.value ?: g } ?: ""

internal fun construirCsv(sesiones: List<SesionGuardada>): String {
    // Columnas por caracter, no por posicion (Obs1, Obs2, ...): el orden
    // de las preguntas es adaptativo y varia de sesion a sesion, asi que
    // una columna posicional mezclaria caracteres distintos entre filas.
    val todasLasObs = sesiones.flatMap { it.observaciones }
    val caracteres = todasLasObs.map { it.character }.toSortedSet()
    // De esos, los que alguna vez se guardaron con numerador/denominador
    // (las razones morfometricas de sesiones viejas) ganan dos columnas
    // extra con el dato crudo en mm — el porcentaje solo (columna de
    // arriba) no alcanza para recalcular.
    val caracteresRazon = todasLasObs.filter { it.numeradorMm != null }.map { it.character }.toSortedSet()

    // Las columnas nuevas van despues de las que ya existian, para no
    // correr de lugar las que alguien ya este leyendo por posicion.
    val encabezados = listOf(
        "id", "datetime", "body_length_mm", "stage", "result_label", "result_level",
        "top1_species", "top1_score", "top2_species", "top2_score", "top3_species", "top3_score",
        "collection_month", "collection_locality",
    ) + (1..3).flatMap { n ->
        listOf("top${n}_risk", "top${n}_season", "top${n}_in_range", "top${n}_risk_reasons")
    } + caracteres + caracteresRazon.flatMap { listOf("${it}_numerator_mm", "${it}_denominator_mm") }

    val filas = sesiones.map { sesion ->
        val top = sesion.resultado?.top3.orEmpty()
        val fijas = listOf(
            sesion.id,
            sesion.fechaHoraIso,
            sesion.tallaMm.toString(),
            sesion.etapa,
            sesion.resultado?.label ?: "",
            sesion.resultado?.nivel ?: "",
            top.getOrNull(0)?.especie ?: "",
            top.getOrNull(0)?.scorePorcentaje?.toString() ?: "",
            top.getOrNull(1)?.especie ?: "",
            top.getOrNull(1)?.scorePorcentaje?.toString() ?: "",
            top.getOrNull(2)?.especie ?: "",
            top.getOrNull(2)?.scorePorcentaje?.toString() ?: "",
            sesion.mesColecta?.toString() ?: "",
            sesion.zonaColecta ?: "",
        )
        val riesgo = (0..2).flatMap { i ->
            val c = top.getOrNull(i)
            listOf(
                aCsv(c?.riesgo, CSV_NIVEL),
                aCsv(c?.temporada, CSV_TEMPORADA),
                aCsv(c?.distribucion, CSV_DISTRIBUCION),
                c?.razonesRiesgo?.joinToString(" ") ?: "",
            )
        }
        // Si un caracter se respondio dos veces (el tecnico uso "Atras" y
        // contesto de nuevo), gana la ultima respuesta.
        val porCaracter = sesion.observaciones.associateBy { it.character }
        val dinamicas = caracteres.map { clave ->
            val o = porCaracter[clave]
            when {
                o == null -> ""
                o.valor != null -> o.valor.toString()
                o.categorico != null -> o.categorico
                else -> "unknown"
            }
        }
        val crudasRazon = caracteresRazon.flatMap { clave ->
            val o = porCaracter[clave]
            listOf(o?.numeradorMm?.toString() ?: "", o?.denominadorMm?.toString() ?: "")
        }
        fijas + riesgo + dinamicas + crudasRazon
    }

    return buildString {
        append(BOM_UTF8) // para que Excel detecte UTF-8 solo, no UTF-16 ni Latin-1
        append(encabezados.joinToString(",") { csvEscape(it) })
        append("\r\n")
        for (fila in filas) {
            append(fila.joinToString(",") { csvEscape(it) })
            append("\r\n")
        }
    }
}

private fun csvEscape(valor: String): String =
    if (valor.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + valor.replace("\"", "\"\"") + "\""
    } else {
        valor
    }
