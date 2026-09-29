package net.reichholf.dreamdroid.testutil

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs every instrumented test on [HiltTestApplication] instead of `DreamDroid`, so a
 * `@HiltAndroidTest` gets a fresh graph per test and can replace bindings with
 * `@TestInstallIn`. `DreamDroid.onCreate` does not run: a test that needs part of its
 * startup (the current profile, for one) does that step itself.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
