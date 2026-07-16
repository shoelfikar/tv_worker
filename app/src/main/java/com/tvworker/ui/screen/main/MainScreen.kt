package com.tvworker.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tvworker.ui.components.StatusCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToLogs: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshAccessibilityStatus(context)
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("TV Worker", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        // Accessibility status + button in one row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusCard(
                label = "Accessibility Service",
                value = if (state.isAccessibilityEnabled) "Aktif" else "Nonaktif",
                isActive = state.isAccessibilityEnabled,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.padding(start = 8.dp),
                colors = if (state.isAccessibilityEnabled) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (state.isAccessibilityEnabled) "Settings" else "Aktifkan")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Auto-approve toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Approve")
                Switch(
                    checked = state.isAutoApproveEnabled,
                    onCheckedChange = { viewModel.toggleAutoApprove(it) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stats
        StatusCard(
            label = "Total Approved",
            value = "${state.approvedCount} koneksi",
            isActive = state.approvedCount > 0
        )

        // Last approval
        state.lastApproval?.let { log ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Terakhir", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(log.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatTimestamp(log.timestamp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Navigate to logs
        OutlinedButton(
            onClick = onNavigateToLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lihat Semua Log")
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
