package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
    var showPostTypeDialog by remember { mutableStateOf(false) }

    // Live feed listener
    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                posts = snapshot?.documents?.map { doc ->
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
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPostTypeDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "Add") }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(posts) { post -> PostCard(post) }
        }
    }

    if (showPostTypeDialog) {
        AlertDialog(
            onDismissRequest = { showPostTypeDialog = false },
            title = { Text("Create New") },
            text = { Text("What would you like to post?") },
            confirmButton = {
                TextButton(onClick = { showPostTypeDialog = false; onNavigateToTrackRun() }) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null); Spacer(Modifier.width(8.dp)); Text("Track Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostTypeDialog = false; onNavigateToCreatePost() }) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Text Post")
                }
            }
        )
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
            if (post.title.isNotBlank()) {
                Text(post.title, style = MaterialTheme.typography.titleLarge)
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
