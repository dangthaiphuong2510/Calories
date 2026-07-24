# Interactive Widget — Add Water (+250ml) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the existing 4×2 home widget to an interactive widget with a `+ 250ml` button that logs water for today in the background and refreshes the widget live — without opening the app.

**Architecture:** Keep the current `RemoteViews` + `AppWidgetProvider` stack (no Glance). Extend `CaloriesWidgetSnapshot` with water fields matching `HomeUiState` math. A `BroadcastReceiver` handles the button tap via `PendingIntent.getBroadcast`, calls `WaterRepository.addWaterEntry(250)`, then `WidgetRefreshNotifier`. The widget root tap still deep-links to Progress; only `btnAddWater` uses the broadcast intent.

**Tech Stack:** Kotlin, Hilt, Room (`WaterEntryDao`), `RemoteViews`, `BroadcastReceiver`, `PendingIntent`, JUnit, existing `WaterRepository`, `WidgetRefreshNotifier`.

## Global Constraints

- Package root: `com.example.calories`
- Widget size stays **4×2 cells** (`minWidth=250dp`, `minHeight=110dp`, `targetCellWidth=4`, `targetCellHeight=2`)
- Water step: **250 ml**; water goal: **2000 ml** (same as `HomeViewModel.WATER_STEP_ML` / `WATER_GOAL_ML`)
- Water progress math must match `HomeUiState.waterProgressPercent` (`waterIntakeMl / waterGoalMl`, clamped 0–100)
- Widget shows **today only** (use `DateTimeUtils.today()` + `DateTimeUtils.nowIso()` for new entries)
- Button tap must **not** launch `MainActivity`; widget body tap still opens Progress tab
- Reuse `WaterRepository.addWaterEntry` — no duplicate Room write logic in the receiver
- All new user-facing strings in `values/strings.xml` **and** `values-vi/strings.xml`
- No new Room tables
- Run unit tests with: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`
- YAGNI: no remove-water button on widget, no Glance migration, no widget configuration activity

---

## File structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/example/calories/util/WaterDefaults.kt` | Shared `GOAL_ML` / `STEP_ML` constants |
| `app/src/main/java/com/example/calories/data/local/dao/WaterEntryDao.kt` | Add suspend day-total query for widget updater |
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt` | Add water fields to snapshot + builder params |
| `app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt` | Water snapshot tests |
| `app/src/main/java/com/example/calories/widget/WidgetWaterActionHandler.kt` | Injectable add-water + refresh orchestration |
| `app/src/test/java/com/example/calories/widget/WidgetWaterActionHandlerTest.kt` | Handler unit tests |
| `app/src/main/java/com/example/calories/widget/WidgetAddWaterReceiver.kt` | Broadcast entry point for button tap |
| `app/src/main/res/layout/widget_calories_home.xml` | Add compact water row + `btnAddWater` |
| `app/src/main/res/drawable/widget_water_progress_bar.xml` | Water-colored progress drawable |
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt` | Bind water UI |
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt` | Load water total; wire pending intents |
| `app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt` | Add `createAddWaterPendingIntent` factory |
| `app/src/main/java/com/example/calories/data/repository/WaterRepositoryImpl.kt` | Notify widget on water changes |
| `app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt` | Use `WaterDefaults` instead of private constants |
| Modify: `AndroidManifest.xml` | Register `WidgetAddWaterReceiver` |
| Modify: `values/strings.xml`, `values-vi/strings.xml` | Widget water copy |
| Modify: `calories_home_widget_info.xml` | Update description string reference only |

---

### Task 1: Shared water constants + DAO day total

**Files:**
- Create: `app/src/main/java/com/example/calories/util/WaterDefaults.kt`
- Modify: `app/src/main/java/com/example/calories/data/local/dao/WaterEntryDao.kt`

**Interfaces:**
- Consumes: none
- Produces:
  - `WaterDefaults.GOAL_ML: Int` (= 2000)
  - `WaterDefaults.STEP_ML: Int` (= 250)
  - `WaterEntryDao.getTotalMlForDay(userId: String, startInclusive: String, endExclusive: String): Int`

- [ ] **Step 1: Create `WaterDefaults.kt`**

```kotlin
package com.example.calories.util

