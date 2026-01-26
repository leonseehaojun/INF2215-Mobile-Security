package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(
    targetUserId: String,
    targetType: Int,
    attachedId: String?,
    onDismiss: () -> Unit
) {
    var reportTitle by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    
    var reasonExpanded by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf("") }
    
    var description by remember { mutableStateOf("") }

    val categories = listOf("Activity Integrity", "Social Conduct", "Content Safety")
    val reasons = mapOf(
        "Activity Integrity" to listOf("Incorrect Activity Type", "Cheating / Faked Data", "Duplicate / Spam Entry"),
        "Social Conduct" to listOf("Harrassment / Bullying", "Hate Speech", "Privacy & Impersonation"),
        "Content Safety" to listOf("Sexual / Violent Contents", "Scams / Bots", "Dangerous Behaviours")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Report Title
                OutlinedTextField(
                    value = reportTitle,
                    onValueChange = { reportTitle = it },
                    label = { Text("Report Title") },
                    placeholder = { Text("Enter a brief title for your report") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    selectedReason = ""
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Reason Dropdown
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded && selectedCategory.isNotEmpty(),
                    onExpandedChange = { if (selectedCategory.isNotEmpty()) reasonExpanded = !reasonExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedReason,
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedCategory.isNotEmpty(),
                        label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded && selectedCategory.isNotEmpty(),
                        onDismissRequest = { reasonExpanded = false }
                    ) {
                        reasons[selectedCategory]?.forEach { reason ->
                            DropdownMenuItem(
                                text = { Text(reason) },
                                onClick = {
                                    selectedReason = reason
                                    reasonExpanded = false
                                }
                            )
                        }
                    }
                }

                // Description Text Box
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Enter description here") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val db = FirebaseFirestore.getInstance()
                    val reportData = hashMapOf(
                        "targetUserId" to targetUserId,
                        "targetType" to targetType,
                        "attachedId" to attachedId,
                        "title" to reportTitle,
                        "category" to selectedCategory,
                        "reason" to selectedReason,
                        "description" to description,
                        "timestamp" to Timestamp.now(),
                        "reportedBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: "Unknown")
                    )
                    db.collection("reports").add(reportData).addOnSuccessListener {
                        onDismiss()
                    }
                },
                enabled = selectedReason.isNotEmpty() && reportTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Report", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
