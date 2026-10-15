package cal.cofi.atlas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Carga de los JSON de composeResources.
 *
 * Vive aparte de Models.kt a proposito: el dominio y el motor no dependen de
 * kotlinx.serialization, asi que se pueden probar con tests de JVM puros sin
 * arrastrar el plugin ni los recursos.
 */

// ---- DTOs: espejo exacto del JSON, nada mas -----------------------------
@Serializable
private data class SpeciesDto(
    val id: String,
    val species: String,
    val family: String,
    val pdf_page: Int,
    val meristics: Map<String, List<Double>> = emptyMap(),
    val diagnostic: Map<String, DiagnosticDto> = emptyMap(),
    val sample_sizes: Map<String, SampleDto> = emptyMap(),
    val life_history: LifeHistoryDto? = null,
)

/**
 * Todo opcional: hay fichas sin temporada, y bundles viejos sin el bloque
 * completo. Un "morphometrics" de un bundle viejo se ignora solo
 * (ignoreUnknownKeys) — la app ya no usa morfometria.
 */
@Serializable
private data class LifeHistoryDto(
    val range: String? = null,
    val habitat: String? = null,
    val spawning_season: String? = null,
    val spawning_months: List<Int> = emptyList(),
    val spawning_peak: List<Int> = emptyList(),
)

@Serializable
private data class DiagnosticDto(
    val range: List<Double>? = null,
    val value: String? = null,
    val conditional: String? = null,
)

@Serializable
private data class SampleDto(val kind: String = "unknown", val n: Int = 0)

@Serializable
private data class FigureDto(
    val plate: String,
    val box: List<Int>,
    val size_mm: Double? = null,
    val stage: String? = null,
    val stage_source: String? = null,
    val caption_taxon: String? = null,
    val species: String? = null,
    val uncertain: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

private fun List<Double>.toRange() = Range(this[0], this[1])

/**
 * Los meses salen de leer texto con OCR: un 13 o un 0 no significa nada y
 * se descarta en vez de llegar a la comparacion de temporada. Si no queda
 * nada de nada, la especie simplemente no trae historia de vida.
 */
private fun LifeHistoryDto.toDomain(): LifeHistory? {
    val lh = LifeHistory(
        range = range?.takeIf { it.isNotBlank() },
        habitat = habitat?.takeIf { it.isNotBlank() },
        spawningSeason = spawning_season?.takeIf { it.isNotBlank() },
        spawningMonths = spawning_months.filter { it in 1..12 }.distinct().sorted(),
        spawningPeak = spawning_peak.filter { it in 1..12 }.distinct().sorted(),
    )
    return lh.takeUnless { it == LifeHistory() }
}

/**
 * [readFile] debe devolver el contenido de una ruta relativa a
 * composeResources/files/. Con Compose Multiplatform:
 *
 *   AtlasLoader.parse { path -> Res.readBytes("files/$path").decodeToString() }
 *
 * Se recibe como lambda para que el dominio no dependa de Compose y se pueda
 * probar leyendo de disco en un test de JVM.
 */
object AtlasLoader {
    fun parse(readFile: (String) -> String): AtlasData {
        val species = json.decodeFromString<List<SpeciesDto>>(readFile("data/species.json"))
            .map { d ->
                Species(
                    id = d.id,
                    species = d.species,
                    family = d.family,
                    pdfPage = d.pdf_page,
                    meristics = d.meristics.mapValues { it.value.toRange() },
                    diagnostic = d.diagnostic.mapValues {
                        DiagnosticChar(
                            range = it.value.range?.toRange(),
                            value = it.value.value,
                            conditional = it.value.conditional,
                        )
                    },
                    sampleSizes = d.sample_sizes.mapValues { SampleSize(it.value.kind, it.value.n) },
                    lifeHistory = d.life_history?.toDomain(),
                )
            }

        val coarse = json
            .decodeFromString<Map<String, Map<String, List<Double>>>>(
                readFile("data/coarse_filter.json"))
            .mapValues { t -> t.value.mapValues { it.value.toRange() } }

        val figures = json.decodeFromString<List<FigureDto>>(readFile("data/figures.json"))
            .map {
                FigureRef(
                    plate = it.plate,
                    box = it.box,
                    sizeMm = it.size_mm,
                    stage = Stage.fromCode(it.stage),
                    stageSource = it.stage_source,
                    captionTaxon = it.caption_taxon,
                    species = it.species,
                    uncertain = it.uncertain,
                )
            }

        return AtlasData(species, CoarseFilter(coarse), figures)
    }
}
