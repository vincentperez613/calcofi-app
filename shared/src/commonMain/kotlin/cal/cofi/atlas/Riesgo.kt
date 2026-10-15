package cal.cofi.atlas

/**
 * Riesgo de que una identificacion este mal, segun la temporada de desove y
 * la distribucion de la especie. Una especie que desova en verano es mal
 * candidato para una larva colectada en enero.
 *
 * Es un NIVEL con sus razones, no un porcentaje: no hay datos para calibrar
 * una probabilidad, y un "23 % de error" aparentaria una precision que no
 * existe. Acompaña al resultado — no cambia el score, no elimina candidatos
 * y no decide el titular.
 *
 * REGLA PROVISIONAL. Los umbrales estan a la vista aqui arriba para poder
 * discutirlos y cambiarlos sin buscar en el resto del codigo. Ademas, los
 * meses de desove salen de leer el texto del atlas automaticamente, sin
 * revision humana: muchas veces son los meses en que se colectaron larvas, o
 * de otra region. Por eso la UI siempre muestra el texto original al lado.
 */

enum class Temporada { PICO, EN_TEMPORADA, CONTIGUO, FUERA, SIN_DATO }

enum class Distribucion { SI, NO, NO_SE }

enum class NivelRiesgo { BAJO, MEDIO, ALTO, NO_EVALUABLE }

/**
 * Cuantos meses antes o despues de la temporada todavia cuentan como
 * "contiguo" (riesgo medio) y no "fuera" (riesgo alto). Las larvas siguen en
 * el plancton semanas despues del desove, asi que el mes siguiente a la
 * temporada es esperable; antes de ella, cubre un desove que arranco antes
 * de lo que dice el atlas.
 */
const val MESES_DE_MARGEN = 1

/** El nivel, como funcion de los dos factores. Es la regla completa. */
fun nivelDeRiesgo(temporada: Temporada, distribucion: Distribucion): NivelRiesgo = when {
    // El peor de los dos factores manda.
    temporada == Temporada.FUERA || distribucion == Distribucion.NO -> NivelRiesgo.ALTO
    temporada == Temporada.CONTIGUO -> NivelRiesgo.MEDIO
    // Bajo exige LAS DOS cosas: con un solo factor a favor y el otro sin
    // saber, no hay base para decir que el riesgo es bajo.
    (temporada == Temporada.PICO || temporada == Temporada.EN_TEMPORADA) &&
        distribucion == Distribucion.SI -> NivelRiesgo.BAJO
    else -> NivelRiesgo.NO_EVALUABLE
}

/** [mes] desplazado [delta] meses, dando la vuelta al año (diciembre + 1 = enero). */
fun mesDesplazado(mes: Int, delta: Int): Int = ((mes - 1 + delta) % 12 + 12) % 12 + 1

/**
 * Como cae [mes] (1-12, o null si no se capturo) respecto a la temporada de
 * desove. El pico cuenta como temporada aunque el atlas no lo repita en
 * [meses].
 */
fun evaluarTemporada(mes: Int?, meses: List<Int>, pico: List<Int>): Temporada {
    if (mes == null) return Temporada.SIN_DATO
    val temporada = (meses + pico).toSet()
    if (temporada.isEmpty()) return Temporada.SIN_DATO
    if (mes in pico) return Temporada.PICO
    if (mes in temporada) return Temporada.EN_TEMPORADA
    val contiguo = (1..MESES_DE_MARGEN).any { d ->
        mesDesplazado(mes, d) in temporada || mesDesplazado(mes, -d) in temporada
    }
    return if (contiguo) Temporada.CONTIGUO else Temporada.FUERA
}

data class EvaluacionRiesgo(
    val nivel: NivelRiesgo,
    val temporada: Temporada,
    val distribucion: Distribucion,
    /** El porque, en lenguaje llano, para que el tecnico pueda discrepar. */
    val razones: List<String>,
)

/**
 * Evaluacion completa de una especie para una larva colectada en [mes]
 * (null si el tecnico no lo capturo), con [distribucion] tal como la marco
 * el tecnico para su zona de colecta.
 */
fun evaluarRiesgo(mes: Int?, lifeHistory: LifeHistory?, distribucion: Distribucion): EvaluacionRiesgo {
    val meses = lifeHistory?.spawningMonths.orEmpty()
    val pico = lifeHistory?.spawningPeak.orEmpty()
    val temporada = evaluarTemporada(mes, meses, pico)
    val nivel = nivelDeRiesgo(temporada, distribucion)

    val razones = buildList {
        val enMes = mes?.let { "Colectada en ${nombreMes(it)}" }
        val periodo = describirMeses(meses + pico)
        add(
            when (temporada) {
                Temporada.PICO -> "$enMes: mes de mayor desove según el atlas."
                Temporada.EN_TEMPORADA -> "$enMes: dentro de la temporada de desove ($periodo)."
                Temporada.CONTIGUO ->
                    "$enMes: justo antes o después de la temporada de desove ($periodo). " +
                        "Las larvas siguen en el plancton semanas después del desove."
                Temporada.FUERA -> "$enMes: fuera de la temporada de desove ($periodo)."
                Temporada.SIN_DATO ->
                    if (mes == null) "Sin mes de colecta: no se comparó con la temporada de desove."
                    else "El atlas no da meses de desove para esta especie."
            },
        )
        add(
            when (distribucion) {
                Distribucion.SI -> "Marcaste que la zona de colecta cae dentro de la distribución."
                Distribucion.NO -> "Marcaste que la zona de colecta queda fuera de la distribución."
                Distribucion.NO_SE ->
                    if (lifeHistory?.range == null) "El atlas no trae la distribución de esta especie."
                    else "Distribución sin marcar."
            },
        )
        val unFactorAFavor = (temporada == Temporada.PICO || temporada == Temporada.EN_TEMPORADA) ||
            distribucion == Distribucion.SI
        if (nivel == NivelRiesgo.NO_EVALUABLE && unFactorAFavor) {
            add("Para decir que el riesgo es bajo hacen falta las dos cosas: temporada y distribución.")
        }
    }
    return EvaluacionRiesgo(nivel, temporada, distribucion, razones)
}

val NOMBRES_MES: List<String> = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

fun nombreMes(mes: Int): String = NOMBRES_MES.getOrElse(mes - 1) { mes.toString() }

/** "junio, julio, agosto", o "todo el año" si estan los doce. */
fun describirMeses(meses: Collection<Int>): String {
    val unicos = meses.filter { it in 1..12 }.toSortedSet()
    return if (unicos.size == 12) "todo el año" else unicos.joinToString(", ") { nombreMes(it) }
}
