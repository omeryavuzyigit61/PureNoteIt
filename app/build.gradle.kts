plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    alias(libs.plugins.google.dagger.hilt)
    alias(libs.plugins.androidx.navigation.safe.args)
}

android {
    namespace = "com.notes.purenoteit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notes.purenoteit"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.gson)
    implementation(libs.androidx.biometric)
    testImplementation(libs.junit)
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
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // RXJava 2
    implementation(libs.androidx.room.rxjava2)
    implementation(libs.rxandroid)

    // Dagger Hilt
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)
}
