package com.example.calories.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class AuthErrorMapperTest {

    @Test
    fun mapsEmailNotConfirmed() {
        assertEquals(AuthError.EmailNotConfirmed, AuthErrorMapper.map("email_not_confirmed", null))
    }

    @Test
    fun mapsInvalidCredentials() {
        assertEquals(AuthError.InvalidCredentials, AuthErrorMapper.map("invalid_credentials", null))
    }

    @Test
    fun mapsEmailAlreadyRegistered() {
        assertEquals(AuthError.EmailAlreadyRegistered, AuthErrorMapper.map("user_already_exists", null))
        assertEquals(AuthError.EmailAlreadyRegistered, AuthErrorMapper.map("email_exists", null))
    }

    @Test
    fun mapsWeakPassword() {
        assertEquals(AuthError.WeakPassword, AuthErrorMapper.map("weak_password", null))
    }

    @Test
    fun mapsRateLimited() {
        assertEquals(AuthError.RateLimited, AuthErrorMapper.map("over_email_send_rate_limit", null))
    }

    @Test
    fun mapsNetworkFromIoException() {
        assertEquals(AuthError.Network, AuthErrorMapper.map(null, IOException("no network")))
    }

    @Test
    fun unknownCodeFallsBackToUnknownWithMessage() {
        val result = AuthErrorMapper.map("some_new_code", RuntimeException("boom"))
        assertEquals(AuthError.Unknown("boom"), result)
    }
}
