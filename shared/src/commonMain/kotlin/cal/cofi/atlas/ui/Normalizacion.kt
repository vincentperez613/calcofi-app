package cal.cofi.atlas.ui

/**
 * Normaliza texto para buscar sin distinguir mayusculas ni acentos:
 * descompone con NFD y descarta las marcas diacriticas resultantes, despues
 * pasa a minusculas. "Anon" y "Añón" deben encontrarse igual.
 *
 * expect/actual porque NFD no esta en el stdlib comun de Kotlin — Android y
 * Desktop son ambos JVM y comparten la misma implementacion via
 * java.text.Normalizer, pero cada source set la declara por separado (mismo
 * patron que AlmacenamientoPlataforma en Almacen.kt).
 */
expect fun normalizarBusqueda(texto: String): String
