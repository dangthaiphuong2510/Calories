# Home Screen Widget (4×2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (user chose **Inline Execution**). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a 4×2 Android home-screen widget that shows today’s calorie progress (goal / consumed / remaining + progress bar) and the highest-priority non-dismissed Progress Insight callout, reusing existing engine logic.

**Architecture:** A pure-Kotlin `CaloriesWidgetSnapshotBuilder` mirrors `HomeUiState` calorie math and calls `ProgressInsightEngine.selectHomeCallout` (same selection rules as the Home banner). `CaloriesWidgetUpdater` loads Room data via existing DAOs, builds a snapshot, and pushes `RemoteViews`. `CaloriesHomeWidgetProvider` handles system lifecycle; `WidgetRefreshNotifier` triggers immediate refresh after food/exercise/goal changes, boot, and data wipe.

**Tech Stack:** Kotlin, Hilt `@Inject`/`@Singleton`, Room DAOs, `AppWidgetProvider` + `RemoteViews`, JUnit unit tests, existing `ProgressInsightEngine` / `ProgressInsightUiMapper`, `SharedPreferences` via `InsightPreferences`.

## Global Constraints

- Package root: `com.example.calories`
- Widget size: **4×2 cells** (`minWidth=250dp`, `minHeight=110dp`, `targetCellWidth=4`, `targetCellHeight=2`)
- Calorie math must match `HomeUiState` (`caloriesRemaining = (dailyGoal - totalEaten + totalBurned).coerceAtLeast(0)`, `calorieProgressPercent` from `totalEaten / dailyGoal`)
- Insight selection: `ProgressInsightEngine.selectHomeCallout(insights, dismissedIds)` — excludes dismissed ids; prefers ACTIONABLE insights; falls back to `logging_gap` only when no ACTIONABLE insight remains (never shows POSITIVE insights on widget, same as Home)
- Reuse `ProgressInsightInputBuilder.build` + `ProgressInsightUiMapper` for insight copy — no duplicated rule logic
- All new user-facing strings in `values/strings.xml` **and** `values-vi/strings.xml`
- Widget shows **today only** (not the date Home may be browsing)
- No new Room tables; read current Room snapshot only
- Run unit tests with: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`
- YAGNI: no Glance library, no WorkManager, no widget dismiss button (dismiss stays in-app)

## File structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt` | Snapshot DTO + pure builder |
| `app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt` | Builder unit tests |
| `app/src/main/res/layout/widget_calories_home.xml` | 4×2 RemoteViews layout |
| `app/src/main/res/xml/calories_home_widget_info.xml` | `appwidget-provider` metadata |
| `app/src/main/res/drawable/widget_progress_bar.xml` | Layer-list progress drawable |
| `app/src/main/res/drawable/widget_background.xml` | Rounded widget background |
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt` | Snapshot → RemoteViews |
| `app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt` | Load DAO data, render, update widgets |
| `app/src/main/java/com/example/calories/widget/WidgetRefreshNotifier.kt` | Fire-and-forget refresh entry point |
| `app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt` | `AppWidgetProvider` |
| Modify: `AndroidManifest.xml` | Register provider + receiver |
| Modify: `MainActivity.kt` | Handle widget tap deep link |
| Modify: `FoodRepositoryImpl.kt`, `ExerciseRepositoryImpl.kt`, `UserGoalsRepositoryImpl.kt`, `WeightRepositoryImpl.kt` | Notify widget on data change |
| Modify: `BootCompletedReceiver.kt`, `LocalDataWiper.kt` | Refresh widget on boot / wipe |
| Modify: `values/strings.xml`, `values-vi/strings.xml` | Widget copy |

---

### Task 1: Widget snapshot model + pure builder

**Files:**
- Create: `app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt`
- Test: `app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine.selectHomeCallout`, `ProgressInsightInputBuilder.build`
- Produces:
  - `CaloriesWidgetSnapshot`
  - `CaloriesWidgetSnapshotBuilder.build(...): CaloriesWidgetSnapshot`

- [ ] **Step 1: Write the failing tests**

Create `CaloriesWidgetSnapshotBuilderTest.kt`:

```kotlin
package com.example.calories.widget

