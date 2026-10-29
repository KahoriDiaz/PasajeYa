package com.pasajeya.app.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.history.domain.HistoryCursor
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.history.domain.HistoryTrip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.WEEK,
    val trips: List<HistoryTrip> = emptyList(),
    val spentCents: Long? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasError: Boolean = false,
    val loadMoreError: Boolean = false,
    val isOffline: Boolean = false,
    val nextCursor: HistoryCursor? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && !hasError && trips.isEmpty()

    val canLoadMore: Boolean
        get() = nextCursor != null && !isLoading && !isLoadingMore && !loadMoreError
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null

    init {
        loadFirstPage()
    }

    fun selectFilter(filter: HistoryFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update {
            it.copy(
                filter = filter,
                trips = emptyList(),
                spentCents = null,
                isLoading = true,
                isLoadingMore = false,
                hasError = false,
                loadMoreError = false,
                isOffline = false,
                nextCursor = null
            )
        }
        loadFirstPage()
    }

    fun retry() {
        _uiState.update {
            it.copy(
                isLoading = true,
                hasError = false,
                loadMoreError = false
            )
        }
        loadFirstPage()
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isLoadingMore || state.hasError) return
        if (state.loadMoreError) return
        val uid = authRepository.currentUser()?.uid ?: return
        val filter = state.filter

        nextPageJob?.cancel()
        nextPageJob = viewModelScope.launch {
            _uiState.update { current ->
                if (current.filter == filter) {
                    current.copy(isLoadingMore = true, loadMoreError = false)
                } else {
                    current
                }
            }
            try {
                val page = historyRepository.loadPage(uid, filter, cursor)
                _uiState.update { current ->
                    if (current.filter != filter) {
                        current
                    } else {
                        current.copy(
                            trips = (current.trips + page.trips)
                                .distinctBy { it.trip.id },
                            nextCursor = page.nextCursor,
                            isLoadingMore = false,
                            loadMoreError = false,
                            isOffline = current.isOffline || page.isFromCache
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (current.filter != filter) {
                        current
                    } else {
                        current.copy(
                            isLoadingMore = false,
                            loadMoreError = true
                        )
                    }
                }
            }
        }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreError = false) }
        loadMore()
    }

    private fun loadFirstPage() {
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        val filter = _uiState.value.filter
        val uid = authRepository.currentUser()?.uid

        if (uid == null) {
            _uiState.update {
                it.copy(isLoading = false, hasError = true)
            }
            return
        }

        firstPageJob = viewModelScope.launch {
            try {
                val page = historyRepository.loadPage(uid, filter, null)
                var offline = page.isFromCache
                val spentCents = if (offline) {
                    null
                } else {
                    try {
                        historyRepository.loadSpentCents(uid, filter)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (error.isOfflineError()) {
                            offline = true
                            null
                        } else {
                            throw error
                        }
                    }
                }

                _uiState.update { current ->
                    if (current.filter != filter) {
                        current
                    } else {
                        current.copy(
                            trips = page.trips,
                            spentCents = spentCents,
                            isLoading = false,
                            isLoadingMore = false,
                            hasError = false,
                            loadMoreError = false,
                            isOffline = offline,
                            nextCursor = page.nextCursor
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (current.filter != filter) {
                        current
                    } else {
                        current.copy(
                            trips = emptyList(),
                            spentCents = null,
                            isLoading = false,
                            isLoadingMore = false,
                            hasError = true,
                            loadMoreError = false,
                            nextCursor = null
                        )
                    }
                }
            }
        }
    }

    private fun Throwable.isOfflineError(): Boolean =
        this is IOException ||
            (this is FirebaseFirestoreException &&
                code == FirebaseFirestoreException.Code.UNAVAILABLE)
}
