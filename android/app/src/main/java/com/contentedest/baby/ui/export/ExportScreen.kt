package com.contentedest.baby.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    vm: ExportViewModel,
    onNavigateBack: () -> Unit
) {
    val exportState by vm.exportState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (exportState) {
                is ExportState.Idle -> {
                    Text(
                        text = "Export all your baby tracking data",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    Button(
                        onClick = { vm.exportData(ExportFormat.CSV) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export as CSV")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { vm.exportData(ExportFormat.JSON) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export as JSON")
                    }
                }

                is ExportState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Exporting data...")
                }

                is ExportState.Success -> {
                    val success = exportState as ExportState.Success
                    Text(
                        text = "Export successful!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val fileName = "baby_data_${System.currentTimeMillis()}.${success.format.name.lowercase()}"
                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val file = File(context.cacheDir, fileName)
                                    file.writeText(success.data)

                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )

                                    val shareIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        type = "text/${success.format.name.lowercase()}"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            shareIntent,
                                            "Share export file"
                                        )
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to share export file")
                                }
                            }
                        }
                    ) {
                        Text("Share File")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { vm.reset() }) {
                        Text("Export Again")
                    }
                }

                is ExportState.Error -> {
                    val error = exportState as ExportState.Error
                    Text(
                        text = "Export failed",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(error.message)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { vm.reset() }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
