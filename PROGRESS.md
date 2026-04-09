# Progress

## 2026-04-08 — Snake timeline row connectors

**Issue:** Long sleep (or any) events spanning multiple rows could draw the curved row-to-row connector on the wrong side (e.g. right on an R→L row where the white track turns left).

**Cause:** Connector X was chosen from `touchesPhysicalLeft` / `touchesPhysicalRight` derived from exact `startFrac == 0f` and `endFrac == 1f`. Slight float error or boundary semantics made `touchesRowEnd` false on R→L rows while `touchesRowStart` stayed true, so the code picked `innerRight` (wrong).

**Fix:** In `SnakeTimeline.kt` `computeEventDrawables`, emit a connector only when `continues && segEnd == rowEndSec` (integer row boundary), and set `edgeX` / `cpX` from `goingRight` only (`innerRight` + offset when L→R, else `innerLeft` - offset).

**Verify:** `./gradlew :app:compileDebugKotlin` from `android/`.
