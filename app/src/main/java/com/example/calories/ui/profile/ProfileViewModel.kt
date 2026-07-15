package com.example.calories.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.ProfileRepository
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.model.Profile
import com.example.calories.model.UserGoal
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.GoalType
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class ProfileUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val goal: UserGoal? = null,
    val profile: Profile? = null,
)

sealed interface ProfileNavEvent {
    data object SignedOut : ProfileNavEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val userGoalsRepository: UserGoalsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _navEvents = Channel<ProfileNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    val uiState: StateFlow<ProfileUiState> = flowOf(userId)
        .flatMapLatest { id ->
            val user = supabase.auth.currentUserOrNull()
            val authName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            val email = user?.email.orEmpty()
            if (id == null) {
                flowOf(ProfileUiState(userName = authName ?: "User", userEmail = email))
            } else {
                combine(
                    userGoalsRepository.observeGoal(id),
                    profileRepository.observeProfile(id),
                ) { goal, profile ->
                    ProfileUiState(
                        userName = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: authName
                            ?: "User",
                        userEmail = email,
                        goal = goal,
                        profile = profile,
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

    fun signOut() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
            _navEvents.send(ProfileNavEvent.SignedOut)
        }
    }

    fun goalTypeLabelRes(goalType: GoalType): Int = when (goalType) {
        GoalType.LOSE_WEIGHT -> com.example.calories.R.string.goal_lose_weight
        GoalType.GAIN_MUSCLE -> com.example.calories.R.string.goal_gain_muscle
        GoalType.MAINTAIN -> com.example.calories.R.string.goal_maintain
    }

    fun activityLevelLabelRes(level: ActivityLevel): Int = when (level) {
        ActivityLevel.SEDENTARY -> com.example.calories.R.string.activity_sedentary
        ActivityLevel.LIGHT -> com.example.calories.R.string.activity_light
        ActivityLevel.MODERATE -> com.example.calories.R.string.activity_moderate
        ActivityLevel.ACTIVE -> com.example.calories.R.string.activity_active
        ActivityLevel.VERY_ACTIVE -> com.example.calories.R.string.activity_very_active
    }
}