import com.example.calories.insights.ProgressInsightIds
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.UserGoal
import com.example.calories.model.WeightEntry
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import com.example.calories.model.enums.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CaloriesWidgetSnapshotBuilderTest {

    private val today = LocalDate.of(2026, 7, 22)
    private val userId = "user-1"

    private fun goal(dailyCalories: Int = 2000) = UserGoal(
        id = "g1",
        userId = userId,
        targetWeight = 70.0,
        currentWeight = 80.0,
        age = 30,
        gender = Gender.MALE,
        heightCm = 175.0,
        activityLevel = ActivityLevel.MODERATE,
        goalType = GoalType.LOSE_WEIGHT,
        tdee = 2200,
        dailyCalories = dailyCalories,
    )

    private fun food(calories: Int, date: LocalDate = today) = FoodEntry(
        id = "f-$calories-${date}",
        userId = userId,
        name = "Food",
        calories = calories,
        protein = 20.0,
        carb = 10.0,
        fat = 5.0,
        mealType = MealType.LUNCH,
        servingGrams = 100.0,
        createdAt = date.atTime(12, 0).toString(),
    )

    @Test
    fun signedOut_returnsSignedOutSnapshot() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = false,
            goal = null,
            todayFoods = emptyList(),
            allFoods = emptyList(),
            weights = emptyList(),
            todayExercises = emptyList(),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(CaloriesWidgetDisplayMode.SIGNED_OUT, snapshot.displayMode)
    }

    @Test
    fun calorieMath_matchesHomeUiState() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = goal(),
            todayFoods = listOf(food(1200)),
            allFoods = listOf(food(1200)),
            weights = emptyList(),
            todayExercises = listOf(
                ExerciseEntry(
                    id = "e1",
                    userId = userId,
                    name = "Run",
                    caloriesBurned = 230.0,
                    durationMinutes = 30,
                    createdAt = today.atTime(8, 0).toString(),
                ),
            ),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(2000, snapshot.dailyGoal)
        assertEquals(1200, snapshot.totalEaten)
        assertEquals(230, snapshot.totalBurned)
        assertEquals(1030, snapshot.caloriesRemaining)
        assertEquals(60, snapshot.progressPercent)
    }

    @Test
    fun dismissedActionableInsight_isSkipped() {
        val foods = (0 until 7).map { offset ->
            food(1500, today.minusDays(offset.toLong()))
        }
        val weights = listOf(
            WeightEntry("w1", userId, 80.0, today.minusDays(10).atStartOfDay().toString()),
            WeightEntry("w2", userId, 80.1, today.atStartOfDay().toString()),
        )
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = goal(),
            todayFoods = foods.filter { it.createdAt.startsWith(today.toString()) },
            allFoods = foods,
            weights = weights,
            todayExercises = emptyList(),
            dismissedIds = setOf(ProgressInsightIds.PLATEAU_UNDER_TARGET),
            today = today,
        )
        assertEquals(ProgressInsightIds.WEEKEND_CALORIE_SPIKE, snapshot.activeInsight?.id)
    }

    @Test
    fun noGoal_hidesInsightSection() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = null,
            todayFoods = listOf(food(500)),
            allFoods = listOf(food(500)),
            weights = emptyList(),
            todayExercises = emptyList(),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(CaloriesWidgetDisplayMode.NO_GOAL, snapshot.displayMode)
        assertNull(snapshot.activeInsight)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest"`

Expected: FAIL with unresolved reference `CaloriesWidgetSnapshotBuilder`

- [ ] **Step 3: Write minimal implementation**

Create `CaloriesWidgetSnapshot.kt`:

