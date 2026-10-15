package cal.cofi.atlas

/**
 * El interrogatorio por fases, sin Compose: que se pregunta, en que orden, y
 * como cambia el estado con cada respuesta. PantallaPregunta solo lo dibuja.
 *
 * El orden sigue el de la identificacion de una larva en el laboratorio:
 * Forma -> Meristica -> Pigmentacion. Dentro de Meristica van primero los
 * miomeros (se cuentan desde las etapas mas tempranas y su numero no cambia
 * con el desarrollo) y despues los radios y el resto, solo si la etapa ya
 * los tiene. Dentro de cada fase el motor sigue eligiendo la pregunta que
 * mas reduce el conjunto, como siempre.
 */

/**
 * Las fases, en orden. [MIOMEROS] y [ALETAS] son las dos mitades de lo que
 * el tecnico ve como "Meristica": se separan para que los miomeros vayan
 * primero aunque un radio de aleta separara mejor.
 */
enum class Fase(val clases: Set<String>) {
    FORMA(setOf("shape")),
    MIOMEROS(setOf("myomeres")),
    ALETAS(setOf("fin_rays", "branchiostegals", "fin_position", "vertebrae", "gill_rakers")),
    PIGMENTACION(setOf("pigment")),
}

/**
 * Un conteo que se usa en DOS niveles: la tabla por familia de
 * coarse_filter.json ([tablaFamilia]) y el rango de la especie ([clave]).
 * Es lo que antes era el filtro grueso, que pedia estos mismos conteos en
 * una pantalla aparte y despues el interrogatorio podia volver a pedirlos.
 *
 * Abren su fase y se piden siempre que la etapa lo permita, aunque no
 * separen especies: son el chequeo a nivel familia, lo que impide que una
 * larva de una familia no cargada termine como la especie mas parecida con
 * un solo caracter a favor.
 */
data class Ancla(val clave: String, val tablaFamilia: String, val fase: Fase)

val ANCLAS: List<Ancla> = listOf(
    // La tabla dice "vertebrae_total", pero se arma para contarse en
    // larvas: segmentos musculares o vertebras, la misma cuenta.
    Ancla(MIOMEROS_TOTALES, "vertebrae_total", Fase.MIOMEROS),
    Ancla("Anal rays", "anal_rays_total", Fase.ALETAS),
    Ancla("Dorsal rays", "dorsal_rays_total", Fase.ALETAS),
)

sealed interface TipoPregunta {
    /** Un numero entero. */
    data object Conteo : TipoPregunta

    /** Presente/ausente. [valorSi]/[valorNo] son los valores del atlas, no "si"/"no". */
    data class SiNo(val valorSi: String, val valorNo: String) : TipoPregunta

    /** Una entre varias opciones categoricas (p.ej. una forma), en orden de catalogo. */
    data class Opciones(val valores: List<String>) : TipoPregunta

    /**
     * Espinas y radios blandos de [aleta], en la misma pregunta. Si ninguna
     * especie en juego reporta espinas para esa aleta ([pedirEspinas] en
     * false, p.ej. la pectoral en todo el atlas actual), solo se piden los
     * radios.
     */
    data class ConteoAleta(val aleta: Aleta, val pedirEspinas: Boolean) : TipoPregunta
}

data class Pregunta(
    val fase: Fase,
    /** Clave canonica; para una aleta, la de sus radios blandos. */
    val clave: String,
    val tipo: TipoPregunta,
    val ancla: Ancla? = null,
) {
    /** Lo que queda marcado como ya preguntado al responder: nunca se pide dos veces. */
    val claves: Set<String>
        get() = (tipo as? TipoPregunta.ConteoAleta)?.aleta?.claves ?: setOf(clave)

    /** Lo que de verdad se le pide al tecnico: sin el campo de espinas si no se muestra. */
    val clavesPedidas: List<String>
        get() = when (val t = tipo) {
            is TipoPregunta.ConteoAleta ->
                if (t.pedirEspinas) listOf(t.aleta.espinas, t.aleta.radios) else listOf(t.aleta.radios)
            else -> listOf(clave)
        }
}

/**
 * Todo lo que hace falta para retomar el interrogatorio en un punto. Es
 * inmutable a proposito: "Atras" guarda una copia por respuesta y restaura
 * la anterior, sea de la misma fase o de otra.
 *
 * [fase] es la de la ULTIMA pregunta hecha: las fases solo avanzan. Una
 * pregunta de una fase anterior que se volviera util despues no se vuelve a
 * ofrecer — el orden es parte del metodo, no una sugerencia.
 */
