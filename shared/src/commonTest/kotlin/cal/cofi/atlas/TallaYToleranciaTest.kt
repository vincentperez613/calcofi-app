package cal.cofi.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TallaPorEtapaTest {

    private val conVentana = especieSintetica(
        "ventana", stageSizes = mapOf(Stage.POF to VentanaTalla(10.0, 20.0), Stage.PRF to VentanaTalla(null, 6.0)),
    )
    private val sinVentana = especieSintetica("sin")
    private val engine = IdentificationEngine(atlasSintetico(conVentana, sinVentana))

    private fun candidato(stage: Stage, talla: Double?, id: String = "ventana") =
        engine.rank(stage, emptyList(), tallaMm = talla).single { it.species.id == id }

    @Test
    fun dentroDeLaVentanaCuentaAFavor() {
        val c = candidato(Stage.POF, 15.0)
        assertEquals(listOf(TALLA_ETAPA), c.supporting)
        assertTrue(c.marginal.isEmpty())
    }

    @Test
    fun laHolguraAbsorbeElFormolPeroQuedaComoMarginal() {
        // 20 mm + 25% = 25 mm; 8 mm es 20% menos que 10.
        for (talla in listOf(24.0, 8.0)) {
            val c = candidato(Stage.POF, talla)
            assertEquals(listOf(TALLA_ETAPA), c.supporting, "talla $talla")
            assertEquals(listOf(TALLA_ETAPA), c.marginal, "talla $talla")
        }
    }

    @Test
    fun muyFueraDeLaVentanaContradice() {
        assertEquals(listOf(TALLA_ETAPA), candidato(Stage.POF, 30.0).contradicting)
        assertEquals(listOf(TALLA_ETAPA), candidato(Stage.POF, 4.0).contradicting)
    }

    @Test
    fun unExtremoQueLaFichaNoFijaNoRestringe() {
        // "Hatching length: <2.3 mm": sin minimo, una larva chica no se descarta.
        assertEquals(listOf(TALLA_ETAPA), candidato(Stage.PRF, 1.2).supporting)
        assertEquals(listOf(TALLA_ETAPA), candidato(Stage.PRF, 9.0).contradicting)
    }

    @Test
    fun sinVentanaOSinTallaNoHayEvidencia() {
        assertEquals(0, candidato(Stage.POF, 30.0, id = "sin").evaluated)
        assertEquals(0, candidato(Stage.F, 30.0).evaluated, "la ficha no trae ventana para flexion")
        assertEquals(0, candidato(Stage.POF, null).evaluated)
    }
}

class ToleranciaDeConteosTest {

    private val directa = especieSintetica(
        "directa", meristics = mapOf("Anal rays" to (10.0 to 12.0)),
        diagnostic = mapOf(MIOMEROS_TOTALES to rango(50.0, 52.0)),
    )
    private val porVertebras = especieSintetica("vertebras", meristics = mapOf("Vertebrae: Total" to (50.0 to 52.0)))
    private val engine = IdentificationEngine(atlasSintetico(directa, porVertebras))

    private fun candidato(id: String, clave: String, valor: Double) =
        engine.rank(Stage.POF, listOf(Observation(clave, value = valor))).single { it.species.id == id }

    @Test
    fun unConteoDirectoAguantaUnoDeMasOMenos() {
        assertEquals(listOf("Anal rays"), candidato("directa", "Anal rays", 13.0).supporting)
        assertEquals(listOf("Anal rays"), candidato("directa", "Anal rays", 13.0).marginal)
        assertEquals(listOf("Anal rays"), candidato("directa", "Anal rays", 14.0).contradicting)
        assertEquals(listOf(MIOMEROS_TOTALES), candidato("directa", MIOMEROS_TOTALES, 54.0).contradicting)
    }

    @Test
    fun losMiomerosContraVertebrasTienenMasMargen() {
        assertTrue(usaSustitutoVertebral(porVertebras))
        assertFalse(usaSustitutoVertebral(directa))
        assertEquals(listOf(MIOMEROS_TOTALES), candidato("vertebras", MIOMEROS_TOTALES, 54.0).supporting)
        assertEquals(listOf(MIOMEROS_TOTALES), candidato("vertebras", MIOMEROS_TOTALES, 55.0).contradicting)
    }

