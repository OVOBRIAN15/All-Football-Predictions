package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.firebase.AuthHelper
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.AdminAuditLog
import com.example.domain.model.AdminConfig
import com.example.domain.model.AppReview
import com.example.domain.model.CompetitionInfo
import com.example.domain.model.ContentReport
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.DiscussionComment
import com.example.domain.model.ModelVersionRecord
import com.example.domain.model.OwnerFeedback
import com.example.domain.model.RecalibrationProposal
import com.example.domain.model.TrackedPrediction
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.GoldBadge
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey
import com.example.ui.viewmodel.AdminViewModel
import com.google.firebase.auth.FirebaseUser

@Composable
fun ReadMeScreen(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val sections = remember {
        listOf(
            "1. What All Football Predictions Does" to "All Football Predictions is a statistical football information and probability modelling platform. It retrieves public football schedules, team histories, league standings, and live scores where available, and computes transparent probability distributions for upcoming matches.",
            "2. How Football Information Is Retrieved" to "Data is fetched directly over HTTPS using modular free, no-key public football adapters (OpenLigaDB, TheSportsDB free public tier, and Wikimedia Open Search). Every fixture and team card records its origin source and retrieval timestamp.",
            "3. How Football Search Works" to "Searching queries structured club and league datasets alongside Wikimedia Open Search for global clubs, national teams, and women's teams. Google Custom Search JSON API requires a billed/quota Programmable Search key and is not activated without owner approval; we never scrape Google search pages.",
            "4. Free Football Data Sources & Limitations" to "Free public datasets do not provide complete worldwide coverage for every lower division or real-time minute feed. Coverage is strictly honest about supported competitions and marks unavailable feeds clearly.",
            "5. How Predictions Are Calculated" to "Predictions use deterministic mathematical calculations with zero random numbers: weighted last-5 form, separate home and away attack/defence averages, Bayesian shrinkage toward league baselines, and up to 5 historical head-to-head meetings.",
            "6. What Probability Percentages Mean" to "A 50% Home Win probability means that across many statistically comparable matches, the home side wins roughly half the time. Mutually exclusive outcomes (such as 1X2) sum to 100%.",
            "7. How Poisson & Dixon-Coles Modelling Works" to "Each team's expected goals (lambda) feeds a bivariate Poisson goal matrix from 0 to 8 goals, adjusted for low-score correlation (0-0, 1-0, 0-1, 1-1) using the Dixon-Coles tau factor.",
            "8. Official xG vs Model-Estimated Expected Goals" to "Official provider xG comes from shot-level event tracking when supplied by a connected provider. When unavailable, our engine displays 'Model-Estimated xG' derived from goal-scoring rates—never mislabelled as official shot-level xG.",
            "9. Correct-Score Probabilities" to "Every correct-score percentage (e.g., 2-1 or 1-1) comes directly from the corresponding cell in the Poisson + Dixon-Coles bivariate score matrix.",
            "10. Insufficient Data & First-10-Minute Markets" to "First-half and second-half markets require verified half-time split records. First-10-minute markets require verified minute-level goal timestamps (available in OpenLigaDB match events). We never compute 10-minute odds from full-time scores alone; when minute data is missing, the app displays 'Insufficient data'.",
            "11. Prediction Tracking & Accuracy Reports" to "Saved predictions are stored as immutable pre-match snapshots with timestamps and model versions. Once verified final scores arrive, markets are settled automatically and aggregated into Brier scores and accuracy breakdowns. Postponed or cancelled matches are marked VOID.",
            "12. Controlled Model Recalibration" to "The owner dashboard can backtest candidate parameter adjustments against settled matches. Any candidate that worsens Brier score or accuracy on validation records is automatically rejected, and previous versions are preserved.",
            "13. Why Accumulator Selections Can Be Correlated" to "Selections from the same match (such as Home Win and Over 2.5 Goals) or the same team/league are statistically dependent. The Accumulator Builder detects correlation and refuses to present multiplied probabilities as reliable.",
            "14. Accounts, Community & Content Reporting" to "Visitors may browse public football schedules, team reports, and statistical predictions. Signing in via Google Sign-In unlocks cloud prediction tracking, 1-to-5 star app reviews (one active review per account), match discussions, private messages to the owner, and content reporting.",
            "15. Estimates Are Not Guarantees" to "Football matches involve high variance, tactical changes, and red cards. All probabilities are mathematical estimates, not guarantees or financial advice."
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Read Me & Methodology", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text("Complete guide to data sources, statistical formulas, and limitations", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
        }
        items(sections, key = { it.first }) { (title, body) ->
            LuxuryCard {
                Text(title, style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
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
    }
}

@Composable
fun PrivacyPolicyScreen() {
    val itemsList = listOf(
        "Information Collected" to "When you sign in with Google, we store your Firebase UID, display name, verified email address (private to your profile), optional profile photo URL, notification settings, and your submitted reviews, comments, feedback, and tracked predictions.",
        "Why Account Information Is Needed" to "Authentication prevents spam, enforces one active review per account, isolates private owner feedback, and lets you edit or delete your own content.",
        "Google OAuth Authentication" to "Sign-in is processed via Android Credential Manager and Firebase Authentication. Your Google password is never requested, transmitted to, or stored by this application.",
        "Public vs Private Data" to "Published reviews and discussion comments show only your display name and optional avatar—never your email address. Private feedback messages are visible solely to you and the application owner.",
        "Content Ownership, Editing & Deletion" to "You retain ownership of your reviews and comments and can edit or delete them at any time. You can permanently delete your account and personal profile from User Profile -> Delete Account.",
        "Data Retention & Security" to "Firestore Security Rules enforce strict per-user ownership, verified-email checks for owner administration, input size limits, and immutable audit logs.",
        "Third-Party Data Limitations" to "Football fixtures and scores are fetched from third-party public datasets (OpenLigaDB, TheSportsDB, Wikimedia). All probabilities are statistical estimates and carry no guarantee of match outcomes."
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite) }
        items(itemsList, key = { it.first }) { (h, b) ->
            LuxuryCard {
                Text(h, style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(6.dp))
                Text(b, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
            }
        }
    }
}

@Composable
fun TermsOfUseScreen() {
    val itemsList = listOf(
        "Informational Purpose Only" to "All Football Predictions provides football statistics and mathematical probability calculations for informational and analytical purposes only. Nothing in this app constitutes betting advice or guaranteed outcomes.",
        "Acceptable Community Conduct" to "Users must not post abusive, defamatory, automated spam, or malicious content. Reported content may be hidden or removed by the owner moderator, and abusive accounts may be restricted.",
        "Third-Party Data Attribution" to "Public fixture data is retrieved from OpenLigaDB, TheSportsDB free tier, and Wikimedia Open Search. Availability and accuracy depend on upstream public feeds.",
        "Owner Administration" to "Platform moderation, competition coverage, and model version approvals are restricted exclusively to the verified application owner."
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Terms of Use", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite) }
        items(itemsList, key = { it.first }) { (h, b) ->
            LuxuryCard {
                Text(h, style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(6.dp))
                Text(b, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
            }
        }
    }
}

@Composable
fun AdminLoginScreen(
    currentUser: FirebaseUser?,
    adminViewModel: AdminViewModel?,
    adminConfig: AdminConfig?,
    isPasscodeUnlocked: Boolean,
    authError: String?,
    onAuthSuccess: () -> Unit,
    onOpenAdminDashboard: () -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var signInErr by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = GoldBright)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Administrator Login", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Text("Owner-only verified access portal", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        }

        if (currentUser == null) {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Step 1: Verify Owner Google Account", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Only the authorised owner email (${AuthHelper.OWNER_ADMIN_EMAIL}) with a verified Google account may unlock the administrator dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IvoryWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    GoogleSignInButton(
                        label = "Sign in as Owner with Google",
                        onAuthSuccess = { signInErr = null; onAuthSuccess() },
                        onAuthError = { signInErr = it }
                    )
                    if (signInErr != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(signInErr!!, style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                    }
                }
            }
            return@LazyColumn
        }

        if (!AuthHelper.isVerifiedOwnerAdmin(currentUser)) {
            item {
                LuxuryCard {
                    Text("Access Denied", style = MaterialTheme.typography.titleLarge, color = CrimsonLoss)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Signed-in account (${currentUser.email ?: "unknown"}) is not authorised for owner administration. Database rules deny all administrative reads and writes for ordinary accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IvoryWhite
                    )
                }
            }
            return@LazyColumn
        }

        if (isPasscodeUnlocked) {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Owner Session Verified", style = MaterialTheme.typography.titleLarge, color = PitchEmerald)
                    Spacer(Modifier.height(6.dp))
                    Text("Verified email: ${currentUser.email}", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenAdminDashboard,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        modifier = Modifier.fillMaxWidth().testTag("enter_admin_dashboard_button")
                    ) {
                        Text("Open Administrator Dashboard")
                    }
                }
            }
            return@LazyColumn
        }

        val hasPasscode = !adminConfig?.passcodeHash.isNullOrBlank()
        item {
            LuxuryCard(highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldBright)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasPasscode) "Step 2: Enter Owner Passphrase" else "Step 2: Configure Initial Owner Passphrase",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasPasscode) "Your passphrase is verified against the salted SHA-256 hash stored privately in Firestore. 5 failed attempts trigger a 5-minute lockout."
                    else "First-run setup: choose a strong passphrase (min 8 characters). Only its salted SHA-256 hash is stored in owner-only Firestore config.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverGrey
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text("Owner Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_passcode_input")
                )
                if (!hasPasscode) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPasscode,
                        onValueChange = { confirmPasscode = it },
                        label = { Text("Confirm Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (authError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(authError, style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (hasPasscode) adminViewModel?.verifyOwnerPasscode(passcode)
                        else adminViewModel?.setInitialOwnerPasscode(passcode, confirmPasscode)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier.fillMaxWidth().testTag("admin_unlock_button")
                ) {
                    Text(if (hasPasscode) "Verify & Unlock Dashboard" else "Save Owner Passphrase & Unlock")
                }
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    currentUser: FirebaseUser?,
    adminViewModel: AdminViewModel,
    sources: List<DataSourceMetadata>,
    competitions: List<CompetitionInfo>,
    isPasscodeUnlocked: Boolean,
    usersState: UiState<List<UserProfile>>,
    predictionsState: UiState<List<TrackedPrediction>>,
    reviewsState: UiState<List<AppReview>>,
    commentsState: UiState<List<DiscussionComment>>,
    feedbackState: UiState<List<OwnerFeedback>>,
    reportsState: UiState<List<ContentReport>>,
    auditLogsState: UiState<List<AdminAuditLog>>,
    modelVersionsState: UiState<List<ModelVersionRecord>>,
    accuracySummary: AccuracyReportSummary,
    recalibrationProposal: RecalibrationProposal?,
    exportedCsv: String?,
    onBackToLogin: () -> Unit
) {
    if (!AuthHelper.isVerifiedOwnerAdmin(currentUser) || !isPasscodeUnlocked) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DisclaimerBanner("Owner verification required to access Administrator Dashboard.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBackToLogin) { Text("Go to Administrator Login") }
        }
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Sources", "Models", "Users", "Moderation", "Feedback", "Audit")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Owner Dashboard", style = MaterialTheme.typography.titleLarge, color = GoldBright)
            OutlinedButton(onClick = { adminViewModel.lockAdminSession(); onBackToLogin() }) {
                Text("Lock", color = GoldBright)
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            TabRow(selectedTabIndex = tab, containerColor = CharcoalElevated, contentColor = GoldBright, modifier = Modifier.width(640.dp)) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = tab == idx, onClick = { tab = idx }, text = { Text(title, maxLines = 1) })
                }
            }
        }

        val users = (usersState as? UiState.Success)?.data ?: emptyList()
        val preds = (predictionsState as? UiState.Success)?.data ?: emptyList()
        val reviews = (reviewsState as? UiState.Success)?.data ?: emptyList()
        val comments = (commentsState as? UiState.Success)?.data ?: emptyList()
        val feedbacks = (feedbackState as? UiState.Success)?.data ?: emptyList()
        val reports = (reportsState as? UiState.Success)?.data ?: emptyList()
        val logs = (auditLogsState as? UiState.Success)?.data ?: emptyList()
        val models = (modelVersionsState as? UiState.Success)?.data ?: emptyList()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LuxuryCard(modifier = Modifier.weight(1f)) {
                                Text("USERS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                                Text("${users.size}", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                            }
                            LuxuryCard(modifier = Modifier.weight(1f)) {
                                Text("TRACKED PREDS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                                Text("${preds.size}", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                            }
                            LuxuryCard(modifier = Modifier.weight(1f)) {
                                Text("REPORTS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                                Text("${reports.count { it.status == "PENDING" }}", style = MaterialTheme.typography.headlineSmall, color = CrimsonLoss)
                            }
                        }
                    }
                    item {
                        LuxuryCard {
                            Text("All-Time Accuracy Summary", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                            Text("Settled: ${accuracySummary.settledPredictions} • Correct: ${accuracySummary.correctPredictions} • Overall: ${accuracySummary.overallAccuracyPct}% • Brier: ${accuracySummary.brierScore}", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { adminViewModel.exportPredictionsCsv() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                            ) {
                                Text("Export Predictions CSV")
                            }
                            if (!exportedCsv.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(exportedCsv.take(800), style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                            }
                        }
                    }
                }
                1 -> {
                    item { SectionHeader("Football Data Sources & Freshness") }
                    items(sources, key = { it.sourceId }) { s ->
                        LuxuryCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.sourceName, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                                GoldBadge(s.health.label)
                            }
                            Text("Last sync: ${DateTimeFormatterUtil.formatLocalDateTime(s.lastRetrievedEpochMs)}", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                            Text(s.statusNote.ifBlank { s.coverageScope }, style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                        }
                    }
                    item { SectionHeader("Competition Coverage Controls") }
                    items(competitions, key = { it.id }) { comp ->
                        LuxuryCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comp.name, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                                    Text("${comp.country} • ${comp.gender.label}", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                                }
                                var enabled by remember(comp.id) { mutableStateOf(comp.enabled) }
                                Switch(checked = enabled, onCheckedChange = {
                                    enabled = it
                                    adminViewModel.toggleCompetition(comp.id, it)
                                })
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        LuxuryCard(highlighted = true) {
                            Text("Controlled Model Recalibration", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                            Text("Backtests parameter candidates against settled predictions. Rejects changes that perform worse.", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { adminViewModel.evaluateRecalibration() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Run Validation Backtest")
                            }
                            if (recalibrationProposal != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(recalibrationProposal.evaluationSummary, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                                if (recalibrationProposal.approvedImprovement) {
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { adminViewModel.approveRecalibration(recalibrationProposal) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PitchEmerald, contentColor = ObsidianBlack)
                                    ) {
                                        Text("Approve & Activate ${recalibrationProposal.candidateParams.versionTag}")
                                    }
                                }
                            }
                        }
                    }
                    item { SectionHeader("Archived Model Versions (${models.size})") }
                    items(models, key = { it.id }) { m ->
                        LuxuryCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m.versionTag, style = MaterialTheme.typography.titleSmall, color = GoldBright)
                                if (m.active) GoldBadge("ACTIVE", contentColor = PitchEmerald)
                            }
                            Text("Rho: ${m.dixonColesRho} • HomeAdv: ${m.homeAdvantage} • Brier: ${m.brierScore} • Acc: ${m.accuracyPct}%", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                        }
                    }
                }
                3 -> {
                    item { SectionHeader("Registered Users (${users.size})") }
                    items(users, key = { it.userId }) { u ->
                        LuxuryCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                                    Text(u.email, style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                                }
                                OutlinedButton(onClick = { adminViewModel.setUserRestricted(u.userId, !u.restricted) }) {
                                    Text(if (u.restricted) "Unrestrict" else "Restrict", color = if (u.restricted) PitchEmerald else CrimsonLoss)
                                }
                            }
                        }
                    }
                }
                4 -> {
                    item { SectionHeader("Reported Content (${reports.size})") }
                    items(reports, key = { it.id }) { rep ->
                        LuxuryCard {
                            Text("${rep.targetCollection}/${rep.targetId}", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            Text("Reason: ${rep.reason} • Status: ${rep.status}", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { adminViewModel.resolveReport(rep.id, "REVIEWED") }) { Text("Mark Reviewed") }
                                OutlinedButton(onClick = { adminViewModel.resolveReport(rep.id, "DISMISSED") }) { Text("Dismiss") }
                            }
                        }
                    }
                    item { SectionHeader("Moderate Reviews & Comments") }
                    items(reviews, key = { "mod_rev_${it.id}" }) { r ->
                        LuxuryCard {
                            Text("Review by ${r.authorName} (${r.rating}★) • Hidden=${r.hidden}", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            Text(r.reviewText, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { adminViewModel.moderateReview(r.id, !r.hidden, false) }) {
                                    Text(if (r.hidden) "Restore" else "Hide")
                                }
                                OutlinedButton(onClick = { adminViewModel.moderateReview(r.id, true, true) }) {
                                    Text("Delete", color = CrimsonLoss)
                                }
                            }
                        }
                    }
                    items(comments, key = { "mod_com_${it.id}" }) { c ->
                        LuxuryCard {
                            Text("Comment by ${c.authorName} • Hidden=${c.hidden}", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            Text(c.body, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { adminViewModel.moderateComment(c.id, !c.hidden, false) }) {
                                    Text(if (c.hidden) "Restore" else "Hide")
                                }
                                OutlinedButton(onClick = { adminViewModel.moderateComment(c.id, true, true) }) {
                                    Text("Delete", color = CrimsonLoss)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    item { SectionHeader("Private Feedback Inbox (${feedbacks.size})") }
                    items(feedbacks, key = { it.id }) { fb ->
                        var replyText by remember(fb.id) { mutableStateOf(fb.ownerReply ?: "") }
                        LuxuryCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${fb.senderName}: ${fb.subject}", style = MaterialTheme.typography.titleSmall, color = GoldBright, modifier = Modifier.weight(1f))
                                GoldBadge(fb.status)
                            }
                            Text(fb.message, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Owner Reply") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { adminViewModel.replyToFeedback(fb.id, replyText, "REPLIED") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                                ) { Text("Send Reply") }
                                OutlinedButton(onClick = { adminViewModel.replyToFeedback(fb.id, replyText, "RESOLVED") }) { Text("Resolve") }
                            }
                        }
                    }
                }
                else -> {
                    item { SectionHeader("Immutable Security & Moderation Audit Logs (${logs.size})") }
                    items(logs, key = { it.id }) { log ->
                        LuxuryCard {
                            Text("${log.action} • ${log.targetPath}", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            Text("${log.actorEmail} • ${log.createdAt?.toDate()?.let { DateTimeFormatterUtil.formatLocalDateTime(it.time) } ?: ""}", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                            if (log.details.isNotBlank()) {
                                Text(log.details, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}
