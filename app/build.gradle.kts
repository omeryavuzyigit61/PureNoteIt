
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    alias(libs.plugins.google.dagger.hilt)
    alias(libs.plugins.androidx.navigation.safe.args)
}

android {
    namespace = "com.notes.purenoteit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.notes.purenoteit"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
    implementation(libs.touchimageview)
    implementation(libs.androidx.biometric)
    implementation(libs.materialsearchbar)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //RXJava 2
    implementation(libs.androidx.room.rxjava2)
    implementation(libs.rxandroid)

    //Dagger Hilt
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    //Room
    implementation(libs.androidx.room.runtime)
    ksp (libs.androidx.room.compiler)

    //Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)
}