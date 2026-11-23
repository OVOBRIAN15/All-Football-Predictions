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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FixtureItem
import com.example.domain.model.GenderCategory
import com.example.domain.model.SourceHealth
import com.example.domain.model.TeamAnalysisReport
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.FixturePredictionCard
import com.example.ui.components.GoldBadge
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TeamCrest
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey
import com.example.ui.viewmodel.FootballFeedUiState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun UpcomingFixturesScreen(
    feedState: FootballFeedUiState,
    viewModel: MainViewModel,
    onSelectFixture: (FixtureItem) -> Unit,
    onOpenTeam: (String, String, String, String, GenderCategory, String?) -> Unit
) {
    val fixtures = viewModel.filteredFixtures(feedState)

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
                    Text("Upcoming Fixtures (7 Days)", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Text(
                        "Kickoff times shown in your local device timezone",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverGrey
                    )
                }
                IconButton(onClick = { viewModel.refreshFeeds(force = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh fixtures", tint = GoldBright)
                }
            }
        }

        item {
            val comps = listOf("All") + feedState.competitions.filter { it.enabled }.map { it.name }.distinct()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                comps.forEach { comp ->
                    FilterChip(
                        selected = feedState.selectedCompetition == comp,
                        onClick = { viewModel.updateFilters(competition = comp) },
                        label = { Text(comp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                    )
                }
            }
        }

        if (feedState.isLoading) {
            item {
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading verified 7-day schedules...", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    }
                }
            }
        } else if (fixtures.isEmpty()) {
            item {
                DisclaimerBanner("No scheduled fixtures found in the next 7 days for the active competition filter.")
            }
        } else {
            items(fixtures, key = { "up_${it.matchId}" }) { fix ->
                FixturePredictionCard(
                    fixture = fix,
                    prediction = feedState.allPredictionsByMatchId[fix.matchId],
                    onPredictClick = { onSelectFixture(fix) },
                    onHomeTeamClick = {
                        onOpenTeam(fix.homeTeamId, fix.homeTeam, fix.country, fix.competition, fix.gender, fix.homeCrestUrl)
                    },
                    onAwayTeamClick = {
                        onOpenTeam(fix.awayTeamId, fix.awayTeam, fix.country, fix.competition, fix.gender, fix.awayCrestUrl)
                    }
                )
            }
        }
    }
}

@Composable
fun LiveMatchCentreScreen(
    feedState: FootballFeedUiState,
    onRefresh: () -> Unit,
    onSelectFixture: (FixtureItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Match Centre", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Text(
                        "Displays verified live scores only — zero fictional scores",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverGrey
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.testTag("live_refresh_button")) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh live scores", tint = GoldBright)
                }
            }
        }

        if (feedState.liveMatches.isEmpty()) {
            item {
                LuxuryCard(highlighted = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("No Live Matches Currently Active", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Connected free public feeds (OpenLigaDB & TheSportsDB free tier) report no in-progress matches right now. Live score coverage is limited to supported competitions when matches are underway.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryWhite
                            )
                        }
                    }
                }
            }
        } else {
            items(feedState.liveMatches, key = { "live_${it.matchId}" }) { fix ->
                FixturePredictionCard(
                    fixture = fix,
                    prediction = feedState.allPredictionsByMatchId[fix.matchId],
                    onPredictClick = { onSelectFixture(fix) }
                )
            }
        }

        item {
            DisclaimerBanner("Last checked: ${DateTimeFormatterUtil.formatLocalDateTime(feedState.lastUpdatedEpochMs)}. We never simulate or fabricate live match events.")
        }
    }
}

