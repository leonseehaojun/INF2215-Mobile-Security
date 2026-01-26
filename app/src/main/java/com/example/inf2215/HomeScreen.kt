package com.example.inf2215

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onGoProfile: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToTrackRun: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit
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
                    val rawRoute = doc.get("route") as? List<Map<String, Any>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        val lat = (it["lat"] as? Number)?.toDouble()
                        val lng = (it["lng"] as? Number)?.toDouble()
                        if (lat != null && lng != null) LatLng(lat, lng) else null
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
                        createdAt = doc.getTimestamp("createdAt"),
                        likes = doc.get("likes") as? List<String> ?: emptyList(),
                        commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
                    )
                } ?: emptyList()
            }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(posts) { post -> 
            PostCard(
                post = post, 
                onClick = { onNavigateToPostDetail(post.id) },
                onCommentClick = { onNavigateToPostDetail(post.id) }
            ) 
        }
    }
}

@Composable
fun PostCard(
    post: FeedPost,
    onClick: () -> Unit,
    onCommentClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }, 
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        PostContent(post = post, onCommentClick = onCommentClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostContent(
    post: FeedPost,
    onCommentClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val isLiked = post.likes.contains(currentUserId)

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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        
        // Like and Comment Buttons + Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Button
            TextButton(
                onClick = {
                    if (currentUserId.isNotEmpty()) {
                        val ref = db.collection("posts").document(post.id)
                        if (isLiked) {
                            ref.update("likes", FieldValue.arrayRemove(currentUserId))
                        } else {
                            ref.update("likes", FieldValue.arrayUnion(currentUserId))
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = post.likes.size.toString(),
                    color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))

            // Comment Button
            TextButton(onClick = onCommentClick) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = post.commentsCount.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            // Creation Date & Time
            val dateStr = post.createdAt?.toDate()?.let {
                SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(it)
            } ?: ""
            
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
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
