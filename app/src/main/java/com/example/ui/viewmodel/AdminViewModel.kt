package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthHelper
import com.example.data.firebase.CommunityAndTrackingRepository
import com.example.data.sources.FootballDataRepository
import com.example.domain.engine.CalibrationEngine
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.AdminAuditLog
import com.example.domain.model.AdminConfig
import com.example.domain.model.AppReview
import com.example.domain.model.ContentReport
import com.example.domain.model.DiscussionComment
import com.example.domain.model.ModelParameters
import com.example.domain.model.ModelVersionRecord
import com.example.domain.model.OwnerFeedback
import com.example.domain.model.RecalibrationProposal
import com.example.domain.model.TrackedPrediction
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val cloudRepo: CommunityAndTrackingRepository,
    private val footballRepo: FootballDataRepository,
    private val currentUserId: String
) : ViewModel() {

    private val isOwnerVerified: Boolean
        get() = AuthHelper.isVerifiedOwnerAdmin(Firebase.auth.currentUser)

    private val _adminConfig = MutableStateFlow<AdminConfig?>(null)
    val adminConfig: StateFlow<AdminConfig?> = _adminConfig.asStateFlow()

    private val _isPasscodeUnlocked = MutableStateFlow(false)
    val isPasscodeUnlocked: StateFlow<Boolean> = _isPasscodeUnlocked.asStateFlow()

    private val _adminAuthError = MutableStateFlow<String?>(null)
    val adminAuthError: StateFlow<String?> = _adminAuthError.asStateFlow()

    private val _adminNotice = MutableStateFlow<String?>(null)
    val adminNotice: StateFlow<String?> = _adminNotice.asStateFlow()

    private val _recalibrationProposal = MutableStateFlow<RecalibrationProposal?>(null)
    val recalibrationProposal: StateFlow<RecalibrationProposal?> = _recalibrationProposal.asStateFlow()

    private val _exportedCsv = MutableStateFlow<String?>(null)
    val exportedCsv: StateFlow<String?> = _exportedCsv.asStateFlow()

    val usersState: StateFlow<UiState<List<UserProfile>>> = if (isOwnerVerified) {
        cloudRepo.observeAllUsersAdmin().toUiState("admin_users")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val predictionsState: StateFlow<UiState<List<TrackedPrediction>>> = if (isOwnerVerified) {
        cloudRepo.observeAllPredictionsAdmin().toUiState("admin_predictions")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val reviewsState: StateFlow<UiState<List<AppReview>>> = if (isOwnerVerified) {
        cloudRepo.observeAllReviewsAdmin().toUiState("admin_reviews")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val commentsState: StateFlow<UiState<List<DiscussionComment>>> = if (isOwnerVerified) {
        cloudRepo.observeAllCommentsAdmin().toUiState("admin_comments")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val feedbackState: StateFlow<UiState<List<OwnerFeedback>>> = if (isOwnerVerified) {
        cloudRepo.observeAllFeedbackAdmin().toUiState("admin_feedback")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val reportsState: StateFlow<UiState<List<ContentReport>>> = if (isOwnerVerified) {
        cloudRepo.observeAllReportsAdmin().toUiState("admin_reports")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val auditLogsState: StateFlow<UiState<List<AdminAuditLog>>> = if (isOwnerVerified) {
        cloudRepo.observeAuditLogsAdmin().toUiState("admin_audit_logs")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val modelVersionsState: StateFlow<UiState<List<ModelVersionRecord>>> = if (isOwnerVerified) {
        cloudRepo.observeModelVersions().toUiState("admin_model_versions")
    } else MutableStateFlow(UiState.Error("Owner verification required"))

    val accuracySummary: StateFlow<AccuracyReportSummary> = predictionsState
        .map { state -> CalibrationEngine.summarizeAccuracy((state as? UiState.Success)?.data ?: emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CalibrationEngine.summarizeAccuracy(emptyList()))

    init {
        if (isOwnerVerified && currentUserId.isNotBlank()) {
            loadAdminConfig()
        }
    }

    fun loadAdminConfig() {
        if (!isOwnerVerified) return
        viewModelScope.launch {
            val res = cloudRepo.getOrInitAdminConfig()
            if (res.isSuccess) {
                _adminConfig.value = res.getOrNull()
            } else {
                _adminAuthError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to load owner security config"
            }
        }
    }

    fun setInitialOwnerPasscode(passcode: String, confirmPasscode: String) {
        if (!isOwnerVerified) {
            _adminAuthError.value = "Only tumusiimeryanpro@gmail.com may configure owner credentials."
            return
        }
        if (passcode.length < 8) {
            _adminAuthError.value = "Owner passphrase must be at least 8 characters."
            return
        }
        if (passcode != confirmPasscode) {
            _adminAuthError.value = "Passphrases do not match."
            return
        }
        viewModelScope.launch {
            val salt = AuthHelper.generateSaltBase64()
            val hash = AuthHelper.hashPasscode(passcode, salt)
            val res = cloudRepo.saveAdminConfig(
                mapOf(
                    "passcodeSalt" to salt,
                    "passcodeHash" to hash,
                    "failedAttempts" to 0,
                    "lockedUntilEpochMs" to 0L
                )
            )
            if (res.isSuccess) {
                cloudRepo.writeAuditLog(AuthHelper.OWNER_ADMIN_EMAIL, "ADMIN_PASSCODE_SET", "admin_config/security", "Owner configured salted passphrase hash.")
                _isPasscodeUnlocked.value = true
                _adminAuthError.value = null
                loadAdminConfig()
            } else {
                _adminAuthError.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save passphrase"
            }
        }
    }

    fun verifyOwnerPasscode(passcode: String) {
        if (!isOwnerVerified) {
            _adminAuthError.value = "Access denied: unauthorised account."
            return
        }
        val cfg = _adminConfig.value ?: run {
            _adminAuthError.value = "Security config not loaded yet."
            return
        }
        val now = System.currentTimeMillis()
        if (cfg.lockedUntilEpochMs > now) {
            val secs = ((cfg.lockedUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
            _adminAuthError.value = "Too many failed attempts. Locked for ${secs}s."
            return
        }
        val salt = cfg.passcodeSalt
        val hash = cfg.passcodeHash
        if (salt.isNullOrBlank() || hash.isNullOrBlank()) {
            _adminAuthError.value = "Owner passphrase not configured yet."
            return
        }
        viewModelScope.launch {
            if (AuthHelper.verifyPasscode(passcode, salt, hash)) {
                cloudRepo.saveAdminConfig(mapOf("failedAttempts" to 0, "lockedUntilEpochMs" to 0L))
                cloudRepo.writeAuditLog(AuthHelper.OWNER_ADMIN_EMAIL, "ADMIN_LOGIN_SUCCESS", "admin_config/security", "Owner verified session.")
                _isPasscodeUnlocked.value = true
                _adminAuthError.value = null
                loadAdminConfig()
            } else {
                val nextAttempts = cfg.failedAttempts + 1
                val lockUntil = if (nextAttempts >= 5) now + 300_000L else 0L
                cloudRepo.saveAdminConfig(mapOf("failedAttempts" to nextAttempts, "lockedUntilEpochMs" to lockUntil))
                cloudRepo.writeAuditLog(AuthHelper.OWNER_ADMIN_EMAIL, "ADMIN_LOGIN_FAILED", "admin_config/security", "Attempt #$nextAttempts")
                _adminAuthError.value = if (lockUntil > 0L) {
                    "5 failed attempts reached. Admin login locked for 5 minutes."
                } else {
                    "Incorrect passphrase (attempt $nextAttempts of 5)."
                }
                loadAdminConfig()
            }
        }
    }

    fun lockAdminSession() {
        _isPasscodeUnlocked.value = false
    }

    fun toggleCompetition(competitionId: String, enabled: Boolean) {
        footballRepo.setCompetitionEnabled(competitionId, enabled)
        viewModelScope.launch {
            cloudRepo.writeAuditLog(AuthHelper.OWNER_ADMIN_EMAIL, "TOGGLE_COMPETITION", "competitions/$competitionId", "enabled=$enabled")
            _adminNotice.value = "Competition $competitionId updated (enabled=$enabled)."
        }
    }

    fun toggleCommunityPosting(open: Boolean) {
        viewModelScope.launch {
            val res = cloudRepo.saveAdminConfig(mapOf("communityPostingOpen" to open))
            if (res.isSuccess) {
                cloudRepo.writeAuditLog(AuthHelper.OWNER_ADMIN_EMAIL, "COMMUNITY_SETTING", "admin_config/security", "communityPostingOpen=$open")
                loadAdminConfig()
                _adminNotice.value = "Community posting setting updated."
            }
        }
    }

    fun setUserRestricted(uid: String, restricted: Boolean) {
        viewModelScope.launch {
            val res = cloudRepo.setUserRestricted(uid, restricted)
            _adminNotice.value = if (res.isSuccess) "User access updated." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun moderateReview(reviewId: String, hidden: Boolean, delete: Boolean) {
        viewModelScope.launch {
            val res = cloudRepo.moderateContent("reviews", reviewId, hidden, delete)
            _adminNotice.value = if (res.isSuccess) "Review moderation applied." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun moderateComment(commentId: String, hidden: Boolean, delete: Boolean) {
        viewModelScope.launch {
            val res = cloudRepo.moderateContent("comments", commentId, hidden, delete)
            _adminNotice.value = if (res.isSuccess) "Comment moderation applied." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun replyToFeedback(feedbackId: String, reply: String, status: String) {
        viewModelScope.launch {
            val res = cloudRepo.replyToFeedback(feedbackId, reply, status)
            _adminNotice.value = if (res.isSuccess) "Owner reply recorded." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun resolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            val res = cloudRepo.resolveReport(reportId, status)
            _adminNotice.value = if (res.isSuccess) "Report marked $status." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun evaluateRecalibration() {
        val preds = (predictionsState.value as? UiState.Success)?.data ?: emptyList()
        val active = (modelVersionsState.value as? UiState.Success)?.data?.firstOrNull { it.active }
        val currentParams = if (active != null) {
            ModelParameters(
                versionTag = active.versionTag,
                dixonColesRho = active.dixonColesRho,
                homeAdvantageMultiplier = active.homeAdvantage,
                h2hWeight = active.h2hWeight
            )
        } else ModelParameters()

        _recalibrationProposal.value = CalibrationEngine.evaluateRecalibration(currentParams, preds)
    }

    fun approveRecalibration(proposal: RecalibrationProposal) {
        if (!proposal.approvedImprovement) {
            _adminNotice.value = "Candidate rejected: it did not outperform the active model on validation data."
            return
        }
        viewModelScope.launch {
            val record = ModelVersionRecord(
                versionTag = proposal.candidateParams.versionTag,
                dixonColesRho = proposal.candidateParams.dixonColesRho,
                homeAdvantage = proposal.candidateParams.homeAdvantageMultiplier,
                h2hWeight = proposal.candidateParams.h2hWeight,
                brierScore = proposal.candidateBrier,
                accuracyPct = proposal.candidateAccuracyPct,
                sampleSize = proposal.sampleSize,
                active = true,
                notes = proposal.evaluationSummary
            )
            val res = cloudRepo.saveModelVersion(record)
            _adminNotice.value = if (res.isSuccess) "Model ${record.versionTag} activated and archived." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun exportPredictionsCsv() {
        val preds = (predictionsState.value as? UiState.Success)?.data ?: emptyList()
        val sb = StringBuilder()
        sb.appendLine("matchId,homeTeam,awayTeam,competition,modelVersion,homeXg,awayXg,pHome,pDraw,pAway,status,actualHome,actualAway,outcomeCorrect")
        preds.forEach { p ->
            sb.appendLine("${p.matchId},\"${p.homeTeam}\",\"${p.awayTeam}\",\"${p.competition}\",${p.modelVersion},${p.homeXg},${p.awayXg},${p.probHomeWin},${p.probDraw},${p.probAwayWin},${p.status},${p.actualHomeGoals ?: ""},${p.actualAwayGoals ?: ""},${p.outcomeCorrect ?: ""}")
        }
        _exportedCsv.value = sb.toString()
    }

    fun clearNotice() {
        _adminNotice.value = null
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> {
        return this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("AdminViewModel", "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe $label"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }
}
