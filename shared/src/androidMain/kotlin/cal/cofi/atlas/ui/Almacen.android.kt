package cal.cofi.atlas.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object AlmacenamientoPlataforma {
    private lateinit var appContext: Context

    /** Llamar una sola vez desde MainActivity.onCreate, antes de montar la UI. */
    fun inicializar(context: Context) {
        appContext = context.applicationContext
    }

    private val archivoSesiones: File
        get() = File(appContext.filesDir, "sesiones.json")

    actual fun leerTexto(): String? {
        val archivo = archivoSesiones
        return if (archivo.exists()) archivo.readText(Charsets.UTF_8) else null
    }

    actual fun escribirTexto(contenido: String) {
        archivoSesiones.writeText(contenido, Charsets.UTF_8)
    }

    actual fun compartirCsv(contenido: String, nombreArchivo: String): String {
        // csv_exports/ es la unica carpeta que expone file_paths.xml al
        // FileProvider — nunca el resto del cache ni filesDir con el JSON.
        val directorio = File(appContext.cacheDir, "csv_exports").apply { mkdirs() }
        val archivo = File(directorio, nombreArchivo)
        archivo.writeText(contenido, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", archivo)
        val intentEnviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val selector = Intent.createChooser(intentEnviar, "Compartir CSV").apply {
            // appContext no es una Activity: hace falta este flag para
            // poder lanzar el chooser desde un contexto de aplicacion.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(selector)
        return "Se abrió el menú para compartir el CSV."
    }

    actual fun ahoraIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
}
