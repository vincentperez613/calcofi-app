package cal.cofi.atlas.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Almacen reescribe el archivo completo en cada operacion, y si el archivo
 * no decodifica empieza una lista nueva: una sesion vieja que deja de
 * cargar se BORRA en el siguiente guardado. Estos tests usan las funciones
 * puras de Almacen.kt, nunca el archivo real del dispositivo.
 */
class AlmacenFormatoTest {

    /**
     * Tal como lo escribia la version con filtro grueso y morfometria: sin
     * mes ni zona, sin riesgo, con conteos del filtro grueso y una razon
     * BD/BL con sus mm crudos.
     */
    private val sesionVieja = """
        [{"id":"2026-09-01T10:00:00-1234","fechaHoraIso":"2026-09-01T10:00:00","tallaMm":18.4,"etapa":"POF",
          "observaciones":[
            {"character":"vertebrae_total","valor":45.0},
            {"character":"BD/BL","valor":11.0,"numeradorMm":2.024,"denominadorMm":18.4},
            {"character":"hindgut_pigment","categorico":"present"},
            {"character":"myomeres_preanal"}
          ],
          "resultado":{"label":"Engraulis mordax","nivel":"SPECIES",
            "top3":[{"especie":"Engraulis mordax","scorePorcentaje":100}]}}]
    """.trimIndent()

    @Test
    fun unaSesionDelFormatoViejoSigueCargando() {
        val sesion = decodificarSesiones(sesionVieja).single()
        assertNull(sesion.mesColecta)
        assertNull(sesion.zonaColecta)
        val razon = sesion.observaciones.single { it.character == "BD/BL" }
        assertEquals(2.024, razon.numeradorMm)
        assertEquals(18.4, razon.denominadorMm)
        assertNull(sesion.resultado!!.top3.single().riesgo)
    }

    @Test
    fun alReescribirseNoPierdeLasMedidasCrudas() {
        // Lo que pasa en el dispositivo: se lee, se agrega otra sesion y se
        // reescribe todo. Las medidas en mm de la vieja tienen que seguir ahi.
        val nueva = SesionGuardada("nueva", "2026-10-15T09:00:00", 5.0, "PRF", mesColecta = 1, zonaColecta = "Bahía")
        val reescrito = codificarSesiones(decodificarSesiones(sesionVieja) + nueva)
        val releidas = decodificarSesiones(reescrito)

        val razon = releidas.first { it.id.startsWith("2026-09") }.observaciones.single { it.character == "BD/BL" }
        assertEquals(2.024, razon.numeradorMm)
        assertEquals(18.4, razon.denominadorMm)
        assertEquals(1, releidas.single { it.id == "nueva" }.mesColecta)
    }

    @Test
    fun seExportaConSusColumnasDeMedidaCruda() {
        val csv = construirCsv(decodificarSesiones(sesionVieja))
        val lineas = csv.removePrefix("﻿").trimEnd().split("\r\n")
        val encabezados = lineas[0].split(",")
        val fila = lineas[1].split(",")
        fun columna(nombre: String) = fila[encabezados.indexOf(nombre).also { assertTrue(it >= 0, "falta $nombre") }]

        assertEquals("2.024", columna("BD/BL_numerador_mm"))
        assertEquals("18.4", columna("BD/BL_denominador_mm"))
        assertEquals("11.0", columna("BD/BL"))
        assertEquals("45.0", columna("vertebrae_total"))
        assertEquals("no se", columna("myomeres_preanal"))
        // Las columnas nuevas existen, vacias para una sesion de antes.
        assertEquals("", columna("mes_colecta"))
        assertEquals("", columna("top1_riesgo"))
        // Las de siempre no cambiaron de lugar.
        assertEquals(listOf("id", "fecha_hora", "talla_mm", "etapa"), encabezados.take(4))
        assertEquals("top3_score", encabezados[11])
    }

    @Test
    fun elRiesgoSeExporta() {
        val sesion = SesionGuardada(
            id = "r", fechaHoraIso = "2026-10-15T09:00:00", tallaMm = 18.4, etapa = "POF",
            mesColecta = 1, zonaColecta = "Ensenada, B.C.",
            resultado = ResultadoGuardado(
                "Anchoa compressa", "SPECIES",
                listOf(
                    CandidatoGuardado(
                        "Anchoa compressa", 100, riesgo = "ALTO", temporada = "FUERA", distribucion = "NO_SE",
                        razonesRiesgo = listOf("Colectada en enero: fuera de la temporada de desove (junio, julio, agosto)."),
                    ),
                ),
            ),
        )
        val csv = construirCsv(listOf(codificarSesiones(listOf(sesion)).let(::decodificarSesiones).single()))
        assertTrue("ALTO,FUERA,NO_SE" in csv, csv)
        // La zona lleva coma: tiene que ir entre comillas para no correr columnas.
        assertTrue("\"Ensenada, B.C.\"" in csv, csv)
    }
}
