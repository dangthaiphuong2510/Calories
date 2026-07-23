### Task 7: Wire Progress tab (ViewModel + layout + adapter + fragment)

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt`
- Modify: `app/src/main/res/layout/fragment_weight_tracking.xml`
- Create: `app/src/main/res/layout/item_progress_insight.xml`
- Create: `app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine.evaluate`, food/weight/goal flows already in VM, `CalorieCalculator.macroTargetsFor`
- Produces: `WeightUiState.insights: List<ProgressInsight>` (domain insights; fragment maps strings)

- [ ] **Step 1: Extend `WeightUiState`**

Add:

```kotlin
    val insights: List<ProgressInsight> = emptyList(),
    val hasGoals: Boolean = false,
```

- [ ] **Step 2: In `buildUiState` / combine path, compute insights when goal exists**

Helper (private in ViewModel or companion):

```kotlin
private fun buildInsights(
    foods: List<FoodEntry>,
    weights: List<WeightEntry>,
    dailyCalories: Int?,
    today: LocalDate = DateTimeUtils.today(),
): List<ProgressInsight> {
    if (dailyCalories == null || dailyCalories <= 0) return emptyList()
    val macros = CalorieCalculator.macroTargetsFor(dailyCalories)
    val foodDays = foods.mapNotNull { entry ->
        val date = DateTimeUtils.toLocalDate(entry.createdAt) ?: return@mapNotNull null
        InsightFoodDay(date, entry.calories, entry.protein)
    }
    val weightPoints = weights.mapNotNull { entry ->
        val date = DateTimeUtils.toLocalDate(entry.recordedAt) ?: return@mapNotNull null
        InsightWeightPoint(date, entry.weightKg)
    }
    return ProgressInsightEngine.evaluate(
        InsightEngineInput(
            today = today,
            dailyCalorieTarget = dailyCalories,
            proteinTargetGrams = macros.proteinGrams,
            foodDays = foodDays,
            weights = weightPoints,
        ),
    )
}
```

Pass `insights` and `hasGoals = source.goal exists / dailyCalories > 0` into `WeightUiState`. Confirm `DateTimeUtils.toLocalDate` exists (used already in this ViewModel); if only `isSameDay` exists, use the same parse pattern as `buildCalorieTrend`.

- [ ] **Step 3: Add insights section to `fragment_weight_tracking.xml`**

Insert **immediately after** the title `progress_dashboard_title` TextView and **before** the â€œWeight progressâ€ section:

```xml
            <LinearLayout
                android:id="@+id/sectionInsights"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:orientation="vertical"
                android:visibility="gone"
                tools:visibility="visible">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/insights_section_title"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rvInsights"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:nestedScrollingEnabled="false"
                    tools:itemCount="2"
                    tools:listitem="@layout/item_progress_insight" />
            </LinearLayout>
```

- [ ] **Step 4: Create `item_progress_insight.xml`**

Match existing Progress MaterialCard stroke/radius (16dp, `card_stroke`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeColor="@color/card_stroke"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/tvInsightTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold"
            tools:text="Scale not moving" />

        <TextView
            android:id="@+id/tvInsightBody"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="@color/text_secondary"
            android:textSize="14sp"
            tools:text="Body copy" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 5: Adapter + fragment binding**

`ProgressInsightAdapter` binds title/body via `ProgressInsightUiMapper` and `formatArgs` (`getString(bodyRes, *args.toTypedArray())` when args non-empty).

In `WeightTrackingFragment`:
- `sectionInsights.isVisible = state.hasGoals && state.insights.isNotEmpty()`
- On insight click: if `action == OpenWeightLog` â†’ existing `showLogWeightDialog()`; else no-op (already on Progress)

- [ ] **Step 6: Build/compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt app/src/main/res/layout/fragment_weight_tracking.xml app/src/main/res/layout/item_progress_insight.xml
git commit -m "feat(insights): show weekly insights on Progress tab"
```

---

