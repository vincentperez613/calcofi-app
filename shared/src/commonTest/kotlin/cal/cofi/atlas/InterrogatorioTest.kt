package cal.cofi.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tres especies que solo se separan en orden: la forma las separa a las
 * tres; si la forma no se ve, los miomeros sacan a gama, y entre alfa y
 * beta (mismos conteos) solo decide el pigmento. Asi cada fase tiene algo
 * que preguntar.
 */
private const val FORMA = "forma_cuerpo_prueba"

private fun tresEspecies(conForma: Boolean = true): AtlasData {
    fun forma(v: String) = if (conForma) mapOf(FORMA to valor(v)) else emptyMap()
    val conteos = { anal: Pair<Double, Double> ->
        mapOf(
            "Anal spines" to (0.0 to 0.0), "Anal rays" to anal,
            "Dorsal spines" to (0.0 to 0.0), "Dorsal rays" to (8.0 to 9.0),
        )
    }
    return atlasSintetico(
        especieSintetica(
            "alfa", meristics = conteos(10.0 to 12.0),
            diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 32.0), "hindgut_pigment" to valor("present")) +
                forma("alargada"),
        ),
        especieSintetica(
            "beta", meristics = conteos(10.0 to 12.0),
            diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 32.0), "hindgut_pigment" to valor("absent")) +
                forma("media"),
        ),
        especieSintetica(
            "gama", meristics = conteos(20.0 to 22.0),
            diagnostic = mapOf(MIOMEROS_TOTALES to rango(40.0, 42.0), "hindgut_pigment" to valor("present")) +
                forma("alta"),
        ),
    )
}

private val CATALOGO_FORMA = listOf("alargada", "media", "alta")

private fun reglas(catalogo: List<String> = CATALOGO_FORMA) = ReglasInterrogatorio(
    preguntables = PREGUNTABLES_PRUEBA + FORMA,
    opcionesConocidas = mapOf(FORMA to catalogo),
    clases = CHARACTER_CLASS + (FORMA to "shape"),
)

/** Un ejemplar de beta: el de "mismos conteos que alfa, sin pigmento". */
private val EJEMPLAR_BETA: Map<String, Any> = mapOf(
    MIOMEROS_TOTALES to 31.0,
    "Anal spines" to 0.0, "Anal rays" to 11.0,
    "Dorsal spines" to 0.0, "Dorsal rays" to 8.0,
    "hindgut_pigment" to "absent",
)

class FasesTest {

    @Test
    fun lasFasesSalenEnOrdenYTerminaEnPigmento() {
        // Forma "no se": la fase Forma se pregunta pero no decide, y el
        // resto de las fases tiene que hacer el trabajo, en orden.
        val r = interrogar(tresEspecies(), Stage.POF, reglas(), EJEMPLAR_BETA)
        val fases = r.preguntas.map { it.fase }

        assertEquals(Fase.FORMA, fases.first())
        assertEquals(fases.sortedBy { it.ordinal }, fases, "las fases nunca deben retroceder: $fases")
        assertEquals(
            listOf(Fase.FORMA, Fase.MIOMEROS, Fase.ALETAS, Fase.PIGMENTACION),
            fases.distinct(),
        )
        assertEquals(MIOMEROS_TOTALES, r.preguntas.first { it.fase == Fase.MIOMEROS }.clave)
        assertEquals("Beta sinteticus", r.identificacion.label)
    }

    @Test
    fun unaFaseSinPreguntasSeSaltaSola() {
        // Sin ningun caracter de forma (como el bundle de hoy) la primera
        // pregunta ya es de miomeros: no hay una pantalla de Forma vacia.
        val r = interrogar(tresEspecies(conForma = false), Stage.POF, reglas(), EJEMPLAR_BETA)
        assertEquals(Fase.MIOMEROS, r.preguntas.first().fase)
        assertTrue(r.preguntas.none { it.fase == Fase.FORMA })
    }

