package com.example.calories.ui.notifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.ActivityNotificationSettingsBinding
import com.example.calories.databinding.ItemMealReminderRowBinding
import com.example.calories.model.enums.MealType
import com.example.calories.notifications.ReminderScheduler
import com.example.calories.ui.common.collectLatestStarted
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private val viewModel: NotificationSettingsViewModel by viewModels()

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private val waterAdapter = ScheduleTimeAdapter(
        onTimeClick = { index, time -> showTimePicker(time) { viewModel.updateWaterTime(index, it) } },
        onRemoveClick = { index -> viewModel.removeWaterTime(index) },
    )
    private val workoutAdapter = ScheduleTimeAdapter(
        onTimeClick = { index, time -> showTimePicker(time) { viewModel.updateWorkoutTime(index, it) } },
        onRemoveClick = { index -> viewModel.removeWorkoutTime(index) },
    )

    private var suppressSwitchCallbacks = false
    private var pendingEnableGroup: ReminderGroup? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val group = pendingEnableGroup
        if (!granted) {
            rejectPendingEnable(
                group = group,
                messageRes = R.string.notification_permission_required,
            )
            return@registerForActivityResult
        }
        if (group != null) {
            continueEnableAfterNotificationPermission(group)
        }
    }

    private val exactAlarmSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val group = pendingEnableGroup
        if (reminderScheduler.canScheduleExactAlarms()) {
            if (group != null) {
                enableReminderGroup(group)
            }
            pendingEnableGroup = null
        } else {
            rejectPendingEnable(
                group = group,
                messageRes = R.string.exact_alarm_permission_required,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rowBreakfast.tvMealName.setText(R.string.meal_breakfast)
        binding.rowLunch.tvMealName.setText(R.string.meal_lunch)
        binding.rowDinner.tvMealName.setText(R.string.meal_dinner)
        binding.rowSnacks.tvMealName.setText(R.string.meal_snacks)

        binding.switchMealReminders.setOnCheckedChangeListener { _, checked ->
            onMasterSwitchChanged(ReminderGroup.MEAL, checked)
        }
        binding.switchWaterReminders.setOnCheckedChangeListener { _, checked ->
            onMasterSwitchChanged(ReminderGroup.WATER, checked)
        }
        binding.switchWorkoutReminders.setOnCheckedChangeListener { _, checked ->
            onMasterSwitchChanged(ReminderGroup.WORKOUT, checked)
        }
        binding.switchIntakeWarnings.setOnCheckedChangeListener { _, checked ->
            onMasterSwitchChanged(ReminderGroup.INTAKE, checked)
        }

        binding.rowBreakfast.tvMealTime.setOnClickListener {
            pickMealTime(MealType.BREAKFAST, binding.rowBreakfast.tvMealTime.text?.toString())
        }
        binding.rowLunch.tvMealTime.setOnClickListener {
            pickMealTime(MealType.LUNCH, binding.rowLunch.tvMealTime.text?.toString())
        }
        binding.rowDinner.tvMealTime.setOnClickListener {
            pickMealTime(MealType.DINNER, binding.rowDinner.tvMealTime.text?.toString())
        }
        binding.rowSnacks.tvMealTime.setOnClickListener {
            pickMealTime(MealType.SNACKS, binding.rowSnacks.tvMealTime.text?.toString())
        }

        binding.rvWaterTimes.layoutManager = LinearLayoutManager(this)
        binding.rvWaterTimes.adapter = waterAdapter
        binding.rvWorkoutTimes.layoutManager = LinearLayoutManager(this)
        binding.rvWorkoutTimes.adapter = workoutAdapter

        binding.btnAddWaterTime.setOnClickListener {
            showTimePicker("08:00") { viewModel.addWaterTime(it) }
        }
        binding.btnAddWorkoutTime.setOnClickListener {
            showTimePicker("07:00") { viewModel.addWorkoutTime(it) }
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            suppressSwitchCallbacks = true
            binding.switchMealReminders.isChecked = state.mealRemindersEnabled
            binding.switchWaterReminders.isChecked = state.waterRemindersEnabled
            binding.switchWorkoutReminders.isChecked = state.workoutRemindersEnabled
            binding.switchIntakeWarnings.isChecked = state.intakeWarningsEnabled
            suppressSwitchCallbacks = false

            binding.mealRemindersContent.visibility =
                if (state.mealRemindersEnabled) View.VISIBLE else View.GONE
            binding.waterRemindersContent.visibility =
                if (state.waterRemindersEnabled) View.VISIBLE else View.GONE
            binding.workoutRemindersContent.visibility =
                if (state.workoutRemindersEnabled) View.VISIBLE else View.GONE

            bindMealRow(binding.rowBreakfast, state.breakfastTime)
            bindMealRow(binding.rowLunch, state.lunchTime)
            bindMealRow(binding.rowDinner, state.dinnerTime)
            bindMealRow(binding.rowSnacks, state.snacksTime)

            waterAdapter.submitList(state.waterTimes)
            workoutAdapter.submitList(state.workoutTimes)
        }
    }

    private fun onMasterSwitchChanged(group: ReminderGroup, checked: Boolean) {
        if (suppressSwitchCallbacks) return
        if (!checked) {
            if (pendingEnableGroup == group) pendingEnableGroup = null
            disableReminderGroup(group)
            return
        }
        // Keep switch visually ON during the permission flow; revert if denied.
        requestPermissionsThenEnable(group)
    }

    private fun requestPermissionsThenEnable(group: ReminderGroup) {
        pendingEnableGroup = group
        if (!hasPostNotificationsPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueEnableAfterNotificationPermission(group)
    }

    private fun continueEnableAfterNotificationPermission(group: ReminderGroup) {
        // Intake warnings are posted immediately after food logging — no exact alarm needed.
        if (group != ReminderGroup.INTAKE && !reminderScheduler.canScheduleExactAlarms()) {
            pendingEnableGroup = group
            openExactAlarmSettings()
            return
        }
        enableReminderGroup(group)
        pendingEnableGroup = null
    }

    private fun enableReminderGroup(group: ReminderGroup) {
        when (group) {
            ReminderGroup.MEAL -> viewModel.setMealRemindersEnabled(true)
            ReminderGroup.WATER -> viewModel.setWaterRemindersEnabled(true)
            ReminderGroup.WORKOUT -> viewModel.setWorkoutRemindersEnabled(true)
            ReminderGroup.INTAKE -> viewModel.setIntakeWarningsEnabled(true)
        }
    }

    private fun disableReminderGroup(group: ReminderGroup) {
        when (group) {
            ReminderGroup.MEAL -> viewModel.setMealRemindersEnabled(false)
            ReminderGroup.WATER -> viewModel.setWaterRemindersEnabled(false)
            ReminderGroup.WORKOUT -> viewModel.setWorkoutRemindersEnabled(false)
            ReminderGroup.INTAKE -> viewModel.setIntakeWarningsEnabled(false)
        }
    }

    private fun rejectPendingEnable(group: ReminderGroup?, messageRes: Int) {
        if (group != null) {
            setSwitchChecked(group, false)
            // Ensure ViewModel stays disabled (never scheduled without permission)
            disableReminderGroup(group)
        }
        pendingEnableGroup = null
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun setSwitchChecked(group: ReminderGroup, checked: Boolean) {
        suppressSwitchCallbacks = true
        switchFor(group).isChecked = checked
        suppressSwitchCallbacks = false
    }

    private fun switchFor(group: ReminderGroup): MaterialSwitch = when (group) {
        ReminderGroup.MEAL -> binding.switchMealReminders
        ReminderGroup.WATER -> binding.switchWaterReminders
        ReminderGroup.WORKOUT -> binding.switchWorkoutReminders
        ReminderGroup.INTAKE -> binding.switchIntakeWarnings
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pendingEnableGroup?.let(::enableReminderGroup)
            pendingEnableGroup = null
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            exactAlarmSettingsLauncher.launch(intent)
        }.onFailure {
            // Fallback for devices that don't support the request intent
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            exactAlarmSettingsLauncher.launch(fallback)
        }
        Toast.makeText(this, R.string.exact_alarm_permission_prompt, Toast.LENGTH_LONG).show()
    }

    private fun bindMealRow(row: ItemMealReminderRowBinding, time: String) {
        row.tvMealTime.text = time
    }

    private fun pickMealTime(mealType: MealType, current: String?) {
        showTimePicker(current ?: "08:00") { time ->
            viewModel.setMealTime(mealType, time)
        }
    }

    private fun showTimePicker(current: String, onPicked: (String) -> Unit) {
        val (hour, minute) = parseTime(current)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.select_reminder_time)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(formatTime(picker.hour, picker.minute))
        }
        picker.show(supportFragmentManager, "reminder_time_picker")
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    private enum class ReminderGroup {
        MEAL,
        WATER,
        WORKOUT,
        INTAKE,
    }
}
