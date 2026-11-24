package com.pasajeya.app.feature.claims

import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.claims.domain.Claim
import com.pasajeya.app.feature.claims.domain.ClaimCategory
import com.pasajeya.app.feature.claims.domain.ClaimRepository
import com.pasajeya.app.feature.claims.ui.ClaimViewModel
import com.pasajeya.app.feature.trip.FakeAuthRepository
import com.pasajeya.app.feature.trip.FakeTripRepository
import com.pasajeya.app.feature.trip.domain.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeClaimRepository : ClaimRepository {
    var submitResult: Result<Unit> = Result.success(Unit)
    var lastSubmittedClaim: Claim? = null
    var existingClaim: Claim? = null

    override suspend fun submitClaim(claim: Claim): Result<Unit> {
        lastSubmittedClaim = claim
        return submitResult
    }

    override fun observeClaimByTripId(tripId: String): Flow<Claim?> = flowOf(existingClaim)

    override fun observePassengerClaims(passengerId: String): Flow<List<Claim>> =
        flowOf(emptyList())

    override fun observeCompanyClaims(companyId: String): Flow<List<Claim>> = flowOf(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val claims = FakeClaimRepository()
    private val trips = FakeTripRepository()
    private val auth = FakeAuthRepository(AuthUser("p1", "ana@correo.pe", true))

    private val trip = Trip(
        id = "trip1",
        passengerUid = "p1",
        operatorUid = "driver1",
        shiftId = "shift1",
        routeId = "R-CAY-01-IDA",
        status = "closed",
        reviewReason = null,
        startedAtMillis = 0L,
        endedAtMillis = 1_000L,
        distanceMeters = 3_800.0,
        passengerFirstName = "Ana",
        passengerAge = 20
    )

    private fun viewModel() = ClaimViewModel(claims, trips, auth)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trips.trips.value = mapOf("trip1" to trip)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin motivo elegido no se envia nada`() = runTest {
        val vm = viewModel()
        vm.start("trip1")
        advanceUntilIdle()

        vm.submit("Elige un motivo", "Error")
        advanceUntilIdle()

        assertNull(claims.lastSubmittedClaim)
        assertEquals("Elige un motivo", vm.uiState.value.error)
        assertFalse(vm.uiState.value.sent)
    }

    @Test
    fun `el reclamo viaja con el conductor y la ruta del viaje`() = runTest {
        val vm = viewModel()
        vm.start("trip1")
        advanceUntilIdle()

        vm.onCategoryChange(ClaimCategory.OVERCHARGE)
        vm.onDetailsChange("Me cobraron el pasaje completo")
        vm.submit("Elige un motivo", "Error")
        advanceUntilIdle()

        val sent = claims.lastSubmittedClaim
        assertEquals("trip1", sent?.tripId)
        assertEquals("p1", sent?.passengerId)
        assertEquals("driver1", sent?.operatorId)
        assertEquals("R-CAY-01-IDA", sent?.routeId)
        assertEquals(ClaimCategory.OVERCHARGE.wire, sent?.category)
        assertEquals("Me cobraron el pasaje completo", sent?.reason)
        assertTrue(vm.uiState.value.sent)
    }

    @Test
    fun `un viaje ya reclamado no deja mandar otro`() = runTest {
        claims.existingClaim = Claim(
            tripId = "trip1",
            passengerId = "p1",
            reason = "ya enviado"
        )
        val vm = viewModel()
        vm.start("trip1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.alreadySent)
    }

    @Test
    fun `si falla el envio se avisa y no queda como enviado`() = runTest {
        claims.submitResult = Result.failure(IllegalStateException("sin conexion"))
        val vm = viewModel()
        vm.start("trip1")
        advanceUntilIdle()

        vm.onCategoryChange(ClaimCategory.SAFETY)
        vm.submit("Elige un motivo", "Error")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.sent)
        assertEquals("sin conexion", vm.uiState.value.error)
    }
}
