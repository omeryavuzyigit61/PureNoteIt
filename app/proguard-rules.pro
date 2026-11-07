# PureNoteIt ProGuard / R8 Optimization & Obfuscation Rules

# 1. Line numbers and debugging attributes (Preserves line numbers in crash reports)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Room Database & SQLCipher Rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.notes.purenoteit.data.db.** { *; }
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-dontwarn androidx.room.paging.**

# 3. Model & Data Classes (Gson / JSON & Type-Safe Navigation kotlinx.serialization)
-keepclassmembers enum * { *; }
-keep class com.notes.purenoteit.model.** { *; }
-keepclassmembers class com.notes.purenoteit.model.** { *; }
-keep class com.notes.purenoteit.util.VaultBackupPayload { *; }
-keep class com.notes.purenoteit.util.Converters { *; }
-keep,includedescriptorclasses class com.notes.purenoteit.ui.navigation.** { *; }
-keepclassmembers class com.notes.purenoteit.ui.navigation.** {
    *** Companion;
}

# 4. Dagger Hilt & Dependency Injection
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @dagger.hilt.InstallIn interface * { *; }

# 5. Jetpack Compose (Allow R8 obfuscation on app UI & Vault classes)
-dontwarn androidx.compose.**

# 6. Biometrics & ML Kit
-keep class androidx.biometric.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# 7. Coroutines
-dontwarn kotlinx.coroutines.**

# 8. Stealth Log Stripping (Removes all android.util.Log calls in release builds)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
