# Unified Auth / Account Linking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a user who registered with email/password later taps "Sign in with Google" using the same address, link to the existing Supabase account instead of creating a duplicate — with email verification enforced (Option A) and clear error handling.

**Architecture:** Introduce a dedicated `AuthRepository` (interface + Supabase-backed impl) that owns every `supabase.auth` call, returns a typed `AuthResult`/`AuthError` (no raw exceptions leaking into UI), and centralizes GoTrue error-code mapping. `RegisterViewModel` and `LoginViewModel` are refactored to depend on `AuthRepository` instead of `SupabaseClient` directly. Account linking itself is handled server-side by Supabase **Automatic Linking** (same verified email → one user); the app's job is to enforce email confirmation, surface the right messages, and never create duplicates.

**Tech Stack:** Kotlin 2.0.21, supabase-kt 3.0.0 (`auth-kt`), Ktor 3.0.0-rc-1, Hilt 2.52, AndroidX Credentials + Google Identity (already wired), MVVM with `StateFlow` + `Channel<UiEvent>`.

## Global Constraints

- supabase-kt version: **3.0.0** (`io.github.jan-tennert.supabase:auth-kt`). Do not add new dependencies.
- All Supabase auth exceptions subclass `io.github.jan.supabase.exceptions.RestException`, which exposes `val error: String` (the GoTrue error-code string, e.g. `email_not_confirmed`) and `val description: String?`. Match on `.error`.
- ViewModels never touch `SupabaseClient` directly after this plan; they depend only on `AuthRepository`.
- UI side-effects use the existing `com.example.calories.ui.common.UiEvent` (`Message(String)` / `MessageRes(Int)`) emitted over a `Channel`, collected via `collectLatestStarted`.
- Package root: `com.example.calories`. Auth code lives under `com.example.calories.data.auth`.
- Repositories are bound in `com.example.calories.di.RepositoryModule` with `@Binds @Singleton`.
- No new user-facing strings may be hardcoded in ViewModels/Activities; add them to `app/src/main/res/values/strings.xml` and emit via `UiEvent.MessageRes`.

---

## Design & Supabase Configuration Decisions (Option A)

This section is the spec the tasks implement. Read it before starting.

**What "Option A" means in practice.** Email verification is the source of truth. A hard, pre-emptive "block Google" cannot be done purely client-side (Supabase does not expose whether an unconfirmed same-email account exists, to prevent user-enumeration attacks). So Option A is realized as three concrete guarantees:

1. **No duplicates.** Supabase **Automatic Linking** is ON (default). When Google sign-in presents a verified email that matches an existing user, Supabase links the Google identity to that existing user — one account, no duplicate row. This is server behavior; the app just calls `signInWith(IDToken)`.
2. **Email confirmation enforced.** Supabase "Confirm email" is ON. An email/password account cannot log in until confirmed. Attempting to log in unconfirmed yields the GoTrue code `email_not_confirmed`, which we surface as a clear message plus a "resend confirmation email" action. This is the client-detectable "block" in Option A.
3. **Takeover-safe linking.** Because Google emails are verified, if an *unconfirmed* email/password account exists for that address, Supabase Automatic Linking links Google and **removes the unconfirmed email identity** (documented anti-"pre-account-takeover" behavior). Net result: the user is signed in via Google, single account, and the never-confirmed password identity is discarded. We surface an informational message when the signed-in user has only a Google identity so the user understands they should use Google (or set a password later).

**Supabase settings required (Task 1):**
- Authentication → Providers → Email → **Confirm email = ON**.
- Authentication → Providers → Email → **Secure email change = ON** (double confirm).
- Automatic Linking: **enabled** (GoTrue default; `GOTRUE_SECURITY_MANUAL_LINKING_ENABLED` stays `false`). Do NOT enable manual linking — automatic linking is what merges same-email accounts.
- Google provider enabled with the app's Web Client ID as an authorized client ID (already used for native ID-token sign-in).

