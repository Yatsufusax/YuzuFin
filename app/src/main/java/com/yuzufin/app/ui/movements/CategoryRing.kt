package com.yuzufin.app.ui.movements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.ui.components.MoneaCard

/**
 * Anillo de gastos por categoría. Al tocar una categoría de la leyenda se filtra la lista; al volver a
 * tocarla, se quita el filtro. Los colores salen de la paleta de la app.
 */
@Composable
fun CategoryRingCard(
    slices: List<RingSlice>,
    totalExpense: Long,
    selectedId: Long?,
    currency: CurrencyInfo,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.outline)
    val total = slices.sumOf { it.amountMinor }.coerceAtLeast(1L)

    MoneaCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(128.dp)) {
                    val stroke = 22.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    var start = -90f
                    slices.forEachIndexed { index, slice ->
                        val sweep = 360f * slice.amountMinor / total
                        val dim = selectedId != null && slice.categoryId != selectedId
                        val color = palette[index % palette.size].copy(alpha = if (dim) 0.28f else 1f)
                        // Un hueco chico entre porciones, salvo que haya una sola.
                        val gap = if (slices.size > 1) 2f else 0f
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.ring_spent),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        MoneyFormatter.formatNumber(totalExpense, currency),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                slices.forEachIndexed { index, slice ->
                    val selected = slice.categoryId != null && slice.categoryId == selectedId
                    val label = slice.name.ifEmpty { stringResource(R.string.ring_other) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) scheme.primaryContainer else Color.Transparent)
                            .clickable(enabled = slice.categoryId != null, role = Role.Button) {
                                onSelect(if (selected) null else slice.categoryId)
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(palette[index % palette.size]),
                        )
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            "${slice.percent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.ring_hint),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
