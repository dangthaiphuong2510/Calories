# Review package Task 5
BASE: 2658622bab7bcd23e600fbd866a897b4a6379e49
HEAD: ffd296a970a32afe1c614dd52f18516adb54c0dc
## Commits
ffd296a feat(insights): add week keys and dismiss preferences

## Diff stat
 .../data/preferences/InsightPreferences.kt         | 60 ++++++++++++++++++++++
 .../calories/data/preferences/LocalDataWiper.kt    |  4 ++
 .../example/calories/insights/InsightWeekKeys.kt   | 13 +++++
 .../calories/insights/InsightWeekKeysTest.kt       | 13 +++++
 4 files changed, 90 insertions(+)

## Full diff
diff --git a/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt b/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt
new file mode 100644
index 0000000..6baf02a
--- /dev/null
+++ b/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt
@@ -0,0 +1,60 @@
+package com.example.calories.data.preferences
+
+import android.content.Context
+import android.content.SharedPreferences
+import com.example.calories.insights.InsightWeekKeys
+import com.example.calories.util.DateTimeUtils
+import dagger.hilt.android.qualifiers.ApplicationContext
+import kotlinx.coroutines.flow.MutableStateFlow
+import kotlinx.coroutines.flow.StateFlow
+import kotlinx.coroutines.flow.asStateFlow
+import javax.inject.Inject
+import javax.inject.Singleton
+
+@Singleton
+class InsightPreferences @Inject constructor(
+    @ApplicationContext context: Context,
+) {
+    private val prefs: SharedPreferences =
+        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
+
+    private val _dismissedIds = MutableStateFlow(readDismissedForCurrentWeek())
+    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()
+
+    fun dismiss(insightId: String) {
+        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
+        val storedWeek = prefs.getString(KEY_WEEK, null)
+        val current = if (storedWeek == week) {
+            prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
+        } else {
+            mutableSetOf()
+        }
+        current += insightId
+        prefs.edit()
+            .putString(KEY_WEEK, week)
+            .putStringSet(KEY_IDS, current)
+            .apply()
+        _dismissedIds.value = current.toSet()
+    }
+
+    fun clear() {
+        prefs.edit().clear().apply()
+        _dismissedIds.value = emptySet()
+    }
+
+    private fun readDismissedForCurrentWeek(): Set<String> {
+        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
+        val storedWeek = prefs.getString(KEY_WEEK, null)
+        if (storedWeek != week) {
+            prefs.edit().clear().apply()
+            return emptySet()
+        }
+        return prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
+    }
+
+    companion object {
+        const val PREFS_NAME = "insight_prefs"
+        private const val KEY_WEEK = "week_key"
+        private const val KEY_IDS = "dismissed_ids"
+    }
+}
diff --git a/app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt b/app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt
index ee14247..807151b 100644
--- a/app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt
+++ b/app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt
@@ -19,20 +19,24 @@ class LocalDataWiper @Inject constructor(
     private val reminderScheduler: ReminderScheduler,
 ) {
     suspend fun wipeAll() = withContext(Dispatchers.IO) {
         database.clearAllTables()
         appPreferences.clear()
         authDataStore.clearLoginState()
         context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE)
             .edit()
             .clear()
             .apply()
+        context.getSharedPreferences(InsightPreferences.PREFS_NAME, Context.MODE_PRIVATE)
+            .edit()
+            .clear()
+            .apply()
         context.getSharedPreferences(EXERCISE_PREFS_NAME, Context.MODE_PRIVATE)
             .edit()
             .clear()
             .apply()
         cancelAllReminders()
     }
 
     private fun cancelAllReminders() {
         reminderScheduler.cancelAlarm(ReminderIds.MEAL_BREAKFAST)
         reminderScheduler.cancelAlarm(ReminderIds.MEAL_LUNCH)
diff --git a/app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt b/app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt
new file mode 100644
index 0000000..749b212
--- /dev/null
+++ b/app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt
@@ -0,0 +1,13 @@
+package com.example.calories.insights
+
+import java.time.LocalDate
+import java.time.temporal.WeekFields
+
+object InsightWeekKeys {
+    fun isoWeekKey(date: LocalDate): String {
+        val weekFields = WeekFields.ISO
+        val week = date.get(weekFields.weekOfWeekBasedYear())
+        val year = date.get(weekFields.weekBasedYear())
+        return "%d-W%02d".format(year, week)
+    }
+}
diff --git a/app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt b/app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt
new file mode 100644
index 0000000..84d744b
--- /dev/null
+++ b/app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt
@@ -0,0 +1,13 @@
+package com.example.calories.insights
+
+import org.junit.Assert.assertEquals
+import org.junit.Test
+import java.time.LocalDate
+
+class InsightWeekKeysTest {
+    @Test
+    fun isoWeekKey_formatsYearAndWeek() {
+        // 2026-07-22 is Wednesday of ISO week 30
+        assertEquals("2026-W30", InsightWeekKeys.isoWeekKey(LocalDate.of(2026, 7, 22)))
+    }
+}

