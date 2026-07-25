package com.contentedest.baby.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.contentedest.baby.data.repo.SettingsRepository
import com.contentedest.baby.ui.growth.GrowthDatePickerDialog
import com.contentedest.baby.update.UpdateChecker
import com.contentedest.baby.update.UpdateResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onForceSync: (() -> Unit)? = null,
    updateChecker: UpdateChecker? = null,
    settingsRepository: SettingsRepository? = null,
    onDobChanged: ((Int?) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var dobEpochDays by remember { mutableStateOf<Int?>(null) }
    var showDobPicker by remember { mutableStateOf(false) }

    LaunchedEffect(settingsRepository) {
        if (settingsRepository != null) {
            dobEpochDays = settingsRepository.getDobEpochDays()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (settingsRepository != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Baby",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Date of birth",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDobPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = dobEpochDays?.let {
                                LocalDate.ofEpochDay(it.toLong())
                                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            } ?: "Not set"
                            Text(label)
                        }
                        Text(
                            text = "Used for growth and vocabulary age percentiles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

                    if (onForceSync != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Starting sync...")
                                    onForceSync()
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

    if (showDobPicker && settingsRepository != null) {
        GrowthDatePickerDialog(
            onDateSelected = { date ->
                showDobPicker = false
                scope.launch {
                    val days = date.toEpochDay().toInt()
                    settingsRepository.setDobEpochDays(days)
                    dobEpochDays = days
                    onDobChanged?.invoke(days)
                    snackbarHostState.showSnackbar("Date of birth saved")
                }
            },
            onDismiss = { showDobPicker = false }
        )
    }
}