data class EstadoInterrogatorio(
    val observaciones: List<Observation> = emptyList(),
    val preguntadas: Set<String> = emptySet(),
    val familias: Set<String>? = null,
    val fase: Fase = Fase.entries.first(),
) {
    fun responder(
        pregunta: Pregunta,
        respuestas: List<Observation>,
        familiasNuevas: Set<String>? = familias,
    ): EstadoInterrogatorio = copy(
        observaciones = observaciones + respuestas,
        preguntadas = preguntadas + pregunta.claves,
        familias = familiasNuevas,
        fase = pregunta.fase,
    )
}

sealed interface PasoInterrogatorio {
    val plausibles: List<Candidate>

    data class Preguntar(val pregunta: Pregunta, override val plausibles: List<Candidate>) : PasoInterrogatorio

    data class Terminar(val identificacion: Identification, override val plausibles: List<Candidate>) :
        PasoInterrogatorio
}

/**
 * Lo que el interrogatorio necesita saber del mundo. Todo inyectable para
 * poder probar con especies y caracteres sinteticos (p.ej. una forma de tres
 * opciones, que el bundle todavia no trae).
 *
 * [preguntables]: caracteres con redaccion en lenguaje llano (PROMPTS).
 * [opcionesConocidas]: para cada caracter de opciones, los valores con
 * redaccion llana, en el orden en que se muestran. Un caracter categorico
 * con algun valor fuera de este catalogo (y que no sea presente/ausente) no
 * se pregunta: no hay como ofrecerle esa opcion al tecnico sin jerga.
 */
data class ReglasInterrogatorio(
    val preguntables: Set<String>,
    val opcionesConocidas: Map<String, List<String>> = emptyMap(),
    val disponiblesPorEtapa: Map<Stage, Set<String>> = AVAILABLE_AT_STAGE,
    val clases: Map<String, String> = CHARACTER_CLASS,
    val anclas: List<Ancla> = ANCLAS,
)

private val VALORES_SI_NO = setOf("present", "absent")

/**
 * El siguiente paso: la proxima pregunta, o el resultado si ya no queda
 * ninguna util en ninguna fase.
 *
 * Recorre las fases desde la actual. En cada una: primero sus anclas
 * pendientes, despues la pregunta que mas reduce (nextQuestion con las
 * clases restringidas a las de la fase). Una fase sin nada que preguntar se
 * salta sola — el tecnico nunca ve una pantalla vacia.
 *
 * Las preguntas se eligen sobre [IdentificationEngine.plausibles], no sobre
 * el ranking completo; resolve() si usa el ranking completo.
 */
fun siguientePaso(
    engine: IdentificationEngine,
    stage: Stage,
    estado: EstadoInterrogatorio,
    tallaMm: Double?,
    reglas: ReglasInterrogatorio,
): PasoInterrogatorio {
    val ranked = engine.rank(stage, estado.observaciones, estado.familias, tallaMm)
    val activos = engine.plausibles(ranked)
    val terminar = {
        PasoInterrogatorio.Terminar(
            engine.resolve(stage, estado.observaciones, estado.familias, tallaMm),
            activos,
        )
    }
    // Pool vacio: el filtro de familia dejo afuera a todas las especies
    // cargadas. No hay nada que preguntar; el resultado lo explica.
    if (ranked.isEmpty()) return terminar()

    val disponibles = reglas.disponiblesPorEtapa[stage].orEmpty()
    val preguntables = reglas.preguntables - categoricosSinRedaccion(activos, reglas.opcionesConocidas)

    for (fase in Fase.entries.drop(estado.fase.ordinal)) {
        val clasesFase = disponibles intersect fase.clases
        if (clasesFase.isEmpty()) continue

        for (ancla in reglas.anclas) {
            if (ancla.fase != fase || ancla.clave in estado.preguntadas) continue
            if ((reglas.clases[ancla.clave] ?: DEFAULT_CHARACTER_CLASS) !in clasesFase) continue
            if (ancla.clave !in preguntables) continue
            return PasoInterrogatorio.Preguntar(
                construirPregunta(ancla.clave, fase, ranked, activos, reglas, ancla),
                activos,
            )
        }

        val clave = engine.nextQuestion(
            activos, stage, estado.preguntadas, clasesFase, reglas.clases, preguntables, tallaMm,
        ) ?: continue
        return PasoInterrogatorio.Preguntar(construirPregunta(clave, fase, ranked, activos, reglas, null), activos)
    }
    return terminar()
}

private fun construirPregunta(
    clave: String,
    fase: Fase,
    ranked: List<Candidate>,
    activos: List<Candidate>,
    reglas: ReglasInterrogatorio,
    ancla: Ancla?,
): Pregunta {
    val aleta = aletaDe(clave)
    if (aleta != null) {
        // Sobre el ranking completo, no solo los plausibles: si alguna
        // especie en juego tiene espinas en esa aleta, el campo tiene que
        // estar para que el tecnico pueda decir cuantas ve (o "0").
        val pedirEspinas = ranked.any { aleta.espinas in it.species.meristics }
        return Pregunta(fase, aleta.radios, TipoPregunta.ConteoAleta(aleta, pedirEspinas), ancla)
    }

    val valores = activos.mapNotNull { it.species.diagnostic[clave]?.value }.toSet()
    val tipo = when {
        valores.isEmpty() -> TipoPregunta.Conteo
        clave in reglas.opcionesConocidas -> TipoPregunta.Opciones(reglas.opcionesConocidas.getValue(clave))
        else -> valoresSiNo(valores)
    }
    return Pregunta(fase, clave, tipo, ancla)
}

