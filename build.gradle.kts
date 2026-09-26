import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

plugins {
    // AGP 9 ships built-in Kotlin. Declare the Kotlin plugin without applying it so AGP
    // uses this compiler instead of its bundled one. Do not apply kotlin-android.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Google’s Android Kotlin style guide via ktlint `android_studio`.
// https://developer.android.com/kotlin/style-guide
// https://ktlint.github.io/ktlint/latest/rules/code-styles/
spotless {
    kotlin {
        // Source roots only: a tree over app/ walks app/build, and Gradle fails spotless
        // when KSP writes there earlier in the same build, even with the files excluded.
        target("app/src/**/*.kt", "app/test/**/*.kt", "app/androidTest/**/*.kt")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    tasks.withType<Test>().configureEach {
        forkEvery = 100L
    }
}
