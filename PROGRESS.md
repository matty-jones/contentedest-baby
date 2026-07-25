# Contentedest Baby — Progress

## Latest: Words Counter Improvements (2026-07-25)

Implemented fuzzy search, DOB-based vocabulary percentile curves, Growth DOB anchoring, and MacArthur-Bates Short Form Level I (MA-B) Understood/Said metadata.

### Done
- **DOB setting**: Settings screen date picker writes `settings.dob_epoch_days` via `SettingsRepository`.
- **Growth age**: Weight/height percentiles and stats age from DOB when set; fall back to first measurement if unset (`dobEpochSeconds` + `calculateAgeMonths`).
- **Fuzzy search**: Local Levenshtein ratio matcher in `WordFuzzyMatcher` (FuzzyKot was incompatible with Room/kapt Kotlin 2.0.21 metadata). Search button beside Add Word; scroll + brief highlight on hit.
- **Vocab percentiles**: `VocabularyPercentileCalculator` (16–30 mo, log1p interp, hard null outside range). `PercentileLineStyles` shared with Growth fade/stroke recipe. Overlay on Words graph when DOB is set.
- **MA-B**: Room migration 4→5 + server `understands`/`says` columns (startup ALTER). Checklist of 89 words. Live radios on Add/Edit when fuzzy-match. List `*` indicators (secondary=Understood, tertiary=Said). Stats bar `MA-B: X/89 Understood, Y/89 Said`. Duplicate word with different flags upserts MA-B. One-time Says backfill on app start.

### Key files
- Android: `SettingsScreen`, `SettingsRepository`, `GrowthScreen`, `WordsScreen`, `WordsGraphView`, `WordsListView`, `AddWordDialog`, `EditWordDialog`, `SearchWordDialog`, `WordsStatsBar`, `WordFuzzyMatcher`, `MacArthurBatesChecklist`, `VocabularyPercentileCalculator`, `PercentileLineStyles`, `WordRepository`, `AppModule` (MIGRATION_4_5)
- Server: `models.BabyWord`, `schemas.WordDTO`, `routers/words.py`, `migrate_schema.py`, `crud._WORD_UPSERT_FIELDS`

### Notes for next agents
- Set DOB in Settings before vocab percentile lines appear.
- Said always stores `understands=true` and `says=true`.
- Vocab norms are male / American English, informational only (Mayor & Plunkett–style table).