private fun valoresSiNo(valores: Set<String>): TipoPregunta.SiNo {
    val valorSi = valores.firstOrNull { it.equals("present", ignoreCase = true) }
        ?: valores.firstOrNull()
        ?: "present"
    val valorNo = valores.firstOrNull { it.equals("absent", ignoreCase = true) }
        ?: valores.firstOrNull { it != valorSi }
        ?: "absent"
    return TipoPregunta.SiNo(valorSi, valorNo)
}

/**
 * Caracteres categoricos del pool que no se pueden ofrecer en lenguaje
 * llano: tienen algun valor que no esta en el catalogo de opciones y no son
 * un simple presente/ausente.
 */
private fun categoricosSinRedaccion(
    pool: List<Candidate>,
    opcionesConocidas: Map<String, List<String>>,
): Set<String> {
    val valores = mutableMapOf<String, MutableSet<String>>()
    for (c in pool) {
        for ((clave, d) in c.species.diagnostic) {
            val v = d.value ?: continue
            valores.getOrPut(clave) { mutableSetOf() }.add(v)
        }
    }
    return valores.filter { (clave, vs) ->
        val catalogo = opcionesConocidas[clave]
        if (catalogo != null) !catalogo.containsAll(vs) else !vs.all { it.lowercase() in VALORES_SI_NO }
    }.keys
}

// ----------------------------------------------------------- nivel familia

/**
 * Como quedaria el filtro de familias con un conteo de tabla.
 *
 * [familias] es el filtro nuevo, listo para rank(). [deLaTabla] son las que
 * la tabla da por compatibles, y [conEspeciesCargadas] cuantas de esas
 * tienen especies en el bundle. [noListadasConservadas] son familias
 * cargadas que la tabla no menciona y por eso siguen en juego (ver
 * CoarseFilter.noListadas).
 */
data class EfectoEnFamilias(
    val familias: Set<String>,
    val deLaTabla: Set<String>,
    val conEspeciesCargadas: Set<String>,
    val noListadasConservadas: Set<String>,
) {
    /** Ninguna familia de la tabla aguanta ese numero: casi siempre es un error de conteo. */
    val sinFamiliaCompatible: Boolean get() = deLaTabla.isEmpty()

    /** Hay familias compatibles, pero ninguna tiene especies en esta version de la app. */
    val sinEspeciesCargadas: Boolean get() = deLaTabla.isNotEmpty() && conEspeciesCargadas.isEmpty()
}

/**
 * Efecto de aplicar [valor] a la tabla [tabla], sobre el filtro [previas]
 * (null = todavia sin filtrar). null si el bundle no trae esa tabla: sin
 * tabla, el conteo solo sirve a nivel especie.
 */
fun efectoEnFamilias(
    coarse: CoarseFilter,
    tabla: String,
    valor: Double,
    previas: Set<String>?,
    familiasCargadas: Set<String>,
): EfectoEnFamilias? {
    if (tabla !in coarse.tables) return null
    val compatibles = coarse.families(tabla, valor)
    val noListadas = coarse.noListadas(tabla, familiasCargadas)
    val base = compatibles + noListadas
    val nuevas = previas?.let { it intersect base } ?: base
    val deLaTabla = nuevas intersect compatibles
    return EfectoEnFamilias(
        familias = nuevas,
        deLaTabla = deLaTabla,
        conEspeciesCargadas = deLaTabla intersect familiasCargadas,
        noListadasConservadas = nuevas intersect noListadas,
    )
}

/**
 * El numero que va contra la tabla por familia, o null si no se puede
 * armar. Para una aleta es espinas + blandos (asi estan hechas las Tablas
 * Intro 3-5); si el tecnico no pudo contar una de las dos partes, la suma
 * no existe y esa aleta no filtra familias — pero lo que si conto se sigue
 * comparando a nivel especie.
 */
fun valorParaTabla(pregunta: Pregunta, respuestas: List<Observation>): Double? {
    val porClave = respuestas.associateBy { it.character }
    return when (val tipo = pregunta.tipo) {
        is TipoPregunta.ConteoAleta -> {
            val radios = porClave[tipo.aleta.radios]?.value ?: return null
            val espinas = if (tipo.pedirEspinas) porClave[tipo.aleta.espinas]?.value ?: return null else 0.0
            radios + espinas
        }
        else -> porClave[pregunta.clave]?.value
    }
}