**Files map:**
- `data/auth/AuthError.kt` — typed error enum.
- `data/auth/AuthResult.kt` — success/failure wrapper.
- `data/auth/AuthErrorMapper.kt` — pure `String? -> AuthError` mapping (unit-tested).
- `data/auth/AuthUser.kt` — minimal user snapshot exposed to UI.
- `data/auth/AuthRepository.kt` — interface.
- `data/auth/AuthRepositoryImpl.kt` — Supabase-backed impl (only place that imports `supabase.auth`).
- `di/RepositoryModule.kt` — add `@Binds` for `AuthRepository`.
- `ui/auth/RegisterViewModel.kt`, `ui/auth/LoginViewModel.kt` — refactor to use `AuthRepository`.
- `ui/auth/LoginActivity.kt` — handle new resend-confirmation nav/event.
- `app/src/main/res/values/strings.xml` — new messages.
- `app/src/test/java/com/example/calories/data/auth/AuthErrorMapperTest.kt` — unit test.

**Testing note:** `AuthErrorMapper` is a pure function and is unit-tested with JUnit (already a dependency). Repository and ViewModel behavior against live Supabase is verified with the manual E2E checklist in Task 9 (there is no existing coroutine/Supabase test harness in the project, so we do not fabricate one).

---

### Task 1: Configure Supabase Auth (email confirmation + automatic linking)

**Files:** none (Supabase dashboard + verification via MCP). Record the outcome in the PR description.

**Interfaces:**
- Produces: a Supabase project where email confirmations are ON and automatic linking is enabled. No code depends on this at compile time, but Tasks 8–9 depend on the runtime behavior.

- [ ] **Step 1: Verify no duplicate emails already exist**

Run this via the Supabase MCP `execute_sql` (read-only) against the project:

```sql
select email, count(*) as n
from auth.users
where email is not null
group by email
having count(*) > 1;
```

Expected: 0 rows. If rows are returned, automatic linking cannot safely merge those addresses — stop and resolve the duplicates manually before continuing (decide which user is canonical, migrate data, delete the extra).

- [ ] **Step 2: Enable "Confirm email" and "Secure email change"**

In the Supabase Dashboard → Authentication → Providers → Email:
- Set **Confirm email = ON**.
- Set **Secure email change = ON**.

Save.

- [ ] **Step 3: Confirm automatic linking is enabled (not manual)**

Automatic linking is the GoTrue default and is what merges same-email identities. Ensure **manual linking is OFF** (Authentication → advanced/security settings; self-hosted env var `GOTRUE_SECURITY_MANUAL_LINKING_ENABLED` must be unset or `false`). No dashboard toggle is needed to keep automatic linking on.

- [ ] **Step 4: Confirm Google provider config**

Authentication → Providers → Google: enabled, and the app's Web Client ID (`BuildConfig.GOOGLE_WEB_CLIENT_ID`) is present in the list of authorized client IDs (required for native ID-token sign-in). Save.

- [ ] **Step 5: Verify settings took effect**

Register a throwaway email/password account from the app (or dashboard), then run via MCP `execute_sql`:

```sql
select email, email_confirmed_at
from auth.users
order by created_at desc
limit 1;
```

Expected: the newest user has `email_confirmed_at = null` immediately after signup (proves "Confirm email" is enforced). Delete the throwaway user afterward.

---

### Task 2: Typed auth errors + result wrapper + error mapper

**Files:**
- Create: `app/src/main/java/com/example/calories/data/auth/AuthError.kt`
- Create: `app/src/main/java/com/example/calories/data/auth/AuthResult.kt`
- Create: `app/src/main/java/com/example/calories/data/auth/AuthErrorMapper.kt`
- Test: `app/src/test/java/com/example/calories/data/auth/AuthErrorMapperTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface AuthError` with objects: `InvalidCredentials`, `EmailNotConfirmed`, `EmailAlreadyRegistered`, `WeakPassword`, `RateLimited`, `Network`, `Cancelled`, and `data class Unknown(val message: String?)`.
  - `sealed interface AuthResult<out T>` with `data class Success<T>(val data: T)` and `data class Failure(val error: AuthError)`.
  - `object AuthErrorMapper { fun map(gotrueCode: String?, throwable: Throwable?): AuthError }`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/calories/data/auth/AuthErrorMapperTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.calories.data.auth.AuthErrorMapperTest"`
Expected: FAIL — `AuthError`, `AuthErrorMapper` unresolved (compilation error).

- [ ] **Step 3: Create `AuthError.kt`**

```kotlin
package com.example.calories.data.auth

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object EmailNotConfirmed : AuthError
    data object EmailAlreadyRegistered : AuthError
    data object WeakPassword : AuthError
    data object RateLimited : AuthError
    data object Network : AuthError
    data object Cancelled : AuthError
    data class Unknown(val message: String?) : AuthError
}
```

