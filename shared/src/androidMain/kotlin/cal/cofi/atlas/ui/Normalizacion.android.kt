package cal.cofi.atlas.ui

import java.text.Normalizer

private val MARCAS_DIACRITICAS = Regex("\\p{Mn}+")

actual fun normalizarBusqueda(texto: String): String {
    val descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFD)
    return descompuesto.replace(MARCAS_DIACRITICAS, "").lowercase()
}
