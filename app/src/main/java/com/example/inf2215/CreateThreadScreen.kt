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
fun CreateThreadScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val me = auth.currentUser?.uid

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Training") }
    var body by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Forum Thread", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message *") }, modifier = Modifier.fillMaxWidth(), minLines = 4)

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }

            Button(
                onClick = {
                    if (me == null) return@Button
                    isSaving = true

                    db.collection("users").document(me).get().addOnSuccessListener { userDoc ->
                        val name = userDoc.getString("displayName") ?: "Runner"
                        val now = Timestamp.now()

                        val threadData = hashMapOf(
                            "title" to title.trim(),
                            "category" to category.trim(),
                            "body" to body.trim(),
                            "createdById" to me,
                            "createdByName" to name,
                            "createdAt" to now,
                            "lastActivityAt" to now,
                            "commentsCount" to 0
                        )

                        db.collection("threads").add(threadData)
                            .addOnSuccessListener { ref ->
                                isSaving = false
                                onCreated(ref.id)
                            }
                            .addOnFailureListener { isSaving = false }
                    }.addOnFailureListener { isSaving = false }
                },
                enabled = !isSaving && title.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaving) "Posting..." else "Post")
            }
        }
    }
}
