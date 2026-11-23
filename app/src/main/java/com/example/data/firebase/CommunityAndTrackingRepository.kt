package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.AdminAuditLog
import com.example.domain.model.AdminConfig
import com.example.domain.model.AppReview
import com.example.domain.model.ContentReport
import com.example.domain.model.DiscussionComment
import com.example.domain.model.MatchPredictionReport
import com.example.domain.model.ModelVersionRecord
import com.example.domain.model.OwnerFeedback
import com.example.domain.model.TrackedPrediction
import com.example.domain.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CommunityAndTrackingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private var lastCommunityPostMs: Long = 0L

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud records.")
    }

    private fun requireAdminEmail(): String {
        val user = auth.currentUser
        if (!AuthHelper.isVerifiedOwnerAdmin(user)) {
            throw SecurityException("Owner administrator access required.")
        }
        return user?.email ?: AuthHelper.OWNER_ADMIN_EMAIL
    }

    fun sanitizeInput(raw: String, maxLen: Int): String {
        return raw
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .trim()
            .take(maxLen)
    }

    private fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        if (now - lastCommunityPostMs < 5_000L) {
            throw IllegalStateException("Please wait a few seconds between submissions.")
        }
        lastCommunityPostMs = now
    }

    // --- USER PROFILE ---

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val displayName = sanitizeInput(user.displayName?.takeIf { it.isNotBlank() } ?: "Football Fan", 80)
            val email = sanitizeInput(user.email ?: "", 160)
            val photoUrl = user.photoUrl?.toString()?.take(500)
            val payload = mapOf(
                "userId" to user.uid,
                "displayName" to displayName,
                "email" to email,
                "photoUrl" to photoUrl,
                "notificationsEnabled" to true,
                "matchAlertsEnabled" to true,
                "resultAlertsEnabled" to true,
                "restricted" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = user.uid, userId = user.uid, displayName = displayName, email = email, photoUrl = photoUrl)
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId).snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserProfile(
        displayName: String,
        notificationsEnabled: Boolean,
        matchAlertsEnabled: Boolean,
        resultAlertsEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = sanitizeInput(displayName, 80)
            require(cleanName.isNotBlank()) { "Display name cannot be empty" }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "notificationsEnabled" to notificationsEnabled,
                    "matchAlertsEnabled" to matchAlertsEnabled,
                    "resultAlertsEnabled" to resultAlertsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountData(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            // Delete user review if present
            runCatching { db.collection("reviews").document(uid).delete().await() }
            db.collection("users").document(uid).delete().await()
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PREDICTIONS TRACKING ---

    fun observeMyPredictions(userId: String): Flow<List<TrackedPrediction>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "predictions"
        emitAll(
            db.collection("predictions")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(TrackedPrediction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.kickoffEpochMs }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePredictionSnapshot(report: MatchPredictionReport): Result<Unit> {
        val path = "predictions"
        return try {
            val uid = requireUserId()
            val cleanMatchId = report.fixture.matchId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val docId = "${uid}_$cleanMatchId".take(128)
            val docRef = db.collection("predictions").document(docId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                return Result.success(Unit) // Immutable snapshot already preserved
            }

            val topScore = report.fullTimeCorrectScores.firstOrNull()
            val bttsYes = report.fullTimeSuite.groups
                .firstOrNull { it.marketName == "Both Teams to Score" }
                ?.outcomes?.firstOrNull { it.label.contains("Yes") }?.probabilityPct ?: 50
            val over25 = report.fullTimeSuite.groups
                .firstOrNull { it.marketName == "Total Goals O/U 2.5" }
                ?.outcomes?.firstOrNull { it.label.contains("Over") }?.probabilityPct ?: 50

            val payload = mapOf(
                "userId" to uid,
                "matchId" to cleanMatchId,
                "homeTeam" to sanitizeInput(report.fixture.homeTeam, 100),
                "awayTeam" to sanitizeInput(report.fixture.awayTeam, 100),
                "competition" to sanitizeInput(report.fixture.competition, 120),
                "country" to sanitizeInput(report.fixture.country, 80),
                "kickoffEpochMs" to report.fixture.kickoffEpochMs,
                "modelVersion" to sanitizeInput(report.modelVersion, 60),
                "dataSources" to sanitizeInput(report.dataSources.joinToString(", "), 200),
                "homeXg" to report.modelEstimatedHomeXg,
                "awayXg" to report.modelEstimatedAwayXg,
                "probHomeWin" to report.probHomeWin,
                "probDraw" to report.probDraw,
                "probAwayWin" to report.probAwayWin,
                "probBttsYes" to bttsYes,
                "probOver25" to over25,
                "topCorrectScore" to (topScore?.scoreLine ?: "1-1"),
                "topCorrectScoreProb" to (topScore?.probabilityPct?.toInt() ?: 10),
                "confidenceScore" to report.confidenceScore,
                "qualityWarning" to report.reliabilityWarnings.firstOrNull()?.take(300),
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun settlePrediction(
        prediction: TrackedPrediction,
        actualHomeGoals: Int,
        actualAwayGoals: Int,
        voidMatch: Boolean = false
    ): Result<Unit> {
        val path = "predictions/${prediction.id}"
        return try {
            requireUserId()
            require(prediction.id.isNotBlank()) { "Prediction ID missing" }
            if (prediction.status != "PENDING" && !AuthHelper.isVerifiedOwnerAdmin(auth.currentUser)) {
                return Result.success(Unit)
            }
            val updates = if (voidMatch) {
                mapOf(
                    "status" to "VOID",
                    "settledAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                val actual1X2 = when {
                    actualHomeGoals > actualAwayGoals -> "HOME"
                    actualHomeGoals == actualAwayGoals -> "DRAW"
                    else -> "AWAY"
                }
                val predicted1X2 = when {
                    prediction.probHomeWin >= prediction.probDraw && prediction.probHomeWin >= prediction.probAwayWin -> "HOME"
                    prediction.probDraw >= prediction.probHomeWin && prediction.probDraw >= prediction.probAwayWin -> "DRAW"
                    else -> "AWAY"
                }
                val btts = if (actualHomeGoals > 0 && actualAwayGoals > 0) "YES" else "NO"
                val over25 = if (actualHomeGoals + actualAwayGoals > 2) "OVER" else "UNDER"
                mapOf(
                    "status" to "SETTLED",
                    "actualHomeGoals" to actualHomeGoals,
                    "actualAwayGoals" to actualAwayGoals,
                    "settledResult1X2" to actual1X2,
                    "settledBtts" to btts,
                    "settledOver25" to over25,
                    "outcomeCorrect" to (actual1X2 == predicted1X2),
                    "settledAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("predictions").document(prediction.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- COMMUNITY REVIEWS ---

    fun observeVisibleReviews(): Flow<List<AppReview>> = flow {
        requireUserId()
        emitAll(
            db.collection("reviews")
                .whereEqualTo("hidden", false)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, "reviews")
                    throw error
                }
        )
    }

    suspend fun upsertMyReview(rating: Int, reviewText: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val path = "reviews/$uid"
        return try {
            enforceRateLimit()
            val cleanText = sanitizeInput(reviewText, 1000)
            require(rating in 1..5) { "Rating must be between 1 and 5" }
            require(cleanText.length >= 3) { "Review must be at least 3 characters" }
            val user = auth.currentUser!!
            val authorName = sanitizeInput(user.displayName?.takeIf { it.isNotBlank() } ?: "Member", 80)
            val photoUrl = user.photoUrl?.toString()?.take(500)

            val docRef = db.collection("reviews").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "authorName" to authorName,
                    "rating" to rating,
                    "reviewText" to cleanText,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) updates["authorPhotoUrl"] = photoUrl
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "authorName" to authorName,
                    "authorPhotoUrl" to photoUrl,
                    "rating" to rating,
                    "reviewText" to cleanText,
                    "helpfulCount" to 0,
                    "hidden" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMyReview(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val path = "reviews/$uid"
        return try {
            db.collection("reviews").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun markReviewHelpful(reviewId: String): Result<Unit> {
        val path = "reviews/$reviewId"
        return try {
            requireUserId()
            db.collection("reviews").document(reviewId).update(
                mapOf(
                    "helpfulCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- COMMUNITY COMMENTS & DISCUSSIONS ---

    fun observeVisibleComments(): Flow<List<DiscussionComment>> = flow {
        requireUserId()
        emitAll(
            db.collection("comments")
                .whereEqualTo("hidden", false)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DiscussionComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) handleFirestoreError(fsError ?: error, OperationType.LIST, "comments")
                    throw error
                }
        )
    }

    suspend fun postComment(
        targetType: String,
        targetId: String,
        targetLabel: String,
        body: String,
        parentCommentId: String? = null
    ): Result<Unit> {
        val path = "comments"
        return try {
            enforceRateLimit()
            val uid = requireUserId()
            val cleanBody = sanitizeInput(body, 800)
            require(cleanBody.length >= 2) { "Comment is too short" }
            val user = auth.currentUser!!
            val authorName = sanitizeInput(user.displayName?.takeIf { it.isNotBlank() } ?: "Member", 80)
            val docId = "c_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val payload = mapOf(
                "userId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to user.photoUrl?.toString()?.take(500),
                "targetType" to targetType,
                "targetId" to targetId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "general" },
                "targetLabel" to sanitizeInput(targetLabel, 120).ifBlank { "General Football" },
                "parentCommentId" to parentCommentId,
                "body" to cleanBody,
                "hidden" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("comments").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateComment(commentId: String, newBody: String): Result<Unit> {
        val path = "comments/$commentId"
        return try {
            requireUserId()
            val cleanBody = sanitizeInput(newBody, 800)
            require(cleanBody.length >= 2) { "Comment cannot be empty" }
            db.collection("comments").document(commentId).update(
                mapOf("body" to cleanBody, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> {
        val path = "comments/$commentId"
        return try {
            requireUserId()
            db.collection("comments").document(commentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PRIVATE FEEDBACK & REPORTS ---

    fun observeMyFeedback(userId: String): Flow<List<OwnerFeedback>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        emitAll(
            db.collection("feedback")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(OwnerFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) handleFirestoreError(fsError ?: error, OperationType.LIST, "feedback")
                    throw error
                }
        )
    }

    suspend fun submitPrivateFeedback(category: String, subject: String, message: String): Result<Unit> {
        val path = "feedback"
        return try {
            enforceRateLimit()
            val uid = requireUserId()
            val cleanSubject = sanitizeInput(subject, 120)
            val cleanMessage = sanitizeInput(message, 2000)
            require(cleanSubject.length >= 3) { "Subject must be at least 3 characters" }
            require(cleanMessage.length >= 5) { "Message must be at least 5 characters" }
            val senderName = sanitizeInput(auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Member", 80)
            val docId = "fb_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val payload = mapOf(
                "userId" to uid,
                "senderName" to senderName,
                "category" to category,
                "subject" to cleanSubject,
                "message" to cleanMessage,
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("feedback").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitContentReport(targetCollection: String, targetId: String, reason: String): Result<Unit> {
        val path = "reports"
        return try {
            enforceRateLimit()
            val uid = requireUserId()
            val cleanReason = sanitizeInput(reason, 500)
            require(cleanReason.length >= 3) { "Please provide a reason" }
            val docId = "rep_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val payload = mapOf(
                "reporterId" to uid,
                "targetCollection" to targetCollection,
                "targetId" to targetId,
                "reason" to cleanReason,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("reports").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- OWNER ADMINISTRATOR OPERATIONS ---

    fun observeAllUsersAdmin(): Flow<List<UserProfile>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("users").snapshots()
                .map { it.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "users")
                    throw error
                }
        )
    }

    fun observeAllPredictionsAdmin(): Flow<List<TrackedPrediction>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("predictions").snapshots()
                .map { it.toObjects(TrackedPrediction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "predictions")
                    throw error
                }
        )
    }

    fun observeAllReviewsAdmin(): Flow<List<AppReview>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("reviews").snapshots()
                .map { it.toObjects(AppReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "reviews")
                    throw error
                }
        )
    }

    fun observeAllCommentsAdmin(): Flow<List<DiscussionComment>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("comments").snapshots()
                .map { it.toObjects(DiscussionComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "comments")
                    throw error
                }
        )
    }

    fun observeAllFeedbackAdmin(): Flow<List<OwnerFeedback>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("feedback").snapshots()
                .map { it.toObjects(OwnerFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "feedback")
                    throw error
                }
        )
    }

    fun observeAllReportsAdmin(): Flow<List<ContentReport>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("reports").snapshots()
                .map { it.toObjects(ContentReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "reports")
                    throw error
                }
        )
    }

    fun observeAuditLogsAdmin(): Flow<List<AdminAuditLog>> = flow {
        requireAdminEmail()
        emitAll(
            db.collection("admin_audit_logs").snapshots()
                .map { snap ->
                    snap.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "admin_audit_logs")
                    throw error
                }
        )
    }

    fun observeModelVersions(): Flow<List<ModelVersionRecord>> = flow {
        requireUserId()
        emitAll(
            db.collection("model_versions").snapshots()
                .map { snap ->
                    snap.toObjects(ModelVersionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    handleFirestoreError(fsError ?: error, OperationType.LIST, "model_versions")
                    throw error
                }
        )
    }

    suspend fun getOrInitAdminConfig(): Result<AdminConfig> {
        val path = "admin_config/security"
        return try {
            requireAdminEmail()
            val ref = db.collection("admin_config").document("security")
            val snap = ref.get().await()
            if (snap.exists()) {
                val cfg = snap.toObject(AdminConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (cfg != null) return Result.success(cfg)
            }
            val payload = mapOf(
                "failedAttempts" to 0,
                "lockedUntilEpochMs" to 0L,
                "communityPostingOpen" to true,
                "autoRecalibrationEnabled" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(AdminConfig(id = "security"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveAdminConfig(updates: Map<String, Any>): Result<Unit> {
        val path = "admin_config/security"
        return try {
            requireAdminEmail()
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("admin_config").document("security").update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserRestricted(targetUid: String, restricted: Boolean): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            val email = requireAdminEmail()
            db.collection("users").document(targetUid).update(
                mapOf("restricted" to restricted, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            writeAuditLog(email, if (restricted) "RESTRICT_USER" else "UNRESTRICT_USER", path, "restricted=$restricted")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun moderateContent(collection: String, docId: String, hidden: Boolean, delete: Boolean): Result<Unit> {
        val path = "$collection/$docId"
        return try {
            val email = requireAdminEmail()
            if (delete) {
                db.collection(collection).document(docId).delete().await()
                writeAuditLog(email, "DELETE_CONTENT", path, "Permanently removed content")
            } else {
                db.collection(collection).document(docId).update(
                    mapOf("hidden" to hidden, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                writeAuditLog(email, if (hidden) "HIDE_CONTENT" else "RESTORE_CONTENT", path, "hidden=$hidden")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun replyToFeedback(feedbackId: String, reply: String, status: String): Result<Unit> {
        val path = "feedback/$feedbackId"
        return try {
            val email = requireAdminEmail()
            val cleanReply = sanitizeInput(reply, 2000)
            db.collection("feedback").document(feedbackId).update(
                mapOf(
                    "ownerReply" to cleanReply,
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(email, "REPLY_FEEDBACK", path, "status=$status")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveReport(reportId: String, status: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            val email = requireAdminEmail()
            db.collection("reports").document(reportId).update(
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            writeAuditLog(email, "RESOLVE_REPORT", path, "status=$status")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveModelVersion(record: ModelVersionRecord): Result<Unit> {
        val docId = record.versionTag.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val path = "model_versions/$docId"
        return try {
            val email = requireAdminEmail()
            val payload = mapOf(
                "versionTag" to record.versionTag,
                "dixonColesRho" to record.dixonColesRho,
                "homeAdvantage" to record.homeAdvantage,
                "h2hWeight" to record.h2hWeight,
                "brierScore" to record.brierScore,
                "accuracyPct" to record.accuracyPct,
                "sampleSize" to record.sampleSize,
                "active" to record.active,
                "notes" to sanitizeInput(record.notes, 500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("model_versions").document(docId).set(payload).await()
            writeAuditLog(email, "SAVE_MODEL_VERSION", path, record.notes.take(200))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun writeAuditLog(actorEmail: String, action: String, targetPath: String, details: String) {
        try {
            val docId = "log_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val payload = mapOf(
                "actorEmail" to actorEmail,
                "action" to sanitizeInput(action, 100),
                "targetPath" to sanitizeInput(targetPath, 200),
                "details" to sanitizeInput(details, 1000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("admin_audit_logs").document(docId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "admin_audit_logs")
        }
    }
}