    @Test
    fun losRadiosSoloSePreguntanSiLaEtapaLosTiene() {
        // En preflexion no hay aletas que contar: de Miomeros salta a
        // Pigmentacion, sin pedir anal ni dorsal (el filtro grueso de antes
        // los pedia siempre).
        val r = interrogar(tresEspecies(conForma = false), Stage.PRF, reglas(), EJEMPLAR_BETA)
        assertTrue(r.preguntas.none { it.fase == Fase.ALETAS }, "en PrF no deberia haber preguntas de aletas")
        assertEquals(listOf(Fase.MIOMEROS, Fase.PIGMENTACION), r.preguntas.map { it.fase }.distinct())
    }

    @Test
    fun lasAnclasSePidenAunqueLaEspecieYaEsteResuelta() {
        // La forma deja solo a beta; los conteos con tabla por familia se
        // piden igual (son el chequeo a nivel familia), y nada mas.
        val r = interrogar(tresEspecies(), Stage.POF, reglas(), EJEMPLAR_BETA + (FORMA to "media"))
        assertEquals(
            listOf(FORMA, MIOMEROS_TOTALES, "Anal rays", "Dorsal rays"),
            r.preguntas.map { it.clave },
        )
        assertTrue(r.preguntas.drop(1).all { it.ancla != null })
    }

    @Test
    fun atrasRestauraLaPreguntaAnteriorAunqueSeaDeOtraFase() {
        // "Atras" en la UI restaura el estado guardado antes de la ultima
        // respuesta. Con ese estado el motor tiene que volver a ofrecer
        // exactamente la misma pregunta, tambien al cruzar de fase.
        val data = tresEspecies()
        val engine = IdentificationEngine(data)
        val r = interrogar(data, Stage.POF, reglas(), EJEMPLAR_BETA)

        for ((i, estadoPrevio) in r.estados.withIndex()) {
            val paso = assertIs<PasoInterrogatorio.Preguntar>(
                siguientePaso(engine, Stage.POF, estadoPrevio, null, reglas()),
            )
            assertEquals(r.preguntas[i], paso.pregunta, "al volver al estado $i deberia repetirse la pregunta $i")
        }
        val cruces = r.preguntas.zipWithNext().count { (a, b) -> a.fase != b.fase }
        assertTrue(cruces >= 3, "el recorrido deberia cruzar al menos tres fases")
    }
}

class PreguntaDeOpcionesTest {

    @Test
    fun formaConTresOpcionesEnOrdenDeCatalogo() {
        val engine = IdentificationEngine(tresEspecies())
        val paso = assertIs<PasoInterrogatorio.Preguntar>(
            siguientePaso(engine, Stage.POF, EstadoInterrogatorio(), null, reglas()),
        )
        assertEquals(Fase.FORMA, paso.pregunta.fase)
        val tipo = assertIs<TipoPregunta.Opciones>(paso.pregunta.tipo)
        assertEquals(CATALOGO_FORMA, tipo.valores)
    }

    @Test
    fun elegirUnaOpcionDejaSoloALaEspecieQueLaTiene() {
        val engine = IdentificationEngine(tresEspecies())
        val ranked = engine.rank(Stage.POF, listOf(Observation(FORMA, categorical = "media")))
        assertEquals(listOf("beta"), engine.plausibles(ranked).map { it.species.id })
    }

    @Test
    fun unValorSinRedaccionLlanaNoSePregunta() {
        // Sin texto llano para "alta" no hay como ofrecerle esa opcion al
        // tecnico sin jerga: el caracter no se pregunta y Forma se salta.
        val engine = IdentificationEngine(tresEspecies())
        val paso = assertIs<PasoInterrogatorio.Preguntar>(
            siguientePaso(engine, Stage.POF, EstadoInterrogatorio(), null, reglas(listOf("alargada", "media"))),
        )
        assertEquals(Fase.MIOMEROS, paso.pregunta.fase)
    }
}

class MiomerosTest {

