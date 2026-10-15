package cal.cofi.atlas

/**
 * Especies y atlas sinteticos para probar el motor sin depender del bundle:
 * con datos reales no se pueden probar cosas que el bundle todavia no trae
 * (una forma de tres opciones, una pelvica "I,5" en una familia chica) ni
 * fijar a mano el caso exacto que se quiere ver.
 */
internal fun especieSintetica(
    id: String,
    familia: String = "Testidae",
    meristics: Map<String, Pair<Double, Double>> = emptyMap(),
    diagnostic: Map<String, DiagnosticChar> = emptyMap(),
    lifeHistory: LifeHistory? = null,
): Species = Species(
    id = id,
    // Un genero por especie: asi un empate se resuelve a familia y no
    // aparece como "Genero sp." por accidente del nombre.
    species = "${id.replaceFirstChar { it.uppercase() }} sinteticus",
    family = familia,
    pdfPage = 0,
    meristics = meristics.mapValues { (_, r) -> Range(r.first, r.second) },
    diagnostic = diagnostic,
    sampleSizes = emptyMap(),
    lifeHistory = lifeHistory,
)

internal fun rango(min: Double, max: Double) = DiagnosticChar(range = Range(min, max))

internal fun valor(v: String, conditional: String? = null) = DiagnosticChar(value = v, conditional = conditional)

internal fun atlasSintetico(
    vararg especies: Species,
    tablas: Map<String, Map<String, Range>> = emptyMap(),
): AtlasData = AtlasData(especies.toList(), CoarseFilter(tablas), emptyList())

/** Todo lo que pregunta el interrogatorio real, mas lo que agregue cada test. */
internal val PREGUNTABLES_PRUEBA: Set<String> = setOf(
    MIOMEROS_TOTALES, "myomeres_preanal", "hindgut_pigment", "D_insertion_over_A_ray",
    "Vertebrae: Total", "Branchiostegals",
) + ALETAS.flatMap { it.claves }

/** Un recorrido completo, pregunta por pregunta, y como termino. */
internal data class Recorrido(
    val preguntas: List<Pregunta>,
    val estados: List<EstadoInterrogatorio>,
    val final: EstadoInterrogatorio,
    val identificacion: Identification,
)

/**
 * Sigue el mismo camino que PantallaPregunta: siguientePaso, respuesta,
 * tabla por familia si es ancla. [ejemplar] da el valor de cada clave
 * (Double para conteos, String para categoricos); lo que no esta se
 * contesta "no se". Los avisos de familia se resuelven como "usar sin
 * filtrar familias", que es lo conservador.
 */
internal fun interrogar(
    data: AtlasData,
    stage: Stage,
    reglas: ReglasInterrogatorio,
    ejemplar: Map<String, Any>,
    tallaMm: Double? = null,
): Recorrido {
    val engine = IdentificationEngine(data)
    val familiasCargadas = data.species.map { it.family }.toSet()
    var estado = EstadoInterrogatorio()
    val preguntas = mutableListOf<Pregunta>()
    val estados = mutableListOf<EstadoInterrogatorio>()

    repeat(200) {
        when (val paso = siguientePaso(engine, stage, estado, tallaMm, reglas)) {
            is PasoInterrogatorio.Terminar -> return Recorrido(preguntas, estados, estado, paso.identificacion)
            is PasoInterrogatorio.Preguntar -> {
                val p = paso.pregunta
                preguntas += p
                estados += estado
                val respuestas = p.clavesPedidas.map { clave ->
                    when (val v = ejemplar[clave]) {
                        null -> Observation(clave)
                        is String -> Observation(clave, categorical = v)
                        is Double -> Observation(clave, value = v)
                        is Int -> Observation(clave, value = v.toDouble())
                        else -> error("tipo no soportado en el ejemplar de prueba: $v")
                    }
                }
                val ancla = p.ancla
                val total = valorParaTabla(p, respuestas)
                val efecto = if (ancla != null && total != null) {
                    efectoEnFamilias(data.coarse, ancla.tablaFamilia, total, estado.familias, familiasCargadas)
                } else {
                    null
                }
                val familias = efecto
                    ?.takeUnless { it.sinFamiliaCompatible || it.sinEspeciesCargadas }
                    ?.familias
                    ?: estado.familias
                estado = estado.responder(p, respuestas, familias)
            }
        }
    }
    error("el interrogatorio no termino en 200 preguntas")
}
