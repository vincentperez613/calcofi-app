package cal.cofi.atlas.ui

import cal.cofi.atlas.ALETAS
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.CHARACTER_CLASS
import cal.cofi.atlas.NUNCA_SE_PREGUNTAN
import cal.cofi.atlas.ReglasInterrogatorio
import cal.cofi.atlas.Stage
import cal.cofi.atlas.claveCanonica

/**
 * Como se le pide cada caracter al tecnico, con la terminologia del atlas
 * (miomeros preanales, insercion de la dorsal, espina hemal). La UI va en
 * ingles, igual que el texto del atlas que muestra al lado. Nunca se
 * muestra la clave cruda del caracter (myomeres_total, etc.). Las aletas con
 * espinas no estan aca sino en [PROMPTS_ALETA]: se piden espinas y radios
 * blandos juntos, en una sola pregunta por aleta.
 *
 * Sin redaccion, a proposito: "Myomeres: Precaudal"/"Caudal". El limite
 * entre miomeros precaudales y caudales es el de las vertebras (la primera
 * con espina hemal), que en una larva no se ve; no es el ano (eso seria
 * preanal/postanal). Estan clasificados, pero no hay forma de pedirlos que
 * se pueda verificar en una larva.
 *
 * D_insertion_over_A_ray es la INSERCION de la dorsal (el extremo posterior
 * de su base), no su origen: el atlas dice "D insertion above A ray 5-7".
 */
val PROMPTS: Map<String, String> = mapOf(
    "myomeres_total" to
        "Count total myomeres (preanal + postanal).",
    "myomeres_preanal" to
        "Count preanal myomeres (anterior to the anus).",
    "D_insertion_over_A_ray" to
        "Locate the dorsal-fin insertion (posterior end of the dorsal-fin base). " +
        "Counting anal-fin rays from the anal-fin origin, above which ray does it lie?",
    "hindgut_pigment" to
        "Melanophores on the dorsal surface of the hindgut:",
    "Vertebrae: Precaudal" to
        "Count precaudal vertebrae (anterior to the first vertebra bearing a haemal spine).",
    "Vertebrae: Total" to
        "Count total vertebrae.",
    "Branchiostegals" to
        "Count branchiostegal rays (one side).",
    "Dorsal finlets" to
        "Count dorsal finlets (between the dorsal fin and the caudal fin).",
    "Anal finlets" to
        "Count anal finlets (between the anal fin and the caudal fin).",
    "Caudal: Total" to
        "Count total caudal-fin rays (principal + procurrent).",
)

/**
 * Una pregunta por aleta, con dos campos: espinas y radios blandos (ver
 * Aleta en Engine.kt). "Rays" en el sentido meristico: espinas y radios
 * blandos juntos. La explicacion de que es cada cosa va en
 * [AYUDA_ESPINAS_Y_RADIOS], comun a todas.
 */
val PROMPTS_ALETA: Map<String, String> = mapOf(
    "anal" to "Count anal-fin rays.",
    "dorsal" to "Count dorsal-fin rays.",
    "pelvica" to "Count pelvic-fin rays (one side).",
    "pectoral" to "Count pectoral-fin rays (one side).",
)

/**
 * El atlas escribe las espinas en romanos ("III", "I,5"); el tecnico escribe
 * numeros arabigos. Lo de "Enter 0" importa: sin espinas no es lo mismo que
 * no saber.
 */
const val AYUDA_ESPINAS_Y_RADIOS =
    "Spines: unsegmented and unbranched. Soft rays: segmented, often branched. " +
        "Enter Arabic numerals (3, not III). Enter 0 if there are no spines. " +
        "Leave a field blank if spines and soft rays cannot be told apart."

/**
 * Nombre de cada etapa como lo escribe el atlas, para captura e historial.
 * Las sesiones guardan el nombre del enum ("POF"): ver [nombreEtapaGuardada].
 */
fun nombreEtapa(etapa: Stage): String = when (etapa) {
    Stage.EGG -> "Egg"
    Stage.YS -> "Yolk-sac"
    Stage.PRF -> "Preflexion"
    Stage.F -> "Flexion"
    Stage.POF -> "Postflexion"
    Stage.TR -> "Transformation"
    Stage.JUV -> "Juvenile"
}