    private val data = atlasSintetico(
        especieSintetica("texto", diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 32.0), "myomeres_preanal" to rango(10.0, 11.0))),
        especieSintetica("tabla", meristics = mapOf("Myomeres: Total" to (40.0 to 42.0)),
            diagnostic = mapOf("myomeres_preanal" to rango(20.0, 21.0))),
        especieSintetica("vertebras", meristics = mapOf("Vertebrae: Total" to (50.0 to 52.0)),
            diagnostic = mapOf("myomeres_preanal" to rango(30.0, 31.0))),
        especieSintetica("ambas", meristics = mapOf("Vertebrae: Total" to (60.0 to 62.0)),
            diagnostic = mapOf(MIOMEROS_TOTALES to rango(44.0, 45.0))),
    )

    @Test
    fun elConteoDeMiomerosSePideUnaSolaVez() {
        // "no se" a todo: el pool no se achica, que es cuando el motor tiene
        // mas tentacion de volver a pedir "Myomeres: Total" por su cuenta.
        val r = interrogar(data, Stage.POF, ReglasInterrogatorio(PREGUNTABLES_PRUEBA + "Myomeres: Total"), emptyMap())
        val totales = r.preguntas.filter { claveCanonica(it.clave) == MIOMEROS_TOTALES }
        assertEquals(1, totales.size, "preguntas: ${r.preguntas.map { it.clave }}")
        assertTrue(r.preguntas.none { it.clave == "Myomeres: Total" })
        // Ninguna clave se pide dos veces, en ninguna fase.
        val todas = r.preguntas.flatMap { it.claves }
        assertEquals(todas.toSet().size, todas.size, "claves repetidas: $todas")
    }

    @Test
    fun miomerosDeLaTablaCuentanIgualQueLosDelTexto() {
        val engine = IdentificationEngine(data)
        val candidato = engine.rank(Stage.POF, listOf(Observation(MIOMEROS_TOTALES, value = 41.0)))
            .first { it.species.id == "tabla" }
        assertEquals(listOf(MIOMEROS_TOTALES), candidato.supporting)
    }

    @Test
    fun sustitutoPorVertebrasCuandoNoHayMiomeros() {
        val soloVertebras = data.species.first { it.id == "vertebras" }
        val ambas = data.species.first { it.id == "ambas" }

        // Si la ficha trae miomeros, se usan esos aunque tenga vertebras.
        assertEquals(Range(44.0, 45.0), rangoMiomerosTotales(ambas))

        val engine = IdentificationEngine(data)
        fun candidato(conteo: Double) = engine.rank(Stage.POF, listOf(Observation(MIOMEROS_TOTALES, value = conteo)))
            .first { it.species.id == "vertebras" }

        if (VERTEBRAS_SUSTITUYEN_MIOMEROS) {
            assertEquals(Range(50.0, 52.0), rangoMiomerosTotales(soloVertebras))
            assertEquals(listOf(MIOMEROS_TOTALES), candidato(51.0).supporting)
            assertEquals(listOf(MIOMEROS_TOTALES), candidato(70.0).contradicting)
        } else {
            // Apagado: sin miomeros en la ficha, el conteo no dice nada de
            // la especie — ni a favor ni en contra.
            assertNull(rangoMiomerosTotales(soloVertebras))
            assertEquals(0, candidato(51.0).evaluated)
        }
    }
}

class AletasTest {

    private val espinosa = especieSintetica(
        "espinosa", meristics = mapOf("Pelvic spines" to (1.0 to 1.0), "Pelvic" to (5.0 to 5.0)),
    )
    // Como Engraulidae: la ficha da los radios pelvicos y no menciona espinas.
    private val blanda = especieSintetica("blanda", meristics = mapOf("Pelvic" to (9.0 to 10.0)))
    private val data = atlasSintetico(espinosa, blanda)

    @Test
    fun laPelvicaPideEspinasYRadiosEnUnaSolaPregunta() {
        val r = interrogar(data, Stage.POF, ReglasInterrogatorio(PREGUNTABLES_PRUEBA), emptyMap())
        val pelvica = r.preguntas.single { aletaDe(it.clave)?.id == "pelvica" }
        val tipo = assertIs<TipoPregunta.ConteoAleta>(pelvica.tipo)
        assertTrue(tipo.pedirEspinas)
        assertEquals(listOf("Pelvic spines", "Pelvic"), pelvica.clavesPedidas)
    }

