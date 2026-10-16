package cal.cofi.atlas.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clasificacion de ancho disponible. Toda decision de layout (columnas,
 * ancho maximo, tamanos) se deriva de esto, nunca de un maxWidth leido a
 * mano en cada pantalla.
 */
enum class Ancho { COMPACTO, MEDIO, AMPLIO }

private fun anchoPara(maxWidth: Dp): Ancho = when {
    maxWidth < 600.dp -> Ancho.COMPACTO
    maxWidth <= 900.dp -> Ancho.MEDIO
    else -> Ancho.AMPLIO
}

/**
 * Ancho actual, memoizado por [BoxWithConstraintsScope.maxWidth]. Se usa
 * desde dentro de un BoxWithConstraints (ver [ProveedorDeLayout]); las
 * pantallas nunca llaman a esto directamente, leen [LocalAncho].
 */
@Composable
fun BoxWithConstraintsScope.rememberAncho(): Ancho = remember(maxWidth) { anchoPara(maxWidth) }

/**
 * Tokens de tamano para un [Ancho] dado. Ninguna pantalla ni componente
 * declara un Dp o Sp suelto: todos leen esto via [LocalDimensiones].
 */
data class Dimensiones(
    val alturaBotonMinima: Dp,
    val tamanoTextoBase: TextUnit,
    val tamanoTextoTitulo: TextUnit,
    val espaciado: Dp,
    val anchoMaximoContenido: Dp,
    val tamanoIlustracion: Dp,
)

/**
 * alturaBotonMinima y tamanoTextoBase se mantienen igual de generosos en
 * COMPACTO y MEDIO a proposito: el barco, los guantes mojados y el sol en
 * la pantalla no dejan de aplicar solo porque el dispositivo sea una
 * tablet. AMPLIO es la excepcion: un monitor de escritorio se usa con
 * mouse y teclado, en condiciones normales de oficina o laboratorio, asi
 * que sus tokens son mas cercanos a los convencionales de escritorio en
 * vez de tamanos pensados para dedo-con-guante.
 */
private fun dimensionesPara(ancho: Ancho): Dimensiones = when (ancho) {
    Ancho.COMPACTO -> Dimensiones(
        alturaBotonMinima = 56.dp,
        tamanoTextoBase = 18.sp,
        tamanoTextoTitulo = 28.sp,
        espaciado = 16.dp,
        anchoMaximoContenido = Dp.Unspecified, // sin tope: la pantalla ya es angosta
        tamanoIlustracion = 72.dp,
    )
    Ancho.MEDIO -> Dimensiones(
        alturaBotonMinima = 56.dp,
        tamanoTextoBase = 18.sp,
        tamanoTextoTitulo = 32.sp,
        espaciado = 20.dp,
        anchoMaximoContenido = Dp.Unspecified,
        tamanoIlustracion = 88.dp,
    )
    Ancho.AMPLIO -> Dimensiones(
        alturaBotonMinima = 40.dp,
        tamanoTextoBase = 15.sp,
        tamanoTextoTitulo = 24.sp,
        espaciado = 16.dp,
        anchoMaximoContenido = 800.dp, // texto a todo el ancho de un monitor es ilegible
        tamanoIlustracion = 72.dp,
    )
}

val LocalAncho = compositionLocalOf<Ancho> {
    error("LocalAncho sin proveer: envolver el arbol con ProveedorDeLayout()")
}

val LocalDimensiones = compositionLocalOf<Dimensiones> {
    error("LocalDimensiones sin proveer: envolver el arbol con ProveedorDeLayout()")
}

/**
 * Raiz del sistema de layout. Mide el espacio disponible una sola vez,
 * clasifica el [Ancho] y provee [LocalAncho]/[LocalDimensiones] para que
 * todo lo de abajo los lea en vez de volver a medir.
 */
@Composable
fun ProveedorDeLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val ancho = rememberAncho()
        val dimensiones = remember(ancho) { dimensionesPara(ancho) }
        CompositionLocalProvider(
            LocalAncho provides ancho,
            LocalDimensiones provides dimensiones,
        ) {
            content()
        }
    }
}

/**
 * Contenedor estandar de una pantalla: aplica el espaciado y, en AMPLIO,
 * limita y centra el contenido para que el texto no quede corrido de punta
 * a punta de un monitor. Las pantallas arman su contenido adentro sin
 * preocuparse por el ancho.
 *
 * Scrollea verticalmente. Ninguna pantalla puede depender de que la ventana
 * sea lo bastante grande para mostrar todo sin cortar — en un telefono eso
 * ni siquiera es una opcion, no hay forma de "agrandar la ventana".
 */
