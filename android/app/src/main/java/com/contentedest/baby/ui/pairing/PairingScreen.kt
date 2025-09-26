package com.contentedest.baby.ui.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(vm: PairingViewModel) {
    var code by remember { mutableStateOf("abc") } // Pre-fill with test pairing code
    var deviceId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val error by vm.error.collectAsState()
    val paired by vm.paired.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Pairing Code") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = deviceId, onValueChange = { deviceId = it }, label = { Text("Device Id") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())

        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Button(
            onClick = {
                if (code.isNotBlank() && deviceId.isNotBlank()) {
                    vm.pair(code, deviceId, name.ifBlank { null })
                }
            },
            enabled = code.isNotBlank() && deviceId.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Pair")
        }

        if (paired) {
            Text(
                text = "✅ Pairing successful! Loading main app...",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