object WaterDefaults {
    const val GOAL_ML = 2000
    const val STEP_ML = 250
}
```

- [ ] **Step 2: Add DAO query**

In `WaterEntryDao.kt`, add:

```kotlin
@Query(
    """
    SELECT COALESCE(SUM(amountMl), 0)
    FROM water_entries
    WHERE userId = :userId
      AND createdAt >= :startInclusive
      AND createdAt < :endExclusive
    """,
)
suspend fun getTotalMlForDay(
    userId: String,
    startInclusive: String,
    endExclusive: String,
): Int
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/util/WaterDefaults.kt app/src/main/java/com/example/calories/data/local/dao/WaterEntryDao.kt
git commit -m "feat(widget): add water defaults and day-total DAO query"
```

---

### Task 2: Extend widget snapshot with water fields

**Files:**
- Modify: `app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt`
- Modify: `app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt`

**Interfaces:**
- Consumes: `WaterDefaults.GOAL_ML`, `WaterDefaults.STEP_ML`
- Produces:
  - `CaloriesWidgetSnapshot.waterIntakeMl: Int`
  - `CaloriesWidgetSnapshot.waterGoalMl: Int`
  - `CaloriesWidgetSnapshot.waterProgressPercent: Int`
  - `CaloriesWidgetSnapshotBuilder.build(..., waterIntakeMl: Int, today: LocalDate)` — new param

- [ ] **Step 1: Write failing water tests**

Add to `CaloriesWidgetSnapshotBuilderTest.kt`:

```kotlin
@Test
fun waterProgress_matchesHomeUiState() {
    val snapshot = CaloriesWidgetSnapshotBuilder.build(
        isSignedIn = true,
        goal = goal(),
        todayFoods = emptyList(),
        allFoods = emptyList(),
        weights = emptyList(),
        todayExercises = emptyList(),
        dismissedIds = emptySet(),
        waterIntakeMl = 500,
        today = today,
    )
    assertEquals(500, snapshot.waterIntakeMl)
    assertEquals(WaterDefaults.GOAL_ML, snapshot.waterGoalMl)
    assertEquals(25, snapshot.waterProgressPercent)
}

@Test
fun signedOut_hidesWaterByUsingZeroDisplayMode() {
    val snapshot = CaloriesWidgetSnapshotBuilder.build(
        isSignedIn = false,
        goal = null,
        todayFoods = emptyList(),
        allFoods = emptyList(),
        weights = emptyList(),
        todayExercises = emptyList(),
        dismissedIds = emptySet(),
        waterIntakeMl = 0,
        today = today,
    )
    assertEquals(CaloriesWidgetDisplayMode.SIGNED_OUT, snapshot.displayMode)
    assertEquals(0, snapshot.waterIntakeMl)
}
```

Add import: `import com.example.calories.util.WaterDefaults`

Update every existing `build(...)` call in this test file to pass `waterIntakeMl = 0`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest"`

Expected: FAIL — unresolved `waterIntakeMl` parameter or missing snapshot fields

- [ ] **Step 3: Implement snapshot changes**

Update `CaloriesWidgetSnapshot.kt`:

```kotlin
data class CaloriesWidgetSnapshot(
    val displayMode: CaloriesWidgetDisplayMode,
    val dailyGoal: Int = 0,
    val totalEaten: Int = 0,
    val totalBurned: Int = 0,
    val caloriesRemaining: Int = 0,
    val progressPercent: Int = 0,
    val waterIntakeMl: Int = 0,
    val waterGoalMl: Int = WaterDefaults.GOAL_ML,
    val waterProgressPercent: Int = 0,
    val activeInsight: ProgressInsight? = null,
)
```

Add import: `import com.example.calories.util.WaterDefaults`

Add `waterIntakeMl: Int` parameter to `build(...)` and compute:

```kotlin
val waterGoalMl = WaterDefaults.GOAL_ML
val waterProgressPercent = if (waterGoalMl <= 0) {
    0
} else {
    ((waterIntakeMl.toFloat() / waterGoalMl) * 100f).toInt().coerceIn(0, 100)
}
```

Include `waterIntakeMl`, `waterGoalMl`, `waterProgressPercent` in the returned `CaloriesWidgetSnapshot`.

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest"`

Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt
git commit -m "feat(widget): add water fields to widget snapshot builder"
```

---

### Task 3: Widget layout, strings, and water progress drawable

**Files:**
- Modify: `app/src/main/res/layout/widget_calories_home.xml`
- Create: `app/src/main/res/drawable/widget_water_progress_bar.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Modify: `app/src/main/res/xml/calories_home_widget_info.xml` (description only)

**Interfaces:**
- Consumes: existing `water_title`, `water_progress_format`, `water_filled`, `water_empty` colors
- Produces layout ids: `sectionWater`, `tvWaterProgress`, `progressWater`, `btnAddWater`

