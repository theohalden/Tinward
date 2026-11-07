package dev.kosh.financetracker.feature.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.kosh.financetracker.security.AutoLock
import dev.kosh.financetracker.security.BackupManager
import dev.kosh.financetracker.security.BiometricKeys
import dev.kosh.financetracker.security.RestoreResult
import dev.kosh.financetracker.security.Vault
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.TinwardTextField
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Dialog {
    object None : Dialog
    object ChangePin : Dialog
    object NewBackup : Dialog
    class RestoreFrom(val uri: Uri) : Dialog
}

/** App lock, biometrics, auto-lock, screen privacy, and encrypted backup/restore. */
@Composable
fun SecuritySection(vault: Vault, backup: BackupManager, note: String?, onNote: (String?) -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val autoLock by vault.autoLock.collectAsState()
    val secureScreen by vault.secureScreen.collectAsState()
    var bio by remember { mutableStateOf(vault.biometricEnabled) }
    var dialog by remember { mutableStateOf<Dialog>(Dialog.None) }
    var pendingBackupPass by remember { mutableStateOf<CharArray?>(null) }
    val bioAvailable = remember(activity) { activity != null && BiometricKeys.canUse(activity) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingBackupPass
        pendingBackupPass = null
        if (uri == null || pass == null) { pass?.fill('\u0000'); return@rememberLauncherForActivityResult }
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { backup.create(pass, it) } }.exceptionOrNull()
            }
            pass.fill('\u0000')
            onNote(if (err == null) "Backup saved. Keep the file and the passphrase safe: without both it cannot be restored." else "Backup failed: ${err.message}")
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = Dialog.RestoreFrom(uri)
    }

    KoshCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("App lock", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                Text("On. Data is encrypted with a key only your PIN unlocks.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
            }
        }
        HorizontalDivider(color = Tinward.colors.divider)
        Row2("Change PIN", "Your data stays as it is; only the PIN changes.") { dialog = Dialog.ChangePin }
        if (bioAvailable) {
            HorizontalDivider(color = Tinward.colors.divider)
            SwitchRow("Biometric unlock", "Fingerprint or face. Your PIN always still works.", bio) { want ->
                if (want && activity != null) BiometricKeys.enable(activity, vault) { err ->
                    bio = vault.biometricEnabled
                    if (err != null) onNote(err)
                } else { vault.clearBiometric(); bio = false }
            }
        }
        HorizontalDivider(color = Tinward.colors.divider)
        Row2("Auto-lock", autoLock.label) {
            val all = AutoLock.values()
            vault.setAutoLock(all[(autoLock.ordinal + 1) % all.size])
        }
        HorizontalDivider(color = Tinward.colors.divider)
        SwitchRow("Hide app in recents and block screenshots", "Stops the app preview showing your money in the task switcher.", secureScreen) { vault.setSecureScreen(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        dev.kosh.financetracker.ui.components.SectionLabel("Backup")
        KoshCard {
            Row2("Create encrypted backup", note ?: "Saves one file protected by a passphrase you choose. Needed if you forget your PIN or change phone.") { dialog = Dialog.NewBackup }
            HorizontalDivider(color = Tinward.colors.divider)
            Row2("Restore from backup", "Replaces everything on this phone with the backup.") { openLauncher.launch(arrayOf("*/*")) }
        }
    }

    when (val d = dialog) {
        Dialog.None -> Unit
        Dialog.ChangePin -> FormDialog(
            title = "Change PIN",
            fields = listOf("Current PIN", "New PIN or passphrase (6+)", "Repeat new"),
            confirm = "Change",
            onDismiss = { dialog = Dialog.None },
        ) { (cur, new, again) ->
            if (new != again) "The new entries don't match."
            else vault.changeSecret(cur.toCharArray(), new.toCharArray()).also { if (it == null) { bio = false; onNote("PIN changed. Turn biometric unlock back on if you use it."); dialog = Dialog.None } }
        }
        Dialog.NewBackup -> FormDialog(
            title = "Backup passphrase",
            fields = listOf("Passphrase (8+ characters)", "Repeat passphrase"),
            confirm = "Choose file",
            onDismiss = { dialog = Dialog.None },
            body = "This is separate from your PIN. If you lose it, the backup cannot be opened by anyone, including us.",
        ) { (p, again) ->
            when {
                p.length < 8 -> "Use at least 8 characters."
                p != again -> "The two entries don't match."
                else -> { pendingBackupPass = p.toCharArray(); dialog = Dialog.None
                    createLauncher.launch("tinward-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.twbak"); null }
            }
        }
        is Dialog.RestoreFrom -> FormDialog(
            title = "Restore backup",
            fields = listOf("Backup passphrase", "Current PIN"),
            confirm = "Restore",
            onDismiss = { dialog = Dialog.None },
            body = "This replaces all data on this phone. The app restarts, then asks for your current PIN.",
        ) { (bp, pin) ->
            val r = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(d.uri)!!.use { backup.restore(bp.toCharArray(), pin.toCharArray(), it) }
            }
            (r as? RestoreResult.Failed)?.message
        }
    }
}

@Composable
private fun Row2(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
        )
    }
}

/** Simple secret-entry dialog. [onSubmit] returns an error to show, or null when it handled success. */
@Composable
private fun FormDialog(
    title: String,
    fields: List<String>,
    confirm: String,
    onDismiss: () -> Unit,
    body: String? = null,
    onSubmit: suspend (List<String>) -> String?,
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        tonalElevation = androidx.compose.ui.unit.Dp(0f),
        containerColor = Tinward.colors.surface,
        title = { Text(title, color = Tinward.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary) }
                fields.forEachIndexed { i, label ->
                    TinwardTextField(values.value[i], { v -> values.value = values.value.toMutableList().also { it[i] = v }; error = null }, label, secret = true)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.negative) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && values.value.all { it.isNotEmpty() }, onClick = {
                busy = true
                scope.launch { error = onSubmit(values.value); busy = false }
            }) { Text(if (busy) "Working\u2026" else confirm, color = Tinward.colors.accentText) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = Tinward.colors.textSecondary) } },
    )
}
