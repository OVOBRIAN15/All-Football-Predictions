package com.example.domain.model

data class MarketOutcomeProb(
    val label: String,
    val probabilityPct: Int,
    val rawProbability: Double
)

data class CorrectScoreProb(
    val homeGoals: Int,
    val awayGoals: Int,
    val probabilityPct: Double
) {
    val scoreLine: String get() = "$homeGoals-$awayGoals"
}

data class MarketGroup(
    val marketName: String,
    val outcomes: List<MarketOutcomeProb>,
    val available: Boolean = true,
    val unavailableReason: String? = null
)

data class PeriodMarketSuite(
    val periodTitle: String,
    val available: Boolean,
    val unavailableReason: String? = null,
    val groups: List<MarketGroup>,
    val topCorrectScores: List<CorrectScoreProb> = emptyList()
)

data class ModelParameters(
    val versionTag: String = "v1.0-dc-poisson",
    val dixonColesRho: Double = -0.11,
    val homeAdvantageMultiplier: Double = 1.14,
    val h2hWeight: Double = 0.18,
    val recencyDecay: Double = 0.85,
    val leagueBaselineGoalsPerTeam: Double = 1.35
)

data class MatchPredictionReport(
    val fixture: FixtureItem,
    val modelVersion: String,
    val generatedEpochMs: Long,
    val modelEstimatedHomeXg: Double,
    val modelEstimatedAwayXg: Double,
    val officialProviderHomeXg: Double? = null,
    val officialProviderAwayXg: Double? = null,
    val probHomeWin: Int,
    val probDraw: Int,
    val probAwayWin: Int,
    val fullTimeSuite: PeriodMarketSuite,
    val firstHalfSuite: PeriodMarketSuite,
    val secondHalfSuite: PeriodMarketSuite,
    val firstTenMinutesSuite: PeriodMarketSuite,
    val fullTimeCorrectScores: List<CorrectScoreProb>,
    val confidenceScore: Int,
    val historicalSampleSize: Int,
    val h2hSampleSize: Int,
    val missingStatistics: List<String>,
    val reliabilityWarnings: List<String>,
    val dataSources: List<String>,
    val factsSummary: List<String>
)

data class AccumulatorSelection(
    val fixture: FixtureItem,
    val marketName: String,
    val selectionLabel: String,
    val probability: Double,
    val probabilityPct: Int
) {
    val selectionKey: String get() = "${fixture.matchId}|$marketName|$selectionLabel"
}

data class AccumulatorEvaluation(
    val selections: List<AccumulatorSelection>,
    val naiveCombinedProbPct: Double,
    val correlationWarnings: List<String>,
    val independenceJustified: Boolean,
    val riskNotice: String
)

data class AccuracyReportSummary(
    val totalPredictions: Int,
    val settledPredictions: Int,
    val voidOrPostponedCount: Int,
    val correctPredictions: Int,
    val incorrectPredictions: Int,
    val overallAccuracyPct: Double,
    val brierScore: Double,
    val accuracyByCompetition: Map<String, Pair<Int, Int>>, // correct to total
    val accuracyByMarket: Map<String, Pair<Int, Int>>,
    val accuracyByPeriod: Map<String, Pair<Int, Int>>,
    val accuracyByConfidenceRange: Map<String, Pair<Int, Int>>
)

data class RecalibrationProposal(
    val candidateParams: ModelParameters,
    val baselineBrier: Double,
    val candidateBrier: Double,
    val baselineAccuracyPct: Double,
    val candidateAccuracyPct: Double,
    val sampleSize: Int,
    val approvedImprovement: Boolean,
    val evaluationSummary: String
)
