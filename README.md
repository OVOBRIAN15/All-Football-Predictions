# All Football Predictions

**All Football Predictions** is a native Android application (Kotlin + Jetpack Compose + Material 3) for deterministic statistical football analysis, multi-source match data retrieval, probability modelling, prediction tracking, community engagement, and owner-only platform administration.

## 1. Key Capabilities

- **Luxury Black & Gold Material 3 Design**: Deep obsidian backgrounds (`#090A0E`), dark charcoal surfaces (`#171A24`), metallic gold accents (`#D4AF37` / `#F2D06B`), custom adaptive launcher icon, responsive layouts for phones, foldables, and tablets (`NavigationBar` + `NavigationRail` + full 16-section navigation drawer).
- **16 Navigation Sections + Legal Pages**:
  1. Home Dashboard (today, tomorrow, next 7 days, top opportunities, accuracy KPIs, community highlights, filters by date/country/competition/men's/women's/market)
  2. Search Football (clubs, national teams, women's teams, competitions, fixtures, suggestions, DataStore recent searches, clear, loading & error handling)
  3. Upcoming Fixtures (next 7 days in local device timezone)
  4. Football Predictions (Full-Time, 1st Half, 2nd Half, First 10 Minutes, confidence score, data-quality warnings, Model-Estimated xG vs Official Provider xG)
  5. Correct Scores (top scorelines directly from the bivariate score matrix)
  6. Live Match Centre (reports verified active feeds only; displays an honest notice when no live matches are active)
  7. Football Statistics (competition standings tables and source licensing/coverage metadata)
  8. Team Analysis (overview, crest, last 5 results, home/away splits, goals scored/conceded, head-to-head history, upcoming matches)
  9. Advanced Accumulator (multi-leg builder with automatic correlation detection and variance warning)
  10. Prediction History (immutable timestamped pre-match snapshots and automatic post-match settlement)
  11. Accuracy Reports (all-time accuracy, Brier score, breakdowns by market, competition, and confidence tier)
  12. Community & Reviews (1–5 star ratings with one active review per user, discussion threads, private owner feedback, content reporting)
  13. User Profile (activity metrics, display name editing, Android notification permission & preferences, sign out, permanent account deletion)
  14. Read Me (full plain-language documentation of sources, Poisson/Dixon-Coles math, xG definitions, and limitations)
  15. Sign In (Google Sign-In via Jetpack Credential Manager + Firebase Auth, Privacy Policy, Terms of Use)
  16. Administrator Login & Separate Owner Dashboard (strictly restricted to `tumusiimeryanpro@gmail.com` + salted SHA-256 secondary passphrase)

## 2. Public Football Data Sources & Licensing

No paid API keys are exposed or required for public football feeds:

- **OpenLigaDB** (`https://api.openligadb.de`) — Public community football API providing Bundesliga, 2. Bundesliga, DFB-Pokal, UEFA Champions League, and FIFA World Cup match schedules, half-time splits, and goal-minute events.
- **TheSportsDB Free Public Tier** (`https://www.thesportsdb.com/api/v1/json/3/...`) — Free public educational/test endpoint (`key 3`) for clubs, national teams, women's leagues, standings, and upcoming/past fixtures.
- **Wikimedia / Wikipedia Open Search API** (`https://en.wikipedia.org/w/api.php`) — Free, no-key open search summary fallback used strictly for contextual club/competition lookups (never used to calculate prediction odds without structured fixture/statistical data, and never scrapes Google search-result pages).

*Note on Google Search*: Google Programmable/Custom Search JSON API requires a billed/quota API key and is **not** activated without owner approval. Scraping Google search pages is prohibited and never performed.

## 3. Deterministic Statistical Prediction Engine

- **Zero Random Generators**: All probabilities come purely from deterministic arithmetic.
- **Expected Goals (`lambdaHome`, `lambdaAway`)**: Computed from weighted recent 5-match form, separate home-venue and away-venue attack/defence rates, Bayesian shrinkage toward league baseline on small samples, and up to 5 historical head-to-head meetings.
- **Bivariate Score Matrix**: Uses Poisson PMF up to 8 goals per side adjusted by Dixon-Coles low-score correlation (`tau` for `0-0`, `1-0`, `0-1`, `1-1`).
- **Normalized Markets**: Mutually exclusive markets (1X2, BTTS, Over/Under, Draw No Bet) are normalized via largest-remainder rounding so integer probabilities sum exactly to `100%`.
- **Honest Period & xG Gating**:
  - `Model-Estimated xG` is always distinguished from `Official Provider xG` (labelled `Unavailable` unless supplied by a shot-event provider).
  - First-Half / Second-Half markets require >= 3 verified half-time split records.
  - First-10-Minute markets require >= 5 minute-logged goal event records; otherwise the app explicitly displays `Insufficient data`.

## 4. Firebase Security & Owner Administration

- **Database**: Cloud Firestore (named database configured in `app/src/main/res/values/firebase_applet_config.xml`) with strict zero-trust rules in `/firestore.rules`.
- **Authentication**: Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`).
- **Guest Access Isolation**: Visitors can browse public football data and calculations without signing in, while zero Firestore queries or listeners run when unauthenticated.
- **Owner-Only Admin Access**:
  - Verified server-side in `firestore.rules` (`request.auth.token.email_verified == true && request.auth.token.email == 'tumusiimeryanpro@gmail.com'`).
  - Protected client-side by `AuthHelper.isVerifiedOwnerAdmin(user)` plus a secondary salted SHA-256 owner passphrase stored in `admin_config/security` with 5-attempt lockout and immutable `admin_audit_logs`.

## 5. Building & Testing

### Prerequisites
- Android SDK (compileSdk 36, minSdk 24, targetSdk 36)
- Gradle (`gradle` CLI)
- Node.js & Firebase CLI (for local Firestore security rules verification)

### Commands
```bash
# Run JVM unit & Robolectric UI tests
gradle :app:testDebugUnitTest

# Build debug APK
gradle :app:assembleDebug

# Run Firestore Security Rules unit tests against local emulator
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Secrets & Environment Configuration
- Never commit `.env`, API keys, keystores, or service-account JSON credentials to version control.
- `.env.example` documents optional configuration placeholders managed via the environment's Secrets configuration.
