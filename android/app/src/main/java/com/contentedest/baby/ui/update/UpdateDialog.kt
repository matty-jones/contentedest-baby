package com.contentedest.baby.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.contentedest.baby.net.UpdateInfoResponse

private fun truncateCommitMessage(message: String, maxLength: Int = 50): String {
    return if (message.length <= maxLength) {
        message
    } else {
        message.take(maxLength).trimEnd() + "..."
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfoResponse,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    isMandatory: Boolean = false
) {
    Dialog(onDismissRequest = { if (!isMandatory) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Version ${updateInfo.versionName} is available",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (!updateInfo.commitMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = truncateCommitMessage(updateInfo.commitMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!updateInfo.releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isMandatory) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Later")
                        }
                    }
                    
                    Button(
                        onClick = onUpdate,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Update")
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateProgressDialog(
    progress: Float? = null,
    message: String = "Downloading update..."
) {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (progress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

