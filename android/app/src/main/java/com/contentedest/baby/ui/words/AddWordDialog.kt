package com.contentedest.baby.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.repo.WordRepository
import com.contentedest.baby.ui.growth.GrowthDatePickerDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MabStatus {
    UNDERSTOOD,
    SAID
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordDialog(
    wordRepository: WordRepository,
    deviceId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var wordText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mabStatus by remember { mutableStateOf(MabStatus.UNDERSTOOD) }

    val isMabMatch = remember(wordText) {
        WordFuzzyMatcher.matchesMab(wordText)
    }

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
                    text = "Add word",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = wordText,
                    onValueChange = {
                        wordText = it
                        errorText = null
                        if (!WordFuzzyMatcher.matchesMab(it)) {
                            mabStatus = MabStatus.UNDERSTOOD
                        }
                    },
                    label = { Text("Word") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = {
                        if (errorText != null) {
                            Text(errorText!!)
                        }
                    }
                )

                if (isMabMatch) {
                    MabStatusRadioGroup(
                        selected = mabStatus,
                        onSelected = { mabStatus = it }
                    )
                }

                Text(text = "Date first said", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val trimmed = wordText.trim()
                            if (trimmed.isEmpty()) {
                                errorText = "Enter a word"
                                return@Button
                            }
                            isLoading = true
                            scope.launch {
                                val understands = isMabMatch
                                val says = isMabMatch && mabStatus == MabStatus.SAID
                                val ts = selectedDate
                                    .atStartOfDay(ZoneId.systemDefault())
                                    .toEpochSecond()
                                when (
                                    val result = wordRepository.insertOrUpsertMab(
                                        deviceId = deviceId,
                                        word = trimmed,
                                        ts = ts,
                                        understands = understands,
                                        says = says
                                    )
                                ) {
                                    is WordRepository.InsertResult.Duplicate -> {
                                        errorText = "That word is already in the list"
                                        isLoading = false
                                    }
                                    is WordRepository.InsertResult.Created,
                                    is WordRepository.InsertResult.UpdatedMab -> {
                                        isLoading = false
                                        onSaved()
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        GrowthDatePickerDialog(
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun MabStatusRadioGroup(
    selected: MabStatus,
    onSelected: (MabStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            text = "MacArthur-Bates (MA-B)",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MabStatus.entries.forEach { status ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected == status,
                            onClick = { onSelected(status) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == status,
                        onClick = null
                    )
                    Text(
                        text = when (status) {
                            MabStatus.UNDERSTOOD -> "Understood"
                            MabStatus.SAID -> "Said"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
