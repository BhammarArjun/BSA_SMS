package com.local.smsllm.ui.settings

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
import com.local.smsllm.ui.theme.OnSurfaceMutedDark

/**
 * Settings placeholder — full implementation added by subsequent agent.
 * [onPermissionsChanged] is called when the user grants/revokes permissions from here.
 */
@Composable
fun SettingsScreen(
    onPermissionsChanged: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .grainOverlay(alpha = 0.03f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Settings screen coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMutedDark,
            )
        }
    }
}