```kotlin
package com.example.calories.widget

import com.example.calories.insights.ProgressInsight
import com.example.calories.insights.ProgressInsightInputBuilder
import com.example.calories.insights.ProgressInsightEngine
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.UserGoal
import com.example.calories.model.WeightEntry
import java.time.LocalDate
import kotlin.math.roundToInt

enum class CaloriesWidgetDisplayMode {
    SIGNED_OUT,
    NO_GOAL,
    READY,
}

data class CaloriesWidgetSnapshot(
    val displayMode: CaloriesWidgetDisplayMode,
    val dailyGoal: Int = 0,
    val totalEaten: Int = 0,
    val totalBurned: Int = 0,
    val caloriesRemaining: Int = 0,
    val progressPercent: Int = 0,
    val activeInsight: ProgressInsight? = null,
)

object CaloriesWidgetSnapshotBuilder {

    fun build(
        isSignedIn: Boolean,
        goal: UserGoal?,
        todayFoods: List<FoodEntry>,
        allFoods: List<FoodEntry>,
        weights: List<WeightEntry>,
        todayExercises: List<ExerciseEntry>,
        dismissedIds: Set<String>,
        today: LocalDate,
    ): CaloriesWidgetSnapshot {
        if (!isSignedIn) {
            return CaloriesWidgetSnapshot(displayMode = CaloriesWidgetDisplayMode.SIGNED_OUT)
        }

        val dailyGoal = goal?.dailyCalories ?: 0
        val totalEaten = todayFoods.sumOf { it.calories }
        val totalBurned = todayExercises.sumOf { it.caloriesBurned.roundToInt() }
        val caloriesRemaining = (dailyGoal - totalEaten + totalBurned).coerceAtLeast(0)
        val progressPercent = if (dailyGoal <= 0) {
            0
        } else {
            ((totalEaten.toFloat() / dailyGoal) * 100f).toInt().coerceIn(0, 100)
        }

        val activeInsight = if (goal == null || dailyGoal <= 0) {
            null
        } else {
            val insights = ProgressInsightInputBuilder.build(
                foods = allFoods,
                weights = weights,
                dailyCalories = dailyGoal,
                today = today,
            )
            ProgressInsightEngine.selectHomeCallout(insights, dismissedIds)
        }

        val displayMode = when {
            dailyGoal <= 0 -> CaloriesWidgetDisplayMode.NO_GOAL
            else -> CaloriesWidgetDisplayMode.READY
        }

        return CaloriesWidgetSnapshot(
            displayMode = displayMode,
            dailyGoal = dailyGoal,
            totalEaten = totalEaten,
            totalBurned = totalBurned,
            caloriesRemaining = caloriesRemaining,
            progressPercent = progressPercent,
            activeInsight = activeInsight,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest"`

Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/CaloriesWidgetSnapshot.kt app/src/test/java/com/example/calories/widget/CaloriesWidgetSnapshotBuilderTest.kt
git commit -m "feat(widget): add pure snapshot builder for home widget"
```

---

### Task 2: Widget layout + provider metadata + strings

**Files:**
- Create: `app/src/main/res/layout/widget_calories_home.xml`
- Create: `app/src/main/res/xml/calories_home_widget_info.xml`
- Create: `app/src/main/res/drawable/widget_progress_bar.xml`
- Create: `app/src/main/res/drawable/widget_background.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`

**Interfaces:**
- Consumes: existing `calorie_goal`, `calories_eaten`, `calories_remaining`, `kcal_unit` strings
- Produces: layout ids used by renderer in Task 3 (`widgetRoot`, `tvRemaining`, `tvGoal`, `tvEaten`, `progressCalories`, `sectionInsight`, `tvInsightTitle`, `tvInsightBody`, `tvWidgetMessage`)

- [ ] **Step 1: Add EN + VI strings**

In `values/strings.xml` add:

```xml
<string name="widget_calories_name">Calories</string>
<string name="widget_calories_description">Today\'s calorie progress and weekly insight</string>
<string name="widget_sign_in_prompt">Sign in to track calories</string>
<string name="widget_no_goal_prompt">Set your goals in the app</string>
<string name="widget_tap_to_open">Tap to open Calories</string>
```

In `values-vi/strings.xml` add:

```xml
<string name="widget_calories_name">Calories</string>
<string name="widget_calories_description">Tiến độ calo hôm nay và nhận xét tuần</string>
<string name="widget_sign_in_prompt">Đăng nhập để theo dõi calo</string>
<string name="widget_no_goal_prompt">Đặt mục tiêu trong ứng dụng</string>
<string name="widget_tap_to_open">Chạm để mở Calories</string>
```

- [ ] **Step 2: Create drawables**

`widget_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/surface" />
    <corners android:radius="16dp" />
    <stroke
        android:width="1dp"
        android:color="@color/card_stroke" />
</shape>
```

`widget_progress_bar.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="6dp" />
            <solid android:color="@color/primary_light" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="6dp" />
                <solid android:color="@color/primary" />
            </shape>
        </clip>
    </item>
