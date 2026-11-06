package cal.cofi.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * material-icons-extended no esta en las dependencias del proyecto. En vez
 * de agregarla solo para unos pocos iconos (navegacion y modo), se dibujan a
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

/** Media luna: un disco al que otro disco le muerde la esquina superior derecha. */
@Composable
fun IconoLuna(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val radio = size.minDimension * 0.38f
        val centro = Offset(size.width * 0.5f, size.height * 0.5f)
        val disco = Path().apply { addOval(Rect(centro, radio)) }
        val mordida = Path().apply {
            addOval(Rect(Offset(centro.x + radio * 0.55f, centro.y - radio * 0.45f), radio * 0.85f))
        }
        drawPath(Path.combine(PathOperation.Difference, disco, mordida), color = color)
    }
}

/** Sol: un circulo con ocho rayos. */
@Composable
fun IconoSol(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val grosor = size.minDimension * 0.09f
        val centro = Offset(size.width * 0.5f, size.height * 0.5f)
        drawCircle(color = color, radius = size.minDimension * 0.18f, center = centro, style = Stroke(width = grosor))
        for (i in 0 until 8) {
            val angulo = i * PI / 4
            val dx = cos(angulo).toFloat()
            val dy = sin(angulo).toFloat()
            drawLine(
                color = color,
                start = Offset(centro.x + dx * size.minDimension * 0.31f, centro.y + dy * size.minDimension * 0.31f),
                end = Offset(centro.x + dx * size.minDimension * 0.44f, centro.y + dy * size.minDimension * 0.44f),
                strokeWidth = grosor,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Una "i" dentro de un circulo, el signo convencional de "informacion". */
@Composable
fun IconoAcerca(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        val grosor = size.minDimension * 0.1f
        val radio = size.minDimension * 0.4f
        val centro = Offset(size.width * 0.5f, size.height * 0.5f)
        drawCircle(color = color, radius = radio, center = centro, style = Stroke(width = grosor))
        drawCircle(color = color, radius = grosor * 0.7f, center = Offset(centro.x, centro.y - radio * 0.45f))
        drawLine(
            color = color,
            start = Offset(centro.x, centro.y - radio * 0.1f),
            end = Offset(centro.x, centro.y + radio * 0.5f),
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
