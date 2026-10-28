package com.pasajeya.app.feature.fare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pasajeya.app.core.util.formatCentsInt

@Composable
fun FareEstimateScreen(
    onEstimate: (Double) -> Unit = {},
    estimatedCents: Int? = null,
    breakdown: String? = null,
    onBack: () -> Unit = {}
) {
    var distanceKm by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = distanceKm,
                    onValueChange = { distanceKm = it },
                    label = { Text("Distancia (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val km = distanceKm.replace(",", ".").toDoubleOrNull() ?: 0.0
                        onEstimate(km)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Calcular")
                }
                if (estimatedCents != null) {
                    Text(text = "Estimado: ${formatCentsInt(estimatedCents)}", style = MaterialTheme.typography.titleLarge)
                }
                if (breakdown != null) {
                    Text(text = breakdown)
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Volver")
        }
    }
}
