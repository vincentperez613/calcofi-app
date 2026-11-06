package cal.cofi.atlas

import kotlin.math.abs

/**
 * Motor de identificacion: puntuacion blanda + preguntas adaptativas.
 *
 * Sin dependencias. Con ~435 especies y ~30 caracteres son unas 13 mil
 * comparaciones por pregunta: submilisegundo en un telefono de 2016. El cuello
 * de botella nunca es el computo.
 *
 * Solo conteos y caracteres cualitativos: la morfometria (razones tipo
 * BD/BL) se quito a proposito. Las larvas se miden fijadas en formol, que
 * las encoge, y las proporciones cambian entre regiones; una medida tomada
 * asi no es comparable con el atlas. Un conteo si lo es.
 */

/**
 * Una observacion del tecnico.
 *
 * Si [value] y [categorical] son ambos null, significa "no se / no visible",
 * que es una respuesta VALIDA y no debe penalizar a nadie. Un ejemplar dañado
 * es lo normal en campo, no la excepcion.
 */
data class Observation(
    val character: String,
    val value: Double? = null,
    val categorical: String? = null,
) {
    val isUnknown: Boolean get() = value == null && categorical == null
}

/**
 * Como le queda una especie a lo observado, con el desglose para poder
 * explicar el porque. Nunca se devuelve un veredicto sin evidencia.
 */
data class Candidate(
    val species: Species,
    val supporting: List<String>,
    val contradicting: List<String>,
    /**
     * De [supporting], los que solo entraron gracias a la tolerancia: el
     * valor cae fuera del rango publicado, pero dentro del margen por error
     * de conteo (o de la holgura de talla). Cuentan a favor igual — solo
     * deciden el orden entre empatados.
     */
    val marginal: List<String> = emptyList(),
) {
    val evaluated: Int get() = supporting.size + contradicting.size
    val score: Double get() = if (evaluated == 0) 0.0 else supporting.size.toDouble() / evaluated
}

enum class TaxonLevel { SPECIES, GENUS, FAMILY, UNRESOLVED }

/**
 * [tied] son los candidatos que comparten el titular (uno solo si
 * [level] es SPECIES), en el orden de [candidates]. Con UNRESOLVED
 * [label] los nombra a todos: pueden ser cientos si casi todo se contesto
 * "no se", asi que la UI cuenta [tied] en vez de mostrar [label] entero.
 */
data class Identification(
    val label: String,
    val level: TaxonLevel,
    val candidates: List<Candidate>,
    val tied: List<Candidate> = emptyList(),
)

/**
 * Reconoce condicionales del atlas como "before 18 mm", "after ca. 20mm",
 * "by 15 mm", "until 12 mm". Todo lo demas (texto libre que no calza) se
 * trata como no-parseable: mejor omitir la pregunta que inventar un limite.
 */
private val PATRON_CONDICIONAL =
    Regex("""(before|after|by|until)\s+(?:ca\.?\s*)?([\d.]+)\s*mm""", RegexOption.IGNORE_CASE)

/**
 * Si [conditional] aplica para un ejemplar de [tallaMm]. Sin talla, o si el
 * texto no parsea, no se puede saber — y no saber no es lo mismo que "no
 * aplica": ver [IdentificationEngine.evaluate] para por que eso importa.
 */
private fun condicionalAplica(conditional: String, tallaMm: Double?): Boolean {
    if (tallaMm == null) return false
    val m = PATRON_CONDICIONAL.find(conditional) ?: return false
    val umbral = m.groupValues[2].toDoubleOrNull() ?: return false
    return when (m.groupValues[1].lowercase()) {
        "before", "by", "until" -> tallaMm < umbral
        "after" -> tallaMm > umbral
        else -> false
    }
}

// ------------------------------------------------- claves y equivalencias

/** Clave canonica del conteo total de miomeros: la que se pregunta y se guarda. */
const val MIOMEROS_TOTALES = "myomeres_total"

/**
 * Claves de ficha que son el MISMO conteo que otra. Algunas fichas dan los
 * miomeros en la tabla MERISTICS ("Myomeres: Total") y otras en el texto
 * diagnostico (myomeres_total): para el tecnico es un solo conteo, y
 * pedirlo dos veces seria pedirle que cuente lo mismo otra vez.
 */
val CLAVES_EQUIVALENTES: Map<String, String> = mapOf(
    "Myomeres: Total" to MIOMEROS_TOTALES,
)

fun claveCanonica(clave: String): String = CLAVES_EQUIVALENTES[clave] ?: clave

