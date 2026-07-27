package de.wegefrei.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Defers validation-error display until a field has been focused and then blurred at least
 * once ("touched") — the common pattern shared by every required field in this app. Attach
 * [modifier] to the field and read [touched] to gate error display.
 */
data class TouchedFieldState(val touched: Boolean, val modifier: Modifier)

@Composable
fun rememberTouchedFieldState(onFocusChanged: (isFocused: Boolean) -> Unit = {}): TouchedFieldState {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val modifier = Modifier.onFocusChanged { focusState ->
        onFocusChanged(focusState.isFocused)
        if (focusState.isFocused) {
            wasFocused = true
        } else if (wasFocused) {
            touched = true
        }
    }
    return TouchedFieldState(touched, modifier)
}
