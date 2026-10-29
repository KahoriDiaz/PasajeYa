package com.pasajeya.app.feature.claims.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClaimDialog(
    tripId: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, proposedDistanceKm: Double?) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reclamar viaje sin Tap Out") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Indica el motivo de tu reclamo y la distancia aproximada que recorriste.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo del reclamo") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Distancia estimada en km (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val distanceMeters = distanceText.toDoubleOrNull()?.times(1000.0)
                    onSubmit(reason, distanceMeters)
                },
                enabled = reason.isNotBlank()
            ) {
                Text("Enviar reclamo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}