- [ ] **Step 4: Create `AuthResult.kt`**

```kotlin
package com.example.calories.data.auth

sealed interface AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}
```

- [ ] **Step 5: Create `AuthErrorMapper.kt`**

```kotlin
package com.example.calories.data.auth

import java.io.IOException

object AuthErrorMapper {

    fun map(gotrueCode: String?, throwable: Throwable?): AuthError = when (gotrueCode) {
        "email_not_confirmed" -> AuthError.EmailNotConfirmed
        "invalid_credentials", "invalid_grant" -> AuthError.InvalidCredentials
        "user_already_exists", "email_exists" -> AuthError.EmailAlreadyRegistered
        "weak_password" -> AuthError.WeakPassword
        "over_email_send_rate_limit", "over_request_rate_limit" -> AuthError.RateLimited
        else -> if (throwable is IOException) {
            AuthError.Network
        } else {
            AuthError.Unknown(throwable?.message)
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.calories.data.auth.AuthErrorMapperTest"`
Expected: PASS (7 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/data/auth/AuthError.kt \
        app/src/main/java/com/example/calories/data/auth/AuthResult.kt \
        app/src/main/java/com/example/calories/data/auth/AuthErrorMapper.kt \
        app/src/test/java/com/example/calories/data/auth/AuthErrorMapperTest.kt
git commit -m "feat(auth): add typed AuthError/AuthResult and GoTrue error mapper"
```

---

### Task 2b: AuthUser snapshot + AuthRepository interface

**Files:**
- Create: `app/src/main/java/com/example/calories/data/auth/AuthUser.kt`
- Create: `app/src/main/java/com/example/calories/data/auth/AuthRepository.kt`

**Interfaces:**
- Consumes: `AuthResult`, `AuthError` (Task 2).
- Produces:
  - `data class AuthUser(val id: String, val email: String?, val isEmailConfirmed: Boolean, val linkedProviders: List<String>)`.
  - `interface AuthRepository` with:
    - `suspend fun registerWithEmail(name: String, email: String, password: String): AuthResult<Unit>`
    - `suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>`
    - `suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<AuthUser>`
    - `suspend fun resendConfirmationEmail(email: String): AuthResult<Unit>`
    - `suspend fun currentUser(): AuthUser?`
    - `suspend fun signOut()`

- [ ] **Step 1: Create `AuthUser.kt`**

```kotlin
package com.example.calories.data.auth

data class AuthUser(
    val id: String,
    val email: String?,
    val isEmailConfirmed: Boolean,
    val linkedProviders: List<String>,
) {
    val hasEmailPasswordIdentity: Boolean get() = linkedProviders.any { it == "email" }
    val hasGoogleIdentity: Boolean get() = linkedProviders.any { it == "google" }
    val isGoogleOnly: Boolean get() = hasGoogleIdentity && !hasEmailPasswordIdentity
}
```

- [ ] **Step 2: Create `AuthRepository.kt`**

```kotlin
package com.example.calories.data.auth

interface AuthRepository {

    /** Registers an email/password user. Success does not imply a session — email confirmation is required. */
    suspend fun registerWithEmail(name: String, email: String, password: String): AuthResult<Unit>

    /** Signs in with email/password. Fails with [AuthError.EmailNotConfirmed] if the email is unverified. */
    suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>

    /**
     * Signs in with a Google ID token. Supabase Automatic Linking merges this identity into an
     * existing user with the same verified email (no duplicate account is created).
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<AuthUser>

    /** Re-sends the signup confirmation email for an unconfirmed account. */
    suspend fun resendConfirmationEmail(email: String): AuthResult<Unit>

    /** Returns the currently authenticated user snapshot, or null if there is no session. */
    suspend fun currentUser(): AuthUser?

    suspend fun signOut()
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (interface + data class only).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/data/auth/AuthUser.kt \
        app/src/main/java/com/example/calories/data/auth/AuthRepository.kt
git commit -m "feat(auth): add AuthUser snapshot and AuthRepository interface"
```

---

### Task 3: AuthRepositoryImpl (Supabase-backed)

**Files:**
- Create: `app/src/main/java/com/example/calories/data/auth/AuthRepositoryImpl.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AuthUser`, `AuthResult`, `AuthError`, `AuthErrorMapper` (Tasks 2, 2b); `SupabaseClient` (provided by `NetworkModule`).
- Produces: `class AuthRepositoryImpl @Inject constructor(private val supabase: SupabaseClient) : AuthRepository`.

- [ ] **Step 1: Create `AuthRepositoryImpl.kt`**

```kotlin
package com.example.calories.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override suspend fun registerWithEmail(
        name: String,
        email: String,
        password: String,
    ): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("full_name", name) }
        }
        // With "Confirm email" ON, sign-up may return a session-less user; never keep a
        // half-open session before the user has confirmed. Force login as the next step.
        if (supabase.auth.currentSessionOrNull() != null) {
            runCatching { supabase.auth.signOut() }
        }
        Unit
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        requireCurrentUser()
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String,
    ): AuthResult<AuthUser> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
        requireCurrentUser()
    }

    override suspend fun resendConfirmationEmail(email: String): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
        Unit
    }

    override suspend fun currentUser(): AuthUser? {
        supabase.auth.awaitInitialization()
        return currentUserSnapshotOrNull()
    }

    override suspend fun signOut() {
        runCatching {
            supabase.auth.awaitInitialization()
            supabase.auth.signOut()
        }
    }

    private suspend fun requireCurrentUser(): AuthUser =
        currentUserSnapshotOrNull() ?: throw IllegalStateException("no_session_after_auth")

    private suspend fun currentUserSnapshotOrNull(): AuthUser? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        val providers = (supabase.auth.currentIdentitiesOrNull() ?: emptyList())
            .map { it.provider }
        return AuthUser(
            id = user.id,
            email = user.email,
            isEmailConfirmed = user.emailConfirmedAt != null,
            linkedProviders = providers,
        )
    }

    private inline fun <T> runAuth(block: () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: RestException) {
        AuthResult.Failure(AuthErrorMapper.map(e.error, e))
    } catch (e: IOException) {
        AuthResult.Failure(AuthError.Network)
    } catch (e: Exception) {
        AuthResult.Failure(AuthError.Unknown(e.message))
    }
}
```

Note on `runAuth` + `suspend`: the lambdas call suspend functions, so `runAuth` must be callable in a suspend context. `inline fun` preserves the suspend calling context of the enclosing suspend function, so this compiles. If the compiler rejects `inline` here, change the signature to `private suspend inline fun <T> runAuth(block: () -> T)` — keep it `inline` so the suspend `block` is allowed.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If `currentIdentitiesOrNull()`, `emailConfirmedAt`, `resendEmail`, or `error` resolve incorrectly, check the installed `auth-kt` 3.0.0 symbols (do not guess a different version): `Identity.provider: String`, `UserInfo.emailConfirmedAt: Instant?`, `Auth.resendEmail(type, email)`, `RestException.error: String`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/data/auth/AuthRepositoryImpl.kt
git commit -m "feat(auth): add Supabase-backed AuthRepositoryImpl"
```

