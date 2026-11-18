package com.notes.purenoteit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class InkTool { PEN, HIGHLIGHTER, PENCIL }

@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    /** 0f..1f; 0.5f when the device reports no pressure. */
    val pressure: Float = 0.5f,
    val tMillis: Long = 0L
)

@Serializable
data class InkStroke(
    val id: String,
    val tool: InkTool = InkTool.PEN,
    val colorArgb: Long = 0xFF000000L,
    val baseWidth: Float = 4f,
    val points: List<InkPoint> = emptyList()
)

@Serializable
data class InkDocument(
    val version: Int = 1,
    val width: Float = 1080f,
    val height: Float = 1440f,
    val strokes: List<InkStroke> = emptyList()
) {
    val isEmpty: Boolean get() = strokes.isEmpty()

    fun withStroke(stroke: InkStroke): InkDocument = copy(strokes = strokes + stroke)

    fun withoutStroke(strokeId: String): InkDocument =
        copy(strokes = strokes.filterNot { it.id == strokeId })

    /**
     * Stroke eraser: removes every stroke that has a point within [radius] of ([x], [y]).
     * Returns the same instance when nothing was hit.
     */
    fun eraseAt(x: Float, y: Float, radius: Float): InkDocument {
        val r2 = radius * radius
        val kept = strokes.filterNot { s ->
            s.points.any { p ->
                val dx = p.x - x
                val dy = p.y - y
                dx * dx + dy * dy <= r2
            }
        }
        return if (kept.size == strokes.size) this else copy(strokes = kept)
    }
}

object InkCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun encode(doc: InkDocument): String = json.encodeToString(InkDocument.serializer(), doc)

    /** Returns null for blank or corrupt payloads instead of throwing. */
    fun decode(raw: String?): InkDocument? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(InkDocument.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Stroke width for a point: pressure-scaled for PEN/PENCIL, constant for HIGHLIGHTER. */
    fun widthAt(stroke: InkStroke, point: InkPoint): Float = when (stroke.tool) {
        InkTool.HIGHLIGHTER -> stroke.baseWidth * 3f
        InkTool.PENCIL -> stroke.baseWidth * (0.4f + 0.8f * point.pressure.coerceIn(0f, 1f))
        InkTool.PEN -> stroke.baseWidth * (0.5f + 1.0f * point.pressure.coerceIn(0f, 1f))
    }
}
