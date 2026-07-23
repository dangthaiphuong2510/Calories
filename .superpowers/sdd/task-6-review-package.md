# Review package Task 6
BASE: ffd296a970a32afe1c614dd52f18516adb54c0dc
HEAD: 7e92423554e304ae29a72c605db81daf588894e6
## Commits
7e92423 feat(insights): add EN/VI copy and UI string mapper

## Diff stat
 .../calories/insights/ProgressInsightUiMapper.kt   | 28 ++++++++++++++++++++++
 app/src/main/res/values-vi/strings.xml             | 15 ++++++++++++
 app/src/main/res/values/strings.xml                | 15 ++++++++++++
 3 files changed, 58 insertions(+)

## Full diff
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt
new file mode 100644
index 0000000..8e8d21a
--- /dev/null
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt
@@ -0,0 +1,28 @@
+package com.example.calories.insights
+
+import androidx.annotation.StringRes
+import com.example.calories.R
+
+object ProgressInsightUiMapper {
+    @StringRes
+    fun titleRes(id: String): Int = when (id) {
+        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_title
+        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_title
+        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_title
+        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_title
+        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_title
+        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_title
+        else -> R.string.insights_section_title
+    }
+
+    @StringRes
+    fun bodyRes(id: String): Int = when (id) {
+        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_body
+        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_body
+        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_body
+        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_body
+        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_body
+        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_body
+        else -> R.string.insights_insufficient_body
+    }
+}
diff --git a/app/src/main/res/values-vi/strings.xml b/app/src/main/res/values-vi/strings.xml
index 86091aa..a4c3622 100644
--- a/app/src/main/res/values-vi/strings.xml
+++ b/app/src/main/res/values-vi/strings.xml
@@ -101,20 +101,35 @@
     <string name="current_weight_label">Hiß╗çn tß║íi</string>
     <string name="target_weight_label">Mß╗Ñc ti├¬u</string>
     <string name="nutrition_statistics">Thß╗æng k├¬ dinh d╞░ß╗íng</string>
     <string name="nutrition_period_day">Ng├áy</string>
     <string name="nutrition_period_week">Tuß║ºn</string>
     <string name="calorie_intake_vs_burned">Calo nß║íp vs. ti├¬u hao</string>
     <string name="macro_distribution">Ph├ón bß╗æ macro</string>
     <string name="weekly_macro_distribution">Ph├ón bß╗æ macro tuß║ºn</string>
     <string name="legend_calories_consumed">─É├ú nß║íp</string>
     <string name="legend_calories_burned">─É├ú ─æß╗æt</string>
