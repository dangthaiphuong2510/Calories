### Task 6: Strings (EN + VI) + `ProgressInsightUiMapper`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Create: `app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt`

**Interfaces:**
- Consumes: `ProgressInsight.id`, `formatArgs`
- Produces: `ProgressInsightUiMapper.titleRes(id)`, `bodyRes(id)` â€” Int resource ids

- [ ] **Step 1: Add English strings** (append near progress section in `values/strings.xml`)

```xml
    <string name="insights_section_title">Weekly insights</string>
    <string name="insights_insufficient_title">Keep logging</string>
    <string name="insights_insufficient_body">Log food for a few more days to unlock personal insights.</string>
    <string name="insights_plateau_title">Scale not moving</string>
    <string name="insights_plateau_body">Your logs show several days under target, but weight is nearly flat. Check portions or logging gaps.</string>
    <string name="insights_weekend_title">Weekend calorie spike</string>
    <string name="insights_weekend_body">Your logs show weekend intake meaningfully higher than weekdays.</string>
    <string name="insights_protein_title">Protein running low</string>
    <string name="insights_protein_body">Protein was under ~80% of target on most recent logged days.</string>
    <string name="insights_logging_gap_title">Missing food logs</string>
    <string name="insights_logging_gap_body">%1$s days in the last week have no food logged, so trends may be incomplete.</string>
    <string name="insights_on_track_title">Solid adherence</string>
    <string name="insights_on_track_body">You stayed near your calorie target on %1$s days this week.</string>
    <string name="insights_dismiss">Dismiss</string>
    <string name="insights_home_callout_cd">Progress insight</string>
```

- [ ] **Step 2: Add Vietnamese parity** in `values-vi/strings.xml` (same keys)

```xml
    <string name="insights_section_title">Nháº­n xÃ©t tuáº§n nÃ y</string>
    <string name="insights_insufficient_title">Tiáº¿p tá»¥c ghi nháº­t kÃ½</string>
    <string name="insights_insufficient_body">Ghi thÃªm vÃ i ngÃ y thá»±c pháº©m Ä‘á»ƒ má»Ÿ khÃ³a nháº­n xÃ©t cÃ¡ nhÃ¢n.</string>
    <string name="insights_plateau_title">CÃ¢n khÃ´ng Ä‘á»•i</string>
    <string name="insights_plateau_body">Nháº­t kÃ½ cho tháº¥y nhiá»u ngÃ y dÆ°á»›i má»¥c tiÃªu nhÆ°ng cÃ¢n gáº§n nhÆ° Ä‘á»©ng yÃªn. HÃ£y kiá»ƒm tra kháº©u pháº§n hoáº·c ngÃ y thiáº¿u log.</string>
    <string name="insights_weekend_title">Cuá»‘i tuáº§n cao calo</string>
    <string name="insights_weekend_body">Nháº­t kÃ½ cho tháº¥y lÆ°á»£ng Äƒn cuá»‘i tuáº§n cao hÆ¡n rÃµ so vá»›i ngÃ y thÆ°á»ng.</string>
    <string name="insights_protein_title">Protein Ä‘ang tháº¥p</string>
    <string name="insights_protein_body">Protein dÆ°á»›i ~80% má»¥c tiÃªu á»Ÿ háº§u háº¿t cÃ¡c ngÃ y gáº§n Ä‘Ã¢y cÃ³ ghi nháº­t kÃ½.</string>
    <string name="insights_logging_gap_title">Thiáº¿u nháº­t kÃ½ mÃ³n Äƒn</string>
    <string name="insights_logging_gap_body">%1$s ngÃ y trong tuáº§n qua chÆ°a cÃ³ nháº­t kÃ½ mÃ³n Äƒn, xu hÆ°á»›ng cÃ³ thá»ƒ chÆ°a Ä‘á»§.</string>
    <string name="insights_on_track_title">Duy trÃ¬ tá»‘t</string>
    <string name="insights_on_track_body">Báº¡n gáº§n má»¥c tiÃªu calo trong %1$s ngÃ y tuáº§n nÃ y.</string>
    <string name="insights_dismiss">ÄÃ³ng</string>
    <string name="insights_home_callout_cd">Nháº­n xÃ©t tiáº¿n Ä‘á»™</string>
```

- [ ] **Step 3: Implement mapper**

```kotlin
package com.example.calories.insights

import androidx.annotation.StringRes
import com.example.calories.R

object ProgressInsightUiMapper {
    @StringRes
    fun titleRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_title
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_title
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_title
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_title
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_title
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_title
        else -> R.string.insights_section_title
    }

    @StringRes
    fun bodyRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_body
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_body
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_body
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_body
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_body
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_body
        else -> R.string.insights_insufficient_body
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt
git commit -m "feat(insights): add EN/VI copy and UI string mapper"
```

---

