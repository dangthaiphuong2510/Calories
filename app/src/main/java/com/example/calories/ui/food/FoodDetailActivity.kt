package com.example.calories.ui.food

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.example.calories.R
import com.example.calories.databinding.ActivityFoodDetailBinding
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.util.DateTimeUtils
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class FoodDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityFoodDetailBinding
    private val viewModel: FoodDetailViewModel by viewModels()
    private var suppressWatchers = false

    private val portionWatcher = simpleWatcher { viewModel.onPortionChanged(it) }

    private var adView: AdView? = null
    private val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        binding = ActivityFoodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()

        val foodId = intent.getStringExtra(EXTRA_FOOD_ID)
        val favoriteFoodId = intent.getStringExtra(EXTRA_FAVORITE_FOOD_ID)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val calories = intent.getIntExtra(EXTRA_CALORIES, 0)
        val protein = intent.getDoubleExtra(EXTRA_PROTEIN, 0.0)
        val carb = intent.getDoubleExtra(EXTRA_CARB, 0.0)
        val fat = intent.getDoubleExtra(EXTRA_FAT, 0.0)
        val servingGrams = intent.getDoubleExtra(EXTRA_SERVING_GRAMS, 100.0)
        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE)
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: MealType.SNACKS
        val selectedDate = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DateTimeUtils.today()
        val createdAt = intent.getStringExtra(EXTRA_CREATED_AT)

        viewModel.initialize(
            foodId = foodId,
            favoriteFoodId = favoriteFoodId,
            name = name,
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            servingGrams = servingGrams,
            mealType = mealType,
            selectedDate = selectedDate,
            createdAt = createdAt,
        )

        setupUi()
        observeViewModel()
        loadCollapsibleBanner()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.etPortion.addTextChangedListener(portionWatcher)
        binding.btnLogFood.setOnClickListener { viewModel.logFood() }
        binding.btnSaveChanges.setOnClickListener { viewModel.saveChanges() }
        setupChart()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.nestedScrollView.updatePadding(top = statusBars.top)
            insets
        }
    }

    private fun loadCollapsibleBanner() {
        val adView = AdView(this)
        adView.adUnitId = BANNER_AD_UNIT_ID
        adView.setAdSize(getAdSize())

        this.adView = adView

        binding.adViewContainer.removeAllViews()
        binding.adViewContainer.addView(adView)

        val extras = Bundle().apply {
            putString("collapsible", "bottom")
        }

        val adRequest = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("CollapsibleBanner", "Banner loaded successfully")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("CollapsibleBanner", "Banner failed to load: ${error.message}")
            }
        }

        adView.loadAd(adRequest)
    }

    private fun getAdSize(): AdSize {
        val adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            bounds.width().toFloat()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels.toFloat()
        }

        val density = resources.displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
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
            binding.btnFavorite.isVisible = state.showFavoriteButton
            if (state.showFavoriteButton) {
                binding.btnFavorite.setImageResource(
                    if (state.isFavorite) {
                        R.drawable.ic_favorite_filled_24
                    } else {
                        R.drawable.ic_favorite_border_24
                    },
                )
                binding.btnFavorite.contentDescription = getString(
                    if (state.isFavorite) {
                        R.string.remove_from_favorites
                    } else {
                        R.string.add_to_favorites
                    },
                )
            }
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

            binding.groupMacroPills.isVisible = !state.isEditMode
            binding.groupEditNutrients.isVisible = state.isEditMode
            binding.btnLogFood.isVisible = !state.isEditMode
            binding.btnSaveChanges.isVisible = state.isEditMode && state.hasChanges
            binding.btnLogFood.isEnabled = !state.isSaving
            binding.btnSaveChanges.isEnabled = !state.isSaving

            bindEditableField(binding.etPortion, formatNumber(state.portionDisplay))
            binding.tilPortion.hint = getString(state.portionHintRes)
            if (state.isEditMode) {
                bindReadOnlyField(binding.etCalories, state.calories.toString())
                bindReadOnlyField(binding.etProtein, formatNumber(state.protein))
                bindReadOnlyField(binding.etCarb, formatNumber(state.carb))
                bindReadOnlyField(binding.etFat, formatNumber(state.fat))
            }
            bindChart(state)
        }

        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes -> {
                    Toast.makeText(this, event.resId, Toast.LENGTH_SHORT).show()
                    if (event.resId == R.string.invalid_portion_size) {
                        val portion = formatNumber(viewModel.uiState.value.portionDisplay)
                        bindEditableField(binding.etPortion, portion)
                    }
                }
            }
        }

        collectLatestStarted(viewModel.logged) { mealType ->
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_MEAL_TYPE, mealType.name),
            )
            finish()
        }

        collectLatestStarted(viewModel.updated) {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_FOOD_UPDATED, true),
            )
            finish()
        }
    }

    private fun bindEditableField(
        field: android.widget.EditText,
        text: String,
    ) {
        if (field.text?.toString() == text) return
        suppressWatchers = true
        field.setText(text)
        field.setSelection(text.length)
        suppressWatchers = false
    }

    private fun bindReadOnlyField(
        field: android.widget.EditText,
        text: String,
    ) {
        if (field.text?.toString() == text) return
        field.setText(text)
    }

    private fun simpleWatcher(onChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressWatchers) {
                onChanged(s?.toString().orEmpty())
            }
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

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FOOD_ID = "extra_food_id"
        const val EXTRA_FAVORITE_FOOD_ID = "extra_favorite_food_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_CALORIES = "extra_calories"
        const val EXTRA_PROTEIN = "extra_protein"
        const val EXTRA_CARB = "extra_carb"
        const val EXTRA_FAT = "extra_fat"
        const val EXTRA_SERVING_GRAMS = "extra_serving_grams"
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
        const val EXTRA_CREATED_AT = "extra_created_at"
        const val EXTRA_FOOD_UPDATED = "extra_food_updated"

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
            foodId: String? = null,
            favoriteFoodId: String? = null,
            createdAt: String? = null,
        ): Intent = Intent(context, FoodDetailActivity::class.java).apply {
            putExtra(EXTRA_FOOD_ID, foodId)
            putExtra(EXTRA_FAVORITE_FOOD_ID, favoriteFoodId ?: foodId)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_CALORIES, calories)
            putExtra(EXTRA_PROTEIN, protein)
            putExtra(EXTRA_CARB, carb)
            putExtra(EXTRA_FAT, fat)
            putExtra(EXTRA_SERVING_GRAMS, servingGrams)
            putExtra(EXTRA_MEAL_TYPE, mealType.name)
            putExtra(EXTRA_SELECTED_DATE, selectedDate.toString())
            putExtra(EXTRA_CREATED_AT, createdAt)
        }
    }
}