package cal.cofi.atlas.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object AlmacenamientoPlataforma {
    // Home del usuario, no "junto al ejecutable": donde corre desde el IDE
    // o desde un jar empaquetado, la ruta del ejecutable no es confiable,
    // y el home siempre existe y es escribible.
    private val directorio: File by lazy {
        File(System.getProperty("user.home"), ".calcofilarvas").apply { mkdirs() }
    }

    private val archivoSesiones: File
        get() = File(directorio, "sesiones.json")

    actual fun leerTexto(): String? {
        val archivo = archivoSesiones
        return if (archivo.exists()) archivo.readText(Charsets.UTF_8) else null
    }

    actual fun escribirTexto(contenido: String) {
        archivoSesiones.writeText(contenido, Charsets.UTF_8)
    }

    actual fun compartirCsv(contenido: String, nombreArchivo: String): String {
        // FileDialog.SAVE es el dialogo de guardar nativo del sistema
        // operativo; bloquea el hilo llamador hasta que se cierra, que es
        // el comportamiento esperado para un dialogo modal como este.
        val dialogo = FileDialog(null as Frame?, "Save CSV", FileDialog.SAVE)
        dialogo.file = nombreArchivo
        dialogo.isVisible = true

        val directorioElegido = dialogo.directory
        val archivoElegido = dialogo.file
        if (directorioElegido == null || archivoElegido == null) {
            return "Export canceled."
        }

        val destino = File(directorioElegido, archivoElegido)
        destino.writeText(contenido, Charsets.UTF_8)
        return "Saved to: ${destino.absolutePath}"
    }

    actual fun ahoraIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
}
