package net.reichholf.dreamdroid.testsupport

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import net.reichholf.dreamdroid.R

/**
 * Debug-only host for instrumented fragment helper tests (avoids fragment-testing's
 * EmptyFragmentActivity, which must live in the app APK).
 */
class FragmentHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DreamDroid_Night)
        super.onCreate(savedInstanceState)
        setContentView(
            FragmentContainerView(this).apply {
                id = android.R.id.content
            },
        )
    }
}
