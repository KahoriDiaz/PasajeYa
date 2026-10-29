package com.pasajeya.app.feature.fare.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.util.formatCentsInt
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareEstimateScreen(
    viewModel: FareEstimateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var routesExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Estimar mi pasaje",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Selección de Ruta
                ExposedDropdownMenuBox(
                    expanded = routesExpanded,
                    onExpandedChange = { routesExpanded = !routesExpanded }
                ) {
                    OutlinedTextField(
                        value = state.routes.find { it.id == state.selectedRouteId }?.name ?: "Seleccionar ruta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ruta") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routesExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = routesExpanded,
                        onDismissRequest = { routesExpanded = false }
                    ) {
                        state.routes.forEach { route ->
                            DropdownMenuItem(
                                text = { Text(route.name) },
                                onClick = {
                                    viewModel.selectRoute(route.id)
                                    routesExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Por ahora la estimación usa la ruta completa. En el futuro podrás elegir paraderos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                Button(
                    onClick = viewModel::estimate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedRouteId != null && !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(text = "Calcular")
                }

                state.error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                state.estimate?.let { estimate ->
                    FareEstimateResult(estimate)
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun FareEstimateResult(breakdown: FareBreakdown) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Resultado de la estimación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Importe estimado", style = MaterialTheme.typography.titleLarge)
            Text(
                text = formatCentsInt(breakdown.totalCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = "Basado en ${breakdown.distanceMeters / 1000.0} km",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        
        Text(
            text = "Este monto es una estimación. El cobro real se calculará al finalizar tu viaje.",
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
