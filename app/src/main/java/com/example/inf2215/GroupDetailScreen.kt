package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onCreateThread: (groupId: String) -> Unit,
    onOpenThread: (groupId: String, threadId: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val me = auth.currentUser?.uid

    var name by remember { mutableStateOf("Loading...") }
    var area by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(listOf<String>()) }
    var time by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var membersCount by remember { mutableStateOf(1) }
    var ownerId by remember { mutableStateOf("") } // Store owner ID

    var isMember by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    var threads by remember { mutableStateOf(listOf<GroupThread>()) }
    var threadsStatus by remember { mutableStateOf("") }

    // State for Members Dialog
    var showMembersDialog by remember { mutableStateOf(false) }
    var membersList by remember { mutableStateOf(listOf<Map<String, Any>>()) }

    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                if (doc == null || !doc.exists()) return@addSnapshotListener

                name = doc.getString("name") ?: "Unnamed Group"
                area = doc.getString("area") ?: ""
                days = doc.get("days") as? List<String> ?: emptyList()
                time = doc.getString("time") ?: ""
                description = doc.getString("description") ?: ""
                membersCount = (doc.getLong("membersCount") ?: 1L).toInt()
                ownerId = doc.getString("ownerId") ?: ""
            }
    }

    LaunchedEffect(groupId, me) {
        if (me == null) return@LaunchedEffect
        db.collection("groups").document(groupId)
            .collection("members").document(me)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                isMember = doc != null && doc.exists()
            }
    }

    // Fetch members when dialog is open
    LaunchedEffect(showMembersDialog) {
        if (showMembersDialog) {
            db.collection("groups").document(groupId).collection("members")
                .get()
                .addOnSuccessListener { result ->
                    membersList = result.documents.map { doc ->
                        doc.data ?: emptyMap()
                    }
                }
        }
    }

    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId)
            .collection("threads")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    threadsStatus = "Error loading threads: ${e.message}"
                    return@addSnapshotListener
                }

                threadsStatus = ""
                threads = snap?.documents?.map { d ->
                    // Parse Route manually from HashMaps
                    val rawRoute = d.get("route") as? List<Map<String, Any>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        val lat = (it["lat"] as? Number)?.toDouble()
                        val lng = (it["lng"] as? Number)?.toDouble()
                        if (lat != null && lng != null) LatLng(lat, lng) else null
                    } ?: emptyList()

                    GroupThread(
                        id = d.id,
                        title = d.getString("title") ?: "Untitled",
                        body = d.getString("body") ?: "",
                        createdById = d.getString("createdById") ?: "",
                        createdByName = d.getString("createdByName") ?: "Unknown",
                        createdAt = d.getTimestamp("createdAt"),
                        commentsCount = (d.getLong("commentsCount") ?: 0L).toInt(),

                        // Map the new fields
                        type = d.getString("type") ?: "NORMAL",
                        runDistance = d.getString("runDistance"),
                        runDuration = d.getString("runDuration"),
                        runPace = d.getString("runPace"),
                        route = parsedRoute
                    )
                } ?: emptyList()
            }
    }

    val canAct = me != null && !working

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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Clickable Member Count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showMembersDialog = true }
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$membersCount ${if (membersCount == 1) "member" else "members"}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Group Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Details
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (area.isNotBlank()) {
                                DetailRow(
                                    icon = Icons.Default.LocationOn,
                                    label = "Location",
                                    value = area
                                )
                            }

                            if (days.isNotEmpty()) {
                                DetailRow(
                                    icon = Icons.Default.CalendarMonth,
                                    label = "Days",
                                    value = days.joinToString(", ")
                                )
                            }

                            if (time.isNotBlank()) {
                                DetailRow(
                                    icon = Icons.Default.Schedule,
                                    label = "Time",
                                    value = time
                                )
                            }
                        }

                        if (description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "About",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = description,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Join/Leave Button
                        Button(
                            onClick = {
                                if (me == null) return@Button
                                working = true

                                val groupRef = db.collection("groups").document(groupId)
                                val memberRef = groupRef.collection("members").document(me)

                                if (!isMember) {
                                    db.collection("users").document(me).get()
                                        .addOnSuccessListener { userDoc ->
                                            val displayName = userDoc.getString("displayName") ?: "Runner"
                                            val memberData = hashMapOf(
                                                "role" to "member", // Default role
                                                "joinedAt" to Timestamp.now(),
                                                "displayName" to displayName,
                                                "userId" to me // Explicitly store ID
                                            )

                                            db.runBatch { batch ->
                                                batch.set(memberRef, memberData)
                                                batch.update(groupRef, "membersCount", FieldValue.increment(1))
                                            }.addOnSuccessListener { working = false }
                                                .addOnFailureListener { working = false }
                                        }
                                        .addOnFailureListener { working = false }
                                } else {
                                    // Prevent owner from leaving if they are the only one?
                                    // For now, just allow leave
                                    db.runBatch { batch ->
                                        batch.delete(memberRef)
                                        batch.update(groupRef, "membersCount", FieldValue.increment(-1))
                                    }.addOnSuccessListener { working = false }
                                        .addOnFailureListener { working = false }
                                }
                            },
                            enabled = canAct,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (working) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        if (isMember) Icons.Default.ExitToApp else Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    when {
                                        working -> "Please wait..."
                                        isMember -> "Leave Group"
                                        else -> "Join Group"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Threads Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Discussion Threads",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${threads.size} ${if (threads.size == 1) "thread" else "threads"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isMember) {
                        FilledTonalButton(
                            onClick = { onCreateThread(groupId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New Thread", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (!isMember && threads.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Join this group to participate in discussions",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (threadsStatus.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = threadsStatus,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (threads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "No threads yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isMember) {
                                Text(
                                    text = "Start the conversation!",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(threads) { thread ->
                    ThreadCard(
                        thread = thread,
                        onClick = { onOpenThread(groupId, thread.id) }
                    )
                }
            }
        }
    }

    // Members Dialog
    if (showMembersDialog) {
        Dialog(onDismissRequest = { showMembersDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Group Members",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (membersList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(membersList) { member ->
                                val mName = member["displayName"] as? String ?: "Unknown"
                                val mRole = member["role"] as? String ?: "member"
                                val isLeader = (ownerId.isNotBlank() && member["userId"] == ownerId) || mRole == "owner"

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if(isLeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if(isLeader) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = mName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                        if (isLeader) {
                                            Text(
                                                text = "Group Leader",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showMembersDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ThreadCard(thread: GroupThread, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HEADER: Author Name + Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text = thread.createdByName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (thread.type == "RUN") "shared a run" else "started a discussion",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CONTENT: Title & Body
            Text(
                text = thread.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (thread.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = thread.body,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // RICH MEDIA: Run Map & Stats
            if (thread.type == "RUN" && thread.route.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Run Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    // Calculate bounds for camera
                    val cameraState = rememberCameraPositionState()
                    LaunchedEffect(thread.route) {
                        if (thread.route.isNotEmpty()) {
                            val bounds = LatLngBounds.builder().apply {
                                thread.route.forEach { include(it) }
                            }.build()
                            try {
                                cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                            } catch (e: Exception) {
                                cameraState.move(CameraUpdateFactory.newLatLngZoom(thread.route.first(), 15f))
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
                        Polyline(
                            points = thread.route,
                            color = Color(0xFFFF4500),
                            width = 10f
                        )
                    }
                }

                // Stats Grid
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RunStatItem(label = "Distance", value = thread.runDistance ?: "-")
                    RunStatItem(label = "Time", value = thread.runDuration ?: "-")
                    RunStatItem(label = "Pace", value = thread.runPace ?: "-")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // FOOTER: Comments count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${thread.commentsCount} comments",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Helper Composable for stats
@Composable
fun RunStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}