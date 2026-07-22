package com.example.calories.data.auth

sealed interface AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}