/**
 * SUSTITUTO PROVISIONAL, PENDIENTE DE CONFIRMAR CON UN ICTIOLOGO: usar las
 * vertebras totales de la ficha como rango del conteo de miomeros cuando la
 * especie no trae miomeros. Es la aproximacion habitual (cada miomero
 * corresponde a una vertebra), y sin ella una larva temprana no tendria con
 * que compararse en ~80% de las especies del atlas — solo unas 46 fichas
 * dan miomeros en el texto. Para apagarla basta con poner esto en false:
 * todo el resto del motor pasa por [rangoMiomerosTotales].
 */
const val VERTEBRAS_SUSTITUYEN_MIOMEROS = true

/**
 * Rango de miomeros totales de la especie: primero lo que el atlas da como
 * miomeros (en el texto o en la tabla), y solo si no hay nada, el
 * sustituto por vertebras de [VERTEBRAS_SUSTITUYEN_MIOMEROS].
 */
fun rangoMiomerosTotales(sp: Species): Range? =
    sp.rangeFor(MIOMEROS_TOTALES)
        ?: sp.rangeFor("Myomeres: Total")
        ?: if (VERTEBRAS_SUSTITUYEN_MIOMEROS) sp.rangeFor("Vertebrae: Total") else null

/**
 * El rango contra el que el motor compara una observacion de [character].
 * Es el UNICO lugar que resuelve equivalencias y sustitutos: evaluate() y
 * expectedRemaining() pasan siempre por aca, para que seleccion y
 * evaluacion razonen igual.
 */
fun rangoDe(sp: Species, character: String): Range? {
    val clave = claveCanonica(character)
    return if (clave == MIOMEROS_TOTALES) rangoMiomerosTotales(sp) else sp.rangeFor(clave)
}

/** Las claves (ya canonicas) que tiene sentido preguntar sobre [sp]. */
fun clavesComparables(sp: Species): Set<String> = buildSet {
    for (k in sp.meristics.keys) add(claveCanonica(k))
    for (k in sp.diagnostic.keys) add(claveCanonica(k))
    if (rangoMiomerosTotales(sp) != null) add(MIOMEROS_TOTALES)
}

/** Error de conteo del tecnico: un miomero o un radio de mas o de menos. */
const val TOLERANCIA_CONTEO = 1.0

/**
 * Margen extra cuando los miomeros se comparan contra las VERTEBRAS
 * ([VERTEBRAS_SUSTITUYEN_MIOMEROS]): en las 67 fichas que traen las dos
 * cosas los rangos siempre se traslapan, pero el de miomeros se sale del de
 * vertebras por 1 en un tercio de ellas y por 2 en unas pocas. +-1 aqui
 * bajaria los matches en preflexion (~57 -> ~46 en la simulacion), al costo
 * de perder a la especie correcta un punto mas seguido.
 */
const val TOLERANCIA_SUSTITUTO_VERTEBRAL = 2.0

/** Si [sp] no trae miomeros y el motor compara contra sus vertebras. */
fun usaSustitutoVertebral(sp: Species): Boolean =
    VERTEBRAS_SUSTITUYEN_MIOMEROS &&
        sp.rangeFor(MIOMEROS_TOTALES) == null && sp.rangeFor("Myomeres: Total") == null &&
        sp.rangeFor("Vertebrae: Total") != null

/**
 * Tolerancia de un conteo de [character] contra la ficha de [sp]. Es fija y
 * no sale del tamaño de muestra (ver [SampleSize]): el rango publicado ya es
 * el de la especie, y lo unico que hay que absorber es el error de conteo.
 */
internal fun toleranciaConteo(sp: Species, character: String): Double =
    if (claveCanonica(character) == MIOMEROS_TOTALES && usaSustitutoVertebral(sp)) {
        TOLERANCIA_SUSTITUTO_VERTEBRAL
    } else {
        TOLERANCIA_CONTEO
    }

// --------------------------------------------------------- talla x etapa

/**
 * Clave con la que la talla (BL) cuenta como caracter: a favor si cae en la
 * ventana de la ficha para la etapa elegida ([Species.stageSizes]), en contra
 * si no. No es una pregunta: la talla se captura siempre, antes del
 * interrogatorio.
 */
const val TALLA_ETAPA = "body_length_at_stage"

/**
 * Holgura de la ventana de talla: 25% (minimo 0.5 mm). Cubre el
 * encogimiento por formol y que la ventana sale de pocas larvas. Validada
 * contra los pies de figura del atlas: de 513 larvas dibujadas con etapa y
 * talla, 1 cae fuera de la ventana de su especie con esta holgura (14 sin
 * ella).
 */
