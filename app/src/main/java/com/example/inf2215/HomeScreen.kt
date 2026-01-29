package com.example.inf2215

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToThread: (String, String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val currentUserId = currentUser?.uid

    // Combined Feed
    var feedItems by remember { mutableStateOf(listOf<FeedPost>()) }

    // State to hold IDs for filtering
    var myGroupIds by remember { mutableStateOf(setOf<String>()) }
    var myFriendIds by remember { mutableStateOf(setOf<String>()) }

    // Fetch Joined Groups
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            db.collectionGroup("members")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val ids = snap.documents.mapNotNull { it.reference.parent.parent?.id }.toSet()
                        myGroupIds = ids
                    }
                }
        }
    }

    // Fetch Friends
    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            // Pointing to the 'friends' subcollection
            db.collection("users").document(currentUserId)
                .collection("friends")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener

                    val ids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                    myFriendIds = ids
                }
        }
    }

    // Fetch Posts (Self + Friends) AND Threads (Joined Groups)
    var rawPosts by remember { mutableStateOf(listOf<FeedPost>()) }
    var rawThreads by remember { mutableStateOf(listOf<FeedPost>()) }

    // Fetch Posts
    LaunchedEffect(currentUserId, myFriendIds) {
        if (currentUserId != null) {
            // Combine user ID + Friend IDs
            val usersToFetch = (myFriendIds + currentUserId).toList()

            // Firestore 'in' query limit is 10, for now take the top 10 to prevent crashes
            // Ideally need to chunk this or fetch all and filter client-side
            val safeUsersList = if (usersToFetch.size > 10) usersToFetch.take(10) else usersToFetch

            if (safeUsersList.isNotEmpty()) {
                db.collection("posts")
                    .whereIn("userId", safeUsersList)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) return@addSnapshotListener
                        rawPosts = snapshot?.documents?.map { doc ->
                            parseFeedPost(doc)
                        } ?: emptyList()
                    }
            } else {
                rawPosts = emptyList()
            }
        }
    }

    // Fetch Threads (From All Groups then filter by membership)
    LaunchedEffect(myGroupIds) {
        if (myGroupIds.isNotEmpty()) {
            db.collectionGroup("threads")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener

                    // Client-side filter: Only show threads from groups user is in
                    val threads = snapshot?.documents?.mapNotNull { doc ->
                        val groupId = doc.reference.parent.parent?.id
                        if (groupId != null && myGroupIds.contains(groupId)) {
                            parseGroupThreadAsPost(doc, groupId)
                        } else null
                    } ?: emptyList()

                    rawThreads = threads
                }
        } else {
            rawThreads = emptyList()
        }
    }

    // Merge and Sort
    LaunchedEffect(rawPosts, rawThreads) {
        val combined = (rawPosts + rawThreads)
            .sortedByDescending { it.createdAt }
            // If two posts have the same runId (and not null) keep only the first one
            .distinctBy { if (it.runId != null) it.runId else it.id }

        feedItems = combined
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (feedItems.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No posts yet. Add friends or join groups!", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        items(feedItems) { post ->
            PostCard(
                post = post,
                onClick = {
                    if (post.type == "THREAD" && post.groupId != null) {
                        onNavigateToThread(post.groupId, post.id)
                    } else {
                        onNavigateToPostDetail(post.id)
                    }
                },
                onCommentClick = {
                    if (post.type == "THREAD" && post.groupId != null) {
                        onNavigateToThread(post.groupId, post.id)
                    } else {
                        onNavigateToPostDetail(post.id)
                    }
                }
            )
        }
    }
}

