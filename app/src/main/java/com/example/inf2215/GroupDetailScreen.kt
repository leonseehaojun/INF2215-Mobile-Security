package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit
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

    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId)
            .addSnapshotListener { doc, _ ->
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

    LaunchedEffect(groupId, me) {
        if (me == null) return@LaunchedEffect
        db.collection("groups").document(groupId)
            .collection("members").document(me)
            .addSnapshotListener { doc, _ ->
                isMember = doc != null && doc.exists()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
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

        val canAct = me != null && !working

        Button(
            onClick = {
                if (me == null) return@Button
                working = true

                val groupRef = db.collection("groups").document(groupId)
                val memberRef = groupRef.collection("members").document(me)

                if (!isMember) {
                    // JOIN
                    db.collection("users").document(me).get().addOnSuccessListener { userDoc ->
                        val displayName = userDoc.getString("displayName") ?: "Runner"
                        val memberData = hashMapOf(
                            "role" to "member",
                            "joinedAt" to Timestamp.now(),
                            "displayName" to displayName
                        )

                        db.runBatch { batch ->
                            batch.set(memberRef, memberData)
                            batch.update(groupRef, "membersCount", FieldValue.increment(1))
                        }.addOnSuccessListener {
                            working = false
                        }.addOnFailureListener { working = false }
                    }.addOnFailureListener { working = false }

                } else {
                    // LEAVE
                    db.runBatch { batch ->
                        batch.delete(memberRef)
                        batch.update(groupRef, "membersCount", FieldValue.increment(-1))
                    }.addOnSuccessListener {
                        working = false
                    }.addOnFailureListener { working = false }
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
    }
}
