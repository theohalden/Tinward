package dev.kosh.financetracker.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.kosh.financetracker.security.BiometricKeys
import dev.kosh.financetracker.security.VaultCore
import dev.kosh.financetracker.ui.components.KoshWordmark
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.delay

private const val N = VaultCore.PIN_LENGTH

/** First-run PIN creation: enter a 6-digit PIN, then enter it again. Mandatory. */
@Composable
fun SetupLockScreen(viewModel: LockViewModel) {
    val ui by viewModel.ui.collectAsState()
    var first by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    val confirming = first.isNotEmpty()

    fun submit(pin: String) {
        if (!confirming) {
            VaultCore.pinProblem(pin.toCharArray())?.let { viewModel.showError(it); entry = ""; return }
            first = pin; entry = ""; viewModel.clearError()
        } else {
            if (pin != first) { viewModel.showError("The two PINs don't match. Start again."); first = ""; entry = ""; return }
            viewModel.setup(first, pin)
            first = ""; entry = ""
        }
    }

    Column(
        Modifier.fillMaxSize().background(Tinward.colors.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.lg))
        KoshWordmark()
        Spacer(Modifier.height(Spacing.lg))
        Text(
            if (confirming) "Enter it again" else "Create your PIN",
            style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (ui.busy) "Securing your data\u2026" else "$N digits. It locks your data on this phone.",
            style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        PinDots(entry.length, error = ui.error != null)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            ui.error ?: " ", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative, textAlign = TextAlign.Center,
            modifier = Modifier.height(24.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        NumPad(
            enabled = !ui.busy,
            onDigit = { d ->
                if (entry.length < N) { viewModel.clearError(); entry += d; if (entry.length == N) submit(entry) }
            },
            onDelete = { entry = entry.dropLast(1) },
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            "There is no account and no reset. If you forget this PIN, only an encrypted backup (Settings \u2192 Security) can bring your data back.",
            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
fun UnlockScreen(viewModel: LockViewModel) {
    val ui by viewModel.ui.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    var entry by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val waitSeconds = ((ui.retryAtMillis - now) / 1000).coerceAtLeast(0)
    val bioReady = remember(activity) { activity != null && viewModel.vault.biometricEnabled && BiometricKeys.canUse(activity) }

    fun tryBiometric() {
        activity ?: return
        BiometricKeys.unlock(activity, viewModel.vault, onKey = viewModel::unlockWithKey, onFail = { msg ->
            // "Use PIN" and cancellations are not errors worth showing.
            if (!msg.contains("PIN", true) && !msg.contains("cancel", true)) viewModel.showError(msg)
        })
    }

    LaunchedEffect(Unit) { if (bioReady) tryBiometric() }
    LaunchedEffect(ui.retryAtMillis) {
        while (System.currentTimeMillis() < ui.retryAtMillis) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    // A wrong or throttled attempt clears the dots so the next try starts fresh.
    LaunchedEffect(ui.error) { if (ui.error != null) entry = "" }

    Column(
        Modifier.fillMaxSize().background(Tinward.colors.background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        KoshWordmark()
        Spacer(Modifier.height(Spacing.xl))
        Text("Enter your PIN", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
        Spacer(Modifier.height(Spacing.lg))
        PinDots(entry.length, error = ui.error != null)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (waitSeconds > 0) "Too many attempts. Try again in ${waitSeconds}s." else if (ui.busy) "Unlocking\u2026" else ui.error ?: " ",
            style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative, textAlign = TextAlign.Center,
            modifier = Modifier.height(48.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        NumPad(
            enabled = !ui.busy && waitSeconds == 0L,
            onDigit = { d ->
                if (entry.length < N) { viewModel.clearError(); entry += d; if (entry.length == N) viewModel.unlock(entry) }
            },
            onDelete = { entry = entry.dropLast(1) },
            bottomLeft = if (bioReady) ({
                Text(
                    "Face / touch", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.accentText,
                    textAlign = TextAlign.Center, modifier = Modifier.clickable { tryBiometric() }.padding(Spacing.sm),
                )
            }) else null,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}
