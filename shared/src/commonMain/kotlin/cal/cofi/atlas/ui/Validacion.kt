package cal.cofi.atlas.ui

import kotlin.math.floor

/**
 * Resultado de validar un texto numerico, con el motivo cuando no es valido
 * — para poder explicarle al tecnico que esta mal, no solo que algo esta
 * mal. [Valido] es el unico caso con un [Double] real; todos los demas son
 * distintas formas de "todavia no".
 */
sealed interface ResultadoValidacion {
    data class Valido(val valor: Double) : ResultadoValidacion
    data object Vacio : ResultadoValidacion
    data object NoNumerico : ResultadoValidacion
    data class FueraDeRango(val min: Double, val max: Double) : ResultadoValidacion
    data object DebeSerEntero : ResultadoValidacion
}

/**
 * Solo digitos, un separador decimal opcional (punto o coma) y mas
 * digitos. Sin signo, sin exponente, sin sufijos, sin hexadecimal.
 */
private val PATRON_NUMERICO_ESTRICTO = Regex("""^\d+([.,]\d+)?$""")

/**
 * Parseo estricto: nunca toDoubleOrNull() directo sobre lo que tecleo el
 * tecnico. toDoubleOrNull() de Kotlin acepta cosas que aca son basura:
 * "1e5" (notacion cientifica), "Infinity"/"NaN", "5f"/"5d" (sufijos de
 * Java), "0x1p3" (hexadecimal flotante), "+5" (signo explicito). El
 * espacio alrededor SI se tolera, pero via un trim() deliberado y
 * controlado antes del regex, no porque toDoubleOrNull() lo permita.
 *
 * El regex corre sobre el texto ya trimmeado; solo si matchea se parsea
 * (y en ese punto toDoubleOrNull() ya es seguro, porque el regex garantiza
 * que no queda nada raro adentro).
 */
private fun parsearEstricto(textoTrimmeado: String): Double? {
    if (!PATRON_NUMERICO_ESTRICTO.matches(textoTrimmeado)) return null
    return textoTrimmeado.replace(',', '.').toDoubleOrNull()
}

private fun validarBase(texto: String): ResultadoValidacion {
    val t = texto.trim()
    if (t.isEmpty()) return ResultadoValidacion.Vacio
    val valor = parsearEstricto(t) ?: return ResultadoValidacion.NoNumerico
    return ResultadoValidacion.Valido(valor)
}

/** Talla del ejemplar: mayor que 0, menor que 200mm — una larva de 5000mm es un error de tecleo. */
fun validarTalla(texto: String): ResultadoValidacion {
    val base = validarBase(texto)
    if (base !is ResultadoValidacion.Valido) return base
    if (base.valor <= 0.0 || base.valor >= 200.0) return ResultadoValidacion.FueraDeRango(0.0, 200.0)
    return base
}

/** Conteos meristicos (vertebras, radios, miomeros): enteros entre 0 y 700. */
fun validarConteo(texto: String): ResultadoValidacion {
    val base = validarBase(texto)
    if (base !is ResultadoValidacion.Valido) return base
    if (base.valor != floor(base.valor)) return ResultadoValidacion.DebeSerEntero
    if (base.valor < 0.0 || base.valor > 700.0) return ResultadoValidacion.FueraDeRango(0.0, 700.0)
    return base
}

private fun formatearNumero(valor: Double): String =
    if (valor == floor(valor)) valor.toLong().toString() else valor.toString()

/**
 * Mensaje para mostrar debajo del campo. [Vacio] se deja
 * en null a proposito: un campo que el tecnico todavia no toco no es un
 * error, es solo "todavia no" — regañarlo por eso es ruido. La falta se
 * nombra aparte, al intentar continuar, no campo por campo mientras se
 * escribe.
 */
fun explicarTalla(resultado: ResultadoValidacion): String? = when (resultado) {
    is ResultadoValidacion.Valido, ResultadoValidacion.Vacio -> null
    ResultadoValidacion.NoNumerico -> "Numbers only."
    is ResultadoValidacion.FueraDeRango ->
        "Body length must be between ${formatearNumero(resultado.min)} and ${formatearNumero(resultado.max)} mm."
    ResultadoValidacion.DebeSerEntero -> "Numbers only." // no aplica a talla, defensivo
}

fun explicarConteo(resultado: ResultadoValidacion): String? = when (resultado) {
    is ResultadoValidacion.Valido, ResultadoValidacion.Vacio -> null
    ResultadoValidacion.NoNumerico -> "Numbers only."
    is ResultadoValidacion.FueraDeRango ->
        "Count must be between ${formatearNumero(resultado.min)} and ${formatearNumero(resultado.max)}."
    ResultadoValidacion.DebeSerEntero -> "Count must be a whole number."
}
