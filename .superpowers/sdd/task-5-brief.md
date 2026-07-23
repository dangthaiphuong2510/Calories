### Task 5: ISO week keys + `InsightPreferences`

**Files:**
- Create: `app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt`
- Create: `app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt`
- Test: `app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt`
- Modify: `app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt`

**Interfaces:**
- Consumes: `java.time.LocalDate`
- Produces:
  - `InsightWeekKeys.isoWeekKey(date: LocalDate): String` â†’ `"YYYY-Www"` (ISO)
  - `InsightPreferences.dismissedIdsForToday(): Set<String>`
  - `InsightPreferences.dismiss(insightId: String)`
  - `InsightPreferences.clear()`

- [ ] **Step 1: Write failing week-key tests**

```kotlin
package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InsightWeekKeysTest {
    @Test
    fun isoWeekKey_formatsYearAndWeek() {
        // 2026-07-22 is Wednesday of ISO week 30
        assertEquals("2026-W30", InsightWeekKeys.isoWeekKey(LocalDate.of(2026, 7, 22)))
    }
}
```

- [ ] **Step 2: Run test â€” expect FAIL**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"`

- [ ] **Step 3: Implement `InsightWeekKeys` + `InsightPreferences`**

```kotlin
package com.example.calories.insights

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

object InsightWeekKeys {
    fun isoWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }
}
```

```kotlin
package com.example.calories.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.calories.insights.InsightWeekKeys
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedIds = MutableStateFlow(readDismissedForCurrentWeek())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()

    fun dismiss(insightId: String) {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        val current = if (storedWeek == week) {
            prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }
        current += insightId
        prefs.edit()
            .putString(KEY_WEEK, week)
            .putStringSet(KEY_IDS, current)
            .apply()
        _dismissedIds.value = current.toSet()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _dismissedIds.value = emptySet()
    }

    private fun readDismissedForCurrentWeek(): Set<String> {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        if (storedWeek != week) {
            prefs.edit().clear().apply()
            return emptySet()
        }
        return prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
    }

    companion object {
        const val PREFS_NAME = "insight_prefs"
        private const val KEY_WEEK = "week_key"
        private const val KEY_IDS = "dismissed_ids"
    }
}
```

Update `LocalDataWiper.wipeAll()` to also clear insight prefs (after notification prefs clear):

```kotlin
        context.getSharedPreferences(InsightPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
```

Add import for `InsightPreferences`.

- [ ] **Step 4: Run week-key tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt
git commit -m "feat(insights): add week keys and dismiss preferences"
```

---