- [ ] **Step 1: Add EN + VI strings**

In `values/strings.xml`:

```xml
<string name="widget_calories_description">Today\'s calories, water, and weekly insight</string>
<string name="widget_add_water_button">+ 250ml</string>
```

In `values-vi/strings.xml`:

```xml
<string name="widget_calories_description">Calo, nước hôm nay và nhận xét tuần</string>
<string name="widget_add_water_button">+ 250ml</string>
```

- [ ] **Step 2: Create `widget_water_progress_bar.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="4dp" />
            <solid android:color="@color/water_empty" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="4dp" />
                <solid android:color="@color/water_filled" />
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 3: Add water section to widget layout**

Insert **between** `sectionProgress` and `sectionInsight` in `widget_calories_home.xml`:

```xml
<LinearLayout
    android:id="@+id/sectionWater"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:visibility="gone">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/water_title"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />

        <TextView
            android:id="@+id/tvWaterProgress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textColor="@color/text_primary"
            android:textSize="13sp"
            android:textStyle="bold"
            android:text="0 / 2000ml" />

        <ProgressBar
            android:id="@+id/progressWater"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="6dp"
            android:layout_marginTop="4dp"
            android:max="100"
            android:progress="0"
            android:progressDrawable="@drawable/widget_water_progress_bar" />
    </LinearLayout>

    <Button
        android:id="@+id/btnAddWater"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:layout_marginStart="8dp"
        android:minWidth="0dp"
        android:minHeight="0dp"
        android:paddingHorizontal="10dp"
        android:paddingVertical="4dp"
        android:text="@string/widget_add_water_button"
        android:textAllCaps="false"
        android:textColor="@color/text_primary"
        android:textSize="12sp" />
</LinearLayout>
```

In `READY` mode the renderer will show this section; hide it for signed-out / no-goal states.

- [ ] **Step 4: Verify resources compile**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/widget_calories_home.xml app/src/main/res/drawable/widget_water_progress_bar.xml app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/res/xml/calories_home_widget_info.xml
git commit -m "feat(widget): add water row and +250ml button layout"
```

---

### Task 4: Widget water action handler + broadcast receiver

**Files:**
- Create: `app/src/main/java/com/example/calories/widget/WidgetWaterActionHandler.kt`
- Create: `app/src/test/java/com/example/calories/widget/WidgetWaterActionHandlerTest.kt`
- Create: `app/src/main/java/com/example/calories/widget/WidgetAddWaterReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WaterRepository.addWaterEntry`, `WidgetRefreshNotifier.notifyDataChanged`, `WaterDefaults.STEP_ML`, `DateTimeUtils.nowIso()`
- Produces:
  - `WidgetWaterActionHandler.addWaterFromWidget(): Boolean`
  - `WidgetAddWaterReceiver.ACTION_ADD_WATER = "com.example.calories.widget.ACTION_ADD_WATER"`

- [ ] **Step 1: Write failing handler tests**

Create `WidgetWaterActionHandlerTest.kt`:

```kotlin
package com.example.calories.widget

import com.example.calories.data.repository.WaterRepository
import com.example.calories.model.WaterEntry
import com.example.calories.util.WaterDefaults
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWaterActionHandlerTest {

    private val waterRepository: WaterRepository = mockk(relaxed = true)
    private val widgetRefreshNotifier: WidgetRefreshNotifier = mockk(relaxed = true)
    private val supabase: SupabaseClient = mockk()
    private val auth: Auth = mockk()

    private val handler = WidgetWaterActionHandler(
        waterRepository = waterRepository,
        widgetRefreshNotifier = widgetRefreshNotifier,
        supabase = supabase,
    )

    @Test
    fun addWaterFromWidget_whenSignedOut_returnsFalse() = runTest {
        every { supabase.auth } returns auth
        every { auth.currentUserOrNull() } returns null

        val result = handler.addWaterFromWidget()

        assertFalse(result)
        coVerify(exactly = 0) { waterRepository.addWaterEntry(any(), any()) }
        verify(exactly = 0) { widgetRefreshNotifier.notifyDataChanged() }
    }

    @Test
    fun addWaterFromWidget_whenSignedIn_addsWaterAndRefreshes() = runTest {
        every { supabase.auth } returns auth
        every { auth.currentUserOrNull() } returns UserInfo(id = "user-1", aud = "", role = "")
        coEvery {
            waterRepository.addWaterEntry(WaterDefaults.STEP_ML, any())
        } returns WaterEntry("w1", "user-1", WaterDefaults.STEP_ML, "2026-07-23T10:00:00Z")

        val result = handler.addWaterFromWidget()

        assertTrue(result)
        coVerify(exactly = 1) {
            waterRepository.addWaterEntry(WaterDefaults.STEP_ML, any())
        }
        verify(exactly = 1) { widgetRefreshNotifier.notifyDataChanged() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.WidgetWaterActionHandlerTest"`

