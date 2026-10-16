package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * material-icons-extended no esta en las dependencias del proyecto. En vez
 * de agregarla solo para tres iconos de la barra de navegacion, se dibujan a
 * mano con Canvas — mismo patron que las 3 ilustraciones de etapa en
 * PantallaCaptura.kt. Leen LocalContentColor.current para heredar el tinte
 * seleccionado/no-seleccionado que NavigationBarItem/NavigationRailItem ya
 * aplican, igual que lo haria un Icon() normal.
 */
@Composable
fun IconoAnalisis(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val grosor = size.minDimension * 0.1f
        val radio = size.minDimension * 0.3f
        val centro = Offset(size.width * 0.4f, size.height * 0.4f)
        drawCircle(
            color = color,
            radius = radio,
            center = centro,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = grosor),
        )
        drawLine(
            color = color,
            start = Offset(centro.x + radio * 0.7f, centro.y + radio * 0.7f),
            end = Offset(size.width * 0.85f, size.height * 0.85f),
            strokeWidth = grosor,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun IconoHistorial(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val grosor = size.minDimension * 0.1f
        val radio = size.minDimension * 0.38f
        val centro = Offset(size.width * 0.5f, size.height * 0.52f)
        drawCircle(
            color = color,
            radius = radio,
            center = centro,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = grosor),
        )
        // manecilla larga: minutos
        drawLine(
            color = color,
            start = centro,
            end = Offset(centro.x, centro.y - radio * 0.65f),
            strokeWidth = grosor,
            cap = StrokeCap.Round,
        )
        // manecilla corta: horas
        drawLine(
            color = color,
            start = centro,
            end = Offset(centro.x + radio * 0.4f, centro.y),
            strokeWidth = grosor,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun IconoEspecies(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val grosor = size.minDimension * 0.09f

        // cuerpo: un ovalo aplanado hecho con dos curvas, de hocico a cola
        val cuerpo = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticTo(size.width * 0.45f, size.height * 0.18f, size.width * 0.72f, size.height * 0.5f)
            quadraticTo(size.width * 0.45f, size.height * 0.82f, size.width * 0.08f, size.height * 0.5f)
            close()
        }
        drawPath(cuerpo, color = color, style = Stroke(width = grosor, join = StrokeJoin.Round))

        // cola: triangulo simple
        val cola = Path().apply {
            moveTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.95f, size.height * 0.3f)
            lineTo(size.width * 0.95f, size.height * 0.7f)
            close()
        }
        drawPath(cola, color = color, style = Stroke(width = grosor, join = StrokeJoin.Round))
    }
}
