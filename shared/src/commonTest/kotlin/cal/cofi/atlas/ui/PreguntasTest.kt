package cal.cofi.atlas.ui

import cal.cofi.atlas.AtlasData
import cal.cofi.atlas.AtlasRepository
import cal.cofi.atlas.EstadoCarga
import cal.cofi.atlas.Fase
import cal.cofi.atlas.MIOMEROS_TOTALES
import cal.cofi.atlas.Stage
import cal.cofi.atlas.interrogar
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private suspend fun cargarAtlasReal(): AtlasData {
    val estado = AtlasRepository().cargar()
    return assertIs<EstadoCarga.Listo>(estado, "esperaba EstadoCarga.Listo, fue $estado").data
}

class CoberturaCaracteresTest {

    /**
     * No afirma nada: es un reporte. Cuando entren familias nuevas, esto
     * imprime que caracteres se quedaron sin clase o sin redaccion en vez de
     * dejarlos desaparecer callados del interrogatorio.
     */
    @Test
    fun reportaCaracteresSinClaseOSinPrompt() = runTest {
        val cobertura = coberturaDeCaracteres(cargarAtlasReal())
        println("Caracteres sin CHARACTER_CLASS (${cobertura.sinClase.size}): ${cobertura.sinClase}")
        println("Caracteres sin PROMPTS (${cobertura.sinPrompt.size}): ${cobertura.sinPrompt}")
        println("Categoricos sin catalogo de opciones (${cobertura.sinOpciones.size}): ${cobertura.sinOpciones}")
    }

    @Test
    fun todoLoPreguntableTieneRedaccionYClase() {
        // Lo contrario del reporte: nada de lo que el interrogatorio puede
        // ofrecer se queda sin redaccion ni sin etapa.
        for (clave in PREGUNTABLES) {
            assertTrue(clave in cal.cofi.atlas.CHARACTER_CLASS, "'$clave' es preguntable pero no tiene clase")
            assertTrue(tieneEtiqueta(clave), "'$clave' es preguntable pero no tiene nombre corto")
        }
    }
}

class SesionAdaptativaTest {

    @Test
    fun noSeATodoDejaATodasLasEspeciesVivas() = runTest {
        val data = cargarAtlasReal()
        // Ejemplar vacio: todo se contesta "no se / no visible".
        val r = interrogar(data, Stage.POF, REGLAS_APP, emptyMap(), tallaMm = 18.4)

        assertEquals(
            data.species.map { it.id }.toSet(),
            r.identificacion.candidates.map { it.species.id }.toSet(),
            "todas las especies deberian seguir vivas cuando nada se responde",
        )
        assertTrue(r.identificacion.candidates.all { it.supporting.isEmpty() && it.contradicting.isEmpty() })
        // "No se" a un conteo con tabla tampoco filtra familias.
        assertEquals(null, r.final.familias)
    }
}

/**
 * La identificacion completa que pide el criterio de aceptacion, sin
 * ventana: Engraulis mordax en postflexion, 18.4 mm (la talla real de su
 * lamina en el atlas, p195), siguiendo exactamente el camino de
 * PantallaPregunta sobre el bundle real. El ejemplar contesta con valores
 * dentro de los rangos de su ficha; lo que no esta en el mapa es "no se".
 */
class IdentificacionCompletaTest {

    private val engraulisMordax: Map<String, Any> = mapOf(
        MIOMEROS_TOTALES to 45.0,
        "myomeres_preanal" to 28.0,
        "Anal spines" to 0.0, "Anal rays" to 22.0,
        "Dorsal spines" to 0.0, "Dorsal rays" to 16.0,
        "Pelvic spines" to 0.0, "Pelvic" to 7.0,
        "Pectoral" to 16.0,
        "Branchiostegals" to 12.0,
        "D_insertion_over_A_ray" to 3.0,
        "hindgut_pigment" to "present",
    )

    @Test
    fun engraulisMordaxEnPostflexion() = runTest {
        val data = cargarAtlasReal()
        val r = interrogar(data, Stage.POF, REGLAS_APP, engraulisMordax, tallaMm = 18.4)

        for ((i, p) in r.preguntas.withIndex()) {
            println("${i + 1}. [${p.fase}] ${p.clave}${if (p.ancla != null) " (ancla)" else ""}")
        }
        println("familias al final: ${r.final.familias?.sorted()}")
        println("resultado: ${r.identificacion.label} (${r.identificacion.level})")
        for (c in r.identificacion.candidates.take(5)) {
            println("  ${c.species.species}: ${(c.score * 100).toInt()}% a favor=${c.supporting} en contra=${c.contradicting}")
        }

        // Orden: las fases nunca retroceden, y los miomeros van primero.
        val fases = r.preguntas.map { it.fase }
        assertEquals(fases.sortedBy { it.ordinal }, fases)
        assertEquals(MIOMEROS_TOTALES, r.preguntas.first().clave)
        assertTrue(Fase.FORMA !in fases, "el bundle no trae caracteres de forma: la fase se salta")
        // Cada conteo una sola vez.
        val claves = r.preguntas.flatMap { it.claves }
        assertEquals(claves.toSet().size, claves.size, "claves repetidas: $claves")

        val primero = r.identificacion.candidates.first()
        assertEquals("engraulis_mordax", primero.species.id)
        assertTrue(primero.contradicting.isEmpty(), "E. mordax no deberia tener nada en contra: ${primero.contradicting}")
    }
}
