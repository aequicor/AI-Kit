package io.aequicor.pdf.data

import io.aequicor.pdf.data.db.Database
import io.aequicor.pdf.domain.AnnotationLayer
import io.aequicor.pdf.domain.DrawingTool
import io.aequicor.pdf.domain.PdfDocumentId
import io.aequicor.pdf.domain.Stroke
import io.aequicor.pdf.domain.StrokeId
import io.aequicor.pdf.domain.StrokePoint
import io.aequicor.pdf.domain.repository.AnnotationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnnotationRepositoryImpl(
    private val db: Database,
    private val dispatcher: CoroutineDispatcher,
) : AnnotationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getLayer(docId: PdfDocumentId, page: Int): AnnotationLayer =
        withContext(dispatcher) {
            val row = db.annotationQueries.selectByPage(docId.value, page.toLong()).executeAsOneOrNull()
            val strokes = row?.let { json.decodeFromString<List<StrokeDto>>(it.strokesJson) }
                ?.map { it.toDomain() } ?: emptyList()
            AnnotationLayer(docId, page, strokes)
        }

    override suspend fun saveLayer(layer: AnnotationLayer): Unit =
        withContext(dispatcher) {
            val strokesJson = json.encodeToString(layer.strokes.map { StrokeDto.fromDomain(it) })
            db.annotationQueries.insertOrReplace(
                documentId = layer.documentId.value,
                pageIndex = layer.pageIndex.toLong(),
                strokeCount = layer.strokes.size.toLong(),
                strokesJson = strokesJson,
            )
        }
}

@Serializable
private data class StrokeDto(
    val id: String,
    val toolType: String,
    val widthDp: Float,
    val color: Long,
    val points: List<StrokePointDto>,
) {
    fun toDomain(): Stroke = Stroke(
        id = StrokeId(id),
        tool = if (toolType == "eraser") DrawingTool.Eraser(widthDp) else DrawingTool.Brush(widthDp, color),
        points = points.map { it.toDomain() },
    )

    companion object {
        private const val ERASER_COLOR_NONE = 0L

        fun fromDomain(stroke: Stroke): StrokeDto {
            val (toolType, widthDp, color) = when (val t = stroke.tool) {
                is DrawingTool.Brush -> Triple("brush", t.widthDp, t.color)
                is DrawingTool.Eraser -> Triple("eraser", t.widthDp, ERASER_COLOR_NONE)
            }
            return StrokeDto(
                id = stroke.id.value,
                toolType = toolType,
                widthDp = widthDp,
                color = color,
                points = stroke.points.map { StrokePointDto.fromDomain(it) },
            )
        }
    }
}

@Serializable
private data class StrokePointDto(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val timestamp: Long,
) {
    fun toDomain() = StrokePoint(x, y, pressure, tiltX, tiltY, timestamp)

    companion object {
        fun fromDomain(p: StrokePoint) = StrokePointDto(p.x, p.y, p.pressure, p.tiltX, p.tiltY, p.timestamp)
    }
}