</layer-list>
```

- [ ] **Step 3: Create widget layout**

`widget_calories_home.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widgetRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/tvWidgetMessage"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="@string/widget_sign_in_prompt"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        android:visibility="gone" />

    <LinearLayout
        android:id="@+id/sectionProgress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvRemaining"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/primary"
            android:textSize="28sp"
            android:textStyle="bold"
            android:text="0" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/calories_remaining"
            android:textColor="@color/text_secondary"
            android:textSize="12sp" />

        <ProgressBar
            android:id="@+id/progressCalories"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="8dp"
            android:layout_marginTop="8dp"
            android:max="100"
            android:progress="0"
            android:progressDrawable="@drawable/widget_progress_bar" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="horizontal"
            android:weightSum="2">

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/calorie_goal"
                    android:textColor="@color/text_secondary"
                    android:textSize="11sp" />

                <TextView
                    android:id="@+id/tvGoal"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    android:text="0" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/calories_eaten"
                    android:textColor="@color/text_secondary"
                    android:textSize="11sp" />

                <TextView
                    android:id="@+id/tvEaten"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    android:text="0" />
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/sectionInsight"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvInsightTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="@color/text_primary"
            android:textSize="13sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvInsightBody"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/text_secondary"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 4: Create appwidget-provider**

`calories_home_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_calories_description"
    android:initialLayout="@layout/widget_calories_home"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:minResizeWidth="250dp"
    android:minResizeHeight="110dp"
    android:previewLayout="@layout/widget_calories_home"
    android:resizeMode="none"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

- [ ] **Step 5: Verify resources compile**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/widget_calories_home.xml app/src/main/res/xml/calories_home_widget_info.xml app/src/main/res/drawable/widget_progress_bar.xml app/src/main/res/drawable/widget_background.xml app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(widget): add 4x2 layout and provider metadata"
```

---

### Task 3: Widget renderer + data updater

**Files:**
- Create: `app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt`
- Create: `app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt`
- Create: `app/src/main/java/com/example/calories/widget/WidgetRefreshNotifier.kt`

**Interfaces:**
- Consumes: `CaloriesWidgetSnapshot`, `CaloriesWidgetSnapshotBuilder.build`
- Produces:
  - `CaloriesWidgetRenderer.render(context, snapshot): RemoteViews`
  - `CaloriesWidgetUpdater.updateAll()`
  - `CaloriesWidgetUpdater.scheduleRefresh()`
  - `WidgetRefreshNotifier.notifyDataChanged()`

- [ ] **Step 1: Create renderer**

`CaloriesWidgetRenderer.kt`:

```kotlin
package com.example.calories.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.calories.R
import com.example.calories.insights.ProgressInsightUiMapper

object CaloriesWidgetRenderer {

    fun render(context: Context, snapshot: CaloriesWidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calories_home)

        when (snapshot.displayMode) {
            CaloriesWidgetDisplayMode.SIGNED_OUT -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.VISIBLE)
                views.setViewVisibility(R.id.sectionProgress, View.GONE)
                views.setViewVisibility(R.id.sectionInsight, View.GONE)
                views.setTextViewText(R.id.tvWidgetMessage, context.getString(R.string.widget_sign_in_prompt))
            }
            CaloriesWidgetDisplayMode.NO_GOAL -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.VISIBLE)
                views.setViewVisibility(R.id.sectionProgress, View.GONE)
                views.setViewVisibility(R.id.sectionInsight, View.GONE)
                views.setTextViewText(R.id.tvWidgetMessage, context.getString(R.string.widget_no_goal_prompt))
            }
            CaloriesWidgetDisplayMode.READY -> {
                views.setViewVisibility(R.id.tvWidgetMessage, View.GONE)
                views.setViewVisibility(R.id.sectionProgress, View.VISIBLE)

                views.setTextViewText(R.id.tvRemaining, snapshot.caloriesRemaining.toString())
                views.setTextViewText(R.id.tvGoal, snapshot.dailyGoal.toString())
                views.setTextViewText(R.id.tvEaten, snapshot.totalEaten.toString())
                views.setProgressBar(R.id.progressCalories, 100, snapshot.progressPercent, false)

                val insight = snapshot.activeInsight
                if (insight == null) {
                    views.setViewVisibility(R.id.sectionInsight, View.GONE)
                } else {
                    views.setViewVisibility(R.id.sectionInsight, View.VISIBLE)
                    views.setTextViewText(
                        R.id.tvInsightTitle,
                        context.getString(ProgressInsightUiMapper.titleRes(insight.id)),
                    )
                    val bodyRes = ProgressInsightUiMapper.bodyRes(insight.id)
                    val body = if (insight.formatArgs.isEmpty()) {
                        context.getString(bodyRes)
                    } else {
                        context.getString(bodyRes, *insight.formatArgs.toTypedArray())
                    }
                    views.setTextViewText(R.id.tvInsightBody, body)
                }
            }
        }

        return views
    }
}
```

