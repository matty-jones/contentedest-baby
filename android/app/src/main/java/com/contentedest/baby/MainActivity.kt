package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import com.contentedest.baby.ui.theme.TheContentedestBabyTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.net.TokenStorage
import com.contentedest.baby.sync.SyncWorker
import com.contentedest.baby.ui.timeline.TimelineScreen
import com.contentedest.baby.ui.timeline.TimelineViewModel
import com.contentedest.baby.ui.export.ExportScreen
import com.contentedest.baby.ui.export.ExportViewModel
import com.contentedest.baby.ui.pairing.PairingScreen
import com.contentedest.baby.ui.pairing.PairingViewModel
import com.contentedest.baby.ui.stats.StatisticsScreen
import com.contentedest.baby.ui.stats.StatisticsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class) // Added OptIn for ExperimentalMaterial3Api
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var eventRepository: EventRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheContentedestBabyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val isPaired = remember { mutableStateOf(tokenStorage.isPaired()) }
                
                // Update pairing state when token storage changes
                LaunchedEffect(Unit) {
                    // Check pairing state on app start
                    isPaired.value = tokenStorage.isPaired()
                    
                    // If already paired, trigger sync to pull existing data
                    if (isPaired.value) {
                        val deviceId = tokenStorage.getDeviceId() ?: "device-${System.currentTimeMillis()}"
                        
                        // Schedule immediate sync for instant data loading
                        SyncWorker.scheduleImmediateSync(this@MainActivity, deviceId)
                        // Also schedule periodic sync for ongoing updates
                        SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                    }
                }
                var showExportScreen by remember { mutableStateOf(false) }
                var showStatisticsScreen by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("The Contentedest Baby") },
                        actions = {
                            if (isPaired.value) {
                                IconButton(onClick = { showStatisticsScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Statistics"
                                    )
                                }
                                IconButton(onClick = { showExportScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Export"
                                    )
                                }
                            }
                        }
                    )

                    if (!isPaired.value) {
                        val vm: PairingViewModel = hiltViewModel()
                        PairingScreen(vm)
                        LaunchedEffect(Unit) {
                            vm.paired.collect { paired ->
                                if (paired) {
                                    isPaired.value = true
                                    val deviceId = tokenStorage.getDeviceId() ?: "device-${System.currentTimeMillis()}"

                                    // Schedule immediate sync for instant data loading
                                    SyncWorker.scheduleImmediateSync(this@MainActivity, deviceId)
                                    // Also schedule periodic sync for ongoing updates
                                    SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                                }
                            }
                            vm.error.collect { error ->
                                if (error != null) {
                                    println("Pairing error in MainActivity: $error")
                                    // You could show a toast or snackbar here
                                }
                            }
                        }
                    } else {
                        if (showExportScreen) {
                            val exportVm: ExportViewModel = hiltViewModel()
                            ExportScreen(exportVm) { showExportScreen = false }
                        } else if (showStatisticsScreen) {
                            val statsVm: StatisticsViewModel = hiltViewModel()
                            StatisticsScreen(statsVm) { showStatisticsScreen = false }
                        } else {
                            // Timeline as main screen
                            val timelineVm = remember { TimelineViewModel(eventRepository) }
                            TimelineScreen(
                                vm = timelineVm,
                                eventRepository = eventRepository,
                                date = LocalDate.now(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                }
            }
        }
    }
}


