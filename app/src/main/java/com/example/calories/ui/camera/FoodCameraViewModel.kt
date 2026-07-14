package com.example.calories.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.remote.gemini.FoodAnalysisService
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FoodCameraNavEvent {
    data class ToResult(val result: FoodAnalysisResult) : FoodCameraNavEvent
}

@HiltViewModel
class FoodCameraViewModel @Inject constructor(
    private val foodAnalysisService: FoodAnalysisService,
) : ViewModel() {

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<FoodCameraNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun analyzeImage(imageBytes: ByteArray) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val result = foodAnalysisService.analyzeFoodImage(imageBytes)
                _navEvents.send(FoodCameraNavEvent.ToResult(result))
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not analyze this photo"))
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun onCaptureFailed(message: String) {
        viewModelScope.launch {
            _isAnalyzing.value = false
            _events.send(UiEvent.Message(message))
        }
    }
}
