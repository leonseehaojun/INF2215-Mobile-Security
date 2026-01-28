package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class GroupCardModel(
    val id: String,
    val name: String,
    val area: String,
    val paceMinKm: Double,
    val days: List<String>,
    val time: String,
    val membersCount: Int
)

@Composable
fun CommunityScreen(
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var groups by remember { mutableStateOf(listOf<GroupCardModel>()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("groups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    status = "Error loading groups: ${e.message}"
                    return@addSnapshotListener
                }

                groups = snap?.documents?.map { doc ->
                    GroupCardModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unnamed Group",
                        area = doc.getString("area") ?: "",
                        paceMinKm = doc.getDouble("paceMinKm") ?: 0.0,
                        days = doc.get("days") as? List<String> ?: emptyList(),
                        time = doc.getString("time") ?: "",
                        membersCount = (doc.getLong("membersCount") ?: 1L).toInt()
                    )
                } ?: emptyList()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onCreateGroup) { Text("Create Group") }
        }

        if (status.isNotBlank()) {
            Text(
                text = status,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups) { g ->
                Card(onClick = { onOpenGroup(g.id) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        if (g.area.isNotBlank()) Text("Area: ${g.area}")
                        if (g.paceMinKm > 0) Text("Pace: ${g.paceMinKm} min/km")
                        if (g.days.isNotEmpty()) Text("Days: ${g.days.joinToString(", ")}")
                        if (g.time.isNotBlank()) Text("Time: ${g.time}")
                        Spacer(Modifier.height(6.dp))
                        Text("Members: ${g.membersCount}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
