package net.reichholf.dreamdroid.testing

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty Hilt activity for instrumented Compose tests, so `hiltViewModel()` resolves
 * against the app's real graph: `createAndroidComposeRule<HiltComposeTestActivity>()`.
 * Debug builds only.
 */
@AndroidEntryPoint
class HiltComposeTestActivity : ComponentActivity()
