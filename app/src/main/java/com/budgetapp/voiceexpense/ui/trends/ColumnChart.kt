package com.budgetapp.voiceexpense.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.budgetapp.voiceexpense.domain.formatAxisMoney
import com.budgetapp.voiceexpense.domain.niceAxisMaxCents

/** [label] is the text under the column, or null to leave it out when they'd crowd. */
data class ChartColumn(val label: String?, val cents: Long, val highlighted: Boolean)

private val ChartHeight = 190.dp
private val AxisWidth = 44.dp
private val TopPadding = 18.dp
private val LabelBand = 22.dp
private val MaxBarWidth = 24.dp
private const val BarSlotShare = 0.72f
private val BarRadius = 4.dp
private val MinBarHeight = 2.dp

/** Light enough that the highlighted column is the first thing you see. */
private const val ContextColumnAlpha = 0.55f

/**
 * Columns on one axis from zero. The current day or week takes the accent colour and the rest stay
 * neutral, since colouring by rank or size would only repeat what the bar's height already says.
 */
@Composable
fun ColumnChart(
    columns: List<ChartColumn>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    referenceCents: Long? = null,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val contextColumn = colors.outline.copy(alpha = ContextColumnAlpha).compositeOver(colors.surface)
    val axisMax = niceAxisMaxCents(maxOf(columns.maxOf { it.cents }, referenceCents ?: 0))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .pointerInput(columns.size) {
                detectTapGestures { tap ->
                    val plotLeft = AxisWidth.toPx()
                    val slot = (size.width - plotLeft) / columns.size
                    if (tap.x >= plotLeft) {
                        onSelect(((tap.x - plotLeft) / slot).toInt().coerceIn(columns.indices))
                    }
                }
            },
    ) {
        val plotLeft = AxisWidth.toPx()
        val plotTop = TopPadding.toPx()
        val baseline = size.height - LabelBand.toPx()
        val slot = (size.width - plotLeft) / columns.size
        fun yFor(cents: Long) = baseline - (baseline - plotTop) * (cents.toFloat() / axisMax)

        drawRoundRect(
            color = colors.surfaceVariant,
            topLeft = Offset(plotLeft + slot * selected, plotTop),
            size = Size(slot, baseline - plotTop),
            cornerRadius = CornerRadius(BarRadius.toPx()),
        )

        listOf(0L, axisMax / 2, axisMax).forEach { tick ->
            val y = yFor(tick)
            drawLine(
                color = colors.outlineVariant,
                start = Offset(plotLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val text = measurer.measure(formatAxisMoney(tick), axisStyle)
            drawText(
                text,
                topLeft = Offset(
                    plotLeft - 6.dp.toPx() - text.size.width,
                    y - text.size.height / 2f,
                ),
            )
        }

        val barWidth = minOf(slot * BarSlotShare, MaxBarWidth.toPx())
        columns.forEachIndexed { index, column ->
            val center = plotLeft + slot * (index + 0.5f)
            if (column.cents > 0) {
                drawBar(
                    left = center - barWidth / 2,
                    top = minOf(yFor(column.cents), baseline - MinBarHeight.toPx()),
                    right = center + barWidth / 2,
                    bottom = baseline,
                    color = if (column.highlighted) colors.primary else contextColumn,
                )
            }
            column.label?.let { label ->
                val text = measurer.measure(label, axisStyle)
                drawText(
                    text,
                    topLeft = Offset(
                        (center - text.size.width / 2f)
                            .coerceIn(plotLeft, size.width - text.size.width),
                        baseline + 6.dp.toPx(),
                    ),
                )
            }
        }

        if (referenceCents != null) {
            val y = yFor(referenceCents)
            drawLine(
                color = colors.onSurface,
                start = Offset(plotLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

/** Rounded at the data end and square where it meets the baseline. */
private fun DrawScope.drawBar(left: Float, top: Float, right: Float, bottom: Float, color: Color) {
    val radius = CornerRadius(minOf(BarRadius.toPx(), bottom - top))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}
