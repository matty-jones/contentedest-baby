package com.contentedest.baby.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.local.GrowthCategory
import com.contentedest.baby.data.repo.GrowthRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGrowthDialog(
    growthRepository: GrowthRepository,
    deviceId: String,
    category: GrowthCategory,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalDateTime.now()) }
    var valueText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(getDefaultUnit(category)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add ${category.name.replaceFirstChar { it.uppercase() }} Entry",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Date picker
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                }

                // Time picker
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedTime.format(DateTimeFormatter.ofPattern("h:mm a")))
                }

                // Value input
                Text(
                    text = "Value",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Enter ${category.name}") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    placeholder = { Text(getPlaceholder(category)) }
                )

                // Unit selector
                Text(
                    text = "Unit",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    getUnitsForCategory(category).forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val value = valueText.toDoubleOrNull()
                            if (value != null && value > 0) {
                                isLoading = true
                                scope.launch {
                                    val dateTime = selectedDate.atTime(selectedTime.toLocalTime())
                                    val ts = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                                    growthRepository.insertGrowthData(
                                        deviceId = deviceId,
                                        category = category,
                                        value = value,
                                        unit = unit,
                                        ts = ts
                                    )
                                    isLoading = false
                                    onSaved()
                                }
                            }
                        },
                        enabled = valueText.toDoubleOrNull() != null && valueText.toDoubleOrNull()!! > 0 && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        GrowthDatePickerDialog(
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time picker dialog
    if (showTimePicker) {
        TimePickerDialog(
            initialTime = selectedTime,
            onTimeSelected = { time ->
                selectedTime = time
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowthDatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateSelected(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: LocalDateTime,
    onTimeSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = remember {
        TimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = false
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.headlineSmall
                )

                TimePicker(state = timePickerState)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val selectedTime = LocalDateTime.of(
                                initialTime.year,
                                initialTime.month,
                                initialTime.dayOfMonth,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                            onTimeSelected(selectedTime)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

fun getDefaultUnit(category: GrowthCategory): String {
    return when (category) {
        GrowthCategory.weight -> "lb"
        GrowthCategory.height -> "in"
        GrowthCategory.head -> "in"
    }
}

fun getUnitsForCategory(category: GrowthCategory): List<String> {
    return when (category) {
        GrowthCategory.weight -> listOf("lb", "kg")
        GrowthCategory.height -> listOf("in", "cm")
        GrowthCategory.head -> listOf("in", "cm")
    }
}

fun getPlaceholder(category: GrowthCategory): String {
    return when (category) {
        GrowthCategory.weight -> "e.g., 10.5"
        GrowthCategory.height -> "e.g., 24.5"
        GrowthCategory.head -> "e.g., 15.5"
    }
}