// Helper to parse regular posts
fun parseFeedPost(doc: com.google.firebase.firestore.DocumentSnapshot): FeedPost {
    val rawRoute = doc.get("route") as? List<Map<String, Any>>
    val parsedRoute = rawRoute?.mapNotNull {
        val lat = (it["lat"] as? Number)?.toDouble()
        val lng = (it["lng"] as? Number)?.toDouble()
        if (lat != null && lng != null) LatLng(lat, lng) else null
    } ?: emptyList()

    return FeedPost(
        id = doc.id,
        userId = doc.getString("userId") ?: "",
        displayName = doc.getString("displayName") ?: "Unknown",
        title = doc.getString("title") ?: "",
        text = doc.getString("text") ?: "",
        imageUrl = doc.getString("imageUrl"),
        type = doc.getString("type") ?: "NORMAL",
        runDistance = doc.getString("runDistance"),
        runDuration = doc.getString("runDuration"),
        runPace = doc.getString("runPace"),
        route = parsedRoute,
        createdAt = doc.getTimestamp("createdAt"),
        likes = doc.get("likes") as? List<String> ?: emptyList(),
        commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
        runId = doc.getString("runId")
    )
}

// Helper to parse group threads into FeedPost format
fun parseGroupThreadAsPost(doc: com.google.firebase.firestore.DocumentSnapshot, groupId: String): FeedPost {
    // Parse the route manually
    val rawRoute = doc.get("route") as? List<Map<String, Any>>
    val parsedRoute = rawRoute?.mapNotNull {
        val lat = (it["lat"] as? Number)?.toDouble()
        val lng = (it["lng"] as? Number)?.toDouble()
        if (lat != null && lng != null) LatLng(lat, lng) else null
    } ?: emptyList()

    return FeedPost(
        id = doc.id,
        userId = doc.getString("createdById") ?: "",
        displayName = doc.getString("createdByName") ?: "Unknown",
        title = doc.getString("title") ?: "",
        text = doc.getString("body") ?: "",
        type = "THREAD",

        createdAt = doc.getTimestamp("createdAt"),
        commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
        groupId = groupId,

        // Populate Run Data
        runDistance = doc.getString("runDistance"),
        runDuration = doc.getString("runDuration"),
        runPace = doc.getString("runPace"),
        route = parsedRoute,
        runId = doc.getString("runId")
    )
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

    // Logic to determine if should show map/stats
    val hasRunData = post.route.isNotEmpty() || post.runDistance != null

    Column(modifier = Modifier.padding(16.dp)) {
        // Header Row
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (post.type == "THREAD") {
                    Text(
                        text = "Group Discussion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

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

        // Title Row
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

            if (post.type == "RUN" || (post.type == "THREAD" && hasRunData)) {
                Spacer(Modifier.width(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("RUN", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Show THREAD badge only if its a thread and NOT a run
            if (post.type == "THREAD" && !hasRunData) {
                Spacer(Modifier.width(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text("THREAD") }
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

        // Map Display Update
        // Check if route exists
        if (post.route.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))) {
                val cameraState = rememberCameraPositionState()
                LaunchedEffect(post.route) {
                    if (post.route.isNotEmpty()) {
                        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
                        post.route.forEach { boundsBuilder.include(it) }
                        val bounds = boundsBuilder.build()
                        try {
                            cameraState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(bounds, 50))
                        } catch (e: Exception) {
                            cameraState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(post.route.last(), 15f))
                        }
                    }
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    googleMapOptionsFactory = {
                        GoogleMapOptions().liteMode(true)
                    },
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    Polyline(points = post.route, color = Color(0xFFFF4500), width = 10f)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(post.text, style = MaterialTheme.typography.bodyMedium)

        // Stats Display Update
        // Check if distance exists
        if (post.runDistance != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(post.runDistance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(post.runDuration ?: "00:00", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (post.runPace != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(post.runPace, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Pace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Like and Comment Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Button
            if (post.type != "THREAD") {
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
            } else {
                Spacer(Modifier.width(8.dp))
            }

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

            // Date
            val dateStr = post.createdAt?.toDate()?.let {
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
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
            targetType = if(post.type == "THREAD") 3 else 1,
            attachedId = post.id,
            onDismiss = { showReportDialog = false }
        )
    }
}