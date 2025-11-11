package com.contentedest.baby.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    vm: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    onForceRepair: (() -> Unit)? = null,
    onForceSync: (() -> Unit)? = null,
    updateChecker: UpdateChecker? = null
) {
    val stats by vm.stats.collectAsState()
    val currentRange by vm.currentRange.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for update process
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Date range selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Date Range",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentRange == StatsRange.LAST_7_DAYS,
                            onClick = { vm.setRange(StatsRange.LAST_7_DAYS) },
                            label = { Text("7 Days") }
                        )
                        FilterChip(
                            selected = currentRange == StatsRange.LAST_30_DAYS,
                            onClick = { vm.setRange(StatsRange.LAST_30_DAYS) },
                            label = { Text("30 Days") }
                        )
                        FilterChip(
                            selected = currentRange == StatsRange.CUSTOM,
                            onClick = { vm.setRange(StatsRange.CUSTOM) },
                            label = { Text("Custom") }
                        )
                    }
                }
            }

            // Debug section (only show if debug functions are provided)
            if (onForceRepair != null || onForceSync != null || updateChecker != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Debug",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
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
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onForceRepair != null) {
                                Button(
                                    onClick = onForceRepair,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Force Re-pair")
                                }
                            }
                            
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Force Sync")
                                }
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
                                    Text("Update App")
                                }
                            }
                        }
                    }
                }
            }

            // Statistics cards
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(stats) { stat ->
                    StatCard(stat)
                }
            }
        }
    }
}

@Composable
fun StatCard(stat: StatItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineSmall
            )
            if (stat.subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stat.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

enum class StatsRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    CUSTOM
}

data class StatItem(
    val title: String,
    val value: String,
    val subtitle: String = ""
)
