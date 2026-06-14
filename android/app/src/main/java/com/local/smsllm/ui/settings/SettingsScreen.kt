package com.local.smsllm.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.llm.BackendChoice
import com.local.smsllm.ui.components.LedgerCard
import com.local.smsllm.ui.components.SectionHeader
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.OnSurfaceMutedDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Settings screen — full implementation.
 * [onPermissionsChanged] is forwarded to the gate-recheck callback in AppNav.
 */
@Composable
fun SettingsScreen(
    onPermissionsChanged: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // CSV export launcher — opens the system file picker for the user to choose a destination.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setExportStatus(ExportStatus.Building)
            scope.launch {
                try {
                    val csv = viewModel.csvForExport()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.setExportStatus(ExportStatus.Exported)
                } catch (e: Exception) {
                    viewModel.setExportStatus(ExportStatus.Failed(e.message ?: "Unknown error"))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .grainOverlay(alpha = 0.03f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Page heading ─────────────────────────────────────────────────
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // ── 1. Processing ─────────────────────────────────────────────────
            SectionHeader(title = "Processing")

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Interval slider
                    val intervalSteps = ((240 - 15) / 5) - 1   // steps between 15 and 240 exclusive
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Run every",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.processingIntervalMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandGold,
                        )
                    }
                    Slider(
                        value = state.processingIntervalMinutes.toFloat(),
                        onValueChange = { viewModel.setInterval(it.roundToInt()) },
                        valueRange = 15f..240f,
                        steps = intervalSteps,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandGold,
                            activeTrackColor = BrandGold,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    // Requires Charging
                    SettingsSwitchRow(
                        label = "Requires charging",
                        checked = state.requiresCharging,
                        onCheckedChange = viewModel::setRequiresCharging,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Battery not low
                    SettingsSwitchRow(
                        label = "Battery not low",
                        checked = state.requiresBatteryNotLow,
                        onCheckedChange = viewModel::setRequiresBatteryNotLow,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Max messages per run stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Max messages per run",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.setMaxMessagesPerRun((state.maxMessagesPerRun - 10).coerceAtLeast(10)) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "${state.maxMessagesPerRun}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(
                            onClick = { viewModel.setMaxMessagesPerRun((state.maxMessagesPerRun + 10).coerceAtMost(500)) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── 2. Model & backend ────────────────────────────────────────────
            SectionHeader(title = "Model & Backend")

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Backend selector (segmented chips)
                    Text(
                        text = "Backend preference",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BackendChoice.entries.forEach { choice ->
                            FilterChip(
                                selected = state.backendPreference == choice,
                                onClick = { viewModel.setBackend(choice) },
                                label = {
                                    Text(
                                        text = choice.name,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandGold.copy(alpha = 0.20f),
                                    selectedLabelColor = BrandGold,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Model status
                    val spec = ModelRegistry.QWEN3_0_6B
                    val sizeKb = spec.expectedBytes / 1024
                    val sizeMb = sizeKb / 1024

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spec.filename.removeSuffix(".litertlm"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = if (state.modelReady) "Ready · ~$sizeMb MB"
                                       else "Not downloaded · ~$sizeMb MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.modelReady) CreditGreen else OnSurfaceMutedDark,
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.redownloadModel() },
                            enabled = state.downloadProgress == null,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            if (state.downloadProgress != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGold,
                                )
                            } else {
                                Text(
                                    text = if (state.modelReady) "Re-download" else "Download",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // Download progress bar
                    if (state.downloadProgress != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = BrandGold,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        val pct = ((state.downloadProgress ?: 0f) * 100).roundToInt()
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }

                    // Download error
                    if (state.downloadError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Download failed. Tap Re-download to retry.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── 3. Analytics ──────────────────────────────────────────────────
            SectionHeader(title = "Analytics")

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Confidence threshold",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%.0f%%".format(state.confidenceThreshold * 100),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandGold,
                        )
                    }
                    Slider(
                        value = state.confidenceThreshold.toFloat(),
                        onValueChange = { viewModel.setConfidenceThreshold(it.toDouble()) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandGold,
                            activeTrackColor = BrandGold,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Hide transactions below this confidence from analytics.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMutedDark,
                    )
                }
            }

            // ── 4. Data ───────────────────────────────────────────────────────
            SectionHeader(title = "Data")

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import existing messages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (state.importRunning) {
                                Text(
                                    text = "Importing & processing… runs in the background.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceMutedDark,
                                )
                            } else if (state.importResult != null) {
                                Text(
                                    text = state.importResult!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CreditGreen,
                                )
                            } else {
                                Text(
                                    text = "Imports the last 30 days and processes them. Keeps running if you leave this screen.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceMutedDark,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.importInbox() },
                            enabled = !state.importRunning,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            if (state.importRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGold,
                                )
                            } else {
                                Text("Import", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Import progress bar (indeterminate — the background job has no live count)
                    if (state.importRunning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = BrandGold,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Export CSV
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Export CSV",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            when (val status = state.exportStatus) {
                                ExportStatus.Idle -> Text(
                                    text = "Save all transactions to a CSV file.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceMutedDark,
                                )
                                ExportStatus.Building -> Text(
                                    text = "Building…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceMutedDark,
                                )
                                ExportStatus.Exported -> Text(
                                    text = "Exported successfully.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CreditGreen,
                                )
                                is ExportStatus.Failed -> Text(
                                    text = "Export failed. Try again.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { exportLauncher.launch("transactions.csv") },
                            enabled = state.exportStatus !is ExportStatus.Building,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            if (state.exportStatus is ExportStatus.Building) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGold,
                                )
                            } else {
                                Text("Export", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── 5. Privacy ────────────────────────────────────────────────────
            SectionHeader(title = "Privacy")

            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = CreditGreen,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Everything stays on your phone. The only network use is the " +
                            "one-time model download. No analytics, no tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Reusable settings row ─────────────────────────────────────────────────────

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = BrandGold,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
