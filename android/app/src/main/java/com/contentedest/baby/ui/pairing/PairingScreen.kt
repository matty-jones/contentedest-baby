package com.contentedest.baby.ui.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(vm: PairingViewModel) {
    var code by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Pairing Code") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = deviceId, onValueChange = { deviceId = it }, label = { Text("Device Id") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.pair(code, deviceId, name.ifBlank { null }) }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Pair")
        }
    }
}
