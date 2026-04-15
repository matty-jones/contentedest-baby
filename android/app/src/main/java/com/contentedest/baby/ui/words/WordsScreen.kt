package com.contentedest.baby.ui.words

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contentedest.baby.data.local.BabyWordEntity
import com.contentedest.baby.data.repo.WordRepository
import com.contentedest.baby.sync.SyncWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(
    wordRepository: WordRepository,
    deviceId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var words by remember { mutableStateOf<List<BabyWordEntity>>(emptyList()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<BabyWordEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            words = wordRepository.getAllOrderedByFirstUseDesc()
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val lineColor = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("List") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Graph") }
                )
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Add word")
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> WordsListView(
                        words = words,
                        onWordClick = { word ->
                            selectedWord = word
                            showEditDialog = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        WordsGraphView(
                            words = words,
                            lineColor = lineColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWordDialog(
            wordRepository = wordRepository,
            deviceId = deviceId,
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                reload()
            }
        )
    }

    if (showEditDialog && selectedWord != null) {
        EditWordDialog(
            wordRepository = wordRepository,
            wordEntity = selectedWord!!,
            onDismiss = {
                showEditDialog = false
                selectedWord = null
            },
            onSaved = {
                showEditDialog = false
                selectedWord = null
                reload()
                SyncWorker.triggerImmediateSync(context, deviceId)
            },
            onDeleted = {
                showEditDialog = false
                selectedWord = null
                reload()
                SyncWorker.triggerImmediateSync(context, deviceId)
            }
        )
    }
}
