package com.example.calories.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.preferences.AppLanguage
import com.example.calories.data.preferences.AppPreferences
import com.example.calories.data.preferences.AuthDataStore
import com.example.calories.data.preferences.AvatarStorage
import com.example.calories.data.preferences.LocalDataWiper
import com.example.calories.data.preferences.ThemeMode
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.ProfileRepository
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.data.repository.WaterRepository
import com.example.calories.model.NutritionReportData
import com.example.calories.model.Profile
import com.example.calories.model.UserGoal
import com.example.calories.util.DateTimeUtils
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import com.example.calories.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import kotlin.math.pow

data class ProfileUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val avatarUrl: String? = null,
    val goal: UserGoal? = null,
    val profile: Profile? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val heightText: String = "—",
    val weightText: String = "—",
    val ageText: String = "—",
    val genderTextRes: Int? = null,
    val bmiValue: Double? = null,
    val bmiCategoryRes: Int? = null,
)

sealed interface ProfileNavEvent {
    data object SignedOut : ProfileNavEvent
    data object DataWiped : ProfileNavEvent
}

sealed interface ProfileMessage {
    data object AvatarUpdated : ProfileMessage
    data object AvatarRemoved : ProfileMessage
    data object AvatarUpdateFailed : ProfileMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val userGoalsRepository: UserGoalsRepository,
    private val profileRepository: ProfileRepository,
    private val foodRepository: FoodRepository,
    private val waterRepository: WaterRepository,
    private val appPreferences: AppPreferences,
    private val authDataStore: AuthDataStore,
    private val localDataWiper: LocalDataWiper,
    private val avatarStorage: AvatarStorage,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _navEvents = Channel<ProfileNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    private val _messages = MutableSharedFlow<ProfileMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val _isUpdatingAvatar = MutableStateFlow(false)
    val isUpdatingAvatar: StateFlow<Boolean> = _isUpdatingAvatar.asStateFlow()

