package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AccumulatorEvaluation
import com.example.domain.model.AccumulatorSelection
import com.example.domain.model.FixtureItem
import com.example.domain.model.MatchPredictionReport
import com.example.domain.model.PeriodMarketSuite
import com.example.ui.components.ConfidenceGauge
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.GoldBadge
import com.example.ui.components.LuxuryCard
import com.example.ui.components.ProbabilityBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.TeamCrest
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.LocalLuxuryTokens
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey

@Composable
fun FootballPredictionsScreen(
    report: MatchPredictionReport?,
    allUpcomingPredictions: List<MatchPredictionReport>,
    onSelectPrediction: (FixtureItem) -> Unit,
    onSaveSnapshot: (MatchPredictionReport) -> Unit,
    onAddToAccumulator: (AccumulatorSelection) -> Unit,
    onOpenCorrectScores: () -> Unit
) {
    val tokens = LocalLuxuryTokens.current
    var selectedPeriodTab by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (report == null) {
            item {
                LuxuryCard {
                    Text("Football Predictions", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Select any upcoming fixture from the Home Dashboard, Upcoming Fixtures, or Search Football to compute deterministic Poisson + Dixon-Coles market probabilities.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverGrey
                    )
                }
            }
            items(allUpcomingPredictions.take(10), key = { "pick_${it.fixture.matchId}" }) { itemReport ->
                LuxuryCard(onClick = { onSelectPrediction(itemReport.fixture) }) {
                    Text("${itemReport.fixture.homeTeam} vs ${itemReport.fixture.awayTeam}", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    Text("${itemReport.fixture.competition} • 1: ${itemReport.probHomeWin}%  X: ${itemReport.probDraw}%  2: ${itemReport.probAwayWin}%", style = MaterialTheme.typography.bodySmall, color = GoldBright)
                }
            }
            return@LazyColumn
        }

        val fix = report.fixture
        // Tactical Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_tactical_banner_1795451825896),
                    contentDescription = "Tactical pitch model header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.heroScrimGradient)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoldBadge("${fix.competition} • ${report.modelVersion}")
                            ConfidenceGauge(report.confidenceScore, size = 56.dp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamCrest(fix.homeCrestUrl, fix.homeTeam, size = 46.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(fix.homeTeam, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("VS", style = MaterialTheme.typography.titleLarge, color = GoldBright)
                                Text(DateTimeFormatterUtil.formatLocalDateTime(fix.kickoffEpochMs), style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                TeamCrest(fix.awayCrestUrl, fix.awayTeam, size = 46.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(fix.awayTeam, style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ProbabilityBar(report.probHomeWin, report.probDraw, report.probAwayWin)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Home: ${report.probHomeWin}%", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                            Text("Draw: ${report.probDraw}%", style = MaterialTheme.typography.labelMedium, color = SilverGrey)
                            Text("Away: ${report.probAwayWin}%", style = MaterialTheme.typography.labelMedium, color = PitchEmerald)
                        }
                    }
                }
            }
        }

        // Actions Row: Save Immutable Snapshot + Correct Scores + Accumulator
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveSnapshot(report) },
                    modifier = Modifier.weight(1f).testTag("save_prediction_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Track Prediction")
                }
                OutlinedButton(
                    onClick = onOpenCorrectScores,
                    modifier = Modifier.weight(1f).testTag("open_correct_scores_button")
                ) {
                    Text("Correct Scores", color = GoldBright)
                }
            }
        }

        // xG Distinction & Data Quality Card
        item {
            LuxuryCard {
                Text("Expected Goals (xG) & Data Quality", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Model-Estimated xG", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${report.modelEstimatedHomeXg}  -  ${report.modelEstimatedAwayXg}", style = MaterialTheme.typography.titleLarge, color = IvoryWhite)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Official Provider xG", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        val offText = if (report.officialProviderHomeXg != null && report.officialProviderAwayXg != null) {
                            "${report.officialProviderHomeXg} - ${report.officialProviderAwayXg}"
                        } else "Unavailable"
                        Text(offText, style = MaterialTheme.typography.titleMedium, color = AmberDraw)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = CharcoalBorder)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sample size: ${report.historicalSampleSize} team matches, ${report.h2hSampleSize} H2H • Sources: ${report.dataSources.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverGrey
                )
                if (report.reliabilityWarnings.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    report.reliabilityWarnings.forEach { warn ->
                        Text("• $warn", style = MaterialTheme.typography.bodySmall, color = AmberDraw)
                    }
                }
            }
        }

        // Period Selector Tabs
        item {
            val periods = listOf("Full-Time", "1st Half", "2nd Half", "First 10'")
            TabRow(
                selectedTabIndex = selectedPeriodTab,
                containerColor = CharcoalElevated,
                contentColor = GoldBright
            ) {
                periods.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedPeriodTab == idx,
                        onClick = { selectedPeriodTab = idx },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }
        }

        val activeSuite: PeriodMarketSuite = when (selectedPeriodTab) {
            0 -> report.fullTimeSuite
            1 -> report.firstHalfSuite
            2 -> report.secondHalfSuite
            else -> report.firstTenMinutesSuite
        }

        if (!activeSuite.available) {
            item {
                DisclaimerBanner(activeSuite.unavailableReason ?: "Insufficient data for this match period.")
            }
        } else {
            items(activeSuite.groups, key = { "${activeSuite.periodTitle}_${it.marketName}" }) { group ->
                LuxuryCard {
                    Text(group.marketName, style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    Spacer(Modifier.height(8.dp))
                    group.outcomes.forEach { out ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(out.label, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                                    Text("${out.probabilityPct}%", style = MaterialTheme.typography.labelMedium, color = GoldBright, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (out.probabilityPct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp),
                                    color = GoldPrimary,
                                    trackColor = CharcoalElevated
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    onAddToAccumulator(
                                        AccumulatorSelection(
                                            fixture = fix,
                                            marketName = group.marketName,
                                            selectionLabel = out.label,
                                            probability = out.rawProbability,
                                            probabilityPct = out.probabilityPct
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add to accumulator", tint = GoldBright)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CorrectScoresScreen(
    report: MatchPredictionReport?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Correct-Score Probabilities", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "Derived directly from the bivariate Poisson + Dixon-Coles goal matrix",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        if (report == null) {
            item {
                DisclaimerBanner("Select a fixture from Fixtures or Dashboard first to view its correct-score probability distribution.")
            }
            return@LazyColumn
        }

        item {
            LuxuryCard(highlighted = true) {
                Text("${report.fixture.homeTeam} vs ${report.fixture.awayTeam}", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Text("Model xG: ${report.modelEstimatedHomeXg} vs ${report.modelEstimatedAwayXg}", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
            }
        }

        item {
            SectionHeader("Full-Time Top Correct Scores")
        }
        items(report.fullTimeCorrectScores, key = { "ft_cs_${it.scoreLine}" }) { cs ->
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cs.scoreLine, style = MaterialTheme.typography.titleLarge, color = GoldBright)
                    Text("${cs.probabilityPct}%", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (cs.probabilityPct.toFloat() / 20f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = GoldPrimary,
                    trackColor = CharcoalElevated
                )
            }
        }

        item {
            SectionHeader("Half-Time Correct Scores")
        }
        if (!report.firstHalfSuite.available) {
            item {
                DisclaimerBanner(report.firstHalfSuite.unavailableReason ?: "Insufficient data for half-time correct scores.")
            }
        } else {
            items(report.firstHalfSuite.topCorrectScores, key = { "ht_cs_${it.scoreLine}" }) { cs ->
                LuxuryCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1st Half ${cs.scoreLine}", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                        Text("${cs.probabilityPct}%", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                    }
                }
            }
        }
    }
}

@Composable
fun AccumulatorScreen(
    evaluation: AccumulatorEvaluation,
    upcomingPredictions: List<MatchPredictionReport>,
    onAddSelection: (AccumulatorSelection) -> Unit,
    onRemoveSelection: (String) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Advanced Accumulator", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Text("Multi-selection correlation & variance analyser", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
                if (evaluation.selections.isNotEmpty()) {
                    OutlinedButton(onClick = onClear) {
                        Text("Clear", color = GoldBright)
                    }
                }
            }
        }

        item {
            LuxuryCard(highlighted = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SELECTIONS", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        Text("${evaluation.selections.size} Legs", style = MaterialTheme.typography.headlineSmall, color = IvoryWhite)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("COMBINED ESTIMATE", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                        val combinedText = when {
                            evaluation.selections.isEmpty() -> "--"
                            !evaluation.independenceJustified -> "Correlated (Invalid)"
                            else -> "${evaluation.naiveCombinedProbPct}%"
                        }
                        Text(
                            combinedText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (evaluation.independenceJustified) GoldBright else AmberDraw
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(evaluation.riskNotice, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
            }
        }

        if (evaluation.correlationWarnings.isNotEmpty()) {
            item {
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberDraw)
                        Spacer(Modifier.width(8.dp))
                        Text("Correlation Warnings", style = MaterialTheme.typography.titleMedium, color = AmberDraw)
                    }
                    Spacer(Modifier.height(6.dp))
                    evaluation.correlationWarnings.forEach { w ->
                        Text("• $w", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        if (evaluation.selections.isNotEmpty()) {
            item { SectionHeader("Selected Legs") }
            items(evaluation.selections, key = { it.selectionKey }) { sel ->
                LuxuryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${sel.fixture.homeTeam} vs ${sel.fixture.awayTeam}", style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                            Text("${sel.marketName}: ${sel.selectionLabel} (${sel.probabilityPct}%)", style = MaterialTheme.typography.bodySmall, color = GoldBright)
                        }
                        IconButton(onClick = { onRemoveSelection(sel.selectionKey) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove leg", tint = SilverGrey)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Quick-Add Fixture Markets", "Tap an outcome to add to your accumulator slip")
        }
        items(upcomingPredictions.take(8), key = { "acc_${it.fixture.matchId}" }) { report ->
            val fix = report.fixture
            LuxuryCard {
                Text("${fix.homeTeam} vs ${fix.awayTeam}", style = MaterialTheme.typography.titleSmall, color = IvoryWhite)
                Text(fix.competition, style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onAddSelection(AccumulatorSelection(fix, "1X2", "Home Win (${fix.homeTeam})", report.probHomeWin / 100.0, report.probHomeWin))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1: ${report.probHomeWin}%", color = GoldBright)
                    }
                    OutlinedButton(
                        onClick = {
                            onAddSelection(AccumulatorSelection(fix, "1X2", "Draw", report.probDraw / 100.0, report.probDraw))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("X: ${report.probDraw}%", color = IvoryWhite)
                    }
                    OutlinedButton(
                        onClick = {
                            onAddSelection(AccumulatorSelection(fix, "1X2", "Away Win (${fix.awayTeam})", report.probAwayWin / 100.0, report.probAwayWin))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("2: ${report.probAwayWin}%", color = PitchEmerald)
                    }
                }
            }
        }
    }
}
