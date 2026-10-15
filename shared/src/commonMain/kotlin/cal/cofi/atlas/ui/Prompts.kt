package cal.cofi.atlas.ui

import cal.cofi.atlas.ALETAS
import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.CHARACTER_CLASS
import cal.cofi.atlas.NUNCA_SE_PREGUNTAN
import cal.cofi.atlas.ReglasInterrogatorio
import cal.cofi.atlas.claveCanonica

/**
 * Como se le pide cada caracter al tecnico, portado de identify.py (PROMPTS).
 * Nunca se muestra el nombre tecnico del caracter (myomeres_total, etc.) en
 * la UI. Las aletas con espinas no estan aca sino en [PROMPTS_ALETA]: se
 * piden espinas y radios blandos juntos, en una sola pregunta por aleta.
 *
 * Sin redaccion, a proposito: "Myomeres: Precaudal"/"Caudal". El limite
 * entre miomeros precaudales y caudales es el de las vertebras (la primera
 * con espina hemal), que en una larva no se ve; no es el ano (eso seria
 * preanal/postanal). Estan clasificados, pero sin forma llana de pedirlos.
 */
val PROMPTS: Map<String, String> = mapOf(
    "myomeres_total" to
        "Cuenta los segmentos musculares en V a lo largo de todo el cuerpo, " +
        "de la cabeza a la cola.",
    "myomeres_preanal" to
        "Cuenta los segmentos musculares desde la cabeza hasta el ano.",
    "D_insertion_over_A_ray" to
        "Ubica donde empieza la aleta de arriba (dorsal). Contando los radios " +
        "de la aleta de abajo (anal) desde el frente, ¿sobre cual queda?",
    "hindgut_pigment" to
        "¿Ves manchas negras sobre la parte final del intestino, del lado de arriba?",
    "Vertebrae: Precaudal" to
        "Cuenta las vertebras desde la cabeza hasta la primera con espina hemal.",
    "Vertebrae: Total" to
        "Cuenta todas las vertebras.",
    "Branchiostegals" to
        "Cuenta los radios branquiostegos bajo la mandibula.",
    "Dorsal finlets" to
        "Cuenta las aletillas sueltas que hay entre la aleta de arriba (dorsal) y la cola.",
    "Anal finlets" to
        "Cuenta las aletillas sueltas que hay entre la aleta de abajo (anal) y la cola.",
    "Caudal: Total" to
        "Cuenta todos los radios de la aleta de la cola.",
)

/**
 * Una pregunta por aleta, con dos campos: espinas y radios blandos (ver
 * Aleta en Engine.kt). La explicacion de que es cada cosa va en
 * [AYUDA_ESPINAS_Y_RADIOS], comun a todas.
 */
val PROMPTS_ALETA: Map<String, String> = mapOf(
    "anal" to "Cuenta los elementos de la aleta de abajo, la que está detrás del ano (anal).",
    "dorsal" to "Cuenta los elementos de la aleta de arriba (dorsal).",
    "pelvica" to "Cuenta los elementos de una de las aletas de abajo, cerca del vientre (pélvicas).",
    "pectoral" to "Cuenta los elementos de una de las aletas del costado, detrás de la cabeza (pectorales).",
)

/**
 * El atlas escribe las espinas en romanos ("III", "I,5"); el tecnico escribe
 * numeros normales. Lo de "pon 0" importa: sin espinas no es lo mismo que no
 * saber.
 */
const val AYUDA_ESPINAS_Y_RADIOS =
    "Espinas: las duras y lisas, sin divisiones. Radios blandos: los flexibles, " +
        "divididos en segmentos. Escribe números normales (3, no III). Si no tiene " +
        "espinas, pon 0. Si no distingues una cosa de la otra, deja ese campo vacío."

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
 * Cuando lleguen, cada caracter necesita aqui TODAS sus opciones con texto
 * llano (y su pregunta en PROMPTS); si falta alguna, el motor no lo pregunta.
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
 * Nombres cortos de las partes de una aleta, para las vistas de auditoria
 * (resultado, historial): ahi se lista cada caracter por separado.
 */
private val ETIQUETAS_ALETA: Map<String, String> = mapOf(
    "Anal spines" to "Espinas de la aleta de abajo (anal)",
    "Anal rays" to "Radios blandos de la aleta de abajo (anal)",
    "Dorsal spines" to "Espinas de la aleta de arriba (dorsal)",
    "Dorsal rays" to "Radios blandos de la aleta de arriba (dorsal)",
    "Pelvic spines" to "Espinas de la aleta pélvica",
    "Pelvic" to "Radios blandos de la aleta pélvica",
    "Pectoral spines" to "Espinas de la aleta pectoral",
    "Pectoral" to "Radios blandos de la aleta pectoral",
)

/**
 * Caracteres que ya no se preguntan pero siguen en sesiones guardadas: los
 * conteos del filtro grueso de antes y las medidas morfometricas, que se
 * quitaron (la larva fijada en formol se encoge). Solo sirven para que el
 * historial no muestre la clave cruda; nunca se usan para preguntar.
 */
private val ETIQUETAS_HISTORICAS: Map<String, String> = mapOf(
    "vertebrae_total" to "Segmentos musculares o vértebras, total (filtro por familia)",
    "anal_rays_total" to "Aleta de abajo (anal), espinas y radios juntos (filtro por familia)",
    "dorsal_rays_total" to "Aleta de arriba (dorsal), espinas y radios juntos (filtro por familia)",
    "Sn-A/BL" to "Del hocico al ano / talla",
    "BD/BL" to "Altura máxima del cuerpo / talla",
    "HL/BL" to "Largo de la cabeza / talla",
    "P1L/BL" to "Largo de la aleta pectoral / talla",
    "P2L/BL" to "Largo de la aleta pélvica / talla",
    "HW/HL" to "Ancho de la cabeza / largo de la cabeza",
    "SnL/HL" to "Largo del hocico / largo de la cabeza",
    "ED/HL" to "Diámetro del ojo / largo de la cabeza",
)

/**
 * Traduce un caracter tecnico a su redaccion, o lo deja tal cual si no hay
 * redaccion todavia. Usado en vistas de auditoria (resultado, historial)
 * donde mostrar la clave cruda es mejor que ocultar el caracter — a
 * diferencia de PantallaPregunta, que nunca debe mostrarle jerga al tecnico
 * en el momento de preguntar.
 */
fun traducirCaracter(character: String): String =
    ETIQUETAS_ALETA[character]
        ?: PROMPTS[claveCanonica(character)]
        ?: ETIQUETAS_HISTORICAS[character]
        ?: character

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
