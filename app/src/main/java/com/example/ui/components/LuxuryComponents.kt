package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.firebase.AuthHelper
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.MatchPredictionReport
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeFormatterUtil {
    fun formatLocalDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "Not synced yet"
        val sdf = SimpleDateFormat("EEE dd MMM, HH:mm z", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date(epochMs))
    }

    fun formatLocalTime(epochMs: Long): String {
        if (epochMs <= 0L) return "--:--"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date(epochMs))
    }

    fun formatLocalDate(epochMs: Long): String {
        if (epochMs <= 0L) return "--"
        val sdf = SimpleDateFormat("EEE dd MMM", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return sdf.format(Date(epochMs))
    }
}

@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = if (highlighted) GoldBorder else CharcoalBorder
    val container = if (highlighted) CharcoalElevated else CharcoalCard
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickMod),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            content = content
        )
    }
}

@Composable
fun GoldBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = GoldContainer,
    contentColor: Color = GoldBright
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TeamCrest(
    crestUrl: String?,
    teamName: String,
    size: Dp = 36.dp
) {
    if (!crestUrl.isNullOrBlank()) {
        AsyncImage(
            model = crestUrl,
            contentDescription = "$teamName crest",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(CharcoalElevated)
                .border(1.dp, GoldBorder, CircleShape)
                .padding(3.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        val initials = teamName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            .ifBlank { "FC" }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(GoldContainer)
                .border(1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = GoldBright,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProbabilityBar(
    homePct: Int,
    drawPct: Int,
    awayPct: Int,
    modifier: Modifier = Modifier
) {
    val total = (homePct + drawPct + awayPct).coerceAtLeast(1).toFloat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CharcoalElevated)
    ) {
        if (homePct > 0) {
            Box(
                modifier = Modifier
                    .weight(homePct / total)
                    .height(8.dp)
                    .background(GoldPrimary)
            )
        }
        if (drawPct > 0) {
            Box(
                modifier = Modifier
                    .weight(drawPct / total)
                    .height(8.dp)
                    .background(SilverGrey.copy(alpha = 0.6f))
            )
        }
        if (awayPct > 0) {
            Box(
                modifier = Modifier
                    .weight(awayPct / total)
                    .height(8.dp)
                    .background(PitchEmerald)
            )
        }
    }
}

@Composable
fun ConfidenceGauge(
    score: Int,
    size: Dp = 64.dp
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = CharcoalElevated,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = GoldPrimary,
                startAngle = 135f,
                sweepAngle = 270f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.labelMedium,
                color = GoldBright,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CONF",
                style = MaterialTheme.typography.labelSmall,
                color = SilverGrey
            )
        }
    }
}

@Composable
fun FixturePredictionCard(
    fixture: FixtureItem,
    prediction: MatchPredictionReport?,
    onPredictClick: () -> Unit,
    onHomeTeamClick: (() -> Unit)? = null,
    onAwayTeamClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LuxuryCard(
        modifier = modifier.testTag("fixture_card_${fixture.matchId}"),
        onClick = onPredictClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${fixture.competition} • ${fixture.country}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            val statusColor = when (fixture.status) {
                FixtureStatus.LIVE -> PitchEmerald
                FixtureStatus.POSTPONED, FixtureStatus.CANCELLED -> MaterialTheme.colorScheme.error
                else -> SilverGrey
            }
            GoldBadge(
                text = if (fixture.status == FixtureStatus.LIVE && fixture.liveMinute != null) {
                    "LIVE ${fixture.liveMinute}'"
                } else fixture.status.label,
                containerColor = CharcoalElevated,
                contentColor = statusColor
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Home team
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onHomeTeamClick != null) Modifier.clickable(onClick = onHomeTeamClick) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamCrest(fixture.homeCrestUrl, fixture.homeTeam, size = 40.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = fixture.homeTeam,
                    style = MaterialTheme.typography.titleSmall,
                    color = IvoryWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Center Kickoff or Score
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (fixture.homeScore != null && fixture.awayScore != null) {
                    Text(
                        text = "${fixture.homeScore} - ${fixture.awayScore}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = GoldBright
                    )
                } else {
                    Text(
                        text = DateTimeFormatterUtil.formatLocalTime(fixture.kickoffEpochMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldBright
                    )
                }
                Text(
                    text = DateTimeFormatterUtil.formatLocalDate(fixture.kickoffEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverGrey
                )
            }

            // Away team
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onAwayTeamClick != null) Modifier.clickable(onClick = onAwayTeamClick) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamCrest(fixture.awayCrestUrl, fixture.awayTeam, size = 40.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = fixture.awayTeam,
                    style = MaterialTheme.typography.titleSmall,
                    color = IvoryWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (prediction != null) {
            Spacer(Modifier.height(12.dp))
            ProbabilityBar(
                homePct = prediction.probHomeWin,
                drawPct = prediction.probDraw,
                awayPct = prediction.probAwayWin
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1: ${prediction.probHomeWin}%", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                Text("X: ${prediction.probDraw}%", style = MaterialTheme.typography.labelMedium, color = SilverGrey)
                Text("2: ${prediction.probAwayWin}%", style = MaterialTheme.typography.labelMedium, color = PitchEmerald)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model xG: ${prediction.modelEstimatedHomeXg} - ${prediction.modelEstimatedAwayXg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverGrey
                )
                Text(
                    text = "${fixture.sourceName} • Conf ${prediction.confidenceScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Source: ${fixture.sourceName} • Tap for probabilistic breakdown",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }
    }
}

@Composable
fun GoogleSignInButton(
    label: String = "Continue with Google",
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            if (context !is Activity) {
                onAuthError("Sign-in requires an active screen context.")
                return@Button
            }
            isLoading = true
            AuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    onAuthError(msg)
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                }
            )
        },
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("google_sign_in_button"),
        colors = ButtonDefaults.buttonColors(
            containerColor = GoldPrimary,
            contentColor = ObsidianBlack
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ObsidianBlack,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text = label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = IvoryWhite
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverGrey
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = GoldPrimary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun DisclaimerBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = GoldContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, GoldBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = IvoryWhite
            )
        }
    }
}
