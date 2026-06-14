package com.local.smsllm.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.smsllm.data.SmsMessageEntity
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.Category
import com.local.smsllm.domain.TxnDirection
import com.local.smsllm.ui.components.CategoryGlyph
import com.local.smsllm.ui.components.HeroBackground
import com.local.smsllm.ui.components.LedgerCard
import com.local.smsllm.ui.components.MoneyTextLarge
import com.local.smsllm.ui.components.SectionHeader
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.DebitAmber
import com.local.smsllm.ui.theme.HeroSerif
import com.local.smsllm.ui.theme.OnSurfaceMutedDark
import com.local.smsllm.ui.theme.PillShape
import com.local.smsllm.ui.theme.amountColor
import com.local.smsllm.ui.util.formatInr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    // Collect one-shot events for snackbar
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHost.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .grainOverlay(alpha = 0.03f),
        ) {
            when {
                state.loading -> LoadingState()
                state.notFound -> NotFoundState(onBack = onBack)
                state.txn != null -> DetailContent(
                    txn = state.txn!!,
                    sms = state.sms,
                    onEditCategory = viewModel::editCategory,
                    onEditFields = viewModel::editFields,
                    onMarkNotTransaction = viewModel::markNotTransaction,
                    onReverify = viewModel::reverify,
                )
            }
        }
    }
}

// ── Detail content ────────────────────────────────────────────────────────────