---

### Task 4: Bind AuthRepository in Hilt

**Files:**
- Modify: `app/src/main/java/com/example/calories/di/RepositoryModule.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AuthRepositoryImpl`.
- Produces: an injectable `AuthRepository` singleton.

- [ ] **Step 1: Add the binding**

In `RepositoryModule.kt`, add imports and a `@Binds` method. Add these imports next to the existing repository imports:

```kotlin
import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthRepositoryImpl
```

Add this method inside the `abstract class RepositoryModule` body (e.g. after `bindFoodRepository`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
```

- [ ] **Step 2: Verify it compiles (Hilt graph resolves)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If Hilt reports a missing binding, confirm `AuthRepositoryImpl`'s only dependency (`SupabaseClient`) is provided by `NetworkModule.provideSupabaseClient` (it is).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/di/RepositoryModule.kt
git commit -m "feat(auth): bind AuthRepository in Hilt RepositoryModule"
```

---

### Task 5: Add user-facing strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces string resource IDs consumed by Tasks 6–7: `R.string.auth_email_not_confirmed`, `R.string.auth_confirmation_resent`, `R.string.auth_invalid_credentials`, `R.string.auth_email_already_registered`, `R.string.auth_weak_password`, `R.string.auth_rate_limited`, `R.string.auth_network_error`, `R.string.auth_google_linked_info`, `R.string.auth_generic_error`.

- [ ] **Step 1: Add the strings**

Add inside the root `<resources>` element of `app/src/main/res/values/strings.xml`:

```xml
    <string name="auth_email_not_confirmed">Please confirm your email first. We\'ve sent a confirmation link to your inbox — verify it, then sign in.</string>
    <string name="auth_confirmation_resent">Confirmation email sent. Check your inbox (and spam folder).</string>
    <string name="auth_invalid_credentials">Incorrect email or password.</string>
    <string name="auth_email_already_registered">This email is already registered. Try signing in instead.</string>
    <string name="auth_weak_password">Password is too weak. Use at least 8 characters.</string>
    <string name="auth_rate_limited">Too many attempts. Please wait a moment and try again.</string>
    <string name="auth_network_error">Network error. Check your connection and try again.</string>
    <string name="auth_google_linked_info">Signed in with Google and linked to your existing account.</string>
    <string name="auth_generic_error">Something went wrong. Please try again.</string>
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (resources compile; no `R` reference errors yet since consumers come next).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(auth): add unified-auth user-facing strings"
```

---

### Task 6: Refactor RegisterViewModel to use AuthRepository

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/auth/RegisterViewModel.kt`

**Interfaces:**
- Consumes: `AuthRepository.registerWithEmail`, `AuthResult`, `AuthError`.
- Produces: unchanged public surface (`uiState`, `events`, `navEvents`, `register(name, email, password)`).

- [ ] **Step 1: Replace the ViewModel body**

Rewrite `RegisterViewModel.kt` to inject `AuthRepository` instead of `SupabaseClient`, and map `AuthResult`:

```kotlin
package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.auth.AuthError
import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthResult
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val isLoading: Boolean = false,
)

