import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun resolveOAuthConfig(envKey: String, propKey: String): String {
    val fromEnv = System.getenv(envKey)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return localProps.getProperty(propKey)?.trim().orEmpty()
}

fun escapeBuildConfigLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.dagger.hilt)
}

android {
    namespace = "com.notes.purenoteit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.purenoteit.app"
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("purenote.versionCode").get().toInt()
        versionName = providers.gradleProperty("purenote.versionName").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val androidReleaseClientId = escapeBuildConfigLiteral(
        resolveOAuthConfig("PURENOTE_ANDROID_OAUTH_CLIENT_ID", "purenote.android.oauth.clientId")
    )
    val androidDebugClientId = escapeBuildConfigLiteral(
        resolveOAuthConfig("PURENOTE_ANDROID_DEBUG_OAUTH_CLIENT_ID", "purenote.android.debug.oauth.clientId")
            .ifEmpty { androidReleaseClientId }
    )

    val releaseStorePath = resolveOAuthConfig("PURENOTE_RELEASE_STORE_FILE", "purenote.release.storeFile")
    val releaseStorePassword = resolveOAuthConfig("PURENOTE_RELEASE_STORE_PASSWORD", "purenote.release.storePassword")
    val releaseKeyAlias = resolveOAuthConfig("PURENOTE_RELEASE_KEY_ALIAS", "purenote.release.keyAlias")
    val releaseKeyPassword = resolveOAuthConfig("PURENOTE_RELEASE_KEY_PASSWORD", "purenote.release.keyPassword")
    val hasReleaseSigning = releaseStorePath.isNotEmpty() &&
        file(releaseStorePath).isFile &&
        releaseStorePassword.isNotEmpty() &&
        releaseKeyAlias.isNotEmpty() &&
        releaseKeyPassword.isNotEmpty()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$androidDebugClientId\"")
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$androidReleaseClientId\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Jetpack Compose Material 3
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // Dagger Hilt
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    // Room + SQLCipher Full-Database AES-256 Encryption
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)

    // ML Kit Text Recognition (OCR)
    implementation(libs.play.services.mlkit.text.recognition)

    // Google Play In-App Updates
    implementation(libs.play.app.update)
}