@Composable
fun FootballStatisticsScreen(
    feedState: FootballFeedUiState
) {
    val standingsEntries = feedState.standingsByCompetition.entries.toList()
    var selectedCompId by remember(standingsEntries) {
        mutableStateOf(standingsEntries.firstOrNull()?.key ?: "")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Football Statistics & Sources", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "League tables, verified recent results, and public data source licensing.",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        if (standingsEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    standingsEntries.forEach { (compId, _) ->
                        val label = feedState.competitions.firstOrNull { it.id == compId }?.name ?: compId
                        FilterChip(
                            selected = selectedCompId == compId,
                            onClick = { selectedCompId = compId },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                        )
                    }
                }
            }

            val rows = feedState.standingsByCompetition[selectedCompId] ?: emptyList()
            if (rows.isNotEmpty()) {
                item {
                    LuxuryCard {
                        Text("Competition Standings", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("#", style = MaterialTheme.typography.labelSmall, color = SilverGrey, modifier = Modifier.width(24.dp))
                            Text("Team", style = MaterialTheme.typography.labelSmall, color = SilverGrey, modifier = Modifier.weight(1f))
                            Text("P", style = MaterialTheme.typography.labelSmall, color = SilverGrey, modifier = Modifier.width(28.dp))
                            Text("GD", style = MaterialTheme.typography.labelSmall, color = SilverGrey, modifier = Modifier.width(32.dp))
                            Text("Pts", style = MaterialTheme.typography.labelSmall, color = GoldBright, modifier = Modifier.width(34.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = CharcoalBorder)
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${row.position}", style = MaterialTheme.typography.labelMedium, color = SilverGrey, modifier = Modifier.width(24.dp))
                                Text(row.teamName, style = MaterialTheme.typography.bodySmall, color = IvoryWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${row.played}", style = MaterialTheme.typography.labelMedium, color = SilverGrey, modifier = Modifier.width(28.dp))
                                Text("${row.goalDifference}", style = MaterialTheme.typography.labelMedium, color = SilverGrey, modifier = Modifier.width(32.dp))
                                Text("${row.points}", style = MaterialTheme.typography.labelMedium, color = GoldBright, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                            }
                            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        } else {
            item {
                DisclaimerBanner("Standings tables load from connected OpenLigaDB and TheSportsDB public feeds when available.")
            }
        }

        item {
            SectionHeader(
                title = "Data Source Coverage & Licensing",
                subtitle = "Free, no-key public football feeds with explicit attribution"
            )
        }

        items(feedState.sourceMetadata, key = { it.sourceId }) { src ->
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(src.sourceName, style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    GoldBadge(
                        text = src.health.label,
                        contentColor = if (src.health == SourceHealth.ACTIVE) PitchEmerald else GoldBright
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Licence: ${src.license}", style = MaterialTheme.typography.bodySmall, color = GoldBright)
                Text("Commercial note: ${src.commercialUseNote}", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                Text("Coverage: ${src.coverageScope}", style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                Text("Update cadence: ${src.updateFrequency} • API Key Required: ${if (src.requiresApiKey) "Yes" else "No"}", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                Text("Last checked: ${DateTimeFormatterUtil.formatLocalDateTime(src.lastRetrievedEpochMs)}", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
            }
        }
    }
}

@Composable
fun TeamAnalysisScreen(
    report: TeamAnalysisReport?,
    isLoading: Boolean,
    onSelectFixture: (FixtureItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isLoading) {
            item {
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Analysing team history, home/away splits & head-to-head records...", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    }
                }
            }
            return@LazyColumn
        }

        if (report == null) {
            item {
                LuxuryCard {
                    Text("Team Analysis", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Search for any club, national team, or women's team in Search Football, or tap a team crest on any fixture card to view full statistical splits, last 5 results, H2H history, and upcoming match predictions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverGrey
                    )
                }
            }
            return@LazyColumn
        }

        val team = report.team
        item {
            LuxuryCard(highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(team.crestUrl, team.name, size = 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(team.name, style = MaterialTheme.typography.headlineSmall, color = IvoryWhite)
                        Text("${team.country} • ${team.competition}", style = MaterialTheme.typography.bodyMedium, color = GoldBright)
                        Text(team.gender.label, style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    }
                }
                if (team.overview.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(team.overview, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Retrieved ${DateTimeFormatterUtil.formatLocalDateTime(report.retrievedEpochMs)} • Source: ${team.sourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverGrey
                )
            }
        }

        // Home vs Away Performance & Totals
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("HOME SPLIT", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                    Spacer(Modifier.height(4.dp))
                    Text("${report.homePerformance.wins}W-${report.homePerformance.draws}D-${report.homePerformance.losses}L", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    Text("GF ${report.homePerformance.goalsScored} • GA ${report.homePerformance.goalsConceded}", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
                LuxuryCard(modifier = Modifier.weight(1f)) {
                    Text("AWAY SPLIT", style = MaterialTheme.typography.labelSmall, color = PitchEmerald)
                    Spacer(Modifier.height(4.dp))
                    Text("${report.awayPerformance.wins}W-${report.awayPerformance.draws}D-${report.awayPerformance.losses}L", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                    Text("GF ${report.awayPerformance.goalsScored} • GA ${report.awayPerformance.goalsConceded}", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        }

        item {
            LuxuryCard {
                Text("Aggregate Goals (Verified Sample)", style = MaterialTheme.typography.titleSmall, color = GoldBright)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Goals Scored: ${report.totalGoalsScored}  •  Goals Conceded: ${report.totalGoalsConceded}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IvoryWhite
                )
            }
        }

        if (report.lastFiveMatches.isNotEmpty()) {
            item {
                SectionHeader("Last 5 Match Results", "Verified historical scorelines")
            }
            items(report.lastFiveMatches, key = { "l5_${it.matchId}" }) { m ->
                LuxuryCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${m.homeTeam} ${m.homeGoals} - ${m.awayGoals} ${m.awayTeam}", style = MaterialTheme.typography.titleSmall, color = IvoryWhite, modifier = Modifier.weight(1f))
                        Text(DateTimeFormatterUtil.formatLocalDate(m.dateEpochMs), style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    }
                    Text("${m.competition} • Source: ${m.sourceName}", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        }

        if (report.headToHeadRecords.isNotEmpty()) {
            item {
                SectionHeader("Head-to-Head History (Last ${report.headToHeadRecords.size})", "Direct meetings vs upcoming opponent")
            }
            items(report.headToHeadRecords, key = { "h2h_${it.matchId}" }) { m ->
                LuxuryCard {
                    Text("${m.homeTeam} ${m.homeGoals} - ${m.awayGoals} ${m.awayTeam}", style = MaterialTheme.typography.titleSmall, color = GoldBright)
                    Text("${DateTimeFormatterUtil.formatLocalDate(m.dateEpochMs)} • ${m.competition}", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
            }
        }

        if (report.upcomingFixtures.isNotEmpty()) {
            item {
                SectionHeader("Upcoming Fixtures", "Tap a fixture to calculate full match probabilities")
            }
            items(report.upcomingFixtures, key = { "tfix_${it.matchId}" }) { fix ->
                FixturePredictionCard(
                    fixture = fix,
                    prediction = null,
                    onPredictClick = { onSelectFixture(fix) }
                )
            }
        }

        if (report.dataReliabilityNotes.isNotEmpty()) {
            item {
                DisclaimerBanner(report.dataReliabilityNotes.joinToString("\n"))
            }
        }
    }
}
