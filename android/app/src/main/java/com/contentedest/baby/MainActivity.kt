package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkManager
import com.contentedest.baby.net.TokenStorage
import com.contentedest.baby.sync.SyncWorker
import com.contentedest.baby.ui.daily.DailyLogScreen
import com.contentedest.baby.ui.daily.DailyLogViewModel
import com.contentedest.baby.ui.export.ExportScreen
import com.contentedest.baby.ui.export.ExportViewModel
import com.contentedest.baby.ui.pairing.PairingScreen
import com.contentedest.baby.ui.pairing.PairingViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class) // Added OptIn for ExperimentalMaterial3Api
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val hasToken = remember { mutableStateOf(tokenStorage.getToken() != null) }
                var showExportScreen by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("The Contentedest Baby") },
                        actions = {
                            if (hasToken.value) {
                                IconButton(onClick = { showExportScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Export"
                                    )
                                }
                            }
                        }
                    )

                    if (!hasToken.value) {
                        val vm: PairingViewModel = hiltViewModel()
                        PairingScreen(vm)
                        LaunchedEffect(Unit) {
                            vm.paired.collect { paired ->
                                if (paired) {
                                    hasToken.value = true
                                    // Schedule periodic sync
                                    val deviceId = "device-${System.currentTimeMillis()}" // TODO: get actual device ID
                                    SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                                }
                            }
                        }
                    } else {
                        if (showExportScreen) {
                            val exportVm: ExportViewModel = hiltViewModel()
                            ExportScreen(exportVm) { showExportScreen = false }
                        } else {
                            val vm: DailyLogViewModel = hiltViewModel()
                            LaunchedEffect(Unit) { vm.load(LocalDate.now()) }
                            DailyLogScreen(vm)
                            // Handle undo snackbar dismissal
                            LaunchedEffect(vm.showUndoSnackbar.collectAsState().value) {
                                // This could trigger a SnackbarHostState.showSnackbar() call
                                // For now, it's handled internally in the screen
                            }
                        }
                    }
                }
            }
        }
    }
}


