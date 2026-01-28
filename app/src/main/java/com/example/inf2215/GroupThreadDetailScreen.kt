package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

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

    var comments by remember { mutableStateOf(listOf<GroupThreadComment>()) }
    var newComment by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val threadRef = remember(groupId, threadId) {
        db.collection("groups").document(groupId)
            .collection("threads").document(threadId)
    }

    DisposableEffect(groupId, threadId) {
        // Thread header listener
        val regHeader = threadRef.addSnapshotListener { doc, e ->
            if (e != null) {
                status = "Error loading thread: ${e.message}"
                title = "Error"
                body = ""
                createdByName = ""
                commentsCount = 0
                return@addSnapshotListener
            }

            if (doc == null || !doc.exists()) {
                status = "Thread not found: groups/$groupId/threads/$threadId"
                title = "Thread not found"
                body = ""
                createdByName = ""
                commentsCount = 0
                return@addSnapshotListener
            }

            status = ""
            title = doc.getString("title") ?: "Untitled"
            body = doc.getString("body") ?: ""
            createdByName = doc.getString("createdByName") ?: "Unknown"
            commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt()
        }

        // Comments listener
        val regComments = threadRef.collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    status = "Error loading comments: ${e.message}"
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Thread", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (status.isNotBlank()) {
            Text(
                status,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (createdByName.isNotBlank()) {
                            Text("By: $createdByName", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (body.isNotBlank()) Text(body)
                        Spacer(Modifier.height(10.dp))
                        Text("Comments: $commentsCount", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            items(comments) { c ->
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(c.displayName, style = MaterialTheme.typography.labelLarge)
                        val date = c.createdAt?.toDate()?.let {
                            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                        } ?: ""
                        if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(c.text)
                    }
                }
            }
        }

        // Comment input
        Surface(tonalElevation = 3.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a comment...") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (me == null) return@Button
                        val text = newComment.trim()
                        if (text.isBlank()) return@Button

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
                    enabled = newComment.trim().isNotBlank() && title != "Thread not found"
                ) { Text("Send") }
            }
        }
    }
}
