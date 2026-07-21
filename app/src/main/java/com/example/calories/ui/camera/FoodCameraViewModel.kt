package com.example.calories.ui.camera

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.network.service.GeminiAnalysisService
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.mapGeminiErrorToUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FoodCameraUiState(
    val isAnalyzing: Boolean = false,
)

sealed interface FoodCameraNavEvent {
    /** Emitted when Gemini analysis finishes; UI should gate navigation behind an interstitial ad. */
    data class AnalysisReady(
        val imagePath: String,
        val result: FoodAnalysisResult,
    ) : FoodCameraNavEvent
}

@HiltViewModel
class FoodCameraViewModel @Inject constructor(
    private val geminiAnalysisService: GeminiAnalysisService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodCameraUiState())
    val uiState: StateFlow<FoodCameraUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<FoodCameraNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun openAnalysis(imagePath: String) {
        if (_uiState.value.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            try {
                val bitmap = BitmapFactory.decodeFile(File(imagePath).absolutePath)
                    ?: throw IllegalArgumentException("Could not decode selected image")
                val result = geminiAnalysisService.analyzeFoodImage(bitmap)
                _navEvents.send(FoodCameraNavEvent.AnalysisReady(imagePath, result))
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false) }
                _events.send(mapGeminiErrorToUiEvent(e))
            }
        }
    }

    fun onAnalysisFlowComplete() {
        _uiState.update { it.copy(isAnalyzing = false) }
    }

    fun onCaptureFailed(message: String) {
        _events.trySend(UiEvent.Message(message))
    }
}