@Composable
private fun DetailContent(
    txn: TransactionEntity,
    sms: SmsMessageEntity?,
    onEditCategory: (String?) -> Unit,
    onEditFields: (String?, Double?, String?, String?) -> Unit,
    onMarkNotTransaction: () -> Unit,
    onReverify: () -> Unit,
) {
    // Dialog state
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNotTxnConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hero section
        item {
            HeroSection(txn = txn)
        }

        // Details card
        item {
            LedgerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SectionHeader(title = "Details")
                DetailRow(label = "Date", value = txn.dateText ?: "—")
                DetailDivider()
                DetailRow(label = "Currency", value = txn.currency ?: "INR")
                DetailDivider()
                DetailRow(
                    label = "Direction",
                    value = txn.direction?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                )
                DetailDivider()
                DetailRow(label = "Amount", value = txn.amount?.let { formatInr(it) } ?: "—")
                DetailDivider()
                DetailRow(label = "Counterparty", value = txn.counterparty ?: "—")
                DetailDivider()
                val category = Category.fromId(txn.category)
                DetailRow(
                    label = "Category",
                    value = category?.let { "${it.emoji} ${it.label}" } ?: "None",
                )
            }
        }

        // SMS section
        if (sms != null) {
            item {
                LedgerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    SectionHeader(title = "SMS")
                    DetailRow(label = "Sender", value = sms.sender)
                    DetailDivider()
                    DetailRow(
                        label = "Received",
                        value = formatEpochMs(sms.receivedAt),
                    )
                    DetailDivider()
                    // Raw SMS body — shown on screen, never logged
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                                .padding(10.dp),
                        ) {
                            Text(
                                text = sms.body,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Metadata card
        item {
            LedgerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SectionHeader(title = "Metadata")
                DetailRow(label = "Model", value = txn.modelId)
                DetailDivider()
                DetailRow(label = "Backend", value = txn.backend)
                DetailDivider()
                DetailRow(
                    label = "Confidence",
                    value = "${(txn.confidence * 100).toInt()}%",
                )
                DetailDivider()
                DetailRow(
                    label = "Status",
                    value = if (sms != null) sms.status.name else "Unknown",
                )
                DetailDivider()
                DetailRow(
                    label = "Included",
                    value = if (txn.includedInAnalytics) "Yes" else "No",
                )
            }
        }

        // Action buttons
        item {
            ActionButtons(
                onEditCategory = { showCategoryDialog = true },
                onEditFields = { showEditDialog = true },
                onMarkNotTransaction = { showNotTxnConfirm = true },
                onReverify = onReverify,
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showCategoryDialog) {
        CategoryPickerDialog(
            currentCategoryId = txn.category,
            onDismiss = { showCategoryDialog = false },
            onSelect = { categoryId ->
                onEditCategory(categoryId)
                showCategoryDialog = false
            },
        )
    }

    if (showEditDialog) {
        EditFieldsDialog(
            txn = txn,
            onDismiss = { showEditDialog = false },
            onSave = { direction, amount, dateText, counterparty ->
                onEditFields(direction, amount, dateText, counterparty)
                showEditDialog = false
            },
        )
    }

    if (showNotTxnConfirm) {
        AlertDialog(
            onDismissRequest = { showNotTxnConfirm = false },
            title = {
                Text(
                    text = "Not a transaction?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "This will exclude the entry from analytics and mark the SMS as non-transaction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMutedDark,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMarkNotTransaction()
                        showNotTxnConfirm = false
                    },
                ) {
                    Text("Confirm", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotTxnConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Hero section ──────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(txn: TransactionEntity) {
    val category = Category.fromId(txn.category)

    HeroBackground(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large),
        glowCreditAlpha = if (txn.direction?.lowercase() == "credit") 0.18f else 0.05f,
        glowDebitAlpha = if (txn.direction?.lowercase() == "debit") 0.15f else 0.05f,
        grain = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // Amount
            if (txn.amount != null) {
                MoneyTextLarge(
                    amount = txn.amount,
                    direction = txn.direction,
                    compact = true,
                )
            } else {
                Text(
                    text = "Amount unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurfaceMutedDark,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Direction label
            val directionLabel = when (txn.direction?.lowercase()) {
                "credit" -> "Credit · received"
                "debit" -> "Debit · sent"
                else -> "Direction unknown"
            }
            val dirColor = amountColor(direction = txn.direction)
            Text(
                text = directionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = dirColor.copy(alpha = 0.80f),
            )

            Spacer(Modifier.height(16.dp))

            // Counterparty
            Text(
                text = txn.counterparty ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(12.dp))

            // Category chip + confidence
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Category chip
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryGlyph(category = category, size = 24.dp)
                    Text(
                        text = category?.label ?: "Uncategorised",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Confidence
                Text(
                    text = "${(txn.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMutedDark,
                )
            }

            // "Edited by you" badge
            if (txn.userEdited) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(BrandGold.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "✏ Edited by you",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandGold,
                    )
                }
            }
        }
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    onEditCategory: () -> Unit,
    onEditFields: () -> Unit,
    onMarkNotTransaction: () -> Unit,
    onReverify: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onEditCategory,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = BrandGold.copy(alpha = 0.12f),
                    contentColor = BrandGold,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            FilledTonalButton(
                onClick = onEditFields,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Edit details",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onReverify,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CreditGreen.copy(alpha = 0.10f),
                    contentColor = CreditGreen,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Re-verify",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            FilledTonalButton(
                onClick = onMarkNotTransaction,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Not a txn",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Category picker dialog ────────────────────────────────────────────────────

@Composable
private fun CategoryPickerDialog(
    currentCategoryId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose category",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // "None" / clear option
                CategoryPickerRow(
                    emoji = "✕",
                    label = "None",
                    selected = currentCategoryId == null,
                    onClick = { onSelect(null) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Category.entries.forEach { category ->
                    CategoryPickerRow(
                        emoji = category.emoji,
                        label = category.label,
                        selected = category.id == currentCategoryId,
                        onClick = { onSelect(category.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CategoryPickerRow(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) BrandGold.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) BrandGold else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(text = "✓", color = BrandGold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ── Edit fields dialog ────────────────────────────────────────────────────────

@Composable
private fun EditFieldsDialog(
    txn: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (direction: String?, amount: Double?, dateText: String?, counterparty: String?) -> Unit,
) {
    var amountText by remember { mutableStateOf(txn.amount?.toString() ?: "") }
    var counterpartyText by remember { mutableStateOf(txn.counterparty ?: "") }
    var dateText by remember { mutableStateOf(txn.dateText ?: "") }
    var selectedDirection by remember {
        mutableStateOf(TxnDirection.fromString(txn.direction) ?: TxnDirection.DEBIT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit transaction",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Direction toggle
                Text(
                    text = "Direction",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMutedDark,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TxnDirection.entries.forEach { dir ->
                        val isSelected = dir == selectedDirection
                        val dirColor = if (dir == TxnDirection.CREDIT) CreditGreen else DebitAmber
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(PillShape)
                                .background(
                                    if (isSelected) dirColor.copy(alpha = 0.18f)
                                    else Color.Transparent,
                                )
                                .clickable { selectedDirection = dir }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = dir.name.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) dirColor else OnSurfaceMutedDark,
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Counterparty
                OutlinedTextField(
                    value = counterpartyText,
                    onValueChange = { counterpartyText = it },
                    label = { Text("Counterparty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Date text
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = amountText.trim().toDoubleOrNull()
                    val parsedDirection = selectedDirection.name.lowercase()
                    val parsedDate = dateText.trim().takeIf { it.isNotBlank() }
                    val parsedCounterparty = counterpartyText.trim().takeIf { it.isNotBlank() }
                    onSave(parsedDirection, parsedAmount, parsedDate, parsedCounterparty)
                },
            ) {
                Text("Save", color = BrandGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ── Detail row helpers ────────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceMutedDark,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}

// ── Loading and not found states ──────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMutedDark,
        )
    }
}

@Composable
private fun NotFoundState(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Transaction not found",
                style = HeroSerif.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBack) {
                Text("Go back", color = BrandGold)
            }
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatEpochMs(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
