package com.pasajeya.app.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.util.formatCents
import com.pasajeya.app.feature.history.domain.HistoryDateFormatter
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryTrip
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("history_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.action_back),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        HistoryContent(
            state = state,
            onFilterSelected = viewModel::selectFilter,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            onTripClick = onTripClick,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onFilterSelected: (HistoryFilter) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onTripClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState, state.nextCursor, state.isLoading, state.isLoadingMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount > 0 &&
                lastVisible >= layoutInfo.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) currentOnLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("history_list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "spent") {
            SpentSummaryCard(
                filter = state.filter,
                spentCents = state.spentCents
            )
        }
        item(key = "filters") {
            HistoryFilters(
                selected = state.filter,
                onSelected = onFilterSelected
            )
        }

        if (state.isOffline) {
            item(key = "offline") {
                InlineMessage(
                    text = stringResource(R.string.history_offline),
                    tone = MessageTone.WARNING
                )
            }
        }

        when {
            state.isLoading -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
            }
            state.hasError -> {
                item(key = "error") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InlineMessage(
                            text = stringResource(R.string.history_load_error),
                            tone = MessageTone.ERROR
                        )
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
            state.isEmpty -> {
                item(key = "empty") {
                    EmptyHistoryCard(isOffline = state.isOffline)
                }
            }
            else -> {
                items(
                    items = state.trips,
                    key = { item -> item.trip.id }
                ) { item ->
                    HistoryTripCard(
                        item = item,
                        onClick = { onTripClick(item.trip.id) }
                    )
                }

                if (state.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                if (state.loadMoreError) {
                    item(key = "load_more_error") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InlineMessage(
                                text = stringResource(R.string.history_load_more_error),
                                tone = MessageTone.ERROR
                            )
                            TextButton(
                                onClick = onRetryLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpentSummaryCard(
    filter: HistoryFilter,
    spentCents: Long?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.history_spent_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextGray
            )
            Text(
                text = spentCents?.let(::formatCents) ?: "—",
                modifier = Modifier.testTag("history_spent_amount"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
            if (spentCents == null) {
                Text(
                    text = stringResource(R.string.history_spent_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Text(
                text = filterLabel(filter),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
    }
}

@Composable
private fun HistoryFilters(
    selected: HistoryFilter,
    onSelected: (HistoryFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filterLabel(filter)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(filterTestTag(filter))
            )
        }
    }
}

@Composable
private fun HistoryTripCard(
    item: HistoryTrip,
    onClick: () -> Unit
) {
    val trip = item.trip
    val breakdown = trip.fareBreakdown
    val isReview = trip.status == "requires_review"
    val benefit = breakdown?.benefit

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_trip_${trip.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isReview) ReviewAmber.copy(alpha = 0.55f) else BorderLight
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.startedAtMillis?.let(HistoryDateFormatter::format)
                        ?: stringResource(R.string.history_date_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                if (isReview) {
                    StatusPill(
                        text = stringResource(R.string.history_status_review),
                        color = ReviewAmber
                    )
                }
            }

            Text(
                text = item.routeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = formatDistance(trip.distanceMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                    if (benefit != null && !isReview) {
                        StatusPill(
                            text = stringResource(
                                R.string.history_benefit,
                                benefit.percent
                            ),
                            color = SuccessGreen
                        )
                    }
                }
                if (isReview || breakdown == null) {
                    Text(
                        text = stringResource(R.string.history_amount_pending),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ReviewAmber
                    )
                } else {
                    Text(
                        text = formatCents(breakdown.totalCents.toLong()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

@Composable
private fun EmptyHistoryCard(isOffline: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_empty"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Text(
            text = stringResource(
                if (isOffline) {
                    R.string.history_empty_offline
                } else {
                    R.string.history_empty
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun filterLabel(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.WEEK -> stringResource(R.string.history_filter_week)
    HistoryFilter.MONTH -> stringResource(R.string.history_filter_month)
    HistoryFilter.ALL -> stringResource(R.string.history_filter_all)
}

private fun filterTestTag(filter: HistoryFilter): String = when (filter) {
    HistoryFilter.WEEK -> "history_filter_week"
    HistoryFilter.MONTH -> "history_filter_month"
    HistoryFilter.ALL -> "history_filter_all"
}

private fun formatDistance(meters: Double): String {
    val locale = Locale.forLanguageTag("es-PE")
    return if (meters >= 1_000) {
        String.format(locale, "%.1f km", meters / 1_000)
    } else {
        String.format(locale, "%.0f m", meters)
    }
}
