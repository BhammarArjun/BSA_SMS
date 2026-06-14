package com.local.smsllm.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.smsllm.ui.components.HeroBackground
import com.local.smsllm.ui.components.LedgerCard
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.theme.CanvasDark
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.ErrorRed
import com.local.smsllm.ui.theme.HeroSerif
import com.local.smsllm.ui.theme.OnSurfaceMutedDark

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Navigate when fully done
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.DONE) onComplete()
    }

    // Permission launcher — RECEIVE_SMS + READ_SMS + POST_NOTIFICATIONS
    val permissionsToRequest = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> viewModel.onPermissionsResult(grants) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .grainOverlay(alpha = 0.04f),
    ) {
        // Radial glow background
        com.local.smsllm.ui.components.HeroGlow(
            modifier = Modifier.matchParentSize(),
            creditAlpha = 0.10f,
            debitAlpha = 0.06f,
        )

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 })
                    .togetherWith(fadeOut(tween(300)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding_step",
        ) { step ->
            when (step) {
                OnboardingStep.HERO -> HeroStep(
                    onContinue = viewModel::proceedFromHero,
                )
                OnboardingStep.PERMISSIONS -> PermissionsStep(
                    state = state,
                    onRequestPermissions = { permLauncher.launch(permissionsToRequest) },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                    onRecheckPermissions = viewModel::recheckPermissions,
                )
                OnboardingStep.DOWNLOAD -> DownloadStep(
                    state = state,
                    onStartDownload = viewModel::startDownload,
                    onRetry = viewModel::retryDownload,
                )
                OnboardingStep.DONE -> {
                    // Handled by LaunchedEffect above
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── Step 0: Privacy Hero ──────────────────────────────────────────────────────
@Composable
private fun HeroStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Lock icon
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = BrandGold,
        )

        Spacer(Modifier.height(24.dp))

        // Hero headline — Fraunces
        Text(
            text = "Your money.\nYour device.\nYour ledger.",
            style = HeroSerif,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 54.sp,
        )

        Spacer(Modifier.height(24.dp))

        // Privacy statement
        Text(
            text = "Everything stays on your phone.\nNothing is ever uploaded.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceMutedDark,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "SMS → local AI → private ledger.",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = CreditGreen.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        // Features list
        FeaturePill("Runs entirely offline after setup")
        Spacer(Modifier.height(8.dp))
        FeaturePill("No accounts, no cloud, no tracking")
        Spacer(Modifier.height(8.dp))
        FeaturePill("Open-source on-device AI model")

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
        ) {
            Text("Get Started", color = CanvasDark, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FeaturePill(text: String) {
    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        border = null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CreditGreen,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Step 1: Permissions ───────────────────────────────────────────────────────
@Composable
private fun PermissionsStep(
    state: OnboardingUiState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecheckPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = BrandGold,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Read your SMS",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "The app reads your bank SMS messages locally to detect transactions. " +
                "No messages leave your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMutedDark,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Permission status cards
        PermissionRow(
            label = "Receive SMS",
            description = "To detect new transactions as they arrive",
            granted = state.smsGranted,
        )
        Spacer(Modifier.height(8.dp))
        PermissionRow(
            label = "Read SMS",
            description = "To scan existing bank messages on setup",
            granted = state.smsGranted,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                label = "Notifications",
                description = "To notify when transactions are processed",
                granted = state.notifGranted,
                optional = true,
            )
        }

        Spacer(Modifier.height(32.dp))

        if (!state.permsDenied) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
            ) {
                Text("Grant Permission", color = CanvasDark, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            // Denied — show path to system settings
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Text(
                            "Permission denied",
                            style = MaterialTheme.typography.titleSmall,
                            color = ErrorRed,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Please grant SMS permission in system settings to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceMutedDark,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
            ) {
                Text("Open App Settings", color = CanvasDark, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRecheckPermissions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I've granted it — continue", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    optional: Boolean = false,
) {
    LedgerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (granted) CreditGreen else OnSurfaceMutedDark,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label + if (optional) " (optional)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMutedDark,
                )
            }
        }
    }
}

// ── Step 2: Model Download ────────────────────────────────────────────────────
@Composable
private fun DownloadStep(
    state: OnboardingUiState,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Model ready — skip
        if (state.modelAlreadyReady) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = CreditGreen,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "AI model ready",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Qwen3-0.6B is already on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMutedDark,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = BrandGold, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            return@Column
        }

        // Error state
        AnimatedVisibility(visible = state.downloadError != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Download failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = ErrorRed,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    state.downloadError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMutedDark,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // Header
        Text(
            text = "Download AI model",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "One-time download of Qwen3-0.6B (~475 MB).\nAfter this, everything works offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMutedDark,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Progress area
        if (state.isDownloading || state.downloadDone) {
            LedgerCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Qwen3-0.6B",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${state.downloadPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CreditGreen,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.downloadFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = CreditGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${"%.1f".format(state.downloadedMb)} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMutedDark,
                        )
                        Text(
                            "${"%.0f".format(state.totalMb)} MB total",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMutedDark,
                        )
                    }
                }
            }

            if (state.isDownloading) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Keep the app open while downloading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMutedDark,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Action button
        when {
            state.downloadDone -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CreditGreen, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Model downloaded!", style = MaterialTheme.typography.titleMedium, color = CreditGreen)
            }
            state.downloadError != null -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                ) {
                    Text("Retry Download", color = CanvasDark)
                }
            }
            !state.isDownloading -> {
                Button(
                    onClick = onStartDownload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                ) {
                    Text("Download Model", color = CanvasDark, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
