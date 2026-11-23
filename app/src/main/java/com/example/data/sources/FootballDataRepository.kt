package com.example.data.sources

import com.example.domain.engine.MarketCalculator
import com.example.domain.engine.PoissonModel
import com.example.domain.model.CompetitionInfo
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchPredictionReport
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.ModelParameters
import com.example.domain.model.SearchResultBundle
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SourceHealth
import com.example.domain.model.StandingRow
import com.example.domain.model.TeamAnalysisReport
import com.example.domain.model.TeamInfo
import com.example.domain.model.TeamPerformanceSplit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import java.util.concurrent.TimeUnit

class FootballDataRepository(
    private val openLigaDb: OpenLigaDbAdapter = OpenLigaDbAdapter(),
    private val theSportsDb: TheSportsDbAdapter = TheSportsDbAdapter(),
    private val wikipediaSearch: WikipediaSearchAdapter = WikipediaSearchAdapter()
) {
    private val cachedFixtures = mutableListOf<FixtureItem>()
    private val cachedResults = mutableListOf<MatchResultRecord>()
    private val cachedStandings = mutableMapOf<String, List<StandingRow>>()
    private val sourceStatuses = FootballSourceCatalog.initialMetadata().associateBy { it.sourceId }.toMutableMap()
    private val enabledCompetitions = FootballSourceCatalog.defaultCompetitions.associateBy { it.id }.toMutableMap()

    fun getCompetitions(): List<CompetitionInfo> = enabledCompetitions.values.toList()

    fun setCompetitionEnabled(competitionId: String, enabled: Boolean) {
        enabledCompetitions[competitionId]?.let {
            enabledCompetitions[competitionId] = it.copy(enabled = enabled)
        }
    }

    fun getSourceMetadata(): List<DataSourceMetadata> = sourceStatuses.values.toList()

    fun getLastUpdateEpochMs(): Long = sourceStatuses.values.maxOfOrNull { it.lastRetrievedEpochMs } ?: 0L

    fun getSearchSuggestions(): List<SearchSuggestion> = listOf(
        SearchSuggestion("Bayern Munich", "Club • Germany", "Bayern Munich"),
        SearchSuggestion("Arsenal", "Club • England", "Arsenal"),
        SearchSuggestion("Real Madrid", "Club • Spain", "Real Madrid"),
        SearchSuggestion("Arsenal Women", "Women's Football", "Arsenal Women"),
        SearchSuggestion("FC Bayern Frauen", "Women's Football", "Bayern"),
        SearchSuggestion("Bundesliga", "Competition", "Bundesliga"),
        SearchSuggestion("Premier League", "Competition", "Premier League"),
        SearchSuggestion("Brazil National", "National Team", "Brazil")
    )

    suspend fun refreshAllFeeds(force: Boolean = false): FeedSnapshot = coroutineScope {
        if (!force && cachedFixtures.isNotEmpty()) {
            return@coroutineScope buildSnapshot()
        }
        val activeComps = enabledCompetitions.values.filter { it.enabled }
        val now = System.currentTimeMillis()

        val openLigaTasks = activeComps.filter { it.sourceId == "openligadb" }.map { comp ->
            async { comp to openLigaDb.fetchUpcomingAndRecent(comp) }
        }
        val sportsDbTasks = activeComps.filter { it.sourceId == "thesportsdb" }.map { comp ->
            async { comp to theSportsDb.fetchUpcomingAndRecent(comp) }
        }

        val allPairs = (openLigaTasks + sportsDbTasks).awaitAll()
        val rawFixtures = mutableListOf<FixtureItem>()
        val rawResults = mutableListOf<MatchResultRecord>()
        var openLigaSuccess = false
        var sportsDbSuccess = false

        allPairs.forEach { (comp, pair) ->
            if (pair.first.isNotEmpty() || pair.second.isNotEmpty()) {
                if (comp.sourceId == "openligadb") openLigaSuccess = true
                if (comp.sourceId == "thesportsdb") sportsDbSuccess = true
            }
            rawFixtures.addAll(pair.first)
            rawResults.addAll(pair.second)
        }

        // Update source health metadata transparently
        updateSourceHealth("openligadb", openLigaSuccess, now,
            if (openLigaSuccess) "Connected" else "No active records returned; check connectivity.")
        updateSourceHealth("thesportsdb", sportsDbSuccess, now,
            if (sportsDbSuccess) "Connected" else "Rate-limited or unreachable; using remaining active feeds.")

        // Deduplicate fixtures by canonical normalized match signature (home + away + day)
        val dedupedFixtures = deduplicateFixtures(rawFixtures)
        val dedupedResults = rawResults.distinctBy { it.matchId }

        synchronized(this@FootballDataRepository) {
            if (dedupedFixtures.isNotEmpty()) {
                cachedFixtures.clear()
                cachedFixtures.addAll(dedupedFixtures)
            }
            if (dedupedResults.isNotEmpty()) {
                cachedResults.clear()
                cachedResults.addAll(dedupedResults)
            }
        }

        // Fetch standings for top leagues in background
        val standingsTasks = activeComps.take(4).map { comp ->
            async {
                val rows = when (comp.sourceId) {
                    "openligadb" -> openLigaDb.fetchStandings(comp)
                    "thesportsdb" -> theSportsDb.fetchStandings(comp)
                    else -> emptyList()
                }
                comp.id to rows
            }
        }
        standingsTasks.awaitAll().forEach { (compId, rows) ->
            if (rows.isNotEmpty()) cachedStandings[compId] = rows
        }

        buildSnapshot()
    }

    private fun updateSourceHealth(sourceId: String, ok: Boolean, now: Long, note: String) {
        sourceStatuses[sourceId]?.let { prev ->
            sourceStatuses[sourceId] = prev.copy(
                health = if (ok) SourceHealth.ACTIVE else SourceHealth.DEGRADED,
                lastRetrievedEpochMs = now,
                statusNote = note
            )
        }
    }

    private fun buildSnapshot(): FeedSnapshot {
        val now = System.currentTimeMillis()
        val sevenDaysLater = now + TimeUnit.DAYS.toMillis(7)
        val upcoming7Days = cachedFixtures
            .filter { it.status == FixtureStatus.SCHEDULED || it.status == FixtureStatus.RESCHEDULED }
            .filter { it.kickoffEpochMs in (now - TimeUnit.HOURS.toMillis(2))..sevenDaysLater }
            .sortedBy { it.kickoffEpochMs }

        val liveMatches = cachedFixtures
            .filter { it.status == FixtureStatus.LIVE }
            .sortedBy { it.kickoffEpochMs }

        val recentFinished = cachedFixtures
            .filter { it.status == FixtureStatus.FINISHED }
            .sortedByDescending { it.kickoffEpochMs }

        return FeedSnapshot(
            upcomingSevenDays = upcoming7Days,
            liveMatches = liveMatches,
            recentFinished = recentFinished,
            allResults = cachedResults.toList(),
            lastUpdatedEpochMs = getLastUpdateEpochMs()
        )
    }

    fun generatePredictionForFixture(
        fixture: FixtureItem,
        params: ModelParameters = ModelParameters()
    ): MatchPredictionReport {
        val homeHistory = findTeamHistory(fixture.homeTeamId, fixture.homeTeam)
        val awayHistory = findTeamHistory(fixture.awayTeamId, fixture.awayTeam)
        val h2h = findH2HHistory(fixture.homeTeamId, fixture.homeTeam, fixture.awayTeamId, fixture.awayTeam)
        val enrichedFixture = fixture.copy(historicalMatchesSample = homeHistory.size + awayHistory.size)
        return MarketCalculator.generatePrediction(
            fixture = enrichedFixture,
            homeHistory = homeHistory,
            awayHistory = awayHistory,
            h2hHistory = h2h,
            params = params
        )
    }

    suspend fun searchFootball(query: String): SearchResultBundle = coroutineScope {
        val q = query.trim()
        val now = System.currentTimeMillis()
        if (q.isBlank()) {
            return@coroutineScope SearchResultBundle(
                query = q,
                matchedTeams = emptyList(),
                matchedCompetitions = emptyList(),
                matchedFixtures = emptyList(),
                webSummaries = emptyList(),
                retrievedEpochMs = now
            )
        }

        val teamsDeferred = async { theSportsDb.searchTeams(q) }
        val wikiDeferred = async { wikipediaSearch.searchFootballInfo(q) }

        val remoteTeams = teamsDeferred.await()
        val wikiResults = wikiDeferred.await()

        // Also match teams from already-loaded OpenLigaDB / TheSportsDB fixtures
        val localTeams = cachedFixtures
            .flatMap { f ->
                listOf(
                    TeamInfo(
                        id = f.homeTeamId,
                        name = f.homeTeam,
                        shortName = f.homeTeam.take(3).uppercase(Locale.US),
                        country = f.country,
                        competition = f.competition,
                        gender = f.gender,
                        crestUrl = f.homeCrestUrl,
                        sourceName = f.sourceName,
                        sourceUrl = f.sourceUrl,
                        retrievedEpochMs = f.retrievedEpochMs
                    ),
                    TeamInfo(
                        id = f.awayTeamId,
                        name = f.awayTeam,
                        shortName = f.awayTeam.take(3).uppercase(Locale.US),
                        country = f.country,
                        competition = f.competition,
                        gender = f.gender,
                        crestUrl = f.awayCrestUrl,
                        sourceName = f.sourceName,
                        sourceUrl = f.sourceUrl,
                        retrievedEpochMs = f.retrievedEpochMs
                    )
                )
            }
            .distinctBy { it.name.lowercase(Locale.US) }
            .filter { it.name.contains(q, ignoreCase = true) || it.competition.contains(q, ignoreCase = true) }

        val mergedTeams = (localTeams + remoteTeams).distinctBy { it.name.lowercase(Locale.US) }
        val matchedComps = getCompetitions().filter {
            it.name.contains(q, ignoreCase = true) || it.country.contains(q, ignoreCase = true)
        }
        val matchedFixtures = cachedFixtures.filter {
            it.homeTeam.contains(q, ignoreCase = true) ||
                it.awayTeam.contains(q, ignoreCase = true) ||
                it.competition.contains(q, ignoreCase = true)
        }.sortedBy { it.kickoffEpochMs }.take(25)

        val notice = if (mergedTeams.isEmpty() && matchedFixtures.isEmpty() && wikiResults.isEmpty()) {
            "No matching teams, competitions, or fixtures returned from public feeds. Google Custom Search JSON API is not configured; using free open sources."
        } else {
            "Results combined from free public football feeds and Wikimedia Open Search (Google Search JSON API requires owner API key & CX configuration)."
        }

        SearchResultBundle(
            query = q,
            matchedTeams = mergedTeams,
            matchedCompetitions = matchedComps,
            matchedFixtures = matchedFixtures,
            webSummaries = wikiResults,
            retrievedEpochMs = now,
            searchNotice = notice
        )
    }

    suspend fun analyzeTeam(team: TeamInfo): TeamAnalysisReport {
        // Fetch live team-specific events if sourced from TheSportsDB
        if (team.id.startsWith("tsdb_t_")) {
            val (extraFixtures, extraResults) = theSportsDb.fetchTeamEvents(team.id)
            synchronized(this) {
                extraFixtures.forEach { fix ->
                    if (cachedFixtures.none { it.matchId == fix.matchId }) cachedFixtures.add(fix)
                }
                extraResults.forEach { res ->
                    if (cachedResults.none { it.matchId == res.matchId }) cachedResults.add(res)
                }
            }
        }

        val allTeamMatches = findTeamHistory(team.id, team.name).sortedByDescending { it.dateEpochMs }
        val lastFive = allTeamMatches.take(5)
        val homeMatches = allTeamMatches.filter {
            PoissonModel.matchesTeam(it.homeTeamId, it.homeTeam, team.id, team.name)
        }
        val awayMatches = allTeamMatches.filter {
            PoissonModel.matchesTeam(it.awayTeamId, it.awayTeam, team.id, team.name)
        }

        val homeSplit = computeSplit(homeMatches, isHomePerspective = true)
        val awaySplit = computeSplit(awayMatches, isHomePerspective = false)

        val totalScored = homeSplit.goalsScored + awaySplit.goalsScored
        val totalConceded = homeSplit.goalsConceded + awaySplit.goalsConceded

        val upcoming = cachedFixtures
            .filter {
                (it.status == FixtureStatus.SCHEDULED || it.status == FixtureStatus.LIVE) &&
                    (PoissonModel.matchesTeam(it.homeTeamId, it.homeTeam, team.id, team.name) ||
                        PoissonModel.matchesTeam(it.awayTeamId, it.awayTeam, team.id, team.name))
            }
            .sortedBy { it.kickoffEpochMs }
            .take(5)

        val nextOpponent = upcoming.firstOrNull()?.let { fix ->
            if (PoissonModel.matchesTeam(fix.homeTeamId, fix.homeTeam, team.id, team.name)) {
                fix.awayTeamId to fix.awayTeam
            } else {
                fix.homeTeamId to fix.homeTeam
            }
        }
        val h2h = if (nextOpponent != null) {
            findH2HHistory(team.id, team.name, nextOpponent.first, nextOpponent.second)
        } else emptyList()

        val standings = cachedStandings.values.firstOrNull { rows ->
            rows.any { PoissonModel.matchesTeam(it.teamId, it.teamName, team.id, team.name) }
        } ?: emptyList()

        val notes = mutableListOf<String>()
        if (allTeamMatches.size < 5) {
            notes.add("Limited verified match history (${allTeamMatches.size} matches retrieved from free feeds).")
        }
        if (standings.isEmpty()) {
            notes.add("League standings table unavailable for ${team.competition} in current public feed.")
        }

        return TeamAnalysisReport(
            team = team,
            lastFiveMatches = lastFive,
            upcomingFixtures = upcoming,
            homePerformance = homeSplit,
            awayPerformance = awaySplit,
            totalGoalsScored = totalScored,
            totalGoalsConceded = totalConceded,
            standings = standings,
            headToHeadRecords = h2h,
            dataReliabilityNotes = notes,
            sourceLinks = listOf(team.sourceName to team.sourceUrl),
            retrievedEpochMs = System.currentTimeMillis()
        )
    }

    fun findTeamHistory(teamId: String, teamName: String): List<MatchResultRecord> {
        return cachedResults.filter {
            PoissonModel.matchesTeam(it.homeTeamId, it.homeTeam, teamId, teamName) ||
                PoissonModel.matchesTeam(it.awayTeamId, it.awayTeam, teamId, teamName)
        }.sortedByDescending { it.dateEpochMs }
    }

    fun findH2HHistory(homeId: String, homeName: String, awayId: String, awayName: String): List<MatchResultRecord> {
        return cachedResults.filter {
            (PoissonModel.matchesTeam(it.homeTeamId, it.homeTeam, homeId, homeName) &&
                PoissonModel.matchesTeam(it.awayTeamId, it.awayTeam, awayId, awayName)) ||
                (PoissonModel.matchesTeam(it.homeTeamId, it.homeTeam, awayId, awayName) &&
                    PoissonModel.matchesTeam(it.awayTeamId, it.awayTeam, homeId, homeName))
        }.sortedByDescending { it.dateEpochMs }.take(5)
    }

    fun getAllStandings(): Map<String, List<StandingRow>> = cachedStandings.toMap()

    fun deduplicateFixtures(list: List<FixtureItem>): List<FixtureItem> {
        return list.distinctBy { f ->
            val dayBucket = f.kickoffEpochMs / TimeUnit.DAYS.toMillis(1)
            "${f.homeTeam.trim().lowercase(Locale.US)}|${f.awayTeam.trim().lowercase(Locale.US)}|$dayBucket"
        }
    }

    private fun computeSplit(matches: List<MatchResultRecord>, isHomePerspective: Boolean): TeamPerformanceSplit {
        var wins = 0
        var draws = 0
        var losses = 0
        var scored = 0
        var conceded = 0
        matches.forEach { m ->
            val gf = if (isHomePerspective) m.homeGoals else m.awayGoals
            val ga = if (isHomePerspective) m.awayGoals else m.homeGoals
            scored += gf
            conceded += ga
            when {
                gf > ga -> wins++
                gf == ga -> draws++
                else -> losses++
            }
        }
        return TeamPerformanceSplit(
            played = matches.size,
            wins = wins,
            draws = draws,
            losses = losses,
            goalsScored = scored,
            goalsConceded = conceded
        )
    }
}

data class FeedSnapshot(
    val upcomingSevenDays: List<FixtureItem>,
    val liveMatches: List<FixtureItem>,
    val recentFinished: List<FixtureItem>,
    val allResults: List<MatchResultRecord>,
    val lastUpdatedEpochMs: Long
)