+    <string name="insights_section_title">Nhß║¡n x├⌐t tuß║ºn n├áy</string>
+    <string name="insights_insufficient_title">Tiß║┐p tß╗Ñc ghi nhß║¡t k├╜</string>
+    <string name="insights_insufficient_body">Ghi th├¬m v├ái ng├áy thß╗▒c phß║⌐m ─æß╗â mß╗ƒ kh├│a nhß║¡n x├⌐t c├í nh├ón.</string>
+    <string name="insights_plateau_title">C├ón kh├┤ng ─æß╗òi</string>
+    <string name="insights_plateau_body">Nhß║¡t k├╜ cho thß║Ñy nhiß╗üu ng├áy d╞░ß╗¢i mß╗Ñc ti├¬u nh╞░ng c├ón gß║ºn nh╞░ ─æß╗⌐ng y├¬n. H├úy kiß╗âm tra khß║⌐u phß║ºn hoß║╖c ng├áy thiß║┐u log.</string>
+    <string name="insights_weekend_title">Cuß╗æi tuß║ºn cao calo</string>
+    <string name="insights_weekend_body">Nhß║¡t k├╜ cho thß║Ñy l╞░ß╗úng ─ân cuß╗æi tuß║ºn cao h╞ín r├╡ so vß╗¢i ng├áy th╞░ß╗¥ng.</string>
+    <string name="insights_protein_title">Protein ─æang thß║Ñp</string>
+    <string name="insights_protein_body">Protein d╞░ß╗¢i ~80% mß╗Ñc ti├¬u ß╗ƒ hß║ºu hß║┐t c├íc ng├áy gß║ºn ─æ├óy c├│ ghi nhß║¡t k├╜.</string>
+    <string name="insights_logging_gap_title">Thiß║┐u nhß║¡t k├╜ m├│n ─ân</string>
+    <string name="insights_logging_gap_body">%1$s ng├áy trong tuß║ºn qua ch╞░a c├│ nhß║¡t k├╜ m├│n ─ân, xu h╞░ß╗¢ng c├│ thß╗â ch╞░a ─æß╗º.</string>
+    <string name="insights_on_track_title">Duy tr├¼ tß╗æt</string>
+    <string name="insights_on_track_body">Bß║ín gß║ºn mß╗Ñc ti├¬u calo trong %1$s ng├áy tuß║ºn n├áy.</string>
+    <string name="insights_dismiss">─É├│ng</string>
+    <string name="insights_home_callout_cd">Nhß║¡n x├⌐t tiß║┐n ─æß╗Ö</string>
 
     <!-- Camera / food scan -->
     <string name="food_not_detected_title">Kh├┤ng nhß║¡n diß╗çn ─æ╞░ß╗úc m├│n ─ân</string>
     <string name="food_not_detected_subtitle">Kh├┤ng t├¼m thß║Ñy m├│n ─ân hoß║╖c ─æß╗ô uß╗æng trong ß║únh. Vui l├▓ng qu├⌐t lß║íi vß╗¢i ß║únh r├╡ h╞ín.</string>
     <string name="food_scan_photo_tips_title">Mß║╣o chß╗Ñp ß║únh</string>
     <string name="food_scan_photo_tip_1">─Éß║╖t m├│n ─ân v├áo giß╗»a khung h├¼nh</string>
     <string name="food_scan_photo_tip_2">Chß╗Ñp trong ─æiß╗üu kiß╗çn ─æß╗º s├íng</string>
     <string name="food_scan_photo_tip_3">Tr├ính che khuß║Ñt m├│n ─ân v├á giß╗» ß║únh n├⌐t</string>
     <string name="scan_again">Qu├⌐t lß║íi</string>
     <string name="camera_permission_title">Quyß╗ün truy cß║¡p camera</string>
diff --git a/app/src/main/res/values/strings.xml b/app/src/main/res/values/strings.xml
index 20ba2d0..d41373f 100644
--- a/app/src/main/res/values/strings.xml
+++ b/app/src/main/res/values/strings.xml
@@ -359,20 +359,35 @@
     <string name="macro_distribution">Macro Distribution</string>
     <string name="weekly_macro_distribution">Weekly Macro Distribution</string>
     <string name="legend_calories_consumed">Consumed</string>
     <string name="legend_calories_burned">Burned</string>
     <string name="macro_legend_format">%1$s: %2$.0f%% (%3$.0fg)</string>
     <string name="macro_legend_empty">No macro data for this period</string>
     <string name="chart_no_calorie_data">No calorie data for this period</string>
     <string name="nutrition_week_range_format">%1$s ΓÇô %2$s</string>
     <string name="macro_center_day">Day</string>
     <string name="macro_center_week">7 days</string>
+    <string name="insights_section_title">Weekly insights</string>
+    <string name="insights_insufficient_title">Keep logging</string>
+    <string name="insights_insufficient_body">Log food for a few more days to unlock personal insights.</string>
+    <string name="insights_plateau_title">Scale not moving</string>
+    <string name="insights_plateau_body">Your logs show several days under target, but weight is nearly flat. Check portions or logging gaps.</string>
+    <string name="insights_weekend_title">Weekend calorie spike</string>
+    <string name="insights_weekend_body">Your logs show weekend intake meaningfully higher than weekdays.</string>
+    <string name="insights_protein_title">Protein running low</string>
+    <string name="insights_protein_body">Protein was under ~80% of target on most recent logged days.</string>
+    <string name="insights_logging_gap_title">Missing food logs</string>
+    <string name="insights_logging_gap_body">%1$s days in the last week have no food logged, so trends may be incomplete.</string>
+    <string name="insights_on_track_title">Solid adherence</string>
+    <string name="insights_on_track_body">You stayed near your calorie target on %1$s days this week.</string>
+    <string name="insights_dismiss">Dismiss</string>
+    <string name="insights_home_callout_cd">Progress insight</string>
 
     <!-- Profile -->
     <string name="profile">Profile</string>
     <string name="edit_goals">Edit goals</string>
     <string name="edit_profile">Edit profile</string>
     <string name="edit_display_name">Edit display name</string>
     <string name="display_name_hint">Display name</string>
     <string name="change_photo">Change photo</string>
     <string name="remove_photo">Remove photo</string>
     <string name="avatar_updated">Profile photo updated</string>

