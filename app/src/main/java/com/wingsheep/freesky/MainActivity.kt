package com.wingsheep.freesky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingsheep.freesky.ui.registration.RegistrationScreen
import com.wingsheep.freesky.model.RegistrationUiState
import com.wingsheep.freesky.ui.registration.RegistrationViewModel
import com.wingsheep.freesky.ui.theme.FreeskyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeskyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: RegistrationViewModel = viewModel()
                    val state by viewModel.state.collectAsState()

                    if (state is RegistrationUiState.Registered) {
                        Greeting(
                            name = "Freesky",
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        RegistrationScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FreeskyTheme {
        Greeting("Freesky")
    }
}