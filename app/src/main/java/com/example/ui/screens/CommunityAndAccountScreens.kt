package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.firebase.AuthHelper
import com.example.data.local.NotificationHelper
import com.example.data.local.NotificationPreferences
import com.example.domain.model.AppReview
import com.example.domain.model.DiscussionComment
import com.example.domain.model.OwnerFeedback
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.GoldBadge
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TeamCrest
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseUser

@Composable
fun CommunityAndReviewsScreen(
    currentUser: FirebaseUser?,
    reviewsState: UiState<List<AppReview>>,
    commentsState: UiState<List<DiscussionComment>>,
    myFeedbackState: UiState<List<OwnerFeedback>>,
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reportReason by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = CharcoalElevated, contentColor = GoldBright) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Reviews") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Discussions") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Owner Message") })
        }

        if (currentUser == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LuxuryCard(highlighted = true) {
                        Text("Account Required for Community Access", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Sign in with Google to read and post application reviews, comment on teams and fixtures, report inappropriate content, or send private feedback to the application owner.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IvoryWhite
                        )
                        Spacer(Modifier.height(12.dp))
                        GoogleSignInButton(onAuthSuccess = onAuthSuccess, onAuthError = onAuthError)
                    }
                }
            }
            return
        }

        when (selectedTab) {
            0 -> ReviewsTab(
                uid = currentUser.uid,
                reviews = (reviewsState as? UiState.Success)?.data ?: emptyList(),
                onSaveReview = { r, txt -> viewModel.submitReview(r, txt) },
                onDeleteReview = { viewModel.deleteMyReview() },
                onHelpful = { id -> viewModel.markReviewHelpful(id) },
                onReport = { id -> reportTarget = "reviews" to id }
            )
            1 -> DiscussionsTab(
                uid = currentUser.uid,
                comments = (commentsState as? UiState.Success)?.data ?: emptyList(),
                onPostComment = { tType, tId, tLabel, body, parentId ->
                    viewModel.postComment(tType, tId, tLabel, body, parentId)
                },
                onDeleteComment = { id -> viewModel.deleteComment(id) },
                onReport = { id -> reportTarget = "comments" to id }
            )
            else -> FeedbackTab(
                feedbackList = (myFeedbackState as? UiState.Success)?.data ?: emptyList(),
                onSubmit = { cat, subj, msg -> viewModel.submitFeedback(cat, subj, msg) }
            )
        }
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { reportTarget = null; reportReason = "" },
            title = { Text("Report Inappropriate Content") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Reason for report") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = reportTarget
                    if (target != null && reportReason.trim().length >= 3) {
                        viewModel.submitReport(target.first, target.second, reportReason)
                        reportTarget = null
                        reportReason = ""
                    }
                }) { Text("Submit Report") }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null; reportReason = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReviewsTab(
    uid: String,
    reviews: List<AppReview>,
    onSaveReview: (Int, String) -> Unit,
    onDeleteReview: () -> Unit,
    onHelpful: (String) -> Unit,
    onReport: (String) -> Unit
) {
    val myReview = reviews.firstOrNull { it.userId == uid }
    var rating by remember(myReview?.id) { mutableIntStateOf(myReview?.rating ?: 5) }
    var reviewText by remember(myReview?.id) { mutableStateOf(myReview?.reviewText ?: "") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LuxuryCard(highlighted = true) {
                Text(if (myReview == null) "Rate & Review the Application" else "Update Your Review", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$star stars",
                                tint = GoldBright
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text("Share honest feedback (1 review per account)") },
                    modifier = Modifier.fillMaxWidth().testTag("review_text_input"),
                    minLines = 2
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSaveReview(rating, reviewText) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        modifier = Modifier.weight(1f).testTag("submit_review_button")
                    ) {
                        Text(if (myReview == null) "Publish Review" else "Update Review")
                    }
                    if (myReview != null) {
                        OutlinedButton(onClick = onDeleteReview) {
                            Text("Delete", color = CrimsonLoss)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Published Member Reviews (${reviews.size})")
        }
        if (reviews.isEmpty()) {
            item { DisclaimerBanner("No member reviews published yet.") }
        } else {
            items(reviews, key = { it.id }) { rev ->
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(rev.authorPhotoUrl, rev.authorName, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(rev.authorName, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                                Text(
                                    "${"★".repeat(rev.rating)}  •  ${rev.updatedAt?.toDate()?.let { DateTimeFormatterUtil.formatLocalDate(it.time) } ?: "Just now"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldBright
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onHelpful(rev.id) }) {
                                Icon(Icons.Filled.ThumbUp, contentDescription = "Helpful", tint = SilverGrey, modifier = Modifier.size(18.dp))
                            }
                            if (rev.userId != uid) {
                                IconButton(onClick = { onReport(rev.id) }) {
                                    Icon(Icons.Filled.Flag, contentDescription = "Report review", tint = SilverGrey, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(rev.reviewText, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    if (rev.helpfulCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${rev.helpfulCount} members found this helpful", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionsTab(
    uid: String,
    comments: List<DiscussionComment>,
    onPostComment: (String, String, String, String, String?) -> Unit,
    onDeleteComment: (String) -> Unit,
    onReport: (String) -> Unit
) {
    var topicLabel by remember { mutableStateOf("General Football") }
    var targetType by remember { mutableStateOf("GENERAL") }
    var commentBody by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LuxuryCard(highlighted = true) {
                Text("Post Football Comment", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GENERAL" to "General Football", "FIXTURE" to "Upcoming Fixtures", "TEAM" to "Team Analysis").forEach { (type, label) ->
                        FilterChip(
                            selected = targetType == type,
                            onClick = { targetType = type; topicLabel = label },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                        )
                    }
                }
                OutlinedTextField(
                    value = topicLabel,
                    onValueChange = { topicLabel = it },
                    label = { Text("Team or Fixture Topic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                if (replyToId != null) {
                    Text("Replying to comment • Tap to clear", style = MaterialTheme.typography.labelSmall, color = GoldBright, modifier = Modifier.clickable { replyToId = null })
                }
                OutlinedTextField(
                    value = commentBody,
                    onValueChange = { commentBody = it },
                    label = { Text("Share tactical or statistical thoughts") },
                    modifier = Modifier.fillMaxWidth().testTag("comment_body_input"),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onPostComment(targetType, topicLabel.lowercase().replace(" ", "_"), topicLabel, commentBody, replyToId)
                        commentBody = ""
                        replyToId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier.fillMaxWidth().testTag("post_comment_button")
                ) {
                    Text("Post Comment")
                }
            }
        }

        items(comments, key = { it.id }) { com ->
            LuxuryCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(com.authorName, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                        Text("${com.targetType}: ${com.targetLabel}", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                    }
                    Row {
                        IconButton(onClick = { replyToId = com.id }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", tint = SilverGrey, modifier = Modifier.size(18.dp))
                        }
                        if (com.userId == uid) {
                            IconButton(onClick = { onDeleteComment(com.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete comment", tint = CrimsonLoss, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = { onReport(com.id) }) {
                                Icon(Icons.Filled.Flag, contentDescription = "Report comment", tint = SilverGrey, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (!com.parentCommentId.isNullOrBlank()) {
                    Text("↳ Reply in thread", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                }
                Spacer(Modifier.height(4.dp))
                Text(com.body, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
            }
        }
    }
}

@Composable
private fun FeedbackTab(
    feedbackList: List<OwnerFeedback>,
    onSubmit: (String, String, String) -> Unit
) {
    var category by remember { mutableStateOf("GENERAL") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LuxuryCard(highlighted = true) {
                Text("Private Message to Owner", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Text("Sent privately to the application owner only — never published publicly.", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GENERAL" to "General", "BUG" to "Bug Report", "FEATURE" to "Feature", "DATA_CORRECTION" to "Data Fix", "OTHER" to "Other").forEach { (key, label) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                        )
                    }
                }
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSubmit(category, subject, message)
                        subject = ""
                        message = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier.fillMaxWidth().testTag("submit_feedback_button")
                ) {
                    Text("Send Private Feedback")
                }
            }
        }

        item { SectionHeader("My Private Messages (${feedbackList.size})") }
        items(feedbackList, key = { it.id }) { fb ->
            LuxuryCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fb.subject, style = MaterialTheme.typography.titleSmall, color = IvoryWhite, modifier = Modifier.weight(1f))
                    GoldBadge(fb.status)
                }
                Text(fb.category, style = MaterialTheme.typography.labelSmall, color = GoldBright)
                Spacer(Modifier.height(4.dp))
                Text(fb.message, style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                if (!fb.ownerReply.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Owner Reply: ${fb.ownerReply}", style = MaterialTheme.typography.bodySmall, color = PitchEmerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SignInScreen(
    currentUser: FirebaseUser?,
    onAuthSuccess: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenAdminLogin: () -> Unit
) {
    var authError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Account Sign In", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "Sign in to sync tracked predictions, post reviews, join discussions, and manage preferences.",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        if (currentUser != null) {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Signed in as ${currentUser.displayName ?: currentUser.email}", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        } else {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Option A: Official Google Sign-In", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Authenticated via Jetpack Credential Manager and Firebase Auth. Your Google password is never seen or stored by this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IvoryWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    GoogleSignInButton(
                        label = "Continue with Google",
                        onAuthSuccess = {
                            authError = null
                            onAuthSuccess()
                        },
                        onAuthError = { authError = it }
                    )
                    if (authError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(authError!!, style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                    }
                }
            }

            item {
                LuxuryCard {
                    Text("Option B: Email & Password Registration", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This environment provisions Google Sign-In as the primary identity provider. To enable Email/Password registration, verification emails, and password resets, activate the Email/Password sign-in method in your Firebase Authentication console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverGrey
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.weight(1f)) {
                    Text("Privacy Policy", color = GoldBright)
                }
                OutlinedButton(onClick = onOpenTerms, modifier = Modifier.weight(1f)) {
                    Text("Terms of Use", color = GoldBright)
                }
            }
        }

        item {
            LuxuryCard(onClick = onOpenAdminLogin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = GoldPrimary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Administrator Login", style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                        Text("Separate owner-only portal", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                    }
                    GoldBadge("OWNER")
                }
            }
        }
    }
}

@Composable
fun UserProfileScreen(
    currentUser: FirebaseUser?,
    profileState: UiState<UserProfile?>,
    notifPrefs: NotificationPreferences,
    reviewCount: Int,
    commentCount: Int,
    feedbackCount: Int,
    viewModel: MainViewModel,
    onSignedOut: () -> Unit,
    onOpenSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile = (profileState as? UiState.Success)?.data
    var editName by remember(profile?.displayName, currentUser?.displayName) {
        mutableStateOf(profile?.displayName ?: currentUser?.displayName ?: "")
    }
    var localAlerts by remember(notifPrefs.localNotificationsEnabled) { mutableStateOf(notifPrefs.localNotificationsEnabled) }
    var matchAlerts by remember(notifPrefs.fixtureAlerts) { mutableStateOf(notifPrefs.fixtureAlerts) }
    var resultAlerts by remember(notifPrefs.settlementAlerts) { mutableStateOf(notifPrefs.settlementAlerts) }
    var confirmDelete by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        localAlerts = granted
        viewModel.updateProfileAndNotifications(editName, granted, matchAlerts, resultAlerts)
        if (granted) {
            NotificationHelper.sendLocalAlert(context, "All Football Predictions", "Match and settlement notifications enabled.")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("User Profile & Settings", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
        }

        if (currentUser == null) {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Not Signed In", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Spacer(Modifier.height(6.dp))
                    Text("Sign in with Google to view your profile, community activity counts, and notification preferences.", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Sign In")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            LuxuryCard(highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(currentUser.photoUrl?.toString(), editName.ifBlank { "U" }, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(editName.ifBlank { "Member" }, style = MaterialTheme.typography.titleLarge, color = IvoryWhite)
                        Text(currentUser.email ?: "", style = MaterialTheme.typography.bodySmall, color = GoldBright)
                        Text(
                            "Member since: ${profile?.createdAt?.toDate()?.let { DateTimeFormatterUtil.formatLocalDate(it.time) } ?: "Today"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverGrey
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("REVIEWS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Text("$reviewCount", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                }
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("COMMENTS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Text("$commentCount", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                }
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("FEEDBACK", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Text("$feedbackCount", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                }
            }
        }

        item {
            LuxuryCard {
                Text("Edit Profile & Notification Preferences", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android Match Notifications", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                        Text("On-device alerts (no paid push server required)", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    }
                    Switch(
                        checked = localAlerts,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(context)) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                localAlerts = checked
                            }
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upcoming Fixture Alerts", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    Switch(checked = matchAlerts, onCheckedChange = { matchAlerts = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Result Settlement Alerts", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    Switch(checked = resultAlerts, onCheckedChange = { resultAlerts = it })
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.updateProfileAndNotifications(editName, localAlerts, matchAlerts, resultAlerts) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                ) {
                    Text("Save Profile & Preferences")
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        AuthHelper.signOut(credentialManager, scope) {
                            onSignedOut()
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("sign_out_button")
                ) {
                    Text("Sign Out", color = GoldBright)
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f).testTag("delete_account_button")
                ) {
                    Text("Delete Account", color = CrimsonLoss)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Account & Data?") },
            text = { Text("This permanently deletes your profile record, active review, and Firebase authentication account.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteAccount { onSignedOut() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss, contentColor = IvoryWhite)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
