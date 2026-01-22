package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "ProfileScreen"

data class ProfileRunItem(
    val id: String = "",
    val title: String = "",
    val distanceStr: String = "",
    val durationStr: String = "",
    val timestamp: Timestamp? = null
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

    // User Stats
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<Int?>(null) }
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var weightKg by remember { mutableStateOf<Double?>(null) }

    // Run History List
    var recentRuns by remember { mutableStateOf(listOf<ProfileRunItem>()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid == null) {
            status = "Not logged in"
            return@LaunchedEffect
        }

        // Load User Profile
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                displayName = doc.getString("displayName") ?: ""
                email = doc.getString("email") ?: ""
                age = doc.getLong("age")?.toInt()
                heightCm = doc.getLong("heightCm")?.toInt()
                weightKg = doc.getDouble("weightKg")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Profile load failed", e)
            }

        // Load Recent Runs from 'posts' collection (to get Titles)
        db.collection("posts")
            .whereEqualTo("userId", uid)
            .whereEqualTo("type", "RUN")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20) // Limit to last 20 runs
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    status = "Error loading runs: ${e.message}"
                    Log.e(TAG, "Runs listener error", e)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.map { doc ->
                    ProfileRunItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled Run",
                        distanceStr = doc.getString("runDistance") ?: "0 km",
                        durationStr = doc.getString("runDuration") ?: "00:00",
                        timestamp = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()

                recentRuns = list
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Info Card
        Text("Runner Profile", style = MaterialTheme.typography.headlineMedium)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayName.ifBlank { "Runner" }, style = MaterialTheme.typography.titleLarge)
                Text(email, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Age: ${age ?: "-"}")
                    Text("Height: ${heightCm ?: "-"} cm")
                    Text("Weight: ${weightKg ?: "-"} kg")
                }
            }
        }

        // Recent Runs Section
        Text("Recent Activity", style = MaterialTheme.typography.titleLarge)

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
        }

        if (recentRuns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No runs recorded yet.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recentRuns) { run ->
                    RunHistoryCard(run)
                }
            }
        }
    }
}

@Composable
fun RunHistoryCard(run: ProfileRunItem) {
    val dateStr = remember(run.timestamp) {
        run.timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(it)
        } ?: "Unknown Date"
    }

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Top Row: Date
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = run.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = run.distanceStr,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Distance", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = run.durationStr,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Duration", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
