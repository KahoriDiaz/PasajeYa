package com.pasajeya.app.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.pasajeya.app.feature.history.domain.OperatorShiftSummary

@Composable
fun OperatorShiftSummaryCard(
    state: OperatorShiftSummaryUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("operator_shift_summary"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.history_operator_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            when {
                state.isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.history_operator_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }

                state.hasError -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InlineMessage(
                        text = stringResource(R.string.history_load_error),
                        tone = MessageTone.ERROR
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }

                state.summary == null -> Text(
                    text = stringResource(R.string.history_operator_no_shift),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )

                else -> {
                    val summary = state.summary
                    Text(
                        text = stringResource(
                            if (summary.shift.isActive) {
                                R.string.history_operator_current
                            } else {
                                R.string.history_operator_last
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetric(
                            label = stringResource(R.string.history_operator_closed),
                            value = summary.closedTrips.toString(),
                            color = SuccessGreen,
                            testTag = "operator_summary_closed",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetric(
                            label = stringResource(R.string.history_operator_review),
                            value = summary.reviewTrips.toString(),
                            color = ReviewAmber,
                            testTag = "operator_summary_review",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetric(
                            label = stringResource(R.string.history_operator_total),
                            value = formatCents(summary.totalCents),
                            color = PrimaryBlue,
                            testTag = "operator_summary_total",
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                    if (summary.closedTrips == 0 && summary.reviewTrips == 0) {
                        Text(
                            text = stringResource(R.string.history_operator_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextGray
        )
        Text(
            text = value,
            modifier = Modifier.testTag(testTag),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