    @Test
    fun entreEmpatadosVaPrimeroElQueNoNecesitoTolerancia() {
        // Los dos admiten 53 miomeros, pero solo "vertebras" gracias al margen.
        val otra = especieSintetica("otra", diagnostic = mapOf(MIOMEROS_TOTALES to rango(52.0, 54.0)))
        val ranking = IdentificationEngine(atlasSintetico(porVertebras, otra))
            .rank(Stage.POF, listOf(Observation(MIOMEROS_TOTALES, value = 53.0)))
        assertEquals(listOf("otra", "vertebras"), ranking.map { it.species.id })
        assertEquals(1.0, ranking[1].score, "el margen no le quita score: solo decide el orden")
    }
}

class FamiliaRespaldadaPorFichaTest {

    // Como Cyematidae: la tabla dice 70-80 vertebras, la ficha de una de sus
    // especies da 95-120 miomeros en el texto.
    private val coarse = mapOf("vertebrae_total" to mapOf("Cyematidae" to Range(70.0, 80.0), "Otridae" to Range(30.0, 32.0)))
    private val holti = especieSintetica("holti", "Cyematidae", diagnostic = mapOf(MIOMEROS_TOTALES to rango(95.0, 120.0)))
    private val otra = especieSintetica("otra", "Otridae", diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 32.0)))
    private val data = atlasSintetico(holti, otra, tablas = coarse)
    private val ancla = ANCLAS.first { it.clave == MIOMEROS_TOTALES }

    @Test
    fun laTablaNoDescartaAUnaFamiliaCuyaEspecieAdmiteElConteo() {
        val respaldadas = familiasRespaldadas(data.species, ancla, 100.0)
        assertEquals(setOf("Cyematidae"), respaldadas)
        val e = efectoEnFamilias(data.coarse, "vertebrae_total", 100.0, null, setOf("Cyematidae", "Otridae"), respaldadas)!!
        assertEquals(setOf("Cyematidae"), e.familias)
        assertEquals(setOf("Cyematidae"), e.respaldadasPorFicha)
        // No es "casi siempre un error de conteo": una ficha cargada lo admite.
        assertFalse(e.sinFamiliaCompatible)
    }

    @Test
    fun laEspecieCorrectaSigueEnLosMatches() {
        val r = interrogar(data, Stage.PRF, ReglasInterrogatorio(PREGUNTABLES_PRUEBA), mapOf(MIOMEROS_TOTALES to 100.0))
        assertEquals(listOf("holti"), r.identificacion.tied.map { it.species.id })
    }

    @Test
    fun unaAletaSeComparaConLaSumaDeLaFichaYDosErroresDeConteo() {
        val sp = especieSintetica("a", "Testidae", meristics = mapOf("Anal spines" to (3.0 to 3.0), "Anal rays" to (7.0 to 8.0)))
        val anal = ANCLAS.first { it.clave == "Anal rays" }
        assertEquals(setOf("Testidae"), familiasRespaldadas(listOf(sp), anal, 13.0))
        assertEquals(emptySet(), familiasRespaldadas(listOf(sp), anal, 14.0))
    }
}

class IntestinoTest {

    private val reglas = ReglasInterrogatorio(
        preguntables = PREGUNTABLES_PRUEBA + "gut_length",
        opcionesConocidas = mapOf("gut_length" to listOf("short", "long")),
    )
    private val corta = especieSintetica("corta", diagnostic = mapOf("gut_length" to valor("short"), MIOMEROS_TOTALES to rango(24.0, 26.0)))
    private val larga = especieSintetica("larga", diagnostic = mapOf("gut_length" to valor("long"), MIOMEROS_TOTALES to rango(24.0, 26.0)))
    private val sinDato = especieSintetica("sindato", diagnostic = mapOf(MIOMEROS_TOTALES to rango(24.0, 26.0)))
    private val data = atlasSintetico(corta, larga, sinDato)

    @Test
    fun seAbreLaFaseDeFormaConLasDosOpciones() {
        val paso = assertIs<PasoInterrogatorio.Preguntar>(
            siguientePaso(IdentificationEngine(data), Stage.PRF, EstadoInterrogatorio(), null, reglas),
        )
        assertEquals(Fase.FORMA, paso.pregunta.fase)
        assertEquals(TipoPregunta.Opciones(listOf("short", "long")), paso.pregunta.tipo)
    }

    @Test
    fun laRespuestaDescartaAlLadoContrarioYNoALaEspecieSinDato() {
        val r = interrogar(data, Stage.PRF, reglas, mapOf("gut_length" to "long", MIOMEROS_TOTALES to 25.0))
        assertEquals(setOf("larga", "sindato"), r.identificacion.tied.map { it.species.id }.toSet())
        // Con mas evidencia, la que tiene el dato va primero.
        assertEquals("larga", r.identificacion.tied.first().species.id)
    }
}
