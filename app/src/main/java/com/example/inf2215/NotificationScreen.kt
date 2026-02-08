package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationScreen(
    onAnnouncementClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    var announcements by remember { mutableStateOf(listOf<Announcement>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val now = Timestamp.now()
                announcements = snapshot?.documents?.mapNotNull { doc ->
                    val ann = doc.toObject(Announcement::class.java)?.copy(id = doc.id)
                    if (ann != null) {
                        // Filter based on date range
                        val isWithinDateRange = if (ann.isIndefinite) {
                            true
                        } else {
                            val start = ann.startDate
                            val end = ann.endDate
                            (start == null || start <= now) && (end == null || end >= now)
                        }
                        
                        if (isWithinDateRange) ann else null
                    } else null
                } ?: emptyList()
                isLoading = false
            }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (announcements.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No new notifications",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(announcements) { announcement ->
                val userId = auth.currentUser?.uid
                val isRead = userId != null && announcement.readBy.contains(userId)

                NotificationAnnouncementCard(
                    announcement = announcement,
                    isRead = isRead,
                    onClick = {
                        if (userId != null && !isRead) {
                            db.collection("announcements").document(announcement.id)
                                .update("readBy", FieldValue.arrayUnion(userId))
                        }
                        onAnnouncementClick(announcement.id)
                    }
                )
            }
        }
    }
}

@Composable
fun NotificationAnnouncementCard(
    announcement: Announcement,
    isRead: Boolean,
    onClick: () -> Unit
) {
    val (icon, color) = getCategoryStyle(announcement.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = if (isRead) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        else CardDefaults.cardColors(),
        elevation = if (isRead) CardDefaults.cardElevation(0.dp) else CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isRead) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(4.dp))
                
                val dateStr = announcement.createdAt?.toDate()?.let {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                } ?: ""
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun AnnouncementDetailScreen(
    announcementId: String,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var announcement by remember { mutableStateOf<Announcement?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(announcementId) {
        db.collection("announcements").document(announcementId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                announcement = snapshot.toObject(Announcement::class.java)?.copy(id = snapshot.id)
            } else {
                announcement = null
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (announcement == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Announcement not found.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
        }
    } else {
        val ann = announcement!!
        val (icon, color) = getCategoryStyle(ann.type)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(ann.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val dateStr = ann.createdAt?.toDate()?.let {
                            SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(it)
                        } ?: ""
                        Text(dateStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }

                HorizontalDivider()

                Text(
                    text = ann.description,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )

                if (onEdit != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Announcement")
                    }
                    
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Announcement")
                    }
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Announcement?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        db.collection("announcements").document(announcementId).delete()
                            .addOnSuccessListener {
                                showDeleteConfirm = false
                                onBack()
                            }
                    }
                ) {
                    Text("Yes, Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
