import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun resolveOAuthSetting(envKey: String, propKey: String): String {
    val fromEnv = System.getenv(envKey)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    return localProps.getProperty(propKey)?.trim().orEmpty()
}

fun escapeKotlinString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\n", "")
        .replace("\r", "")

val oauthGeneratedDir = layout.buildDirectory.dir("generated/oauth/kotlin")

val generateDesktopOAuthConfig = tasks.register("generateDesktopOAuthConfig") {
    val desktopClientId = resolveOAuthSetting("PURENOTE_DESKTOP_OAUTH_CLIENT_ID", "purenote.desktop.oauth.clientId")
    val desktopClientSecret = resolveOAuthSetting("PURENOTE_DESKTOP_OAUTH_CLIENT_SECRET", "purenote.desktop.oauth.clientSecret")
    val purenoteVersion = providers.gradleProperty("purenote.versionName").orElse("1.2.1").get()
    inputs.property("desktopClientId", desktopClientId)
    inputs.property("desktopClientSecret", desktopClientSecret)
    inputs.property("purenoteVersion", purenoteVersion)
    outputs.dir(oauthGeneratedDir)

    doLast {
        val outFile = oauthGeneratedDir.get()
            .file("com/notes/purenoteit/desktop/BuildDesktopOAuthConfig.kt")
            .asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package com.notes.purenoteit.desktop

            import com.notes.purenoteit.security.OAuthPkceConfig

            object BuildDesktopOAuthConfig {
                const val APP_VERSION: String = "${escapeKotlinString(purenoteVersion)}"
                const val GOOGLE_DESKTOP_CLIENT_ID: String = "${escapeKotlinString(desktopClientId)}"
                const val GOOGLE_DESKTOP_CLIENT_SECRET: String = "${escapeKotlinString(desktopClientSecret)}"

                fun isConfigured(): Boolean = GOOGLE_DESKTOP_CLIENT_ID.isNotBlank()

                fun createPkceConfig(): OAuthPkceConfig? {
                    val cid = GOOGLE_DESKTOP_CLIENT_ID.trim()
                    if (cid.isEmpty()) return null
                    return OAuthPkceConfig(
                        clientId = cid,
                        redirectUri = "http://127.0.0.1/oauth2redirect",
                        clientSecret = GOOGLE_DESKTOP_CLIENT_SECRET.trim().takeIf { it.isNotEmpty() }
                    )
                }
            }
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sourceSets {
    named("main") {
        kotlin.srcDir(oauthGeneratedDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateDesktopOAuthConfig)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
}

compose.desktop {
    application {
        mainClass = "com.notes.purenoteit.desktop.DesktopMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "PureNoteIt"
            packageVersion = providers.gradleProperty("purenote.versionName").get()
            vendor = "PureNoteIt"
            copyright = "Copyright (c) 2026 PureNoteIt"
            description = "PureNoteIt Smart Notes & Workspace"
            modules("java.sql", "java.desktop", "java.naming", "jdk.crypto.ec")
            windows {
                iconFile.set(project.file("src/main/resources/purenoteit.ico"))
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                menuGroup = "PureNoteIt"
                upgradeUuid = "a4d7c2e0-8f3b-4c51-9b12-6e1f7c8a9d20"
            }
        }
    }
}
