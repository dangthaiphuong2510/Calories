package com.example.calories.ui.food

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.ActivitySearchFoodBinding
import com.example.calories.model.FoodSearchFilter
import com.example.calories.model.FoodSearchTab
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.DateTimeUtils
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class SearchFoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchFoodBinding
    private val viewModel: SearchFoodViewModel by viewModels()
    private val adapter = FoodDictionaryAdapter { item -> openFoodDetail(item) }

    private var suppressQueryWatcher = false

    private val foodDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, result.data)
            finish()
        }
    }

    private val queryWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressQueryWatcher) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchFoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.SNACK
        val selectedDate = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DateTimeUtils.today()

        viewModel.initialize(mealType, selectedDate)
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rvFoods.layoutManager = LinearLayoutManager(this)
        binding.rvFoods.adapter = adapter
        binding.etSearch.addTextChangedListener(queryWatcher)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_recent))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_favorites))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_my_foods))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selected = when (tab.position) {
                    1 -> FoodSearchTab.FAVORITES
                    2 -> FoodSearchTab.MY_FOODS
                    else -> FoodSearchTab.RECENT
                }
                viewModel.onTabSelected(selected)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipHighProtein -> FoodSearchFilter.HIGH_PROTEIN
                R.id.chipLowCarbs -> FoodSearchFilter.LOW_CARBS
                R.id.chipLowFat -> FoodSearchFilter.LOW_FAT
                else -> FoodSearchFilter.ALL
            }
            viewModel.onFilterSelected(filter)
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.tvHeaderTitle.text = getString(
                R.string.search_food_header_format,
                getString(mealTypeLabelRes(state.mealType)),
                state.dateLabelShort,
            )

            if (binding.etSearch.text?.toString() != state.query) {
                suppressQueryWatcher = true
                binding.etSearch.setText(state.query)
                binding.etSearch.setSelection(state.query.length)
                suppressQueryWatcher = false
            }

            adapter.submitList(state.results)
            binding.progressLoading.visibility =
                if (state.isLoading) View.VISIBLE else View.GONE
            binding.tvEmpty.visibility =
                if (state.isEmpty && !state.isLoading) View.VISIBLE else View.GONE
            binding.rvFoods.visibility =
                if (state.isEmpty && !state.isLoading) View.GONE else View.VISIBLE
        }

        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(this, event.resId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFoodDetail(item: com.example.calories.model.FoodDictionaryItem) {
        val state = viewModel.uiState.value
        foodDetailLauncher.launch(
            FoodDetailActivity.intent(
                context = this,
                name = item.name,
                calories = item.caloriesInt,
                protein = item.proteinGrams,
                carb = item.carbGrams,
                fat = item.fatGrams,
                servingGrams = 100.0,
                mealType = state.mealType,
                selectedDate = state.selectedDate,
                viewOnly = false,
            ),
        )
    }

    private fun mealTypeLabelRes(mealType: MealType): Int = when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snacks
    }

    companion object {
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
    }
}