const val HOLGURA_TALLA = 0.25
const val HOLGURA_TALLA_MIN_MM = 0.5

private fun VentanaTalla.admite(tallaMm: Double, holgura: Boolean): Boolean {
    val bajo = min?.let { if (holgura) minOf(it * (1 - HOLGURA_TALLA), it - HOLGURA_TALLA_MIN_MM) else it }
    val alto = max?.let { if (holgura) maxOf(it * (1 + HOLGURA_TALLA), it + HOLGURA_TALLA_MIN_MM) else it }
    return (bajo == null || tallaMm >= bajo) && (alto == null || tallaMm <= alto)
}

// -------------------------------------------------------------------- aletas

/**
 * Una aleta con espinas y radios blandos. El atlas los da separados
 * ("Dorsal spines" + "Dorsal rays"; la pelvica "I,5" queda como
 * "Pelvic spines" = 1 y "Pelvic" = 5), y el tecnico tambien los cuenta
 * separados, en una sola pregunta. Asi espinas se comparan con espinas y
 * blandos con blandos: comparar un total contra solo los blandos descartaria
 * a la especie correcta en cualquier pez con espinas.
 *
 * [tablaFamilia] es la tabla de coarse_filter.json que usa la SUMA (las
 * Tablas Intro 3-5 cuentan espinas + blandos), o null si no hay tabla.
 */
data class Aleta(
    val id: String,
    val espinas: String,
    val radios: String,
    val tablaFamilia: String?,
) {
    val claves: Set<String> get() = setOf(espinas, radios)
}

val ALETAS: List<Aleta> = listOf(
    Aleta("anal", "Anal spines", "Anal rays", "anal_rays_total"),
    Aleta("dorsal", "Dorsal spines", "Dorsal rays", "dorsal_rays_total"),
    Aleta("pelvica", "Pelvic spines", "Pelvic", null),
    Aleta("pectoral", "Pectoral spines", "Pectoral", null),
)

fun aletaDe(character: String): Aleta? = ALETAS.firstOrNull { character in it.claves }

class IdentificationEngine(private val data: AtlasData) {

    // ------------------------------------------------------------ puntuacion
    private fun evaluate(sp: Species, stage: Stage, obs: List<Observation>, tallaMm: Double?): Candidate {
        val ok = mutableListOf<String>()
        val bad = mutableListOf<String>()
        val marginal = mutableListOf<String>()

        // Sin ventana para esta etapa la talla no dice nada de la especie.
        val ventana = sp.stageSizes[stage.code]
        if (tallaMm != null && ventana != null) {
            when {
                ventana.admite(tallaMm, holgura = false) -> ok.add(TALLA_ETAPA)
                ventana.admite(tallaMm, holgura = true) -> {
                    ok.add(TALLA_ETAPA)
                    marginal.add(TALLA_ETAPA)
                }
                else -> bad.add(TALLA_ETAPA)
            }
        }

        for (o in obs) {
            if (o.isUnknown) continue

            if (o.categorical != null) {
                val d = sp.diagnostic[o.character] ?: continue
                if (d.value == null) continue          // no reportado: no penaliza
                // Con condicional ("before 18 mm") solo se puede evaluar si
                // se sabe la talla Y esa talla cae dentro de lo que el atlas
                // describe. Fuera de ese rango el atlas no dice nada: omitir
                // es mejor que inventar un descarte.
                if (d.conditional != null && !condicionalAplica(d.conditional, tallaMm)) continue
                (if (d.value == o.categorical) ok else bad).add(o.character)
                continue
            }

            val r = rangoDe(sp, o.character) ?: continue  // no penaliza
            val v = o.value!!
            when {
                r.contains(v, 0.0) -> ok.add(o.character)
                r.contains(v, toleranciaConteo(sp, o.character)) -> {
                    ok.add(o.character)
                    marginal.add(o.character)
                }
                else -> bad.add(o.character)
            }
        }
        return Candidate(sp, ok, bad, marginal)
    }

