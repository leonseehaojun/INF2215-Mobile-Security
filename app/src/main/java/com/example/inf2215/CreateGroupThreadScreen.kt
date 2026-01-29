package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var isMember by remember { mutableStateOf(false) }

    LaunchedEffect(groupId, me) {
        if (me == null) return@LaunchedEffect
        db.collection("groups").document(groupId)
            .collection("members").document(me)
            .get()
            .addOnSuccessListener { isMember = it.exists() }
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
                Column {
                    Text(
                        text = "New Discussion",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Start a conversation with your group",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Error/Status Messages
            if (status.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
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
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = status,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Thread Title
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Title",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What's this thread about?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Thread Body
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Message",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("Share your thoughts, questions, or updates...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 6,
                    maxLines = 12
                )
            }

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "All group members can see and reply to this thread. Be respectful and constructive!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text(
                        "Cancel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

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
                        if (cleanTitle.isBlank() || cleanBody.isBlank()) {
                            status = "Please fill in both title and message."
                            return@Button
                        }

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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Post Thread",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}