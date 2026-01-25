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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*

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
            post = post,
            onDismiss = { showReportDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(post: FeedPost, onDismiss: () -> Unit) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    
    var reasonExpanded by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("") }
    
    var description by remember { mutableStateOf("") }

    val categories = listOf("Activity Integrity", "Social Conduct", "Content Safety")
    val reasons = mapOf(
        "Activity Integrity" to listOf("Incorrect Activity Type", "Cheating / Faked Data", "Duplicate / Spam Entry"),
        "Social Conduct" to listOf("Harrassment / Bullying", "Hate Speech", "Privacy & Impersonation"),
        "Content Safety" to listOf("Sexual / Violent Contents", "Scams / Bots", "Dangerous Behaviours")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    selectedReason = ""
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Reason Dropdown
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded && selectedCategory.isNotEmpty(),
                    onExpandedChange = { if (selectedCategory.isNotEmpty()) reasonExpanded = !reasonExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedReason,
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedCategory.isNotEmpty(),
                        label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded && selectedCategory.isNotEmpty(),
                        onDismissRequest = { reasonExpanded = false }
                    ) {
                        reasons[selectedCategory]?.forEach { reason ->
                            DropdownMenuItem(
                                text = { Text(reason) },
                                onClick = {
                                    selectedReason = reason
                                    reasonExpanded = false
                                }
                            )
                        }
                    }
                }

                // Description Text Box
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Enter description here") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val db = FirebaseFirestore.getInstance()
                    val reportData = hashMapOf(
                        "targetId" to post.id,
                        "targetType" to 1, // 1 for Post
                        "targetTitle" to post.title.ifBlank { "Untitled Post" },
                        "category" to selectedCategory,
                        "reason" to selectedReason,
                        "description" to description,
                        "timestamp" to Timestamp.now(),
                        "reportedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "Unknown")
                    )
                    db.collection("reports").add(reportData).addOnSuccessListener {
                        onDismiss()
                    }
                },
                enabled = selectedReason.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Report", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
