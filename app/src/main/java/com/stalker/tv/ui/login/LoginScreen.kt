package com.stalker.tv.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onSubmit: (serverUrl: String, username: String, password: String) -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "StalkerTV", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))

            TvTextField(value = serverUrl, label = "Server URL", onValueChange = { serverUrl = it })
            Spacer(Modifier.height(12.dp))
            TvTextField(value = username, label = "Username", onValueChange = { username = it })
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password,
                label = "Password",
                onValueChange = { password = it },
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(24.dp))

            if (error != null) {
                Text(text = error, color = androidx.compose.ui.graphics.Color.Red)
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { onSubmit(serverUrl.trim(), username.trim(), password) },
                enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank()
            ) {
                Text(if (isLoading) "Signing in..." else "Sign In")
            }
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None
) {
    // androidx.tv.material3 has no TextField yet; use the standard Material one styled minimally.
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth()
    )
}
