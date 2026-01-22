package com.example.inf2215

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import java.util.UUID

// Upload Image Function
fun uploadImageToStorage(uri: Uri, onSuccess: (String) -> Unit, onError: () -> Unit) {
    val storageRef = FirebaseStorage.getInstance().reference
    val imageRef = storageRef.child("post_images/${UUID.randomUUID()}.jpg")

    imageRef.putFile(uri)
        .addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                onSuccess(uri.toString())
            }
        }
        .addOnFailureListener { onError() }
}

// SCREEN 1: CREATE TEXT/IMAGE POST
@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    onPostSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPosting by remember { mutableStateOf(false) }

    // Image Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("New Post", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Title Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title *") },
            isError = title.isBlank() && text.isNotBlank(), // Visual cue if they start typing body but no title
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Body Input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("What's on your mind?") },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        // Image Preview
        if (selectedImageUri != null) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { selectedImageUri = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, "Remove Image", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.AddPhotoAlternate, "Add Image")
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onCancel) { Text("Cancel") }

            Button(
                // Button is only enabled if Title is not blank AND (Text or Image exists)
                enabled = !isPosting && title.isNotBlank() && (text.isNotBlank() || selectedImageUri != null),
                onClick = {
                    isPosting = true
                    val user = auth.currentUser ?: return@Button

                    val savePost = { imageUrl: String? ->
                        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                            val safeName = doc.getString("displayName") ?: "Runner"

                            val postData: HashMap<String, Any?> = hashMapOf(
                                "userId" to user.uid,
                                "displayName" to safeName,
                                "title" to title,
                                "text" to text,
                                "imageUrl" to imageUrl,
                                "type" to "NORMAL",
                                "createdAt" to Timestamp.now()
                            )
                            db.collection("posts").add(postData).addOnSuccessListener {
                                isPosting = false
                                onPostSuccess()
                            }
                        }
                    }

                    if (selectedImageUri != null) {
                        uploadImageToStorage(selectedImageUri!!, onSuccess = { url ->
                            savePost(url)
                        }, onError = { isPosting = false })
                    } else {
                        savePost(null)
                    }
                }
            ) {
                Text(if (isPosting) "Posting..." else "Post")
            }
        }
    }
}

// SCREEN 2: TRACK RUN SCREEN
@Composable
fun TrackRunScreen(
    modifier: Modifier = Modifier,
    onRunFinished: () -> Unit,
    onCancel: () -> Unit
) {
    // Run Logic States
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) } // True when run is done, change to "Review Mode"
    var seconds by remember { mutableStateOf(0L) }
    var distanceKm by remember { mutableStateOf(0.0) }

    // Review States
    var runTitle by remember { mutableStateOf("") }
    var runDescription by remember { mutableStateOf("") } // User input for body text
    var isSaving by remember { mutableStateOf(false) }

    // Timer Logic
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = System.currentTimeMillis()
            val startSeconds = seconds
            while (isRunning) {
                seconds = startSeconds + (System.currentTimeMillis() - startTime) / 1000
                distanceKm += 0.003
                delay(1000)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isFinished) {
            // VIEW 1: ACTIVE RUNNER
            Text("Current Run", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(32.dp))

            Text(formatTime(seconds), style = MaterialTheme.typography.displayLarge)
            Text("Duration", style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(16.dp))

            Text(String.format("%.2f km", distanceKm), style = MaterialTheme.typography.displayMedium)
            Text("Distance", style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.height(48.dp))

            if (!isRunning && seconds == 0L) {
                // Initial State
                Button(onClick = { isRunning = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("START RUN") }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            } else if (isRunning) {
                // Running State
                Button(onClick = { isRunning = false }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("PAUSE") }
            } else {
                // Paused State
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { isRunning = true }, modifier = Modifier.weight(1f)) { Text("Resume") }
                    Button(onClick = { isFinished = true }, modifier = Modifier.weight(1f)) { Text("Finish") }
                }
            }
        } else {
            // VIEW 2: REVIEW & SAVE
            Text("Great Job!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("${formatTime(seconds)} • ${String.format("%.2f km", distanceKm)}", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(24.dp))

            // Title Input
            OutlinedTextField(
                value = runTitle,
                onValueChange = { runTitle = it },
                label = { Text("Title (Required)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Description Input
            OutlinedTextField(
                value = runDescription,
                onValueChange = { runDescription = it },
                label = { Text("How did it go?") },
                placeholder = { Text("E.g. Beat my personal best!") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.height(32.dp))

            if (isSaving) {
                CircularProgressIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cancel Option
                    OutlinedButton(
                        onClick = onCancel, // Discard run and go home
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Discard")
                    }

                    // Save Button
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = runTitle.isNotBlank(), // Force user to add a title
                        onClick = {
                            isSaving = true
                            saveRunToFirestore(
                                seconds = seconds,
                                distanceKm = distanceKm,
                                title = runTitle,
                                description = runDescription, // Pass the user input
                                onSuccess = onRunFinished
                            )
                        }
                    ) {
                        Text("Save to Feed")
                    }
                }
            }
        }
    }
}

// Helper Function to save to Firestore
fun saveRunToFirestore(
    seconds: Long,
    distanceKm: Double,
    title: String,
    description: String,
    onSuccess: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser ?: return

    val durationMin = seconds / 60.0

    // Save raw run data
    val runData = hashMapOf(
        "userId" to user.uid,
        "distanceKm" to distanceKm,
        "durationMin" to durationMin,
        "createdAt" to Timestamp.now()
    )

    db.collection("runs").add(runData).addOnSuccessListener { runRef ->
        db.collection("users").document(user.uid).get().addOnSuccessListener { userDoc ->
            val displayName = userDoc.getString("displayName") ?: "Runner"

            // Create the Post
            val postData = hashMapOf(
                "userId" to user.uid,
                "displayName" to displayName,
                "title" to title,
                "text" to description.ifBlank { "Completed a run!" }, // Use input or fallback
                "type" to "RUN",
                "runId" to runRef.id,
                "runDistance" to String.format("%.2f km", distanceKm),
                "runDuration" to formatTime(seconds),
                "createdAt" to Timestamp.now()
            )

            db.collection("posts").add(postData).addOnSuccessListener { onSuccess() }
        }
    }
}

fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}