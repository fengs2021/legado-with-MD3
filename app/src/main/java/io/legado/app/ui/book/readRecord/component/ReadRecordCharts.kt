package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.book.readRecord.ReadPeriod
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.formatReadDuration
import java.time.LocalDate

@Composable
fun ReadingTimeBarChartCard(
    data: List<Pair<LocalDate, Long>>,
    period: ReadPeriod,
    modifier: Modifier = Modifier
) {
    val maxTime = data.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText("阅读时长分布", style = LegadoTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                // Y-Axis
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    AppText(
                        text = formatReadDuration(maxTime),
                        style = LegadoTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                    AppText(
                        text = "0",
                        style = LegadoTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chart
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    data.forEachIndexed { index, (date, time) ->
                        val heightFactor = time.toFloat() / maxTime

                        val showLabel = when (period) {
                            ReadPeriod.DAY -> true
                            ReadPeriod.WEEK -> date.dayOfWeek.value in listOf(2, 4, 6)
                            ReadPeriod.MONTH -> date.dayOfMonth == 1 || date.dayOfMonth % 7 == 0 || index == data.lastIndex
                            ReadPeriod.YEAR -> date.dayOfMonth == 1
                            else -> false
                        }

                        val labelText = when (period) {
                            ReadPeriod.YEAR -> "${date.monthValue}月"
                            ReadPeriod.WEEK -> when (date.dayOfWeek.value) {
                                2 -> "二"; 4 -> "四"; 6 -> "六"; else -> ""
                            }
                            else -> date.dayOfMonth.toString()
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(heightFactor.coerceAtLeast(0.01f))
                                    .padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (time > 0) LegadoTheme.colorScheme.primary
                                        else LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                            )

                            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.TopCenter) {
                                if (showLabel) {
                                    AppText(
                                        text = labelText,
                                        style = LegadoTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = LegadoTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
