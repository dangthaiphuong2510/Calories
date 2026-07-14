package com.example.calories.ui.common

/**
 * One-shot UI side-effects emitted by ViewModels.
 */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class MessageRes(val resId: Int) : UiEvent
}
