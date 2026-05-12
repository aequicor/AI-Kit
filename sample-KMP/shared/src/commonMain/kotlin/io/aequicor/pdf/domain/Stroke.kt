package io.aequicor.pdf.domain

import kotlin.jvm.JvmInline

@JvmInline
value class StrokeId(val value: String)

data class Stroke(
    val id: StrokeId,
    val tool: DrawingTool,
    val points: List<StrokePoint>,
)
