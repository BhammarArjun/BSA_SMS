package com.local.smsllm.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.Category
import com.local.smsllm.ui.components.CategoryGlyph
import com.local.smsllm.ui.components.MoneyTextSmall
import com.local.smsllm.ui.components.SectionHeader
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.DebitAmber
import com.local.smsllm.ui.theme.DividerDark
import com.local.smsllm.ui.theme.FrauncesFamily
import com.local.smsllm.ui.theme.HankenFamily

// ── Confidence dot colors ─────────────────────────────────────────────────────
private val ConfidenceGreen = CreditGreen
private val ConfidenceAmber = DebitAmber
private val ConfidenceGrey = Color(0xFF555750)

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun TransactionsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filter
    val hasActiveFilters = filter.query.isNotBlank()
        || filter.categoryIds.isNotEmpty()
        || filter.direction != null
        || filter.minConfidence > 0.0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .grainOverlay(alpha = 0.03f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Screen title ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FrauncesFamily,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (hasActiveFilters) {
                    TextButton(onClick = { viewModel.clearFilters() }) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandGold,
                        )
                    }
                }
            }

            // ── Search field ──────────────────────────────────────────────────
            LedgerSearchField(
                query = filter.query,
                onQueryChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Filter chip row ───────────────────────────────────────────────
            FilterChipRow(
                direction = filter.direction,
                selectedCategories = filter.categoryIds,
                minConfidence = filter.minConfidence,
                onDirectionChange = { viewModel.setDirectionFilter(it) },
                onCategoryToggle = { id ->
                    val next = if (id in filter.categoryIds)
                        filter.categoryIds - id else filter.categoryIds + id
                    viewModel.setCategoryFilter(next)
                },
                onMinConfidenceChange = { viewModel.setMinConfidence(it) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Count badge ───────────────────────────────────────────────────
            if (!uiState.isLoading && uiState.totalCount > 0) {
                Text(
                    text = if (hasActiveFilters)
                        "${uiState.filteredCount} of ${uiState.totalCount}"
                    else
                        "${uiState.totalCount} transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            // ── List or empty state ───────────────────────────────────────────
            if (uiState.sections.isEmpty() && !uiState.isLoading) {
                EmptyState(filtersActive = hasActiveFilters)
            } else {
                TransactionsList(
                    sections = uiState.sections,
                    onTransactionClick = onTransactionClick,
                )
            }
        }
    }
}

// ── Search field ──────────────────────────────────────────────────────────────

@Composable
private fun LedgerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search counterparty…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = hintColor,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(BrandGold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = hintColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Filter chip row ───────────────────────────────────────────────────────────

private val DIRECTION_OPTIONS = listOf(null to "All", "debit" to "Debit", "credit" to "Credit")
private val CONFIDENCE_OPTIONS = listOf(0.0 to "Any", 0.5 to "≥50%", 0.8 to "≥80%")

@Composable
private fun FilterChipRow(
    direction: String?,
    selectedCategories: Set<String>,
    minConfidence: Double,
    onDirectionChange: (String?) -> Unit,
    onCategoryToggle: (String) -> Unit,
    onMinConfidenceChange: (Double) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Direction segment
        DIRECTION_OPTIONS.forEach { (value, label) ->
            val selected = direction == value
            LedgerFilterChip(
                label = label,
                selected = selected,
                onClick = { onDirectionChange(if (selected) null else value) },
            )
        }

        // Separator dot
        Box(
            modifier = Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(DividerDark),
        )

        // Confidence chips
        CONFIDENCE_OPTIONS.forEach { (value, label) ->
            val selected = minConfidence == value
            LedgerFilterChip(
                label = label,
                selected = selected,
                onClick = { onMinConfidenceChange(value) },
            )
        }

        // Separator dot
        Box(
            modifier = Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(DividerDark),
        )

        // Category chips — show only categories that have recognisable ids
        Category.entries.forEach { cat ->
            val selected = cat.id in selectedCategories
            LedgerFilterChip(
                label = "${cat.emoji} ${cat.label}",
                selected = selected,
                onClick = { onCategoryToggle(cat.id) },
            )
        }
    }
}

@Composable
private fun LedgerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandGold.copy(alpha = 0.18f),
            selectedLabelColor = BrandGold,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = BrandGold.copy(alpha = 0.45f),
            borderColor = DividerDark,
        ),
    )
}

// ── Transactions lazy list ────────────────────────────────────────────────────

@Composable
private fun TransactionsList(
    sections: List<DateSection>,
    onTransactionClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        sections.forEach { section ->
            item(key = "header_${section.label}") {
                SectionHeader(title = section.label)
            }
            items(
                items = section.items,
                key = { txn -> txn.id },
            ) { txn ->
                TransactionRow(
                    txn = txn,
                    onClick = { onTransactionClick(txn.id) },
                )
            }
        }
    }
}

// ── Transaction row ───────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(
    txn: TransactionEntity,
    onClick: () -> Unit,
) {
    val isExcluded = !txn.includedInAnalytics || !txn.isTransaction
    val rowAlpha = if (isExcluded) 0.55f else 1f
    val category = Category.fromId(txn.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(rowAlpha)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Category glyph
        CategoryGlyph(
            category = category,
            size = 40.dp,
        )

        // Middle content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Counterparty
            Text(
                text = txn.counterparty?.takeIf { it.isNotBlank() } ?: "Unknown",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Tag row: category label + excluded badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (category != null) {
                    TagBadge(
                        text = category.label,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!txn.isTransaction) {
                    TagBadge(
                        text = "not a txn",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        textColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else if (!txn.includedInAnalytics) {
                    TagBadge(
                        text = "excluded",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Right side: amount + confidence dot
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (txn.amount != null) {
                MoneyTextSmall(
                    amount = txn.amount,
                    direction = txn.direction,
                    short = true,
                )
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Confidence dot
            ConfidenceDot(confidence = txn.confidence)
        }
    }
}

// ── Small reusable atoms ──────────────────────────────────────────────────────

@Composable
private fun TagBadge(
    text: String,
    containerColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConfidenceDot(confidence: Double) {
    val color = when {
        confidence >= 0.8 -> ConfidenceGreen
        confidence >= 0.5 -> ConfidenceAmber
        else -> ConfidenceGrey
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(filtersActive: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (filtersActive) "No matches" else "No transactions yet",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FrauncesFamily,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (filtersActive)
                    "Try adjusting your filters"
                else
                    "SMS transactions will appear here once processed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
