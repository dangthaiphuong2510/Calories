package com.example.calories.ui.exercise

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.databinding.ActivityExerciseLoggerBinding
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class ExerciseLoggerActivity : BaseActivity() {

    private lateinit var binding: ActivityExerciseLoggerBinding
    private val viewModel: ExerciseLoggerViewModel by viewModels()
    private val adapter = ExercisePresetAdapter { viewModel.logPreset(it) }

    private var suppressWatchers = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseLoggerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val selectedDate = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DateTimeUtils.today()
        viewModel.initialize(selectedDate)

        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { finish() }
        binding.rvPresets.layoutManager = LinearLayoutManager(this)
        binding.rvPresets.adapter = adapter

        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == binding.btnModeCustom.id) {
                ExerciseLoggerMode.CUSTOM
            } else {
                ExerciseLoggerMode.PRESET
            }
            viewModel.setMode(mode)
        }

        binding.etSearchExercise.addTextChangedListener(simpleWatcher { viewModel.onQueryChanged(it) })
        binding.etCustomName.addTextChangedListener(simpleWatcher { viewModel.onCustomNameChanged(it) })
        binding.etCustomCalories.addTextChangedListener(simpleWatcher { viewModel.onCustomCaloriesChanged(it) })
        binding.etCustomDuration.addTextChangedListener(simpleWatcher { viewModel.onCustomDurationChanged(it) })

        binding.cbSaveCustomDb.setOnCheckedChangeListener { _, checked ->
            if (!suppressWatchers) viewModel.onSaveToCustomDbChanged(checked)
        }
        binding.cbAddTodayLog.setOnCheckedChangeListener { _, checked ->
            if (!suppressWatchers) viewModel.onAddToTodayLogChanged(checked)
        }
        binding.btnSubmitCustom.setOnClickListener { viewModel.submitCustom() }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            val showPreset = state.mode == ExerciseLoggerMode.PRESET
            binding.panelPreset.visibility = if (showPreset) View.VISIBLE else View.GONE
            binding.panelCustom.visibility = if (showPreset) View.GONE else View.VISIBLE
            adapter.submitList(state.presets)
            binding.btnSubmitCustom.isEnabled = !state.isSaving

            suppressWatchers = true
            if (binding.etSearchExercise.text?.toString() != state.query) {
                binding.etSearchExercise.setText(state.query)
            }
            if (binding.etCustomName.text?.toString() != state.customName) {
                binding.etCustomName.setText(state.customName)
            }
            if (binding.etCustomCalories.text?.toString() != state.customCalories) {
                binding.etCustomCalories.setText(state.customCalories)
            }
            if (binding.etCustomDuration.text?.toString() != state.customDuration) {
                binding.etCustomDuration.setText(state.customDuration)
            }
            binding.cbSaveCustomDb.isChecked = state.saveToCustomDb
            binding.cbAddTodayLog.isChecked = state.addToTodayLog
            suppressWatchers = false
        }

        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(this, event.resId, Toast.LENGTH_SHORT).show()
            }
        }

        collectLatestStarted(viewModel.logged) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressWatchers) onChange(s?.toString().orEmpty())
        }
    }

    companion object {
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
    }
}
