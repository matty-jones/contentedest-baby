package com.contentedest.baby.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contentedest.baby.BuildConfig
import com.contentedest.baby.update.UpdateChecker
import com.contentedest.baby.update.UpdateResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onForceSync: (() -> Unit)? = null,
    updateChecker: UpdateChecker? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for update process
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // App version info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Version",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Force Event Sync button
                    if (onForceSync != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Starting sync...")
                                    onForceSync()
                                    // Show completion message after a short delay
                                    kotlinx.coroutines.delay(500)
                                    snackbarHostState.showSnackbar("Sync completed", duration = SnackbarDuration.Short)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Force Event Sync")
                        }
                    }
                    
                    // Check for App Updates button
                    if (updateChecker != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    try {
                                        val updateInfo = updateChecker.checkForUpdate()
                                        isCheckingUpdate = false
                                        
                                        if (updateInfo != null) {
                                            // Update available - download and install
                                            isDownloadingUpdate = true
                                            snackbarHostState.showSnackbar("Downloading update...")
                                            
                                            val result = updateChecker.performUpdate(context)
                                            isDownloadingUpdate = false
                                            
                                            when (result) {
                                                is UpdateResult.InstallStarted -> {
                                                    snackbarHostState.showSnackbar(
                                                        "Update downloaded. Installation starting...",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                                is UpdateResult.DownloadFailed -> {
                                                    snackbarHostState.showSnackbar(
                                                        "Failed to download update",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                                is UpdateResult.InstallFailed -> {
                                                    snackbarHostState.showSnackbar(
                                                        "Failed to install update",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                                is UpdateResult.NoUpdateAvailable -> {
                                                    snackbarHostState.showSnackbar(
                                                        "No update available",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                "App is up to date",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    } catch (e: Exception) {
                                        isCheckingUpdate = false
                                        isDownloadingUpdate = false
                                        snackbarHostState.showSnackbar(
                                            "Error checking for update: ${e.message}",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCheckingUpdate && !isDownloadingUpdate
                        ) {
                            if (isCheckingUpdate || isDownloadingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isDownloadingUpdate) "Downloading..." else "Checking...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Update,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check for App Updates")
                            }
                        }
                    }
                }
            }
        }
    }
}

