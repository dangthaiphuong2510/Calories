package com.example.calories.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentHomeBinding
import com.example.calories.databinding.ItemHomeExerciseBinding
import com.example.calories.databinding.ItemHomeMealCircleBinding
import com.example.calories.databinding.ItemHomeMealDetailSectionBinding
import com.example.calories.databinding.ItemHomeMealFoodBinding
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.exercise.ExerciseLoggerActivity
import com.example.calories.ui.food.FoodDetailActivity
import com.example.calories.ui.food.SearchFoodActivity
import com.example.calories.ui.notifications.NotificationSettingsActivity
import com.example.calories.util.DateTimeUtils
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneOffset

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val mealFeedbackShown = mutableMapOf<MealType, Boolean>()

    private val searchFoodLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val mealName = result.data?.getStringExtra(SearchFoodActivity.EXTRA_MEAL_TYPE)
            ?: result.data?.getStringExtra(FoodDetailActivity.EXTRA_MEAL_TYPE)
        val mealType = mealName
            ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
            ?: return@registerForActivityResult
        viewModel.onMealFoodLogged(mealType)
    }

    private val foodDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* view-only; no feedback */ }

    private val exerciseLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* StateFlow in repository updates Home automatically */ }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInteractions()
        observeViewModel()
    }

    private fun setupInteractions() {
        binding.btnPreviousDate.setOnClickListener { viewModel.previousDay() }
        binding.btnNextDate.setOnClickListener { viewModel.nextDay() }
        binding.tvCurrentDate.setOnClickListener { showDatePicker() }
        binding.btnNotifications.setOnClickListener { viewModel.onNotificationsClicked() }

        binding.sectionMeals.mealBreakfast.btnAddMealFood.setOnClickListener {
            viewModel.onAddMealClicked(MealType.BREAKFAST)
        }
        binding.sectionMeals.mealLunch.btnAddMealFood.setOnClickListener {
            viewModel.onAddMealClicked(MealType.LUNCH)
        }
        binding.sectionMeals.mealDinner.btnAddMealFood.setOnClickListener {
            viewModel.onAddMealClicked(MealType.DINNER)
        }
        binding.sectionMeals.mealSnacks.btnAddMealFood.setOnClickListener {
            viewModel.onAddMealClicked(MealType.SNACK)
        }
        binding.sectionMeals.btnToggleMealDetails.setOnClickListener {
            viewModel.toggleMealDetails()
        }

        binding.sectionWater.btnAddWater.setOnClickListener { viewModel.addWater() }
        binding.sectionWater.btnRemoveWater.setOnClickListener { viewModel.removeWater() }

        binding.sectionExercise.root.setOnClickListener { viewModel.onExerciseCardClicked() }
        binding.sectionExercise.btnAddExercise.setOnClickListener { viewModel.onExerciseCardClicked() }

        binding.sectionWeight.btnWeightMinus.setOnClickListener { viewModel.adjustWeight(-0.1) }
        binding.sectionWeight.btnWeightPlus.setOnClickListener { viewModel.adjustWeight(+0.1) }
    }

    override fun onPause() {
        viewModel.flushPendingWeight()
        super.onPause()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            bindDateHeader(state)
            bindCalorieCard(state)
            bindMacrosCard(state)
            bindMealsSection(state)
            bindWaterCard(state)
            bindExerciseCard(state)
            bindWeightCard(state)
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(requireContext(), event.resId, Toast.LENGTH_SHORT).show()
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                is HomeNavEvent.OpenSearchFood -> openSearchFood(event.mealType)
                is HomeNavEvent.OpenFoodDetail -> openFoodDetail(event)
                HomeNavEvent.OpenExerciseLogger -> openExerciseLogger()
                HomeNavEvent.OpenNotificationSettings -> openNotificationSettings()
            }
        }
    }

    private fun showDatePicker() {
        val current = viewModel.currentDate.value
        val selection = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.select_date)
            .setSelection(selection)
            .setTheme(R.style.ThemeOverlay_Calories_MaterialCalendar)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val selected = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            viewModel.selectDate(selected)
        }
        picker.show(parentFragmentManager, "home_date_picker")
    }

    private fun openNotificationSettings() {
        startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
    }

    private fun bindDateHeader(state: HomeUiState) {
        val today = DateTimeUtils.today()
        binding.tvCurrentDate.text = when (state.currentDate) {
            today -> getString(
                R.string.home_date_today_format,
                DateTimeUtils.formatMonthDay(state.currentDate),
            )
            today.minusDays(1) -> getString(
                R.string.home_date_yesterday_format,
                DateTimeUtils.formatMonthDay(state.currentDate),
            )
            else -> state.currentDateLabel
        }
    }

    private fun bindCalorieCard(state: HomeUiState) {
        val card = binding.sectionCalories
        card.tvCalorieGoal.text = state.dailyGoal.toString()
        card.tvCaloriesEaten.text = state.totalEaten.toString()
        card.tvCaloriesBurned.text = state.totalBurned.toString()
        card.tvCaloriesRemaining.text = state.caloriesRemaining.toString()
        card.progressCalories.setProgressCompat(state.calorieProgressPercent, true)
    }

    private fun bindMacrosCard(state: HomeUiState) {
        val card = binding.sectionMacros
        card.tvProteinValue.text = getString(
            R.string.macro_progress_format,
            state.protein.currentGrams,
            state.protein.targetGrams,
        )
        card.tvCarbValue.text = getString(
            R.string.macro_progress_format,
            state.carbs.currentGrams,
            state.carbs.targetGrams,
        )
        card.tvFatValue.text = getString(
            R.string.macro_progress_format,
            state.fat.currentGrams,
            state.fat.targetGrams,
        )
        card.progressProtein.setProgressCompat(state.protein.progressPercent, true)
        card.progressCarb.setProgressCompat(state.carbs.progressPercent, true)
        card.progressFat.setProgressCompat(state.fat.progressPercent, true)
    }

    private fun bindMealsSection(state: HomeUiState) {
        bindMealCircle(
            binding.sectionMeals.mealBreakfast,
            state.breakfast,
            R.drawable.ic_meal_breakfast_48,
        )
        bindMealCircle(
            binding.sectionMeals.mealLunch,
            state.lunch,
            R.drawable.ic_meal_lunch_48,
        )
        bindMealCircle(
            binding.sectionMeals.mealDinner,
            state.dinner,
            R.drawable.ic_meal_dinner_48,
        )
        bindMealCircle(
            binding.sectionMeals.mealSnacks,
            state.snacks,
            R.drawable.ic_meal_snack_48,
        )

        binding.sectionMeals.tvToggleMealDetails.setText(
            if (state.mealDetailsExpanded) {
                R.string.hide_meal_details
            } else {
                R.string.view_meal_details
            },
        )
        binding.sectionMeals.ivToggleMealDetails.rotation =
            if (state.mealDetailsExpanded) 90f else 0f

        val drawer = binding.sectionMeals.llMealDetailsDrawer
        if (state.mealDetailsExpanded) {
            if (drawer.visibility != View.VISIBLE) {
                drawer.visibility = View.VISIBLE
                drawer.alpha = 0f
                drawer.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            bindMealDetailsDrawer(state)
        } else {
            drawer.visibility = View.GONE
            drawer.removeAllViews()
        }
    }

    private fun bindMealCircle(
        circle: ItemHomeMealCircleBinding,
        section: MealSection,
        illustrationRes: Int,
    ) {
        circle.tvMealCircleTitle.setText(section.titleRes)
        circle.tvMealCircleCalories.text = section.totalCalories.toString()
        circle.ivMealIllustration.setImageResource(illustrationRes)
        applyAddButtonFeedback(
            button = circle.btnAddMealFood,
            mealType = section.mealType,
            showCheck = section.showLoggedFeedback,
        )
    }

    private fun applyAddButtonFeedback(
        button: View,
        mealType: MealType,
        showCheck: Boolean,
    ) {
        val imageButton = button as android.widget.ImageButton
        val wasShowing = mealFeedbackShown[mealType] == true
        mealFeedbackShown[mealType] = showCheck
        if (showCheck) {
            imageButton.setImageResource(R.drawable.ic_check_24)
            imageButton.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_meal_check_button,
            )
            if (!wasShowing) {
                imageButton.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(120)
                    .withEndAction {
                        imageButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    .start()
            }
        } else {
            imageButton.setImageResource(R.drawable.ic_add_24)
            imageButton.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bg_meal_add_button,
            )
        }
    }

    private fun bindMealDetailsDrawer(state: HomeUiState) {
        val drawer = binding.sectionMeals.llMealDetailsDrawer
        drawer.removeAllViews()
        state.mealSections.forEach { section ->
            val detail = ItemHomeMealDetailSectionBinding.inflate(layoutInflater, drawer, false)
            detail.tvDetailMealTitle.setText(section.titleRes)
            detail.btnLogFoodQuick.setOnClickListener {
                viewModel.onAddMealClicked(section.mealType)
            }
            detail.tvPillCarb.text = getString(R.string.macro_pill_carb, section.totalCarb)
            detail.tvPillProtein.text = getString(R.string.macro_pill_protein, section.totalProtein)
            detail.tvPillFat.text = getString(R.string.macro_pill_fat, section.totalFat)

            detail.llDetailFoods.removeAllViews()
            if (section.foods.isEmpty()) {
                detail.tvDetailEmpty.visibility = View.VISIBLE
                detail.llMacroPills.visibility = View.GONE
            } else {
                detail.tvDetailEmpty.visibility = View.GONE
                detail.llMacroPills.visibility = View.VISIBLE
                section.foods.forEach { food ->
                    val row = ItemHomeMealFoodBinding.inflate(
                        layoutInflater,
                        detail.llDetailFoods,
                        false,
                    )
                    row.tvMealFoodName.text = food.name
                    row.tvMealFoodMeta.text = getString(
                        R.string.meal_food_meta_format,
                        food.servingGrams,
                        food.calories,
                    )
                    row.btnRemoveMealFood.setOnClickListener {
                        viewModel.deleteMealFood(food.id)
                    }
                    row.rowFoodContent.setOnClickListener {
                        viewModel.onMealFoodClicked(food, section.mealType)
                    }
                    detail.llDetailFoods.addView(row.root)
                }
            }
            drawer.addView(detail.root)
        }
    }

    private fun bindWaterCard(state: HomeUiState) {
        val card = binding.sectionWater
        card.tvWaterProgress.text = getString(
            R.string.water_progress_format,
            state.waterIntakeMl,
            state.waterGoalMl,
        )
        card.progressWater.setProgressCompat(state.waterProgressPercent, true)
    }

    private fun bindExerciseCard(state: HomeUiState) {
        val card = binding.sectionExercise
        card.llExercises.removeAllViews()
        if (state.exercises.isEmpty()) {
            card.llExercises.visibility = View.GONE
            card.tvExerciseEmpty.visibility = View.VISIBLE
            return
        }
        card.llExercises.visibility = View.VISIBLE
        card.tvExerciseEmpty.visibility = View.GONE
        state.exercises.forEach { exercise ->
            val row = ItemHomeExerciseBinding.inflate(layoutInflater, card.llExercises, false)
            row.tvExerciseName.text = exercise.name
            row.tvExerciseCalories.text =
                getString(R.string.exercise_calories_format, exercise.caloriesBurned)
            card.llExercises.addView(row.root)
        }
    }

    private fun bindWeightCard(state: HomeUiState) {
        val weight = state.todayWeightKg

        binding.sectionWeight.tvWeightValue.text = if (weight == null) {
            "— kg"
        } else {
            getString(R.string.weight_kg_format, weight)
        }

        binding.sectionWeight.btnWeightMinus.isEnabled = true
        binding.sectionWeight.btnWeightPlus.isEnabled = true

        binding.sectionWeight.btnWeightMinus.alpha = 1.0f
        binding.sectionWeight.btnWeightPlus.alpha = 1.0f
    }

    private fun openSearchFood(mealType: MealType) {
        val selectedDate = viewModel.uiState.value.currentDate
        searchFoodLauncher.launch(
            Intent(requireContext(), SearchFoodActivity::class.java).apply {
                putExtra(SearchFoodActivity.EXTRA_MEAL_TYPE, mealType.name)
                putExtra(SearchFoodActivity.EXTRA_SELECTED_DATE, selectedDate.toString())
            },
        )
    }

    private fun openFoodDetail(event: HomeNavEvent.OpenFoodDetail) {
        foodDetailLauncher.launch(
            FoodDetailActivity.intent(
                context = requireContext(),
                name = event.name,
                calories = event.calories,
                protein = event.protein,
                carb = event.carb,
                fat = event.fat,
                servingGrams = event.servingGrams,
                mealType = event.mealType,
                selectedDate = viewModel.uiState.value.currentDate,
                viewOnly = event.viewOnly,
            ),
        )
    }

    private fun openExerciseLogger() {
        exerciseLauncher.launch(
            Intent(requireContext(), ExerciseLoggerActivity::class.java).apply {
                putExtra(
                    ExerciseLoggerActivity.EXTRA_SELECTED_DATE,
                    viewModel.uiState.value.currentDate.toString(),
                )
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
