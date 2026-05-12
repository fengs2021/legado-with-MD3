package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText

data class StatItem(val label: String, val value: String)

@Composable
fun StatsGridCard(
    title: String,
    items: List<StatItem>,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(title, style = LegadoTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Column {
                for (i in items.indices step 2) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCell(items[i], Modifier.weight(1f))
                        if (i + 1 < items.size) {
                            StatCell(items[i + 1], Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (i + 2 < items.size) Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    item: StatItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        AppText(
            text = item.value,
            style = LegadoTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LegadoTheme.colorScheme.primary,
            textAlign = TextAlign.Start
        )
        AppText(
            text = item.label,
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}
