import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String, default: String = ""): String =
    localProperties.getProperty(key)?.trim().orEmpty().ifEmpty { default }

/** Strips quotes and duplicate `.apps.googleusercontent.com` suffixes from the Web Client ID. */
fun sanitizeGoogleWebClientId(raw: String): String {
    var value = raw.trim().trim('"').trim('\'')
    val suffix = ".apps.googleusercontent.com"
    while (value.endsWith("$suffix$suffix")) {
        value = value.removeSuffix(suffix)
    }
    return value
}

android {
    namespace = "com.example.calories"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.calories"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val supabaseUrl = localProp("SUPABASE_URL", "https://YOUR_PROJECT.supabase.co")
        val supabaseAnonKey = localProp("SUPABASE_ANON_KEY", "YOUR_ANON_KEY")
        val geminiApiKey = localProp("GEMINI_API_KEY", "YOUR_GEMINI_API_KEY")
        val googleWebClientId = sanitizeGoogleWebClientId(localProp("GOOGLE_WEB_CLIENT_ID", ""))

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.constraintlayout)

    implementation(platform("io.ktor:ktor-bom:${libs.versions.ktor.get()}"))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.mpandroidchart)
    implementation(libs.coil)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect.client)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}