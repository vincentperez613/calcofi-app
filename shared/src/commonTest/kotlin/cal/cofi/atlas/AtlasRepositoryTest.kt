package cal.cofi.atlas

import calcofilarvas.shared.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
private data class VerificationCheckDto(
    val id: String,
    val verified_by_human: Boolean = true,
)

/** Sin default: aca se cuentan solo las que dicen true explicitamente, como el candado. */
@Serializable
private data class VerificacionExplicitaDto(val verified_by_human: Boolean? = null)

class AtlasRepositoryTest {

    @Test
    fun cargaTodasLasEspeciesVerificadasDelBundle() = runTest {
        val repo = AtlasRepository()
        val estado = repo.cargar()

        val listo = assertIs<EstadoCarga.Listo>(estado, "esperaba EstadoCarga.Listo, fue $estado")
        val raw = Res.readBytes("files/data/species.json").decodeToString()
        val verificadas = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<VerificacionExplicitaDto>>(raw)
            .count { it.verified_by_human == true }

        assertTrue(verificadas > 0, "el bundle no trae ninguna especie verificada")
        assertEquals(verificadas, listo.data.species.size, "deberian cargar todas las verificadas y solo esas")
        assertTrue(listo.data.species.any { it.id == "engraulis_mordax" }, "falta Engraulis mordax")
    }

    @Test
    fun cargaLaHistoriaDeVida() = runTest {
        val listo = assertIs<EstadoCarga.Listo>(AtlasRepository().cargar())
        val lh = listo.data.species.single { it.id == "engraulis_mordax" }.lifeHistory!!

        assertEquals((1..12).toList(), lh.spawningMonths)
        assertEquals(listOf(1, 2, 3, 4, 5, 12), lh.spawningPeak)
        assertTrue(lh.range!!.startsWith("Queen Charlotte"), lh.range)
        assertTrue(lh.spawningSeason!!.isNotBlank())
    }

    @Test
    fun cargaMetadatosDeFigurasDelBundle() = runTest {
        val listo = assertIs<EstadoCarga.Listo>(AtlasRepository().cargar())

        assertTrue(listo.data.figures.any { it.sizeLabel != null }, "figure size labels are missing")
        assertTrue(listo.data.figures.any { it.needsReview }, "figure review flags are missing")
    }

    @Test
    fun lifeHistoryEsOpcionalYDescartaMesesImposibles() = runTest {
        val speciesJson = """
            [
              {"id":"sin_bloque","species":"Testus nudus","family":"Testidae","pdf_page":1,"verified_by_human":true},
              {"id":"vacio","species":"Testus vacuus","family":"Testidae","pdf_page":2,"verified_by_human":true,
               "life_history":{}},
              {"id":"ocr","species":"Testus ocr","family":"Testidae","pdf_page":3,"verified_by_human":true,
               "life_history":{"spawning_season":"Summer","spawning_months":[6,7,13,0,7],"spawning_peak":[]}}
            ]
        """.trimIndent()
        val repo = AtlasRepository(readFile = { path ->
            when (path) {
                "data/species.json" -> speciesJson
                "data/coarse_filter.json" -> "{}"
                "data/figures.json" -> "[]"
                else -> error("ruta inesperada: $path")
            }
        })
        val especies = assertIs<EstadoCarga.Listo>(repo.cargar()).data.species.associateBy { it.id }

        assertEquals(null, especies.getValue("sin_bloque").lifeHistory)
        assertEquals(null, especies.getValue("vacio").lifeHistory)
        assertEquals(listOf(6, 7), especies.getValue("ocr").lifeHistory!!.spawningMonths)
    }

    @Test
    fun coarseFilterTrae170FamiliasEnVertebraeTotal() = runTest {
        val repo = AtlasRepository()
        val listo = assertIs<EstadoCarga.Listo>(repo.cargar())

        val familias = listo.data.coarse.tables.getValue("vertebrae_total")
        assertEquals(170, familias.size, "vertebrae_total deberia tener 170 familias")
    }

    @Test
    fun ningunaEspecieCargadaQuedoSinVerificarPorHumano() = runTest {
        val repo = AtlasRepository()
        val listo = assertIs<EstadoCarga.Listo>(repo.cargar())

        // Chequeo independiente contra el JSON crudo: ninguna especie que haya
        // llegado al dominio puede tener verified_by_human == false.
        val raw = Res.readBytes("files/data/species.json").decodeToString()
        val verificado = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<VerificationCheckDto>>(raw)
            .associate { it.id to it.verified_by_human }

        for (sp in listo.data.species) {
            assertTrue(
                verificado[sp.id] != false,
                "${sp.id} llego al dominio sin verified_by_human",
            )
        }
    }

    @Test
    fun segundaCargaUsaCache() = runTest {
        val repo = AtlasRepository()
        val primero = assertIs<EstadoCarga.Listo>(repo.cargar())
        val segundo = assertIs<EstadoCarga.Listo>(repo.cargar())

        assertTrue(primero.data === segundo.data, "la segunda carga deberia reusar el mismo AtlasData")
    }

    @Test
    fun especieSinCampoVerifiedByHumanSeDescarta() = runTest {
        val speciesJson = """
            [
              {"id":"con_flag_true","species":"Testus verificatus","family":"Testidae","pdf_page":1,"verified_by_human":true},
              {"id":"sin_flag","species":"Testus incognitus","family":"Testidae","pdf_page":2}
            ]
        """.trimIndent()

        val repo = AtlasRepository(readFile = { path ->
            when (path) {
                "data/species.json" -> speciesJson
                "data/coarse_filter.json" -> "{}"
                "data/figures.json" -> "[]"
                else -> error("ruta inesperada: $path")
            }
        })

        val listo = assertIs<EstadoCarga.Listo>(repo.cargar())

        assertEquals(1, listo.data.species.size, "la especie sin verified_by_human deberia descartarse")
        assertEquals("con_flag_true", listo.data.species.single().id)
        assertEquals(1, repo.descartadasPorVerificacion)
    }
}