    val uiState: StateFlow<ProfileUiState> = flowOf(userId)
        .flatMapLatest { id ->
            val user = supabase.auth.currentUserOrNull()
            val authName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            val email = user?.email.orEmpty()
            if (id == null) {
                combine(
                    appPreferences.unitSystem,
                    appPreferences.themeMode,
                    appPreferences.language,
                ) { unit, theme, language ->
                    ProfileUiState(
                        userName = authName ?: "User",
                        userEmail = email,
                        unitSystem = unit,
                        themeMode = theme,
                        language = language,
                    )
                }
            } else {
                combine(
                    userGoalsRepository.observeGoal(id),
                    profileRepository.observeProfile(id),
                    appPreferences.unitSystem,
                    appPreferences.themeMode,
                    appPreferences.language,
                ) { goal, profile, unit, theme, language ->
                    val metrics = formatMetrics(goal, unit)
                    ProfileUiState(
                        userName = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: authName
                            ?: "User",
                        userEmail = email,
                        avatarUrl = profile?.avatarUrl,
                        goal = goal,
                        profile = profile,
                        unitSystem = unit,
                        themeMode = theme,
                        language = language,
                        heightText = metrics.heightText,
                        weightText = metrics.weightText,
                        ageText = metrics.ageText,
                        genderTextRes = metrics.genderTextRes,
                        bmiValue = metrics.bmiValue,
                        bmiCategoryRes = metrics.bmiCategoryRes,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        refresh()
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { userGoalsRepository.refresh(id) }
            runCatching { profileRepository.refresh(id) }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            runCatching {
                profileRepository.upsertProfile(displayName = name.trim())
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        val id = userId ?: return
        viewModelScope.launch {
            _isUpdatingAvatar.value = true
            val result = runCatching {
                val path = avatarStorage.saveFromUri(id, uri)
                profileRepository.upsertProfile(avatarUrl = path, updateAvatar = true)
            }
            _isUpdatingAvatar.value = false
            _messages.emit(
                if (result.isSuccess) ProfileMessage.AvatarUpdated
                else ProfileMessage.AvatarUpdateFailed,
            )
        }
    }

    fun removeAvatar() {
        val id = userId ?: return
        viewModelScope.launch {
            _isUpdatingAvatar.value = true
            val result = runCatching {
                avatarStorage.delete(id)
                profileRepository.upsertProfile(avatarUrl = null, updateAvatar = true)
            }
            _isUpdatingAvatar.value = false
            _messages.emit(
                if (result.isSuccess) ProfileMessage.AvatarRemoved
                else ProfileMessage.AvatarUpdateFailed,
            )
        }
    }

    fun setUnitSystem(system: UnitSystem) {
        appPreferences.setUnitSystem(system)
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.setThemeMode(mode)
    }

    fun setLanguage(language: AppLanguage) {
        appPreferences.setLanguage(language)
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
            authDataStore.clearLoginState()
            _navEvents.send(ProfileNavEvent.SignedOut)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            runCatching {
                localDataWiper.wipeAll()
                supabase.auth.signOut()
            }
            authDataStore.clearLoginState()
            _navEvents.send(ProfileNavEvent.DataWiped)
        }
    }

    suspend fun buildNutritionReport(dayCount: Int): NutritionReportData? {
        val id = userId ?: return null
        if (dayCount <= 0) return null

        val state = uiState.value
        val endDate = DateTimeUtils.today()
        val startDate = endDate.minusDays((dayCount - 1).toLong())
        val startInclusive = DateTimeUtils.dayRange(startDate).first
        val endExclusive = DateTimeUtils.dayRange(endDate.plusDays(1)).first

        val foods = foodRepository.observeFoodEntries(id).first()
            .filter { it.createdAt >= startInclusive && it.createdAt < endExclusive }
        val totalWaterMl = waterRepository.observeWaterEntries(id).first()
            .filter { it.createdAt >= startInclusive && it.createdAt < endExclusive }
            .sumOf { it.amountMl }

        return NutritionReportData(
            userName = state.userName,
            dateRangeText = "${DateTimeUtils.formatDdMmYyyy(startDate)} – " +
                DateTimeUtils.formatDdMmYyyy(endDate),
            targetCaloriesPerDay = state.goal?.dailyCalories ?: 0,
            avgCaloriesIntake = foods.sumOf { it.calories } / dayCount,
            avgProteinGrams = foods.sumOf { it.protein } / dayCount,
            avgCarbsGrams = foods.sumOf { it.carb } / dayCount,
            avgFatGrams = foods.sumOf { it.fat } / dayCount,
            totalWaterMl = totalWaterMl,
        )
    }

    fun goalTypeLabelRes(goalType: GoalType): Int = when (goalType) {
        GoalType.LOSE_WEIGHT -> R.string.goal_lose_weight
        GoalType.GAIN_MUSCLE -> R.string.goal_gain_muscle
        GoalType.MAINTAIN -> R.string.goal_maintain
    }

    fun activityLevelLabelRes(level: ActivityLevel): Int = when (level) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHT -> R.string.activity_light
        ActivityLevel.MODERATE -> R.string.activity_moderate
        ActivityLevel.ACTIVE -> R.string.activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
    }

    fun unitSystemLabelRes(system: UnitSystem): Int = when (system) {
        UnitSystem.METRIC -> R.string.unit_metric
        UnitSystem.IMPERIAL -> R.string.unit_imperial
    }

    fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    }

    fun languageLabelRes(language: AppLanguage): Int = when (language) {
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.VIETNAMESE -> R.string.language_vietnamese
    }

    private fun formatMetrics(goal: UserGoal?, unit: UnitSystem): MetricsDisplay {
        if (goal == null) return MetricsDisplay()
        val heightText = UnitConverter.formatHeight(goal.heightCm, unit)
        val weightText = when (unit) {
            UnitSystem.METRIC -> String.format("%.1f kg", goal.currentWeight)
            UnitSystem.IMPERIAL -> String.format(
                "%.1f lb",
                UnitConverter.kgToLb(goal.currentWeight),
            )
        }
        val bmi = calculateBmi(goal.heightCm, goal.currentWeight)
        return MetricsDisplay(
            heightText = heightText,
            weightText = weightText,
            ageText = goal.age.toString(),
            genderTextRes = when (goal.gender) {
                Gender.MALE -> R.string.gender_male
                Gender.FEMALE -> R.string.gender_female
            },
            bmiValue = bmi,
            bmiCategoryRes = bmiCategoryRes(bmi),
        )
    }

    private fun calculateBmi(heightCm: Double, weightKg: Double): Double? {
        if (heightCm <= 0.0 || weightKg <= 0.0) return null
        val heightM = heightCm / 100.0
        return weightKg / heightM.pow(2)
    }

    private fun bmiCategoryRes(bmi: Double?): Int? = when {
        bmi == null -> null
        bmi < 18.5 -> R.string.bmi_underweight
        bmi < 25.0 -> R.string.bmi_normal
        bmi < 30.0 -> R.string.bmi_overweight
        else -> R.string.bmi_obese
    }

    private data class MetricsDisplay(
        val heightText: String = "—",
        val weightText: String = "—",
        val ageText: String = "—",
        val genderTextRes: Int? = null,
        val bmiValue: Double? = null,
        val bmiCategoryRes: Int? = null,
    )
}
