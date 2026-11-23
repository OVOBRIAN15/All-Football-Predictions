package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CommunityAndTrackingRepository
import com.example.data.local.LocalPreferencesStore
import com.example.data.local.NotificationPreferences
import com.example.data.sources.FootballDataRepository
import com.example.domain.engine.AccumulatorAnalyzer
import com.example.domain.engine.CalibrationEngine
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.AccumulatorEvaluation
import com.example.domain.model.AccumulatorSelection
import com.example.domain.model.AppReview
import com.example.domain.model.CompetitionInfo
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.DiscussionComment
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchPredictionReport
import com.example.domain.model.ModelParameters
import com.example.domain.model.ModelVersionRecord
import com.example.domain.model.OwnerFeedback
import com.example.domain.model.SearchResultBundle
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.StandingRow
import com.example.domain.model.TeamAnalysisReport
import com.example.domain.model.TeamInfo
import com.example.domain.model.TrackedPrediction
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class DateRangeFilter(val label: String) {
    ALL_7_DAYS("Next 7 Days"),
    TODAY("Today"),
    TOMORROW("Tomorrow")
}

enum class MarketQuickFilter(val label: String) {
    ALL("All Markets"),
    HIGH_CONFIDENCE_1X2("Top 1X2 Confidence"),
    BTTS_LIKELY("BTTS >= 55%"),
    OVER_25_LIKELY("Over 2.5 >= 55%")
}

data class FootballFeedUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val upcomingSevenDays: List<FixtureItem> = emptyList(),
    val liveMatches: List<FixtureItem> = emptyList(),
    val recentFinished: List<FixtureItem> = emptyList(),
    val topPredictions: List<MatchPredictionReport> = emptyList(),
    val allPredictionsByMatchId: Map<String, MatchPredictionReport> = emptyMap(),
    val standingsByCompetition: Map<String, List<StandingRow>> = emptyMap(),
    val competitions: List<CompetitionInfo> = emptyList(),
    val sourceMetadata: List<DataSourceMetadata> = emptyList(),
    val lastUpdatedEpochMs: Long = 0L,
    val selectedCountry: String = "All",
    val selectedCompetition: String = "All",
    val selectedDateFilter: DateRangeFilter = DateRangeFilter.ALL_7_DAYS,
    val selectedGender: GenderCategory? = null,
    val selectedMarketFilter: MarketQuickFilter = MarketQuickFilter.ALL
)

