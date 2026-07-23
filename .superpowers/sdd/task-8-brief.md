### Task 8: Wire Home callout + navigate to Progress

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeFragment.kt`
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/example/calories/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine`, `InsightPreferences`, same food/weight/goal streams as Home already has
- Produces: `HomeUiState.activeCallout: ProgressInsight?`; `HomeNavEvent.OpenProgressInsights`; `MainActivity.openProgressTab()`

- [ ] **Step 1: Extend models**

In `HomeUiState` add:

```kotlin
    val activeCallout: ProgressInsight? = null,
```

In `HomeNavEvent` add:

```kotlin
    data object OpenProgressInsights : HomeNavEvent
```

- [ ] **Step 2: Inject `InsightPreferences` into `HomeViewModel` and compute callout**

When building `HomeUiState` (same place macros/goals are applied):
1. If `goal == null || dailyGoal <= 0` â†’ `activeCallout = null`
2. Else build `InsightEngineInput` from **all** observed foods/weights (not only selected day) â€” same aggregation as Progress
3. `val insights = ProgressInsightEngine.evaluate(...)`
4. `activeCallout = ProgressInsightEngine.selectHomeCallout(insights, insightPreferences.dismissedIds.value)`

Also combine `insightPreferences.dismissedIds` into the existing `uiState` combine so dismiss updates the banner.

Add:

```kotlin
fun dismissCallout() {
    val id = uiState.value.activeCallout?.id ?: return
    insightPreferences.dismiss(id)
}

fun onCalloutClicked() {
    viewModelScope.launch { _navEvents.send(HomeNavEvent.OpenProgressInsights) }
}
```

`uiState` is already a `StateFlow<HomeUiState>` â€” use `.value.activeCallout?.id` for dismiss.

- [ ] **Step 3: Layout â€” callout under date header, above calorie card**

In `fragment_home.xml` after the date/notifications row, before `sectionCalories`:

```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardInsightCallout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/home_header_gap"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorSurface"
            app:cardCornerRadius="16dp"
            app:cardElevation="0dp"
            app:strokeColor="@color/card_stroke"
            app:strokeWidth="1dp"
            tools:visibility="visible">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="12dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tvCalloutTitle"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_primary"
                        android:textStyle="bold"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/tvCalloutBody"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="2dp"
                        android:textColor="@color/text_secondary"
                        android:textSize="13sp"
                        android:maxLines="3" />
                </LinearLayout>

                <ImageButton
                    android:id="@+id/btnDismissCallout"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/insights_dismiss"
                    android:src="@android:drawable/ic_menu_close_clear_cancel"
                    app:tint="?attr/colorOnSurface" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: `HomeFragment` bind + click**

- Show card when `state.activeCallout != null`
- Set title/body via mapper
- `cardInsightCallout.setOnClickListener { viewModel.onCalloutClicked() }`
- `btnDismissCallout.setOnClickListener { viewModel.dismissCallout() }`
- Handle `HomeNavEvent.OpenProgressInsights` â†’ `(activity as? MainActivity)?.openProgressTab()`

- [ ] **Step 5: `MainActivity.openProgressTab()`**

```kotlin
fun openProgressTab() {
    supportFragmentManager.popBackStack(
        CAMERA_BACK_STACK,
        FragmentManager.POP_BACK_STACK_INCLUSIVE,
    )
    showTab(TAG_PROGRESS, updateBottomNav = true)
}
```

(`showTab` is private â€” keep `openProgressTab` in MainActivity calling it.)

- [ ] **Step 6: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt app/src/main/java/com/example/calories/ui/home/HomeFragment.kt app/src/main/res/layout/fragment_home.xml app/src/main/java/com/example/calories/ui/MainActivity.kt
git commit -m "feat(insights): add dismissible Home callout linked to Progress"
```

---

