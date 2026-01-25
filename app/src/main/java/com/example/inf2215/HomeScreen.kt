package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import androidx.compose.ui.graphics.Color

data class FeedPost(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val title: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val type: String = "NORMAL",
    val runDistance: String? = null,
    val runDuration: String? = null,
    val route: List<LatLng> = emptyList(),
    val createdAt: Timestamp? = null
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onGoProfile: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToTrackRun: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var posts by remember { mutableStateOf(listOf<FeedPost>()) }

    // Live feed listener
    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                posts = snapshot?.documents?.map { doc ->
                    val rawRoute = doc.get("route") as? List<Map<String, Double>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        if (it.containsKey("lat") && it.containsKey("lng")) {
                            LatLng(it["lat"]!!, it["lng"]!!)
                        } else null
                    } ?: emptyList()

                    FeedPost(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        displayName = doc.getString("displayName") ?: "Unknown",
                        title = doc.getString("title") ?: "",
                        text = doc.getString("text") ?: "",
                        imageUrl = doc.getString("imageUrl"),
                        type = doc.getString("type") ?: "NORMAL",
                        runDistance = doc.getString("runDistance"),
                        runDuration = doc.getString("runDuration"),
                        route = parsedRoute,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
            }
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Community Feed", style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(posts) { post -> PostCard(post) }
        }
    }
}

@Composable
fun PostCard(post: FeedPost) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name & Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                if (post.type == "RUN") Badge { Text("RUN") }
            }
            Spacer(Modifier.height(8.dp))

            // Title (Bold)
            if (post.title.isNotBlank()) Text(post.title, style = MaterialTheme.typography.titleLarge)

            // Image (If exists)
            if (post.imageUrl != null) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // GMap Display
            if (post.type == "RUN" && post.route.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(post.route.last(), 15f)
                        },
                        googleMapOptionsFactory = {
                            GoogleMapOptions().liteMode(true)
                        },
                        uiSettings = MapUiSettings(zoomControlsEnabled = false)
                    ) {
                        Polyline(
                            points = post.route,
                            color = Color.Red,
                            width = 10f
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(post.text, style = MaterialTheme.typography.bodyMedium)

            // Run Stats
            if (post.type == "RUN" && post.runDistance != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column { Text(post.runDistance, style = MaterialTheme.typography.titleMedium); Text("Distance", style = MaterialTheme.typography.labelSmall) }
                    Column { Text(post.runDuration ?: "00:00", style = MaterialTheme.typography.titleMedium); Text("Time", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}