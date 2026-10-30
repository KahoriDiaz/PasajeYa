package com.pasajeya.app.feature.trip.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.util.formatCentsInt
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown

@Composable
fun TripSummaryScreen(
    viewModel: TripSummaryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Text(text = "Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.action_back))
                }
            }
            state.trip != null -> {
                val trip = state.trip!!
                val breakdown = trip.fareBreakdown
                
                if (breakdown != null) {
                    FareBreakdownCard(breakdown)
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Resumen no disponible. El viaje podría estar en revisión.")
                            if (trip.reviewReason != null) {
                                Text(text = "Motivo: ${trip.reviewReason}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.action_back))
                }
            }
        }
    }
}

@Composable
private fun FareBreakdownCard(breakdown: FareBreakdown) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.trip_summary_distance),
                style = MaterialTheme.typography.titleMedium,
                color = TextGray
            )
            Text(
                text = "${breakdown.distanceMeters / 1000.0} km",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = stringResource(R.string.trip_summary_rate),
                style = MaterialTheme.typography.titleSmall,
                color = TextGray
            )
            Text(text = "${breakdown.baseFarePerKmCents} céntimos/km")
            
            Text(
                text = stringResource(R.string.trip_summary_fuel),
                style = MaterialTheme.typography.titleSmall,
                color = TextGray
            )
            Text(text = "Índice: ${breakdown.fuelIndex}")
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.trip_summary_regular), color = TextGray)
                Text(text = formatCentsInt(breakdown.regularCents))
            }
            
            breakdown.benefit?.let { benefit ->
                Text(
                    text = stringResource(R.string.trip_summary_benefit, "${benefit.percent}%"),
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.trip_summary_discount, ""), color = TextGray)
                    Text(text = "-${formatCentsInt(benefit.discountCents)}", color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.trip_summary_total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = formatCentsInt(breakdown.totalCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            if (breakdown.minApplied) {
                Text(
                    text = stringResource(R.string.trip_summary_min_applied),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            
            Text(
                text = stringResource(R.string.trip_summary_proposal_notice),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