    /**
     * Ordena candidatos. NO elimina: un solo caracter fuera de rango no debe
     * descartar a la especie correcta, que es el modo de falla a evitar.
     *
     * Entre los que empatan en score va primero el que tiene mas evidencia,
     * y a igual evidencia el que necesito menos tolerancia ([Candidate.marginal]).
     * No cambia cuantos matches hay, solo cual se ve primero: en la
     * simulacion sobre el atlas, la especie correcta queda primera en
     * postflexion ~79% de las veces contra ~62% sin el ultimo criterio.
     */
    fun rank(
        stage: Stage,
        obs: List<Observation>,
        familyFilter: Set<String>? = null,
        tallaMm: Double? = null,
    ): List<Candidate> {
        val pool = if (familyFilter.isNullOrEmpty()) data.species
        else data.species.filter { it.family in familyFilter }

        return pool.map { evaluate(it, stage, obs, tallaMm) }
            .sortedWith(compareByDescending<Candidate> { it.score }
                .thenByDescending { it.evaluated }
                .thenBy { it.marginal.size })
    }

    /**
     * Los candidatos de [ranked] que no tienen ninguna contradiccion con lo
     * observado hasta ahora — los que de verdad siguen en carrera. rank() no
     * elimina a nadie a proposito (ver arriba), pero elegir la SIGUIENTE
     * pregunta sobre los 4 de siempre, contradichos o no, desperdicia la
     * mejor pregunta del motor en cada ronda.
     *
     * Si ninguno queda sin contradicciones (un ejemplar raro, con un
     * caracter fuera de rango en todos), devuelve [ranked] completo: el
     * motor necesita candidatos con los que seguir razonando, no quedarse
     * sin ninguno.
     */
    fun plausibles(ranked: List<Candidate>): List<Candidate> {
        val sinContradicciones = ranked.filter { it.contradicting.isEmpty() }
        return sinContradicciones.ifEmpty { ranked }
    }

    /**
     * Resuelve al nivel taxonomico que la evidencia soporta, no siempre
     * especie. El atlas mismo solo llega a "Anchoa sp." en etapas tempranas;
     * forzar una especie ahi seria inventar precision.
     */
    fun resolve(
        stage: Stage,
        obs: List<Observation>,
        familyFilter: Set<String>? = null,
        tallaMm: Double? = null,
    ): Identification {
        val ranked = rank(stage, obs, familyFilter, tallaMm)
        if (ranked.isEmpty()) {
            return Identification("no data", TaxonLevel.UNRESOLVED, emptyList())
        }

        // El titular se decide sobre los candidatos SIN contradicciones, no
        // sobre el ranking completo. score no distingue "nunca evaluado"
        // (evaluated == 0) de "contradicho en todo" (bad > 0, good == 0):
        // ambos dan score == 0.0. Sin este filtro, un candidato activamente
        // contradicho podia empatar el titular con uno que simplemente
        // todavia no tiene evidencia. candidates si sigue siendo el ranking
        // completo — el tecnico tiene que poder ver a los contradichos con
        // su evidencia, solo que no compiten por el titulo.
        val activos = plausibles(ranked)
        val base = activos.ifEmpty { ranked }

        val best = base.first().score
        val tied = base.filter { abs(best - it.score) < 1e-9 }

        if (tied.size == 1) {
            return Identification(tied.first().species.species, TaxonLevel.SPECIES, ranked, tied)
        }
        val genera = tied.map { it.species.genus }.toSet()
        if (genera.size == 1) {
            return Identification("${genera.first()} sp.", TaxonLevel.GENUS, ranked, tied)
        }
        val fams = tied.map { it.species.family }.toSet()
        if (fams.size == 1) {
            return Identification(fams.first(), TaxonLevel.FAMILY, ranked, tied)
        }
        val nombres = tied.map { it.species.species }.sorted().joinToString(" / ")
        return Identification(nombres, TaxonLevel.UNRESOLVED, ranked, tied)
    }

