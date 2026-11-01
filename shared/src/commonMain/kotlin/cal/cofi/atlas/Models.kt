package cal.cofi.atlas

/**
 * Modelos del dataset del CalCOFI Atlas 33.
 *
 * Este archivo NO tiene dependencias: ni kotlinx.serialization, ni Compose, ni
 * Android. Va en commonMain y compila igual para Android y para Windows.
 * La carga de JSON vive aparte, en Loader.kt, para que el dominio y el motor
 * se puedan probar con tests de JVM puros.
 *
 * El dataset completo (~435 especies) pesa ~2.4 MB en JSON. Cabe en memoria
 * sin problema, incluso en un telefono de gama baja. No hace falta SQLite.
 */

/** Etapas de desarrollo. El orden del enum es la progresion ontogenetica. */
enum class Stage(val code: String) {
    EGG("Egg"), YS("Y-S"), PRF("PrF"), F("F"), POF("PoF"), TR("Tr"), JUV("Juv");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String?): Stage? = code?.let { byCode[it] }
    }
}

/**
 * Rango publicado. NO es un rango poblacional: sale de pocos ejemplares
 * (a veces n=3), asi que siempre se compara con tolerancia.
 */
data class Range(val min: Double, val max: Double) {
    fun contains(v: Double, tol: Double): Boolean = v >= min - tol && v <= max + tol
}

/**
 * Cuantos ejemplares respaldan los datos de una etapa.
 * kind == "literature" significa que el atlas no midio nada propio.
 */
data class SampleSize(val kind: String, val n: Int) {
    /**
     * Tolerancia derivada del respaldo. Evita tratar un rango de n=3 con la
     * misma confianza que uno de n=200.
     */
    val tolerance: Double
        get() = when {
            kind == "literature" || n == 0 -> 2.0
            n < 5 -> 2.0
            n < 15 -> 1.0
            else -> 0.5
        }

    companion object {
        val UNKNOWN = SampleSize("unknown", 0)
    }
}

/**
 * Caracter cualitativo del texto diagnostico (pigmento, posicion de aleta).
 *
 * [conditional] es la condicion que lo limita, p.ej. "before 18 mm". Perderla
 * cambia la respuesta: Cetengraulis NO tiene pigmento dorsal en el intestino
 * antes de 18 mm, pero SI despues. Tratarlo como "absent" a secas descartaria
 * mal a los ejemplares grandes.
 */
data class DiagnosticChar(
    val range: Range? = null,
    val value: String? = null,
    val conditional: String? = null,
)

/**
 * Bloque LIFE HISTORY de la ficha. No identifica nada: le da a la app con
 * que DUDAR de un resultado (ver Riesgo.kt). Todo es opcional — hay fichas
 * del atlas sin temporada, sin distribucion o sin nada.
 *
 * [range], [habitat] y [spawningSeason] son el texto del atlas tal cual: en
 * ingles y con algun error de OCR. Se muestran asi, sin traducir ni
 * corregir, para que el tecnico vea exactamente que dice la fuente.
 *
 * [spawningMonths] y [spawningPeak] (1-12) salen de leer ese texto
 * automaticamente y NO pasaron por revision humana. Muchas veces dicen en
 * que meses se colectaron larvas, no cuando desova la especie, y a veces
 * hablan de otra region ("(panama)"). Por eso la app los muestra siempre
 * junto al texto original.
 */
data class LifeHistory(
    val range: String? = null,
    val habitat: String? = null,
    val spawningSeason: String? = null,
    val spawningMonths: List<Int> = emptyList(),
    val spawningPeak: List<Int> = emptyList(),
)

data class Species(
    val id: String,
    val species: String,
    val family: String,
    val pdfPage: Int,
    val meristics: Map<String, Range>,
    val diagnostic: Map<String, DiagnosticChar>,
    val sampleSizes: Map<String, SampleSize>,
    val lifeHistory: LifeHistory? = null,
) {
    val genus: String get() = species.substringBefore(' ')

    /**
     * Rango publicado TAL COMO viene en la ficha, o null si el atlas no lo
     * reporta. Un caracter ausente NUNCA debe penalizar a la especie.
     *
     * No resuelve equivalencias (Myomeres: Total = myomeres_total) ni el
     * sustituto de miomeros por vertebras: eso es criterio del motor y vive
     * en un solo lugar, [rangoDe] en Engine.kt. El motor siempre pregunta
     * via rangoDe, nunca via esta funcion.
     */
    fun rangeFor(key: String): Range? = meristics[key] ?: diagnostic[key]?.range

    fun toleranceFor(stage: Stage): Double =
        (sampleSizes[stage.code] ?: SampleSize.UNKNOWN).tolerance
}

/**
 * Rangos por FAMILIA (Tablas Introduccion 3-5). Reduce ~170 familias a unas
 * pocas con un solo conteo, sin pedirle vocabulario al tecnico.
 */
class CoarseFilter(val tables: Map<String, Map<String, Range>>) {
    fun families(character: String, count: Double, tol: Double = 1.0): Set<String> =
        tables[character]
            ?.filterValues { it.contains(count, tol) }
            ?.keys
            ?: emptySet()

    /**
     * De [familias], las que la tabla de [character] no menciona. Una tabla
     * no puede descartar una familia de la que no dice nada: es la misma
     * regla de "un dato que el atlas no trae no penaliza" que aplica el
     * motor a nivel especie. Pasa mas de lo que parece — con el atlas
     * completo, ~15% de las especies traen el nombre de familia con un
     * error de OCR ("Scorpa Enidae", "Myctopiddae") que nunca va a coincidir
     * con la tabla, y alguna familia real (Scombridae) falta en alguna de
     * las tres tablas. Sin esto, el primer conteo las haria desaparecer.
     */
    fun noListadas(character: String, familias: Set<String>): Set<String> {
        val tabla = tables[character] ?: return familias
        return familias.filterTo(mutableSetOf()) { it !in tabla }
    }
}

/**
 * Una figura recortable de una lamina.
 *
 * Se guarda la LAMINA completa mas una caja; recortar al mostrar evita
 * duplicar pixeles (10.5 MB contra ~100 MB).
 */
data class FigureRef(
    val plate: String,
    /** [x0, y0, x1, y1] en pixeles de la lamina. */
    val box: List<Int>,
    val sizeMm: Double? = null,
    val sizeLabel: String? = null,
    val stage: Stage? = null,
    /**
     * De donde salio la etapa: "caption" (la dice el pie) o "media"/"baja"
     * (inferida de la talla). La UI deberia distinguirlas.
     */
    val stageSource: String? = null,
    /**
     * Nombre TAL COMO aparece en el pie de figura. Puede ser mas laxo que la
     * ficha: el atlas rotula varias larvas tempranas como "Anchoa sp." aunque
     * la ficha sea de A. compressa.
     */
    val captionTaxon: String? = null,
    val species: String? = null,
    val uncertain: Boolean = false,
    val needsReview: Boolean = false,
) {
    /**
     * Lo que se le debe MOSTRAR al usuario como identidad del dibujo.
     * Usar el nombre de la ficha en su lugar prometeria precision que el
     * dibujo no respalda.
     */
    fun displayLabel(): String = captionTaxon ?: species ?: "sin identificar"
}

class AtlasData(
    val species: List<Species>,
    val coarse: CoarseFilter,
    val figures: List<FigureRef>,
) {
    fun figuresFor(sp: Species, stage: Stage): List<FigureRef> =
        figures.filter { it.species == sp.species && it.stage == stage }
}
