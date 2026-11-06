package cal.cofi.atlas.ui

import cal.cofi.atlas.AtlasRepository
import cal.cofi.atlas.EstadoCarga
import cal.cofi.atlas.Species
import cal.cofi.atlas.Stage
import cal.cofi.atlas.interrogar
import cal.cofi.atlas.rangoDe
import kotlinx.coroutines.test.runTest
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Cada especie del bundle hace de ejemplar y recorre el interrogatorio real
 * (REGLAS_APP), igual que en PantallaPregunta: contesta con el punto medio de
 * su ficha, "no se" a lo que su ficha no trae, y una talla dentro de su
 * ventana para la etapa. Reporta cuantos matches quedan y exige que la
 * especie correcta este entre ellos.
 *
 * Es el caso facil, sin errores de conteo: aca perder a la especie correcta
 * es un bug (un dato del bundle que contradice a su propia ficha, una tabla
 * de familia que la descarta), no mala suerte.
 */
class SimulacionAtlasTest {

    private fun ejemplarDe(sp: Species): Map<String, Any> = buildMap {
        for (clave in REGLAS_APP.preguntables) {
            val d = sp.diagnostic[clave]
            if (d?.value != null) {
                if (d.conditional == null) put(clave, d.value)
                continue
            }
            val r = rangoDe(sp, clave) ?: continue
            put(clave, round((r.min + r.max) / 2))
        }
    }

    private fun tallaDentro(sp: Species, stage: Stage): Double? {
        val v = sp.stageSizes[stage.code] ?: return null
        val lo = v.min
        val hi = v.max
        return when {
            lo != null && hi != null -> (lo + hi) / 2
            hi != null -> hi * 0.8
            else -> lo!! * 1.2
        }
    }

    @Test
    fun laEspecieCorrectaQuedaEntreLosMatches() = runTest {
        val data = assertIs<EstadoCarga.Listo>(AtlasRepository().cargar()).data
        for (stage in listOf(Stage.PRF, Stage.F, Stage.POF)) {
            val matches = mutableListOf<Int>()
            val perdidas = mutableListOf<String>()
            var sinEvidencia = 0
            for (sp in data.species) {
                val talla = tallaDentro(sp, stage)
                val r = interrogar(data, stage, REGLAS_APP, ejemplarDe(sp), talla ?: 5.0)
                // Ni ventana de talla ni nada que se pueda preguntar en esta
                // etapa (macruridos en preflexion: solo traen radios de
                // aletas): no hay con que identificarla, y no es un error.
                if (r.identificacion.candidates.single { it.species.id == sp.id }.evaluated == 0) {
                    sinEvidencia++
                    continue
                }
                matches += r.identificacion.tied.size
                if (r.identificacion.tied.none { it.species.id == sp.id }) perdidas += sp.species
            }
            val orden = matches.sorted()
            println(
                "$stage: ${orden.size} ejemplares ($sinEvidencia sin evidencia en esta etapa), " +
                    "matches mediana=${orden[orden.size / 2]} p90=${orden[orden.size * 9 / 10]}, " +
                    "un solo match=${orden.count { it == 1 } * 100 / orden.size}%, " +
                    "<=5=${orden.count { it <= 5 } * 100 / orden.size}%, perdidas=${perdidas.size}",
            )
            assertTrue(perdidas.isEmpty(), "$stage: la especie correcta no quedo entre los matches en $perdidas")
        }
    }
}
