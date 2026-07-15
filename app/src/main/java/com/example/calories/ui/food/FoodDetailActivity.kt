package com.example.calories.ui.food

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.calories.R
import com.example.calories.databinding.ActivityFoodDetailBinding
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.DateTimeUtils
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class FoodDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFoodDetailBinding
    private val viewModel: FoodDetailViewModel by viewModels()
    private var suppressPortionWatcher = false

    private val portionWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressPortionWatcher) {
                viewModel.onPortionChanged(s?.toString().orEmpty())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val calories = intent.getIntExtra(EXTRA_CALORIES, 0)
        val protein = intent.getDoubleExtra(EXTRA_PROTEIN, 0.0)
        val carb = intent.getDoubleExtra(EXTRA_CARB, 0.0)
        val fat = intent.getDoubleExtra(EXTRA_FAT, 0.0)
        val servingGrams = intent.getDoubleExtra(EXTRA_SERVING_GRAMS, 100.0)
        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.SNACK
        val selectedDate = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DateTimeUtils.today()
        val viewOnly = intent.getBooleanExtra(EXTRA_VIEW_ONLY, false)

        viewModel.initialize(
            name = name,
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            servingGrams = servingGrams,
            mealType = mealType,
            selectedDate = selectedDate,
            viewOnly = viewOnly,
        )

        setupUi(viewOnly)
        observeViewModel()
    }

    private fun setupUi(viewOnly: Boolean) {
        binding.btnBack.setOnClickListener { finish() }
        binding.etPortion.addTextChangedListener(portionWatcher)
        binding.btnLogFood.visibility = if (viewOnly) View.GONE else View.VISIBLE
        binding.btnLogFood.setOnClickListener { viewModel.logFood() }
        setupChart()
    }

    private fun setupChart() {
        binding.chartMacros.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            holeRadius = 62f
            transparentCircleRadius = 66f
            setHoleColor(Color.TRANSPARENT)
            setDrawCenterText(true)
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.tvFoodName.text = state.name
            binding.tvCalories.text = getString(R.string.meal_calories_format, state.calories)
            binding.tvProtein.text = getString(R.string.macro_pill_protein, state.protein)
            binding.tvCarb.text = getString(R.string.macro_pill_carb, state.carb)
            binding.tvFat.text = getString(R.string.macro_pill_fat, state.fat)
            binding.tvBurnWalk.text =
                getString(R.string.burn_walk_format, state.burnMinutesWalk)
            binding.tvBurnRun.text =
                getString(R.string.burn_run_format, state.burnMinutesRun)
            binding.tvBurnCycle.text =
                getString(R.string.burn_cycle_format, state.burnMinutesCycle)
            binding.btnLogFood.isEnabled = !state.isSaving

            val portionText = formatPortion(state.portionGrams)
            if (binding.etPortion.text?.toString() != portionText) {
                suppressPortionWatcher = true
                binding.etPortion.setText(portionText)
                binding.etPortion.setSelection(portionText.length)
                suppressPortionWatcher = false
            }
            bindChart(state)
        }

        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(this, event.resId, Toast.LENGTH_SHORT).show()
            }
        }

        collectLatestStarted(viewModel.logged) { mealType ->
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_MEAL_TYPE, mealType.name),
            )
            finish()
        }
    }

    private fun bindChart(state: FoodDetailUiState) {
        val entries = buildList {
            if (state.protein > 0) add(PieEntry(state.protein.toFloat(), "P"))
            if (state.carb > 0) add(PieEntry(state.carb.toFloat(), "C"))
            if (state.fat > 0) add(PieEntry(state.fat.toFloat(), "F"))
        }
        if (entries.isEmpty()) {
            binding.chartMacros.clear()
            binding.chartMacros.centerText = "—"
            binding.chartMacros.invalidate()
            return
        }
        val colors = listOf(
            ContextCompat.getColor(this, R.color.macro_protein),
            ContextCompat.getColor(this, R.color.macro_carb),
            ContextCompat.getColor(this, R.color.macro_fat),
        )
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors.take(entries.size)
            setDrawValues(false)
            sliceSpace = 2f
        }
        binding.chartMacros.data = PieData(dataSet)
        binding.chartMacros.centerText = getString(R.string.kcal_value_format, state.calories)
        binding.chartMacros.invalidate()
    }

    private fun formatPortion(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_CALORIES = "extra_calories"
        const val EXTRA_PROTEIN = "extra_protein"
        const val EXTRA_CARB = "extra_carb"
        const val EXTRA_FAT = "extra_fat"
        const val EXTRA_SERVING_GRAMS = "extra_serving_grams"
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
        const val EXTRA_VIEW_ONLY = "extra_view_only"

        fun intent(
            context: Context,
            name: String,
            calories: Int,
            protein: Double,
            carb: Double,
            fat: Double,
            servingGrams: Double,
            mealType: MealType,
            selectedDate: LocalDate,
            viewOnly: Boolean,
        ): Intent = Intent(context, FoodDetailActivity::class.java).apply {
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_CALORIES, calories)
            putExtra(EXTRA_PROTEIN, protein)
            putExtra(EXTRA_CARB, carb)
            putExtra(EXTRA_FAT, fat)
            putExtra(EXTRA_SERVING_GRAMS, servingGrams)
            putExtra(EXTRA_MEAL_TYPE, mealType.name)
            putExtra(EXTRA_SELECTED_DATE, selectedDate.toString())
            putExtra(EXTRA_VIEW_ONLY, viewOnly)
        }
    }
}
