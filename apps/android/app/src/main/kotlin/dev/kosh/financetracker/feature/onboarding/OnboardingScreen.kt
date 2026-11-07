package dev.kosh.financetracker.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.ui.components.KoshWordmark
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

private data class Page(val title: String, val emphasis: String?, val body: String, val points: List<String>)

private val PAGES = listOf(
    Page(
        "All your money.", "In perspective.",
        "A personal finance tracker that brings clarity to your money: tracking, understanding and planning it with intention.",
        emptyList(),
    ),
    Page(
        "Private by design.", "Stays on your phone.",
        "Tinward has no account and no cloud finance backend.",
        listOf("Bank SMS are read on this device only", "Nothing is uploaded or shared", "Export your data whenever you want"),
    ),
    Page(
        "Bring your money in.", null,
        "Start with what you already have, then add the rest.",
        listOf("Automatic: allow SMS access on the next screen", "Import a bank statement (CSV or Excel)", "Add anything the bank never texted"),
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val page = PAGES[step]
    val last = step == PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tinward.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            KoshWordmark()
            if (!last) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = Tinward.colors.textSecondary,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onFinish).padding(horizontal = Spacing.sm).wrapCenter(),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(page.title, style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
        page.emphasis?.let {
            Text(it, style = MaterialTheme.typography.displaySmall.copy(fontStyle = FontStyle.Italic), color = Tinward.colors.accentText)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textSecondary)
        Spacer(Modifier.height(Spacing.lg))
        page.points.forEach { p ->
            Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Tinward.colors.accent))
                Spacer(Modifier.size(Spacing.ms))
                Text(p, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PAGES.indices.forEach { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == step) 20.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i == step) Tinward.colors.accent else Tinward.colors.track),
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            label = if (last) "Get started" else "Next",
            onClick = { if (last) onFinish() else step += 1 },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.padding(vertical = 12.dp))
