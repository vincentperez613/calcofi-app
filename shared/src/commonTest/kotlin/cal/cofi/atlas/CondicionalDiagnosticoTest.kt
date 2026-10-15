package cal.cofi.atlas

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cetengraulis mysticetus declara hindgut_pigment: absent, conditional:
 * "before 18 mm" — no tiene pigmento dorsal en el intestino antes de 18mm,
 * y el atlas no dice que pasa despues. Estos tests verifican que evaluate()
 * (via rank()) solo usa ese valor cuando la talla realmente cae dentro de
 * lo que el atlas describe, nunca fuera de ese rango ni sin talla.
 *
 * Con una especie sintetica que copia esa ficha: en el bundle del atlas
 * completo Cetengraulis todavia no esta verificada (tiene un renglon sin
 * firmar en la hoja de revision), y la regla del condicional tiene que
 * seguir probada aunque ninguna especie cargada la use.
 */
class CondicionalDiagnosticoTest {

    private val cetengraulis = especieSintetica(
        "cetengraulis",
        diagnostic = mapOf("hindgut_pigment" to valor("absent", conditional = "before 18 mm")),
    )

    private fun candidato(tallaMm: Double?): Candidate {
        val engine = IdentificationEngine(atlasSintetico(cetengraulis))
        val obs = listOf(Observation("hindgut_pigment", categorical = "present"))
        return engine.rank(Stage.POF, obs, null, tallaMm).single()
    }

    @Test
    fun antesDelUmbralElCondicionalAplicaYContradice() {
        assertTrue(
            "hindgut_pigment" in candidato(tallaMm = 15.0).contradicting,
            "a 15mm (< 18mm) el condicional aplica: pigmento presente deberia contradecir 'absent'",
        )
    }

    @Test
    fun despuesDelUmbralElAtlasNoDiceYNoContradice() {
        val c = candidato(tallaMm = 22.0)
        assertTrue(
            "hindgut_pigment" !in c.contradicting,
            "a 22mm (>= 18mm) el atlas no describe el caracter: no deberia contradecir",
        )
        assertTrue("hindgut_pigment" !in c.supporting)
    }

    @Test
    fun sinTallaEsConservadorYNoContradice() {
        val c = candidato(tallaMm = null)
        assertTrue(
            "hindgut_pigment" !in c.contradicting,
            "sin talla no se puede saber si el condicional aplica: no deberia contradecir",
        )
        assertTrue("hindgut_pigment" !in c.supporting)
    }
}