/** Para el historial: el nombre del enum guardado, o tal cual si no se reconoce. */
fun nombreEtapaGuardada(guardada: String): String =
    Stage.entries.firstOrNull { it.name == guardada }?.let(::nombreEtapa) ?: guardada

/** "1 family", "3 families": los textos de conteo, sin el "(s)" de antes. */
fun conteo(n: Int, singular: String, plural: String): String = "$n ${if (n == 1) singular else plural}"

/**
 * Una opcion de una pregunta categorica de mas de dos valores (p.ej. una
 * forma del cuerpo): [valor] es lo que trae el atlas en "diagnostic",
 * [texto] lo que ve el tecnico. El dibujo de cada opcion vive en
 * DIBUJOS_OPCION (PantallaPregunta.kt), porque necesita Compose.
 */
data class OpcionLlana(val valor: String, val texto: String)

/**
 * Catalogo de opciones por caracter, en el orden en que se muestran. Vacio
 * por ahora: todavia no esta decidido de donde salen las categorias de forma
 * ni sus valores por especie, y el bundle no trae ningun caracter "shape".
 * Cuando lleguen, cada caracter necesita aqui TODAS sus opciones con su
 * texto (y su pregunta en PROMPTS y su nombre corto en ETIQUETAS); si falta
 * alguna opcion, el motor no lo pregunta.
 */
val OPCIONES_CATEGORICAS: Map<String, List<OpcionLlana>> = emptyMap()

/** Union de caracteres preguntables, para el interrogatorio. */
val PREGUNTABLES: Set<String> =
    PROMPTS.keys + ALETAS.filter { it.id in PROMPTS_ALETA }.flatMap { it.claves }

/** Las reglas con las que corre el interrogatorio en la app (los tests arman las suyas). */
val REGLAS_APP: ReglasInterrogatorio = ReglasInterrogatorio(
    preguntables = PREGUNTABLES,
    opcionesConocidas = OPCIONES_CATEGORICAS.mapValues { (_, opciones) -> opciones.map { it.valor } },
)

/**
 * Nombre corto de cada caracter, para las vistas de auditoria (resultado,
 * historial, catalogo): ahi se lista cada caracter por separado y la
 * pregunta completa de [PROMPTS] seria ruido. Cubre todo lo preguntable y
 * las claves del motor que no son texto del atlas; las claves de la tabla
 * MERISTICS ("Gill rakers: Lower") ya vienen en la terminologia del atlas
 * y se muestran tal cual.
 */
private val ETIQUETAS: Map<String, String> = mapOf(
    "myomeres_total" to "Total myomeres",
    "myomeres_preanal" to "Preanal myomeres",
    "myomeres_postanal" to "Postanal myomeres",
    "D_insertion_over_A_ray" to "Dorsal-fin insertion above anal-fin ray",
    "D_insertion_postanal_myomere" to "Dorsal-fin insertion above postanal myomere",
    "hindgut_pigment" to "Dorsal hindgut pigment",
    "Vertebrae: Precaudal" to "Precaudal vertebrae",
    "Vertebrae: Total" to "Total vertebrae",
    "Branchiostegals" to "Branchiostegal rays",
    "Dorsal finlets" to "Dorsal finlets",
    "Anal finlets" to "Anal finlets",
    "Caudal: Total" to "Total caudal-fin rays",
    "Anal spines" to "Anal-fin spines",
    "Anal rays" to "Anal-fin soft rays",
    "Dorsal spines" to "Dorsal-fin spines",
    "Dorsal rays" to "Dorsal-fin soft rays",
    "Pelvic spines" to "Pelvic-fin spines",
    "Pelvic" to "Pelvic-fin soft rays",
    "Pectoral spines" to "Pectoral-fin spines",
    "Pectoral" to "Pectoral-fin soft rays",
)

/**
 * Caracteres que ya no se preguntan pero siguen en sesiones guardadas: los
 * conteos del filtro grueso de antes y las medidas morfometricas, que se
 * quitaron (la larva fijada en formol se encoge). Solo sirven para que el
 * historial no muestre la clave cruda; nunca se usan para preguntar.
 */
