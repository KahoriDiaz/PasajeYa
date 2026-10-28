package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.auth.domain.model.UserRole
import com.pasajeya.app.feature.trip.ui.passenger.PassengerQrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class PassengerQrViewModelTest {

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
    fun `issues a token and renews it after 45 seconds`() = runTest {
        val repository = FakeTripRepository()
        val viewModel = viewModel(repository)

        runCurrent()
        assertEquals("token-1", viewModel.uiState.value.token)
        assertFalse(viewModel.uiState.value.isOffline)

        advanceTimeBy(45_000)
        runCurrent()

        assertEquals("token-2", viewModel.uiState.value.token)
        assertEquals(2, repository.tokenCalls)
        assertEquals(45, viewModel.uiState.value.secondsRemaining)
        viewModel.stopForTest()
    }

    @Test(timeout = 10_000)
    fun `shows offline state and recovers when the server returns`() =
        runTest {
            val repository = FakeTripRepository().apply {
                issueFailure = offlineError()
            }
            val viewModel = viewModel(repository)

            runCurrent()
            assertNull(viewModel.uiState.value.token)
            assertTrue(viewModel.uiState.value.isOffline)

            repository.issueFailure = null
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals("token-1", viewModel.uiState.value.token)
            assertFalse(viewModel.uiState.value.isOffline)
            viewModel.stopForTest()
        }

    @Test(timeout = 10_000)
    fun `observes the active trip and its route name`() = runTest {
        val repository = FakeTripRepository()
        val viewModel = viewModel(repository)

        runCurrent()
        repository.activeTrip.value = sampleTrip()
        runCurrent()

        assertEquals("trip-1", viewModel.uiState.value.trip?.id)
        assertEquals("Ruta 1", viewModel.uiState.value.routeName)
        viewModel.stopForTest()
    }

    private fun viewModel(repository: FakeTripRepository): PassengerQrViewModel =
        PassengerQrViewModel(
            authRepository = FakeAuthRepository(
                AuthUser(
                    uid = "passenger-1",
                    email = "pasajero@example.com",
                    emailVerified = true
                )
            ),
            accountRepository = FakeAccountRepository(
                uid = "passenger-1",
                role = UserRole.PASSENGER
            ),
            tripRepository = repository
        )
}
