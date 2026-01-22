package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private const val TAG = "ProfileScreen"

data class RunItem(
    val id: String = "",
    val distanceKm: Double = 0.0,
    val durationMin: Int = 0,
    val createdAt: Timestamp? = null
)

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    val uid = auth.currentUser?.uid

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<Int?>(null) }
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var weightKg by remember { mutableStateOf<Double?>(null) }

    var runs by remember { mutableStateOf(listOf<RunItem>()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid == null) {
            status = "Not logged in"
            return@LaunchedEffect
        }

        // Load user profile once
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                displayName = doc.getString("displayName") ?: ""
                email = doc.getString("email") ?: ""
                age = doc.getLong("age")?.toInt()
                heightCm = doc.getLong("heightCm")?.toInt()
                weightKg = doc.getDouble("weightKg")
            }
            .addOnFailureListener { e ->
                status = "Profile load failed: ${e.message}"
                Log.e(TAG, "Profile load failed", e)
            }

        // Real-time recent runs
        db.collection("runs")
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    status = "Runs error: ${e.message}"
                    Log.e(TAG, "Runs listener error", e)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.map { doc ->
                    RunItem(
                        id = doc.id,
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        durationMin = (doc.getLong("durationMin") ?: 0L).toInt(),
                        createdAt = doc.getTimestamp("createdAt")?.let { Timestamp(it.seconds, it.nanoseconds) }
                    )
                } ?: emptyList()

                runs = list
            }
    }

    fun addSampleRun() {
        val user = auth.currentUser ?: run {
            status = "Not logged in"
            return
        }

        val run = hashMapOf(
            "userId" to user.uid,
            "distanceKm" to 5.0,
            "durationMin" to 30,
            "createdAt" to Timestamp.now()
        )

        db.collection("runs").add(run)
            .addOnSuccessListener { status = "Added sample run" }
            .addOnFailureListener { e -> status = "Add run failed: ${e.message}" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Logout") }
        }

        Text("Profile", style = MaterialTheme.typography.titleLarge)
        if (status.isNotBlank()) Text(status)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Name: $displayName")
                Text("Email: $email")
                Text("Age: ${age ?: "-"}")
                Text("Height: ${heightCm ?: "-"} cm")
                Text("Weight: ${weightKg ?: "-"} kg")
            }
        }

        Button(onClick = { addSampleRun() }, modifier = Modifier.fillMaxWidth()) {
            Text("Add sample run (for testing)")
        }

        Text("Recent runs", style = MaterialTheme.typography.titleMedium)

        if (runs.isEmpty()) {
            Text("No runs yet. Tap 'Add sample run' to test.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(runs) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Distance: ${r.distanceKm} km")
                            Text("Duration: ${r.durationMin} min")
                        }
                    }
                }
            }
        }
    }
}
