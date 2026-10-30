package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.fare.domain.FareRepository
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.FareVersionInput
import com.pasajeya.app.feature.trip.domain.GpsFix
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.location.LocationProvider
import com.pasajeya.app.feature.trip.ui.passenger.LiveTripViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeFareRepository : FareRepository {
    val benefit = MutableStateFlow(false)

    val context = RouteFareContext(
        route = RouteGeo(
            id = "route-1",
            name = "Ruta 1",
            stops = listOf(
                RouteStop("A", 0.0, -71.5),
                RouteStop("B", 0.05, -71.5)
            ),
            officialFareCents = 330,
            officialUniversityFareCents = 165
        ),
        version = FareVersionInput(
            baseFarePerKmCents = 120,
            fuelIndex = 1.0,
            minFareCents = 50,
            roundingStepCents = 5
        )
    )

    override suspend fun loadRouteFareContext(routeId: String) = context

    override fun observeUniversityBenefit(uid: String): Flow<Boolean> = benefit
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTripViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val trips = FakeTripRepository()
    private val fares = FakeFareRepository()
    private val fixes = MutableSharedFlow<GpsFix>(extraBufferCapacity = 16)
    private val location = mockk<LocationProvider> {
        every { hasPermission() } returns true
        every { locationUpdates(any()) } returns fixes
    }

    private val trip = Trip(
        id = "trip-1",
        passengerUid = "passenger-1",
        operatorUid = "operator-1",
        shiftId = "shift-1",
        routeId = "route-1",
        status = "open",
        reviewReason = null,
        startedAtMillis = 1L,
        endedAtMillis = null,
        distanceMeters = 0.0,
        passengerFirstName = "Ana",
        passengerAge = 20
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LiveTripViewModel(
        authRepository = FakeAuthRepository(
            AuthUser(uid = "passenger-1", email = "p@example.com", emailVerified = true)
        ),
        tripRepository = trips,
        fareRepository = fares,
        locationProvider = location
    )

    private fun fix(lat: Double, seconds: Long) =
        GpsFix(lat = lat, lng = -71.5, accuracyMeters = 5.0, timeMillis = seconds * 1000)

    @Test(timeout = 10_000)
    fun `sin viaje activo no hay seguimiento`() = runTest {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.uiState.value.hasTrip)
        assertEquals(0, vm.uiState.value.fareCents)
    }

    @Test(timeout = 10_000)
    fun `emite distancia y costo en vivo con cada lectura del gps`() = runTest {
        trips.activeTrip.value = trip
        val vm = viewModel()
        runCurrent()

        assertTrue(vm.uiState.value.hasTrip)
        assertNotNull(vm.uiState.value.route)
        // Sin movimiento aun: tarifa minima.
        assertEquals(0.0, vm.uiState.value.distanceMeters, 0.0)
        assertEquals(50, vm.uiState.value.fareCents)

        fixes.tryEmit(fix(0.000, 0))
        fixes.tryEmit(fix(0.005, 60)) // ~556 m
        fixes.tryEmit(fix(0.010, 120)) // ~556 m
        runCurrent()

        val state = vm.uiState.value
        assertEquals(1_112.0, state.distanceMeters, 2.0)
        // 1.112 km * 120 = 133.4 -> 135
        assertEquals(135, state.fareCents)
        assertNotNull(state.position)
    }

    @Test(timeout = 10_000)
    fun `aplica el descuento universitario en vivo cuando el beneficio esta aprobado`() = runTest {
        trips.activeTrip.value = trip
        val vm = viewModel()
        runCurrent()
        fixes.tryEmit(fix(0.000, 0))
        fixes.tryEmit(fix(0.010, 120))
        runCurrent()
        assertEquals(135, vm.uiState.value.fareCents)
        assertFalse(vm.uiState.value.hasUniversityBenefit)

        fares.benefit.value = true
        runCurrent()

        assertTrue(vm.uiState.value.hasUniversityBenefit)
        assertEquals(135, vm.uiState.value.regularCents)
        assertEquals(70, vm.uiState.value.fareCents)
    }

    @Test(timeout = 10_000)
    fun `al cerrarse el viaje se reinicia el estado`() = runTest {
        trips.activeTrip.value = trip
        val vm = viewModel()
        runCurrent()
        fixes.tryEmit(fix(0.000, 0))
        fixes.tryEmit(fix(0.010, 120))
        runCurrent()
        assertTrue(vm.uiState.value.distanceMeters > 0)

        trips.activeTrip.value = null
        runCurrent()

        assertFalse(vm.uiState.value.hasTrip)
        assertEquals(0.0, vm.uiState.value.distanceMeters, 0.0)
    }
}