private val ETIQUETAS_HISTORICAS: Map<String, String> = mapOf(
    "vertebrae_total" to "Total myomeres or vertebrae (family filter)",
    "anal_rays_total" to "Anal-fin spines + soft rays (family filter)",
    "dorsal_rays_total" to "Dorsal-fin spines + soft rays (family filter)",
    "Sn-A/BL" to "Snout–anus length / BL",
    "BD/BL" to "Body depth / BL",
    "HL/BL" to "Head length / BL",
    "P1L/BL" to "Pectoral-fin length / BL",
    "P2L/BL" to "Pelvic-fin length / BL",
    "HW/HL" to "Head width / HL",
    "SnL/HL" to "Snout length / HL",
    "ED/HL" to "Eye diameter / HL",
)

/**
 * El nombre corto de un caracter, o la clave tal cual si no tiene. Usado en
 * vistas de auditoria (resultado, historial, catalogo), donde mostrar la
 * clave cruda es mejor que ocultar el caracter. PantallaPregunta no pasa
 * por aca: pregunta con [PROMPTS].
 */
fun traducirCaracter(character: String): String =
    ETIQUETAS[claveCanonica(character)]
        ?: ETIQUETAS_HISTORICAS[character]
        ?: character

/**
 * Si [character] tiene nombre corto propio. No basta con comparar
 * traducirCaracter contra la clave: en ingles el nombre puede coincidir con
 * la clave del atlas ("Dorsal finlets") y aun asi estar puesto a proposito.
 */
internal fun tieneEtiqueta(character: String): Boolean = claveCanonica(character) in ETIQUETAS

/**
 * Caracteres presentes en el dataset (meristics + diagnostic de todas las
 * especies cargadas) que faltan en [CHARACTER_CLASS] o en [PREGUNTABLES], o
 * que son categoricos sin catalogo de opciones. No es una validacion que
 * deba fallar un build: cuando se agregan familias nuevas, algunos
 * caracteres van a llegar sin clasificar o sin redaccion, y eso hay que
 * verlo, no que desaparezca callado.
 *
 * sinClase: nunca se preguntan (DEFAULT_CHARACTER_CLASS = "unknown" no esta
 * en AVAILABLE_AT_STAGE de ninguna etapa).
 * sinPrompt: el motor los descarta aunque tengan clase valida, porque no hay
 * como pedirselos al tecnico en lenguaje llano.
 * sinOpciones: categoricos con valores que no son presente/ausente y no
 * estan en [OPCIONES_CATEGORICAS].
 *
 * Los de NUNCA_SE_PREGUNTAN no se reportan: estan afuera a proposito.
 */
data class CoberturaCaracteres(
    val sinClase: List<String>,
    val sinPrompt: List<String>,
    val sinOpciones: List<String>,
)

fun coberturaDeCaracteres(data: AtlasData): CoberturaCaracteres {
    val presentes = buildSet {
        for (especie in data.species) {
            addAll(especie.meristics.keys)
            addAll(especie.diagnostic.keys)
        }
    }.map { claveCanonica(it) }.toSet() - NUNCA_SE_PREGUNTAN

    val categoricos = buildMap<String, MutableSet<String>> {
        for (especie in data.species) {
            for ((clave, d) in especie.diagnostic) {
                d.value?.let { getOrPut(clave) { mutableSetOf() }.add(it) }
            }
        }
    }
    val sinOpciones = categoricos.filter { (clave, valores) ->
        val catalogo = OPCIONES_CATEGORICAS[clave]?.map { it.valor }
        if (catalogo != null) !catalogo.containsAll(valores)
        else !valores.all { it.lowercase() in setOf("present", "absent") }
    }.keys

    return CoberturaCaracteres(
        sinClase = presentes.filter { it !in CHARACTER_CLASS }.sorted(),
        sinPrompt = presentes.filter { it !in PREGUNTABLES }.sorted(),
        sinOpciones = sinOpciones.sorted(),
    )
}
