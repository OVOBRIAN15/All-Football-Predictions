package com.example.domain.model

enum class GenderCategory(val label: String) {
    MEN("Men's Football"),
    WOMEN("Women's Football"),
    MIXED("International / Mixed")
}

enum class FixtureStatus(val label: String) {
    SCHEDULED("Scheduled"),
    LIVE("Live"),
    FINISHED("Finished"),
    POSTPONED("Postponed"),
    CANCELLED("Cancelled"),
    RESCHEDULED("Rescheduled")
}

enum class SourceHealth(val label: String) {
    ACTIVE("Active"),
    DEGRADED("Degraded"),
    UNAVAILABLE("Unavailable"),
    CACHED_FALLBACK("Cached Snapshot")
}

data class DataSourceMetadata(
    val sourceId: String,
    val sourceName: String,
    val license: String,
    val commercialUseNote: String,
    val coverageScope: String,
    val updateFrequency: String,
    val requiresApiKey: Boolean,
    val health: SourceHealth,
    val lastRetrievedEpochMs: Long,
    val baseUrl: String,
    val statusNote: String = ""
)

data class CompetitionInfo(
    val id: String,
    val name: String,
    val country: String,
    val gender: GenderCategory,
    val type: String, // Domestic League, Domestic Cup, Continental, National Team
    val sourceId: String,
    val enabled: Boolean = true
)

data class TeamInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val country: String,
    val competition: String,
    val gender: GenderCategory,
    val crestUrl: String? = null,
    val overview: String = "",
    val stadium: String = "",
    val sourceName: String,
    val sourceUrl: String,
    val retrievedEpochMs: Long
)

data class MatchResultRecord(
    val matchId: String,
    val dateEpochMs: Long,
    val competition: String,
    val homeTeamId: String,
    val homeTeam: String,
    val awayTeamId: String,
    val awayTeam: String,
    val homeGoals: Int,
    val awayGoals: Int,
    val halftimeHomeGoals: Int? = null,
    val halftimeAwayGoals: Int? = null,
    val homeGoalMinutes: List<Int> = emptyList(),
    val awayGoalMinutes: List<Int> = emptyList(),
    val sourceName: String
)

data class StandingRow(
    val position: Int,
    val teamId: String,
    val teamName: String,
    val crestUrl: String? = null,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int
)

data class FixtureItem(
    val matchId: String,
    val homeTeamId: String,
    val homeTeam: String,
    val homeCrestUrl: String? = null,
    val awayTeamId: String,
    val awayTeam: String,
    val awayCrestUrl: String? = null,
    val competitionId: String,
    val competition: String,
    val country: String,
    val gender: GenderCategory,
    val kickoffEpochMs: Long,
    val status: FixtureStatus,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val liveMinute: Int? = null,
    val officialProviderHomeXg: Double? = null,
    val officialProviderAwayXg: Double? = null,
    val historicalMatchesSample: Int,
    val sourceId: String,
    val sourceName: String,
    val sourceUrl: String,
    val retrievedEpochMs: Long
)

data class TeamPerformanceSplit(
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsScored: Int,
    val goalsConceded: Int
) {
    val avgScored: Double get() = if (played > 0) goalsScored.toDouble() / played else 0.0
    val avgConceded: Double get() = if (played > 0) goalsConceded.toDouble() / played else 0.0
}

data class TeamAnalysisReport(
    val team: TeamInfo,
    val lastFiveMatches: List<MatchResultRecord>,
    val upcomingFixtures: List<FixtureItem>,
    val homePerformance: TeamPerformanceSplit,
    val awayPerformance: TeamPerformanceSplit,
    val totalGoalsScored: Int,
    val totalGoalsConceded: Int,
    val standings: List<StandingRow>,
    val headToHeadRecords: List<MatchResultRecord>,
    val dataReliabilityNotes: List<String>,
    val sourceLinks: List<Pair<String, String>>,
    val retrievedEpochMs: Long
)

data class SearchSuggestion(
    val label: String,
    val category: String,
    val query: String
)

data class WebSummaryResult(
    val title: String,
    val snippet: String,
    val url: String,
    val sourceName: String,
    val retrievedEpochMs: Long
)

data class SearchResultBundle(
    val query: String,
    val matchedTeams: List<TeamInfo>,
    val matchedCompetitions: List<CompetitionInfo>,
    val matchedFixtures: List<FixtureItem>,
    val webSummaries: List<WebSummaryResult>,
    val retrievedEpochMs: Long,
    val searchNotice: String? = null
)