- [ ] **Step 2: Create updater**

`CaloriesWidgetUpdater.kt`:

```kotlin
package com.example.calories.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.preferences.InsightPreferences
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaloriesWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val foodEntryDao: FoodEntryDao,
    private val userGoalDao: UserGoalDao,
    private val weightEntryDao: WeightEntryDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val insightPreferences: InsightPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun scheduleRefresh() {
        scope.launch { updateAll() }
    }

    suspend fun updateAll() = withContext(Dispatchers.IO) {
        val userId = supabase.auth.currentUserOrNull()?.id
        val today = DateTimeUtils.today()
        val (startOfDay, startOfTomorrow) = DateTimeUtils.dayRange(today)

        val snapshot = if (userId == null) {
            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = false,
                goal = null,
                todayFoods = emptyList(),
                allFoods = emptyList(),
                weights = emptyList(),
                todayExercises = emptyList(),
                dismissedIds = emptySet(),
                today = today,
            )
        } else {
            insightPreferences.ensureCurrentWeek()
            val goal = userGoalDao.getForUser(userId)?.toDomain()
            val todayFoods = foodEntryDao.getForDay(userId, startOfDay, startOfTomorrow)
                .map { it.toDomain() }
            val allFoods = foodEntryDao.getAll(userId).map { it.toDomain() }
            val weights = weightEntryDao.getAll(userId).map { it.toDomain() }
            val todayExercises = exerciseEntryDao.observeAll(userId)
                .kotlinx.coroutines.flow.first()
                .map { it.toDomain() }
                .filter { DateTimeUtils.isSameDay(it.createdAt, today) }

            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = true,
                goal = goal,
                todayFoods = todayFoods,
                allFoods = allFoods,
                weights = weights,
                todayExercises = todayExercises,
                dismissedIds = insightPreferences.dismissedIds.value,
                today = today,
            )
        }

        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CaloriesHomeWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return@withContext

        val remoteViews = CaloriesWidgetRenderer.render(context, snapshot)
        val openIntent = CaloriesHomeWidgetProvider.createOpenAppPendingIntent(context)
        ids.forEach { id ->
            remoteViews.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
            manager.updateAppWidget(id, remoteViews)
        }
    }
}
```

**Fix before committing Task 3:** replace the invalid `observeAll(...).kotlinx.coroutines.flow.first()` line with a proper import and suspend DAO read. Add to `ExerciseEntryDao`:

```kotlin
@Query("SELECT * FROM exercise_entries WHERE userId = :userId ORDER BY createdAt DESC")
suspend fun getAll(userId: String): List<ExerciseEntryEntity>
```

Then in updater use:

```kotlin
val todayExercises = exerciseEntryDao.getAll(userId)
    .map { it.toDomain() }
    .filter { DateTimeUtils.isSameDay(it.createdAt, today) }
```

- [ ] **Step 3: Create refresh notifier**

`WidgetRefreshNotifier.kt`:

```kotlin
package com.example.calories.widget

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefreshNotifier @Inject constructor(
    private val widgetUpdater: CaloriesWidgetUpdater,
) {
    fun notifyDataChanged() {
        widgetUpdater.scheduleRefresh()
    }
}
```

- [ ] **Step 4: Add ExerciseEntryDao.getAll**

In `ExerciseEntryDao.kt` add the suspend `getAll` query shown above.

- [ ] **Step 5: Verify compile**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL (provider class referenced but not yet created — temporarily comment provider reference or complete Task 4 first; recommended order: finish Task 4 Step 1 before Step 5 here)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/CaloriesWidgetRenderer.kt app/src/main/java/com/example/calories/widget/CaloriesWidgetUpdater.kt app/src/main/java/com/example/calories/widget/WidgetRefreshNotifier.kt app/src/main/java/com/example/calories/data/local/dao/ExerciseEntryDao.kt
git commit -m "feat(widget): add renderer and Room-backed updater"
```

---

### Task 4: AppWidgetProvider + manifest registration

**Files:**
- Create: `app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `CaloriesWidgetUpdater`
- Produces:
  - `CaloriesHomeWidgetProvider` (system entry point)
  - `CaloriesHomeWidgetProvider.createOpenAppPendingIntent(context): PendingIntent`
  - Intent extra: `CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS = "extra_open_progress"`

