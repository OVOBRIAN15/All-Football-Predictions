package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AccuracyReportSummary
import com.example.domain.model.AppReview
import com.example.domain.model.DiscussionComment
import com.example.domain.model.FixtureItem
import com.example.domain.model.GenderCategory
import com.example.domain.model.SearchResultBundle
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.TeamInfo
import com.example.ui.components.DateTimeFormatterUtil
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.FixturePredictionCard
import com.example.ui.components.GoldBadge
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TeamCrest
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.IvoryWhite
import com.example.ui.theme.LocalLuxuryTokens
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PitchEmerald
import com.example.ui.theme.SilverGrey
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.FootballFeedUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MarketQuickFilter

@Composable
fun HomeDashboardScreen(
    feedState: FootballFeedUiState,
    accuracySummary: AccuracyReportSummary,
    recentReviews: List<AppReview>,
    recentComments: List<DiscussionComment>,
    isSignedIn: Boolean,
    viewModel: MainViewModel,
    onOpenSearch: () -> Unit,
    onOpenFixtures: () -> Unit,
    onOpenFixturePrediction: (FixtureItem) -> Unit,
    onOpenTeam: (String, String, String, String, GenderCategory, String?) -> Unit,
    onOpenAccuracy: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenSignIn: () -> Unit
) {
    val tokens = LocalLuxuryTokens.current
    val filtered = viewModel.filteredFixtures(feedState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Stadium Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_stadium_hero_1795451824152),
                    contentDescription = "All Football Predictions stadium arena",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.heroScrimGradient)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GoldBadge("DETERMINISTIC POISSON + DIXON-COLES ENGINE")
                        Text(
                            text = "All Football Predictions",
                            style = MaterialTheme.typography.headlineMedium,
                            color = IvoryWhite
                        )
                        Text(
                            text = "Updated ${DateTimeFormatterUtil.formatLocalDateTime(feedState.lastUpdatedEpochMs)} • ${feedState.competitions.count { it.enabled }} active competitions",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverGrey
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshFeeds(force = true) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("refresh_feeds_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh football feeds",
                            tint = GoldBright
                        )
                    }
                }
            }
        }

        // Quick Search Input Trigger
        item {
            LuxuryCard(
                modifier = Modifier.testTag("home_search_card"),
                onClick = onOpenSearch
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = GoldPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Search Football", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                        Text(
                            "Clubs, national teams, women's teams, leagues & fixtures",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverGrey
                        )
                    }
                    GoldBadge("OPEN")
                }
            }
        }

        // Summary KPI Row (Coverage, Accuracy, Live)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LuxuryCard(modifier = Modifier.weight(1f), onClick = onOpenFixtures) {
                    Text("7-DAY FIXTURES", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Spacer(Modifier.height(4.dp))
                    Text("${feedState.upcomingSevenDays.size}", style = MaterialTheme.typography.headlineMedium, color = GoldBright)
                    Text("Across ${feedState.competitions.count { it.enabled }} leagues", style = MaterialTheme.typography.bodySmall, color = SilverGrey)
                }
                LuxuryCard(modifier = Modifier.weight(1f), onClick = onOpenAccuracy) {
                    Text("ALL-TIME ACCURACY", style = MaterialTheme.typography.labelSmall, color = SilverGrey)
                    Spacer(Modifier.height(4.dp))
                    val accText = if (accuracySummary.settledPredictions > 0) "${accuracySummary.overallAccuracyPct}%" else "--"
                    Text(accText, style = MaterialTheme.typography.headlineMedium, color = PitchEmerald)
                    Text(
                        if (isSignedIn) "${accuracySummary.settledPredictions} settled" else "Sign in to track",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverGrey
                    )
                }
            }
        }

        if (!isSignedIn) {
            item {
                LuxuryCard(highlighted = true, onClick = onOpenSignIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Personal Tracking & Community", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                            Text(
                                "Sign in with Google to save prediction snapshots, track all-time accuracy, post reviews, and join discussions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryWhite
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        GoldBadge("SIGN IN")
                    }
                }
            }
        }

        // Filters Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filter Dashboard & Fixtures", style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangeFilter.entries.forEach { df ->
                        FilterChip(
                            selected = feedState.selectedDateFilter == df,
                            onClick = { viewModel.updateFilters(dateFilter = df) },
                            label = { Text(df.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldContainer,
                                selectedLabelColor = GoldBright
                            )
                        )
                    }
                    FilterChip(
                        selected = feedState.selectedGender == null,
                        onClick = { viewModel.updateFilters(clearGender = true) },
                        label = { Text("All Football") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                    )
                    FilterChip(
                        selected = feedState.selectedGender == GenderCategory.MEN,
                        onClick = { viewModel.updateFilters(gender = GenderCategory.MEN) },
                        label = { Text("Men's") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                    )
                    FilterChip(
                        selected = feedState.selectedGender == GenderCategory.WOMEN,
                        onClick = { viewModel.updateFilters(gender = GenderCategory.WOMEN) },
                        label = { Text("Women's") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarketQuickFilter.entries.forEach { mf ->
                        FilterChip(
                            selected = feedState.selectedMarketFilter == mf,
                            onClick = { viewModel.updateFilters(marketFilter = mf) },
                            label = { Text(mf.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                        )
                    }
                    val countries = listOf("All") + feedState.competitions.map { it.country }.distinct()
                    countries.forEach { country ->
                        FilterChip(
                            selected = feedState.selectedCountry == country,
                            onClick = { viewModel.updateFilters(country = country) },
                            label = { Text(country) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldContainer, selectedLabelColor = GoldBright)
                        )
                    }
                }
            }
        }

        if (feedState.isLoading) {
            item {
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Synchronising verified public football feeds...", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    }
                }
            }
        } else if (feedState.error != null) {
            item {
                DisclaimerBanner(feedState.error)
            }
        }

        // Top Prediction Opportunities
        if (feedState.topPredictions.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Top Prediction Opportunities",
                    subtitle = "Ranked by sample depth & model probability clarity",
                    actionLabel = "All Fixtures",
                    onActionClick = onOpenFixtures
                )
            }
            items(feedState.topPredictions.take(4), key = { "top_${it.fixture.matchId}" }) { report ->
                FixturePredictionCard(
                    fixture = report.fixture,
                    prediction = report,
                    onPredictClick = { onOpenFixturePrediction(report.fixture) },
                    onHomeTeamClick = {
                        onOpenTeam(report.fixture.homeTeamId, report.fixture.homeTeam, report.fixture.country, report.fixture.competition, report.fixture.gender, report.fixture.homeCrestUrl)
                    },
                    onAwayTeamClick = {
                        onOpenTeam(report.fixture.awayTeamId, report.fixture.awayTeam, report.fixture.country, report.fixture.competition, report.fixture.gender, report.fixture.awayCrestUrl)
                    }
                )
            }
        }

        // Filtered Upcoming Matches
        item {
            SectionHeader(
                title = "Matches (${feedState.selectedDateFilter.label})",
                subtitle = "${filtered.size} verified fixtures matching active filters"
            )
        }
        if (filtered.isEmpty() && !feedState.isLoading) {
            item {
                LuxuryCard {
                    Text(
                        text = "No fixtures match the selected filters in the current 7-day window. Try switching to Next 7 Days or All Football.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverGrey
                    )
                }
            }
        } else {
            items(filtered.take(10), key = { "home_fix_${it.matchId}" }) { fix ->
                FixturePredictionCard(
                    fixture = fix,
                    prediction = feedState.allPredictionsByMatchId[fix.matchId],
                    onPredictClick = { onOpenFixturePrediction(fix) },
                    onHomeTeamClick = {
                        onOpenTeam(fix.homeTeamId, fix.homeTeam, fix.country, fix.competition, fix.gender, fix.homeCrestUrl)
                    },
                    onAwayTeamClick = {
                        onOpenTeam(fix.awayTeamId, fix.awayTeam, fix.country, fix.competition, fix.gender, fix.awayCrestUrl)
                    }
                )
            }
        }

        // Recent Results
        if (feedState.recentFinished.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recent Verified Results",
                    subtitle = "Used for form modelling & automatic settlement"
                )
            }
            items(feedState.recentFinished.take(4), key = { "res_${it.matchId}" }) { fix ->
                FixturePredictionCard(
                    fixture = fix,
                    prediction = null,
                    onPredictClick = { onOpenFixturePrediction(fix) }
                )
            }
        }

        // Recent Community Reviews & Discussions
        item {
            SectionHeader(
                title = "Community & Reviews",
                subtitle = "Latest verified member ratings & match discussions",
                actionLabel = "Open",
                onActionClick = onOpenCommunity
            )
        }
        if (recentReviews.isEmpty() && recentComments.isEmpty()) {
            item {
                LuxuryCard(onClick = onOpenCommunity) {
                    Text(
                        text = if (isSignedIn) "No reviews or discussions yet. Be the first to share your football analysis."
                        else "Sign in with Google to view live member reviews and match discussions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverGrey
                    )
                }
            }
        } else {
            items(recentReviews.take(2), key = { "home_rev_${it.id}" }) { rev ->
                LuxuryCard(onClick = onOpenCommunity) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = GoldBright, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${rev.rating}/5 • ${rev.authorName}", style = MaterialTheme.typography.titleSmall, color = GoldBright)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(rev.reviewText, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            items(recentComments.take(2), key = { "home_com_${it.id}" }) { com ->
                LuxuryCard(onClick = onOpenCommunity) {
                    Text("${com.authorName} on ${com.targetLabel}", style = MaterialTheme.typography.labelMedium, color = GoldPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(com.body, style = MaterialTheme.typography.bodyMedium, color = IvoryWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            DisclaimerBanner("All predictions are mathematical probability estimates from open football datasets. They are not guarantees or financial advice.")
        }
    }
}

@Composable
fun SearchFootballScreen(
    query: String,
    suggestions: List<SearchSuggestion>,
    recentSearches: List<String>,
    bundle: SearchResultBundle?,
    isSearching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSearch: (String?) -> Unit,
    onClearSearch: () -> Unit,
    onClearRecent: () -> Unit,
    onSelectTeam: (TeamInfo) -> Unit,
    onSelectFixture: (FixtureItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Search Football", style = MaterialTheme.typography.headlineMedium, color = IvoryWhite)
            Text(
                "Search clubs, national teams, women's teams, leagues, and upcoming matches.",
                style = MaterialTheme.typography.bodySmall,
                color = SilverGrey
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("e.g. Arsenal, Bayern, Brazil, WSL...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_football_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = CharcoalElevated,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = SilverGrey)
                            }
                        }
                    }
                )
                Button(
                    onClick = { onSearch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("search_football_button")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }

        // Suggestions
        item {
            Text("Suggestions", style = MaterialTheme.typography.titleSmall, color = GoldBright)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { sug ->
                    FilterChip(
                        selected = false,
                        onClick = { onSearch(sug.query) },
                        label = { Text("${sug.label} (${sug.category})") }
                    )
                }
            }
        }

        // Recent searches
        if (recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Searches", style = MaterialTheme.typography.titleSmall, color = SilverGrey)
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                        modifier = Modifier.clickable(onClick = onClearRecent).padding(4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentSearches.forEach { recent ->
                        FilterChip(
                            selected = false,
                            onClick = { onSearch(recent) },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(recent) }
                        )
                    }
                }
            }
        }

        if (isSearching) {
            item {
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = GoldPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Querying football datasets and open web knowledge...", style = MaterialTheme.typography.bodyMedium, color = IvoryWhite)
                    }
                }
            }
        }

        if (error != null) {
            item {
                DisclaimerBanner(error)
            }
        }

        if (bundle != null && !isSearching) {
            bundle.searchNotice?.let { notice ->
                item {
                    DisclaimerBanner(notice)
                }
            }

            if (bundle.matchedTeams.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Verified Teams (${bundle.matchedTeams.size})",
                        subtitle = "Tap any team for full overview, form, splits, H2H & predictions"
                    )
                }
                items(bundle.matchedTeams, key = { "team_${it.id}_${it.name}" }) { team ->
                    LuxuryCard(
                        modifier = Modifier.testTag("search_team_${team.id}"),
                        onClick = { onSelectTeam(team) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamCrest(team.crestUrl, team.name, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(team.name, style = MaterialTheme.typography.titleMedium, color = IvoryWhite)
                                Text(
                                    "${team.competition} • ${team.country} • ${team.gender.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldBright
                                )
                                Text(
                                    "Source: ${team.sourceName} • ${DateTimeFormatterUtil.formatLocalTime(team.retrievedEpochMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SilverGrey
                                )
                            }
                            GoldBadge("ANALYSE")
                        }
                    }
                }
            }

            if (bundle.matchedFixtures.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Matching Fixtures (${bundle.matchedFixtures.size})",
                        subtitle = "Tap any fixture to generate validated match probabilities"
                    )
                }
                items(bundle.matchedFixtures, key = { "sfix_${it.matchId}" }) { fix ->
                    FixturePredictionCard(
                        fixture = fix,
                        prediction = null,
                        onPredictClick = { onSelectFixture(fix) }
                    )
                }
            }

            if (bundle.webSummaries.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Open Football Search Summaries",
                        subtitle = "Verified references (predictions are never calculated from snippets alone)"
                    )
                }
                items(bundle.webSummaries, key = { it.url }) { web ->
                    LuxuryCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(web.title, style = MaterialTheme.typography.titleSmall, color = GoldBright)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(web.snippet, style = MaterialTheme.typography.bodySmall, color = IvoryWhite)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${web.sourceName} • Retrieved ${DateTimeFormatterUtil.formatLocalTime(web.retrievedEpochMs)} • ${web.url}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverGrey
                        )
                    }
                }
            }
        }
    }
}
