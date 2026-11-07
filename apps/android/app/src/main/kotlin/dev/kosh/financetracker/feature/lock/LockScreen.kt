package dev.kosh.financetracker.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import dev.kosh.financetracker.security.BiometricKeys
import dev.kosh.financetracker.ui.components.KoshWordmark
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.delay

/** First-run PIN creation. Mandatory: there is no way past this screen without a lock. */
@Composable
fun SetupLockScreen(viewModel: LockViewModel) {
    val ui by viewModel.ui.collectAsState()
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().background(Tinward.colors.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        KoshWordmark()
        Spacer(Modifier.height(Spacing.md))
        Text("Lock your money data", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
        Text(
            "Tinward keeps everything on this phone, encrypted with a key that only your PIN can unlock.",
            style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textSecondary,
        )
        TinwardTextField(
            value = secret, onValueChange = { secret = it.filter { c -> !digits || c.isDigit() }; viewModel.clearError() },
            label = if (digits) "Choose a PIN (6 or more digits)" else "Choose a passphrase (6 or more characters)",
            secret = true, digitsOnly = digits,
        )
        TinwardTextField(
            value = confirm, onValueChange = { confirm = it.filter { c -> !digits || c.isDigit() }; viewModel.clearError() },
            label = "Enter it again", secret = true, digitsOnly = digits,
        )
        ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative) }
        Text(
            if (digits) "Use a passphrase instead" else "Use a numeric PIN instead",
            style = MaterialTheme.typography.labelLarge, color = Tinward.colors.accentText,
            modifier = Modifier.clickable { digits = !digits; secret = ""; confirm = "" }.padding(vertical = Spacing.xs),
        )
        PrimaryButton(if (ui.busy) "Securing your data…" else "Create lock", { viewModel.setup(secret, confirm) }, enabled = !ui.busy && secret.isNotEmpty())
        Text(
            "There is no account and no email reset. If you forget this PIN, only an encrypted backup (Settings → Backup) can bring your data back. Make one once you are in.",
            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
fun UnlockScreen(viewModel: LockViewModel) {
    val ui by viewModel.ui.collectAsState()
    val activity = LocalContext.current as? FragmentActivity
    var secret by remember { mutableStateOf("") }
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
    LaunchedEffect(ui.retryAtMillis) { while (System.currentTimeMillis() < ui.retryAtMillis) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }

    Column(
        Modifier.fillMaxSize().background(Tinward.colors.background).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        KoshWordmark()
        Spacer(Modifier.height(Spacing.lg))
        Text("Enter your PIN", style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary)
        TinwardTextField(
            value = secret, onValueChange = { secret = it; viewModel.clearError() },
            label = "PIN or passphrase", secret = true,
        )
        if (waitSeconds > 0) {
            Text("Too many attempts. Try again in ${waitSeconds}s.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative)
        } else {
            ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.negative) }
        }
        PrimaryButton(
            if (ui.busy) "Unlocking…" else "Unlock",
            { viewModel.unlock(secret).also { secret = "" } },
            enabled = !ui.busy && secret.isNotEmpty() && waitSeconds == 0L,
        )
        if (bioReady) SecondaryButton("Use biometrics", { tryBiometric() })
    }
}
