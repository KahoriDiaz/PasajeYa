package com.pasajeya.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pasajeya.app.core.theme.PasajeYaTheme
import com.pasajeya.app.core.util.formatCents
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryTrip
import com.pasajeya.app.feature.history.ui.HistoryContent
import com.pasajeya.app.feature.history.ui.HistoryUiState
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import com.pasajeya.app.feature.trip.domain.model.Trip
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEmptyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PasajeYaTheme {
                HistoryContent(
                    state = HistoryUiState(
                        isLoading = false,
                        spentCents = 0L
                    ),
                    onFilterSelected = {},
                    onRetry = {},
                    onLoadMore = {},
                    onRetryLoadMore = {},
                    onTripClick = {}
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.history_empty))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("history_empty")
            .assertIsDisplayed()
    }

    @Test
    fun showsReviewTripWithPendingAmount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PasajeYaTheme {
                HistoryContent(
                    state = HistoryUiState(
                        isLoading = false,
                        spentCents = 0L,
                        trips = listOf(reviewTrip())
                    ),
                    onFilterSelected = {},
                    onRetry = {},
                    onLoadMore = {},
                    onRetryLoadMore = {},
                    onTripClick = {}
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.history_status_review))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.history_amount_pending))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Lun 20 oct · 08:15")
            .assertIsDisplayed()
    }

    @Test
    fun changingFilterUpdatesTheSpentAmount() {
        var state by mutableStateOf(
            HistoryUiState(
                filter = HistoryFilter.WEEK,
                isLoading = false,
                spentCents = 1_200L
            )
        )
        composeRule.setContent {
            PasajeYaTheme {
                HistoryContent(
                    state = state,
                    onFilterSelected = { filter ->
                        state = state.copy(
                            filter = filter,
                            spentCents = if (filter == HistoryFilter.MONTH) {
                                4_500L
                            } else {
                                1_200L
                            }
                        )
                    },
                    onRetry = {},
                    onLoadMore = {},
                    onRetryLoadMore = {},
                    onTripClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("history_filter_month").performClick()

        composeRule
            .onNodeWithText(formatCents(4_500L))
            .assertIsDisplayed()
    }

    private fun reviewTrip(): HistoryTrip = HistoryTrip(
        trip = Trip(
            id = "review-trip",
            passengerUid = "passenger-1",
            operatorUid = "operator-1",
            shiftId = "shift-1",
            routeId = "route-1",
            status = "requires_review",
            reviewReason = "coverage_low",
            startedAtMillis = Instant
                .parse("2025-10-20T13:15:00Z")
                .toEpochMilli(),
            endedAtMillis = null,
            distanceMeters = 2_400.0,
            passengerFirstName = "Ana",
            passengerAge = 22,
            fareBreakdown = FareBreakdown(
                distanceMeters = 2_400.0,
                baseFarePerKmCents = 12,
                fuelIndex = 1.0,
                calculatedCents = 180,
                officialFareCents = 130,
                regularCents = 130,
                benefit = null,
                totalCents = 130,
                capped = true,
                minApplied = false,
                fareVersionId = "v1"
            )
        ),
        routeName = "Ruta Arequipa"
    )
}
