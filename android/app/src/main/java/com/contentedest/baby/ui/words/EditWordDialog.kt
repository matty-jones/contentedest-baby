package com.contentedest.baby.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.data.repo.WordRepository
import com.contentedest.baby.ui.growth.GrowthDatePickerDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWordDialog(
    wordRepository: WordRepository,
    wordEntity: BabyWordEntity,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var wordText by remember(wordEntity.id) { mutableStateOf(wordEntity.word) }
    var selectedDate by remember(wordEntity.id) {
        mutableStateOf(Instant.ofEpochSecond(wordEntity.ts).atZone(zone).toLocalDate())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mabStatus by remember(wordEntity.id) {
        mutableStateOf(
            when {
                wordEntity.says -> MabStatus.SAID
                else -> MabStatus.UNDERSTOOD
            }
        )
    }

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
                    text = "Edit word",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = wordText,
                    onValueChange = {
                        wordText = it
                        errorText = null
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
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
                                if (wordRepository.hasWordCaseInsensitiveExceptId(trimmed, wordEntity.id)) {
                                    errorText = "That word is already in the list"
                                    isLoading = false
                                    return@launch
                                }
                                val understands = isMabMatch
                                val says = isMabMatch && mabStatus == MabStatus.SAID
                                val ts = selectedDate
                                    .atStartOfDay(zone)
                                    .toEpochSecond()
                                wordRepository.updateWord(
                                    id = wordEntity.id,
                                    word = trimmed,
                                    ts = ts,
                                    understands = understands,
                                    says = says
                                )
                                isLoading = false
                                onSaved()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }

                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Delete word",
                        color = MaterialTheme.colorScheme.error
                    )
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

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete word?") },
            text = { Text("This word will be removed. Sync will update other devices.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        isLoading = true
                        scope.launch {
                            wordRepository.delete(wordEntity.id)
                            isLoading = false
                            onDeleted()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
