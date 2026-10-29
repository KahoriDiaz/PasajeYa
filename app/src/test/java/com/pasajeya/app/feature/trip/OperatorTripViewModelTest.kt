package com.pasajeya.app.feature.trip

import android.content.Context
import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.auth.domain.model.UserRole
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.location.LocationProvider
import com.pasajeya.app.feature.voice.domain.VoiceAnnouncementFormatter
import com.pasajeya.app.feature.trip.ui.operator.OperatorNoticeKind
import com.pasajeya.app.feature.trip.ui.operator.OperatorTripViewModel
import com.pasajeya.app.feature.trip.ui.operator.VoiceWarning
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperatorTripViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var locationProvider: LocationProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        locationProvider = mockk(relaxed = true)
        every { locationProvider.hasPermission() } returns false
        coEvery { locationProvider.currentPosition() } returns TripPosition(
            latitude = -16.3989,
            longitude = -71.5350,
            accuracy = 5.0
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test(timeout = 10_000)
    fun `starts a shift and lists assigned routes`() = runTest {
        val repository = FakeTripRepository()
        val viewModel = viewModel(repository)

        runCurrent()
        assertEquals("ABC123", viewModel.uiState.value.context?.plate)
        assertEquals("route-1", viewModel.uiState.value.selectedRouteId)

        viewModel.startShift()
        runCurrent()

        assertEquals(1, repository.startShiftCalls)
        assertEquals("shift-1", viewModel.uiState.value.shift?.id)
        assertEquals(
            OperatorNoticeKind.SHIFT_STARTED,
            viewModel.uiState.value.notice?.kind
        )
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `ignores a repeated qr frame during the anti rebound window`() =
        runTest {
            val repository = FakeTripRepository()
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.startShift()
            runCurrent()

            viewModel.openScanner()
            viewModel.onQrScanned("qr-token")
            runCurrent()
            assertEquals(1, repository.tapInCalls)
            assertEquals(
                OperatorNoticeKind.TAP_IN,
                viewModel.uiState.value.notice?.kind
            )
            assertFalse(viewModel.uiState.value.scannerOpen)

            viewModel.openScanner()
            viewModel.onQrScanned("qr-token")
            runCurrent()
            assertEquals(1, repository.tapInCalls)

            viewModel.onQrScanned("qr-token-2")
            runCurrent()
            assertEquals(2, repository.tapInCalls)
            viewModel.viewModelScope.cancel()
        }

    @Test(timeout = 10_000)
    fun `reports review trips when the shift ends`() = runTest {
        val repository = FakeTripRepository().apply {
            endShiftResult = endShiftResult.copy(reviewCount = 2)
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.requestEndShift()
        viewModel.confirmEndShift()
        runCurrent()

        assertNull(viewModel.uiState.value.shift)
        assertEquals(
            OperatorNoticeKind.SHIFT_ENDED,
            viewModel.uiState.value.notice?.kind
        )
        assertEquals(2, viewModel.uiState.value.notice?.reviewCount)
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `taps out a passenger from the boarding list`() = runTest {
        val repository = FakeTripRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        val trip = sampleTrip()
        viewModel.requestTapOut(trip)
        assertEquals(trip, viewModel.uiState.value.tapOutConfirmation)

        viewModel.confirmTapOut()
        runCurrent()

        assertNull(viewModel.uiState.value.tapOutConfirmation)
        assertEquals(1, repository.tapOutCalls)
        assertEquals(
            OperatorNoticeKind.TAP_OUT,
            viewModel.uiState.value.notice?.kind
        )
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `shows the server error when a tap in fails`() = runTest {
        val repository = FakeTripRepository().apply {
            tapInFailure = offlineError()
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.onQrScanned("qr-token")
        runCurrent()

        assertEquals(
            OperatorNoticeKind.ERROR,
            viewModel.uiState.value.notice?.kind
        )
        assertEquals(
            "Sin conexión.",
            viewModel.uiState.value.notice?.serverMessage
        )
        assertTrue(viewModel.uiState.value.scannerOpen.not())
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `announces the passenger once per tap in`() = runTest {
        val announcer = FakeSpeechAnnouncer()
        val viewModel = viewModel(FakeTripRepository(), announcer)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.onQrScanned("qr-token")
        runCurrent()
        viewModel.onQrScanned("qr-token")
        runCurrent()

        assertEquals(listOf("Ana, 22 años"), announcer.spoken)
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `queues consecutive tap in announcements in order`() = runTest {
        val announcer = FakeSpeechAnnouncer()
        val repository = FakeTripRepository().apply {
            tapInResults = listOf(
                TapInResult("trip-1", "Ana", 22),
                TapInResult("trip-2", "Luis", 1)
            )
        }
        val viewModel = viewModel(repository, announcer)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.onQrScanned("qr-token-1")
        runCurrent()
        viewModel.onQrScanned("qr-token-2")
        runCurrent()

        assertEquals(
            listOf("Ana, 22 años", "Luis, 1 año"),
            announcer.spoken
        )
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `does not announce when voice is disabled`() = runTest {
        val announcer = FakeSpeechAnnouncer()
        val viewModel = viewModel(
            repository = FakeTripRepository(),
            speechAnnouncer = announcer,
            voicePreferences = FakeVoicePreferences(initialEnabled = false)
        )
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.onQrScanned("qr-token")
        runCurrent()

        assertTrue(announcer.spoken.isEmpty())
        assertNotNull(viewModel.uiState.value.lastTapIn)
        assertNull(viewModel.uiState.value.voiceWarning)
        viewModel.viewModelScope.cancel()
    }

    @Test(timeout = 10_000)
    fun `shows a warning when speech is unavailable`() = runTest {
        val announcer = FakeSpeechAnnouncer(isAvailable = false)
        val viewModel = viewModel(FakeTripRepository(), announcer)
        runCurrent()
        viewModel.startShift()
        runCurrent()

        viewModel.onQrScanned("qr-token")
        runCurrent()

        assertTrue(announcer.spoken.isEmpty())
        assertEquals(
            VoiceWarning.UNAVAILABLE,
            viewModel.uiState.value.voiceWarning
        )
        viewModel.viewModelScope.cancel()
    }

    private fun viewModel(
        repository: FakeTripRepository,
        speechAnnouncer: FakeSpeechAnnouncer = FakeSpeechAnnouncer(),
        voicePreferences: FakeVoicePreferences = FakeVoicePreferences()
    ): OperatorTripViewModel =
        OperatorTripViewModel(
            appContext = mockk<Context>(relaxed = true),
            authRepository = FakeAuthRepository(
                AuthUser(
                    uid = "operator-1",
                    email = "operador@example.com",
                    emailVerified = true
                )
            ),
            accountRepository = FakeAccountRepository(
                uid = "operator-1",
                role = UserRole.OPERATOR
            ),
            tripRepository = repository,
            locationProvider = locationProvider,
            speechAnnouncer = speechAnnouncer,
            voicePreferences = voicePreferences,
            voiceAnnouncementFormatter = VoiceAnnouncementFormatter()
        )
}