@Composable
fun ContenedorPantalla(
    modifier: Modifier = Modifier,
    anchoCompleto: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimensiones = LocalDimensiones.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (anchoCompleto) Modifier
                    else Modifier.widthIn(max = dimensiones.anchoMaximoContenido),
                )
                .verticalScroll(rememberScrollState())
                .padding(dimensiones.espaciado),
            verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado),
            content = content,
        )
    }
}

// ------------------------------------------------------------ componentes

@Composable
fun TituloPantalla(texto: String, modifier: Modifier = Modifier) {
    val dimensiones = LocalDimensiones.current
    Text(
        text = texto,
        fontSize = dimensiones.tamanoTextoTitulo,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val dimensiones = LocalDimensiones.current
    Button(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensiones.alturaBotonMinima),
    ) {
        Text(texto, fontSize = dimensiones.tamanoTextoBase)
    }
}

@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    val dimensiones = LocalDimensiones.current
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensiones.alturaBotonMinima),
    ) {
        Text(texto, fontSize = dimensiones.tamanoTextoBase)
    }
}

/**
 * Campo para valores numericos (tallas, conteos). Teclado decimal en
 * Android — pero KeyboardType.Decimal no impide pegar texto arbitrario, asi
 * que [mensajeError] (de Validacion.kt) es la unica validacion real: vive
 * en el modelo, no en el teclado. Cuando no es null se muestra debajo del
 * campo en rojo, via el soporte nativo de OutlinedTextField para esto.
 */
@Composable
fun CampoNumerico(
    valor: String,
    onValorCambia: (String) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String? = null,
    mensajeError: String? = null,
) {
    val dimensiones = LocalDimensiones.current
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = etiqueta?.let { texto -> { Text(texto, fontSize = dimensiones.tamanoTextoBase) } },
        textStyle = TextStyle(fontSize = dimensiones.tamanoTextoBase),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = mensajeError != null,
        supportingText = mensajeError?.let { mensaje ->
            { Text(mensaje, fontSize = dimensiones.tamanoTextoBase) }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensiones.alturaBotonMinima),
    )
}

/**
 * Boton de una opcion entre varias (mes de colecta, si/no/no se) que deja
 * ver cual esta elegida. A diferencia de [BotonPrincipal], elegir no avanza:
 * solo marca.
 */
@Composable
fun BotonSeleccionable(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensiones = LocalDimensiones.current
    val color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val forma = RoundedCornerShape(dimensiones.espaciado / 2)
    Box(
        modifier = modifier
            .heightIn(min = dimensiones.alturaBotonMinima)
            .clip(forma)
            .border(if (seleccionado) 2.dp else 1.dp, color, forma)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            fontSize = dimensiones.tamanoTextoBase,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
            color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Tarjeta de opcion con dibujo arriba y texto abajo: la de las etapas de
 * PantallaCaptura y la de las preguntas de opciones (p.ej. forma del
 * cuerpo). El tecnico elige viendo el dibujo, no leyendo un termino.
 * [dibujo] recibe el color a usar (resaltado si esta seleccionada) y el
 * tamaño que le toca.
 */
@Composable
fun TarjetaIlustrada(
    texto: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
    dibujo: @Composable (Color, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensiones = LocalDimensiones.current
    val colorBorde = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val colorDibujo = if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val forma = RoundedCornerShape(dimensiones.espaciado)

    Column(
        modifier = modifier
            .clip(forma)
            .border(if (seleccionada) 2.dp else 1.dp, colorBorde, forma)
            .clickable(onClick = onClick)
            .padding(dimensiones.espaciado),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensiones.espaciado / 2),
    ) {
        dibujo(colorDibujo, Modifier.size(dimensiones.tamanoIlustracion))
        Text(
            text = texto,
            fontSize = dimensiones.tamanoTextoBase,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Campo de texto libre (buscador de especies, etc.) — a diferencia de
 * [CampoNumerico], sin teclado decimal ni [mensajeError]: la sanitizacion de
 * lo que se escribe aca (limite de largo, caracteres de control) vive en
 * quien lo usa, no en este componente generico.
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorCambia: (String) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String? = null,
) {
    val dimensiones = LocalDimensiones.current
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = etiqueta?.let { texto -> { Text(texto, fontSize = dimensiones.tamanoTextoBase) } },
        textStyle = TextStyle(fontSize = dimensiones.tamanoTextoBase),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensiones.alturaBotonMinima),
    )
}
