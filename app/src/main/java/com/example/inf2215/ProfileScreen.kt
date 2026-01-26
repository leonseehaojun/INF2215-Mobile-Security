package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "ProfileScreen"

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
    var displayName by remember { mutableStateOf("Loading...") }
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

        // Load Recent Runs
        db.collection("posts")
            .whereEqualTo("userId", uid)
            .whereEqualTo("type", "RUN")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    status = "Error loading runs: ${e.message}"
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.map { doc ->
                    // Parse the Route Data
                    val rawRoute = doc.get("route") as? List<Map<String, Double>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        if (it.containsKey("lat") && it.containsKey("lng")) {
                            LatLng(it["lat"]!!, it["lng"]!!)
                        } else null
                    } ?: emptyList()

                    ProfileRunItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled Run",
                        distanceStr = doc.getString("runDistance") ?: "0 km",
                        durationStr = doc.getString("runDuration") ?: "00:00",
                        route = parsedRoute,
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Picture Placeholder (Matching Admin style)
        Surface(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Name and Email
        Text(text = displayName, style = MaterialTheme.typography.headlineSmall)
        Text(text = email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Box (Rounded Edge Box)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatColumn("Age", age?.toString() ?: "-")
                StatDivider()
                StatColumn("Height", if (heightCm != null) "${heightCm}cm" else "-")
                StatDivider()
                StatColumn("Weight", if (weightKg != null) "${weightKg}kg" else "-")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Activity Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Recent Activity", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
        }

        if (recentRuns.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No runs recorded yet.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(recentRuns) { run ->
                    RunHistoryCard(run)
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
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

            // Map View if route exists
            if (run.route.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(run.route.last(), 14f)
                        },
                        googleMapOptionsFactory = {
                            GoogleMapOptions().liteMode(true)
                        },
                        uiSettings = MapUiSettings(zoomControlsEnabled = false)
                    ) {
                        Polyline(
                            points = run.route,
                            color = Color.Red,
                            width = 8f
                        )
                    }
                }
            }

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

                Column(horizontalAlignment = Alignment.End) {
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
