package com.example.calories.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.model.UserGoal
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.CalorieCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OnboardingFormState(
    val existingGoalId: String? = null,
    val gender: Gender = Gender.MALE,
    val age: Int? = null,
    val heightCm: Double? = null,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: GoalType = GoalType.MAINTAIN,
    val tdee: Int = 0,
    val dailyCalories: Int = 0,
    val isLoading: Boolean = false,
    val isPrefillReady: Boolean = false,
)

sealed interface OnboardingNavEvent {
    data object ToMain : OnboardingNavEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val userGoalsRepository: UserGoalsRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(OnboardingFormState())
    val formState: StateFlow<OnboardingFormState> = _formState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<OnboardingNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    init {
        loadExistingGoal()
    }

    fun loadExistingGoal() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: run {
            _formState.update { it.copy(isPrefillReady = true) }
            return
        }
        viewModelScope.launch {
            try {
                userGoalsRepository.refresh(userId)
                val goal = userGoalsRepository.observeGoal(userId).first()
                if (goal != null) {
                    _formState.update {
                        it.copy(
                            existingGoalId = goal.id,
                            gender = goal.gender,
                            age = goal.age,
                            heightCm = goal.heightCm,
                            currentWeight = goal.currentWeight,
                            targetWeight = goal.targetWeight,
                            activityLevel = goal.activityLevel,
                            goalType = goal.goalType,
                            tdee = goal.tdee,
                            dailyCalories = goal.dailyCalories,
                            isPrefillReady = true,
                        )
                    }
                } else {
                    _formState.update { it.copy(isPrefillReady = true) }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isPrefillReady = true) }
                _events.send(UiEvent.Message(e.message ?: "Could not load goals"))
            }
        }
    }

    fun updateGender(gender: Gender) {
        _formState.update { it.copy(gender = gender) }
        recalculate()
    }

    fun updateAge(age: Int?) {
        _formState.update { it.copy(age = age) }
        recalculate()
    }

    fun updateHeight(heightCm: Double?) {
        _formState.update { it.copy(heightCm = heightCm) }
        recalculate()
    }

    fun updateCurrentWeight(weight: Double?) {
        _formState.update { it.copy(currentWeight = weight) }
        recalculate()
    }

    fun updateTargetWeight(weight: Double?) {
        _formState.update { it.copy(targetWeight = weight) }
    }

    fun updateActivityLevel(level: ActivityLevel) {
        _formState.update { it.copy(activityLevel = level) }
        recalculate()
    }

    fun updateGoalType(goalType: GoalType) {
        _formState.update { it.copy(goalType = goalType) }
        recalculate()
    }

    fun saveGoals() {
        val state = _formState.value
        val userId = supabase.auth.currentUserOrNull()?.id
        val age = state.age
        val height = state.heightCm
        val currentWeight = state.currentWeight
        val targetWeight = state.targetWeight

        if (userId == null ||
            age == null ||
            height == null ||
            currentWeight == null ||
            targetWeight == null ||
            state.tdee == 0
        ) {
            viewModelScope.launch {
                _events.send(UiEvent.Message("Please fill in all fields"))
            }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                userGoalsRepository.saveGoal(
                    UserGoal(
                        id = state.existingGoalId ?: UUID.randomUUID().toString(),
                        userId = userId,
                        targetWeight = targetWeight,
                        currentWeight = currentWeight,
                        age = age,
                        gender = state.gender,
                        heightCm = height,
                        activityLevel = state.activityLevel,
                        goalType = state.goalType,
                        tdee = state.tdee,
                        dailyCalories = state.dailyCalories,
                    ),
                )
                _navEvents.send(OnboardingNavEvent.ToMain)
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.Message(e.message ?: "Could not save your goals"))
            }
        }
    }

    private fun recalculate() {
        val state = _formState.value
        val age = state.age
        val height = state.heightCm
        val weight = state.currentWeight
        if (age == null || height == null || weight == null ||
            age <= 0 || height <= 0 || weight <= 0
        ) {
            _formState.update { it.copy(tdee = 0, dailyCalories = 0) }
            return
        }
        val bmr = CalorieCalculator.calculateBmr(weight, height, age, state.gender)
        val tdee = CalorieCalculator.calculateTdee(bmr, state.activityLevel)
        val daily = CalorieCalculator.calculateDailyCalories(tdee, state.goalType)
        _formState.update { it.copy(tdee = tdee, dailyCalories = daily) }
    }
}
