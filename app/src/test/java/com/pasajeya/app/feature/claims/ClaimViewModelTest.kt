package com.pasajeya.app.feature.claims

import app.cash.turbine.test
import com.pasajeya.app.feature.claims.domain.Claim
import com.pasajeya.app.feature.claims.domain.ClaimRepository
import com.pasajeya.app.feature.claims.ui.ClaimUiState
import com.pasajeya.app.feature.claims.ui.ClaimViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeClaimRepository : ClaimRepository {
    var submitResult: Result<Unit> = Result.success(Unit)
    var lastSubmittedClaim: Claim? = null

    override suspend fun submitClaim(claim: Claim): Result<Unit> {
        lastSubmittedClaim = claim
        return submitResult
    }

    override fun observeClaimByTripId(tripId: String): Flow<Claim?> = flowOf(null)
    override fun observePassengerClaims(passengerId: String): Flow<List<Claim>> = flowOf(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeClaimRepository
    private lateinit var viewModel: ClaimViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeClaimRepository()
        viewModel = ClaimViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitClaim con motivo vacio produce Error inmediato`() = runTest {
        viewModel.uiState.test {
            assertEquals(ClaimUiState.Idle, awaitItem())

            viewModel.submitClaim(
                tripId = "trip1",
                passengerId = "p1",
                operatorId = "op1",
                reason = "   ",
                proposedDistanceMeters = null
            )

            val errorState = awaitItem()
            assertTrue(errorState is ClaimUiState.Error)
            assertEquals("El motivo no puede estar vacío", (errorState as ClaimUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitClaim exitoso transiciona a Submitting y luego a Success`() = runTest {
        viewModel.uiState.test {
            assertEquals(ClaimUiState.Idle, awaitItem())

            viewModel.submitClaim(
                tripId = "trip1",
                passengerId = "p1",
                operatorId = "op1",
                reason = "El bus no me dio tiempo para el tap out",
                proposedDistanceMeters = 3500.0
            )

            assertEquals(ClaimUiState.Submitting, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()

            val successState = awaitItem()
            assertTrue(successState is ClaimUiState.Success)
            assertEquals("El bus no me dio tiempo para el tap out", fakeRepo.lastSubmittedClaim?.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }
}