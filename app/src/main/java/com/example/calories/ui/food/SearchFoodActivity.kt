package com.example.calories.ui.food

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.ActivitySearchFoodBinding
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodSearchFilter
import com.example.calories.model.FoodSearchTab
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.DateTimeUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class SearchFoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchFoodBinding
    private val viewModel: SearchFoodViewModel by viewModels()
    private val adapter = FoodDictionaryAdapter { item -> openFoodDetail(item) }

    private var suppressQueryWatcher = false
    private var nativeAd: NativeAd? = null

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
            ?: MealType.SNACKS
        val selectedDate = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DateTimeUtils.today()

        viewModel.initialize(mealType, selectedDate)
        setupUi()

        MobileAds.initialize(this) {
            loadNativeAd()
        }

        observeViewModel()
    }

    private fun loadNativeAd() {
        val adLoader = AdLoader.Builder(this, "ca-app-pub-3940256099942544/2247696110")
            .forNativeAd { ad ->
                Log.d("AdTest", "Load Native Ad Successful")

                nativeAd?.destroy()
                nativeAd = ad

                val currentResults = viewModel.uiState.value.results
                updateAdapterList(currentResults)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdTest", "Load Native Ad Failed. Error: ${adError.message}, Code: ${adError.code}")
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rvFoods.layoutManager = LinearLayoutManager(this)
        binding.rvFoods.adapter = adapter
        binding.etSearch.addTextChangedListener(queryWatcher)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_recent))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_favorites))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selected = if (tab.position == 1) {
                    FoodSearchTab.FAVORITES
                } else {
                    FoodSearchTab.RECENT
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

            updateAdapterList(state.results)

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

    private fun updateAdapterList(foods: List<FoodDictionaryItem>) {
        if (foods.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        val displayItems = mutableListOf<DisplayItem>()
        val ad = nativeAd

        foods.forEachIndexed { index, foodItem ->
            displayItems.add(DisplayItem.Food(foodItem))
            if (ad != null && index == 2) {
                displayItems.add(DisplayItem.Ad(ad))
            }
        }

        if (ad != null && foods.size in 1..2) {
            displayItems.add(DisplayItem.Ad(ad))
        }

        adapter.submitList(displayItems)
    }

    private fun openFoodDetail(item: FoodDictionaryItem) {
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
                favoriteFoodId = item.id,
            ),
        )
    }

    private fun mealTypeLabelRes(mealType: MealType): Int = when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACKS -> R.string.meal_snacks
    }

    override fun onDestroy() {
        nativeAd?.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
    }
}