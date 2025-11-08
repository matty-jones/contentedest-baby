package com.contentedest.baby

import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
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
import com.contentedest.baby.ui.stats.QuickStatsBar
import com.contentedest.baby.ui.growth.GrowthScreen
import com.contentedest.baby.ui.nursery.NurseryScreen
import com.contentedest.baby.update.UpdateChecker
import com.contentedest.baby.update.UpdateResult
import com.contentedest.baby.ui.update.UpdateDialog
import com.contentedest.baby.ui.update.UpdateProgressDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheContentedestBabyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showExportScreen by remember { mutableStateOf(false) }
                    var showStatisticsScreen by remember { mutableStateOf(false) }

                    // Simple bottom nav across three tabs
                    var selectedTab by remember { mutableStateOf(0) } // 0: Timeline, 1: Growth, 2: Nursery
                    
                    // Detect orientation
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    
                    // Auto-rotate to landscape when Nursery tab is selected
                    LaunchedEffect(selectedTab) {
                        if (selectedTab == 2) {
                            // Nursery tab - force landscape
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            // Other tabs - allow auto-rotation
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }

                    // Generate device ID once per app install
                    val deviceId = remember { 
                        android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "device-${System.currentTimeMillis()}"
                    }

                    // Update checking state
                    var updateInfo by remember { mutableStateOf<com.contentedest.baby.net.UpdateInfoResponse?>(null) }
                    var showUpdateDialog by remember { mutableStateOf(false) }
                    var showProgressDialog by remember { mutableStateOf(false) }
                    var updateProgress by remember { mutableStateOf<Float?>(null) }
                    val coroutineScope = rememberCoroutineScope()

                    // Schedule sync on app start
                    LaunchedEffect(Unit) {
                        SyncWorker.schedulePeriodicSync(this@MainActivity, deviceId)
                        // Trigger immediate sync to pull existing data
                        SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
                        
                        // Check for updates
                        coroutineScope.launch {
                            val info = updateChecker.checkForUpdate()
                            if (info != null) {
                                updateInfo = info
                                showUpdateDialog = true
                            }
                        }
                    }

                    // Update dialogs
                    updateInfo?.let { info ->
                        if (showUpdateDialog) {
                            UpdateDialog(
                                updateInfo = info,
                                isMandatory = info.mandatory,
                                onUpdate = {
                                    showUpdateDialog = false
                                    showProgressDialog = true
                                    coroutineScope.launch {
                                        val result = updateChecker.performUpdate(this@MainActivity)
                                        showProgressDialog = false
                                        when (result) {
                                            is UpdateResult.InstallStarted -> {
                                                // Installation intent started, app will close
                                            }
                                            is UpdateResult.DownloadFailed -> {
                                                // Show error - could add error dialog here
                                                android.util.Log.e("MainActivity", "Update download failed")
                                            }
                                            is UpdateResult.InstallFailed -> {
                                                // Show error - could add error dialog here
                                                android.util.Log.e("MainActivity", "Update install failed")
                                            }
                                            is UpdateResult.NoUpdateAvailable -> {
                                                // Shouldn't happen here
                                            }
                                        }
                                    }
                                },
                                onDismiss = {
                                    showUpdateDialog = false
                                }
                            )
                        }
                    }
                    
                    if (showProgressDialog) {
                        UpdateProgressDialog(
                            progress = updateProgress,
                            message = "Downloading update..."
                        )
                    }

                    if (isLandscape && selectedTab == 2) {
                        // Landscape mode with Nursery - use side navigation
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail {
                                NavigationRailItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Filled.Schedule, contentDescription = "Timeline") },
                                    label = { Text("Timeline") }
                                )
                                NavigationRailItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Filled.TrendingUp, contentDescription = "Growth") },
                                    label = { Text("Growth") }
                                )
                                NavigationRailItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Filled.Videocam, contentDescription = "Nursery") },
                                    label = { Text("Nursery") }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                NurseryScreen(
                                    streamUrl = "http://192.168.86.3:1984/stream.html?src=hubble_android",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        // Portrait mode or other tabs - use standard Scaffold
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
                                    Column {
                                        // Only show QuickStatsBar on Timeline tab
                                        if (selectedTab == 0) {
                                            QuickStatsBar(
                                                eventRepository = eventRepository,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
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
                                    // Nursery screen - only shown in portrait mode here
                                    // (landscape mode is handled above)
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
}
