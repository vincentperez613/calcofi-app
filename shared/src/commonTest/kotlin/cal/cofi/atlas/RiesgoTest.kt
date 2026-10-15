package cal.cofi.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemporadaTest {

    // Verano, con pico en julio (como Anchoa compressa, sin el pico).
    private val verano = listOf(6, 7, 8)
    private val picoJulio = listOf(7)

    @Test
    fun enPico() {
        assertEquals(Temporada.PICO, evaluarTemporada(7, verano, picoJulio))
    }

    @Test
    fun enTemporada() {
        assertEquals(Temporada.EN_TEMPORADA, evaluarTemporada(6, verano, picoJulio))
    }

    @Test
    fun mesContiguoAntesYDespues() {
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(5, verano, picoJulio))
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(9, verano, picoJulio))
    }

    @Test
    fun fueraDeTemporada() {
        assertEquals(Temporada.FUERA, evaluarTemporada(1, verano, picoJulio))
        assertEquals(Temporada.FUERA, evaluarTemporada(10, verano, picoJulio))
    }

    @Test
    fun sinDato() {
        assertEquals(Temporada.SIN_DATO, evaluarTemporada(null, verano, picoJulio), "sin mes de colecta")
        assertEquals(Temporada.SIN_DATO, evaluarTemporada(3, emptyList(), emptyList()), "ficha sin temporada")
    }

    @Test
    fun elAnoDaLaVueltaEntreDiciembreYEnero() {
        // Desova en diciembre: enero es el mes siguiente, no "fuera".
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(1, listOf(12), emptyList()))
        // Desova en enero: diciembre es el mes anterior.
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(12, listOf(1), emptyList()))
        // Como Cetengraulis mysticetus: otoño-invierno con pico en diciembre.
        val cetengraulis = listOf(1, 2, 9, 10, 11, 12)
        assertEquals(Temporada.PICO, evaluarTemporada(12, cetengraulis, listOf(12)))
        assertEquals(Temporada.EN_TEMPORADA, evaluarTemporada(1, cetengraulis, listOf(12)))
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(3, cetengraulis, listOf(12)))
        assertEquals(Temporada.FUERA, evaluarTemporada(6, cetengraulis, listOf(12)))
    }

    @Test
    fun elPicoCuentaAunqueNoEsteEnLosMeses() {
        assertEquals(Temporada.PICO, evaluarTemporada(4, listOf(5, 6), listOf(4)))
        assertEquals(Temporada.CONTIGUO, evaluarTemporada(3, listOf(5, 6), listOf(4)))
    }

    @Test
    fun mesDesplazadoDaLaVuelta() {
        assertEquals(1, mesDesplazado(12, 1))
        assertEquals(12, mesDesplazado(1, -1))
        assertEquals(6, mesDesplazado(6, 0))
    }
}

class NivelDeRiesgoTest {

    /** La regla completa, combinacion por combinacion. */
    @Test
    fun tablaCompleta() {
        val esperado = mapOf(
            (Temporada.PICO to Distribucion.SI) to NivelRiesgo.BAJO,
            (Temporada.PICO to Distribucion.NO) to NivelRiesgo.ALTO,
            (Temporada.PICO to Distribucion.NO_SE) to NivelRiesgo.NO_EVALUABLE,
            (Temporada.EN_TEMPORADA to Distribucion.SI) to NivelRiesgo.BAJO,
            (Temporada.EN_TEMPORADA to Distribucion.NO) to NivelRiesgo.ALTO,
            (Temporada.EN_TEMPORADA to Distribucion.NO_SE) to NivelRiesgo.NO_EVALUABLE,
            (Temporada.CONTIGUO to Distribucion.SI) to NivelRiesgo.MEDIO,
            (Temporada.CONTIGUO to Distribucion.NO) to NivelRiesgo.ALTO,
            (Temporada.CONTIGUO to Distribucion.NO_SE) to NivelRiesgo.MEDIO,
            (Temporada.FUERA to Distribucion.SI) to NivelRiesgo.ALTO,
            (Temporada.FUERA to Distribucion.NO) to NivelRiesgo.ALTO,
            (Temporada.FUERA to Distribucion.NO_SE) to NivelRiesgo.ALTO,
            (Temporada.SIN_DATO to Distribucion.SI) to NivelRiesgo.NO_EVALUABLE,
            (Temporada.SIN_DATO to Distribucion.NO) to NivelRiesgo.ALTO,
            (Temporada.SIN_DATO to Distribucion.NO_SE) to NivelRiesgo.NO_EVALUABLE,
        )
        assertEquals(Temporada.entries.size * Distribucion.entries.size, esperado.size)
        for ((factores, nivel) in esperado) {
            assertEquals(nivel, nivelDeRiesgo(factores.first, factores.second), "para $factores")
        }
    }

    @Test
    fun siempreDiceElPorqueYDeDondeSaleLaTemporada() {
        val lh = LifeHistory(range = "Morro Bay to Bahia Magdalena", spawningSeason = "Summer", spawningMonths = listOf(6, 7, 8))
        val e = evaluarRiesgo(1, lh, Distribucion.NO_SE)
        assertEquals(NivelRiesgo.ALTO, e.nivel)
        assertTrue(e.razones.any { "enero" in it && "fuera" in it }, "razones: ${e.razones}")
        assertTrue(e.razones.any { "junio, julio, agosto" in it }, "razones: ${e.razones}")
    }

    @Test
    fun conUnSoloFactorAFavorExplicaPorQueNoEsBajo() {
        val lh = LifeHistory(range = "Oregon to Baja", spawningMonths = (1..12).toList())
        val e = evaluarRiesgo(3, lh, Distribucion.NO_SE)
        assertEquals(NivelRiesgo.NO_EVALUABLE, e.nivel)
        assertTrue(e.razones.any { "hacen falta las dos" in it }, "razones: ${e.razones}")
    }

    @Test
    fun sinNingunDatoNoEsEvaluable() {
        val e = evaluarRiesgo(null, null, Distribucion.NO_SE)
        assertEquals(NivelRiesgo.NO_EVALUABLE, e.nivel)
        assertEquals(Temporada.SIN_DATO, e.temporada)
    }
}
