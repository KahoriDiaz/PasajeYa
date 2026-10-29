package com.pasajeya.app.feature.history

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.history.domain.HistoryCursor
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryPage
import com.pasajeya.app.feature.history.ui.HistoryViewModel
import com.pasajeya.app.feature.trip.FakeAuthRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test(timeout = 10_000)
    fun `loads the first page and the aggregate spend`() = runTest {
        val repository = FakeHistoryRepository().apply {
            pageHandler = { _, _, _ ->
                HistoryPage(
                    trips = listOf(
                        sampleHistoryTrip("trip-1", totalCents = 200),
                        sampleHistoryTrip("trip-2", totalCents = 300)
                    ),
                    nextCursor = null,
                    isFromCache = false
                )
            }
            spentHandler = { _, _ -> 500L }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertTrue(awaitItem().isLoading)
            runCurrent()
            val loaded = awaitItem()

            assertEquals(2, loaded.trips.size)
            assertEquals(500L, loaded.spentCents)
            assertFalse(loaded.isLoading)
            assertFalse(loaded.hasError)
            assertFalse(loaded.isOffline)
            assertEquals(listOf(HistoryFilter.WEEK), repository.spentRequests)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `changes the filter and reloads page and spend`() = runTest {
        val repository = FakeHistoryRepository().apply {
            pageHandler = { _, filter, _ ->
                val trips = if (filter == HistoryFilter.WEEK) {
                    listOf(
                        sampleHistoryTrip("week-1"),
                        sampleHistoryTrip("week-2")
                    )
                } else {
                    listOf(sampleHistoryTrip("month-1"))
                }
                HistoryPage(trips, null, false)
            }
            spentHandler = { _, filter ->
                if (filter == HistoryFilter.WEEK) 200L else 900L
            }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            runCurrent()
            val week = awaitItem()
            assertEquals(HistoryFilter.WEEK, week.filter)
            assertEquals(2, week.trips.size)

            viewModel.selectFilter(HistoryFilter.MONTH)
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertEquals(HistoryFilter.MONTH, loading.filter)
            assertTrue(loading.trips.isEmpty())

            runCurrent()
            val month = awaitItem()
            assertEquals(HistoryFilter.MONTH, month.filter)
            assertEquals(1, month.trips.size)
            assertEquals(900L, month.spentCents)
            assertEquals(
                listOf(HistoryFilter.WEEK, HistoryFilter.MONTH),
                repository.spentRequests
            )
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `loads the next page with the cursor without duplicates`() = runTest {
        val firstPage = (1..20).map { index ->
            sampleHistoryTrip("trip-$index", totalCents = index * 100)
        }
        val secondPage = (21..25).map { index ->
            sampleHistoryTrip("trip-$index", totalCents = index * 100)
        }
        val cursor = HistoryCursor(
            startedAtMillis = 20_000L,
            tripId = "trip-20"
        )
        val repository = FakeHistoryRepository().apply {
            pageHandler = { _, _, requestedCursor ->
                if (requestedCursor == null) {
                    HistoryPage(firstPage, cursor, false)
                } else {
                    HistoryPage(secondPage, null, false)
                }
            }
            spentHandler = { _, _ -> 32_500L }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            runCurrent()
            assertEquals(20, awaitItem().trips.size)

            viewModel.loadMore()
            runCurrent()
            var loaded = awaitItem()
            while (loaded.isLoadingMore) {
                loaded = awaitItem()
            }

            assertEquals(25, loaded.trips.size)
            assertEquals(25, loaded.trips.distinctBy { it.trip.id }.size)
            assertNull(loaded.nextCursor)
            assertEquals(cursor, repository.pageRequests.last().cursor)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `shows an error when the first page fails`() = runTest {
        val repository = FakeHistoryRepository().apply {
            pageHandler = { _, _, _ -> throw IOException("sin red") }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            runCurrent()
            val state = awaitItem()

            assertTrue(state.hasError)
            assertFalse(state.isLoading)
            assertTrue(state.trips.isEmpty())
            assertNull(state.spentCents)
            assertTrue(repository.spentRequests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `marks cached data as offline and skips the server aggregate`() =
        runTest {
            val repository = FakeHistoryRepository().apply {
                pageHandler = { _, _, _ ->
                    HistoryPage(
                        trips = listOf(sampleHistoryTrip("cached-1")),
                        nextCursor = null,
                        isFromCache = true
                    )
                }
                spentHandler = { _, _ -> 999L }
            }
            val viewModel = viewModel(repository)

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                val state = awaitItem()

                assertTrue(state.isOffline)
                assertNull(state.spentCents)
                assertEquals(1, state.trips.size)
                assertTrue(repository.spentRequests.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.viewModelScope.cancel()
        }

    @Test(timeout = 10_000)
    fun `shown aggregate equals the sum of closed loaded trips`() = runTest {
        val allTrips = (1..24).map { index ->
            sampleHistoryTrip(
                id = "trip-$index",
                status = if (index % 3 == 0) "requires_review" else "closed",
                totalCents = index * 100
            )
        }
        val closedTotal = allTrips
            .filter { it.trip.status == "closed" }
            .sumOf { it.trip.fareBreakdown?.totalCents ?: 0 }
            .toLong()
        val cursor = HistoryCursor(20_000L, "trip-20")
        val repository = FakeHistoryRepository().apply {
            pageHandler = { _, _, requestedCursor ->
                if (requestedCursor == null) {
                    HistoryPage(allTrips.take(20), cursor, false)
                } else {
                    HistoryPage(allTrips.drop(20), null, false)
                }
            }
            spentHandler = { _, _ -> closedTotal }
        }
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            runCurrent()
            awaitItem()

            viewModel.selectFilter(HistoryFilter.ALL)
            awaitItem()
            runCurrent()
            awaitItem()

            while (viewModel.uiState.value.nextCursor != null) {
                viewModel.loadMore()
                runCurrent()
                var loaded = awaitItem()
                while (loaded.isLoadingMore) {
                    loaded = awaitItem()
                }
            }

            val state = viewModel.uiState.value
            val loadedClosedTotal = state.trips
                .filter { it.trip.status == "closed" }
                .sumOf { it.trip.fareBreakdown?.totalCents ?: 0 }
                .toLong()

            assertEquals(24, state.trips.size)
            assertEquals(loadedClosedTotal, state.spentCents)
            assertTrue(
                repository.pageRequests.any {
                    it.filter == HistoryFilter.ALL && it.cursor == cursor
                }
            )
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.viewModelScope.cancel()
    }

    private fun viewModel(
        repository: FakeHistoryRepository
    ): HistoryViewModel = HistoryViewModel(
        authRepository = FakeAuthRepository(
            AuthUser(
                uid = "passenger-1",
                email = "pasajero@example.com",
                emailVerified = true
            )
        ),
        historyRepository = repository
    )
}
