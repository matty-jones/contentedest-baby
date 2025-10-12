package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.contentedest.baby.ui.growth.GrowthScreen
import com.contentedest.baby.ui.nursery.NurseryScreen
import com.contentedest.baby.BuildConfig
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
                        
                        // Schedule periodic sync (this will also trigger immediate sync if needed)
                        SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                    }
                }
                var showExportScreen by remember { mutableStateOf(false) }
                var showStatisticsScreen by remember { mutableStateOf(false) }

                // Simple bottom nav across three tabs
                var selectedTab by remember { mutableStateOf(0) } // 0: Timeline, 1: Growth, 2: Nursery

                Scaffold(
                    topBar = {
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
                    },
                    bottomBar = {
                        if (isPaired.value && !showExportScreen && !showStatisticsScreen) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Outlined.Timeline, contentDescription = "Timeline") },
                                    label = { Text("Timeline") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Outlined.MonitorWeight, contentDescription = "Growth") },
                                    label = { Text("Growth") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Outlined.Videocam, contentDescription = "Nursery") },
                                    label = { Text("Nursery") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    if (!isPaired.value) {
                        val vm: PairingViewModel = hiltViewModel()
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)) {
                            PairingScreen(vm)
                        }
                        LaunchedEffect(Unit) {
                            vm.paired.collect { paired ->
                                if (paired) {
                                    isPaired.value = true
                                    val deviceId = tokenStorage.getDeviceId() ?: "device-${System.currentTimeMillis()}"
                                    SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                                }
                            }
                            vm.error.collect { error ->
                                if (error != null) {
                                    println("Pairing error in MainActivity: $error")
                                }
                            }
                        }
                    } else if (showExportScreen) {
                        val exportVm: ExportViewModel = hiltViewModel()
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)) {
                            ExportScreen(exportVm) { showExportScreen = false }
                        }
                    } else if (showStatisticsScreen) {
                        val statsVm: StatisticsViewModel = hiltViewModel()
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)) {
                            StatisticsScreen(
                                vm = statsVm,
                                onNavigateBack = { showStatisticsScreen = false },
                                onForceRepair = {
                                    tokenStorage.clear()
                                    isPaired.value = false
                                    showStatisticsScreen = false
                                },
                                onForceSync = {
                                    val deviceId = tokenStorage.getDeviceId() ?: "device-${System.currentTimeMillis()}"
                                    SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
                                }
                            )
                        }
                    } else {
                        // Main tabs content
                        val deviceId = remember { tokenStorage.getDeviceId() ?: "device-${System.currentTimeMillis()}" }
                        when (selectedTab) {
                            0 -> {
                                val timelineVm = remember { TimelineViewModel(eventRepository) }
                                TimelineScreen(
                                    vm = timelineVm,
                                    eventRepository = eventRepository,
                                    deviceId = deviceId,
                                    date = LocalDate.now(),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                )
                            }
                            1 -> {
                                GrowthScreen(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                )
                            }
                            2 -> {
                                val rtspUrl by remember {
                                    mutableStateOf(computeRtspUrlFromBase(BuildConfig.BASE_URL))
                                }
                                NurseryScreen(
                                    rtspUrl = rtspUrl,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

private fun computeRtspUrlFromBase(baseUrl: String): String {
    // Expecting http(s)://host:port/
    return try {
        val uri = android.net.Uri.parse(baseUrl)
        val host = uri.host ?: "localhost"
        val port = if (uri.port != -1) uri.port else 8554 // common RTSP port
        // Default path for local camera stream
        "rtsp://$host:$port/stream"
    } catch (t: Throwable) {
        "rtsp://localhost:8554/stream"
    }
}
}


