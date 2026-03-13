package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminReportsScreen() {
    var selectedReport by remember { mutableStateOf<ReportItem?>(null) }

    if (selectedReport == null) {
        ReportList(onReportClick = { selectedReport = it })
    } else {
        ReportDetail(report = selectedReport!!, onBack = { selectedReport = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportList(onReportClick: (ReportItem) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var reports by remember { mutableStateOf(listOf<ReportItem>()) }
    
    // Multi-select Filters
    var selectedTypes by remember { mutableStateOf(setOf<Int>()) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedReasons by remember { mutableStateOf(setOf<String>()) }
    
    // Structure from ReportDialog in HomeScreen.kt
    val typeOptions = listOf("User" to 0, "Post" to 1, "Comment" to 2)
    val categoryOptions = listOf("Activity Integrity", "Social Conduct", "Content Safety")
    val reasonMapping = mapOf(
        "Activity Integrity" to listOf("Incorrect Activity Type", "Cheating / Faked Data", "Duplicate / Spam Entry"),
        "Social Conduct" to listOf("Harrassment / Bullying", "Hate Speech", "Privacy & Impersonation"),
        "Content Safety" to listOf("Sexual / Violent Contents", "Scams / Bots", "Dangerous Behaviours")
    )

    LaunchedEffect(selectedTypes, selectedCategories, selectedReasons) {
        var query = db.collection("reports").orderBy("timestamp", Query.Direction.DESCENDING)
        
        query.addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                val item = doc.toObject(ReportItem::class.java)?.copy(id = doc.id)
                
                val matchesType = selectedTypes.isEmpty() || item?.targetType in selectedTypes
                val matchesCat = selectedCategories.isEmpty() || item?.category in selectedCategories
                val matchesReason = selectedReasons.isEmpty() || item?.reason in selectedReasons
                
                if (matchesType && matchesCat && matchesReason) item else null
            } ?: emptyList()
            reports = list
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Box
        Surface(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Filters", fontWeight = FontWeight.Bold)
                    }
                    if (selectedTypes.isNotEmpty() || selectedCategories.isNotEmpty() || selectedReasons.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedTypes = emptySet()
                                selectedCategories = emptySet()
                                selectedReasons = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type Dropdown
                    MultiSelectDropdown(
                        label = "Type",
                        options = typeOptions.map { it.first },
                        selectedOptions = typeOptions.filter { it.second in selectedTypes }.map { it.first }.toSet(),
                        onOptionToggle = { label ->
                            val type = typeOptions.find { it.first == label }?.second ?: return@MultiSelectDropdown
                            selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Category Dropdown
                    MultiSelectDropdown(
                        label = "Category",
                        options = categoryOptions,
                        selectedOptions = selectedCategories,
                        onOptionToggle = { cat ->
                            selectedCategories = if (cat in selectedCategories) {
                                // If removing category, also remove its reasons
                                val relatedReasons = reasonMapping[cat] ?: emptyList()
                                selectedReasons = selectedReasons - relatedReasons.toSet()
                                selectedCategories - cat
                            } else {
                                selectedCategories + cat
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Reason Dropdown (Enabled only if a category is selected)
                    val availableReasons = selectedCategories.flatMap { reasonMapping[it] ?: emptyList() }
                    MultiSelectDropdown(
                        label = "Reasons",
                        options = availableReasons,
                        selectedOptions = selectedReasons,
                        onOptionToggle = { reason ->
                            selectedReasons = if (reason in selectedReasons) selectedReasons - reason else selectedReasons + reason
                        },
                        enabled = selectedCategories.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // List Header
        Text(
            text = "Reports (${reports.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // Scrollable List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(reports) { report ->
                ReportCard(report, onClick = { onReportClick(report) })
            }
        }
    }
}

@Composable
fun MultiSelectDropdown(
    label: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selectedOptions.isEmpty()) label else "${selectedOptions.size} Selected",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(intrinsicSize = IntrinsicSize.Max).heightIn(max = 300.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option in selectedOptions,
                                onCheckedChange = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option, fontSize = 14.sp)
                        }
                    },
                    onClick = { onOptionToggle(option) }
                )
            }
        }
    }
}

@Composable
fun ReportCard(report: ReportItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeLabel = when(report.targetType) {
                    0 -> "USER"
                    1 -> "POST"
                    else -> "COMMENT"
                }
                Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text(typeLabel) }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(Modifier.height(8.dp))
            Text("${report.category} > ${report.reason}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            if (report.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(Modifier.height(8.dp))
            val dateStr = report.timestamp?.toDate()?.let { 
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it) 
            } ?: ""
            Text(
                dateStr, 
                style = MaterialTheme.typography.labelSmall, 
                modifier = Modifier.align(Alignment.End).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
fun ReportDetail(report: ReportItem, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Text("Report Details", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            
            DetailItem("Report Title", report.title)
            DetailItem("Target User ID", report.targetUserId)
            DetailItem("Attached ID", report.attachedId ?: "N/A")
            DetailItem("Type", when(report.targetType) { 0 -> "User"; 1 -> "Post"; else -> "Comment" })
            DetailItem("Category", report.category)
            DetailItem("Reason", report.reason)
            DetailItem("Reporter ID", report.reportedBy)
            
            Spacer(Modifier.height(16.dp))
            Text("Description", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = report.description.ifBlank { "No description provided." },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { /* TODO: Implement action like Delete Post */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Take Action")
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
