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

data class ThreadComment(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)

@Composable
fun ThreadDetailScreen(
    threadId: String,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val me = auth.currentUser?.uid

    var title by remember { mutableStateOf("Loading...") }
    var category by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var createdByName by remember { mutableStateOf("") }
    var commentsCount by remember { mutableStateOf(0) }

    var comments by remember { mutableStateOf(listOf<ThreadComment>()) }
    var newComment by remember { mutableStateOf("") }

    LaunchedEffect(threadId) {
        db.collection("threads").document(threadId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                title = doc.getString("title") ?: "Untitled"
                category = doc.getString("category") ?: "General"
                body = doc.getString("body") ?: ""
                createdByName = doc.getString("createdByName") ?: "Unknown"
                commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt()
            }

        db.collection("threads").document(threadId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.map { d ->
                    ThreadComment(
                        id = d.id,
                        userId = d.getString("userId") ?: "",
                        displayName = d.getString("displayName") ?: "User",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt")
                    )
                } ?: emptyList()
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

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text("Category: $category", style = MaterialTheme.typography.bodySmall)
                        Text("By: $createdByName", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Text(body)
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

        // comment input
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

                            val threadRef = db.collection("threads").document(threadId)
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
                    enabled = newComment.trim().isNotBlank()
                ) { Text("Send") }
            }
        }
    }
}
