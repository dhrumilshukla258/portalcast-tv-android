package com.portalcast.tv.ui.components

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Text
import com.portalcast.tv.ui.theme.AppColors

/**
 * Single-line text entry for the auth screens. Wraps material3's `OutlinedTextField`
 * (the one material3 component allowed here; there is no TV equivalent) and makes it
 * behave on a TV:
 * - a 2 dp white ring shows while the field has focus (the material outline alone is
 *   too faint on the dark background);
 * - d-pad Up/Down move focus out of the field instead of being swallowed by the text
 *   cursor, so the user can reach the button below without opening the IME;
 * - the IME action (Go/Next/Done) calls [onImeAction].
 * Label and hint are tv-material `Text`s so they pick up the app palette.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    var openedAtMs by remember { mutableLongStateOf(0L) }
    // TV pattern: focusing the field must NOT open the keyboard (Compose opens it on focus, and the
    // open keyboard then captures the d-pad, so crossing a Search field trapped the remote and
    // typed letters). The field is read-only while merely focused; Center makes it editable.
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) AppColors.Text else AppColors.TextMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                // Center opens the keyboard on key-down; the keyboard can appear in time to take the
                // key-up as a press on its focused key and type a stray character (a "q" on Gboard,
                // intermittent). Drop a single appended character arriving right after opening:
                // nobody can reach and press a keyboard key that fast with a remote.
                if (!isStrayFromOpen(value, next, SystemClock.uptimeMillis() - openedAtMs)) onValueChange(next)
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) editing = false
                }
                .border(2.dp, if (focused) AppColors.Focus else Color.Transparent, shape)
                .onPreviewKeyEvent { event ->
                    val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (isSelect && !editing) {
                        // Consume both halves; start editing after the key-up so the keyboard can't
                        // receive it as a press on its own focused key.
                        if (event.type == KeyEventType.KeyUp) {
                            editing = true
                            openedAtMs = SystemClock.uptimeMillis()
                            scope.launch {
                                delay(IME_OPEN_DELAY_MS)
                                keyboard?.show()
                                delay(IME_OPEN_DELAY_MS * 2)
                                keyboard?.show() // a show() racing the input session start can be dropped
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        // An empty field has no cursor to move: let Left/Right leave it.
                        Key.DirectionLeft -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
            enabled = enabled,
            readOnly = !editing,
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Text, fontSize = 20.sp),
            placeholder = hint?.let { { Text(it, color = AppColors.TextMuted.copy(alpha = 0.7f), fontSize = 18.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrect = false),
            keyboardActions = KeyboardActions(
                onDone = { editing = false; keyboard?.hide(); onImeAction() },
                onGo = { editing = false; keyboard?.hide(); onImeAction() },
                onNext = { editing = false; onImeAction() },
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            shape = shape,
        )
    }
}

internal const val STRAY_KEY_WINDOW_MS = 500L
private const val IME_OPEN_DELAY_MS = 120L

/**
 * True when [next] is [current] plus one appended character and it arrived within
 * [STRAY_KEY_WINDOW_MS] of the Center/Enter press that opened the keyboard: the keyboard's
 * own focused key, not something the user typed.
 */
internal fun isStrayFromOpen(current: String, next: String, msSinceOpen: Long): Boolean =
    msSinceOpen in 0 until STRAY_KEY_WINDOW_MS && next.length == current.length + 1 && next.startsWith(current)
