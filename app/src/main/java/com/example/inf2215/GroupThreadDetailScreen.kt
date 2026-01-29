package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions

@Composable
fun GroupThreadDetailScreen(
    groupId: String,
    threadId: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val me = auth.currentUser?.uid

    var title by remember { mutableStateOf("Loading...") }
    var body by remember { mutableStateOf("") }
    var createdByName by remember { mutableStateOf("") }
    var commentsCount by remember { mutableStateOf(0) }

    var type by remember { mutableStateOf("NORMAL") }
    var runDistance by remember { mutableStateOf<String?>(null) }
    var runDuration by remember { mutableStateOf<String?>(null) }
    var runPace by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    var comments by remember { mutableStateOf(listOf<GroupThreadComment>()) }
    var newComment by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    // Member Check State
    var isMember by remember { mutableStateOf(false) }

    val threadRef = remember(groupId, threadId) {
        db.collection("groups").document(groupId)
            .collection("threads").document(threadId)
    }

    // Check Membership
    LaunchedEffect(groupId, me) {
        if (me != null) {
            db.collection("groups").document(groupId)
                .collection("members").document(me)
                .addSnapshotListener { doc, _ ->
                    isMember = doc != null && doc.exists()
                }
        }
    }

    DisposableEffect(groupId, threadId) {
        val regHeader = threadRef.addSnapshotListener { doc, e ->
            if (e != null) {
                status = "Error loading thread: ${e.message}"
                title = "Error"
                return@addSnapshotListener
            }

            if (doc == null || !doc.exists()) {
                status = "Thread not found"
                title = "Thread not found"
                return@addSnapshotListener
            }

            status = ""
            title = doc.getString("title") ?: "Untitled"
            body = doc.getString("body") ?: ""
            createdByName = doc.getString("createdByName") ?: "Unknown"
            commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt()

            // Parse Run Data
            type = doc.getString("type") ?: "NORMAL"
            runDistance = doc.getString("runDistance")
            runDuration = doc.getString("runDuration")
            runPace = doc.getString("runPace")

            val rawRoute = doc.get("route") as? List<Map<String, Any>>
            route = rawRoute?.mapNotNull {
                val lat = (it["lat"] as? Number)?.toDouble()
                val lng = (it["lng"] as? Number)?.toDouble()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            } ?: emptyList()
        }

        // Comments listener
        val regComments = threadRef.collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    comments = emptyList()
                    return@addSnapshotListener
                }

                comments = snap?.documents?.map { d ->
                    GroupThreadComment(
                        id = d.id,
                        userId = d.getString("userId") ?: "",
                        displayName = d.getString("displayName") ?: "User",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt")
                    )
                } ?: emptyList()
            }

        onDispose {
            regHeader.remove()
            regComments.remove()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Discussion",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$commentsCount ${if (commentsCount == 1) "reply" else "replies"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }

        // Thread and Comments List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Original Thread Post
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // User Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = createdByName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (type == "RUN") {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "RUN",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        //  Run Map and Stats
                        if (type == "RUN" && route.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                val cameraState = rememberCameraPositionState()
                                LaunchedEffect(route) {
                                    if (route.isNotEmpty()) {
                                        val bounds = LatLngBounds.builder().apply {
                                            route.forEach { include(it) }
                                        }.build()
                                        try {
                                            cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                                        } catch (e: Exception) {
                                            cameraState.move(CameraUpdateFactory.newLatLngZoom(route.first(), 15f))
                                        }
                                    }
                                }

                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraState,
                                    googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                                ) {
                                    Polyline(points = route, color = Color(0xFFFF4500), width = 10f)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Distance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(runDistance ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(runDuration ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Pace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(runPace ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        if (body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = body,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Comments Header
            if (comments.isNotEmpty()) {
                item {
                    Text(
                        text = "Replies",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Comments
            items(comments) { comment ->
                CommentCard(comment = comment, isCurrentUser = comment.userId == me)
            }

            // Empty State
            if (comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No replies yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Be the first to reply!",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Comment Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (isMember) "Write a reply..." else "Join group to reply")
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = isMember // Disable if not member
                )

                FilledIconButton(
                    onClick = {
                        if (me == null || !isMember) return@FilledIconButton
                        val text = newComment.trim()
                        if (text.isBlank()) return@FilledIconButton

                        db.collection("users").document(me).get().addOnSuccessListener { userDoc ->
                            val displayName = userDoc.getString("displayName") ?: "Runner"
                            val now = Timestamp.now()

                            val data = hashMapOf(
                                "userId" to me,
                                "displayName" to displayName,
                                "text" to text,
                                "createdAt" to now
                            )

                            threadRef.collection("comments").add(data)
                                .addOnSuccessListener {
                                    threadRef.update(
                                        mapOf(
                                            "commentsCount" to FieldValue.increment(1),
                                            "lastActivityAt" to now
                                        )
                                    )
                                    newComment = ""
                                }
                        }
                    },
                    enabled = isMember && newComment.trim().isNotBlank() && title != "Thread not found",
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

@Composable
fun CommentCard(comment: GroupThreadComment, isCurrentUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isCurrentUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = comment.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val date = comment.createdAt?.toDate()?.let {
                        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                    } ?: ""
                    if (date.isNotBlank()) {
                        Text(
                            text = date,
                            fontSize = 12.sp,
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = comment.text,
                    fontSize = 14.sp,
                    color = if (isCurrentUser)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
        }
    }
}