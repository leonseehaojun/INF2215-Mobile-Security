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
fun CreateGroupScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val me = auth.currentUser?.uid

    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf("") } // string input
    var daysText by remember { mutableStateOf("Tue,Thu") }
    var time by remember { mutableStateOf("7:00 PM") }
    var description by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create Running Group", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area (e.g. Punggol)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = pace, onValueChange = { pace = it }, label = { Text("Pace (min/km) e.g. 6.5") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = daysText, onValueChange = { daysText = it }, label = { Text("Days (comma separated) e.g. Tue,Thu") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time e.g. 7:00 PM") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }

            Button(
                onClick = {
                    if (me == null) return@Button
                    isSaving = true

                    val days = daysText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val paceValue = pace.toDoubleOrNull() ?: 0.0

                    // Get displayName for ownerName
                    db.collection("users").document(me).get().addOnSuccessListener { userDoc ->
                        val ownerName = userDoc.getString("displayName") ?: "Runner"

                        val groupData = hashMapOf(
                            "name" to name.trim(),
                            "area" to area.trim(),
                            "paceMinKm" to paceValue,
                            "days" to days,
                            "time" to time.trim(),
                            "description" to description.trim(),
                            "ownerId" to me,
                            "ownerName" to ownerName,
                            "membersCount" to 1,
                            "createdAt" to Timestamp.now()
                        )

                        db.collection("groups").add(groupData)
                            .addOnSuccessListener { groupRef ->
                                // Add owner as member
                                val memberData = hashMapOf(
                                    "role" to "owner",
                                    "joinedAt" to Timestamp.now(),
                                    "displayName" to ownerName
                                )

                                groupRef.collection("members").document(me).set(memberData)
                                    .addOnSuccessListener {
                                        isSaving = false
                                        onCreated(groupRef.id)
                                    }
                                    .addOnFailureListener { isSaving = false }
                            }
                            .addOnFailureListener { isSaving = false }
                    }.addOnFailureListener { isSaving = false }
                },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaving) "Creating..." else "Create")
            }
        }
    }
}
