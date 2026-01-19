package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private const val TAG = "RegisterScreen"

@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var displayName by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") } // cm
    var weightText by remember { mutableStateOf("") } // kg
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun isValid(): String? {
        if (displayName.isBlank()) return "Please enter a display name."

        val age = ageText.toIntOrNull() ?: return "Age must be a number."
        val height = heightText.toIntOrNull() ?: return "Height must be a number."
        val weight = weightText.toDoubleOrNull() ?: return "Weight must be a number."

        if (age !in 10..100) return "Age looks invalid."
        if (height !in 100..250) return "Height looks invalid."
        if (weight !in 30.0..250.0) return "Weight looks invalid."

        if (email.isBlank()) return "Please enter an email."
        if (password.length < 8) return "Password must be at least 8 characters."
        if (password != confirmPassword) return "Passwords do not match."

        return null
    }

    fun saveProfile(uid: String) {
        val profile = hashMapOf(
            "displayName" to displayName.trim(),
            "email" to email.trim().lowercase(),
            "age" to ageText.toInt(),
            "heightCm" to heightText.toInt(),
            "weightKg" to weightText.toDouble(),
            "role" to "public",
            "createdAt" to Timestamp.now()
        )

        db.collection("users").document(uid)
            .set(profile)
            .addOnSuccessListener {
                status = "Registered + profile saved!"
                isLoading = false
                Log.d(TAG, "Profile saved users/$uid")
                onRegistered() //go to landing page
            }
            .addOnFailureListener { e ->
                status = "Firestore failed: ${e.message}"
                isLoading = false
                Log.e(TAG, "Firestore save failed", e)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create your account", style = MaterialTheme.typography.titleLarge)
        if (status.isNotBlank()) Text(status)

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it },
                label = { Text("Age") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Height (cm)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight (kg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val err = isValid()
                if (err != null) {
                    status = "$err"
                    return@Button
                }

                isLoading = true
                status = "Registering..."

                auth.createUserWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        if (uid == null) {
                            status = "No UID returned."
                            isLoading = false
                        } else {
                            saveProfile(uid)
                        }
                    }
                    .addOnFailureListener { e ->
                        status = "Auth failed: ${e.message}"
                        isLoading = false
                        Log.e(TAG, "Auth register failed", e)
                    }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Creating..." else "Create account")
        }
        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Login")
        }
    }
}
