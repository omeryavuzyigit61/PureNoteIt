import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.notes.purenoteit.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        val commonTest = getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.ktor.client.mock)
            }
        }

        val jvmCommonMain = create("jvmCommonMain") {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jvmCommonTest = create("jvmCommonTest") {
            dependsOn(commonTest)
        }

        getByName("androidMain") {
            dependsOn(jvmCommonMain)
        }

        getByName("desktopMain") {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.java.keyring)
            }
        }
        getByName("desktopTest") {
            dependsOn(jvmCommonTest)
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
