package com.local.smsllm.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.components.HeroGlow
import com.local.smsllm.ui.theme.HeroSerif
import com.local.smsllm.ui.theme.OnSurfaceMutedDark

/**
 * Dashboard placeholder — the full dashboard implementation will be added by
 * a subsequent agent. This screen is themed and wired into the nav graph.
 */
@Composable
fun DashboardScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .grainOverlay(alpha = 0.03f),
    ) {
        HeroGlow(modifier = Modifier.matchParentSize(), creditAlpha = 0.08f, debitAlpha = 0.05f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Ledger",
                style = HeroSerif,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Dashboard coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMutedDark,
            )
        }
    }
}
