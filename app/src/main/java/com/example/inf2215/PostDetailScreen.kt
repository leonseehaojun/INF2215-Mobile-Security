package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    highlightCommentId: String? = null,
    isAdminReview: Boolean = false,
    reportId: String? = null
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid
    var post by remember { mutableStateOf<FeedPost?>(null) }
    var comments by remember { mutableStateOf(listOf<Comment>()) }
    var newCommentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Fetch Post and Comments
    LaunchedEffect(postId) {
        db.collection("posts").document(postId).addSnapshotListener { snapshot, _ ->
            snapshot?.let { doc ->
                if (doc.exists()) {
                    val rawRoute = doc.get("route") as? List<Map<String, Any>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        val lat = (it["lat"] as? Number)?.toDouble()
                        val lng = (it["lng"] as? Number)?.toDouble()
                        if (lat != null && lng != null) LatLng(lat, lng) else null
                    } ?: emptyList()

                    post = FeedPost(
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
                } else if (!isAdminReview) {
                    // Go back if post is deleted while viewing (unless admin is reviewing)
                    onBack()
                }
            }
        }

        db.collection("comments")
            .whereEqualTo("postId", postId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("PostDetail", "Error fetching comments", e)
                    return@addSnapshotListener
                }
                comments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    // Organize comments into threads
    val organizedComments = remember(comments) {
        val topLevel = comments.filter { it.parentId == null }.sortedBy { it.timestamp }
        val replies = comments.filter { it.parentId != null }.sortedBy { it.timestamp }

        val result = mutableListOf<Pair<Comment, Int>>()
        topLevel.forEach { parent ->
            result.add(parent to 0)
            replies.filter { it.parentId == parent.id }.forEach { reply ->
                result.add(reply to 1)
            }
        }
        result
    }

    if (post == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isAdminReview) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This content may have already been deleted.", color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                    if (reportId != null) {
                        TextButton(onClick = {
                            db.collection("reports").document(reportId).delete()
                                .addOnSuccessListener { onBack() }
                        }) {
                            Text("Remove Report", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    PostContent(
                        post = post!!,
                        onCommentClick = { /* Already in Detail */ },
                        onDelete = {
                            // Delete the post and navigate back
                            db.collection("posts").document(post!!.id).delete()
                                .addOnSuccessListener {
                                    if (isAdminReview && reportId != null) {
                                        db.collection("reports").document(reportId).delete()
                                    }
                                    onBack()
                                }
                        }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Text(
                        text = "Comments (${post!!.commentsCount})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                items(organizedComments) { (comment, indentation) ->
                    CommentItem(
                        comment = comment,
                        indentation = indentation,
                        currentUserId = currentUserId,
                        isHighlighted = comment.id == highlightCommentId,
                        onReplyClick = { replyingTo = it },
                        onDeleteClick = {
                            // Delete Logic
                            db.collection("comments")
                                .whereEqualTo("parentId", comment.id)
                                .get()
                                .addOnSuccessListener { childrenSnapshot ->
                                    val batch = db.batch()
                                    batch.delete(db.collection("comments").document(comment.id))
                                    childrenSnapshot.documents.forEach { childDoc ->
                                        batch.delete(childDoc.reference)
                                    }
                                    batch.commit().addOnSuccessListener {
                                        val totalDeleted = 1 + childrenSnapshot.size()
                                        db.collection("posts").document(postId)
                                            .update("commentsCount", FieldValue.increment(-totalDeleted.toLong()))
                                        
                                        if (isAdminReview && reportId != null && comment.id == highlightCommentId) {
                                            db.collection("reports").document(reportId).delete()
                                            onBack()
                                        }
                                    }
                                }
                        }
                    )
                }
            }

            if (isAdminReview) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Delete Content")
                    }
                }
            } else {
                // Comment Input Bar with Reply Context
                Column {
                    if (replyingTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Replying to ${replyingTo!!.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()
                                .imePadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = { Text(if (replyingTo != null) "Write a reply..." else "Write a comment...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                shape = MaterialTheme.shapes.medium
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newCommentText.isNotBlank()) {
                                        val user = auth.currentUser ?: return@IconButton
                                        db.collection("users").document(user.uid).get().addOnSuccessListener { userDoc ->
                                            val displayName = userDoc.getString("displayName") ?: "Anonymous"

                                            // If replying to a reply, use the same parentId to keep indentation at 1.
                                            val actualParentId = replyingTo?.let { it.parentId ?: it.id }

                                            val commentData = hashMapOf(
                                                "postId" to postId,
                                                "userId" to user.uid,
                                                "displayName" to displayName,
                                                "text" to newCommentText,
                                                "timestamp" to Timestamp.now(),
                                                "parentId" to actualParentId
                                            )
                                            db.collection("comments").add(commentData).addOnSuccessListener {
                                                db.collection("posts").document(postId)
                                                    .update("commentsCount", FieldValue.increment(1))
                                                newCommentText = ""
                                                replyingTo = null
                                            }
                                        }
                                    }
                                },
                                enabled = newCommentText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Content?") },
            text = { Text("This will permanently remove this content and its associated report.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        if (highlightCommentId != null) {
                            // Delete the highlighted comment
                            val commentToDelete = comments.find { it.id == highlightCommentId }
                            if (commentToDelete != null) {
                                db.collection("comments")
                                    .whereEqualTo("parentId", commentToDelete.id)
                                    .get()
                                    .addOnSuccessListener { childrenSnapshot ->
                                        val batch = db.batch()
                                        batch.delete(db.collection("comments").document(commentToDelete.id))
                                        childrenSnapshot.documents.forEach { childDoc ->
                                            batch.delete(childDoc.reference)
                                        }
                                        batch.commit().addOnSuccessListener {
                                            val totalDeleted = 1 + childrenSnapshot.size()
                                            db.collection("posts").document(postId)
                                                .update("commentsCount", FieldValue.increment(-totalDeleted.toLong()))
                                            if (reportId != null) db.collection("reports").document(reportId).delete()
                                            onBack()
                                        }
                                    }
                            }
                        } else {
                            // Delete the post
                            db.collection("posts").document(postId).delete()
                                .addOnSuccessListener {
                                    if (reportId != null) db.collection("reports").document(reportId).delete()
                                    onBack()
                                }
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

@Composable
fun CommentItem(
    comment: Comment,
    indentation: Int,
    currentUserId: String?,
    onReplyClick: (Comment) -> Unit,
    onDeleteClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighlighted) Color.Red.copy(alpha = 0.15f) else Color.Transparent)
            .padding(
                start = (16 + (indentation * 32)).dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            )
    ) {
        Surface(
            modifier = Modifier.size(32.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxSize(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = comment.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (indentation > 0) 13.sp else 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                val dateStr = comment.timestamp?.toDate()?.let {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                } ?: ""
                Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(16.dp)
                        )
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
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) }
                        )

                        // Delete Option
                        if (currentUserId == comment.userId) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = if (indentation > 0) 13.sp else 14.sp
            )

            TextButton(
                onClick = { onReplyClick(comment) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text("Reply", fontSize = 12.sp)
            }
        }
    }

    if (showReportDialog) {
        ReportDialog(
            targetUserId = comment.userId,
            targetType = 2, // 2 for Comment
            attachedId = comment.id,
            onDismiss = { showReportDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Comment?") },
            text = { Text("This will delete the comment and any replies to it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// Extension function to help with icon size
private fun Modifier.size(size: Int) = this.size(size.dp)
