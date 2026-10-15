package cal.cofi.atlas.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidacionTest {

    // ---- Bug 2: entradas que toDoubleOrNull() acepta pero aca son invalidas ----

    @Test
    fun rechazaNotacionCientifica() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("1e5"))
    }

    @Test
    fun rechazaInfinityYNaN() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("Infinity"))
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("NaN"))
    }

    @Test
    fun rechazaSufijosDeJava() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("5f"))
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("5d"))
    }

    @Test
    fun rechazaHexadecimalFlotante() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("0x1p3"))
    }

    @Test
    fun rechazaSignoExplicito() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("+5"))
    }

    @Test
    fun aceptaEspaciosAlrededorPorqueSeTrimmeaDeliberadamente() {
        // " 5 " trimmea a "5", que si es valido: el trim es una decision
        // deliberada y controlada sobre el texto ya trimmeado (como pide
        // la consigna), no una laxitud heredada de toDoubleOrNull.
        assertEquals(ResultadoValidacion.Valido(5.0), validarConteo(" 5 "))
    }

    @Test
    fun rechazaEspacioInterno() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("1 2"))
    }

    @Test
    fun rechazaMultiplesSeparadoresDecimales() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("1.2.3"))
    }

    @Test
    fun rechazaTextoNoNumerico() {
        assertEquals(ResultadoValidacion.NoNumerico, validarConteo("abc"))
    }

    // ---- vacio: no es un error, es "todavia no" ----

    @Test
    fun vacioYSoloEspaciosSonVacio() {
        assertEquals(ResultadoValidacion.Vacio, validarConteo(""))
        assertEquals(ResultadoValidacion.Vacio, validarConteo("   "))
    }

    // ---- validarTalla: (0, 200) exclusivo en ambos extremos ----

    @Test
    fun tallaAceptaValoresDentroDelRango() {
        assertEquals(ResultadoValidacion.Valido(0.1), validarTalla("0.1"))
        assertEquals(ResultadoValidacion.Valido(199.9), validarTalla("199.9"))
        assertEquals(ResultadoValidacion.Valido(15.5), validarTalla("15,5")) // coma decimal
    }

    @Test
    fun tallaRechazaCeroYElLimiteSuperior() {
        assertEquals(ResultadoValidacion.FueraDeRango(0.0, 200.0), validarTalla("0"))
        assertEquals(ResultadoValidacion.FueraDeRango(0.0, 200.0), validarTalla("200"))
    }

    @Test
    fun tallaRechazaUnErrorDeTecleoGrosero() {
        assertEquals(ResultadoValidacion.FueraDeRango(0.0, 200.0), validarTalla("5000"))
    }

    // ---- validarConteo: entero, [0, 700] inclusive ----

    @Test
    fun conteoAceptaLosLimitesInclusive() {
        assertEquals(ResultadoValidacion.Valido(0.0), validarConteo("0"))
        assertEquals(ResultadoValidacion.Valido(700.0), validarConteo("700"))
    }

    @Test
    fun conteoRechazaFueraDelRango() {
        assertEquals(ResultadoValidacion.FueraDeRango(0.0, 700.0), validarConteo("701"))
    }

    @Test
    fun conteoRechazaDecimales() {
        assertEquals(ResultadoValidacion.DebeSerEntero, validarConteo("12.5"))
    }

    // ---- mensajes en lenguaje llano ----

    @Test
    fun explicarNoMuestraNadaParaVacioOValido() {
        assertEquals(null, explicarTalla(ResultadoValidacion.Vacio))
        assertEquals(null, explicarTalla(ResultadoValidacion.Valido(10.0)))
    }

    @Test
    fun explicarUsaLenguajeLlano() {
        assertEquals("Solo números.", explicarConteo(ResultadoValidacion.NoNumerico))
        assertEquals(
            "La talla debe estar entre 0 y 200 mm.",
            explicarTalla(ResultadoValidacion.FueraDeRango(0.0, 200.0)),
        )
        assertEquals(
            "El conteo debe ser un número entero, sin decimales.",
            explicarConteo(ResultadoValidacion.DebeSerEntero),
        )
    }
}
