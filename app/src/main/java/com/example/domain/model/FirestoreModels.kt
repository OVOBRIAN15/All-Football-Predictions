package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val notificationsEnabled: Boolean = true,
    val matchAlertsEnabled: Boolean = true,
    val resultAlertsEnabled: Boolean = true,
    val restricted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TrackedPrediction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val matchId: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
    val competition: String = "",
    val country: String = "",
    val kickoffEpochMs: Long = 0L,
    val modelVersion: String = "v1.0-dc-poisson",
    val dataSources: String = "",
    val homeXg: Double = 0.0,
    val awayXg: Double = 0.0,
    val probHomeWin: Int = 0,
    val probDraw: Int = 0,
    val probAwayWin: Int = 0,
    val probBttsYes: Int = 0,
    val probOver25: Int = 0,
    val topCorrectScore: String = "1-1",
    val topCorrectScoreProb: Int = 0,
    val confidenceScore: Int = 0,
    val qualityWarning: String? = null,
    val status: String = "PENDING",
    val actualHomeGoals: Int? = null,
    val actualAwayGoals: Int? = null,
    val settledResult1X2: String? = null,
    val settledBtts: String? = null,
    val settledOver25: String? = null,
    val outcomeCorrect: Boolean? = null,
    val settledAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val rating: Int = 5,
    val reviewText: String = "",
    val helpfulCount: Int = 0,
    val hidden: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DiscussionComment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val targetType: String = "GENERAL",
    val targetId: String = "general",
    val targetLabel: String = "General Football",
    val parentCommentId: String? = null,
    val body: String = "",
    val hidden: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnerFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderName: String = "",
    val category: String = "GENERAL",
    val subject: String = "",
    val message: String = "",
    val status: String = "OPEN",
    val ownerReply: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ContentReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetCollection: String = "comments",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModelVersionRecord(
    @DocumentId val id: String = "",
    val versionTag: String = "v1.0-dc-poisson",
    val dixonColesRho: Double = -0.11,
    val homeAdvantage: Double = 1.14,
    val h2hWeight: Double = 0.18,
    val brierScore: Double = 0.0,
    val accuracyPct: Double = 0.0,
    val sampleSize: Int = 0,
    val active: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetPath: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminConfig(
    @DocumentId val id: String = "",
    val passcodeHash: String? = null,
    val passcodeSalt: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long = 0L,
    val communityPostingOpen: Boolean = true,
    val autoRecalibrationEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