Expected: FAIL — unresolved `WidgetWaterActionHandler`

- [ ] **Step 3: Implement handler**

Create `WidgetWaterActionHandler.kt`:

```kotlin
package com.example.calories.widget

import com.example.calories.data.repository.WaterRepository
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.WaterDefaults
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetWaterActionHandler @Inject constructor(
    private val waterRepository: WaterRepository,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    private val supabase: SupabaseClient,
) {
    suspend fun addWaterFromWidget(): Boolean {
        if (supabase.auth.currentUserOrNull() == null) return false
        waterRepository.addWaterEntry(
            amountMl = WaterDefaults.STEP_ML,
            createdAt = DateTimeUtils.nowIso(),
        )
        widgetRefreshNotifier.notifyDataChanged()
        return true
    }
}
```

- [ ] **Step 4: Create broadcast receiver**

Create `WidgetAddWaterReceiver.kt`:

```kotlin
package com.example.calories.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetAddWaterReceiver : BroadcastReceiver() {

    @Inject lateinit var waterActionHandler: WidgetWaterActionHandler

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_WATER) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                waterActionHandler.addWaterFromWidget()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD_WATER = "com.example.calories.widget.ACTION_ADD_WATER"
    }
}
```

- [ ] **Step 5: Register receiver in manifest**

Inside `<application>`, after `CaloriesHomeWidgetProvider`:

```xml
<receiver
    android:name=".widget.WidgetAddWaterReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.example.calories.widget.ACTION_ADD_WATER" />
    </intent-filter>
</receiver>
```

- [ ] **Step 6: Run handler tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.WidgetWaterActionHandlerTest"`

Expected: PASS (2 tests)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/WidgetWaterActionHandler.kt app/src/test/java/com/example/calories/widget/WidgetWaterActionHandlerTest.kt app/src/main/java/com/example/calories/widget/WidgetAddWaterReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(widget): handle +250ml tap via broadcast receiver"
```

---

### Task 5: Renderer, updater, and pending-intent wiring

**Files:**
- Modify: `app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt`
- Modify: `app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt`
- Modify: `app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt`

**Interfaces:**
- Consumes: `CaloriesWidgetSnapshot` water fields, `WaterEntryDao.getTotalMlForDay`, `WidgetAddWaterReceiver.ACTION_ADD_WATER`
- Produces:
  - `CaloriesHomeWidgetProvider.createAddWaterPendingIntent(context): PendingIntent`
  - Updated `CaloriesWidgetUpdater.updateAll()` loading water + binding intents

- [ ] **Step 1: Add pending-intent factory to provider**

In `CaloriesHomeWidgetProvider.kt` companion object:

```kotlin
private const val REQUEST_ADD_WATER = 1

