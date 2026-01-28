package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun CreateGroupThreadScreen(
    groupId: String,
    onCreated: (threadId: String) -> Unit,
    onCancel: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val me = auth.currentUser?.uid

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // Optional: block non-members from posting
    var isMember by remember { mutableStateOf(false) }

    LaunchedEffect(groupId, me) {
        if (me == null) return@LaunchedEffect
        db.collection("groups").document(groupId)
            .collection("members").document(me)
            .get()
            .addOnSuccessListener { isMember = it.exists() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Group Thread", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title *") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Message *") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("Cancel") }

            Button(
                onClick = {
                    if (me == null) {
                        status = "Please login first."
                        return@Button
                    }
                    if (!isMember) {
                        status = "Join this group to post threads."
                        return@Button
                    }

                    val cleanTitle = title.trim()
                    val cleanBody = body.trim()
                    if (cleanTitle.isBlank() || cleanBody.isBlank()) return@Button

                    isSaving = true
                    status = ""

                    db.collection("users").document(me).get()
                        .addOnSuccessListener { userDoc ->
                            val name = userDoc.getString("displayName") ?: "Runner"
                            val now = Timestamp.now()

                            val threadData = hashMapOf(
                                "title" to cleanTitle,
                                "body" to cleanBody,
                                "createdById" to me,
                                "createdByName" to name,
                                "createdAt" to now,
                                "lastActivityAt" to now,
                                "commentsCount" to 0
                            )

                            db.collection("groups").document(groupId)
                                .collection("threads")
                                .add(threadData)
                                .addOnSuccessListener { ref ->
                                    isSaving = false
                                    onCreated(ref.id)
                                }
                                .addOnFailureListener { e ->
                                    isSaving = false
                                    status = "Failed to post: ${e.message}"
                                }
                        }
                        .addOnFailureListener { e ->
                            isSaving = false
                            status = "Failed to load user: ${e.message}"
                        }
                },
                enabled = !isSaving && title.trim().isNotBlank() && body.trim().isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaving) "Posting..." else "Post")
            }
        }
    }
}
