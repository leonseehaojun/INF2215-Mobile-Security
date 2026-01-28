package com.example.inf2215

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
    var pace by remember { mutableStateOf(0.0) }
    var days by remember { mutableStateOf(listOf<String>()) }
    var time by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var membersCount by remember { mutableStateOf(1) }

    var isMember by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    var threads by remember { mutableStateOf(listOf<GroupThread>()) }
    var threadsStatus by remember { mutableStateOf("") }

    // ----- Listen group info -----
    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                if (doc == null || !doc.exists()) return@addSnapshotListener

                name = doc.getString("name") ?: "Unnamed Group"
                area = doc.getString("area") ?: ""
                pace = doc.getDouble("paceMinKm") ?: 0.0
                days = doc.get("days") as? List<String> ?: emptyList()
                time = doc.getString("time") ?: ""
                description = doc.getString("description") ?: ""
                membersCount = (doc.getLong("membersCount") ?: 1L).toInt()
            }
    }

    // ----- Listen membership -----
    LaunchedEffect(groupId, me) {
        if (me == null) return@LaunchedEffect
        db.collection("groups").document(groupId)
            .collection("members").document(me)
            .addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                isMember = doc != null && doc.exists()
            }
    }

    // ----- Listen threads inside group -----
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
                    GroupThread(
                        id = d.id,
                        title = d.getString("title") ?: "Untitled",
                        body = d.getString("body") ?: "",
                        createdById = d.getString("createdById") ?: "",
                        createdByName = d.getString("createdByName") ?: "Unknown",
                        createdAt = d.getTimestamp("createdAt"),
                        commentsCount = (d.getLong("commentsCount") ?: 0L).toInt()
                    )
                } ?: emptyList()
            }
    }

    val canAct = me != null && !working

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Header
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        if (area.isNotBlank()) Text("Area: $area")
        if (pace > 0) Text("Pace: $pace min/km")
        if (days.isNotEmpty()) Text("Days: ${days.joinToString(", ")}")
        if (time.isNotBlank()) Text("Time: $time")

        Spacer(Modifier.height(8.dp))
        Text("Members: $membersCount", style = MaterialTheme.typography.labelLarge)

        Spacer(Modifier.height(12.dp))
        if (description.isNotBlank()) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        // Join/Leave button
        Button(
            onClick = {
                if (me == null) return@Button
                working = true

                val groupRef = db.collection("groups").document(groupId)
                val memberRef = groupRef.collection("members").document(me)

                if (!isMember) {
                    // JOIN
                    db.collection("users").document(me).get()
                        .addOnSuccessListener { userDoc ->
                            val displayName = userDoc.getString("displayName") ?: "Runner"
                            val memberData = hashMapOf(
                                "role" to "member",
                                "joinedAt" to Timestamp.now(),
                                "displayName" to displayName
                            )

                            db.runBatch { batch ->
                                batch.set(memberRef, memberData)
                                batch.update(groupRef, "membersCount", FieldValue.increment(1))
                            }.addOnSuccessListener { working = false }
                                .addOnFailureListener { working = false }
                        }
                        .addOnFailureListener { working = false }

                } else {
                    // LEAVE
                    db.runBatch { batch ->
                        batch.delete(memberRef)
                        batch.update(groupRef, "membersCount", FieldValue.increment(-1))
                    }.addOnSuccessListener { working = false }
                        .addOnFailureListener { working = false }
                }
            },
            enabled = canAct,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    working -> "Please wait..."
                    isMember -> "Leave Group"
                    else -> "Join Group"
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (isMember) {
            Button(
                onClick = { onCreateThread(groupId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Thread in Group")
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Text(
                "Join this group to create threads and participate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        // Threads list
        Text("Group Threads", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (threadsStatus.isNotBlank()) {
            Text(threadsStatus, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (threads.isEmpty()) {
            Text(
                "No threads yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(threads) { t ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(groupId, t.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(t.title, fontWeight = FontWeight.Bold)
                            if (t.body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    t.body,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "By ${t.createdByName} • Comments: ${t.commentsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