class MainViewModel(
    private val footballRepo: FootballDataRepository,
    private val cloudRepo: CommunityAndTrackingRepository,
    private val prefsStore: LocalPreferencesStore,
    private val currentUserId: String
) : ViewModel() {

    private val _feedState = MutableStateFlow(FootballFeedUiState())
    val feedState: StateFlow<FootballFeedUiState> = _feedState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchBundle = MutableStateFlow<SearchResultBundle?>(null)
    val searchBundle: StateFlow<SearchResultBundle?> = _searchBundle.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedPrediction = MutableStateFlow<MatchPredictionReport?>(null)
    val selectedPrediction: StateFlow<MatchPredictionReport?> = _selectedPrediction.asStateFlow()

    private val _selectedTeamAnalysis = MutableStateFlow<TeamAnalysisReport?>(null)
    val selectedTeamAnalysis: StateFlow<TeamAnalysisReport?> = _selectedTeamAnalysis.asStateFlow()

    private val _isAnalyzingTeam = MutableStateFlow(false)
    val isAnalyzingTeam: StateFlow<Boolean> = _isAnalyzingTeam.asStateFlow()

    private val _accumulatorSelections = MutableStateFlow<List<AccumulatorSelection>>(emptyList())
    val accumulatorEvaluation: StateFlow<AccumulatorEvaluation> = _accumulatorSelections
        .map { AccumulatorAnalyzer.evaluate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccumulatorAnalyzer.evaluate(emptyList()))

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val searchSuggestions: List<SearchSuggestion> = footballRepo.getSearchSuggestions()

    val recentSearches: StateFlow<List<String>> = prefsStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notificationPrefs: StateFlow<NotificationPreferences> = prefsStore.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NotificationPreferences())

    // Authenticated Firestore streams (only attached when currentUserId is non-blank)
    val userProfile: StateFlow<UiState<UserProfile?>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeUserProfile(currentUserId).toUiState("profile")
    } else {
        MutableStateFlow(UiState.Success(null))
    }

    val trackedPredictions: StateFlow<UiState<List<TrackedPrediction>>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeMyPredictions(currentUserId).toUiState("predictions")
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val accuracySummary: StateFlow<AccuracyReportSummary> = trackedPredictions
        .map { state ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            CalibrationEngine.summarizeAccuracy(list)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            CalibrationEngine.summarizeAccuracy(emptyList())
        )

    val reviewsState: StateFlow<UiState<List<AppReview>>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeVisibleReviews().toUiState("reviews")
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val commentsState: StateFlow<UiState<List<DiscussionComment>>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeVisibleComments().toUiState("comments")
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val myFeedbackState: StateFlow<UiState<List<OwnerFeedback>>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeMyFeedback(currentUserId).toUiState("feedback")
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    val modelVersionsState: StateFlow<UiState<List<ModelVersionRecord>>> = if (currentUserId.isNotBlank()) {
        cloudRepo.observeModelVersions().toUiState("model_versions")
    } else {
        MutableStateFlow(UiState.Success(emptyList()))
    }

    private var activeModelParams = ModelParameters()

    init {
        refreshFeeds(force = false)
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                cloudRepo.ensureUserProfile()
            }
            observeActiveModelAndSettlement()
        }
    }

    private fun observeActiveModelAndSettlement() {
        viewModelScope.launch {
            modelVersionsState.collect { state ->
                val active = (state as? UiState.Success)?.data?.firstOrNull { it.active }
                if (active != null) {
                    activeModelParams = ModelParameters(
                        versionTag = active.versionTag,
                        dixonColesRho = active.dixonColesRho,
                        homeAdvantageMultiplier = active.homeAdvantage,
                        h2hWeight = active.h2hWeight
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(trackedPredictions, _feedState) { predState, feed ->
                val preds = (predState as? UiState.Success)?.data ?: emptyList()
                preds to feed.recentFinished
            }.collect { (preds, finished) ->
                val pending = preds.filter { it.status == "PENDING" }
                if (pending.isNotEmpty() && finished.isNotEmpty()) {
                    pending.forEach { pred ->
                        val match = finished.firstOrNull { f ->
                            val cleanId = f.matchId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                            cleanId == pred.matchId ||
                                (f.homeTeam.equals(pred.homeTeam, true) && f.awayTeam.equals(pred.awayTeam, true))
                        }
                        if (match != null) {
                            when (match.status) {
                                FixtureStatus.FINISHED -> {
                                    val hg = match.homeScore
                                    val ag = match.awayScore
                                    if (hg != null && ag != null) {
                                        cloudRepo.settlePrediction(pred, hg, ag, voidMatch = false)
                                    }
                                }
                                FixtureStatus.POSTPONED, FixtureStatus.CANCELLED -> {
                                    cloudRepo.settlePrediction(pred, 0, 0, voidMatch = true)
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    fun refreshFeeds(force: Boolean = true) {
        viewModelScope.launch {
            _feedState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = footballRepo.refreshAllFeeds(force = force)
                val predictionsMap = mutableMapOf<String, MatchPredictionReport>()
                val upcomingReports = snapshot.upcomingSevenDays.take(40).map { fix ->
                    val report = footballRepo.generatePredictionForFixture(fix, activeModelParams)
                    predictionsMap[fix.matchId] = report
                    report
                }
                val topOpportunities = upcomingReports
                    .sortedByDescending { it.confidenceScore }
                    .take(8)

                _feedState.update {
                    it.copy(
                        isLoading = false,
                        error = if (snapshot.upcomingSevenDays.isEmpty() && snapshot.recentFinished.isEmpty()) {
                            "Public football feeds returned no active fixtures. Check network connectivity and refresh."
                        } else null,
                        upcomingSevenDays = snapshot.upcomingSevenDays,
                        liveMatches = snapshot.liveMatches,
                        recentFinished = snapshot.recentFinished,
                        topPredictions = topOpportunities,
                        allPredictionsByMatchId = predictionsMap,
                        standingsByCompetition = footballRepo.getAllStandings(),
                        competitions = footballRepo.getCompetitions(),
                        sourceMetadata = footballRepo.getSourceMetadata(),
                        lastUpdatedEpochMs = snapshot.lastUpdatedEpochMs
                    )
                }
                if (_selectedPrediction.value == null && topOpportunities.isNotEmpty()) {
                    _selectedPrediction.value = topOpportunities.first()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load football feeds")
                }
            }
        }
    }

    fun updateFilters(
        country: String? = null,
        competition: String? = null,
        dateFilter: DateRangeFilter? = null,
        gender: GenderCategory? = _feedState.value.selectedGender,
        clearGender: Boolean = false,
        marketFilter: MarketQuickFilter? = null
    ) {
        _feedState.update { curr ->
            curr.copy(
                selectedCountry = country ?: curr.selectedCountry,
                selectedCompetition = competition ?: curr.selectedCompetition,
                selectedDateFilter = dateFilter ?: curr.selectedDateFilter,
                selectedGender = if (clearGender) null else gender,
                selectedMarketFilter = marketFilter ?: curr.selectedMarketFilter
            )
        }
    }

    fun filteredFixtures(state: FootballFeedUiState): List<FixtureItem> {
        val nowCal = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrowStart = todayStart + TimeUnit.DAYS.toMillis(1)
        val dayAfterTomorrowStart = todayStart + TimeUnit.DAYS.toMillis(2)

        return state.upcomingSevenDays.filter { fix ->
            val countryMatch = state.selectedCountry == "All" || fix.country.equals(state.selectedCountry, true)
            val compMatch = state.selectedCompetition == "All" || fix.competition.equals(state.selectedCompetition, true)
            val genderMatch = state.selectedGender == null || fix.gender == state.selectedGender
            val dateMatch = when (state.selectedDateFilter) {
                DateRangeFilter.ALL_7_DAYS -> true
                DateRangeFilter.TODAY -> fix.kickoffEpochMs in todayStart until tomorrowStart
                DateRangeFilter.TOMORROW -> fix.kickoffEpochMs in tomorrowStart until dayAfterTomorrowStart
            }
            val pred = state.allPredictionsByMatchId[fix.matchId]
            val marketMatch = when (state.selectedMarketFilter) {
                MarketQuickFilter.ALL -> true
                MarketQuickFilter.HIGH_CONFIDENCE_1X2 -> (pred?.confidenceScore ?: 0) >= 65
                MarketQuickFilter.BTTS_LIKELY -> {
                    val p = pred?.fullTimeSuite?.groups?.firstOrNull { it.marketName == "Both Teams to Score" }
                        ?.outcomes?.firstOrNull { it.label.contains("Yes") }?.probabilityPct ?: 0
                    p >= 55
                }
                MarketQuickFilter.OVER_25_LIKELY -> {
                    val p = pred?.fullTimeSuite?.groups?.firstOrNull { it.marketName == "Total Goals O/U 2.5" }
                        ?.outcomes?.firstOrNull { it.label.contains("Over") }?.probabilityPct ?: 0
                    p >= 55
                }
            }
            countryMatch && compMatch && genderMatch && dateMatch && marketMatch
        }
    }

    // --- SEARCH ---

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchBundle.value = null
        _searchError.value = null
    }

    fun executeSearch(queryOverride: String? = null) {
        val q = (queryOverride ?: _searchQuery.value).trim()
        if (q.isBlank()) {
            _searchError.value = "Enter a club, national team, women's team, competition, or match."
            return
        }
        _searchQuery.value = q
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            prefsStore.addRecentSearch(q)
            try {
                val bundle = footballRepo.searchFootball(q)
                _searchBundle.value = bundle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchError.value = e.localizedMessage ?: "Search failed"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { prefsStore.clearRecentSearches() }
    }

    // --- PREDICTIONS & TEAM ANALYSIS ---

    fun selectFixtureForPrediction(fixture: FixtureItem) {
        val report = _feedState.value.allPredictionsByMatchId[fixture.matchId]
            ?: footballRepo.generatePredictionForFixture(fixture, activeModelParams)
        _selectedPrediction.value = report
    }

    fun openTeamAnalysis(team: TeamInfo) {
        viewModelScope.launch {
            _isAnalyzingTeam.value = true
            try {
                _selectedTeamAnalysis.value = footballRepo.analyzeTeam(team)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "Unable to load team history: ${e.localizedMessage}"
            } finally {
                _isAnalyzingTeam.value = false
            }
        }
    }

    fun openTeamAnalysisByFixtureTeam(teamId: String, teamName: String, country: String, competition: String, gender: GenderCategory, crestUrl: String?) {
        openTeamAnalysis(
            TeamInfo(
                id = teamId,
                name = teamName,
                shortName = teamName.take(3).uppercase(),
                country = country,
                competition = competition,
                gender = gender,
                crestUrl = crestUrl,
                sourceName = "Connected Football Feed",
                sourceUrl = "https://www.openligadb.de",
                retrievedEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun trackPrediction(report: MatchPredictionReport) {
        if (currentUserId.isBlank()) {
            _actionMessage.value = "Sign in with Google to save predictions to your tracked history."
            return
        }
        viewModelScope.launch {
            val res = cloudRepo.savePredictionSnapshot(report)
            _actionMessage.value = if (res.isSuccess) {
                "Prediction snapshot saved immutably for ${report.fixture.homeTeam} vs ${report.fixture.awayTeam}."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save prediction."
            }
        }
    }

    // --- ACCUMULATOR ---

    fun toggleAccumulatorSelection(selection: AccumulatorSelection) {
        _accumulatorSelections.update { curr ->
            val exists = curr.any { it.selectionKey == selection.selectionKey }
            if (exists) curr.filterNot { it.selectionKey == selection.selectionKey }
            else curr + selection
        }
    }

    fun removeAccumulatorSelection(key: String) {
        _accumulatorSelections.update { curr -> curr.filterNot { it.selectionKey == key } }
    }

    fun clearAccumulator() {
        _accumulatorSelections.value = emptyList()
    }

    // --- COMMUNITY & PROFILE ---

    fun submitReview(rating: Int, text: String) {
        viewModelScope.launch {
            val res = cloudRepo.upsertMyReview(rating, text)
            _actionMessage.value = if (res.isSuccess) "Review published." else res.exceptionOrNull()?.localizedMessage ?: "Failed to save review."
        }
    }

    fun deleteMyReview() {
        viewModelScope.launch {
            val res = cloudRepo.deleteMyReview()
            _actionMessage.value = if (res.isSuccess) "Your review was deleted." else res.exceptionOrNull()?.localizedMessage ?: "Delete failed."
        }
    }

    fun markReviewHelpful(reviewId: String) {
        viewModelScope.launch {
            cloudRepo.markReviewHelpful(reviewId)
        }
    }

    fun postComment(targetType: String, targetId: String, targetLabel: String, body: String, parentCommentId: String? = null) {
        viewModelScope.launch {
            val res = cloudRepo.postComment(targetType, targetId, targetLabel, body, parentCommentId)
            _actionMessage.value = if (res.isSuccess) "Comment posted." else res.exceptionOrNull()?.localizedMessage ?: "Failed to post comment."
        }
    }

    fun updateComment(commentId: String, newBody: String) {
        viewModelScope.launch {
            val res = cloudRepo.updateComment(commentId, newBody)
            _actionMessage.value = if (res.isSuccess) "Comment updated." else res.exceptionOrNull()?.localizedMessage ?: "Update failed."
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            val res = cloudRepo.deleteComment(commentId)
            _actionMessage.value = if (res.isSuccess) "Comment deleted." else res.exceptionOrNull()?.localizedMessage ?: "Delete failed."
        }
    }

    fun submitFeedback(category: String, subject: String, message: String) {
        viewModelScope.launch {
            val res = cloudRepo.submitPrivateFeedback(category, subject, message)
            _actionMessage.value = if (res.isSuccess) "Private message sent to application owner." else res.exceptionOrNull()?.localizedMessage ?: "Failed to send message."
        }
    }

    fun submitReport(targetCollection: String, targetId: String, reason: String) {
        viewModelScope.launch {
            val res = cloudRepo.submitContentReport(targetCollection, targetId, reason)
            _actionMessage.value = if (res.isSuccess) "Report submitted for owner moderation review." else res.exceptionOrNull()?.localizedMessage ?: "Failed to submit report."
        }
    }

    fun updateProfileAndNotifications(
        displayName: String,
        notificationsEnabled: Boolean,
        matchAlerts: Boolean,
        resultAlerts: Boolean
    ) {
        viewModelScope.launch {
            prefsStore.updateNotificationSettings(
                NotificationPreferences(notificationsEnabled, matchAlerts, resultAlerts)
            )
            if (currentUserId.isNotBlank()) {
                val res = cloudRepo.updateUserProfile(displayName, notificationsEnabled, matchAlerts, resultAlerts)
                _actionMessage.value = if (res.isSuccess) "Profile and preferences saved." else res.exceptionOrNull()?.localizedMessage ?: "Update failed."
            } else {
                _actionMessage.value = "Local notification preferences saved."
            }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val res = cloudRepo.deleteAccountData()
            if (res.isSuccess) {
                _actionMessage.value = "Account and profile data deleted."
                onDeleted()
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Account deletion failed. Please re-authenticate and retry."
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> {
        return this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("MainViewModel", "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe $label"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }
}
