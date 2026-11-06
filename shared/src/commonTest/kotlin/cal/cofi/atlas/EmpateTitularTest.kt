package cal.cofi.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Identification.tied: los candidatos que comparten el titular. La
 * pantalla de resultado arma sus tarjetas con esto en vez de partir el
 * label de UNRESOLVED, que nombra a todos los empatados.
 */
class EmpateTitularTest {

    private val anal1012 = mapOf("Anal rays" to (10.0 to 12.0))

    private fun resolver(vararg especies: Species, obs: List<Observation>): Identification =
        IdentificationEngine(atlasSintetico(*especies)).resolve(Stage.POF, obs)

    @Test
    fun conUnSoloGanadorTiedEsEseCandidato() {
        val id = resolver(
            especieSintetica("alfa", familia = "Alfidae", meristics = anal1012),
            especieSintetica("beta", familia = "Betidae", meristics = mapOf("Anal rays" to (20.0 to 22.0))),
            obs = listOf(Observation("Anal rays", value = 11.0)),
        )
        assertEquals(TaxonLevel.SPECIES, id.level)
        assertEquals(listOf("Alfa sinteticus"), id.tied.map { it.species.species })
    }

    @Test
    fun sinResolverTiedTraeATodosLosEmpatadosYANingunContradicho() {
        val id = resolver(
            especieSintetica("alfa", familia = "Alfidae", meristics = anal1012),
            especieSintetica("beta", familia = "Betidae", meristics = anal1012),
            especieSintetica("gamma", familia = "Gammidae", meristics = anal1012),
            especieSintetica("delta", familia = "Deltidae", meristics = mapOf("Anal rays" to (20.0 to 22.0))),
            obs = listOf(Observation("Anal rays", value = 11.0)),
        )
        assertEquals(TaxonLevel.UNRESOLVED, id.level)
        assertEquals(
            setOf("Alfa sinteticus", "Beta sinteticus", "Gamma sinteticus"),
            id.tied.map { it.species.species }.toSet(),
        )
        assertEquals(id.tied.map { it.species.species }.sorted().joinToString(" / "), id.label)
    }

    /**
     * Los empatados no siempre encabezan el ranking: si ningun candidato
     * sin contradicciones tiene evidencia (score 0), uno contradicho a
     * medias (score 0.5) queda primero en candidates sin entrar al titular.
     * Por eso la pantalla pone tied antes que candidates.
     */
    @Test
    fun unContradichoPuedeEncabezarCandidatesSinEstarEnTied() {
        val id = resolver(
            especieSintetica(
                "alfa",
                familia = "Alfidae",
                meristics = anal1012 + ("Dorsal rays" to (10.0 to 12.0)),
            ),
            especieSintetica("beta", familia = "Betidae"),
            especieSintetica("gamma", familia = "Gammidae"),
            obs = listOf(Observation("Anal rays", value = 11.0), Observation("Dorsal rays", value = 30.0)),
        )
        assertEquals("Alfa sinteticus", id.candidates.first().species.species)
        assertEquals(setOf("Beta sinteticus", "Gamma sinteticus"), id.tied.map { it.species.species }.toSet())
        assertTrue(id.tied.none { it.species.species == "Alfa sinteticus" })
    }
}
