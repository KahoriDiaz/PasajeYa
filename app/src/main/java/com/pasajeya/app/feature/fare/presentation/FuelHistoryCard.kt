package com.pasajeya.app.feature.fare.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pasajeya.app.feature.fare.domain.FuelRecord
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun FuelHistoryCard(
    history: List<FuelRecord>,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "PE"))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Transparencia tarifaria: Combustible",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fuente oficial: Osinergmin (Arequipa)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            history.forEach { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateLabel = record.recordedAt?.let { dateFormat.format(it) } ?: "Reciente"
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "S/ ${"%.2f".format(record.price)} / galón",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val isIncrease = record.changePercent > 0
                    val isZero = record.changePercent == 0.0
                    val badgeColor = when {
                        isZero -> Color.Gray
                        isIncrease -> Color(0xFFC62828)
                        else -> Color(0xFF2E7D32)
                    }
                    val sign = if (isIncrease) "+" else ""

                    Text(
                        text = "$sign${"%.1f".format(record.changePercent)}%",
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