- [ ] **Step 1: Create provider**

`CaloriesHomeWidgetProvider.kt`:

```kotlin
package com.example.calories.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.calories.R
import com.example.calories.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CaloriesHomeWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetUpdater: CaloriesWidgetUpdater

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        widgetUpdater.scheduleRefresh()
    }

    override fun onEnabled(context: Context) {
        widgetUpdater.scheduleRefresh()
    }

    companion object {
        const val EXTRA_OPEN_PROGRESS = "extra_open_progress"

        fun createOpenAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_PROGRESS, true)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
```

- [ ] **Step 2: Register in manifest**

Inside `<application>` in `AndroidManifest.xml`, after `BootCompletedReceiver`:

```xml
<receiver
    android:name=".widget.CaloriesHomeWidgetProvider"
    android:exported="true"
    android:label="@string/widget_calories_name">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/calories_home_widget_info" />
</receiver>
```

- [ ] **Step 3: Verify build**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/CaloriesHomeWidgetProvider.kt app/src/main/AndroidManifest.xml
git commit -m "feat(widget): register 4x2 home screen widget provider"
```

---

### Task 5: Widget tap opens Progress tab

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS`, existing `openProgressTab()`

- [ ] **Step 1: Handle intent extra in MainActivity**

In `onCreate`, after `setContentView` / binding setup, add:

```kotlin
handleWidgetIntent(intent)
```

Add method + override:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleWidgetIntent(intent)
}

private fun handleWidgetIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(
            com.example.calories.widget.CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS,
            false,
        ) == true
    ) {
        intent.removeExtra(com.example.calories.widget.CaloriesHomeWidgetProvider.EXTRA_OPEN_PROGRESS)
        openProgressTab()
    }
}
```

Call `handleWidgetIntent(intent)` at end of `onCreate`.

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/MainActivity.kt
git commit -m "feat(widget): deep link widget tap to Progress tab"
```

---

### Task 6: Refresh hooks on data changes, boot, and wipe

**Files:**
- Modify: `app/src/main/java/com/example/calories/data/repository/FoodRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/data/repository/ExerciseRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/data/repository/UserGoalsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/data/repository/WeightRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt`
- Modify: `app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt`
- Modify: `app/src/main/java/com/example/calories/notifications/BootCompletedReceiver.kt`

**Interfaces:**
- Consumes: `WidgetRefreshNotifier.notifyDataChanged()`

- [ ] **Step 1: Inject notifier into repositories**

Add constructor param to each impl:

```kotlin
private val widgetRefreshNotifier: WidgetRefreshNotifier,
```

Call `widgetRefreshNotifier.notifyDataChanged()` at end of:
- `FoodRepositoryImpl`: `addFoodEntry`, `updateFoodEntry`, `deleteFoodEntry`, `refresh` (after sync)
- `ExerciseRepositoryImpl`: `addExerciseEntry`, `deleteExerciseEntry`, `refresh`
- `UserGoalsRepositoryImpl`: `saveGoal`, `refresh`
- `WeightRepositoryImpl`: `upsertWeightForDate`, `deleteWeightEntry`, `refresh`

Keep calls non-blocking (notifier already launches coroutine).

- [ ] **Step 2: Refresh widget when insight dismissed**

In `InsightPreferences`, inject `WidgetRefreshNotifier` and call `notifyDataChanged()` at end of `dismiss()`.

Because `InsightPreferences` currently only takes `Context`, change to:

```kotlin
@Singleton
class InsightPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
) {
```

Add `widgetRefreshNotifier.notifyDataChanged()` in `dismiss()`.

**Circular dependency risk:** `WidgetRefreshNotifier` → `CaloriesWidgetUpdater` → `InsightPreferences` → `WidgetRefreshNotifier`.

**Fix:** Do **not** inject notifier into `InsightPreferences`. Instead, call refresh only from `HomeViewModel.dismissCallout()` after `insightPreferences.dismiss(id)`. Document this in implementation.

