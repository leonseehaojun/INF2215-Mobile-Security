package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(posts) { post -> PostCard(post) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(post: FeedPost) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: User Icon, Name, and 3-dots Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = post.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.offset(x = 12.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report") },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Title Row: Title and Run Badge (same level)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (post.title.isNotBlank()) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                
                if (post.type == "RUN") {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("RUN") }
                }
            }

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
                        Polyline(points = post.route, color = Color.Red, width = 10f)
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
                    Column {
                        Text(post.runDistance, style = MaterialTheme.typography.titleMedium)
                        Text("Distance", style = MaterialTheme.typography.labelSmall)
                    }
                    Column {
                        Text(post.runDuration ?: "00:00", style = MaterialTheme.typography.titleMedium)
                        Text("Time", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportDialog(
            targetUserId = post.userId,
            targetType = 1, // 1 for Post
            attachedId = post.id,
            onDismiss = { showReportDialog = false }
        )
    }
}
