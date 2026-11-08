package cal.cofi.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import cal.cofi.atlas.AtlasData

/** El PDF que CalCOFI publica del atlas; hace las veces de DOI en la cita. */
const val URL_ATLAS_PDF = "https://calcofi.org/downloads/publications/atlases/CalCOFI_Atlas_33.pdf"

/**
 * La cita tal como la registran las bibliotecas (ISBN 0-935868-82-8), con la
 * liga al PDF al final, despues del ISBN y sin punto, como en APA 7.
 */
const val CITA_ATLAS =
    "Moser, H. G. (editor). 1996. The Early Stages of Fishes in the California Current Region. " +
        "California Cooperative Oceanic Fisheries Investigations (CalCOFI) Atlas No. 33. " +
        "Allen Press, Lawrence, Kansas. 1505 pp. ISBN 0-935868-82-8. $URL_ATLAS_PDF"

private const val URL_CALCOFI = "https://calcofi.org"

/**
 * Seccion About: de donde sale todo lo que muestra la app. Cada conteo,
 * cada texto y cada figura vienen del CalCOFI Atlas 33; aca va la cita
 * completa (seleccionable, para copiarla a un informe), el credito a
 * CalCOFI y a los autores e ilustradores del atlas, y que tanto de lo
 * extraido reviso una persona.
 *
 * Dice tambien, sin rodeos, que la app no es de CalCOFI: usar su nombre
 * para dar credito no puede leerse como un aval.
 */
@Composable
fun PantallaAcerca(data: AtlasData, modifier: Modifier = Modifier) {
    val dimensiones = LocalDimensiones.current
    val uriHandler = LocalUriHandler.current
    val familias = remember(data) { data.species.map { it.family }.toSet().size }
    // La cita entera se sigue copiando como texto; solo la URL abre el PDF
    val colorLiga = MaterialTheme.colorScheme.primary
    val cita = remember(colorLiga) {
        buildAnnotatedString {
            append(CITA_ATLAS.substringBefore(URL_ATLAS_PDF))
            val estilo = TextLinkStyles(SpanStyle(color = colorLiga, textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Url(URL_ATLAS_PDF, estilo)) { append(URL_ATLAS_PDF) }
        }
    }

    ContenedorPantalla(modifier) {
        TituloPantalla("About")
        Text(
            "CalcofiLarvae is an identification aid for fish larvae of the California Current region, " +
                "built on the CalCOFI Atlas 33.",
            fontSize = dimensiones.tamanoTextoBase,
        )

        Seccion("Source") {
            SelectionContainer {
                Text(cita, fontSize = dimensiones.tamanoTextoBase, fontStyle = FontStyle.Italic)
            }
            Parrafo(
                "Meristic counts, diagnostic characters, life-history text and larval illustrations shown " +
                    "in this app come from this atlas and from the work of its many authors and illustrators. " +
                    "Each species in the catalog gives the atlas page it was taken from.",
            )
        }

        Seccion("CalCOFI") {
            Parrafo(
                "The California Cooperative Oceanic Fisheries Investigations is a partnership of NOAA " +
                    "Fisheries, Scripps Institution of Oceanography (UC San Diego) and the California " +
                    "Department of Fish and Wildlife. It has studied the California Current ecosystem since 1949.",
            )
            BotonSecundario(texto = "Visit calcofi.org", onClick = { uriHandler.openUri(URL_CALCOFI) })
        }

        Seccion("Illustrations") {
            Parrafo(
                "Larval figures are cropped from the atlas plates and shown for reference only. " +
                    "Rights to the illustrations remain with their original holders.",
            )
        }

        Seccion("How the data was prepared") {
            Parrafo(
                "The atlas was read from a scanned copy. Meristic tables were extracted automatically and " +
                    "every species sheet was checked by a person before being included " +
                    "(${conteo(data.species.size, "species", "species")} from " +
                    "${conteo(familias, "family", "families")}).",
            )
            Parrafo(
                "Characters read from the free text of each sheet — myomere counts, preanal length, " +
                    "hindgut pigment, body length by stage and spawning months — were extracted automatically " +
                    "and have not been reviewed by hand. When a result matters, check it against the atlas page.",
            )
            Parrafo(
                "CalCOFI's Atlas 33 errata are applied: updated scientific names, corrected counts, " +
                    "stage sizes and ranges. Species affected by a correction show its text on their page.",
            )
        }

        Seccion("Disclaimer") {
            Parrafo(
                "This app is an independent tool. It is not affiliated with, endorsed by or maintained by " +
                    "CalCOFI, NOAA, Scripps Institution of Oceanography or the California Department of Fish " +
                    "and Wildlife. Results are candidate matches to be confirmed by a trained specialist.",
            )
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    val dimensiones = LocalDimensiones.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2)) {
        Text(
            titulo,
            fontSize = dimensiones.tamanoTextoBase * 1.25f,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        contenido()
    }
}

@Composable
private fun Parrafo(texto: String) {
    Text(texto, fontSize = LocalDimensiones.current.tamanoTextoBase)
}