- [ ] **Step 3: Boot + wipe hooks**

In `BootCompletedReceiver.onReceive`, after `ReminderScheduler.syncAll`:

```kotlin
WidgetRefreshBridge.refresh(appContext)
```

Create `WidgetRefreshBridge.kt` for non-Hilt receiver access:

```kotlin
package com.example.calories.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRefreshEntryPoint {
    fun widgetRefreshNotifier(): WidgetRefreshNotifier
}

object WidgetRefreshBridge {
    fun refresh(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRefreshEntryPoint::class.java,
        )
        entryPoint.widgetRefreshNotifier().notifyDataChanged()
    }
}
```

Use `WidgetRefreshBridge.refresh(appContext)` from `BootCompletedReceiver`.

In `LocalDataWiper.wipeAll()`, after `insightPreferences.clear()`:

```kotlin
widgetRefreshNotifier.notifyDataChanged()
```

Inject `WidgetRefreshNotifier` into `LocalDataWiper`.

In `HomeViewModel.dismissCallout()`:

```kotlin
fun dismissCallout() {
    val id = uiState.value.activeCallout?.id ?: return
    insightPreferences.dismiss(id)
    widgetRefreshNotifier.notifyDataChanged()
}
```

Inject `WidgetRefreshNotifier` into `HomeViewModel`.

- [ ] **Step 4: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.widget.CaloriesWidgetSnapshotBuilderTest" --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/widget/WidgetRefreshBridge.kt app/src/main/java/com/example/calories/data/repository/FoodRepositoryImpl.kt app/src/main/java/com/example/calories/data/repository/ExerciseRepositoryImpl.kt app/src/main/java/com/example/calories/data/repository/UserGoalsRepositoryImpl.kt app/src/main/java/com/example/calories/data/repository/WeightRepositoryImpl.kt app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt app/src/main/java/com/example/calories/notifications/BootCompletedReceiver.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
git commit -m "feat(widget): refresh on data changes, boot, and dismiss"
```

---

### Task 7: Manual verification

**Files:** none (verification only)

- [ ] **Step 1: Install debug build**

Run: `.\gradlew.bat :app:installDebug`

Expected: INSTALL SUCCESS

- [ ] **Step 2: Add widget to home screen**

1. Long-press home screen → Widgets → Calories → add 4×2 widget.
2. Confirm layout shows Remaining number, horizontal progress bar, Goal + Eaten row.

- [ ] **Step 3: Verify calorie progress**

1. Sign in with a user that has a daily calorie goal.
2. Log food for today.
3. Widget should update within a few seconds (or remove/re-add widget).
4. Numbers should match Home tab for **today** (goal, eaten, remaining).

- [ ] **Step 4: Verify insight callout**

1. Seed ≥7 days of food logs so an ACTIONABLE insight fires (see `ProgressInsightEngineTest` fixtures).
2. Widget bottom section shows insight title + body matching Home callout.
3. Dismiss callout on Home → widget insight section hides on next refresh.
4. Widget should **not** show POSITIVE-only insights (`on_track_streak`) when no ACTIONABLE insight is active.

- [ ] **Step 5: Verify tap action**

Tap widget → app opens to Progress tab.

- [ ] **Step 6: Verify edge states**

1. Signed out → widget shows sign-in prompt.
2. Signed in, no goals → widget shows set-goals prompt.
3. Reboot device → widget still shows current data after boot.

---

## Self-review

**Spec coverage**

| Requirement | Task |
|-------------|------|
| 4×2 widget size | Task 2 (`calories_home_widget_info.xml`) |
| Daily calorie progress (goal / consumed / remaining + visual) | Tasks 1–3 |
| Highest-priority insight excluding dismissed | Task 1 (`selectHomeCallout`) |
| Reuse ProgressInsightEngine | Task 1 |
| EN + VI strings | Task 2 |
| Tap opens app | Tasks 4–5 |

**Placeholder scan:** No TBD/TODO placeholders.

**Type consistency:** `CaloriesWidgetSnapshot` fields align with `HomeUiState` computed properties; insight type is `ProgressInsight?` throughout.

---

Plan complete and saved to `docs/superpowers/plans/2026-07-22-home-widget.md`.

**Execution:** You chose **Inline Execution** — implement task-by-task in this session using `superpowers:executing-plans` with checkpoints after each task.
