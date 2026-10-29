package com.pasajeya.app.feature.benefit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitScreen(
    onBack: () -> Unit,
    onSubmitRequest: (university: String, code: String) -> Unit
) {
    var university by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beneficio Universitario 50%") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Registra los datos de tu carné universitario para acceder al medio pasaje legal (50% de descuento).",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = university,
                onValueChange = { university = it },
                label = { Text("Universidad / Instituto") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Código de estudiante / Carné") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSubmitRequest(university, code) },
                enabled = university.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Solicitar beneficio del 50%")
            }
        }
    }
}