    // --------------------------------------------------- pregunta adaptativa
    /**
     * Cuantos candidatos quedarian, en promedio, si preguntaramos esto.
     * Menor = mejor pregunta. Se barre el rango plausible y se promedia.
     */
    private fun expectedRemaining(
        pool: List<Candidate>,
        character: String,
        tallaMm: Double?,
    ): Double {
        // Categorico (p.ej. hindgut_pigment: "present"/"absent", o una
        // forma con tres o mas opciones): sin esta rama, rangoDe() devuelve
        // null para todos y la funcion caia al peor puntaje posible
        // (pool.size) SIEMPRE, asi que nextQuestion nunca elegia un caracter
        // categorico por mas que separara bien. Funciona igual con dos
        // opciones que con diez: se promedia sobre las que de verdad
        // aparecen en el pool.
        val valoresCategoricos = pool.mapNotNull { it.species.diagnostic[character]?.value }
        if (valoresCategoricos.isNotEmpty()) {
            val opciones = valoresCategoricos.toSet()
            val total = opciones.sumOf { opcion ->
                pool.count { c ->
                    val d = c.species.diagnostic[character]
                    when {
                        d?.value == null -> true // ausente: no penaliza
                        d.conditional != null && !condicionalAplica(d.conditional, tallaMm) -> true
                        else -> d.value == opcion
                    }
                }
            }
            return total.toDouble() / opciones.size
        }

        val ranges = pool.mapNotNull { rangoDe(it.species, character) }
        if (ranges.isEmpty()) return pool.size.toDouble()

        val lo = ranges.minOf { it.min }
        val hi = ranges.maxOf { it.max }
        if (hi <= lo) return pool.size.toDouble()

        val probes = 21
        var total = 0.0
        for (i in 0 until probes) {
            val v = lo + (hi - lo) * i / (probes - 1)
            total += pool.count { c ->
                // Mismo rango y misma tolerancia que evaluate(): seleccion y
                // evaluacion tienen que razonar igual.
                val r = rangoDe(c.species, character)
                r == null || r.contains(v, toleranciaConteo(c.species, character))
            }
        }
        return total / probes
    }

    /**
     * La siguiente pregunta a mostrar, o null si ninguna reduce el conjunto.
     *
     * Devolver null es una respuesta legitima: seguir preguntando cuando nada
     * separa solo desperdicia el tiempo del tecnico y le sugiere que la app
     * todavia puede llegar a especie. No puede.
     *
     * [availableAtStage] son las clases que se pueden preguntar AHORA: el
     * interrogatorio por fases (Interrogatorio.kt) lo restringe a las clases
     * de la fase en curso, asi que esta funcion sigue eligiendo "la que mas
     * reduce" pero dentro de una fase a la vez.
     *
     * [preguntables] restringe la eleccion a caracteres que el llamador sabe
     * redactar en lenguaje llano (el equivalente de `k in PROMPTS` de la
     * version Python). Sin este filtro el motor puede gastar su mejor
     * pregunta en un caracter que la UI de todos modos va a descartar.
     *
     * Devuelve siempre una clave canonica (ver [claveCanonica]), y [asked]
     * se compara contra claves canonicas: "Myomeres: Total" y
     * myomeres_total son una sola pregunta.
     */
    fun nextQuestion(
        pool: List<Candidate>,
        stage: Stage,
        asked: Set<String>,
        availableAtStage: Set<String>,
        classOf: Map<String, String>,
        preguntables: Set<String>,
        tallaMm: Double? = null,
    ): String? {
        if (pool.size <= 1) return null

        val keys = buildSet {
            for (c in pool) addAll(clavesComparables(c.species))
        }.filter {
            it !in asked && (classOf[it] ?: DEFAULT_CHARACTER_CLASS) in availableAtStage && it in preguntables
        }

        if (keys.isEmpty()) return null

        // Desempate alfabetico explicito (como Python: sorted((valor, clave))):
        // sin esto el orden depende de la iteracion de un LinkedHashSet, que
        // depende de como Loader.kt arma los mapas desde el JSON — no
        // reproducible entre corridas si cambian los datos.
        val puntuados = keys.map { it to expectedRemaining(pool, it, tallaMm) }
        val (best, mejorEsperado) = puntuados.minWithOrNull(compareBy({ it.second }, { it.first })) ?: return null
        // regla de paro: si la mejor pregunta no reduce nada, ninguna lo hara
        if (mejorEsperado >= pool.size - 0.05) return null
        return best
    }
}

/**
 * Que clases de caracter son OBSERVABLES en cada etapa. Una larva en preflexion
 * no tiene aletas que contar.
 *
 * SUPUESTO A CONFIRMAR CON UN ICTIOLOGO antes de produccion. Se deriva del
 * campo "Fin development sequence" de cada ficha y puede variar por familia.
 *
 * "shape" (forma del cuerpo) se ve desde la primera etapa. "branchiostegals"
 * conserva la disponibilidad que tenia cuando estaba dentro de fin_rays
 * (desde postflexion): tener clase propia permite ajustarla sin tocar las
 * aletas, pero cambiar la etapa es una decision biologica pendiente.
 */
