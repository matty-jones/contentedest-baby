package com.contentedest.baby.ui.daily

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.contentedest.baby.data.local.EventEntity

@Composable
fun DailyLogScreen(vm: DailyLogViewModel) {
    val events by vm.events.collectAsState()
    Column {
        LazyColumn {
            items(events) { e: EventEntity ->
                Text(text = "${e.type} @ ${e.start_ts ?: e.ts}")
            }
        }
    }
}


