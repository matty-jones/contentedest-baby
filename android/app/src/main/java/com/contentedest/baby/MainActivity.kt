package com.contentedest.baby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.contentedest.baby.net.TokenStorage
import com.contentedest.baby.ui.daily.DailyLogScreen
import com.contentedest.baby.ui.daily.DailyLogViewModel
import com.contentedest.baby.ui.pairing.PairingScreen
import com.contentedest.baby.ui.pairing.PairingViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val hasToken = remember { mutableStateOf(tokenStorage.getToken() != null) }
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(title = { Text("The Contentedest Baby") })
                    if (!hasToken.value) {
                        val vm: PairingViewModel = hiltViewModel()
                        PairingScreen(vm)
                        LaunchedEffect(Unit) {
                            vm.paired.collect { paired -> if (paired) hasToken.value = true }
                        }
                    } else {
                        val vm: DailyLogViewModel = hiltViewModel()
                        LaunchedEffect(Unit) { vm.load(LocalDate.now()) }
                        DailyLogScreen(vm)
                    }
                }
            }
        }
    }
}