    @Test
    fun espinasContraEspinasYRadiosContraRadios() {
        // El atlas escribe "I,5": Pelvic spines = 1, Pelvic = 5 (radios).
        // El tecnico cuenta 1 espina y 5 radios; cada numero se compara con
        // su parte, nunca el total (6) contra los radios.
        val engine = IdentificationEngine(data)
        val obs = listOf(Observation("Pelvic spines", value = 1.0), Observation("Pelvic", value = 5.0))
        val ranked = engine.rank(Stage.POF, obs)

        val e = ranked.first { it.species.id == "espinosa" }
        assertEquals(setOf("Pelvic spines", "Pelvic"), e.supporting.toSet())
        assertTrue(e.contradicting.isEmpty())

        // La que no reporta espinas no se penaliza por ellas: solo cuentan
        // sus radios, que no le quedan.
        val b = ranked.first { it.species.id == "blanda" }
        assertEquals(listOf("Pelvic"), b.contradicting)
        assertTrue("Pelvic spines" !in b.supporting + b.contradicting)
    }

    @Test
    fun laTablaPorFamiliaRecibeLaSuma() {
        val anal = Pregunta(Fase.ALETAS, "Anal rays", TipoPregunta.ConteoAleta(ALETAS.first { it.id == "anal" }, true))
        assertEquals(
            25.0,
            valorParaTabla(anal, listOf(Observation("Anal spines", value = 3.0), Observation("Anal rays", value = 22.0))),
        )
        // Sin una de las dos partes no hay suma: esa aleta no filtra familias.
        assertNull(valorParaTabla(anal, listOf(Observation("Anal spines"), Observation("Anal rays", value = 22.0))))
    }
}

class FamiliasTest {

    private val coarse = CoarseFilter(
        mapOf(
            "vertebrae_total" to mapOf(
                "Testidae" to Range(30.0, 32.0),
                "Otridae" to Range(40.0, 42.0),
            ),
        ),
    )
    // "Mal Escritidae" es como "Scorpa Enidae" en el bundle real: un nombre
    // que la tabla nunca va a listar.
    private val cargadas = setOf("Testidae", "Mal Escritidae")

    @Test
    fun unaFamiliaQueLaTablaNoMencionaNoSeDescarta() {
        val e = efectoEnFamilias(coarse, "vertebrae_total", 31.0, null, cargadas)!!
        assertEquals(setOf("Testidae", "Mal Escritidae"), e.familias)
        assertEquals(setOf("Testidae"), e.deLaTabla)
        assertEquals(setOf("Mal Escritidae"), e.noListadasConservadas)
    }

    @Test
    fun avisos() {
        assertTrue(efectoEnFamilias(coarse, "vertebrae_total", 60.0, null, cargadas)!!.sinFamiliaCompatible)
        // Otridae es compatible pero no tiene especies cargadas.
        assertTrue(efectoEnFamilias(coarse, "vertebrae_total", 41.0, null, cargadas)!!.sinEspeciesCargadas)
        // Sin tabla en el bundle, el conteo no toca las familias.
        assertNull(efectoEnFamilias(coarse, "anal_rays_total", 20.0, null, cargadas))
    }

    @Test
    fun elAnclaDeMiomerosFiltraFamiliasYSeUsaParaLaEspecie() {
        val data = atlasSintetico(
            especieSintetica("t", "Testidae", diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 32.0))),
            especieSintetica("o", "Otridae", diagnostic = mapOf(MIOMEROS_TOTALES to rango(40.0, 42.0))),
            especieSintetica("m", "Mal Escritidae", diagnostic = mapOf(MIOMEROS_TOTALES to rango(30.0, 31.0))),
            tablas = coarse.tables,
        )
        val r = interrogar(data, Stage.PRF, ReglasInterrogatorio(PREGUNTABLES_PRUEBA), mapOf(MIOMEROS_TOTALES to 31.0))
        assertEquals(setOf("Testidae", "Mal Escritidae"), r.final.familias)
        // Un solo conteo, usado en los dos niveles: no se vuelve a pedir.
        assertEquals(1, r.preguntas.count { it.clave == MIOMEROS_TOTALES })
        val supporting = r.identificacion.candidates.filter { MIOMEROS_TOTALES in it.supporting }.map { it.species.id }
        assertEquals(setOf("t", "m"), supporting.toSet())
    }
}