sealed interface RegisterNavEvent {
    data object ToLogin : RegisterNavEvent
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<RegisterNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.registerWithEmail(name, email, password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    // Note: if the email is already used by an OAuth (Google) account, Supabase
                    // returns an obfuscated success with no email sent (anti-enumeration). We still
                    // route to login and show the standard success message — no duplicate is created.
                    _events.send(UiEvent.MessageRes(R.string.register_success_message))
                    _navEvents.send(RegisterNavEvent.ToLogin)
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add the shared error-to-string extension**

The mapping `AuthError -> @StringRes Int` is used by both Register and Login ViewModels. Create it once at `app/src/main/java/com/example/calories/ui/auth/AuthErrorMessages.kt`:

```kotlin
package com.example.calories.ui.auth

import androidx.annotation.StringRes
import com.example.calories.R
import com.example.calories.data.auth.AuthError

@StringRes
fun AuthError.toMessageRes(): Int = when (this) {
    AuthError.EmailNotConfirmed -> R.string.auth_email_not_confirmed
    AuthError.InvalidCredentials -> R.string.auth_invalid_credentials
    AuthError.EmailAlreadyRegistered -> R.string.auth_email_already_registered
    AuthError.WeakPassword -> R.string.auth_weak_password
    AuthError.RateLimited -> R.string.auth_rate_limited
    AuthError.Network -> R.string.auth_network_error
    AuthError.Cancelled -> R.string.auth_generic_error
    is AuthError.Unknown -> R.string.auth_generic_error
}
```

(Add `Create: app/src/main/java/com/example/calories/ui/auth/AuthErrorMessages.kt` to this task's file list.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Confirm `R.string.register_success_message` still exists (it was used by the old code).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/auth/RegisterViewModel.kt \
        app/src/main/java/com/example/calories/ui/auth/AuthErrorMessages.kt
git commit -m "refactor(auth): RegisterViewModel uses AuthRepository with typed errors"
```

---

### Task 7: Refactor LoginViewModel (email + Google + resend)

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/auth/LoginViewModel.kt`

**Interfaces:**
- Consumes: `AuthRepository` (all methods), `AuthResult`, `AuthError`, `AuthUser`, `AuthNavigationResolver`, `AuthError.toMessageRes()` (Task 6).
- Produces:
  - Unchanged: `uiState`, `events`, `navEvents`, `login(email, password)`, `loginWithGoogle(idToken, rawNonce)`.
  - New public method: `fun resendConfirmation(email: String)`.
  - New nav event: `LoginNavEvent.PromptResendConfirmation(val email: String)` so the Activity can offer a resend action.

- [ ] **Step 1: Rewrite `LoginViewModel.kt`**

```kotlin
package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.auth.AuthDestination
import com.example.calories.data.auth.AuthError
import com.example.calories.data.auth.AuthNavigationResolver
import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthResult
import com.example.calories.data.auth.AuthUser
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
)

sealed interface LoginNavEvent {
    data object ToMain : LoginNavEvent
    data object ToOnboarding : LoginNavEvent
    data class PromptResendConfirmation(val email: String) : LoginNavEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authNavigationResolver: AuthNavigationResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<LoginNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> completeAuthentication()
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    if (result.error == AuthError.EmailNotConfirmed) {
                        // Option A: block sign-in and offer to resend the confirmation link.
                        _events.send(UiEvent.MessageRes(R.string.auth_email_not_confirmed))
                        _navEvents.send(LoginNavEvent.PromptResendConfirmation(email))
                    } else {
                        _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                    }
                }
            }
        }
    }

    fun loginWithGoogle(idToken: String, rawNonce: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.signInWithGoogle(idToken, rawNonce)) {
                is AuthResult.Success -> {
                    maybeInformAboutLinking(result.data)
                    completeAuthentication()
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                }
            }
        }
    }

    fun resendConfirmation(email: String) {
        viewModelScope.launch {
            when (val result = authRepository.resendConfirmationEmail(email)) {
                is AuthResult.Success ->
                    _events.send(UiEvent.MessageRes(R.string.auth_confirmation_resent))
                is AuthResult.Failure ->
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
            }
        }
    }

    /**
     * When automatic linking merged Google into an existing email account (or the unconfirmed
     * email identity was dropped), the signed-in user may now be Google-only. Inform the user so
     * they understand the account was linked.
     */
    private suspend fun maybeInformAboutLinking(user: AuthUser) {
        if (user.hasGoogleIdentity && user.hasEmailPasswordIdentity) {
            _events.send(UiEvent.MessageRes(R.string.auth_google_linked_info))
        }
    }

    private suspend fun completeAuthentication() {
        authNavigationResolver.markAuthenticated()
        val destination = authNavigationResolver.resolveDestination()
        _uiState.update { it.copy(isLoading = false) }
        _navEvents.send(
            when (destination) {
                AuthDestination.MAIN -> LoginNavEvent.ToMain
                AuthDestination.ONBOARDING -> LoginNavEvent.ToOnboarding
                AuthDestination.LOGIN -> LoginNavEvent.ToMain
            },
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL at `LoginActivity.kt` `when (event)` — it does not yet handle `LoginNavEvent.PromptResendConfirmation`. This is expected and fixed in Task 8. (If you build only this file's module target it compiles; the failure is in the consumer.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/auth/LoginViewModel.kt
git commit -m "refactor(auth): LoginViewModel uses AuthRepository; handle email_not_confirmed + resend + link info"
```

---

### Task 8: Wire resend-confirmation prompt in LoginActivity

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/auth/LoginActivity.kt`

**Interfaces:**
- Consumes: `LoginNavEvent.PromptResendConfirmation`, `LoginViewModel.resendConfirmation(email)`.
- Produces: a user prompt (dialog) offering to resend the confirmation email.

- [ ] **Step 1: Handle the new nav event**

In `LoginActivity.observeViewModel()`, the current `collectLatestStarted(viewModel.navEvents)` block maps events to a destination `Class` and always starts an Activity. Restructure it to branch, so `PromptResendConfirmation` shows a dialog instead of navigating:

Replace the existing `collectLatestStarted(viewModel.navEvents) { event -> ... }` block with:

```kotlin
        collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                LoginNavEvent.ToMain -> navigateTo(MainActivity::class.java)
                LoginNavEvent.ToOnboarding -> navigateTo(OnboardingActivity::class.java)
                is LoginNavEvent.PromptResendConfirmation -> showResendConfirmationDialog(event.email)
            }
        }
```

Add these two helper methods to `LoginActivity`:

```kotlin
    private fun navigateTo(destination: Class<*>) {
        startActivity(
            Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    private fun showResendConfirmationDialog(email: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_email_not_confirmed_title)
            .setMessage(R.string.auth_email_not_confirmed)
            .setPositiveButton(R.string.auth_resend_confirmation) { _, _ ->
                viewModel.resendConfirmation(email)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
```

- [ ] **Step 2: Add the two new strings used by the dialog**

Append to `app/src/main/res/values/strings.xml`:

```xml
    <string name="auth_email_not_confirmed_title">Confirm your email</string>
    <string name="auth_resend_confirmation">Resend email</string>
```

- [ ] **Step 3: Confirm MaterialAlertDialog import path**

`com.google.android.material.dialog.MaterialAlertDialogBuilder` is provided by `com.google.android.material:material` (version `1.12.0`, already a dependency). The activity theme extends a Material/AppCompat theme (verify `app/src/main/res/values/themes.xml`); if the dialog crashes at runtime with a theme error, pass an explicit overlay theme as the second `MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Material3_MaterialAlertDialog)` argument.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the Task 7 consumer error is now resolved).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/auth/LoginActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(auth): prompt to resend confirmation email when login blocked by unverified email"
```

---

### Task 9: Full build, lint, and manual E2E verification

**Files:** none (verification only).

- [ ] **Step 1: Assemble the debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run unit tests + lint**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: `AuthErrorMapperTest` passes; lint reports no new errors on the changed files.

- [ ] **Step 3: Manual E2E — no duplicate on same-email Google (core requirement)**

1. Register `test+link@yourdomain.com` with email/password in the app.
2. Open the confirmation email and confirm the account.
3. Sign in once with email/password to prove it works.
4. Sign out. Tap "Sign in with Google" and pick the same-email Google account.
5. Confirm sign-in succeeds and lands on Main/Onboarding.
6. Verify a single account exists via MCP `execute_sql`:

```sql
select id, email, email_confirmed_at from auth.users where email = 'test+link@yourdomain.com';
```

Expected: exactly **one** row. Then:

```sql
select provider from auth.identities i
join auth.users u on u.id = i.user_id
where u.email = 'test+link@yourdomain.com';
```

Expected: both `email` and `google` providers on the same user id → linked, no duplicate.

- [ ] **Step 4: Manual E2E — unverified email is blocked (Option A)**

1. Register `test+unverified@yourdomain.com` but DO NOT confirm.
2. Attempt email/password login.
3. Expected: toast `Please confirm your email first...` and a dialog offering "Resend email". Tap it → toast `Confirmation email sent...`.

- [ ] **Step 5: Manual E2E — Google onto unconfirmed email (takeover-safe)**

1. Register `test+takeover@yourdomain.com`, do NOT confirm.
2. Tap "Sign in with Google" with that same Google account.
3. Expected: sign-in succeeds. Verify via MCP:

```sql
select provider from auth.identities i
join auth.users u on u.id = i.user_id
where u.email = 'test+takeover@yourdomain.com';
```

Expected: only `google` remains (the unconfirmed `email` identity was dropped by Supabase). Single user row, no duplicate.

- [ ] **Step 6: Clean up test users**

Delete the three test users from the Supabase dashboard (Authentication → Users) or via admin API.

- [ ] **Step 7: Final commit (if any lint autofixes)**

```bash
git add -A
git commit -m "chore(auth): finalize unified auth account linking"
```

---

## Self-Review

**Spec coverage:**
- "Same-email Google links to existing account, no duplicate" → Task 1 (automatic linking + email uniqueness) + Task 3 (`signInWithGoogle`) + Task 9 Step 3. ✅
- "How to configure Supabase Auth policies/settings" → Task 1. ✅
- "Structure Kotlin Auth Repository/ViewModel" → Tasks 2–8 (`AuthRepository` + refactored ViewModels). ✅
- "Clear code flow for Google tokens + linking" → Task 3 `signInWithGoogle` + Task 7. ✅
- "Error handling if email not verified" → Task 2 (`EmailNotConfirmed`) + Task 7 (block + resend) + Task 8 (dialog) + Task 9 Step 4. ✅

**Placeholder scan:** No TBD/TODO; all code steps contain complete code. ✅

**Type consistency:** `AuthResult`/`AuthError`/`AuthUser`/`AuthRepository` names and method signatures are consistent across Tasks 2–8. `AuthError.toMessageRes()` defined once (Task 6) and reused (Task 7). `LoginNavEvent.PromptResendConfirmation(email)` produced in Task 7, consumed in Task 8. ✅

**Known risk flagged:** exact `auth-kt` 3.0.0 symbol names (`currentIdentitiesOrNull`, `emailConfirmedAt`, `resendEmail`, `RestException.error`) are called out in Task 3 Step 2 with the expected signatures to check against, rather than assumed silently.
