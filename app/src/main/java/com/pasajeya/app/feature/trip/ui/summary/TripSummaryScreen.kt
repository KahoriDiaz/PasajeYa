package com.pasajeya.app.feature.trip.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pasajeya.app.R
import com.pasajeya.app.core.util.formatCentsInt

@Composable
fun TripSummaryScreen(
    distanceMeters: Int = 0,
    baseFarePerKmCents: Int = 12,
    fuelType: String = "GNV",
    fuelIndex: Double = 1.0,
    fuelSourceName: String = "",
    fuelPriceDate: String = "",
    regularCents: Int = 0,
    totalCents: Int = 0,
    benefitPercent: Int? = null,
    discountCents: Int = 0,
    minApplied: Boolean = false,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(id = R.string.trip_summary_distance), style = MaterialTheme.typography.titleMedium)
                Text(text = "${distanceMeters / 1000.0} km")
                Text(text = stringResource(id = R.string.trip_summary_rate))
                Text(text = "$baseFarePerKmCents céntimos/km")
                Text(text = stringResource(id = R.string.trip_summary_fuel))
                Text(text = "$fuelType • índice $fuelIndex • $fuelSourceName • $fuelPriceDate")
                Text(text = stringResource(id = R.string.trip_summary_calculated))
                if (benefitPercent != null && benefitPercent == 50) {
                    Text(text = stringResource(id = R.string.trip_summary_benefit, benefitPercent))
                    Text(text = stringResource(id = R.string.trip_summary_discount, formatCentsInt(discountCents)))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(id = R.string.trip_summary_regular))
                    Text(text = formatCentsInt(regularCents))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(id = R.string.trip_summary_total), style = MaterialTheme.typography.titleLarge)
                    Text(text = formatCentsInt(totalCents), style = MaterialTheme.typography.titleLarge)
                }
                if (minApplied) {
                    Text(text = stringResource(id = R.string.trip_summary_min_applied))
                }
                Text(text = stringResource(id = R.string.trip_summary_proposal_notice))
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.action_back))
        }
    }
}
