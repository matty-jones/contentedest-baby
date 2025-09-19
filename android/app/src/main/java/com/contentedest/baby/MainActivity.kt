package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.contentedest.baby.ui.daily.DailyLogScreen
import com.contentedest.baby.ui.daily.DailyLogViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val vm: DailyLogViewModel = hiltViewModel()
                LaunchedEffect(Unit) { vm.load(LocalDate.now()) }
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(title = { Text("The Contentedest Baby") })
                    DailyLogScreen(vm)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { /* TODO: quick actions */ }) { Text("Start Sleep") }
                        Button(onClick = { /* TODO */ }) { Text("Add Feed") }
                        Button(onClick = { /* TODO */ }) { Text("Add Nappy") }
                    }
                }
            }
        }
    }
}


