package com.tomczyn.ui.greeting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomczyn.ui.common.AppScreen
import com.tomczyn.ui.common.theme.ComposeArchTestTheme
import timber.log.Timber

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun GreetingScreen() {
    val viewModel: GreetingViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Timber.tag("Maciek").d("Screen: $state")
    AppScreen {
        Greeting("Android", state)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.updateFoo("250") }) {
            Text(text = "Set Foo to 250")
        }
    }
}

@Composable
fun Greeting(name: String, state: GreetingState) {
    Text(text = "Hello $name! Foo: ${state.foo} Bar: ${state.bar}", color = Color.White)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ComposeArchTestTheme { GreetingScreen() }
}
