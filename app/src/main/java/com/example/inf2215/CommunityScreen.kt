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

private enum class CommunityTab { Groups, Forum }

data class GroupCardModel(
    val id: String,
    val name: String,
    val area: String,
    val paceMinKm: Double,
    val days: List<String>,
    val time: String,
    val membersCount: Int
)

data class ThreadCardModel(
    val id: String,
    val title: String,
    val category: String,
    val createdByName: String,
    val commentsCount: Int
)

@Composable
fun CommunityScreen(
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateThread: () -> Unit,
    onOpenThread: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var tab by remember { mutableStateOf(CommunityTab.Groups) }

    var groups by remember { mutableStateOf(listOf<GroupCardModel>()) }
    var threads by remember { mutableStateOf(listOf<ThreadCardModel>()) }

    // listen groups
    LaunchedEffect(Unit) {
        db.collection("groups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
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

    // listen threads
    LaunchedEffect(Unit) {
        db.collection("threads")
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                threads = snap?.documents?.map { doc ->
                    ThreadCardModel(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled",
                        category = doc.getString("category") ?: "General",
                        createdByName = doc.getString("createdByName") ?: "Unknown",
                        commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt()
                    )
                } ?: emptyList()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TabRow(selectedTabIndex = if (tab == CommunityTab.Groups) 0 else 1) {
            Tab(selected = tab == CommunityTab.Groups, onClick = { tab = CommunityTab.Groups }, text = { Text("Groups") })
            Tab(selected = tab == CommunityTab.Forum, onClick = { tab = CommunityTab.Forum }, text = { Text("Forum") })
        }

        when (tab) {
            CommunityTab.Groups -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCreateGroup) { Text("Create Group") }
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

            CommunityTab.Forum -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCreateThread) { Text("Create Thread") }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(threads) { t ->
                        Card(onClick = { onOpenThread(t.id) }) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text("Category: ${t.category}", style = MaterialTheme.typography.bodySmall)
                                Text("By: ${t.createdByName}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Text("Comments: ${t.commentsCount}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
