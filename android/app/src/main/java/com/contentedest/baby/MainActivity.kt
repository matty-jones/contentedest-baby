package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import com.contentedest.baby.ui.theme.TheContentedestBabyTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.contentedest.baby.data.repo.EventRepository
import com.contentedest.baby.sync.SyncWorker
import com.contentedest.baby.ui.timeline.TimelineScreen
import com.contentedest.baby.ui.timeline.TimelineViewModel
import com.contentedest.baby.ui.export.ExportScreen
import com.contentedest.baby.ui.export.ExportViewModel
import com.contentedest.baby.ui.stats.StatisticsScreen
import com.contentedest.baby.ui.stats.StatisticsViewModel
import com.contentedest.baby.ui.growth.GrowthScreen
import com.contentedest.baby.ui.nursery.NurseryScreen
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var eventRepository: EventRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheContentedestBabyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showExportScreen by remember { mutableStateOf(false) }
                    var showStatisticsScreen by remember { mutableStateOf(false) }

                    // Simple bottom nav across three tabs
                    var selectedTab by remember { mutableStateOf(0) } // 0: Timeline, 1: Growth, 2: Nursery

                    // Generate device ID once per app install
                    val deviceId = remember { 
                        android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "device-${System.currentTimeMillis()}"
                    }

                    // Schedule sync on app start
                    LaunchedEffect(Unit) {
                        SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                        // Trigger immediate sync to pull existing data
                        SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
                    }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("The Contentedest Baby") },
                                actions = {
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
                            )
                        },
                        bottomBar = {
                            if (!showExportScreen && !showStatisticsScreen) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = { Icon(Icons.Filled.Schedule, contentDescription = "Timeline") },
                                        label = { Text("Timeline") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = { Icon(Icons.Filled.TrendingUp, contentDescription = "Growth") },
                                        label = { Text("Growth") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = { Icon(Icons.Filled.Videocam, contentDescription = "Nursery") },
                                        label = { Text("Nursery") }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        if (showExportScreen) {
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
                                    onForceRepair = null, // No longer needed
                                    onForceSync = {
                                        SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
                                    }
                                )
                            }
                        } else {
                            // Main tabs content
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
                                    NurseryScreen(
                                        streamUrl = "http://192.168.86.3:1984/stream.html?src=hubble_android",
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
}