fun createAddWaterPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, WidgetAddWaterReceiver::class.java).apply {
        action = WidgetAddWaterReceiver.ACTION_ADD_WATER
    }
    return PendingIntent.getBroadcast(
        context,
        REQUEST_ADD_WATER,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

- [ ] **Step 2: Update renderer for water section**

In `CaloriesWidgetDisplayMode.SIGNED_OUT` and `NO_GOAL` branches, add:

```kotlin
views.setViewVisibility(R.id.sectionWater, View.GONE)
```

In `READY` branch, after calorie binding:

```kotlin
views.setViewVisibility(R.id.sectionWater, View.VISIBLE)
views.setTextViewText(
    R.id.tvWaterProgress,
    context.getString(
        R.string.water_progress_format,
        snapshot.waterIntakeMl,
        snapshot.waterGoalMl,
    ),
)
views.setProgressBar(R.id.progressWater, 100, snapshot.waterProgressPercent, false)
```

- [ ] **Step 3: Load water in updater**

Inject `WaterEntryDao` into `CaloriesWidgetUpdater`.

In the signed-in branch, after loading exercises:

```kotlin
val waterIntakeMl = waterEntryDao.getTotalMlForDay(userId, startOfDay, startOfTomorrow)
```

Pass `waterIntakeMl = waterIntakeMl` into `CaloriesWidgetSnapshotBuilder.build(...)`.

In the signed-out branch, pass `waterIntakeMl = 0`.

After `val remoteViews = CaloriesWidgetRenderer.render(context, snapshot)`:

```kotlin
val openIntent = CaloriesHomeWidgetProvider.createOpenAppPendingIntent(context)
val addWaterIntent = CaloriesHomeWidgetProvider.createAddWaterPendingIntent(context)
remoteViews.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
remoteViews.setOnClickPendingIntent(R.id.btnAddWater, addWaterIntent)
```

**Important:** Set `btnAddWater` intent **after** render, same as root. Do not set click on `sectionWater` container — only the button.

- [ ] **Step 4: Verify compile**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run widget unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest" --tests "com.example.calories.widget.WidgetWaterActionHandlerTest"`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt
git commit -m "feat(widget): render water and wire +250ml interactive button"
```

---

### Task 6: Widget refresh on in-app water changes + constant dedup

**Files:**
- Modify: `app/src/main/java/com/example/calories/data/repository/WaterRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt`

**Interfaces:**
- Consumes: `WidgetRefreshNotifier.notifyDataChanged()`, `WaterDefaults`

- [ ] **Step 1: Notify widget from water repository**

Add constructor param to `WaterRepositoryImpl`:

```kotlin
private val widgetRefreshNotifier: WidgetRefreshNotifier,
```

Call `widgetRefreshNotifier.notifyDataChanged()` at end of:
- `addWaterEntry` (after local upsert succeeds — before or after remote sync, either is fine)
- `removeLastForDate` (after successful delete)
- `deleteWaterEntry`
- `refresh`

- [ ] **Step 2: Deduplicate HomeViewModel constants**

In `HomeViewModel.kt`:
- Add `import com.example.calories.util.WaterDefaults`
- Replace `WATER_GOAL_ML` with `WaterDefaults.GOAL_ML`
- Replace `WATER_STEP_ML` with `WaterDefaults.STEP_ML`
- Remove `WATER_GOAL_ML` and `WATER_STEP_ML` from companion object

- [ ] **Step 3: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest" --tests "com.example.calories.widget.WidgetWaterActionHandlerTest" --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/data/repository/WaterRepositoryImpl.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
git commit -m "feat(widget): refresh on water changes and share water defaults"
```

---

### Task 7: Manual verification

**Files:** none (verification only)

- [ ] **Step 1: Install debug build**

Run: `.\gradlew.bat :app:installDebug`

Expected: INSTALL SUCCESS

- [ ] **Step 2: Add / refresh widget**

1. Long-press home screen → Widgets → Calories → add 4×2 widget (or remove/re-add if already placed).
2. Confirm layout shows calories **and** water row with `+ 250ml` button.

- [ ] **Step 3: Verify interactive add water**

1. Sign in with a user that has goals.
2. Note current water total on widget (e.g. `500 / 2000ml`).
3. Tap `+ 250ml` on the widget.
4. **App must not open.** Within ~1–2 seconds widget updates to `750 / 2000ml` and progress bar advances.
5. Open app → Home water card shows the same total.

- [ ] **Step 4: Verify non-interactive tap still works**

Tap widget background (not the button) → app opens to Progress tab (existing behavior).

- [ ] **Step 5: Verify in-app → widget sync**

Add 250ml from Home water card → widget updates to match without reopening app.

- [ ] **Step 6: Verify edge states**

1. Signed out → widget shows sign-in prompt; no water section.
2. Signed in, no goals → set-goals prompt; no water section.
3. Rapid taps on `+ 250ml` → each tap adds 250ml (no duplicate crash).

---

## Self-review

**Spec coverage**

| Requirement | Task |
|-------------|------|
| Keep 4×2 widget size | Task 3 (no size metadata change) |
| `+ 250ml` button on widget | Tasks 3, 5 |
| Log water without opening app | Tasks 4, 5 (`BroadcastReceiver` + broadcast `PendingIntent`) |
| Live widget update after tap | Tasks 4, 5, 6 |
| Water math matches Home | Tasks 1–2 (`WaterDefaults`, same percent formula) |
| EN + VI strings | Task 3 |
| Reuse existing water repository | Tasks 4, 6 |

**Placeholder scan:** No TBD/TODO placeholders.

**Type consistency:** `waterIntakeMl` / `waterGoalMl` / `waterProgressPercent` flow from DAO → `CaloriesWidgetSnapshotBuilder` → `CaloriesWidgetRenderer`. `WaterDefaults.STEP_ML` used in handler, Home, and button label string.

---

Plan complete and saved to `docs/superpowers/plans/2026-07-23-interactive-widget-water.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