val AVAILABLE_AT_STAGE: Map<Stage, Set<String>> = mapOf(
    Stage.YS to setOf("shape", "myomeres"),
    Stage.PRF to setOf("shape", "myomeres", "pigment"),
    Stage.F to setOf("shape", "myomeres", "pigment", "fin_position"),
    Stage.POF to setOf("shape", "myomeres", "pigment", "fin_position", "fin_rays", "branchiostegals"),
    Stage.TR to setOf("shape", "myomeres", "pigment", "fin_position", "fin_rays", "branchiostegals",
        "vertebrae", "gill_rakers"),
    Stage.JUV to setOf("shape", "pigment", "fin_position", "fin_rays", "branchiostegals",
        "vertebrae", "gill_rakers"),
)

/**
 * A que clase pertenece cada caracter, para el filtro por etapa y para la
 * fase del interrogatorio (Interrogatorio.kt).
 *
 * La clase "shape" por ahora solo tiene gut_length. Un caracter de forma
 * nuevo llega como entrada de "diagnostic" con "value" categorico; se agrega
 * aca con clase "shape" y su redaccion en PROMPTS/OPCIONES_CATEGORICAS.
 */
val CHARACTER_CLASS: Map<String, String> = mapOf(
    // Donde queda el ano: antes ("short") o despues ("long") de la mitad del
    // cuerpo. Se ve desde la eclosion. Lo extrae el pipeline del texto
    // diagnostico, solo cuando es inequivoco (~30 especies).
    "gut_length" to "shape",
    "myomeres_total" to "myomeres",
    "myomeres_preanal" to "myomeres",
    "myomeres_postanal" to "myomeres",
    // Algunas fichas dan los miomeros en la tabla en vez de vertebras.
    // "Myomeres: Total" es el mismo conteo que myomeres_total (ver
    // CLAVES_EQUIVALENTES).
    "Myomeres: Total" to "myomeres",
    "Myomeres: Precaudal" to "myomeres",
    "Myomeres: Caudal" to "myomeres",
    "D_insertion_over_A_ray" to "fin_position",
    "D_insertion_postanal_myomere" to "fin_position",
    "hindgut_pigment" to "pigment",
    "Anal spines" to "fin_rays",
    "Anal rays" to "fin_rays",
    "Dorsal spines" to "fin_rays",
    "Dorsal rays" to "fin_rays",
    "Pectoral" to "fin_rays",
    "Pectoral spines" to "fin_rays",
    // "Pelvic" sigue siendo el numero de RADIOS; la espina de "I,5" viene
    // aparte en "Pelvic spines" (ver ALETAS).
    "Pelvic" to "fin_rays",
    "Pelvic spines" to "fin_rays",
    // Pinulas de atunes y afines: aletillas sueltas detras de dorsal y anal.
    "Dorsal finlets" to "fin_rays",
    "Anal finlets" to "fin_rays",
    "Caudal: Total" to "fin_rays",
    // No son radios de aleta: son los de la membrana bajo la garganta.
    "Branchiostegals" to "branchiostegals",
    "Vertebrae: Total" to "vertebrae",
    "Vertebrae: Precaudal" to "vertebrae",
    "Vertebrae: Caudal" to "vertebrae",
    // Las branquiespinas se forman tarde: pedirlas en una larva de preflexion
    // o postflexion es pedir algo que no existe todavia.
    "Gill rakers: Total" to "gill_rakers",
    "Gill rakers: Upper" to "gill_rakers",
    "Gill rakers: Lower" to "gill_rakers",
)

/**
 * Caracteres que el atlas trae pero que NUNCA se preguntan, a proposito:
 * organos internos que no se ven en una larva. Se quedan sin clase (asi el
 * motor no los ofrece) y se listan aca para que el reporte de cobertura no
 * los confunda con caracteres pendientes de clasificar.
 */
val NUNCA_SE_PREGUNTAN: Set<String> = setOf(
    "Pyloric caeca",
    "Gas glands",
)

/**
 * Clase por defecto para un caracter no mapeado.
 *
 * Deliberadamente CONSERVADORA: "unknown" no aparece en ningun conjunto de
 * AVAILABLE_AT_STAGE, asi que un caracter sin clasificar nunca se pregunta.
 * Es preferible a asumir que se puede observar y mandar al tecnico a buscar
 * algo que en esa etapa no existe.
 *
 * Pendientes de clasificar (CoberturaCaracteresTest los reporta bajo "sin
 * CHARACTER_CLASS"): "Caudal: Procurrent Upper"/"Lower" (radios cortos de
 * las orillas de la cola, se forman tarde) y "Caudal: Principal" (llega en
 * notacion compuesta "10+9" y casi nunca viaja a la app).
 */
const val DEFAULT_CHARACTER_CLASS = "unknown"
