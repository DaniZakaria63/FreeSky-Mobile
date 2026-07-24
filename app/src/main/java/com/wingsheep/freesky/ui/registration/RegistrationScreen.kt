package com.wingsheep.freesky.ui.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingsheep.freesky.model.RegistrationUiState

@Composable
fun RegistrationScreen(viewModel: RegistrationViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is RegistrationUiState.Checking -> {
                CircularProgressIndicator()
                Text("Connecting...", modifier = Modifier.padding(top = 16.dp))
            }
            is RegistrationUiState.NeedsRegistration -> {
                Text("Welcome to Freesky")
                Button(
                    onClick = { viewModel.register() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Register Device")
                }
            }
            is RegistrationUiState.Registered -> {
                Text("You are")
                Text(
                    s.name,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is RegistrationUiState.Error -> {
                Text("Error: ${s.message}")
                Button(
                    onClick = { viewModel.register() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Retry")
                }
            }
        }
    }
}