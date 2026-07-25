package com.contentedest.baby.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.data.local.BabyWordEntity

@Composable
fun SearchWordDialog(
    words: List<BabyWordEntity>,
    onDismiss: () -> Unit,
    onFound: (BabyWordEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

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
                    text = "Search word",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val trimmed = query.trim()
                            if (trimmed.isEmpty()) {
                                errorText = "Enter a word"
                                return@Button
                            }
                            val match = WordFuzzyMatcher.findWordInList(trimmed, words)
                            if (match == null) {
                                errorText = "Word not found yet"
                            } else {
                                onFound(match)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Find")
                    }
                }
            }
        }
    }
}
