package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.TrackedPrediction
import com.example.domain.model.UiState
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.GoldBadge
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey

@Composable
fun PredictionHistoryScreen(
    isSignedIn: Boolean,
    predictionsState: UiState<List<TrackedPrediction>>,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Prediction History", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "Immutable pre-match probability snapshots & automatic result settlements",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        if (!isSignedIn) {
            item {
                LuxuryCard(highlighted = true) {
                    Text("Sign In to View & Track Predictions", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saved prediction snapshots are securely stored per account and automatically settled when verified scores arrive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IvoryWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    GoogleSignInButton(onAuthSuccess = onAuthSuccess, onAuthError = onAuthError)
                }
            }
            return@LazyColumn
        }

        when (predictionsState) {
            is UiState.Loading -> {
                item {
                    LuxuryCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Loading immutable prediction records...", color = IvoryWhite)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item { DisclaimerBanner(predictionsState.message) }
            }
            is UiState.Success -> {
                val list = predictionsState.data
                if (list.isEmpty()) {
                    item {
                        LuxuryCard {
                            Text("No Saved Predictions Yet", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Open any upcoming fixture and tap 'Track Prediction' to save an immutable timestamped snapshot before kickoff.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverGrey
                            )
                        }
                    }
                } else {
                    items(list, key = { it.id }) { pred ->
                        LuxuryCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${pred.competition} • ${pred.modelVersion}", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                                val badgeColor = when (pred.status) {
                                    "SETTLED" -> if (pred.outcomeCorrect == true) PitchEmerald else CrimsonLoss
                                    "VOID" -> AmberDraw
                                    else -> SilverGrey
                                }
                                val badgeLabel = when (pred.status) {
                                    "SETTLED" -> if (pred.outcomeCorrect == true) "CORRECT" else "INCORRECT"
                                    "VOID" -> "VOID / POSTPONED"
                                    else -> "PENDING"
                                }
                                GoldBadge(badgeLabel, contentColor = badgeColor)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${pred.homeTeam} vs ${pred.awayTeam}", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                            Text(
                                "Kickoff: ${DateTimeFormatterUtil.formatLocalDateTime(pred.kickoffEpochMs)} • Snapshot: ${pred.createdAt?.toDate()?.let { DateTimeFormatterUtil.formatLocalDateTime(it.time) } ?: "Pending server ts"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverGrey
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "1X2: ${pred.probHomeWin}% / ${pred.probDraw}% / ${pred.probAwayWin}%  •  xG: ${pred.homeXg} - ${pred.awayXg}  •  Top CS: ${pred.topCorrectScore} (${pred.topCorrectScoreProb}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = IvoryWhite
                            )
                            if (pred.status == "SETTLED" && pred.actualHomeGoals != null && pred.actualAwayGoals != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Verified Final Score: ${pred.actualHomeGoals} - ${pred.actualAwayGoals} (${pred.settledResult1X2})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pred.outcomeCorrect == true) PitchEmerald else CrimsonLoss,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (!pred.qualityWarning.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Warning: ${pred.qualityWarning}", style = MaterialTheme.typography.labelSmall, color = AmberDraw)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccuracyReportsScreen(
    summary: AccuracyReportSummary,
    isSignedIn: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Accuracy Reports", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "All-time calibration & outcome verification across settled matches",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        if (!isSignedIn) {
            item {
                DisclaimerBanner("Sign in with Google and track fixture predictions to build your personal all-time accuracy dashboard.")
            }
        }

        // KPI Summary Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LuxuryCard(modifier = Modifier.weight(1f), highlighted = true) {
                    Text("OVERALL ACCURACY", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Text(
                        if (summary.settledPredictions > 0) "${summary.overallAccuracyPct}%" else "--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PitchEmerald
                    )
                    Text("${summary.correctPredictions} / ${summary.settledPredictions} settled", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("BRIER SCORE", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Text(
                        if (summary.settledPredictions > 0) "${summary.brierScore}" else "--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = GoldBright
                    )
                    Text("Lower is better calibrated", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        }

        item {
            LuxuryCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${summary.totalPredictions}", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    }
                    Column {
                        Text("Settled", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${summary.settledPredictions}", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    }
                    Column {
                        Text("Correct", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${summary.correctPredictions}", style = MaterialTheme.typography.titleMedium, color = PitchEmerald)
                    }
                    Column {
                        Text("Incorrect", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${summary.incorrectPredictions}", style = MaterialTheme.typography.titleMedium, color = CrimsonLoss)
                    }
                    Column {
                        Text("Void", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${summary.voidOrPostponedCount}", style = MaterialTheme.typography.titleMedium, color = AmberDraw)
                    }
                }
            }
        }

        if (summary.accuracyByMarket.isNotEmpty()) {
            item {
                SectionHeader("Accuracy by Market & Chart")
                Spacer(Modifier.height(6.dp))
                LuxuryCard {
                    val entries = summary.accuracyByMarket.entries.toList()
                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        val barWidth = (size.width / (entries.size * 2f)).coerceAtLeast(24f)
                        entries.forEachIndexed { idx, (_, pair) ->
                            val pct = if (pair.second > 0) pair.first.toFloat() / pair.second else 0f
                            val barH = size.height * pct.coerceIn(0f, 1f)
                            val x = idx * (size.width / entries.size) + (size.width / entries.size - barWidth) / 2f
                            drawRect(
                                color = CharcoalElevated,
                                topLeft = Offset(x, 0f),
                                size = Size(barWidth, size.height)
                            )
                            drawRect(
                                color = GoldPrimary,
                                topLeft = Offset(x, size.height - barH),
                                size = Size(barWidth, barH)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    entries.forEach { (label, pair) ->
                        val pct = if (pair.second > 0) (pair.first * 100) / pair.second else 0
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                            Text("${pair.first}/${pair.second} ($pct%)", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                        }
                    }
                }
            }
        }

        if (summary.accuracyByCompetition.isNotEmpty()) {
            item { SectionHeader("Accuracy by Competition") }
            items(summary.accuracyByCompetition.entries.toList(), key = { "comp_${it.key}" }) { (comp, pair) ->
                val pct = if (pair.second > 0) (pair.first * 100) / pair.second else 0
                LuxuryCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(comp, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                        Text("${pair.first}/${pair.second} ($pct%)", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (pair.second > 0) pair.first.toFloat() / pair.second else 0f },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = PitchEmerald,
                        trackColor = CharcoalElevated
                    )
                }
            }
        }

        if (summary.accuracyByConfidenceRange.isNotEmpty()) {
            item { SectionHeader("Accuracy by Confidence Range") }
            items(summary.accuracyByConfidenceRange.entries.toList(), key = { "conf_${it.key}" }) { (bucket, pair) ->
                val pct = if (pair.second > 0) (pair.first * 100) / pair.second else 0
                LuxuryCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bucket, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                        Text("${pair.first}/${pair.second} ($pct%)", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                    }
                }
            }
        }

        item {
            DisclaimerBanner("Prediction accuracy measures statistical outcome calibration against final results. It does not represent betting profitability or guarantee future performance.")
        }
    }
}
