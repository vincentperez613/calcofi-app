package cal.cofi.atlas

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

private suspend fun cargarAtlasReal(): AtlasData {
    val estado = AtlasRepository().cargar()
    return assertIs<EstadoCarga.Listo>(estado, "esperaba EstadoCarga.Listo, fue $estado").data
}

/**
 * Una misma clave (ya canonica: "Myomeres: Total" = myomeres_total) puede
 * venir en meristics y en diagnostic a la vez — Serranus sp. trae los
 * miomeros en la tabla y en el texto. rangoDe() resuelve con prioridad
 * diagnostic > meristics para los miomeros y meristics > diagnostic para lo
 * demas; mientras los dos rangos coincidan da igual cual gane. Si algun dia
 * no coinciden, el resultado depende de esa prioridad sin que nadie lo haya
 * decidido: este test lo detecta en vez de dejar que cambie en silencio.
 */
class SolapamientoDeCaracteresTest {

    @Test
    fun unaClaveEnDosEspaciosTieneElMismoRangoEnAmbos() = runTest {
        val data = cargarAtlasReal()
        val conflictos = mutableListOf<String>()

        for (sp in data.species) {
            val enMeristics = sp.meristics.entries.groupBy({ claveCanonica(it.key) }, { it.value })
            val enDiagnostic = sp.diagnostic.entries
                .mapNotNull { (k, d) -> d.range?.let { claveCanonica(k) to it } }
                .groupBy({ it.first }, { it.second })

            for (clave in enMeristics.keys intersect enDiagnostic.keys) {
                val rangos = (enMeristics.getValue(clave) + enDiagnostic.getValue(clave)).toSet()
                if (rangos.size > 1) conflictos += "${sp.id}: '$clave' con rangos distintos $rangos"
            }
        }

        assertTrue(
            conflictos.isEmpty(),
            "estas claves aparecen en meristics y diagnostic con rangos distintos, y el " +
                "resultado depende de cual gane en rangoDe():\n" + conflictos.joinToString("\n"),
        )
    }
}
