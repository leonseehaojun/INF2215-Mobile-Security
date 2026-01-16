package com.example.inf2215

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

private const val TAG = "FirebaseTest"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            INF2215Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FirebaseTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun FirebaseTestScreen(modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var email by remember { mutableStateOf("testuser1@example.com") }
    var password by remember { mutableStateOf("Password123!") }
    var status by remember { mutableStateOf("Not signed in") }

    fun saveProfile(uid: String) {
        val profile = hashMapOf(
            "email" to email,
            "displayName" to "Test User",
            "createdAt" to Timestamp.now()
        )

        db.collection("users").document(uid)
            .set(profile)
            .addOnSuccessListener {
                status = "✅ Firestore write OK (users/$uid)"
                Log.d(TAG, "Firestore write OK: users/$uid")
            }
            .addOnFailureListener { e ->
                status = "❌ Firestore write failed: ${e.message}"
                Log.e(TAG, "Firestore write failed", e)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Firebase Auth + Firestore Test", style = MaterialTheme.typography.titleLarge)
        Text(status)

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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: ""
                        status = "Registered. UID = $uid"
                        Log.d(TAG, "Registered: $uid")
                        if (uid.isNotBlank()) saveProfile(uid)
                    }
                    .addOnFailureListener { e ->
                        status = "Register failed: ${e.message}"
                        Log.e(TAG, "Register failed", e)
                    }
            }) {
                Text("Register")
            }

            Button(onClick = {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid ?: ""
                        status = "Logged in. UID = $uid"
                        Log.d(TAG, "Logged in: $uid")
                    }
                    .addOnFailureListener { e ->
                        status = "Login failed: ${e.message}"
                        Log.e(TAG, "Login failed", e)
                    }
            }) {
                Text("Login")
            }
        }

        Button(onClick = {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                status = "Not logged in yet. Register/Login first."
            } else {
                saveProfile(uid)
            }
        }) {
            Text("Save Profile to Firestore")
        }
    }
}
